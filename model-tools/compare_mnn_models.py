#!/usr/bin/env python3
"""Pinned, serialized host MNN A/B. Raw text lives only in local child logs.

Consumes the existing native/p0/asr_main.cpp protocol, not arbitrary stdout as
success. This is an engineering tool, not an accuracy oracle or a sandbox.
"""
import argparse
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import statistics
import sys
import time
import unicodedata
import wave

from fp16_process import cancellation_signals, digest, run_bounded

ASSETS = {'audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight',
          'tokenizer.txt', 'llm_config.json', 'config.json'}
SAFE_ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9_-]{0,63}\Z')
# Benchmark INPUT policy anchored in the reviewed repo fixed seven-tuple
# reports/p0/mnn-model-manifest.json (base 7e794773); not caller spec or path trust.
# Graph/weight identities remain caller-bound A/B inputs, not APK trust updates.
TRUSTED_CONFIG_SHA256 = {
    'config.json': 'fd4127baf9ff5c45aaa2224f65db36e9110088d2aa7223e4853238d31be84e9d',
    'llm_config.json': '8e548c5393334616cffb6e8a751603ad8314d01293aa29765f1f507f0b412b85',
}


def canonical_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(',', ':')).encode()).hexdigest()


def atomic_json(path, value):
    temporary = path.with_suffix('.pending')
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + '\n')
    temporary.replace(path)


def bind_file(root, entry):
    path = root / entry['path']
    if path.is_symlink() or not path.is_file():
        raise ValueError('identity requires regular non-symlink file: ' + str(path))
    if path.stat().st_size != entry['bytes'] or digest(path) != entry['sha256']:
        raise ValueError('input identity mismatch: ' + str(path))
    return path.resolve()


def normalize(text):
    text = unicodedata.normalize('NFKC', text).casefold()
    text = ''.join(c for c in text if unicodedata.category(c)[0] not in 'PS')
    return ' '.join(text.split())


def edit_counts(reference, hypothesis, language):
    """Levenshtein edit numerator + truth denominator; no model reference labels."""
    if language not in ('Chinese', 'English'):
        raise ValueError('WER/CER requires explicit Chinese/English truth language')
    truth, prediction = normalize(reference), normalize(hypothesis)
    a = list(truth.replace(' ', '')) if language == 'Chinese' else truth.split()
    b = list(prediction.replace(' ', '')) if language == 'Chinese' else prediction.split()
    previous = list(range(len(b) + 1))
    for i, left in enumerate(a, 1):
        current = [i]
        for j, right in enumerate(b, 1):
            current.append(min(current[-1] + 1, previous[j] + 1,
                               previous[j - 1] + (left != right)))
        previous = current
    return {'edits': previous[-1], 'reference_units': len(a),
            'rate': previous[-1] / len(a) if a else None,
            'empty_reference_insertions': len(b) if not a else 0}


def spread(values):
    if not values:
        return None
    median = statistics.median(values)
    return {'n': len(values), 'median': median, 'min': min(values), 'max': max(values),
            'mad': statistics.median(abs(x - median) for x in values)}


def parse_native(log, exit_code, expected):
    def unique_json(prefix):
        rows = re.findall('^' + prefix + r' (.+)$', log, re.M)
        if len(rows) != 1:
            raise ValueError('native protocol requires unique ' + prefix)
        return json.loads(rows[0])
    config = unique_json('P0_EFFECTIVE_CONFIG')
    metrics = unique_json('P0_METRICS')
    if config != expected:
        raise ValueError('native effective config differs from frozen expected config')
    if log.count('P0_TEXT_BEGIN\n') != 1 or log.count('\nP0_TEXT_END') != 1:
        raise ValueError('native text boundary missing/duplicate')
    match = re.search(r'P0_TEXT_BEGIN\n(.*?)\nP0_TEXT_END', log, re.S)
    if match is None or 'P0_ERROR' in log:
        raise ValueError('native inference error or invalid text boundary')
    for key in ('load_s', 'inference_s', 'audio_us', 'prefill_us', 'decode_us',
                'prompt_tokens', 'generated_tokens', 'status'):
        value = metrics.get(key)
        if type(value) not in (float, int) or not math.isfinite(value) or value < 0:
            raise ValueError('invalid native metric: ' + key)
    for key in ('prompt_tokens', 'generated_tokens', 'status'):
        if type(metrics[key]) is not int:
            raise ValueError('native integer metric required: ' + key)
    if metrics['generated_tokens'] > 128 or type(metrics.get('truncated')) is not bool:
        raise ValueError('invalid token bound/truncation flag')
    if (metrics['status'], metrics['truncated'], exit_code) not in ((1, False, 0), (2, True, 6)):
        raise ValueError('inconsistent native EOS/exit/truncation tuple')
    return match.group(1).strip(), metrics, canonical_hash(config)


