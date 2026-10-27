#!/usr/bin/env python3
"""Independent-output checks for an actual AGP APK (not Android/device acceptance)."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import uuid
import zipfile
from pathlib import Path

from dex_class_inventory import defined_classes

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def collect_build_inputs(root):
    """Live build inventory; shared by actual receipts and isolated input-negative tests."""
    baseline = json.loads((root / 'gradle/baseline-inputs.json').read_text())
    inputs = set(baseline['files'])
    for relative in ['settings.gradle.kts', 'build.gradle.kts', 'gradle.properties',
                     'flake.nix', 'flake.lock', 'gradlew', 'gradlew.bat',
                     'buildscript-gradle.lockfile', 'android/app/gradle.lockfile',
                     'core/build.gradle.kts', 'core/gradle.lockfile',
                     'android/asr-runtime/build.gradle.kts', 'android/asr-runtime/gradle.lockfile']:
        inputs.add(relative)
    for folder in ['gradle', 'scripts', 'tests', 'android/app/res', 'native/cmake',
                   'core/src', 'android/asr-runtime/src']:
        inputs.update(str(p.relative_to(root)) for p in (root / folder).rglob('*')
                      if p.is_file() and '__pycache__' not in p.parts)
    inputs.add('android/app/build.gradle.kts')
    inputs.add('native/source-lock.json')
    import importlib.util
    spec = importlib.util.spec_from_file_location('android_test_inputs', ROOT / 'scripts/verify-android-test-apk.py')
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    inputs.update(checker.module_inputs(root))
    return {relative: sha(root / relative) for relative in sorted(inputs)}


def check_identity(apk, baseline, reports):
    signature = (reports / 'signature.txt').read_text()
    certificates = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})', signature)
    if certificates != [baseline['signing_certificate_sha256']]:
        raise ValueError('Signing identity mismatch')
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        dex_files = [n for n in names if re.fullmatch(r'classes(?:\d+)?\.dex', n)]
        if not dex_files:
            raise ValueError('DEX missing')
        classes = set()
        for name in dex_files:
            definitions = defined_classes(archive.read(name))
            if classes.intersection(definitions):
                raise ValueError('Duplicate production/runtime owner across DEX files')
            classes.update(definitions)
        owners = [p.removeprefix('android/app/src/').rsplit('.', 1)[0]
                  for p in baseline['files'] if p.endswith(('.kt', '.java'))]
        for name in owners:
            if ('L' + name + ';').encode() not in classes:
                raise ValueError('Production Kotlin/Java owner definition missing: ' + name)
        if b'Lkotlin/jvm/internal/Intrinsics;' not in classes:
            raise ValueError('Kotlin runtime definition missing')
        native = archive.read('lib/arm64-v8a/libqwen_asr_jni.so')
        if hashlib.sha256(native).hexdigest() != baseline['native']['sha256']:
            raise ValueError('AGP changed the frozen native payload')
        expected_assets = {relative.removeprefix('android/app/')
                           for relative in baseline['files'] if relative.startswith('android/app/assets/')}
        expected_assets.update(['assets/model-manifest.json', 'assets/sample.wav'])
        actual_assets = {name for name in names if name.startswith('assets/') and not name.endswith('/')}
        if actual_assets != expected_assets:
            raise ValueError('APK asset inventory mismatch')
        for name in expected_assets:
            if name == 'assets/sample.wav':
                expected = baseline['sample']['sha256']
            elif name == 'assets/model-manifest.json':
                expected = baseline['files']['reports/p0/mnn-model-manifest.json']
            else:
                expected = baseline['files']['android/app/' + name]
            if hashlib.sha256(archive.read(name)).hexdigest() != expected:
                raise ValueError('APK asset hash mismatch: ' + name)
    tree = (reports / 'manifest-tree.txt').read_text()
    check_activity_back_policy(tree)
    for key, value in [('debuggable', 'true'), ('allowBackup', 'false'), ('extractNativeLibs', 'true')]:
        if not re.search(r'android:' + key + r'[^\n]*=' + value + r'(?:\s|$)', tree):
            raise ValueError('Manifest policy mismatch: ' + key)


def check_activity_back_policy(tree):
    # Bound each component by its tree indentation; never borrow an opt-in from
    # a following service/activity or nested child. Only direct attributes count.
    lines = tree.splitlines()
    activities = {}
    for index, line in enumerate(lines):
        node = re.fullmatch(r'(\s*)E: activity(?:\s+.*)?', line)
        if not node:
            continue
        depth = len(node[1])
        attributes = []
        for following in lines[index + 1:]:
            following_depth = len(following) - len(following.lstrip())
            if following.strip() and following_depth <= depth:
                break
            if following_depth == depth + 2 and following.lstrip().startswith('A: '):
                attributes.append(following)
        direct = '\n'.join(attributes)
        names = re.findall(r'android:name[^\n]*?="([^"]+)"', direct)
        if len(names) != 1 or names[0] in activities:
            raise ValueError('Activity identity missing/duplicated')
        activities[names[0]] = direct
    for name in ['MainActivity', 'ModelManagementActivity', 'LogsActivity']:
        direct = activities.get('org.llmasr.minimal.' + name, '')
        if not re.search(r'android:enableOnBackInvokedCallback[^\n]*=true(?:\s|$)', direct):
            raise ValueError('Activity modern back opt-in missing: ' + name)


def verify(apk_dir, report_dir, native_build):
    baseline = json.loads((ROOT / 'gradle/baseline-inputs.json').read_text())
    subprocess.run(['python3', str(ROOT / 'tests/module_owners_test.py')], check=True)
    import importlib.util
    spec = importlib.util.spec_from_file_location('native_verify', ROOT / 'scripts/verify-native-artifact.py')
    native_verify = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(native_verify)
    native_result = native_verify.verify(native_build.absolute(), ROOT / '.work/native-session-reuse/sources')
    apks = sorted(apk_dir.glob('*.apk'))
    if len(apks) != 1:
        raise ValueError('Expected exactly one AGP APK, got ' + str(apks))
    apk = apks[0]
    sdk = Path(os.environ['ANDROID_HOME'])
    tools = sdk / 'build-tools/35.0.0'
    report = report_dir / ('run-' + uuid.uuid4().hex)
    report.mkdir(parents=True, exist_ok=False)
    commands = {
        'signature.txt': [str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)],
        'badging.txt': [str(tools / 'aapt2'), 'dump', 'badging', str(apk)],
        'permissions.txt': [str(tools / 'aapt2'), 'dump', 'permissions', str(apk)],
        'manifest-tree.txt': [str(tools / 'aapt2'), 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'],
        'ime-method-tree.txt': [str(tools / 'aapt2'), 'dump', 'xmltree', str(apk), '--file', 'res/xml/method.xml'],
    }
    for name, command in commands.items():
        with (report / name).open('w') as output:
            subprocess.run(command, stdout=output, check=True)
    subprocess.run([str(tools / 'zipalign'), '-c', '-p', '4', str(apk)], check=True)
    check_identity(apk, baseline, report)
    # Inspect AGP's actual compiled classes, not the legacy JNI check's classes.
    with tempfile.TemporaryDirectory(prefix='jni-classes-', dir=report) as temporary:
        classes = Path(temporary)
        class_roots = [ROOT / 'android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',
                       ROOT / 'android/app/build/tmp/kotlin-classes/debug',
                       ROOT / 'android/asr-runtime/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',
                       ROOT / 'android/asr-runtime/build/tmp/kotlin-classes/debug',
                       ROOT / 'core/build/classes/java/main',
                       ROOT / 'core/build/classes/kotlin/main']
        for source in class_roots:
            if not source.is_dir():
                raise ValueError('AGP class output missing: ' + str(source))
            for path in source.rglob('*.class'):
                destination = classes / path.relative_to(source)
                if destination.exists():
                    raise ValueError('Duplicate AGP class owner: ' + str(destination))
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, destination)
        native = Path(temporary) / 'libqwen_asr_jni.so'
        with zipfile.ZipFile(apk) as archive:
            native.write_bytes(archive.read('lib/arm64-v8a/libqwen_asr_jni.so'))
        subprocess.run(['python3', str(ROOT / 'scripts/check-jni-symbols.py'),
                        str(classes), str(native)], check=True)
    # Reuse the existing actual-checker contract, with freshly collected reports
    # bound to THIS APK. Never rewrite reports/apk or use a previous package report.
    with tempfile.TemporaryDirectory(prefix='legacy-check-', dir=report) as temporary:
        fixture = Path(temporary)
        (fixture / 'dist').mkdir()
        shutil.copyfile(apk, fixture / 'dist/qwen-asr-minimal-debug.apk')
        destination = fixture / 'reports/apk'
        destination.mkdir(parents=True)
        for name in commands:
            shutil.copyfile(report / name, destination / name)
        shutil.copyfile(native_build / 'artifact/native-link-provenance.json',
                        destination / 'native-link-provenance.json')
        for relative in ['android/app/assets/KOTLIN-LICENSE.txt',
                         'android/app/assets/KOTLIN-NOTICES.txt', 'reports/p0/mnn-model-manifest.json']:
            path = fixture / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, path)
        from apk_report_binding import payload
        binding = payload(fixture / 'dist/qwen-asr-minimal-debug.apk', destination)
        (destination / 'apk-report-binding.json').write_text(json.dumps(binding) + '\n')
        subprocess.run(['python3', str(ROOT / 'scripts/check-minimal-apk.py'), '--root', str(fixture)], check=True)
        shutil.copyfile(destination / 'apk-report-binding.json', report / 'apk-report-binding.json')
        shutil.copyfile(destination / 'native-link-provenance.json', report / 'native-link-provenance.json')
    shutil.copyfile(native_build / 'artifact/verification.json', report / 'native-verification.json')
    fingerprints = collect_build_inputs(ROOT)
    (report / 'build-input-sha256.json').write_text(json.dumps(fingerprints, indent=2) + '\n')
    result = {
        'schema': 1, 'artifact': str(apk.relative_to(ROOT)), 'bytes': apk.stat().st_size,
        'sha256': sha(apk), 'package': 'org.llmasr.minimal', 'version': '0.6-debug', 'version_code': 6,
        'signing_certificate_sha256': baseline['signing_certificate_sha256'],
        'native_sha256': baseline['native']['sha256'], 'product_baseline': baseline['source_commit'],
        'inputs_checked': len(fingerprints), 'device_verified': False,
        'external_inputs': {
            str((native_build / 'artifact/libqwen_asr_jni.so').relative_to(ROOT)): native_result['native_sha256'],
            baseline['sample']['path']: sha(ROOT / baseline['sample']['path']),
            'models/mnn-16/config.json': sha(ROOT / 'models/mnn-16/config.json'),
            'models/mnn-16/llm_config.json': sha(ROOT / 'models/mnn-16/llm_config.json'),
        },
        'toolchain': {'gradle': '8.7', 'agp': '8.6.1', 'kotlin': '2.0.21',
                      'java': subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT,
                                                      text=True).strip(),
                      'aapt2_sha256': sha(tools / 'aapt2'),
                      'apksigner_jar_sha256': sha(tools / 'lib/apksigner.jar')},
        'limitations': ['Native rebuilt via explicit CMake targets; 4KiB/16KiB runtime not run',
                        'Host/JNI checks are separate verifyDebug prerequisites',
                        'No device/microphone/SAF/IME runtime acceptance'],
    }
    (report / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print('PASS AGP actual APK signature/identity/assets/DEX/native/report binding')
    print('VERIFIED_REPORT ' + str(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk-dir', type=Path, required=True)
    parser.add_argument('--report-dir', type=Path, required=True)
    parser.add_argument('--native-build', type=Path, required=True)
    args = parser.parse_args()
    verify(args.apk_dir, args.report_dir, args.native_build)
