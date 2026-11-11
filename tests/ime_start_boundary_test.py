#!/usr/bin/env python3
"""Actual IME hardware-start arbitration; host platform fakes, never microphone/device acceptance."""
import argparse
import json
import os
from pathlib import Path
import signal
import kotlin_next_migration_test as mixed


def suite(directory):
    directory.mkdir(parents=True, exist_ok=False)
    sources = mixed.prepare(directory)
    attempted = []
    (directory / 'inputs.json').write_text(json.dumps([mixed.identity(p) for p in sources], indent=2) + '\n')
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'

    def execute(argv, label, timeout):
        attempted.append(directory / (label + '.json'))
        (directory / 'attempted.json').write_text(json.dumps([str(p) for p in attempted], indent=2) + '\n')
        return mixed.owned.execute(argv, directory, label, timeout)

    def quiescent():
        for p in attempted:
            try:
                row = json.loads(p.read_text())
            except (OSError, ValueError):
                return False
            if not isinstance(row, dict) or row.get('owned_exited') is not True or row.get('root_reaped') is not True:
                return False
        return True

    def compile_run(label, red_mode=None, diagnostic=None):
        classes = directory / ('classes-' + label)
        result = execute(['bash', str(mixed.ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '', *map(str, sources)], label + '-compile', 270)
        mixed.require(result.returncode == 0, 'compile/tool failure NOT target red ' + result.stderr.decode())
        modes = [red_mode] if red_mode else ['start-intent', 'start-resident', 'start-protocol']
        for mode in modes:
            result = execute(['java', '-cp', str(classes) + ':' + str(stdlib), 'NextAppContractTest', mode], label + '-' + mode, 45)
            if diagnostic:
                mixed.require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic).encode() in result.stderr,
                              'not exact start-boundary red ' + result.stderr.decode())
            else:
                mixed.require(result.returncode == 0, 'start-boundary green failed ' + result.stderr.decode())

    mutants = [
        ('intent', 'AsrImeService',
         'if (service == null || service.session !== expected || !service.eligible() ||\n                    !service.isInputViewShown || !service.permitted()) return false',
         'if (service == null) return false', 'start-intent', 'R1 post-gate invalidation must reject actual microphone start'),
        ('resident', 'ModelResidency',
         'if (epoch != releaseEpoch || pending || maintenance != 0 || state == State.ACTIVE || state == State.EVICTING)',
         'if (maintenance != 0 || state == State.ACTIVE || state == State.EVICTING)',
         'start-resident', 'R2 pending release must reject actual microphone start'),
    ]
    primary = None
    try:
        compile_run('green')
        for name, owner, before, after, mode, diagnostic in mutants:
            target, = [p for p in sources if p.stem == owner and p.suffix == '.kt']
            original = target.read_bytes()
            mixed.require(original.decode().count(before) == 1, 'unique mutation anchor ' + name)
            mutant_primary = None
            try:
                target.write_text(original.decode().replace(before, after, 1))
                compile_run(name + '-red', mode, diagnostic)
            except BaseException as failure:
                mutant_primary = failure
                raise
            finally:
                try:
                    if quiescent():
                        target.write_bytes(original)
                except BaseException as restore_failure:
                    if mutant_primary is None:
                        raise
                    mutant_primary.add_note('restoration refused: ' + repr(restore_failure))
            mixed.require(target.read_bytes() == original, 'restore refused ' + name)
            compile_run(name + '-restored')
            print('PASS actual start production mutant compile0/exact assertion1/restored0 ' + name, flush=True)
        mixed.require(quiescent(), 'unconfirmed owned exit')
        (directory / 'result.json').write_text(json.dumps({'pass': True, 'compiles': 5, 'runs': 11,
            'compile_timeout': 270, 'jvm_timeout': 45, 'driver': mixed.identity(Path(__file__)),
            'caller': mixed.identity(mixed.ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java'), 'not_android': True}, indent=2) + '\n')
    except BaseException as failure:
        primary = failure
        raise
    finally:
        try:
            mixed.owned.cleanup_workspace(directory, probe=quiescent, remove=False)
        except BaseException as cleanup_failure:
            if primary is None:
                raise
            primary.add_note('cleanup refused: ' + repr(cleanup_failure))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    signal.signal(signal.SIGTERM, mixed.owned.interrupted_by_signal)
    suite(args.evidence_dir.resolve())
