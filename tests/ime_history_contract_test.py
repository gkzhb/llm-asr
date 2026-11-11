#!/usr/bin/env python3
"""Real IME history/parallel capture contracts; bounded owned compiler lifecycle, no devices."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import sys
import re
import uuid
import time
import kotlin_next_migration_test as mixed
import mixed_compiler_lifecycle as lifecycle


MARKER = 'IME_HISTORY_EVIDENCE '
SUMMARY_FILES = ('result.json', 'inputs.json', 'driver-inputs.json', 'attempted.json', 'tool-inputs.json')
ROUTES = (('green', 'all', None),
          ('history-limit-red', 'history', 'history newest-first bounded at fifty'),
          ('history-limit-restored', 'all', None),
          ('append-red', 'publication', 'successful current preview must append once'),
          ('append-restored', 'all', None),
          ('release-red', 'parallel', 'early capture must reject release winner'),
          ('release-restored', 'all', None))


def require_protocol(value, message):
    if not value:
        raise ValueError('history provenance: ' + message)


def valid_run_id(value):
    require_protocol(isinstance(value, str) and re.fullmatch(r'[0-9a-f]{32}', value) is not None,
                     'run ID must be 32 lowercase UUID hex digits')
    require_protocol(uuid.UUID(hex=value).hex == value, 'noncanonical run ID')
    return value


def strict_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            require_protocol(key not in result, 'duplicate JSON key ' + key)
            result[key] = value
        return result
    return json.loads(text, object_pairs_hook=pairs,
                      parse_constant=lambda value: require_protocol(False, 'JSON constant ' + value))


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def summary_hashes(out):
    return {name: digest(out / name) for name in SUMMARY_FILES}


def driver_inputs():
    return (Path(__file__).resolve(), mixed.ROOT / 'tests/fixtures/kotlin-next/ImeHistoryContractTest.java',
            mixed.ROOT / 'tests/kotlin_next_migration_test.py',
            mixed.ROOT / 'tests/mixed_compiler_lifecycle.py', mixed.ROOT / 'scripts/compile-jvm-sources.sh',
            mixed.ROOT / 'scripts/test-minimal-apk.sh',
            mixed.ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java',
            mixed.ROOT / 'tests/logs_activity_picker_test.py')


def trusted_tools():
    # Controlled Nix/toolchain environment, never inferred from the audited JVM argv.
    home = Path(os.environ.get('KOTLIN_HOME', ''))
    require_protocol(home.is_absolute(), 'trusted KOTLIN_HOME must be absolute')
    stdlib = (home / 'lib/kotlin-stdlib.jar').resolve(strict=True)
    require_protocol(stdlib.is_file(), 'trusted stdlib missing')
    return {'schema': 1, 'stdlib': mixed.identity(stdlib)}


def trusted_classpath(classes):
    return str(classes) + ':' + trusted_tools()['stdlib']['path']


def formal_summary(out, run_id, argv):
    return {'schema': 1, 'formal_bound': run_id is not None, 'run_id': run_id,
            'directory': str(out), 'mode': 'ordinary' if __debug__ else 'optimized',
            'driver': mixed.identity(Path(__file__).resolve()), 'argv': list(argv),
            'producer': mixed.owned.process_identity(os.getpid()),
            'summary_inputs': {name: digest(out / name) for name in SUMMARY_FILES[1:]}}


def validate_header(out, run_id):
    valid_run_id(run_id)
    require_protocol(out.is_absolute() and out == out.resolve() and out.is_dir(), 'canonical evidence directory')
    result = strict_json((out / 'result.json').read_text())
    p = result.get('provenance', {})
    require_protocol(type(p.get('schema')) is int and p['schema'] == 1 and p.get('formal_bound') is True and p.get('run_id') == run_id,
                     'result run identity')
    require_protocol(p.get('directory') == str(out) and p.get('mode') in ('ordinary', 'optimized')
                     and out.name == p['mode'], 'result path/mode')
    require_protocol(result.get('optimized') is (p['mode'] == 'optimized') and result.get('green_only') is False,
                     'full ordinary/optimized matrix required')
    require_protocol(p.get('driver') == mixed.identity(Path(__file__).resolve()), 'driver identity')
    argv = p.get('argv', [])
    require_protocol(argv == [str(Path(__file__).resolve()), '--evidence-dir', str(out), '--run-id', run_id],
                     'actual formal driver argv')
    producer = p.get('producer', {})
    require_protocol(all(type(producer.get(k)) is int and producer[k] > 0 for k in ('pid', 'parent', 'session', 'start')),
                     'actual producer PID/start identity')
    require_protocol(strict_json((out / 'tool-inputs.json').read_text()) == trusted_tools(),
                     'trusted tool input identity')
    require_protocol(p.get('summary_inputs') == {name: digest(out / name) for name in SUMMARY_FILES[1:]},
                     'summary input hashes')
    return result, p


def validate_inputs(out):
    require_protocol(strict_json((out / 'driver-inputs.json').read_text()) ==
                     [mixed.identity(p) for p in driver_inputs()], 'current driver input closure')
    rows = strict_json((out / 'inputs.json').read_text())
    require_protocol(isinstance(rows, list) and rows, 'input inventory')
    paths = []
    for row in rows:
        path = Path(row['path'])
        require_protocol(path.is_absolute() and path == path.resolve() and path.is_relative_to(out), 'input path')
        require_protocol(mixed.identity(path) == row, 'copied input drift')
        rel = path.relative_to(out)
        if rel.parts[0] == 'production':
            origin = mixed.ROOT / Path(*rel.parts[1:])
            require_protocol(origin.is_file() and digest(origin) == row['sha256'], 'current production input')
        elif rel.parts[0] == 'callers':
            origins = [p for base in (mixed.ROOT / 'tests', mixed.ROOT / 'tests/fixtures/kotlin-next')
                       for p in base.glob(path.name)]
            require_protocol(len(origins) == 1 and digest(origins[0]) == row['sha256'], 'current caller input')
        else:
            require_protocol(rel.parts[0] == 'fakes' and
                             path.read_text() == mixed.stubs().get(str(Path(*rel.parts[1:])), '') + '\n',
                             'current calibrated fake input')
        paths.append(str(path))
    return paths


def validate_routes(out, result, paths):
    attempts = [label + suffix for label, _, _ in ROUTES for suffix in ('-compile', '-run')]
    require_protocol(result.get('attempts') == attempts and strict_json((out / 'attempted.json').read_text()) == attempts,
                     'complete route inventory')
    require_protocol(result.get('compile_budget') == 270 and result.get('run_budget') == 45, 'original budgets')
    for label, mode, diagnostic in ROUTES:
        for suffix in ('compile', 'run'):
            name = label + '-' + suffix
            record = strict_json((out / (name + '.json')).read_text())
            expected = 1 if suffix == 'run' and diagnostic else 0
            require_protocol(record.get('runner') == 'owned-linux-session-v1' and record.get('owned_exited') is True
                             and record.get('root_reaped') is True and record.get('primary') is None
                             and record.get('timeout') is False and type(record.get('exit')) is int
                             and record['exit'] == expected, 'actual terminal ' + name)
            for stream in ('stdout', 'stderr'):
                require_protocol(digest(out / (name + '.' + stream)) == record.get(stream + '_sha256'),
                                 'raw output SHA ' + name)
            identity = record.get('root_identity', {})
            producer = result['provenance']['producer']
            require_protocol(identity.get('parent') == producer['pid']
                             and type(identity.get('start')) is int and identity['start'] >= producer['start'],
                             'direct driver-owned route PID/start ' + name)
            argv = record.get('argv', [])
            classes = str(out / ('classes-' + label))
            if suffix == 'compile':
                require_protocol(argv == ['bash', str(mixed.ROOT / 'scripts/compile-jvm-sources.sh'), classes, '', *paths],
                                 'compile argv ' + name)
            else:
                require_protocol(len(argv) == 6 and argv[:3] == ['java', '-Xmx256m', '-cp']
                                 and argv[3] == trusted_classpath(Path(classes))
                                 and argv[4:] == ['ImeHistoryContractTest', mode], 'JVM argv ' + name)
                output = (out / (name + ('.stderr' if diagnostic else '.stdout'))).read_bytes()
                needle = ('java.lang.AssertionError: ' + diagnostic).encode() if diagnostic else b'PASS IME history'
                require_protocol(needle in output, 'exact behavioral oracle ' + name)


def evidence_marker(out, run_id):
    result, p = validate_header(out, run_id)
    validate_routes(out, result, validate_inputs(out))
    return {'schema': 1, 'family': 'ime-history', 'mode': p['mode'], 'run_id': run_id,
            'directory': str(out), 'hashes': summary_hashes(out)}


def bind_markers(raw_log, run_id, allowed_root):
    valid_run_id(run_id)
    found = []
    for line in raw_log.splitlines():
        if line.startswith(MARKER):
            marker = strict_json(line[len(MARKER):])
            require_protocol(isinstance(marker, dict) and set(marker) ==
                             {'schema', 'family', 'mode', 'run_id', 'directory', 'hashes'}, 'marker schema keys')
            require_protocol(type(marker['schema']) is int and marker['schema'] == 1 and marker['family'] == 'ime-history'
                             and marker['run_id'] == run_id and marker['mode'] in ('ordinary', 'optimized'),
                             'marker family/run/mode')
            out = Path(marker['directory'])
            require_protocol(out.is_absolute() and out == out.resolve() and out.is_relative_to(allowed_root.resolve()),
                             'marker allowed canonical path')
            require_protocol(marker == evidence_marker(out, run_id), 'marker summary/path mismatch')
            found.append(marker)
    require_protocol(len(found) == 2 and {x['mode'] for x in found} == {'ordinary', 'optimized'}
                     and len({x['directory'] for x in found}) == 2, 'exactly one marker per mode')
    return found


def suite(out, green_only=False, incoming=None, run_id=None):
    if run_id is not None:
        valid_run_id(run_id)
        require_protocol(not green_only and incoming is None, "formal full matrix only")
    mixed.require(not out.exists(), 'fresh evidence directory required')
    out.mkdir(parents=True)
    started = time.monotonic()
    mixed.owned.write_json(out / 'tool-inputs.json', trusted_tools())
    sources = mixed.prepare(out)
    fixture = mixed.ROOT / 'tests/fixtures/kotlin-next/ImeHistoryContractTest.java'
    target = out / 'callers' / fixture.name
    target.write_bytes(fixture.read_bytes())
    sources.append(target)
    mixed.owned.write_json(out / 'inputs.json', [mixed.identity(p) for p in sources])
    mixed.owned.write_json(out / 'driver-inputs.json', [mixed.identity(p) for p in driver_inputs()])
    attempts = []

    def execute(argv, label, budget):
        attempts.append(label)
        mixed.owned.write_json(out / 'attempted.json', attempts)
        return lifecycle.run(argv, out, label, budget)

    def quiescent():
        for name in attempts:
            try:
                record = json.loads((out / (name + '.json')).read_text())
            except (OSError, ValueError):
                return False
            if (not isinstance(record, dict) or record.get('owned_exited') is not True
                    or record.get('root_reaped') is not True or type(record.get('exit')) is not int):
                return False
        return True

    def route(label, mode='all', diagnostic=None):
        classes = out / ('classes-' + label)
        result = execute(['bash', str(mixed.ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '',
                          *map(str, sources)], label + '-compile', 270)
        mixed.require(result.returncode == 0, 'compile/tool failure NOT target red\n' + result.stderr.decode())
        cp = trusted_classpath(classes)
        result = execute(['java', '-Xmx256m', '-cp', cp, 'ImeHistoryContractTest', mode], label + '-run', 45)
        if diagnostic:
            mixed.require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic).encode() in result.stderr,
                          'not exact behavioral red\n' + result.stderr.decode())
        else:
            mixed.require(result.returncode == 0 and b'PASS IME history' in result.stdout,
                          'history production green failed\n' + result.stderr.decode())

    mutants = [
        ('history-limit', 'ImeHistoryStore', 'public static final int LIMIT = 50;', 'public static final int LIMIT = 51;',
         'history', 'history newest-first bounded at fifty'),
        ('append', 'ImeController', 'if (session.preview.isNotEmpty()) history?.append(session.preview)',
         'if (false) history?.append(session.preview)', 'publication', 'successful current preview must append once'),
        ('release', 'ModelResidency', 'if (epoch != releaseEpoch || pending || maintenance != 0 || state == State.ACTIVE || state == State.EVICTING)',
         'if (maintenance != 0 || state == State.ACTIVE || state == State.EVICTING)', 'parallel', 'early capture must reject release winner'),
    ]
    primary = None
    try:
        if incoming is not None:
            # R1 is a compiler regression, not a behavioral mutant. Copy exact unchanged callers.
            controller, = [p for p in sources if p.stem == 'ImeController']
            original = controller.read_bytes()
            old = incoming / 'android/app/src/org/llmasr/minimal/ime/ImeController.kt'
            caller_sources = []
            for name in ('FakePorts', 'FixtureChecks'):
                source = mixed.ROOT / ('android/test-host/fixtures/java/org/llmasr/minimal/testhost/' + name + '.kt')
                copy = out / 'callers' / source.name; copy.write_bytes(source.read_bytes()); caller_sources.append(copy)
            mixed.owned.write_json(out / 'unchanged-kotlin-callers.json', [mixed.identity(p) for p in caller_sources])
            compat = out / 'callers/LegacyImeConstructors.kt'
            compat.write_text('import org.llmasr.minimal.ime.ImeController\nimport org.llmasr.minimal.task.TaskCoordinator\nimport org.llmasr.minimal.testhost.FakePorts\nobject LegacyImeConstructors { @JvmStatic fun main(args: Array<String>) { val queue=FakePorts.Queue(); val backend=FakePorts.Backend(); val c = ImeController(TaskCoordinator(queue), backend) {}; check(c.begin(1,0,"compat") != null); org.llmasr.minimal.testhost.FixtureChecks.readinessRejectsBeforeLoad(); org.llmasr.minimal.testhost.FixtureChecks.singleConfirmationAndPassword(); org.llmasr.minimal.testhost.FixtureChecks.lateResultIsInvalidated(); org.llmasr.minimal.testhost.FixtureChecks.reusedBackendQueuedCancellation(); org.llmasr.minimal.testhost.FixtureChecks.cleanupRetainsOwner(); println("PASS exact legacy Kotlin constructor consumer") } }\n')
            caller_sources.append(compat)
            mixed.owned.write_json(out / 'legacy-compat-inputs.json', [mixed.identity(p) for p in caller_sources] +
                [mixed.identity(old), mixed.identity(mixed.ROOT / 'android/test-host/src/androidTest/kotlin/org/llmasr/minimal/testhost/FixtureInstrumentationTest.kt')])
            try:
                controller.write_bytes(old.read_bytes())
                result = execute(['bash', str(mixed.ROOT / 'scripts/compile-jvm-sources.sh'), str(out / 'classes-compiler-red'), '', *map(str, sources + caller_sources)], 'legacy-compiler-red', 270)
                mixed.require(result.returncode != 0 and b'FixtureChecks.kt' in result.stderr and b'ImeHistoryStore' in result.stderr,
                              'not exact legacy Kotlin source-compat compiler regression\n' + result.stderr.decode())
            finally:
                lifecycle.guard(out); mixed.require(quiescent(), 'unknown compiler exit retains incoming constructor'); controller.write_bytes(original)
            result = execute(['bash', str(mixed.ROOT / 'scripts/compile-jvm-sources.sh'), str(out / 'classes-compat-green'), '', *map(str, sources + caller_sources)], 'legacy-compile-green', 270)
            mixed.require(result.returncode == 0, 'unchanged old Kotlin consumers must compile\n' + result.stderr.decode())
            cp = trusted_classpath(out / 'classes-compat-green')
            result = execute(['java', '-cp', cp, 'LegacyImeConstructors'], 'legacy-consumer-run', 45)
            mixed.require(result.returncode == 0 and b'PASS exact legacy' in result.stdout, 'legacy Kotlin constructor run')
            for owner, relative, mode, diagnostic in [
                ('ImeHistoryStore', 'android/app/src/org/llmasr/minimal/ime/ImeHistoryStore.java', 'orphans', 'restart must remove owned orphan text before history use'),
                ('AsrImeService', 'android/app/src/org/llmasr/minimal/AsrImeService.kt', 'history-ui', 'closed history outer scroll must not reserve blank space')]:
                candidate, = [p for p in sources if p.stem == owner]
                original = candidate.read_bytes()
                try:
                    candidate.write_bytes((incoming / relative).read_bytes())
                    route(owner + '-incoming-red', mode, diagnostic)
                finally:
                    lifecycle.guard(out); mixed.require(quiescent(), 'unknown owned exit retains incoming source'); candidate.write_bytes(original)
                route(owner + '-repaired', mode)
            print('PASS exact unchanged Kotlin compiler regression red/green and orphan/UI incoming compile0 behavioral red1/repaired0', flush=True)
        route('green')
        if not green_only:
            for name, owner, before, after, mode, diagnostic in mutants:
                candidate, = [p for p in sources if p.stem == owner and p.suffix in ('.java', '.kt')]
                original = candidate.read_bytes()
                mixed.require(original.decode().count(before) == 1, 'unique mutant anchor ' + name)
                mixed.owned.write_json(out / (name + '-mutation.json'), {'owner': str(candidate),
                    'before_sha256': hashlib.sha256(original).hexdigest(), 'before': before, 'after': after,
                    'diagnostic': diagnostic, 'mode': mode})
                mutation_failure = None
                try:
                    candidate.write_text(original.decode().replace(before, after, 1))
                    route(name + '-red', mode, diagnostic)
                except BaseException as failure:
                    mutation_failure = failure
                    raise
                finally:
                    try:
                        lifecycle.guard(out)
                        mixed.require(quiescent(), 'unknown owned exit retains exact mutant inputs')
                        candidate.write_bytes(original)
                    except BaseException as failure:
                        if mutation_failure is None:
                            raise
                        mutation_failure.add_note('restoration refused: ' + repr(failure))
                route(name + '-restored')
    except BaseException as failure:
        primary = failure
        raise
    finally:
        try:
            mixed.owned.cleanup_workspace(out, probe=quiescent, remove=False)
        except BaseException as failure:
            if primary is None:
                raise
            primary.add_note('cleanup refused: ' + repr(failure))
    # A successful summary must never survive a failed owned cleanup/exit check.
    mixed.owned.write_json(out / 'result.json', {'elapsed_seconds': time.monotonic() - started,
        'attempts': attempts, 'compile_budget': 270, 'run_budget': 45, 'green_only': green_only,
        'optimized': not __debug__, 'device': 'NOT RUN',
        'provenance': formal_summary(out, run_id, sys.argv)})
    print('PASS history/parallel capture production compile0/exact-red/restored-green evidence', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--green-only', action='store_true')
    parser.add_argument('--run-id', help='optional dedicated formal launch UUID hex; absent means not formal bound')
    parser.add_argument('--emit-marker', action='store_true', help='validate completed formal evidence and emit host marker only')
    parser.add_argument('--incoming', type=Path, help='exact preserved incoming repair source tree; compiler and behavior replay')
    args = parser.parse_args()
    signal.signal(signal.SIGTERM, mixed.owned.interrupted_by_signal)
    if args.emit_marker:
        require_protocol(args.run_id is not None and not args.green_only and args.incoming is None, 'marker requires formal run ID only')
        print(MARKER + json.dumps(evidence_marker(args.evidence_dir.resolve(), args.run_id), sort_keys=True, separators=(',', ':')))
    else:
        suite(args.evidence_dir.resolve(), args.green_only, args.incoming.resolve() if args.incoming else None, args.run_id)