def text_content(raw):
    # Do not run upstream repetition repair: repetition is an observed outcome.
    text = raw.removesuffix('<|im_end|>').strip()
    if '<asr_text>' in text:
        prefix, text = text.split('<asr_text>', 1)
        if not prefix.startswith('language '):
            raise ValueError('unexpected ASR output prefix')
    return text.strip()


def repetitions(text):
    # Diagnostic only; legitimate repeated words/characters are not errors by fiat.
    compact = normalize(text).replace(' ', '')
    return any(compact[i:i + 4] * 3 == compact[i:i + 12]
               for i in range(max(0, len(compact) - 11)))


def validate_spec(spec, root):
    if spec.get('schema') != 1 or spec.get('repeats') != 3:
        raise ValueError('schema1 requires exactly three timing pairs')
    if set(spec['models']) != {'A', 'B'}:
        raise ValueError('requires exactly A and B')
    driver = bind_file(root, spec['driver'])
    if not os.access(driver, os.X_OK):
        raise ValueError('native driver not executable')
    if not spec.get('runtime_inputs'):
        raise ValueError('runtime/source bindings required')
    for entry in spec['runtime_inputs']:
        bind_file(root, entry)
    models = {name: validate_model(spec, root, entries)
              for name, entries in spec['models'].items()}
    cases = {}
    for case in spec['cases']:
        identity = case['id']
        if not SAFE_ID.fullmatch(identity) or identity in cases:
            raise ValueError('invalid/duplicate case id')
        cases[identity] = validate_case(root, case)
    if not cases or sum(c['samples'] for c in cases.values()) / 16000 > 300:
        raise ValueError('unique corpus must be nonempty and <=300 seconds')
    if not spec['representatives'] or len(set(spec['representatives'])) != len(spec['representatives']) or not set(spec['representatives']) <= cases.keys():
        raise ValueError('invalid performance representatives')
    return driver, models, cases


def validate_model(spec, root, entries):
    if len(entries) != 7 or {Path(e['path']).name for e in entries} != ASSETS:
        raise ValueError('model requires exact seven asset closure')
    paths = {Path(e['path']).name: bind_file(root, e) for e in entries}
    if len({p.parent for p in paths.values()}) != 1:
        raise ValueError('all model assets must be in same directory')
    for name, trusted_sha in TRUSTED_CONFIG_SHA256.items():
        if digest(paths[name]) != trusted_sha:
            raise ValueError('fixed trusted config policy requires exact supported ' + name + ' bytes')
    config = json.loads(paths['config.json'].read_text())
    metadata = json.loads(paths['llm_config.json'].read_text())
    if config != spec['base_config'] or metadata != spec['model_config']:
        raise ValueError('both models must share exact frozen config and metadata')
    required = {'backend_type': 'cpu', 'thread_num': 2, 'precision': 'high',
                'memory': 'low', 'sampler_type': 'greedy', 'max_new_tokens': 128,
                'async': False, 'llm_model': 'llm.mnn', 'llm_weight': 'llm.mnn.weight',
                'tokenizer_file': 'tokenizer.txt',
                'mllm': {'backend_type': 'cpu', 'thread_num': 2, 'precision': 'high', 'memory': 'low'}}
    effective = validate_effective_config(config, metadata, required)
    validate_qwen_input_selectors(config, metadata, effective)
    # Native Llm::load probes this default input even without a context_file key.
    if os.path.lexists(paths['config.json'].parent / 'context.json'):
        raise ValueError('unbound optional native context.json input')
    return paths['config.json']


def native_merge(base, metadata):
    """Match audited native ujson object/object recursive merge; never mutate spec."""
    result = copy.deepcopy(base)
    for key, value in metadata.items():
        if isinstance(result.get(key), dict) and isinstance(value, dict):
            result[key] = native_merge(result[key], value)
        else:
            result[key] = copy.deepcopy(value)
    return result


