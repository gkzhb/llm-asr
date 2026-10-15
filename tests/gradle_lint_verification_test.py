#!/usr/bin/env python3
"""Single strict/offline library Lint driver; no main/native/key/model inputs."""
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROBE = 'src/main/java/org/llmasr/lintfixture/LintProbe.java'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_xml(path, source, expect_error):
    root = ET.parse(path).getroot()
    errors = [n for n in root.findall('issue') if n.get('severity') in ('Error', 'Fatal')]
    if not expect_error:
        if errors:
            raise ValueError('Lint positive contains Error/Fatal')
        return
    line = next(i for i, text in enumerate(source.read_text().splitlines(), 1)
                if 'activity.getOnBackInvokedDispatcher();' in text)
    targets = [n for n in errors if n.get('id') == 'NewApi' and
               re.search(r'API (?:level )?33', n.get('message', '')) and
               re.search(r'(?:min|current min).*29', n.get('message', ''), re.I) and
               any(Path(loc.get('file', '')).resolve() == source.resolve() and
                   loc.get('line') == str(line) for loc in n.findall('location'))]
    if len(targets) != 1 or len(errors) != 1:
        raise ValueError('Lint negative missing unique NewApi API33/min29 target file/line')


def run_fixture():
    base = ROOT / '.work/gradle-lint-negative'
    base.mkdir(parents=True, exist_ok=True)
    fixture = Path(tempfile.mkdtemp(prefix='run-', dir=base))
    template = ROOT / 'tests/fixtures/lint-error'
    shutil.copytree(template, fixture, dirs_exist_ok=True)
    for relative in ('gradlew', 'gradle.properties', 'gradle/wrapper/gradle-wrapper.jar',
                     'gradle/wrapper/gradle-wrapper.properties', 'gradle/verification-metadata.xml',
                     'gradle/verify-lint-tools.gradle', 'gradle/lint-tool-identity.json'):
        destination = fixture / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / relative, destination)
    # Trusted AGP8.6.1 option, child-only resource cap; no tool/version override.
    with (fixture / 'gradle.properties').open('a') as properties:
        properties.write('\nandroid.experimental.lint.heapSize=768m\n')
    # Ordinary Gradle task invokes the SAME production validator closure with
    # the real passed Configuration and a mutable test-only file collection.
    # This is integration evidence, not a simulated/default AGP consumer.
    build_file = fixture / 'build.gradle'
    probe_script = '''
ext.p1LintFixtureAdapter = { gate, passed, validator ->
    tasks.register('lintFilesProbe') { probe ->
        dependsOn(gate)
        doFirst {
            def consumer = tasks.named('lintAnalyzeDebug').get()
            def record = passed.get(consumer)
            if (record == null || !record.classpath.is(consumer.lintTool.classpath)) {
                throw new GradleException('Fixture missing real successful consumer record')
            }
            def files = record.classpath.files.toList()
            def fc = objects.fileCollection().from(files)
            if (providers.gradleProperty('p1LintClasspathMutation').isPresent()) {
                def mutation = providers.gradleProperty('p1LintClasspathMutation').get()
                def unknown = file('unknown-lint-tool.jar')
                unknown.text = 'non-executable target negative fixture'
                if (mutation == 'missing') files.remove(0)
                else if (mutation == 'extra') files.add(unknown)
                else if (mutation == 'replacement') files[0] = unknown
                else throw new GradleException('Unknown test mutation')
                fc.setFrom(files)
                println 'LINT_TEST_ADAPTER_MUTATION=' + mutation
            }
            validator.call(probe, record.configuration, fc, 'fixture-validator')
        }
        doLast { println 'LINT_SHARED_VALIDATOR_RESTORE_PASS' }
    }
}
apply from: 'gradle/verify-lint-tools.gradle'
'''
    original_build = build_file.read_text()
    if original_build.count("apply from: 'gradle/verify-lint-tools.gradle'") != 1:
        raise ValueError('Fixture template must apply production validator exactly once')
    build_file.write_text(original_build.replace("apply from: 'gradle/verify-lint-tools.gradle'", probe_script))
    source = fixture / PROBE
    green = source.read_bytes()
    report = fixture / 'build/reports/lint-target.xml'
    env = dict(os.environ, GRADLE_USER_HOME=os.environ.get('GRADLE_USER_HOME', str(ROOT / '.cache/gradle-user-home')))
    commands = ['./gradlew', 'lintDebug', '--offline', '--rerun-tasks', '--no-daemon', '--console=plain',
                '--dependency-verification', 'strict',
                '-Pandroid.aapt2FromMavenOverride=' + os.environ['ANDROID_HOME'] + '/build-tools/35.0.0/aapt2']

    def execute(label, task='lintDebug', dry=False, mutation=None):
        command = list(commands)
        command[1] = task
        if dry:
            command.append('--dry-run')
        if mutation:
            command.append('-Pp1LintClasspathMutation=' + mutation)
        (fixture / (label + '.command.json')).write_text(json.dumps(command) + '\n')
        started = time.time_ns()
        timed_out = False
        with (fixture / (label + '.log')).open('w') as log:
            try:
                result = subprocess.run(command, cwd=fixture, env=env, stdout=log,
                                        stderr=subprocess.STDOUT, timeout=180)
                code = result.returncode
            except subprocess.TimeoutExpired:
                # subprocess.run kills/waits its Gradle launcher; report failure,
                # never turn timeout/missing XML into a target negative success.
                code = 124
                timed_out = True
        (fixture / (label + '.exit')).write_text(str(code) + '\n')
        (fixture / (label + '.execution.json')).write_text(json.dumps({
            'command': command, 'started_ns': started, 'finished_ns': time.time_ns(),
            'exit': code, 'timeout': timed_out}) + '\n')
        if timed_out:
            raise ValueError('Lint subprocess timed out: ' + label + ': ' + str(fixture))
        return code, (fixture / (label + '.log')).read_text()

    code, text = execute('graph', dry=True)
    if code or not re.search(r'^:verifyLintToolIdentity SKIPPED$', text, re.M):
        raise ValueError('Lint fixture graph/gate missing: ' + str(fixture))
    for task in re.findall(r'^(:\S+) SKIPPED$', text, re.M):
        # AGP library model stages empty JNI folders even with no native inputs;
        # these are not compiler/native artifact targets. Template has no jniLibs.
        empty_native_staging = {':mergeDebugJniLibFolders', ':mergeDebugNativeLibs',
                                ':stripDebugDebugSymbols', ':copyDebugJniLibsProjectAndLocalJars'}
        if task not in empty_native_staging and re.search(r'native|prepareInputs|assemble|install|connected|CMake', task, re.I):
            raise ValueError('Lint fixture graph includes forbidden product task: ' + task)
    # Actual identity failure must stop before the Lint worker runs.
    manifest = fixture / 'gradle/lint-tool-identity.json'
    approved = manifest.read_bytes()
    for label, domain in (('identity-artifact-negative', 'artifacts'), ('identity-edge-negative', 'edges')):
        bad = json.loads(approved)
        if domain == 'artifacts':
            bad['artifacts'][0]['sha256'] = '0' * 64
        else:
            bad['edges'].pop()
        manifest.write_text(json.dumps(bad) + '\n')
        code, text = execute(label)
        if code == 0 or 'Lint identity mismatch ' + domain not in text or re.search(r'^> Task :lint(?:Analyze|Report)Debug', text, re.M):
            raise ValueError('Lint actual identity negative not rejected before consumer: ' + str(fixture))
        manifest.write_bytes(approved)
    for mutation in ('missing', 'extra', 'replacement'):
        report.unlink(missing_ok=True)
        code, text = execute('classpath-' + mutation + '-negative', task='lintFilesProbe', mutation=mutation)
        if (code == 0 or 'LINT_TEST_ADAPTER_MUTATION=' + mutation not in text or
                'Lint identity actual classpath files mismatch: :lintFilesProbe' not in text or
                'task=:lintFilesProbe phase=fixture-validator' in text or
                'LINT_SHARED_VALIDATOR_RESTORE_PASS' in text or report.exists()):
            raise ValueError('Lint post-gate file mutation lacks guard-before-worker rejection: ' + str(fixture))
    code, text = execute('classpath-restore', task='lintFilesProbe')
    if (code or 'task=:lintFilesProbe phase=fixture-validator' not in text or
            'LINT_SHARED_VALIDATOR_RESTORE_PASS' not in text):
        raise ValueError('Ordinary task shared production validator restore failed: ' + str(fixture))
    records = []
    for label in ('green0', 'red', 'green1'):
        if label == 'red':
            guard = b'        if (android.os.Build.VERSION.SDK_INT >= 33) {\n'
            if green.count(guard) != 1 or green.count(b'        }\n') != 1:
                raise ValueError('Fixture source must have exactly one API guard')
            source.write_bytes(green.replace(guard, b'').replace(b'        }\n', b'', 1))
        else:
            source.write_bytes(green)
        report.unlink(missing_ok=True)
        shutil.copyfile(source, fixture / (label + '.source.java'))
        code, text = execute(label)
        if (code == 0) != (label != 'red') or 'phase=consumer' not in text:
            raise ValueError('Lint phase wrong exit/identity ' + label + ': ' + str(fixture))
        if label == 'red' and 'Lint found errors' not in text:
            raise ValueError('Lint red lacks actual error diagnostic')
        if not report.is_file():
            raise ValueError('Lint phase missing fresh XML: ' + label)
        check_xml(report, source, label == 'red')
        copied = fixture / (label + '.xml')
        shutil.copyfile(report, copied)
        if not re.search(r'^> Task :lintReportDebug$', text, re.M):
            # Red prints FAILED; it must still execute the report consumer.
            if label != 'red' or '> Task :lintReportDebug FAILED' not in text:
                raise ValueError('Lint report action missing or stale/up-to-date: ' + label)
        records.append({'phase': label, 'exit': code, 'source_sha256': sha(source),
                        'xml_sha256': sha(copied), 'log_sha256': sha(fixture / (label + '.log'))})
        (fixture / 'phases.json').write_text(json.dumps(records, indent=2) + '\n')
    if records[0]['source_sha256'] != records[2]['source_sha256']:
        raise ValueError('Lint restored source bytes mismatch')
    negatives = []
    for label in ('identity-artifact-negative', 'identity-edge-negative',
                  'classpath-missing-negative', 'classpath-extra-negative', 'classpath-replacement-negative',
                  'classpath-restore'):
        negatives.append({'phase': label, 'exit': int((fixture / (label + '.exit')).read_text()),
                          'log_sha256': sha(fixture / (label + '.log'))})
    (fixture / 'result.json').write_text(json.dumps({'run': fixture.name, 'records': records,
        'negative_records': negatives,
        'filecollection_negative_semantics': 'Ordinary Gradle task invokes same production validator closure '
            'with real passed Configuration; marker restore is not a typed/default AGP Lint worker',
        'device_verified': False, 'main_lint_executed': False}, indent=2) + '\n')
    print('PASS actual Lint identity rejection/ordinary task shared production validator negatives+restore; '
          'default AGP green0/NewApi red/green1 restore reports=' + str(fixture))


if __name__ == '__main__':
    run_fixture()
