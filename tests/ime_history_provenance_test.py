#!/usr/bin/env python3
"""Finite tests of the real history protocol; synthetic process checks are not ASR evidence."""
import argparse
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import ime_history_contract_test as protocol


def reject(call, label):
    try:
        call()
    except (ValueError, OSError, KeyError, TypeError):
        return
    raise RuntimeError('history provenance accepted negative: ' + label)


def basic():
    for value in (None, '', 'x' * 32, 'A' * 32, '0' * 31, '0' * 33):
        reject(lambda: protocol.valid_run_id(value), 'invalid UUID')
    run_id = protocol.valid_run_id('0123456789abcdef0123456789abcdef')
    reject(lambda: protocol.strict_json('{"schema":1,"schema":1}'), 'duplicate key')
    reject(lambda: protocol.strict_json('{"value":NaN}'), 'nonfinite JSON')
    with tempfile.TemporaryDirectory(prefix='history-protocol-') as temporary:
        root = Path(temporary)
        for text in ['', protocol.MARKER + '{}', protocol.MARKER + '{"schema":1,"schema":1}',
                     protocol.MARKER + json.dumps({'schema': 1, 'family': 'wrong', 'mode': 'ordinary',
                                                  'run_id': run_id, 'directory': str(root), 'hashes': {}})]:
            reject(lambda: protocol.bind_markers(text, run_id, root), 'incomplete marker')
        # Real nested independent owned sessions. These are ordinary tiny process producers,
        # not fabricated history compiler/JVM ledgers or a substitute for the actual matrix.
        child = root / 'nested.py'
        child.write_text('import sys,json,os\nfrom pathlib import Path\n'
                         'sys.path.insert(0,sys.argv[1])\nimport mixed_compiler_lifecycle as life\n'
                         'work=Path(sys.argv[2])\n'
                         'r=life.run([sys.executable,"-c","print(\\\"synthetic nested child\\\")"],work,"inner",5)\n'
                         'raise SystemExit(r.returncode)\n')
        life = protocol.lifecycle
        result = life.run([sys.executable, str(child), str(protocol.mixed.ROOT / 'tests'), str(root / 'inner')],
                          root, 'outer', 10)
        outer = protocol.strict_json((root / 'outer.json').read_text())
        inner = protocol.strict_json((root / 'inner/inner.json').read_text())
        if (result.returncode != 0 or not outer['owned_exited'] or not inner['owned_exited']
                or not outer['root_reaped'] or not inner['root_reaped']
                or outer['root_identity']['session'] == inner['root_identity']['session']):
            raise RuntimeError('real nested owned session check failed')
    print('PASS finite history protocol parsing and real nested setsid sessions (not history behavior)')


