#!/usr/bin/env python3
"""Isolated actual Gradle/JUnit green-red-green behavior proof, no Android execution.

Copies only build definitions and Java/Kotlin sources; no signing keys, models,
assets, audio, device access or existing class/build outputs. Cache is read-only
with respect to dependency evidence: strict/offline, no lock/metadata write flags.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import shlex
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CLASS = 'org.llmasr.minimal.testhost.FixtureContractTest'
METHODS = sorted(['enginePayloadAndOrder', 'readinessRejectsBeforeLoad', 'badBytesAreSafe',
                  'singleConfirmationAndPassword', 'lateResultIsInvalidated', 'cleanupRetainsOwner',
                  'reusedBackendQueuedCancellation'])
WRAPPER_SHA = 'cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def xml_result(path, expected_method=None, diagnostic=None):
    tree = ET.parse(path).getroot()
    cases = tree.findall('testcase')
    if sorted(c.get('name') for c in cases) != METHODS or any(c.get('classname') != CLASS for c in cases):
        raise AssertionError('exact nonzero JUnit discovery mismatch')
    if any(c.find('skipped') is not None or c.find('error') is not None for c in cases):
        raise AssertionError('JUnit skipped/error is not behavior proof')
    failures = [(c.get('name'), f) for c in cases for f in c.findall('failure')]
    if expected_method is None:
        if failures or any(int(tree.get(k, '-1')) != 0 for k in ('failures', 'errors', 'skipped')):
            raise AssertionError('expected green JUnit suite')
    else:
        if len(failures) != 1 or failures[0][0] != expected_method:
            raise AssertionError('wrong red method or count')
        failure = failures[0][1]
        if failure.get('type') != 'java.lang.AssertionError' or diagnostic not in failure.get('message', ''):
            raise AssertionError('red was not the exact behavior AssertionError')
    if int(tree.get('tests', '-1')) != len(METHODS):
        raise AssertionError('JUnit count mismatch')
    return {'methods': METHODS, 'tests': len(cases), 'failures': [n for n, _ in failures], 'sha256': sha(path)}


def fixture_inputs(root):
    """Current maintained inputs, independent of staging; no generated/private tree copy."""
    root = Path(root)
    exact = {'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties',
             'buildscript-gradle.lockfile', 'gradlew', 'gradlew.bat',
             'core/build.gradle.kts', 'core/gradle.lockfile',
             'android/app/build.gradle.kts', 'android/app/gradle.lockfile',
             'android/asr-runtime/build.gradle.kts', 'android/asr-runtime/gradle.lockfile',
             'android/test-host/build.gradle.kts', 'android/test-host/gradle.lockfile',
             'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
             'gradle/verification-metadata.xml'}
    excluded = {'build', '.gradle', '.cache', '__pycache__', '.git', 'assets',
                'private', 'models', 'weights', 'keys', 'secrets'}

    def safe(relative):
        parts = Path(relative).parts
        return not any(part in excluded for part in parts)

    def regular(relative):
        path = root / relative
        return path.is_file() and not any((root / Path(*Path(relative).parts[:i])).is_symlink()
                                          for i in range(1, len(Path(relative).parts) + 1))

    def current_files(directory):
        base = root / directory
        if base.is_symlink():
            return
        for parent, dirs, files in os.walk(base, followlinks=False):
            dirs[:] = sorted(d for d in dirs if d not in excluded and not (Path(parent) / d).is_symlink())
            for name in sorted(files):
                relative = str((Path(parent) / name).relative_to(root))
                if safe(relative) and regular(relative):
                    yield relative

    # Keep all maintained Gradle definitions/evidence, and fail on a missing
    # tracked config rather than silently skipping required dependency inputs.
    def gradle_config(relative):
        return safe(relative) and (Path(relative).suffix in ('.kts', '.gradle', '.json', '.xml', '.properties', '.lockfile')
                                   or relative == 'gradle/wrapper/gradle-wrapper.jar')

    tracked = subprocess.check_output(['git', 'ls-files', '-z', '--', 'gradle/'], cwd=root).decode().split('\0')
    required = exact | {p for p in tracked if p and gradle_config(p)}
    for relative in sorted(required):
        if not regular(relative):
            raise FileNotFoundError('required fixture config missing or non-regular: ' + relative)
    inputs = set(required)
    inputs.update(p for p in current_files('gradle') if gradle_config(p))
    for directory in ('android/app/src', 'android/asr-runtime/src', 'core/src', 'android/test-host/src',
                      'android/test-host/fixtures/java'):
        inputs.update(p for p in current_files(directory) if p.endswith(('.java', '.kt', '.xml')))
    return sorted(inputs)


def main():
    import mixed_compiler_lifecycle as life
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path)
    args = parser.parse_args()
    if args.evidence_dir is None:
        output = ROOT / '.work/p1b-build/2b'
        output.mkdir(parents=True, exist_ok=True)
        run = Path(tempfile.mkdtemp(prefix='behavior-', dir=output))
    else:
        run = args.evidence_dir.resolve()
        run.mkdir(parents=True, exist_ok=False)
    source = ROOT / 'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FakePorts.kt'
    original = source.read_text()
    inputs = fixture_inputs(ROOT)
    receipt = {'driver_sha256': sha(Path(__file__)), 'pid': os.getpid(), 'runs': [],
               'android_runtime': 'not run', 'main_source_sha256': sha(source),
               'fixture_inputs': {p: sha(ROOT / p) for p in inputs}}
    # No existing/generated build outputs or private/large inputs enter the copy.
    # Retain the isolated tree and logs on failure; no deletion before owned exit.
    work = run / 'isolated'
    work.mkdir()
    if work.is_dir():
        for relative in inputs:
            target = work / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
        wrapper = work / 'gradle/wrapper/gradle-wrapper.jar'
        if sha(wrapper) != WRAPPER_SHA:
            raise AssertionError('wrapper identity mismatch')
        port = work / source.relative_to(ROOT)
        payload = original.replace('language English<asr_text>fixture sentence',
                                   'language English<asr_text>different legal sentence')
        cleanup_anchor = '            cleanupEntered.countDown()'
        cleanup = original.replace(cleanup_anchor,
                                   '            cleanupCompleted = true\n' + cleanup_anchor)
        if original.count('language English<asr_text>fixture sentence') != 1 or original.count(cleanup_anchor) != 1:
            raise AssertionError('mutation target missing')
        phases = [('green-initial', original, None, None),
                  ('red-payload', payload, 'enginePayloadAndOrder', 'engine payload display'),
                  ('green-payload-restored', original, None, None),
                  ('red-cleanup', cleanup, 'cleanupRetainsOwner', 'cleanup must wait for release'),
                  ('green-final', original, None, None)]
        env = os.environ.copy()
        env['GRADLE_USER_HOME'] = str(ROOT / '.cache/gradle-user-home')
        tools = Path(env['ANDROID_HOME']) / 'build-tools/35.0.0'
        for name, content, red_method, diagnostic in phases:
            # Fresh classes/build per phase, not merely a rerun over stale outputs.
            life.guard(work)
            for module in ['core', 'android/asr-runtime', 'android/test-host', 'android/app']:
                shutil.rmtree(work / module / 'build', ignore_errors=True)
            port.write_text(content)
            command = ['bash', './gradlew', ':test-host:testDebugUnitTest', '--offline', '--no-daemon',
                       '--console=plain', '--max-workers=1', '--dependency-verification', 'strict',
                       '-Pandroid.aapt2FromMavenOverride=' + str(tools / 'aapt2')]
            log = run / (name + '.log')
            # The reused owner seals both streams and reaps descendants, including Gradle.
            previous_home = os.environ.get('GRADLE_USER_HOME')
            os.environ['GRADLE_USER_HOME'] = env['GRADLE_USER_HOME']
            try:
                result = life.run(['bash', '-c', 'cd ' + shlex.quote(str(work)) + ' && exec ' + shlex.join(command)], work, name, 600)
            finally:
                if previous_home is None:
                    os.environ.pop('GRADLE_USER_HOME', None)
                else:
                    os.environ['GRADLE_USER_HOME'] = previous_home
            log.write_bytes(result.stdout + result.stderr)
            ledger = json.loads((work / (name + '.json')).read_text())
            if not ledger.get('owned_exited') or not ledger.get('root_reaped') or ledger.get('timeout'):
                raise AssertionError('fixture owned exit/timeout not behavior proof')
            text = log.read_text()
            if re.search(r'WARNING: [DR]8|malformed kotlin.Metadata|Unexpected rewriting', text):
                raise AssertionError('DEX diagnostic requires review')
            if (result.returncode == 0) != (red_method is None):
                raise AssertionError('unexpected Gradle status: ' + name)
            for task in ['compileDebugUnitTestKotlin', 'compileDebugKotlin']:
                if not re.search(r'^> Task :test-host:' + task + r'$', text, re.M):
                    raise AssertionError('fresh Kotlin compilation not proved: ' + task)
            for task in ['compileDebugUnitTestJavaWithJavac', 'compileDebugJavaWithJavac']:
                if not re.search(r'^> Task :test-host:' + task + r' NO-SOURCE$', text, re.M):
                    raise AssertionError('expected fresh Java NO-SOURCE not proved: ' + task)
            for variant, owners in [('debug', ['ImeHostActivity']),
                                    ('debugUnitTest', ['FixtureContractTest', 'FakePorts', 'FixtureChecks'])]:
                classes = work / 'android/test-host/build/tmp/kotlin-classes' / variant / 'org/llmasr/minimal/testhost'
                for owner in owners:
                    if not (classes / (owner + '.class')).is_file():
                        raise AssertionError('fresh Kotlin class missing: ' + variant + '/' + owner)
            if list((work / 'android/test-host/build/intermediates/javac').rglob('*.class')):
                raise AssertionError('unexpected Java class emission in main/unit fixture')
            xml = work / ('android/test-host/build/test-results/testDebugUnitTest/TEST-' + CLASS + '.xml')
            check = xml_result(xml, red_method, diagnostic)
            saved = run / (name + '.xml'); shutil.copyfile(xml, saved)
            receipt['runs'].append({'name': name, 'command': command, 'exit': result.returncode,
                                    'log': str(log.relative_to(ROOT)), 'log_sha256': sha(log),
                                    'port_sha256': sha(port), 'xml': check})
            (run / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
            print(name, 'Gradle exit', result.returncode, 'exact XML', check, flush=True)
    if sha(source) != receipt['main_source_sha256']:
        raise AssertionError('main tree fixture unexpectedly changed')
    print('PASS two legal compiling fixture behavior mutants, both restored green; report', run)


if __name__ == '__main__':
    main()
