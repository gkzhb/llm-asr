#!/usr/bin/env python3
"""Fail-closed checks over an existing public development APK; no device/key access."""
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
spec = importlib.util.spec_from_file_location('gradle_apk_verify', ROOT / 'scripts/verify-gradle-apk.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class IdentityTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.report = Path(self.directory.name)
        self.baseline = json.loads((ROOT / 'gradle/baseline-inputs.json').read_text())
        self.apk = Path(os.environ.get('GRADLE_TEST_APK', ROOT / 'dist/qwen-asr-minimal-debug.apk'))
        # Gradle task tests its fresh AGP package, not a required old dist APK.
        # Standalone fallback still permits checker development before AGP exists.
        if 'GRADLE_TEST_APK' in os.environ:
            tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
            for name, command in {
                'signature.txt': [str(tools / 'apksigner'), 'verify', '--print-certs', str(self.apk)],
                'manifest-tree.txt': [str(tools / 'aapt2'), 'dump', 'xmltree', str(self.apk),
                                      '--file', 'AndroidManifest.xml'],
            }.items():
                (self.report / name).write_text(subprocess.check_output(command, text=True))
        else:
            for name in ['signature.txt', 'manifest-tree.txt']:
                shutil.copyfile(ROOT / 'reports/apk' / name, self.report / name)

    def test_real_verify_uses_only_current_prepared_root(self):
        # Observe the actual verify() native boundary. Stop there deliberately:
        # this fixture is not native execution or a substitute for real ELF gates.
        class StopAfterNativeBoundary(Exception):
            pass
        called = []
        def intercept(build, prepared):
            called.append((build, prepared))
            if prepared != ROOT / '.work/native-session-reuse/sources':
                raise AssertionError('APK verifier selected stale/override prepared root')
            raise StopAfterNativeBoundary()
        original_loader = importlib.util.spec_from_file_location
        def loader(name, location, *args, **kwargs):
            spec = original_loader(name, location, *args, **kwargs)
            if name == 'native_verify':
                original_exec = spec.loader.exec_module
                def execute(loaded):
                    original_exec(loaded)
                    loaded.verify = intercept
                spec.loader.exec_module = execute
            return spec
        build = self.report / 'native-build'
        with patch.object(importlib.util, 'spec_from_file_location', side_effect=loader), \
             patch.object(module.subprocess, 'run') as module_owner:
            with self.assertRaises(StopAfterNativeBoundary):
                module.verify(self.report / 'apk', self.report / 'reports', build)
            module_owner.assert_called_once_with(['python3', str(ROOT / 'tests/module_owners_test.py')], check=True)
        self.assertEqual(called, [(build.absolute(), ROOT / '.work/native-session-reuse/sources')])

    def test_zip_owner_definitions_required_in_actual_dex(self):
        # Use the actual parser over the current APK, remove one owner definition
        # at the parser seam; no fake ZIP bytes or string grep can satisfy this gate.
        original = module.defined_classes
        for name in ('FixedModelPackage', 'ModelZipArchive', 'ModelZipSource', 'SafModelZipSource'):
            descriptor = ('Lorg/llmasr/minimal/model/' + name + ';').encode()
            def without_owner(data, descriptor=descriptor):
                return original(data) - {descriptor}
            with patch.object(module, 'defined_classes', side_effect=without_owner):
                with self.assertRaisesRegex(ValueError, 'owner definition missing.*' + name):
                    module.check_identity(self.apk, self.baseline, self.report)

    def test_positive_existing_apk(self):
        module.check_identity(self.apk, self.baseline, self.report)

    def test_policy_guard_rejects_borrowed_sibling_and_nested_attributes(self):
        tree = (self.report / 'manifest-tree.txt').read_text()
        module.check_activity_back_policy(tree)
        lines = tree.splitlines()
        # Last Activity must not borrow the following Service's callback flag.
        start = next(i for i, line in enumerate(lines) if '="org.llmasr.minimal.LogsActivity"' in line)
        flag = next(i for i in range(start + 1, len(lines)) if 'android:enableOnBackInvokedCallback' in lines[i])
        callback = lines.pop(flag)
        service = next(i for i in range(start + 1, len(lines)) if 'E: service' in lines[i])
        lines.insert(service + 1, callback)
        with self.assertRaisesRegex(ValueError, 'LogsActivity'):
            module.check_activity_back_policy('\n'.join(lines))
        lines = tree.splitlines()
        flag = next(i for i in range(start + 1, len(lines)) if 'android:enableOnBackInvokedCallback' in lines[i])
        lines[flag] = '    ' + lines[flag]
        with self.assertRaisesRegex(ValueError, 'LogsActivity'):
            module.check_activity_back_policy('\n'.join(lines))

    def test_changed_native_expectation_rejected(self):
        self.baseline['native']['sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'native payload'):
            module.check_identity(self.apk, self.baseline, self.report)

    def test_changed_certificate_rejected(self):
        self.baseline['signing_certificate_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'Signing identity'):
            module.check_identity(self.apk, self.baseline, self.report)

    def test_missing_kotlin_owner_rejected(self):
        self.baseline['files']['android/app/src/org/llmasr/minimal/Missing.kt'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'owner definition missing'):
            module.check_identity(self.apk, self.baseline, self.report)

    def test_missing_java_owner_rejected(self):
        self.baseline['files']['android/app/src/org/llmasr/minimal/Missing.java'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'owner definition missing'):
            module.check_identity(self.apk, self.baseline, self.report)

    def test_duplicate_across_dex_rejected(self):
        duplicate = self.report / 'duplicate.apk'
        with zipfile.ZipFile(self.apk) as source, zipfile.ZipFile(duplicate, 'w') as target:
            for name in source.namelist():
                target.writestr(name, source.read(name))
            target.writestr('classes99.dex', source.read('classes.dex'))
        with self.assertRaisesRegex(ValueError, 'Duplicate production/runtime owner across DEX'):
            module.check_identity(duplicate, self.baseline, self.report)

    def test_activity_back_opt_in_rejected(self):
        self.baseline['revision_scope'] = 'M6 actual callback policy'
        path = self.report / 'manifest-tree.txt'
        path.write_text(path.read_text().replace('android:enableOnBackInvokedCallback',
                                               'android:incorrectBackCallback'))
        with self.assertRaisesRegex(ValueError, 'modern back opt-in missing'):
            module.check_identity(self.apk, self.baseline, self.report)

    def test_backup_policy_rejected(self):
        path = self.report / 'manifest-tree.txt'
        path.write_text(path.read_text().replace('android:allowBackup', 'android:incorrectBackup'))
        with self.assertRaisesRegex(ValueError, 'allowBackup'):
            module.check_identity(self.apk, self.baseline, self.report)


if __name__ == '__main__':
    unittest.main()
