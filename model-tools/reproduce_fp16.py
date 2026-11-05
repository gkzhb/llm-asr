#!/usr/bin/env python3
"""Fresh serial FP16 encoder/decoder export. Requires pre-audited offline tools.

No downloads, source preparation, trust/catalog writes, quantization or deployment.
Numeric downstream ASR/device acceptance is separate from conversion gates.
"""
import argparse
import json
import math
import os
from pathlib import Path
import sys
import time

from convert_fp16 import validate_gate
from fp16_process import cancellation_signals, digest, run_bounded

SCRIPTS = Path(__file__).resolve().parent
LENGTHS = {20, 99, 100, 101, 800, 801}
ASSETS = ('audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight',
          'tokenizer.txt', 'llm_config.json', 'config.json')


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    for name in ('model', 'source', 'converter', 'audio-probe', 'output', 'evidence'):
        result.add_argument('--' + name, type=Path, required=True)
    result.add_argument('--python', type=Path, default=Path(sys.executable))
    result.add_argument('--encoder-seconds', type=int, default=1800)
    result.add_argument('--decoder-seconds', type=int, default=2400)
    result.add_argument('--rss-mib', type=int, default=9216)
    result.add_argument('--dry-run', action='store_true', help='print NOT_RUN plan; create no directories')
    return result


def validate_paths(args):
    for key in ('model', 'source', 'converter', 'audio_probe', 'output', 'evidence', 'python'):
        path = getattr(args, key)
        if path.is_symlink() and key != 'python':
            raise ValueError('symlink argument refused: ' + key)
        # Preserve venv executable path: resolving its symlink loses pyvenv.cfg discovery.
        setattr(args, key, path.absolute() if key == 'python' else path.resolve())
    required = [args.model / 'config.json', args.model / 'model.safetensors',
                args.source / 'transformers/llm/export/utils/audio.py',
                args.source / 'transformers/llm/export/llmexport.py', args.converter,
                args.audio_probe, args.python]
    if not all(path.is_file() for path in required):
        raise ValueError('missing audited model/source/converter/probe/python prerequisite')
    if not Path('/proc/self/stat').is_file():
        raise ValueError('Linux /proc required for owned-process RSS supervision')
    if not (0 < args.encoder_seconds <= 1800 and 0 < args.decoder_seconds <= 2400
            and 0 < args.rss_mib <= 9216):
        raise ValueError('positive bounds required; maximum1800/2400seconds and9216MiB')
    writes = (args.output, args.evidence)
    protected = [args.model, args.source, args.converter, args.audio_probe, args.python, SCRIPTS]
    for write in writes:
        if write.exists():
            raise ValueError('fresh path required, refusing overwrite: ' + str(write))
        if any(write.is_relative_to(p) or p.is_relative_to(write) for p in protected):
            raise ValueError('output/evidence overlaps protected input')
    if any(a.is_relative_to(b) for a, b in ((writes[0], writes[1]), (writes[1], writes[0]))):
        raise ValueError('output and evidence must be disjoint fresh directories')
    return required


def plan(args):
    py = str(args.python)
    audio_source = str(args.source / 'transformers/llm/export/utils/audio.py')
    encoder = str(args.evidence / 'encoder')
    gate = str(args.evidence / 'mnn-audio-parity.json')
    def helper(script, *values):
        return [py, str(SCRIPTS / script), *map(str, values)]
    return [
        ('python-parity', helper('check_audio_parity.py', '--model', args.model, '--source', audio_source,
                                '--lengths', '20,99,100,101,800,801', '--output', args.evidence / 'audio-python-parity.json')),
        ('prompt-parity', helper('check_prompt.py', '--model', args.model, '--source',
                                args.source / 'transformers/llm/export/llmexport.py', '--output', args.evidence / 'prompt-parity.json')),
        ('onnx-export', helper('onnx_audio_gate.py', 'export', '--model', args.model, '--source', audio_source,
                              '--directory', encoder, '--output', args.evidence / 'onnx-audio-parity.json')),
        ('ort-validate', helper('onnx_audio_gate.py', 'validate', '--directory', encoder,
                               '--output', args.evidence / 'onnx-audio-parity.json')),
        ('encoder-convert', [str(args.converter), '-f', 'ONNX', '--modelFile', encoder + '/audio.onnx',
                             '--MNNModel', str(args.output / 'audio.mnn'), '--fp16', '--saveExternalData', '--transformerFuseC4=0']),
        ('native-prepare', helper('mnn_audio_gate.py', 'prepare', '--directory', encoder, '--output', gate)),
        ('native-run', [str(args.audio_probe), str(args.output / 'audio.mnn'), encoder, '801,20,99,100,101,800,801']),
        ('native-validate', helper('mnn_audio_gate.py', 'validate', '--directory', encoder,
                                  '--model', args.output / 'audio.mnn', '--output', gate)),
        ('decoder-export', helper('convert_fp16.py', '--model', args.model, '--source', args.source,
                                 '--converter', args.converter, '--output', args.output,
                                 '--audio-gate', gate, '--evidence', args.evidence)),
    ]


