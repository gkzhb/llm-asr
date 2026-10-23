#!/usr/bin/env python3
"""Narrow migrated-test source/JUnit discovery guard and real production mutants.

No Android execution. XML is actual runner evidence only when its owner executed
fresh Test tasks; synthetic self-tests prove only this checker's refusals.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BODIES = ('MinimalApkTest', 'ResultFilesTest', 'NativeResponseTest',
          'ModelOperationControlTest', 'ModelRepositoryDeleteTest', 'ModelPrivateRootTest',
          'TextExportFatalTest', 'TextExportSafetyTest')
CORE = ('kotlinJavaAbi', 'waveAndRecordingProtocol', 'recordingRaces', 'resultFiles',
        'nativeResponseProtocol', 'modelRepository', 'privateModelRoot', 'partRecoveryProductionMutant',
        'requestRunner', 'taskCoordinator', 'admissionOwnerBoundary', 'imeSessionPrivacy',
        'modelReadiness', 'modelOperationControl', 'modelCancelCleanup', 'modelWhitelistDeletion',
        'modelManagementController', 'modelPageHelpers', 'runtimeLogCore', 'runtimeLogErrorIdentity',
        'logExportGenerations', 'textExport', 'textExportSafety', 'textExportFatalIdentity',
        'modelNotifications', 'providerErrorAndStreamBoundary', 'reviewHistory', 'reviewTerminal',
        'reviewWindow', 'reviewBoundary', 'reviewDeletion', 'reviewLiveDeletion', 'reviewPlanning',
        'reviewPublication')
SUITES = {'core': ('CoreRegressionJUnitTest', CORE, 'core/build/test-results/test'),
          'runtime': ('RuntimeRegressionJUnitTest', ('inferenceAdapterAndJniConstruction',),
                      'android/asr-runtime/build/test-results/testDebugUnitTest')}
WRAPPERS = ('core/src/test/kotlin/CoreRegressionJUnitTest.kt',
            'android/asr-runtime/src/test/kotlin/RuntimeRegressionJUnitTest.kt')


def require(value, diagnostic):
    if not value:
        raise AssertionError(diagnostic)


def source_check(root):
    for name in BODIES:
        source = root / ('tests/' + name + '.kt')
        require(source.is_file() and not source.is_symlink(), 'selected Kotlin source missing: ' + name)
        require(not source.with_suffix('.java').exists(), 'duplicate selected Java/Kotlin source: ' + name)
        text = source.read_text()
        require('class ' + name in text and 'fun main(args: Array<String>?)' in text
                and '@JvmStatic' in text, 'selected static main source shape: ' + name)
    for relative in WRAPPERS:
        source = root / relative
        require(source.is_file() and not source.is_symlink(), 'wrapper Kotlin source missing: ' + relative)
        old = root / relative.replace('/kotlin/', '/java/').replace('.kt', '.java')
        require(not old.exists(), 'duplicate wrapper Java/Kotlin source: ' + relative)
    host = (root / 'scripts/test-minimal-apk.sh').read_text()
    for name in BODIES:
        require(host.count('  tests/' + name + '.kt') == 1
                and 'tests/' + name + '.java' not in host, 'host selected caller inventory: ' + name)
    return len(BODIES) + len(WRAPPERS)


def xml_check(tree, suite):
    classname, methods, _ = SUITES[suite]
    cases = tree.findall('testcase')
    require(len(cases) == len(methods) and sorted(c.get('name') for c in cases) == sorted(methods),
            'exact nonzero JUnit method inventory: ' + suite)
    require(all(c.get('classname') == classname for c in cases), 'JUnit class mismatch: ' + suite)
    require(all(not any(c.find(tag) is not None for tag in ('failure', 'error', 'skipped')) for c in cases),
            'JUnit failure/error/skip: ' + suite)
    require(tree.get('name') == classname and tree.get('tests') == str(len(methods))
            and all(tree.get(tag) == '0' for tag in ('failures', 'errors', 'skipped')),
            'JUnit aggregate mismatch: ' + suite)
    return len(cases)


def junit_check(root, suite):
    classname, _, directory = SUITES[suite]
    path = root / directory / ('TEST-' + classname + '.xml')
    require(path.is_file() and not path.is_symlink(), 'fresh JUnit XML missing: ' + suite)
    count = xml_check(ET.parse(path).getroot(), suite)
    print('PASS exact runner JUnit', suite, count, 'XML SHA', hashlib.sha256(path.read_bytes()).hexdigest())


def rejected(call, diagnostic):
    try:
        call()
    except AssertionError as error:
        require(diagnostic in str(error), 'wrong guard diagnostic: ' + str(error))
    else:
        raise AssertionError('guard accepted negative: ' + diagnostic)


def self_test():
    for suite, (classname, methods, _) in SUITES.items():
        good = ET.Element('testsuite', name=classname, tests=str(len(methods)), failures='0', errors='0', skipped='0')
        for name in methods:
            ET.SubElement(good, 'testcase', classname=classname, name=name)
        xml_check(good, suite)
        missing = copy.deepcopy(good); missing.remove(missing[0])
        extra = copy.deepcopy(good); ET.SubElement(extra, 'testcase', classname=classname, name='extra')
        duplicate = copy.deepcopy(good)
        if len(methods) == 1:
            duplicate.append(copy.deepcopy(duplicate[0]))
        else:
            duplicate[-1].set('name', methods[0])
        empty = copy.deepcopy(good)
        for case in list(empty):
            empty.remove(case)
        for bad in (missing, extra, duplicate, empty):
            rejected(lambda: xml_check(bad, suite), 'method inventory')
        wrong = copy.deepcopy(good); wrong[0].set('classname', 'Wrong')
        rejected(lambda: xml_check(wrong, suite), 'class mismatch')
        for tag in ('failure', 'error', 'skipped'):
            bad = copy.deepcopy(good); ET.SubElement(bad[0], tag)
            rejected(lambda: xml_check(bad, suite), 'failure/error/skip')
        for key, value in (('tests', '0'), ('tests', str(len(methods) + 1)), ('failures', '1'),
                           ('errors', '1'), ('skipped', '1'), ('name', 'Wrong')):
            bad = copy.deepcopy(good); bad.set(key, value)
            rejected(lambda: xml_check(bad, suite), 'aggregate mismatch')
        xml_check(good, suite)
    with tempfile.TemporaryDirectory(prefix='slice-source-') as temporary:
        root = Path(temporary)
        for name in BODIES:
            path = root / ('tests/' + name + '.kt'); path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('class ' + name + ' { companion object { @JvmStatic fun main(args: Array<String>?) {} } }\n')
        for relative in WRAPPERS:
            path = root / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_text('wrapper\n')
        host = root / 'scripts/test-minimal-apk.sh'; host.parent.mkdir()
        host.write_text(''.join('  tests/' + name + '.kt\n' for name in BODIES))
        source_check(root)
        source = root / ('tests/' + BODIES[0] + '.kt'); original = source.read_bytes()
        source.unlink(); rejected(lambda: source_check(root), 'source missing'); source.write_bytes(original)
        sibling = source.with_suffix('.java'); sibling.touch()
        rejected(lambda: source_check(root), 'duplicate selected'); sibling.unlink()
        source.write_text(original.decode().replace('fun main', 'fun renamed'))
        rejected(lambda: source_check(root), 'main source shape'); source.write_bytes(original)
        wrapper = root / WRAPPERS[0]; old = root / WRAPPERS[0].replace('/kotlin/', '/java/').replace('.kt', '.java')
        old.parent.mkdir(parents=True); old.touch()
        rejected(lambda: source_check(root), 'duplicate wrapper'); old.unlink()
        data = wrapper.read_bytes(); wrapper.unlink(); rejected(lambda: source_check(root), 'wrapper Kotlin source missing'); wrapper.write_bytes(data)
        data = host.read_bytes(); host.write_text('')
        rejected(lambda: source_check(root), 'host selected caller inventory'); host.write_bytes(data)
        source_check(root)
    print('PASS same guard missing/extra/duplicate/class/zero/failure/error/skip/aggregate/source/main target negatives and recovery')


def contracts(classpath, evidence):
    # Imports only for actual contracts; pure XML/source checks never initialize an execution runner.
    import mixed_compiler_lifecycle as life
    evidence.mkdir(parents=True, exist_ok=False)
    dependency = Path(classpath).resolve(strict=True)
    life.guard(dependency)
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed stdlib missing')
    mutations = (
        ('asr/NativeResponse.kt', 'const val MAX_TOKENS = 128', 'const val MAX_TOKENS = 129',
         'NativeResponseTest', 'check 22'),
        ('transcription/ResultState.java', 'snapshot.clearEpoch == clearEpoch', 'true',
         'TextExportSafetyTest', 'clear before write admission must cause ZERO provider opens'),
        ('transcription/TextExportController.kt', '            throw closing\n', '            return\n',
         'TextExportFatalTest', 'exact fatal must propagate even when close is suppressed under ordinary write failure'))
    records = []
    for index, (relative, old, new, test, diagnostic) in enumerate(mutations):
        source = ROOT / 'android/app/src/org/llmasr/minimal' / relative
        original = source.read_bytes(); text = original.decode()
        require(text.count(old) == 1, 'unique production mutant anchor: ' + relative)
        # An owned Workspace preserves failed evidence; mixed leases are checked before its own cleanup.
        def lease_free(directory):
            life.guard(directory)
            life.guard(dependency)
            return True

        with life.owned.Workspace(parent=evidence, probe=lambda: lease_free(work)) as work:
            target = work / source.name; target.write_bytes(original)
            try:
                for phase, content in (('green', original), ('red', text.replace(old, new, 1).encode()), ('restored', original)):
                    life.guard(work); life.guard(dependency)
                    target.write_bytes(content)
                    classes = work / ('classes-' + phase)
                    compiled = life.run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes),
                                         str(dependency), str(target)], work, phase + '-compile', 270, dependency=dependency)
                    require(compiled.returncode == 0, 'production compilation failure NOT target red: ' + relative)
                    ran = life.run(['java', '-cp', str(classes) + ':' + str(dependency) + ':' + str(stdlib), test],
                                   work, phase + '-run', 45, dependency=dependency)
                    output = (ran.stdout + ran.stderr).decode(errors='replace')
                    if phase == 'red':
                        require(ran.returncode == 1 and 'java.lang.AssertionError: ' + diagnostic in output,
                                'exact production behavior red missing: ' + relative + '\n' + output)
                    else:
                        require(ran.returncode == 0 and 'PASS' in output, 'production positive failed: ' + relative + '\n' + output)
                    # Store closed execution records outside normally removed successful workspace.
                    files = {}
                    saved = evidence / (str(index) + '-' + phase)
                    saved.mkdir()
                    (saved / target.name).write_bytes(content)
                    for suffix in ('compile', 'run'):
                        record = work / (phase + '-' + suffix + '.json')
                        state = json.loads(record.read_text())
                        require(state.get('owned_exited') and state.get('root_reaped'), 'owned exit unconfirmed')
                        files[suffix] = state
                        for extension in ('.json', '.stdout', '.stderr'):
                            log = work / (phase + '-' + suffix + extension)
                            require(log.is_file(), 'closed execution output missing: ' + str(log))
                            (saved / log.name).write_bytes(log.read_bytes())
                    records.append({'owner': relative, 'test': test, 'phase': phase, 'exit': ran.returncode,
                                    'source_sha256': hashlib.sha256(content).hexdigest(), 'executions': files,
                                    'stdout': ran.stdout.decode(errors='replace'), 'stderr': ran.stderr.decode(errors='replace')})
                    (evidence / 'routes.json').write_text(json.dumps(records, indent=2) + '\n')
                require(source.read_bytes() == original and target.read_bytes() == original, 'production source restoration identity')
            finally:
                # The Workspace probe also checks leases before any removal and preserves the primary exception.
                # Never restore a target or clear classes here while a compiler might still own them.
                pass
        print('PASS production mutant compile0/exact assertion1/restored0', relative, flush=True)
    require(len(records) == 9, 'exact bounded nine routes')
    print('PASS selected test production contracts nine routes; JVM only')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--self-test', action='store_true')
    mode.add_argument('--source', action='store_true')
    mode.add_argument('--junit', choices=SUITES)
    mode.add_argument('--contracts', type=Path)
    parser.add_argument('--evidence-dir', type=Path)
    args = parser.parse_args()
    if args.self_test:
        self_test()
    elif args.source:
        print('PASS exact migrated source/main inventory', source_check(ROOT))
    elif args.junit:
        junit_check(ROOT, args.junit)
    else:
        require(args.evidence_dir is not None, 'contracts requires fresh evidence directory')
        contracts(args.contracts, args.evidence_dir)


if __name__ == '__main__':
    main()