def integration(log, run_id, allowed_root):
    raw = log.read_text()
    # Simultaneous same-input producers have independent owned sessions and a different
    # launch ID. Their unreferenced directories must never be scanned or selected.
    with tempfile.TemporaryDirectory(prefix='history-decoy-', dir=allowed_root) as temporary:
        decoy = Path(temporary)
        child = decoy / 'producer.py'
        child.write_text('import sys,json,os,time\nfrom pathlib import Path\n'
                         'sys.path.insert(0,sys.argv[1])\nimport mixed_compiler_lifecycle as life\n'
                         'root=Path(sys.argv[2]); root.mkdir(); root.joinpath("input").write_bytes(b"same synthetic input")\n'
                         'r=life.run([sys.executable,"-c","print(123)"],root,"producer",5)\n'
                         'root.joinpath("summary.json").write_text(json.dumps({"run_id":sys.argv[3],"input":"same synthetic input"}))\n'
                         'raise SystemExit(r.returncode)\n')
        workers = [subprocess.Popen([sys.executable, str(child), str(protocol.mixed.ROOT / 'tests'),
                                    str(decoy / name), 'f' * 32], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                   for name in ('one', 'two')]
        try:
            markers = protocol.bind_markers(raw, run_id, allowed_root)
            for worker in workers:
                stdout, stderr = worker.communicate(timeout=15)
                if worker.returncode:
                    raise RuntimeError('decoy process failed: ' + stderr.decode())
            if any(Path(x['directory']).is_relative_to(decoy) for x in markers):
                raise RuntimeError('unreferenced decoy selected')
        finally:
            for worker in workers:
                if worker.poll() is None:
                    worker.terminate()
                    worker.communicate(timeout=10)
    negatives = 0

    def denied(text, label):
        nonlocal negatives
        reject(lambda: protocol.bind_markers(text, run_id, allowed_root), label)
        negatives += 1

    def lines(values):
        return '\n'.join(protocol.MARKER + json.dumps(x) for x in values)

    denied('', 'missing marker')
    denied(lines(markers + markers[:1]), 'duplicate mode marker')
    for key, value in [('run_id', 'f' * 32), ('family', 'other'), ('mode', 'wrong'),
                       ('schema', 2), ('schema', True), ('directory', str(allowed_root / 'missing/ordinary'))]:
        changed = copy.deepcopy(markers)
        changed[0][key] = value
        denied(lines(changed), 'wrong marker ' + key)
    out = Path(markers[0]['directory'])
    for filename, mutate in [
            ('result.json', lambda x: x['provenance'].__setitem__('run_id', 'f' * 32)),
            ('result.json', lambda x: x['provenance'].__setitem__('directory', str(out.parent / 'decoy/ordinary'))),
            ('result.json', lambda x: x['provenance'].__setitem__('driver', {})),
            ('result.json', lambda x: x.__setitem__('attempts', [])),
            ('inputs.json', lambda x: x[0].__setitem__('sha256', '0' * 64)),
            ('driver-inputs.json', lambda x: x[0].__setitem__('sha256', '0' * 64)),
            ('tool-inputs.json', lambda x: x['stdlib'].__setitem__('sha256', '0' * 64)),
            ('tool-inputs.json', lambda x: x['stdlib'].__setitem__('path', '/synthetic/wrong-stdlib.jar')),
            ('green-compile.json', lambda x: x.__setitem__('owned_exited', False)),
            ('green-run.json', lambda x: x.__setitem__('root_reaped', False)),
            ('green-run.json', lambda x: x.__setitem__('argv', ['java', 'wrong-path'])),
            ('green-run.json', lambda x: x['root_identity'].__setitem__('parent', 1)),
            ('green-run.json', lambda x: x.__setitem__('timeout', True)),
            ('green-run.json', lambda x: x['root_identity'].__setitem__('start', 0))]:
        path = out / filename
        original = path.read_bytes()
        try:
            record = protocol.strict_json(original.decode())
            mutate(record)
            path.write_text(json.dumps(record))
            # Summary and marker hashes are intentionally refreshed for input negatives:
            # rejection must still arise from real closure/terminal/oracle validation.
            result = out / 'result.json'
            result_original = result.read_bytes()
            if filename in protocol.SUMMARY_FILES[1:]:
                data = protocol.strict_json(result.read_text())
                data['provenance']['summary_inputs'][filename] = protocol.digest(path)
                result.write_text(json.dumps(data))
            changed = copy.deepcopy(markers)
            changed[0]['hashes'] = protocol.summary_hashes(out)
            try:
                denied(lines(changed), filename + ' mutated')
            finally:
                if filename != 'result.json':
                    result.write_bytes(result_original)
        finally:
            path.write_bytes(original)
    ledger_path = out / 'green-run.json'
    original = ledger_path.read_bytes()
    record = protocol.strict_json(original.decode())
    classes, stdlib = record['argv'][3].split(':')
    for invalid in [classes + ':/synthetic/old-classes:' + stdlib,
                    classes + ':/synthetic/wrong-stdlib.jar',
                    classes + ':' + stdlib + ':', classes + '::' + stdlib,
                    classes + ':' + stdlib + ':/synthetic/old-classes',
                    classes + ':lib/kotlin-stdlib.jar', classes + ':']:
        try:
            record['argv'][3] = invalid
            ledger_path.write_text(json.dumps(record))
            denied(lines(markers), 'complete two-entry trusted classpath')
        finally:
            ledger_path.write_bytes(original)
    for filename in ['result.json', 'inputs.json', 'attempted.json', 'tool-inputs.json', 'green-run.json']:
        path = out / filename
        backup = path.with_name(path.name + '.test-backup')
        path.rename(backup)
        try:
            denied(lines(markers), 'missing ' + filename)
        finally:
            backup.rename(path)
    raw_path = out / 'green-run.stdout'
    original = raw_path.read_bytes()
    try:
        raw_path.write_bytes(original + b'synthetic drift')
        denied(lines(markers), 'raw output drift')
    finally:
        raw_path.write_bytes(original)
    protocol.bind_markers(raw, run_id, allowed_root)
    print('PASS real history binding: two exact named matrices, ' + str(negatives) + ' strict negatives/restored')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log', type=Path)
    parser.add_argument('--run-id')
    parser.add_argument('--allowed-root', type=Path)
    args = parser.parse_args()
    if args.log:
        if not args.run_id or not args.allowed_root:
            parser.error('integration requires run ID and allowed root')
        integration(args.log, args.run_id, args.allowed_root)
    else:
        basic()