def validate_effective_config(config, metadata, required):
    effective = native_merge(config, metadata)
    # Canonical JSON comparison also rejects bool-as-int and float-as-int policy bypasses.
    actual = {key: effective.get(key) for key in required}
    if canonical_hash(actual) != canonical_hash(required):
        raise ValueError('unsupported effective native model/runtime config')
    forbidden = {'base_dir', 'llm_config', 'audio_model', 'visual_model', 'embedding_file',
                 'block_model', 'lm_model', 'embedding_model', 'npu_model_dir', 'context_file',
                 'tmp_path', 'prefix_cache_path'}
    if forbidden & effective.keys():
        raise ValueError('unsupported external asset config override')
    return effective


def validate_qwen_input_selectors(config, metadata, effective):
    """Finite audited pinned Qwen3-ASR load path, not a generic model schema."""
    if 'ple_embed_file' in config or 'ple_embed_file' in metadata:
        # Native has_ple tests presence, including empty/null values and dimension zero.
        raise ValueError('unsupported PLE input config activation')
    defaults = {'is_visual': False, 'has_talker': False, 'has_deepstack': False,
                'is_embedding': False, 'speculative_type': '', 'use_mmap': False,
                'kvcache_mmap': False}
    actual = {key: effective.get(key, value) for key, value in defaults.items()}
    if canonical_hash(actual) != canonical_hash(defaults):
        raise ValueError('unsupported external input config selector')
    qwen = {'model_type': 'qwen3_asr', 'is_audio': True, 'audio_type': 'qwen3_asr'}
    if canonical_hash({key: effective.get(key) for key in qwen}) != canonical_hash(qwen):
        raise ValueError('requires audited Qwen3-ASR input config')
    tie = effective.get('tie_embeddings')
    offset = tie.get('weight_offset') if isinstance(tie, dict) else None
    if type(offset) is not int or offset <= 0:
        raise ValueError('input config must tie embeddings to bound llm.mnn.weight')


def validate_case(root, case):
    path = bind_file(root, case)
    with wave.open(str(path), 'rb') as audio:
        if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getcomptype()) != (1, 2, 16000, 'NONE'):
            raise ValueError('requires mono PCM16 16k WAV')
        if audio.getnframes() != case['samples'] or case['samples'] <= 0:
            raise ValueError('WAV sample count mismatch')
        if len(audio.readframes(case['samples'])) != case['samples'] * 2:
            raise ValueError('truncated WAV body')
    if case['language'] not in ('Chinese', 'English', None):
        raise ValueError('unsupported language')
    if case.get('gold'):
        gold = case['gold']
        if not gold.get('independently_verified') or not gold.get('source') or not gold.get('license'):
            raise ValueError('gold requires audited independent truth source/license')
        if case['language'] is None:
            raise ValueError('gold requires explicit language')
        try:
            bind_file(root, gold).read_text(encoding='utf-8')
        except (OSError, UnicodeError) as error:
            raise ValueError('gold must be readable UTF-8 before admission') from error
    return {**case, 'resolved': path}


def schedule(cases, representatives, repeats):
    result = []
    for index, case in enumerate(cases):
        for model in ('A', 'B') if index % 2 == 0 else ('B', 'A'):
            result.append(('correctness', index, case, model))
    for case in representatives:
        for index in range(repeats):
            for model in ('A', 'B') if index % 2 == 0 else ('B', 'A'):
                result.append(('performance', index, case, model))
    return result


def expected_config(spec, language):
    # Admission already validates the same native merge, including protected metadata.
    return {**native_merge(spec['base_config'], spec['model_config']), 'async': False,
            'sampler_type': 'greedy', 'max_new_tokens': 128, 'asr_language': language or ''}


