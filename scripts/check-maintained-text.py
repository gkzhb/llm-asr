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
    'tests/local_verify_entry_test.py',
    'tests/maintained_text_test.py',
    'tests/logs_activity_picker_test.py',
    'tests/LogExportTest.java',
    'android/app/src/org/llmasr/minimal/LogsActivity.kt',
    'tests/kotlin_next_migration_test.py',
    'tests/fixtures/kotlin-next/NextAppContractTest.java',
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
    'android/test-host/src/main/java/org/llmasr/minimal/testhost/ImeHostActivity.java',
    'android/test-host/src/androidTest/AndroidManifest.xml',
    'android/test-host/src/androidTest/java/org/llmasr/minimal/testhost/FixtureInstrumentationTest.java',
    'android/test-host/src/androidTest/java/org/llmasr/minimal/testhost/FakeDocumentsProvider.java',
    'android/test-host/src/test/java/org/llmasr/minimal/testhost/FixtureContractTest.java',
    'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FakePorts.java',
    'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FixtureChecks.java',
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
