#!/usr/bin/env python3
"""Production M1 staging checks, using public fixture assets (no keys/weights)."""
import importlib.util
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from manifest_permission_test import check_manifest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('gradle_inputs', ROOT / 'scripts/prepare-gradle-inputs.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class InputTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.baseline = json.loads((ROOT / 'gradle/baseline-inputs.json').read_text())
        files = set(self.baseline['files']) | {self.baseline['sample']['path']}
        files.add('gradle/baseline-inputs.json')
        self.native = self.root / 'current-native/libqwen_asr_jni.so'
        self.native.parent.mkdir()
        shutil.copyfile(Path(os.environ.get('NATIVE_TEST_ARTIFACT',
                        ROOT / self.baseline['native']['path'])), self.native)
        for relative in files:
            destination = self.root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, destination)
        # Separate config verifier already has real filesystem/CLI coverage;
        # here mock only that subprocess, not production baseline/staging logic.
        self.subprocess = patch.object(module.subprocess, 'run')
        self.command = self.subprocess.start()
        self.addCleanup(self.subprocess.stop)

    def test_actual_staging_and_manifest(self):
        output = self.root / 'build'
        # The previous transition path is absent and must not be read.
        self.assertFalse((self.root / self.baseline['native']['path']).exists())
        module.stage(self.root, output, self.native)
        self.assertEqual(module.digest(output / 'jniLibs/arm64-v8a/libqwen_asr_jni.so'),
                         self.baseline['native']['sha256'])
        manifest = module.ET.parse(output / 'AndroidManifest.xml').getroot()
        self.assertNotIn('package', manifest.attrib)
        self.assertIsNone(manifest.find('uses-sdk'))
        self.assertEqual(len(manifest.find('application').findall('activity')), 3)
        self.assertEqual(len(manifest.find('application').findall('service')), 1)
        check_manifest(manifest, 'app-staged')
        self.command.assert_called_once()

    def test_staged_debug_owned_by_dsl_only(self):
        output = self.root / 'build'
        original = (self.root / 'android/app/AndroidManifest.xml').read_bytes()
        module.stage(self.root, output, self.native)
        source = module.ET.fromstring(original)
        staged = module.ET.parse(output / 'AndroidManifest.xml').getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        self.assertEqual(source.find('application').get(android + 'debuggable'), 'true')
        self.assertNotIn(android + 'debuggable', staged.find('application').attrib)
        # Whole XML tree equality after exactly the existing AGP-owned fields
        # and the newly redundant debug field are removed. Permissions/components
        # and all other policies must remain exact, not just component counts.
        for key in ('package', android + 'versionCode', android + 'versionName'):
            source.attrib.pop(key, None)
        source.remove(source.find('uses-sdk'))
        for key in ('extractNativeLibs', 'debuggable'):
            source.find('application').attrib.pop(android + key, None)
        self.assertEqual(module.ET.tostring(staged), module.ET.tostring(source))
        self.assertEqual((self.root / 'android/app/AndroidManifest.xml').read_bytes(), original)
        self.assertIn('isDebuggable = true', (ROOT / 'android/app/build.gradle.kts').read_text())
        # Hardcoding a different value in the source is product drift, not a
        # reason to accept arbitrary debug/permission/component changes.
        (self.root / 'android/app/AndroidManifest.xml').write_bytes(
            original.replace(b'android:debuggable="true"', b'android:debuggable="false"'))
        with self.assertRaisesRegex(ValueError, 'Frozen product input mismatch'):
            module.stage(self.root, self.root / 'rejected', self.native)
        self.assertFalse((self.root / 'rejected').exists())

    def test_selected_java_omission_rejected_before_output(self):
        for owner in ('audio/RecordingControl', 'task/RequestContext'):
            path = self.root / ('android/app/src/org/llmasr/minimal/' + owner + '.java')
            original = path.read_bytes()
            path.unlink()
            output = self.root / 'missing-selected-output'
            with self.assertRaisesRegex(ValueError, 'Frozen product inventory mismatch'):
                module.stage(self.root, output, self.native)
            self.assertFalse(output.exists())
            path.write_bytes(original)

    def test_selected_java_drift_rejected_before_output(self):
        for owner in ('audio/RecordingControl', 'task/RequestContext'):
            path = self.root / ('android/app/src/org/llmasr/minimal/' + owner + '.java')
            original = path.read_bytes()
            path.write_bytes(original + b'// synthetic drift\n')
            output = self.root / 'drift-selected-output'
            with self.assertRaisesRegex(ValueError, 'Frozen product input mismatch'):
                module.stage(self.root, output, self.native)
            self.assertFalse(output.exists())
            path.write_bytes(original)

    def test_resurrected_kotlin_owner_rejected_before_output(self):
        for owner in ('audio/RecordingControl', 'task/RequestContext'):
            duplicate = self.root / ('android/app/src/org/llmasr/minimal/' + owner + '.kt')
            duplicate.write_text('// synthetic duplicate old owner\n')
            output = self.root / 'duplicate-selected-output'
            with self.assertRaisesRegex(ValueError, 'Frozen product inventory mismatch'):
                module.stage(self.root, output, self.native)
            self.assertFalse(output.exists())
            duplicate.unlink()

    def test_verification_owners_missing_drift_duplicate(self):
        for owner in ('ModelVerification', 'AndroidModelVerificationIo'):
            path = self.root / ('android/app/src/org/llmasr/minimal/model/' + owner + '.kt')
            original = path.read_bytes()
            output = self.root / ('reject-' + owner)
            path.unlink()
            with self.assertRaisesRegex(ValueError, 'Frozen product inventory mismatch'):
                module.stage(self.root, output, self.native)
            self.assertFalse(output.exists())
            path.write_bytes(original + b'// synthetic drift\n')
            with self.assertRaisesRegex(ValueError, 'Frozen product input mismatch'):
                module.stage(self.root, output, self.native)
            self.assertFalse(output.exists())
            path.write_bytes(original)
            duplicate = path.with_suffix('.java')
            duplicate.write_text('// synthetic duplicate owner\n')
            with self.assertRaisesRegex(ValueError, 'Frozen product inventory mismatch'):
                module.stage(self.root, output, self.native)
            self.assertFalse(output.exists())
            duplicate.unlink()

    def test_added_asset_rejected(self):
        (self.root / 'android/app/assets/extra.txt').write_text('fixture')
        with self.assertRaisesRegex(ValueError, 'inventory mismatch'):
            module.stage(self.root, self.root / 'output', self.native)
        self.assertFalse((self.root / 'output').exists())

    def test_native_drift_rejected_before_output(self):
        self.native.write_bytes(b'wrong')
        with self.assertRaisesRegex(ValueError, 'artifact hash mismatch'):
            module.stage(self.root, self.root / 'output', self.native)
        self.assertFalse((self.root / 'output').exists())

    def test_product_drift_rejected_before_output(self):
        (self.root / 'native/apk/asr_jni.cpp').write_text('changed')
        with self.assertRaisesRegex(ValueError, 'Frozen product input mismatch'):
            module.stage(self.root, self.root / 'output', self.native)
        self.assertFalse((self.root / 'output').exists())

    def test_native_symlink_rejected(self):
        native = self.native
        native.unlink()
        native.symlink_to(ROOT / self.baseline['native']['path'])
        with self.assertRaisesRegex(ValueError, 'artifact hash mismatch'):
            module.stage(self.root, self.root / 'output', self.native)

    def test_stale_asset_rejected(self):
        output = self.root / 'output'
        (output / 'assets').mkdir(parents=True)
        (output / 'assets/private.txt').write_text('fixture')
        with self.assertRaisesRegex(ValueError, 'stale generated asset'):
            module.stage(self.root, output, self.native)

    def test_descendant_symlink_rejected(self):
        output = self.root / 'output'
        output.mkdir()
        target = self.root / 'target'
        target.mkdir()
        (output / 'assets').symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'descendants'):
            module.stage(self.root, output, self.native)
        self.assertEqual(list(target.iterdir()), [])

    def test_output_symlink_rejected(self):
        target = self.root / 'target'
        target.mkdir()
        output = self.root / 'output'
        output.symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            module.stage(self.root, output, self.native)
        self.assertEqual(list(target.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