def report(path):
    return json.loads(path.read_text())


def check_result(name, args):
    if name == 'python-parity':
        check_python(report(args.evidence / 'audio-python-parity.json')['results'])
    if name == 'prompt-parity':
        rows = report(args.evidence / 'prompt-parity.json')
        if len(rows) != 3 or not all(r['equal'] for r in rows):
            raise ValueError('three-language prompt gate failed')
    if name == 'ort-validate':
        check_ort(report(args.evidence / 'onnx-audio-parity.json'), args.evidence / 'encoder/audio.onnx')
    if name == 'native-validate':
        validate_gate(args.evidence / 'mnn-audio-parity.json', args.output)
    if name == 'decoder-export':
        validate_gate(args.evidence / 'mnn-audio-parity.json', args.output)
        check_assets(args)


def check_python(rows):
    if len(rows) != 6 or {r['frames'] for r in rows} != LENGTHS:
        raise ValueError('Python parity six lengths required')
    if not all(r['finite'] and math.isfinite(r['max_abs']) and r['max_abs'] < 1e-5 for r in rows):
        raise ValueError('Python parity gate failed')


def check_ort(data, graph):
    rows = data['results']
    if [r['frames'] for r in rows] != [801, 20, 99, 100, 101, 800, 801]:
        raise ValueError('ORT same-session dynamic lengths required')
    valid = all(r['passed'] and r['finite'] and math.isfinite(r['max_abs'])
                and math.isfinite(r['relative_l2']) and r['max_abs'] <= 1e-4
                and r['relative_l2'] <= 1e-3 for r in rows)
    if not valid or data['onnx_sha256'] != digest(graph):
        raise ValueError('current ONNX ORT gate failed/unbound')


def identity(path, allow_symlink=False):
    if (path.is_symlink() and not allow_symlink) or not path.is_file() or path.stat().st_size <= 0:
        raise ValueError('missing/empty/symlink artifact: ' + str(path))
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest(path)}


def check_assets(args):
    data = report(args.evidence / 'exported-assets.json')
    rows = data['files']
    if len(rows) != 7 or {r['file'] for r in rows} != set(ASSETS):
        raise ValueError('exact seven-asset export manifest required')
    for row in rows:
        actual = identity(args.output / row['file'])
        if actual['bytes'] != row['bytes'] or actual['sha256'] != row['sha256']:
            raise ValueError('decoder manifest does not bind current output')


def save(path, data):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, indent=2) + '\n')
    temporary.replace(path)


def execute(args, commands, inputs):
    def finalize(cancellation):
        path = args.evidence / 'reproduction.json'
        if cancellation.signum and path.is_file():
            finish_cancel(path, report(path), cancellation)
    with cancellation_signals(finalize) as cancellation:
        result = execute_owned(args, commands, inputs, cancellation)
    return 128 + cancellation.signum if cancellation.signum else result