def one_run(item, spec, root, driver, models, cases, output, env, cancellation, persist, seconds):
    phase, repeat, case_id, model = item
    case = cases[case_id]
    entry = {'phase': phase, 'repeat': repeat, 'case': case_id, 'model': model,
             'status': 'RUNNING', 'complete_eos': False}
    log = output / f'{phase}-{repeat:02d}-{case_id}-{model}.log'
    argv = [str(driver), str(models[model]), str(case['resolved']), case['language'] or 'auto']
    def update(record):
        entry['execution'] = dict(record)
        persist(entry)
    execution = run_bounded(argv, log, min(120, seconds), 9 * 1024**3, env,
                            cancellation=cancellation, update=update)
    entry.update(execution=execution, status='FAILED')
    text = None
    try:
        raw, metrics, config_sha = parse_native(log.read_text(), execution['exit'],
                                                expected_config(spec, case['language']))
        text = text_content(raw)
        entry.update(metrics=metrics, config_sha256=config_sha,
                     raw_output_sha256=hashlib.sha256(raw.encode()).hexdigest(),
                     text_sha256=hashlib.sha256(text.encode()).hexdigest(),
                     normalized_sha256=hashlib.sha256(normalize(text).encode()).hexdigest(),
                     text_characters=len(text), repetition_heuristic=repetitions(text),
                     silence_nonempty=bool(text) if case.get('synthetic_silence') else None,
                     inference_rtf=metrics['inference_s'] / (case['samples'] / 16000),
                     wall_rtf=execution['seconds'] / (case['samples'] / 16000))
        entry['complete_eos'] = metrics['status'] == 1 and execution['status'] == 'PASS'
        entry['status'] = 'PASS' if entry['complete_eos'] else 'INCOMPLETE'
    except (ValueError, KeyError, TypeError, OSError) as error:
        entry.update(status='FAILED', protocol_error=type(error).__name__ + ': ' + str(error))
    if case.get('gold') and entry['complete_eos']:
        try:
            truth = bind_file(root, case['gold']).read_text(encoding='utf-8')
            entry['gold_score'] = edit_counts(truth, text, case['language'])
        except (ValueError, KeyError, TypeError, OSError) as error:
            # Native EOS remains true, but requested scoring is an independent obligation.
            entry.update(status='FAILED', score_error=type(error).__name__ + ': ' + str(error))
    persist(entry)
    return entry, text


def summarize(runs, spec):
    result = {'models': {}, 'agreement': {'eligible': 0, 'raw_equal': 0, 'normalized_equal': 0},
              'quality': 'not_available_without_independent_gold', 'paired_performance': {}}
    for model in ('A', 'B'):
        owned = [r for r in runs if r['model'] == model]
        correct = [r for r in owned if r['phase'] == 'correctness']
        perf = [r for r in owned if r['phase'] == 'performance' and r['complete_eos']]
        stats = {key: spread([r['metrics'][key] for r in perf]) for key in ('load_s', 'inference_s')}
        stats.update({key: spread([r[key] for r in perf]) for key in ('inference_rtf', 'wall_rtf')})
        stats['process_wall_s'] = spread([r['execution']['seconds'] for r in perf])
        stats['sampled_owned_rss_sum_bytes'] = spread([r['execution']['peak_owned_tree_rss_bytes'] for r in perf])
        scores = [r['gold_score'] for r in correct if 'gold_score' in r]
        result['models'][model] = {'correctness_runs': len(correct),
            'complete_eos': sum(r['complete_eos'] for r in correct),
            'incomplete_or_failed': sum(r['status'] != 'PASS' for r in correct),
            'truncated': sum(r.get('metrics', {}).get('truncated', False) for r in correct),
            'silence_nonempty': sum(r.get('silence_nonempty') is True for r in correct),
            'repetition_heuristic': sum(r.get('repetition_heuristic', False) for r in correct),
            'first_observed_load_s': owned[0].get('metrics', {}).get('load_s') if owned else None,
            'performance_complete': len(perf), 'performance_stats': stats,
            'gold_scores': scores}
    by_key = {(r['case'], r['model']): r for r in runs if r['phase'] == 'correctness'}
    for case in spec['cases']:
        a, b = (by_key.get((case['id'], m)) for m in ('A', 'B'))
        if a and b and a['complete_eos'] and b['complete_eos']:
            result['agreement']['eligible'] += 1
            result['agreement']['raw_equal'] += a['raw_output_sha256'] == b['raw_output_sha256']
            result['agreement']['normalized_equal'] += a['normalized_sha256'] == b['normalized_sha256']
    result['paired_performance'] = {case: paired_performance(runs, case, spec['repeats'])
                                    for case in spec['representatives']}
    summarize_gold(result, runs, spec)
    return result


def paired_performance(runs, case, repeats):
    lookup = {(r['repeat'], r['model']): r for r in runs
              if r['phase'] == 'performance' and r['case'] == case and r['complete_eos']}
    paired = []
    for repeat in range(repeats):
        if (repeat, 'A') not in lookup or (repeat, 'B') not in lookup:
            continue
        a = lookup[repeat, 'A']['metrics']['inference_s']
        b = lookup[repeat, 'B']['metrics']['inference_s']
        paired.append({'repeat': repeat, 'inference_delta_b_minus_a_s': b - a,
                       'inference_ratio_b_over_a': b / a if a else None})
    return {'pairs': paired,
            'delta_stats': spread([p['inference_delta_b_minus_a_s'] for p in paired]),
            'ratio_stats': spread([p['inference_ratio_b_over_a'] for p in paired
                                   if p['inference_ratio_b_over_a'] is not None])}


