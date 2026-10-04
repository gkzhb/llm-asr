#!/usr/bin/env python3
"""Exercise actual module-owner checker using copies of current compiled outputs."""
import shutil
import tempfile
import unittest
from pathlib import Path
from module_owners_test import check, ROOT

APP = Path('android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes')
RUNTIME = Path('android/asr-runtime/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes')
CORE = Path('core/build/classes/java/main')
PREFIX = Path('org/llmasr/minimal')


class OwnersTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='owners-negative-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for path in [APP, RUNTIME, CORE, Path('core/build/classes/kotlin/main')]:
            shutil.copytree(ROOT / path, self.root / path)
        baseline = self.root / 'gradle/baseline-inputs.json'
        baseline.parent.mkdir()
        shutil.copyfile(ROOT / 'gradle/baseline-inputs.json', baseline)

    def test_current_positive(self):
        check(self.root)

    def test_missing_owner(self):
        (self.root / CORE / PREFIX / 'model/ModelRepository.class').unlink()
        with self.assertRaisesRegex(AssertionError, 'missing'):
            check(self.root)

    def test_recompiled_dependency_duplicate(self):
        source = self.root / CORE / PREFIX / 'model/ModelRepository.class'
        destination = self.root / RUNTIME / PREFIX / 'model/ModelRepository.class'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        with self.assertRaisesRegex(AssertionError, 'direct production owner missing/extra|owner mismatch|duplicate'):
            check(self.root)

    def test_platform_reference_in_core(self):
        path = self.root / CORE / PREFIX / 'model/ModelRepository.class'
        path.write_bytes(path.read_bytes() + b'android/content/Context')
        with self.assertRaisesRegex(AssertionError, 'platform reference'):
            check(self.root)

    def test_old_app_kotlin_classes(self):
        path = self.root / 'android/app/build/tmp/kotlin-classes/debug/Stale.class'
        path.parent.mkdir(parents=True)
        path.write_bytes(b'fixture')
        with self.assertRaisesRegex(AssertionError, 'retain core Kotlin'):
            check(self.root)


if __name__ == '__main__':
    unittest.main()
