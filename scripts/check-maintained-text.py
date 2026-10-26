#!/usr/bin/env python3
"""Read-only whitespace/encoding gate for an explicit maintained-text inventory.
Not a Java/Kotlin style formatter; historical raw evidence is deliberately excluded.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = (
    'build.gradle.kts',
    'scripts/nix-env.sh',
    'scripts/verify-gradle.sh',
    'scripts/verify-local.sh',
    'scripts/check-maintained-text.py',
    'tests/kotlin_test_slice_test.py',
    'core/src/test/kotlin/CoreRegressionJUnitTest.kt',
    'android/asr-runtime/src/test/kotlin/RuntimeRegressionJUnitTest.kt',
    'tests/MinimalApkTest.kt',
    'tests/ResultFilesTest.kt',
    'tests/NativeResponseTest.kt',
    'tests/ModelOperationControlTest.kt',
    'tests/ModelRepositoryDeleteTest.kt',
    'tests/ModelPrivateRootTest.kt',
    'tests/TextExportFatalTest.kt',
    'tests/TextExportSafetyTest.kt',
    'tests/local_verify_entry_test.py',
    'tests/maintained_text_test.py',
    'tests/logs_activity_picker_test.py',
    'tests/AdmissionBoundaryTest.kt',
    'tests/ImeSessionTest.kt',
    'tests/InferenceAdapterTest.kt',
    'tests/LogExportTest.kt',
    'tests/ModelAndroidHelpersTest.kt',
    'tests/ModelManagementControllerTest.kt',
    'tests/ModelNotificationTest.kt',
    'tests/ModelProviderBoundaryTest.kt',
    'tests/ModelReadinessTest.kt',
    'tests/ModelRepositoryCancelTest.kt',
    'tests/ModelRepositoryTest.kt',
    'tests/ModelReviewFixTest.kt',
    'tests/RecordingRaceTest.kt',
    'tests/RequestRunnerTest.kt',
    'tests/RuntimeLogCoreTest.kt',
    'tests/RuntimeLogStoreErrorTest.kt',
    'tests/RuntimeLogWorkerErrorTest.kt',
    'tests/TaskCoordinatorTest.kt',
    'tests/TextExportTest.kt',
    'tests/runtime_log_enum_mutant_test.py',
    'tests/model_review_mutation_test.py',
    'core/build.gradle.kts',
    'reports/kotlin-tests-p9/status.md',
    'android/app/src/org/llmasr/minimal/LogsActivity.kt',
    'tests/kotlin_next_migration_test.py',
    'tests/fixtures/kotlin-next/NextAppContractTest.java',
    'tests/kotlin_runtime_migration_test.py',
    'tests/fixtures/kotlin-runtime/RuntimeContractTest.java',
    'android/app/src/org/llmasr/minimal/audio/ForegroundRecorder.kt',
    'android/app/src/org/llmasr/minimal/ime/ImeBackend.kt',
    'android/app/src/org/llmasr/minimal/model/SafModelSource.kt',
    'android/app/src/org/llmasr/minimal/platform/OperationContext.kt',
    'android/asr-runtime/build.gradle.kts',
    'android/asr-runtime/gradle.lockfile',
    'reports/kotlin-runtime-p7/status.md',
    'reports/kotlin-next-p5/status.md',
    'scripts/test-minimal-apk.sh',
    'reports/runtime-logs-picker/status.md',
    'scripts/check-fast-task-graph.py',
    'tests/fast_task_graph_test.py',
    'tests/local_verify_fault_test.py',
    'tests/dependency_checksum_fixture.py',
    'tests/dependency_checksum_fixture_test.py',
    'tests/gradle_dependency_verification_test.sh',
    'gradle/verify-lint-tools.gradle',
    'gradle/lint-tool-identity.json',
    'tests/gradle_lint_verification_test.py',
    'tests/lint_report_test.py',
    'tests/lint_identity_test.py',
    'tests/fixtures/lint-error/build.gradle',
    'tests/fixtures/lint-error/settings.gradle',
    'tests/fixtures/lint-error/src/main/AndroidManifest.xml',
    'tests/fixtures/lint-error/src/main/java/org/llmasr/lintfixture/LintProbe.java',
    'docs/local-verification.md',
    'docs/module-test-map.md',
    'settings.gradle.kts',
    'scripts/verify-gradle-apk.py',
    'scripts/verify-android-test-apk.py',
    'tests/android_test_apk_test.py',
    'tests/android_test_inputs_test.py',
    'tests/android_test_fixture_test.py',
    'reports/android-test-p1/status.md',
    'android/test-host/build.gradle.kts',
    'android/test-host/gradle.lockfile',
    'android/test-host/src/main/AndroidManifest.xml',
    'android/test-host/src/main/kotlin/org/llmasr/minimal/testhost/ImeHostActivity.kt',
    'android/test-host/src/androidTest/AndroidManifest.xml',
    'android/test-host/src/androidTest/kotlin/org/llmasr/minimal/testhost/FixtureInstrumentationTest.kt',
    'android/test-host/src/androidTest/java/org/llmasr/minimal/testhost/FakeDocumentsProvider.java',
    'android/test-host/src/test/kotlin/org/llmasr/minimal/testhost/FixtureContractTest.kt',
    'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FakePorts.kt',
    'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FixtureChecks.kt',
    'tests/kotlin_testhost_migration_test.py',
    'reports/kotlin-testhost-p10/status.md',
    'android/app/src/org/llmasr/minimal/audio/RecordingControl.java',
    'android/app/src/org/llmasr/minimal/task/RequestContext.java',
    'tests/KotlinMigrationTest.java',
    'tests/kotlin_to_java_contract_test.py',
    'tests/gradle_inputs_test.py',
    'reports/kotlin-to-java-p1/status.md',
    'reports/ime-return-23p/status.md',
    'android/app/src/org/llmasr/minimal/model/ModelVerification.kt',
    'android/app/src/org/llmasr/minimal/model/AndroidModelVerificationIo.kt',
    'tests/model_verification_contract_test.py',
    'tests/android_model_verification_io_test.py',
    'reports/model-verification-zcm/status.md',
    'reports/model-verification-zcm/targeted-inputs.json',
)


def maintained_inventory(root):
    # Share the same live module collector; newly added source is checked too.
    import importlib.util
    if str(ROOT / 'scripts') not in sys.path:
        sys.path.insert(0, str(ROOT / 'scripts'))
    spec = importlib.util.spec_from_file_location('android_text_inputs', ROOT / 'scripts/verify-android-test-apk.py')
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    return sorted(set(INVENTORY).union(checker.module_inputs(root)))


def check_bytes(data, label):
    try:
        text = data.decode('utf-8')
    except UnicodeDecodeError as error:
        raise ValueError(f'{label}: invalid UTF-8') from error
    if '\r' in text:
        raise ValueError(f'{label}: CR line ending')
    if '\x00' in text:
        raise ValueError(f'{label}: NUL byte')
    if not text.endswith('\n'):
        raise ValueError(f'{label}: missing final newline')
    for number, line in enumerate(text.split('\n'), 1):
        if line.endswith((' ', '\t')):
            raise ValueError(f'{label}:{number}: trailing whitespace')
        if line.startswith('\t'):
            raise ValueError(f'{label}:{number}: tab indentation')


def check_inventory(root):
    inventory = maintained_inventory(root)
    for relative in inventory:
        path = root / relative
        if path.is_symlink() or not path.is_file():
            raise ValueError(f'{relative}: missing file or symlink')
        check_bytes(path.read_bytes(), relative)
    print(f'PASS maintained text: {len(inventory)} explicit/live module files (read-only)')


if __name__ == '__main__':
    try:
        check_inventory(ROOT)
    except (ValueError, OSError) as error:
        print(f'FATAL: {error}', file=sys.stderr)
        sys.exit(1)
