#!/usr/bin/env python3
"""Shell contract tests with isolated mock executables; not Gradle/Lint acceptance."""
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


with tempfile.TemporaryDirectory(prefix='verify-entry-') as temporary:
    root = Path(temporary)
    (root / 'scripts').mkdir()
    for name in ('verify-local.sh', 'verify-gradle.sh', 'nix-env.sh', 'check-fast-task-graph.py'):
        shutil.copyfile(ROOT / 'scripts' / name, root / 'scripts' / name)
    (root / 'gradle/wrapper').mkdir(parents=True)
    shutil.copyfile(ROOT / 'gradle/wrapper/gradle-wrapper.jar', root / 'gradle/wrapper/gradle-wrapper.jar')
    wrapper = root / 'gradlew'
    wrapper.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$@" > "$ARG_LOG"\nprintf "%s\\n" "${MOCK_OUTPUT:-MOCK_EXECUTED}"\nexit "${MOCK_EXIT:-0}"\n')
    wrapper.chmod(0o755)
    arguments = root / 'args.txt'
    env = dict(os.environ, ANDROID_HOME='/mock-sdk', ARG_LOG=str(arguments))

    def run(entry, *args, extra=None):
        arguments.unlink(missing_ok=True)
        return subprocess.run(['bash', str(root / 'scripts' / entry), *args], cwd=root,
                              env=dict(env, **(extra or {})), capture_output=True, text=True, timeout=15)

    for flag in ('--write-locks', '--write-locks=true', '--update-locks=a:b',
                 '--write-verification-metadata', '--dependency-verification=off',
                 '--dependency-verification', '-F', '-x', '--exclude-task=verifyLint',
                 '-I', '--init-script=evil', '-Pandroid.aapt2FromMavenOverride=evil',
                 '--dry-run', '--continue', '--max-workers=99', 'help', ':core:test',
                 '--write-ver', '--offline=true'):
        for entry, prefix in (('verify-local.sh', ('fast',)), ('verify-gradle.sh', ())):
            result = run(entry, *prefix, flag)
            require(result.returncode == 2 and 'unsupported verification argument' in result.stderr,
                    f'{entry} failed target refusal: {flag}: {result.stderr}')
            require(not arguments.exists(), f'Gradle invoked for rejected argument: {flag}')
    require(run('verify-local.sh', 'unknown').returncode == 2, 'unknown layer accepted')
    for layer, task in (('fast', 'verifyFast'), ('full', 'verifyDebug'),
                        ('lint', 'verifyLint'), ('format', 'verifyFormat'), ('graph-fast', 'verifyFast')):
        result = run('verify-local.sh', layer, '--offline', '--rerun-tasks',
                     extra={'MOCK_OUTPUT': ':verifyFast SKIPPED'} if layer == 'graph-fast' else None)
        require(result.returncode == 0, result.stderr)
        actual = arguments.read_text().splitlines()
        require(actual[0] == task and actual.count('--dependency-verification') == 1 and
                actual[actual.index('--dependency-verification') + 1] == 'strict', 'strict/task contract changed')
        require('--offline' in actual and '--rerun-tasks' in actual, 'allowed flags lost')
        require(('--dry-run' in actual) == (layer == 'graph-fast'), 'graph contract changed')
    require(run('verify-gradle.sh', 'clean', '--offline').returncode == 0, 'legacy full flags rejected')
    require(arguments.read_text().splitlines()[:2] == ['clean', 'verifyDebug'], 'legacy full gate changed')
    result = run('verify-local.sh', 'fast', extra={'MOCK_OUTPUT': 'WARNING: D8 metadata'})
    require(result.returncode == 1 and 'DEX diagnostics require review' in result.stderr, 'DEX warning accepted')
    result = run('verify-local.sh', 'graph-fast', extra={'MOCK_OUTPUT': ':app:buildNativeArtifact SKIPPED\n:verifyFast SKIPPED'})
    require(result.returncode == 1 and 'forbidden heavy task' in result.stderr, 'native fast graph accepted')
    result = run('verify-local.sh', 'fast', extra={'MOCK_EXIT': '7'})
    require(result.returncode == 7, 'Gradle failure hidden by tee')

    # Verify actual nix-env.sh argument construction, not an assumed env switch.
    for name in ('flake.nix', 'flake.lock'):
        shutil.copyfile(ROOT / name, root / name)
    binaries = root / 'bin'
    binaries.mkdir()
    nix = binaries / 'nix'
    nix.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$@" > "$ARG_LOG"\n')
    nix.chmod(0o755)
    result = run('nix-env.sh', 'apk', '--cache-only', 'bash', '-c', 'echo ready',
                 extra={'PATH': str(binaries) + os.pathsep + os.environ['PATH']})
    require(result.returncode == 0, result.stderr)
    actual = arguments.read_text().splitlines()
    require(actual[:12] == ['develop', '--offline', '--no-update-lock-file', '--no-write-lock-file',
                           '--option', 'substituters', '', '--option', 'builders', '', '--max-jobs', '0'], 'Nix cache-only boundary changed')
    require(actual[-4:] == ['--command', 'bash', '-c', 'echo ready'], 'Nix command forwarding changed')
# Static wiring regression complements (does NOT replace) the real dry-run graph.
build = (ROOT / 'build.gradle.kts').read_text()
fast = build.split('tasks.register("verifyFast") {', 1)[1].split('val verifyDependencyEvidence', 1)[0]
for token in (':core:test', ':asr-runtime:testDebugUnitTest', 'verifyArchitecture',
              'verifyFormat', 'verifyLocalGuards'):
    require(token in fast, f'fast gate lost: {token}')
for token in ('buildNativeArtifact', 'prepareInputs', 'verifyHost', 'verifyJni', 'verifyLint'):
    require(token not in fast, f'full/native gate directly added to fast: {token}')
full = build.split('tasks.register("verifyDebug") {', 1)[1]
for token in ('verifyHost', 'verifyBuildInputs', 'verifyNativeInputs', 'verifyApkChecker', 'verifyJni',
              ':core:test', ':asr-runtime:testDebugUnitTest', ':app:verifyDebugPackage',
              'verifyFormat', 'verifyLocalGuards', 'verifyLint', 'verifyDependencyEvidence', 'verifyLintEvidence'):
    require(token in full, f'full gate lost: {token}')
require(':app:lintDebug' in build and ':asr-runtime:lintDebug' in build, 'AGP lint wiring lost')
require('apply(from = "gradle/verify-lint-tools.gradle")' in build, 'actual Lint identity gate lost')
require('tests/gradle_lint_verification_test.py' in build and 'mustRunAfter(verifyHost' in build,
        'Lint evidence driver or heavy-child ordering lost')
print('PASS static fast/full/Lint/checksum wiring (not a real Gradle task graph)')
print('PASS local entry target refusals, strict/full/graph/DEX/exit contracts and Nix cache-only argument construction (mock executables)')