def execute_owned(args, commands, inputs, cancellation):
    # mkdir without exist_ok: never use stale artifacts or clean up another run's files.
    args.evidence.mkdir(parents=True)
    args.output.mkdir(parents=True)
    (args.evidence / 'encoder').mkdir()
    env = dict(os.environ, HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1', HF_DATASETS_OFFLINE='1',
               TOKENIZERS_PARALLELISM='false', OMP_NUM_THREADS='2', MKL_NUM_THREADS='2',
               OPENBLAS_NUM_THREADS='2', PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1')
    receipt = {'status': 'RUNNING', 'numeric_scope': 'conversion gates only, not frontend/ASR/device quality',
               'inputs': [identity(p, allow_symlink=(p == args.python)) for p in inputs], 'environment': {k: env[k] for k in
                ('HF_HUB_OFFLINE', 'TRANSFORMERS_OFFLINE', 'OMP_NUM_THREADS', 'MKL_NUM_THREADS', 'OPENBLAS_NUM_THREADS')},
               'stages': [{'name': name, 'argv': argv, 'status': 'NOT_RUN', 'started': False, 'exit': None}
                          for name, argv in commands]}
    destination = args.evidence / 'reproduction.json'
    save(destination, receipt)
    deadline = time.monotonic() + args.encoder_seconds
    for row in receipt['stages']:
        if cancellation.signum:
            return finish_cancel(destination, receipt, cancellation)
        if row['name'] == 'decoder-export':
            deadline = time.monotonic() + args.decoder_seconds
        remaining = deadline - time.monotonic()
        row['status'] = 'RUNNING'
        save(destination, receipt)  # durable actually-attempted stage before any Popen
        def started(record):
            row.update(record)
            save(destination, receipt)
        try:
            passed = execute_stage(row, args, env, remaining, cancellation, started)
        except (OSError, ValueError) as error:
            row.update(status='FAILED', failure='execution infrastructure: ' + str(error))
            passed = False
        if cancellation.signum:
            return finish_cancel(destination, receipt, cancellation)
        if not passed:
            receipt['status'] = 'FAILED'
            save(destination, receipt)
            return 1
        save(destination, receipt)
    receipt['status'] = 'PASS'
    receipt['assets'] = [identity(args.output / name) for name in ASSETS]
    save(destination, receipt)
    if cancellation.signum:
        return finish_cancel(destination, receipt, cancellation)
    return 0


def finish_cancel(destination, receipt, cancellation):
    # Nonthrowing handlers remain installed through terminal serialization and cleanup.
    uncertain = any(row.get('cleanup', {}).get('confirmed_observed_terminal') is False
                    for row in receipt['stages'])
    receipt.update(status='FAILED' if uncertain else 'CANCELLED', signal=cancellation.signum)
    save(destination, receipt)
    return 128 + cancellation.signum


def execute_stage(row, args, env, seconds, cancellation, update):
    if seconds <= 0:
        row.update(status='FAILED', failure='phase timeout before command; NOT_RUN', exit=None)
        return False
    row.update(run_bounded(row['argv'], args.evidence / (row['name'] + '.log'), seconds,
                           args.rss_mib * 1024 ** 2, env, cancellation, update))
    if row['status'] != 'PASS':
        return False
    try:
        check_result(row['name'], args)
        row['artifacts'] = stage_artifacts(row['name'], args)
    except (OSError, ValueError, KeyError, TypeError, IndexError) as error:
        row.update(status='FAILED', failure='gate/artifact validation: ' + str(error))
    return row['status'] == 'PASS'


def stage_artifacts(name, args):
    # Retain immutable stage receipts even if upstream later rewrites decoder graph.
    paths = [args.evidence / 'encoder/audio.onnx', args.output / 'audio.mnn', args.output / 'audio.mnn.weight']
    if name == 'decoder-export':
        paths.extend(args.output / item for item in ASSETS[2:])
        paths.extend(sorted((args.output / 'onnx').glob('*')))
    return [identity(path) for path in paths if path.is_file()]


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        inputs = validate_paths(args)
        commands = plan(args)
        if args.dry_run:
            print(json.dumps({'status': 'NOT_RUN', 'stages': [{'name': n, 'argv': a, 'status': 'NOT_RUN'}
                                                           for n, a in commands]}, indent=2))
            return 0
        return execute(args, commands, inputs)
    except (OSError, ValueError) as error:
        print('FP16 reproduction refused: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
