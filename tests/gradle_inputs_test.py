#!/usr/bin/env python3
"""Production M1 staging checks, using public fixture assets (no keys/weights)."""
import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

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
        files = set(self.baseline['files']) | {self.baseline[key]['path'] for key in ['native', 'sample']}
        files.add('gradle/baseline-inputs.json')
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
        module.stage(self.root, output)
        self.assertEqual(module.digest(output / 'jniLibs/arm64-v8a/libqwen_asr_jni.so'),
                         self.baseline['native']['sha256'])
        manifest = module.ET.parse(output / 'AndroidManifest.xml').getroot()
        self.assertNotIn('package', manifest.attrib)
        self.assertIsNone(manifest.find('uses-sdk'))
        self.assertEqual(len(manifest.find('application').findall('activity')), 3)
        self.assertEqual(len(manifest.find('application').findall('service')), 1)
        self.command.assert_called_once()

    def test_added_asset_rejected(self):
        (self.root / 'android/app/assets/extra.txt').write_text('fixture')
        with self.assertRaisesRegex(ValueError, 'inventory mismatch'):
            module.stage(self.root, self.root / 'output')
        self.assertFalse((self.root / 'output').exists())

    def test_native_drift_rejected_before_output(self):
        (self.root / self.baseline['native']['path']).write_bytes(b'wrong')
        with self.assertRaisesRegex(ValueError, 'artifact hash mismatch'):
            module.stage(self.root, self.root / 'output')
        self.assertFalse((self.root / 'output').exists())

    def test_product_drift_rejected_before_output(self):
        (self.root / 'native/apk/asr_jni.cpp').write_text('changed')
        with self.assertRaisesRegex(ValueError, 'Frozen product input mismatch'):
            module.stage(self.root, self.root / 'output')
        self.assertFalse((self.root / 'output').exists())

    def test_native_symlink_rejected(self):
        native = self.root / self.baseline['native']['path']
        native.unlink()
        native.symlink_to(ROOT / self.baseline['native']['path'])
        with self.assertRaisesRegex(ValueError, 'artifact hash mismatch'):
            module.stage(self.root, self.root / 'output')

    def test_stale_asset_rejected(self):
        output = self.root / 'output'
        (output / 'assets').mkdir(parents=True)
        (output / 'assets/private.txt').write_text('fixture')
        with self.assertRaisesRegex(ValueError, 'stale generated asset'):
            module.stage(self.root, output)

    def test_descendant_symlink_rejected(self):
        output = self.root / 'output'
        output.mkdir()
        target = self.root / 'target'
        target.mkdir()
        (output / 'assets').symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'descendants'):
            module.stage(self.root, output)
        self.assertEqual(list(target.iterdir()), [])

    def test_output_symlink_rejected(self):
        target = self.root / 'target'
        target.mkdir()
        output = self.root / 'output'
        output.symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            module.stage(self.root, output)
        self.assertEqual(list(target.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
