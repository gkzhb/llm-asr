#!/usr/bin/env python3
"""Finite current production variant policy/catalog contracts; metadata synthetic, no models."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tests'))
from mixed_compiler_lifecycle import run


def fixtures(directory, catalog_only=False):
    legacy = json.loads((ROOT / 'reports/p0/mnn-model-manifest.json').read_text())
    (directory / 'legacy-manifest.json').write_text(json.dumps(legacy))
    combined = json.loads(next(line[len('P0_EFFECTIVE_CONFIG '):] for line in
        (ROOT / 'reports/p0/device-inference-final-patches.txt').read_text().splitlines() if line.startswith('P0_EFFECTIVE_CONFIG ')))
    base = dict(list(combined.items())[list(combined).index('model_type'):])
    source = {e['file']: e for e in legacy['files']}

    def write(label, bits, mutation=None):
        config = json.loads(json.dumps(base))
        tie = config['tie_embeddings']
        tie.update(weight_offset=64, alpha_offset=64 + 151936 * 1024 * bits // 8,
                   alpha_size=0 if bits == 16 else 151936 * 16 * 2,
                   quant_bit=bits, quant_block=64, alpha_dtype='fp16')
        if mutation:
            mutation(config)
        content = json.dumps(config, ensure_ascii=False, indent=4).encode('ascii')
        entries = []
        for name in ('audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight', 'tokenizer.txt', 'llm_config.json', 'config.json'):
            if name in ('tokenizer.txt', 'config.json'):
                entries.append(dict(source[name]))
            elif name == 'llm_config.json':
                entries.append({'file': name, 'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest()})
            else:
                size = 400000000 if name == 'llm.mnn.weight' else 1024
                entries.append({'file': name, 'bytes': size, 'sha256': hashlib.sha256((label + name).encode()).hexdigest()})
        canonical = 'llm-asr-content-v2\nmnn\nqwen3-asr-0.6b\nqwen3-asr-mnn-cpu-v2\n' + ''.join(
            f"{e['file']}:{e['bytes']}:{e['sha256']}\n" for e in sorted(entries, key=lambda e: e['file']))
        policy = {'schema': 2, 'modelId': 'fixture-' + label, 'engine': 'mnn', 'architecture': 'qwen3-asr-0.6b',
                  'runtimePolicy': 'qwen3-asr-mnn-cpu-v2', 'precision': label.upper() if label in ('fp16', 'int8', 'int4', 'mixed') else 'MIXED',
                  'displayName': 'Fixture ' + label, 'contentSha256': hashlib.sha256(canonical.encode()).hexdigest(), 'files': entries}
        (directory / (label + '-config.json')).write_bytes(content)
        (directory / (label + '.json')).write_text(json.dumps(policy, separators=(',', ':')))
        return policy
    policies = [write(label, bits) for label, bits in [('fp16', 16), ('int8', 8), ('int4', 4), ('mixed', 16)]]
    reordered = dict(reversed(list(policies[0].items())))
    reordered['files'] = list(reversed(reordered['files']))
    (directory / 'reordered.json').write_text(json.dumps(reordered))
    write('config-path', 16, lambda c: c.update(base_dir='/outside/'))
    write('config-template', 16, lambda c: c['jinja'].update(chat_template='unapproved'))
    write('config-offset', 16, lambda c: c['tie_embeddings'].update(weight_offset=0))
    write('config-block', 8, lambda c: c['tie_embeddings'].update(quant_block=3))
    conflict = json.loads(json.dumps(policies[1])); conflict['modelId'] = policies[0]['modelId']
    (directory / 'collision-id.json').write_text(json.dumps(conflict))
    conflict = json.loads(json.dumps(policies[0])); conflict['modelId'] = 'alias-id'
    (directory / 'collision-alias.json').write_text(json.dumps(conflict))
    if catalog_only:
        return
    # Explicit constructor-injected tiny tokenizer fixed policy; APK production pins unchanged.
    assets = {name: b'x' for name in ('audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight', 'tokenizer.txt')}
    base_runtime = dict(list(combined.items())[:list(combined).index('model_type')])
    assets['config.json'] = (json.dumps(base_runtime, ensure_ascii=False, indent=2) + '\n').encode()
    cfg = json.loads(json.dumps(base)); cfg['tie_embeddings'].update(weight_offset=64, alpha_offset=64 + 151936 * 1024 * 16 // 8)
    assets['llm_config.json'] = json.dumps(cfg, ensure_ascii=False, indent=4).encode()
    # Seven-asset native-compatible embedding range requires a synthetic ~83MiB span.
    # Stream ZIP IO in JVM and keep this bounded zero-filled fixture independent of models.
    cfg['tie_embeddings'].update(quant_bit=4, alpha_offset=64 + 151936 * 1024 * 4 // 8,
        alpha_size=151936 * 16 * 2)
    assets['llm_config.json'] = json.dumps(cfg, ensure_ascii=False, indent=4).encode()
    weight_size = cfg['tie_embeddings']['alpha_offset'] + cfg['tie_embeddings']['alpha_size']
    # Canonical seven-file archive fixture uses bounded synthetic payload bytes, not actual weights.
    assets['llm.mnn.weight'] = bytes(weight_size)
    for name, data in assets.items():
        (directory / ('archive-' + name)).write_bytes(data)
    entries = [{'file': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()} for name, data in assets.items()]
    (directory / 'archive-v2-fixed.json').write_text(json.dumps({'files': entries}))
    def archive_descriptor(entries):
        canonical = 'llm-asr-content-v2\nmnn\nqwen3-asr-0.6b\nqwen3-asr-mnn-cpu-v2\n' + ''.join(f"{e['file']}:{e['bytes']}:{e['sha256']}\n" for e in sorted(entries, key=lambda e: e['file']))
        return {'schema': 2, 'modelId': 'archive-fixture-v2', 'engine': 'mnn', 'architecture': 'qwen3-asr-0.6b', 'runtimePolicy': 'qwen3-asr-mnn-cpu-v2', 'precision': 'INT4', 'displayName': 'Tiny archive fixture', 'contentSha256': hashlib.sha256(canonical.encode()).hexdigest(), 'files': entries}
    (directory / 'archive-v2.json').write_text(json.dumps(archive_descriptor(entries)))
    cfg['base_dir'] = '/malicious-rebound-path'
    malicious = json.dumps(cfg, indent=4).encode(); (directory / 'archive-malicious-config.json').write_bytes(malicious)
    entries = [dict(e) for e in entries]
    for e in entries:
        if e['file'] == 'llm_config.json': e.update(bytes=len(malicious), sha256=hashlib.sha256(malicious).hexdigest())
    (directory / 'archive-malicious.json').write_text(json.dumps(archive_descriptor(entries)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--catalog-only', action='store_true', help='finite metadata/recovery contracts; omit synthetic large archive')
    parser.add_argument('--mutants', action='store_true', help='two scoped compileable production guards, exact red then restoration')
    args = parser.parse_args()
    out = args.evidence_dir.resolve(); out.mkdir(parents=True, exist_ok=False)
    fixture = out / 'fixtures'; fixture.mkdir(); fixtures(fixture, args.catalog_only)
    sources = sorted(p for folder in ('model', 'modelmanagement', 'task', 'diagnostics')
                     for p in (ROOT / 'android/app/src/org/llmasr/minimal' / folder).glob('*')
                     if p.suffix in ('.kt', '.java') and p.stem not in ('AndroidModelVerificationIo', 'SafModelSource', 'SafModelZipSource'))
    sources.append(ROOT / 'tests/ModelVariantsTest.kt')
    (out / 'inputs.json').write_text(json.dumps({str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}, indent=2) + '\n')
    result = run(['bash', 'scripts/compile-jvm-sources.sh', str(out / 'classes'), ''] + list(map(str, sources)), out, 'compile', 180)
    if result.returncode:
        raise RuntimeError('variant compiler failure (not behavior red): ' + result.stderr.decode())
    import os
    cp = str(out / 'classes') + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'
    result = run(['java', '-Xmx256m', '-cp', cp, 'ModelVariantsTest', str(fixture)] + (['catalog-only'] if args.catalog_only else []), out, 'contracts', 45)
    print(result.stdout.decode(), end='')
    if result.returncode:
        raise RuntimeError('variant contract failed: ' + result.stderr.decode())
    if args.mutants:
        cases = [
            ('config', 'model/ModelPackagePolicy.kt',
             'need(root.keys == baseline.keys && root.filterKeys { it != "tie_embeddings" } == baseline.filterKeys { it != "tie_embeddings" }, "模型配置/模板不匹配固定契约")',
             'if (root.keys != baseline.keys) return // MUTANT extra config keys bypass contract',
             'correctly rebound malicious config must fail closed: config-path'),
            ('collision', 'model/ModelCatalog.kt',
             'if (models.values.any { it.policy.modelId == policy.modelId }) throw IOException("相同模型ID对应不同内容，请使用新ID")',
             'if (false) throw IOException("相同模型ID对应不同内容，请使用新ID")',
             'same ID different content must not replace good installation')]
        stdlib = os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'
        for label, relative, anchor, replacement, diagnostic in cases:
            source = ROOT / 'android/app/src/org/llmasr/minimal' / relative
            original = source.read_text()
            if original.count(anchor) != 1:
                raise RuntimeError('exact production mutant anchor missing: ' + label)
            mutant = out / (label + '-source') / source.name
            mutant.parent.mkdir()
            mutant.write_text(original.replace(anchor, replacement))
            destination = out / (label + '-classes')
            compiled = run(['bash', 'scripts/compile-jvm-sources.sh', str(destination), str(out / 'classes'), str(mutant)], out, label + '-compile', 180, dependency=out / 'classes')
            if compiled.returncode:
                raise RuntimeError('mutant compile failure not behavior red: ' + compiled.stderr.decode())
            case_root = out / (label + '-fixtures'); case_root.mkdir()
            for fixture_file in fixture.iterdir():
                if fixture_file.is_file():
                    os.link(fixture_file, case_root / fixture_file.name)
            checked = run(['java', '-Xmx256m', '-cp', str(destination) + ':' + str(out / 'classes') + ':' + stdlib, 'ModelVariantsTest', str(case_root)], out, label + '-red', 45, dependency=out / 'classes')
            if checked.returncode != 1 or 'java.lang.AssertionError: ' + diagnostic not in checked.stderr.decode():
                raise RuntimeError('not exact variant production red: ' + label + ': ' + checked.stderr.decode())
            restore_root = out / (label + '-restored-fixtures'); restore_root.mkdir()
            for fixture_file in fixture.iterdir():
                if fixture_file.is_file():
                    os.link(fixture_file, restore_root / fixture_file.name)
            restored = run(['java', '-Xmx256m', '-cp', cp, 'ModelVariantsTest', str(restore_root)], out, label + '-restored', 45, dependency=out / 'classes')
            if restored.returncode:
                raise RuntimeError('unmodified production restore failed: ' + restored.stderr.decode())
            print('PASS variant production mutant compile0/exactAssertion1/restored0 ' + label)
    (out / 'result.json').write_text(json.dumps({'policy_catalog_contracts': 'PASS', 'synthetic_declarations': ['FP16', 'INT8', 'INT4', 'MIXED'], 'actual_native_ASR': 'NOT RUN'}, indent=2) + '\n')


if __name__ == '__main__':
    main()
