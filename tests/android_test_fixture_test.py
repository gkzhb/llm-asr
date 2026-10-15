#!/usr/bin/env python3
"""Isolated actual Gradle/JUnit green-red-green behavior proof, no Android execution.

Copies only build definitions and Java/Kotlin sources; no signing keys, models,
assets, audio, device access or existing class/build outputs. Cache is read-only
with respect to dependency evidence: strict/offline, no lock/metadata write flags.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
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


def main():
    output = ROOT / '.work/p1b-build/2b'
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='behavior-', dir=output))
    source = ROOT / 'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FakePorts.java'
    original = source.read_text()
    receipt = {'driver_sha256': sha(Path(__file__)), 'pid': os.getpid(), 'runs': [],
               'android_runtime': 'not run', 'main_source_sha256': sha(source)}
    # No existing/generated build outputs or private/large inputs enter the copy.
    with tempfile.TemporaryDirectory(prefix='p1b-fixture-') as temporary:
        work = Path(temporary)
        tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
        exact = {'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties',
                 'buildscript-gradle.lockfile', 'gradlew', 'gradlew.bat',
                 'core/build.gradle.kts', 'core/gradle.lockfile',
                 'android/app/build.gradle.kts', 'android/app/gradle.lockfile',
                 'android/asr-runtime/build.gradle.kts', 'android/asr-runtime/gradle.lockfile'}
        for relative in tracked:
            if not relative:
                continue
            if relative in exact or relative.startswith('gradle/') or (
                    relative.startswith(('android/app/src/', 'android/asr-runtime/src/', 'core/src/'))
                    and relative.endswith(('.java', '.kt', '.xml'))):
                target = work / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / relative, target)
        shutil.copytree(ROOT / 'android/test-host', work / 'android/test-host',
                        ignore=shutil.ignore_patterns('build', '.gradle', '__pycache__'))
        wrapper = work / 'gradle/wrapper/gradle-wrapper.jar'
        if sha(wrapper) != WRAPPER_SHA:
            raise AssertionError('wrapper identity mismatch')
        port = work / source.relative_to(ROOT)
        payload = original.replace('language English<asr_text>fixture sentence',
                                   'language English<asr_text>different legal sentence')
        cleanup = original.replace('            cleanupEntered.countDown();',
                                   '            cleanupCompleted = true;\n            cleanupEntered.countDown();')
        if payload == original or cleanup == original:
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
            for module in ['core', 'android/asr-runtime', 'android/test-host', 'android/app']:
                shutil.rmtree(work / module / 'build', ignore_errors=True)
            port.write_text(content)
            command = ['bash', './gradlew', ':test-host:testDebugUnitTest', '--offline', '--no-daemon',
                       '--console=plain', '--max-workers=1', '--dependency-verification', 'strict',
                       '-Pandroid.aapt2FromMavenOverride=' + str(tools / 'aapt2')]
            log = run / (name + '.log')
            with log.open('w') as stream:
                result = subprocess.run(command, cwd=work, env=env, stdout=stream,
                                        stderr=subprocess.STDOUT, timeout=600)
            text = log.read_text()
            if re.search(r'WARNING: [DR]8|malformed kotlin.Metadata|Unexpected rewriting', text):
                raise AssertionError('DEX diagnostic requires review')
            if (result.returncode == 0) != (red_method is None):
                raise AssertionError('unexpected Gradle status: ' + name)
            for task in ['compileDebugUnitTestJavaWithJavac', 'compileDebugJavaWithJavac']:
                if ':test-host:' + task not in text or re.search(':test-host:' + task + r' (FAILED|NO-SOURCE|UP-TO-DATE|FROM-CACHE)', text):
                    raise AssertionError('fresh Java compilation not proved: ' + task)
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
