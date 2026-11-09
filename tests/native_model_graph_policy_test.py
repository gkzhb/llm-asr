#!/usr/bin/env python3
"""Compile the actual graph guard with pinned cached schema, never run an ASR model."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tests'))
from mixed_compiler_lifecycle import run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--admit-existing', action='store_true', help='explicit authorized four graphs only; paired weight stat, no inference')
    args = parser.parse_args()
    out = args.evidence_dir.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = ROOT / '.work/native-session-reuse/sources/mnn'
    paths = [ROOT / 'native/apk/model_graph_policy.h', ROOT / 'tests/native_model_graph_policy_probe.cpp', Path(__file__)]
    paths += sorted((source / 'schema/current').glob('*.h'))
    paths += sorted((source / '3rd_party/flatbuffers/include/flatbuffers').glob('*.h'))
    (out / 'inputs.json').write_text(json.dumps({str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}, indent=2) + '\n')
    command = ['c++', '-std=c++17', '-O1', '-Wall', '-Wextra', '-Werror',
               '-I' + str(ROOT / 'native/apk'), '-I' + str(source / 'schema/current'),
               '-I' + str(source / '3rd_party/flatbuffers/include'),
               str(ROOT / 'tests/native_model_graph_policy_probe.cpp'), '-o', str(out / 'probe')]
    result = run(command, out, 'compile', 120)
    if result.returncode:
        raise RuntimeError('guard compiler failure, not behavior red: ' + result.stderr.decode())
    result = run([str(out / 'probe')], out, 'tiny', 30)
    if result.returncode:
        raise RuntimeError('tiny guard probe failed: ' + result.stderr.decode())
    print(result.stdout.decode(), end='')
    if args.admit_existing:
        for folder in ['QwenASR', 'models/mnn-v1v-entry-fp16']:
            for name in ['audio.mnn', 'llm.mnn']:
                result = run([str(out / 'probe'), '--admit', str(ROOT / folder), name], out,
                             folder.replace('/', '-') + '-' + name, 30)
                print(result.stdout.decode(), end='')
                if result.returncode:
                    raise RuntimeError('existing graph not admitted: ' + folder + '/' + name + ': ' + result.stderr.decode())
    (out / 'result.json').write_text(json.dumps({'tiny_production_guard': 'PASS', 'existing_four_graphs': args.admit_existing,
        'paired_weight_contents_read': False, 'ASR_inference': 'NOT RUN'}, indent=2) + '\n')


if __name__ == '__main__':
    main()
