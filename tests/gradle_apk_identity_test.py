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
from pathlib import Path

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

    def test_positive_existing_apk(self):
        module.check_identity(self.apk, self.baseline, self.report)

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

    def test_backup_policy_rejected(self):
        path = self.report / 'manifest-tree.txt'
        path.write_text(path.read_text().replace('android:allowBackup', 'android:incorrectBackup'))
        with self.assertRaisesRegex(ValueError, 'allowBackup'):
            module.check_identity(self.apk, self.baseline, self.report)


if __name__ == '__main__':
    unittest.main()
