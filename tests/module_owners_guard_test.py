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
APP_KOTLIN = Path('android/app/build/tmp/kotlin-classes/debug')
RUNTIME_KOTLIN = Path('android/asr-runtime/build/tmp/kotlin-classes/debug')
PREFIX = Path('org/llmasr/minimal')


class OwnersTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='owners-negative-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for path in [APP, APP_KOTLIN, RUNTIME, RUNTIME_KOTLIN, CORE, Path('core/build/classes/kotlin/main')]:
            if (ROOT / path).is_dir():
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

    def test_foreign_core_in_app_kotlin(self):
        source = self.root / CORE / PREFIX / 'model/ModelRepository.class'
        path = self.root / APP_KOTLIN / PREFIX / 'model/ModelRepository.class'
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, path)
        with self.assertRaisesRegex(AssertionError, 'owner|duplicate'):
            check(self.root)

    def test_missing_app_kotlin_top_owner(self):
        path = self.root / APP_KOTLIN / PREFIX / 'AppGraph.class'
        if not path.exists():
            self.skipTest('original Java output has no app Kotlin owner')
        path.unlink()
        with self.assertRaisesRegex(AssertionError, 'missing'):
            check(self.root)

    def test_app_java_kotlin_duplicate(self):
        source = self.root / APP_KOTLIN / PREFIX / 'AppGraph.class'
        if not source.exists():
            self.skipTest('original Java output has no app Kotlin owner')
        destination = self.root / APP / PREFIX / 'AppGraph.class'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        with self.assertRaisesRegex(AssertionError, 'duplicate'):
            check(self.root)

    def test_runtime_missing_direct_owner(self):
        (self.root / RUNTIME_KOTLIN / PREFIX / 'audio/ForegroundRecorder.class').unlink()
        with self.assertRaisesRegex(AssertionError, 'missing'):
            check(self.root)

    def test_runtime_java_kotlin_duplicate(self):
        source = self.root / RUNTIME_KOTLIN / PREFIX / 'audio/ForegroundRecorder.class'
        destination = self.root / RUNTIME / PREFIX / 'audio/ForegroundRecorder.class'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        with self.assertRaisesRegex(AssertionError, 'duplicate'):
            check(self.root)

    def test_foreign_owner_in_runtime_kotlin(self):
        for original in [self.root / CORE / PREFIX / 'model/ModelRepository.class',
                         self.root / APP_KOTLIN / PREFIX / 'AppGraph.class']:
            destination = self.root / RUNTIME_KOTLIN / PREFIX / ('model/ModelRepository.class' if original.stem == 'ModelRepository' else 'AppGraph.class')
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, destination)
            try:
                with self.assertRaisesRegex(AssertionError, 'owner|duplicate|extra'):
                    check(self.root)
            finally:
                destination.unlink()

    def test_missing_new_verification_owner(self):
        (self.root / 'core/build/classes/kotlin/main' / PREFIX / 'model/ModelVerification.class').unlink()
        with self.assertRaisesRegex(AssertionError, 'missing'):
            check(self.root)

    def test_android_proof_wrong_core_owner(self):
        original = self.root / RUNTIME_KOTLIN / PREFIX / 'model/AndroidModelVerificationIo.class'
        target = self.root / 'core/build/classes/kotlin/main' / PREFIX / 'model/AndroidModelVerificationIo.class'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, target)
        with self.assertRaisesRegex(AssertionError, 'owner|extra|platform|duplicate'):
            check(self.root)

    def test_missing_android_proof_owner(self):
        (self.root / RUNTIME_KOTLIN / PREFIX / 'model/AndroidModelVerificationIo.class').unlink()
        with self.assertRaisesRegex(AssertionError, 'missing'):
            check(self.root)

    def test_runtime_extra_top_owner(self):
        target = self.root / RUNTIME_KOTLIN / PREFIX / 'Unexpected.class'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b'not a declared owner')
        with self.assertRaisesRegex(AssertionError, 'owner|extra'):
            check(self.root)

    def test_extra_app_kotlin_owner(self):
        path = self.root / APP_KOTLIN / PREFIX / 'Unexpected.class'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'not a declared production owner')
        with self.assertRaisesRegex(AssertionError, 'extra|owner'):
            check(self.root)


if __name__ == '__main__':
    unittest.main()