def summarize_gold(result, runs, spec):
    # Independent truth metrics stay language-separated, never combine words and characters.
    for language in ('Chinese', 'English'):
        truth_cases = {c['id'] for c in spec['cases'] if c['language'] == language and c.get('gold')}
        if truth_cases:
            result['quality'] = 'finite_independent_gold_only'
            for model in ('A', 'B'):
                scores = [r['gold_score'] for r in runs if r['phase'] == 'correctness' and r['case'] in truth_cases and r['model'] == model and 'gold_score' in r]
                edits = sum(s['edits'] for s in scores); units = sum(s['reference_units'] for s in scores)
                failures = sum('score_error' in r for r in runs if r['phase'] == 'correctness' and r['case'] in truth_cases and r['model'] == model)
                result['models'][model][language + '_gold'] = {'eligible': len(truth_cases), 'scored': len(scores), 'score_failures': failures, 'scoring_complete': len(scores) == len(truth_cases) and not failures, 'edits': edits, 'reference_units': units, 'micro_rate': edits / units if units else None}


def execute(spec_path, output):
    root = Path.cwd()
    spec = json.loads(spec_path.read_text())
    driver, models, cases = validate_spec(spec, root)
    output.mkdir(parents=True, exist_ok=False)
    state = {'status': 'RUNNING', 'spec_sha256': digest(spec_path), 'spec': spec,
             'tool_sha256': digest(Path(__file__)), 'process_helper_sha256': digest(Path(__file__).with_name('fp16_process.py')),
             'platform': platform.platform(), 'python': sys.version, 'runs': [],
             'memory_boundary': '250ms sampled owned RSS sum, not PSS or guaranteed peak',
             'filesystem_boundary': 'fresh process is not OS-page-cold; no cache flush',
             'environment': {key: os.environ.get(key) for key in
                             ('PATH', 'LD_LIBRARY_PATH', 'OMP_NUM_THREADS', 'MKL_NUM_THREADS',
                              'OPENBLAS_NUM_THREADS', 'HF_HUB_OFFLINE', 'TRANSFORMERS_OFFLINE')},
             'hardware': {'machine': platform.machine(), 'cpu_count': os.cpu_count(),
                          'cpuinfo_sha256': digest('/proc/cpuinfo')}}
    atomic_json(output / 'results.json', state)
    env = dict(os.environ, OMP_NUM_THREADS='2', MKL_NUM_THREADS='2', OPENBLAS_NUM_THREADS='2',
               TOKENIZERS_PARALLELISM='false', HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1')
    start = time.monotonic()
    def persist(entry):
        if not state['runs'] or state['runs'][-1] is not entry:
            state['runs'].append(entry)
        atomic_json(output / 'results.json', state)
    def finalize(cancellation):
        if cancellation.signum:
            state.update(status='CANCELLED', signal=cancellation.signum)
        atomic_json(output / 'results.json', state)
    with cancellation_signals(finalize) as cancellation:
        try:
            for item in schedule(cases, spec['representatives'], spec['repeats']):
                remaining = 1800 - (time.monotonic() - start)
                if cancellation.signum or remaining <= 0:
                    state['status'] = 'CANCELLED' if cancellation.signum else 'BUDGET_EXHAUSTED'
                    break
                entry, _ = one_run(item, spec, root, driver, models, cases, output, env, cancellation, persist, remaining)
                print('run', *item, entry['status'], 'exit', entry['execution']['exit'], flush=True)
                if not entry['execution'].get('cleanup', {}).get('confirmed_observed_terminal', not entry['execution']['started']):
                    raise RuntimeError('cleanup uncertain: no further model may start')
            else:
                state['status'] = 'PASS' if all(r['status'] == 'PASS' for r in state['runs']) else 'COMPLETED_WITH_FAILURES'
            state['summary'] = summarize(state['runs'], spec)
            state['seconds'] = time.monotonic() - start
            # Rebind inputs after real execution; any drift invalidates the batch.
            validate_spec(spec, root)
        except Exception as error:
            state.update(status='FAILED', error=type(error).__name__ + ': ' + str(error))
        finally:
            atomic_json(output / 'results.json', state)
    return 0 if state['status'] == 'PASS' else 128 + state['signal'] if state['status'] == 'CANCELLED' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--spec', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        return execute(args.spec, args.output)
    except (OSError, ValueError, KeyError, TypeError) as error:
        print('comparison rejected: ' + type(error).__name__ + ': ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
