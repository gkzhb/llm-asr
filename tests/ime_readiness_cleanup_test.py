#!/usr/bin/env python3
"""Bounded faults through the real readiness suite; fake tools, not compile/Android evidence."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tests/kotlin_next_migration_test.py'


def require(value, message):
    if not value:
        raise AssertionError(message)


def driver(source):
    # Alternate frozen bytes execute unchanged with the real module location/imports.
    spec = importlib.util.spec_from_file_location('_ime_cleanup_driver', source)
    module = importlib.util.module_from_spec(spec)
    module.__file__ = str(SOURCE)
    spec.loader.exec_module(module)
    return module


class Fatal(BaseException):
    pass


def case(module, *, primary=None, ledger='valid', restore_failure=None, probe_failure=None,
         fail_mutant_write=False, fail_at_run=False):
    with tempfile.TemporaryDirectory(prefix='ime-ready-cleanup-') as temporary:
        root = Path(temporary)
        stdlib = root / 'lib/kotlin-stdlib.jar'
        stdlib.parent.mkdir(); stdlib.write_bytes(b'fake-stdlib')
        target = root / 'evidence/AsrImeService.kt'
        original = (b' && !busy && wired)\n'
                    b'service.isInputViewShown && service.permitted()\n'
                    b'else if (busy && controller!!.ownsTask()) session!!.status()\n')
        calls = []
        active = False
        restored = 0
        real_write = Path.write_bytes
        real_read = Path.read_text
        real_write_text = Path.write_text
        bad = {'missing': None, 'json': '{', 'list': '[]', 'null': 'null',
               'wrong-bool': json.dumps({'runner': 'owned-linux-session-v1', 'exit': 0,
                                        'owned_exited': 1, 'root_reaped': True, 'primary': None}),
               'incomplete': json.dumps({'owned_exited': True, 'root_reaped': True}),
               'unknown-exit': json.dumps({'runner': 'owned-linux-session-v1', 'exit': None,
                                          'owned_exited': True, 'root_reaped': True, 'primary': None}),
               'false-exit': json.dumps({'runner': 'owned-linux-session-v1', 'exit': False,
                                        'owned_exited': True, 'root_reaped': True, 'primary': None}),
               'not-exited': json.dumps({'runner': 'owned-linux-session-v1', 'exit': 0,
                                        'owned_exited': False, 'root_reaped': True, 'primary': None}),
               'wrong-primary': json.dumps({'runner': 'owned-linux-session-v1', 'exit': 0,
                                           'owned_exited': True, 'root_reaped': True, 'primary': []}),
               'wrong-runner': json.dumps({'runner': 'unexpected', 'exit': 0,
                                          'owned_exited': True, 'root_reaped': True, 'primary': None})}

        def prepare(directory):
            real_write(target, original)
            return [target]

        def execute(argv, directory, label, timeout):
            nonlocal active
            calls.append((label, timeout))
            record = directory / (label + '.json')
            record.write_text(json.dumps({'runner': 'owned-linux-session-v1', 'exit': 0,
                                         'owned_exited': True, 'root_reaped': True, 'primary': None}))
            if label == 'proof-only-render-red-compile':
                active = True
                if ledger != 'valid':
                    if bad[ledger] is None:
                        record.unlink()
                    else:
                        record.write_text(bad[ledger])
                if primary is not None and not fail_at_run:
                    raise primary
            if label == 'proof-only-render-red-run' and primary is not None and fail_at_run:
                raise primary
            diagnostic = {'proof-only-render': 'first-use record must be enabled without proof or resident',
                          'stale-click-admission': 'permission change during prepare must prevent capture',
                          'own-busy-priority': 'owned IME task must retain session progress while resident active'}
            red = label.endswith('-red-run')
            errors = ('java.lang.AssertionError: ' + diagnostic[label[:-8]]).encode() if red else b''
            return subprocess.CompletedProcess(argv, 1 if red else 0,
                b'PASS next real app/controller host contracts ime-model-ready', errors)

        def write(path, data):
            nonlocal restored
            if path == target and data == original and active:
                restored += 1
                if restore_failure is not None:
                    raise restore_failure
            return real_write(path, data)

        def write_text(path, data, *args, **kwargs):
            nonlocal active
            result = real_write_text(path, data, *args, **kwargs)
            if path == target and fail_mutant_write:
                active = True
                raise primary
            return result

        def read(path, *args, **kwargs):
            if active and probe_failure is not None and path.name == 'proof-only-render-red-compile.json':
                raise probe_failure
            return real_read(path, *args, **kwargs)

        def cleanup(directory, *, probe, remove):
            require(remove is False, 'fault evidence must not be removed')
            if not probe():
                raise OSError('controlled unconfirmed cleanup')

        seen = None
        with patch.object(module, 'prepare', prepare), patch.object(module.owned, 'execute', execute), \
                patch.object(module.owned, 'cleanup_workspace', cleanup), \
                patch.dict(os.environ, {'KOTLIN_HOME': str(root)}), \
                patch.object(module.shutil, 'which', return_value=sys.executable), \
                patch.object(Path, 'write_bytes', write), patch.object(Path, 'read_text', read), \
                patch.object(Path, 'write_text', write_text):
            try:
                module.ime_model_ready_suite(root / 'evidence')
            except BaseException as failure:
                seen = failure
        return {'seen': seen, 'restored': restored, 'bytes': target.read_bytes(),
                'original': original, 'calls': calls}


def contracts(module):
    for failure in (Fatal('original fatal'), KeyboardInterrupt('original cancel'), OSError('original IO')):
        secondary = Fatal('restore failed')
        result = case(module, primary=failure, restore_failure=secondary)
        require(result['seen'] is failure, 'readiness restoration must preserve original BaseException identity')
        require(any('restore failed' in note for note in getattr(failure, '__notes__', [])),
                'restore secondary must be noted on original primary')
    original = KeyboardInterrupt('mutant JVM primary')
    result = case(module, primary=original, restore_failure=Fatal('restore after JVM'),
                  fail_at_run=True)
    require(result['seen'] is original, 'mutant JVM failure must preserve original primary')
    require(any('restore after JVM' in note for note in getattr(original, '__notes__', [])),
            'mutant JVM restoration failure must be noted')
    original = Fatal('mutant write primary')
    result = case(module, primary=original, restore_failure=Fatal('restore after write'),
                  fail_mutant_write=True)
    require(result['seen'] is original, 'mutant write failure must preserve original primary')
    require(any('restore after write' in note for note in getattr(original, '__notes__', [])),
            'mutant write restoration failure must be noted')
    original = Fatal('probe primary')
    result = case(module, primary=original, probe_failure=Fatal('probe failed'))
    require(result['seen'] is original, 'quiescent failure must preserve original BaseException identity')
    require(any('probe failed' in note for note in getattr(original, '__notes__', [])),
            'quiescent secondary must be noted on original')
    for kind in ('missing', 'json', 'list', 'null', 'wrong-bool', 'incomplete',
                 'unknown-exit', 'false-exit', 'not-exited', 'wrong-primary', 'wrong-runner'):
        original = KeyboardInterrupt('ledger primary ' + kind)
        result = case(module, primary=original, ledger=kind)
        require(result['seen'] is original, 'invalid ledger must preserve original primary: ' + kind)
        require(result['restored'] == 0 and result['bytes'] != result['original'],
                'invalid ledger must retain exact mutant inputs: ' + kind)
        require(any('cleanup refused' in note for note in getattr(original, '__notes__', [])),
                'outer cleanup failure must be noted on same primary: ' + kind)
    secondary = Fatal('restore without primary')
    result = case(module, restore_failure=secondary)
    require(result['seen'] is secondary, 'restore failure without primary must propagate itself')
    result = case(module)
    require(result['seen'] is None and result['bytes'] == result['original'] and result['restored'] == 3,
            'quiescent true must restore all three mutant originals')
    require(len(result['calls']) == 14 and sum(label.endswith('-compile') for label, _ in result['calls']) == 7,
            'successful readiness route must remain seven compiles/seven JVMs')
    require(all(timeout == (270 if label.endswith('-compile') else 45) for label, timeout in result['calls']),
            'readiness child budgets changed')
    print('PASS real readiness cleanup fault identity/schema/restore contracts (fake tools only)')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--driver-source', type=Path, default=SOURCE)
    args = parser.parse_args()
    contracts(driver(args.driver_source))


if __name__ == '__main__':
    main()
