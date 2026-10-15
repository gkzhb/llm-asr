#!/usr/bin/env python3
"""Invoke the real shell entry with isolated executable faults, not real Gradle."""
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures = []
with tempfile.TemporaryDirectory(prefix='verify-fault-') as temporary:
    root = Path(temporary)
    (root / 'scripts').mkdir()
    for name in ('verify-local.sh', 'verify-gradle.sh', 'check-fast-task-graph.py'):
        if (ROOT / 'scripts' / name).exists():
            shutil.copyfile(ROOT / 'scripts' / name, root / 'scripts' / name)
    (root / 'gradle/wrapper').mkdir(parents=True)
    shutil.copyfile(ROOT / 'gradle/wrapper/gradle-wrapper.jar', root / 'gradle/wrapper/gradle-wrapper.jar')
    wrapper = root / 'gradlew'
    wrapper.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "${MOCK_OUTPUT:-SAFE}"\nexit "${MOCK_EXIT:-0}"\n')
    wrapper.chmod(0o755)
    binaries = root / 'bin'
    binaries.mkdir()
    env = dict(os.environ, ANDROID_HOME='/mock-sdk')

    def invoke(label, mode, expected, diagnostic='', extra=None):
        result = subprocess.run(['bash', str(root / 'scripts/verify-local.sh'), mode, '--offline'],
                                cwd=root, env=dict(env, **(extra or {})), capture_output=True,
                                text=True, timeout=20)
        good = result.returncode == expected and diagnostic in result.stderr
        print(f'{label}: actual={result.returncode} expected={expected} target={diagnostic!r} ' +
              ('PASS' if good else 'FAIL'))
        if not good:
            failures.append(label)
            print(result.stdout + result.stderr)

    tee = binaries / 'tee'
    tee.write_text('#!/usr/bin/env bash\n"$REAL_TEE" "$@"\nexit 23\n')
    tee.chmod(0o755)
    fault_env = {'PATH': str(binaries) + os.pathsep + os.environ['PATH'], 'REAL_TEE': shutil.which('tee')}
    invoke('tee failure', 'fast', 23, 'log capture failed', fault_env)
    invoke('Gradle and tee arbitration', 'fast', 7, 'log capture failed', dict(fault_env, MOCK_EXIT='7'))
    tee.unlink()
    invoke('tee recovery', 'fast', 0)
    tee.write_text('#!/usr/bin/env bash\n"$REAL_TEE" "$@"\nrm -- "$1"\n')
    tee.chmod(0o755)
    invoke('grep log read failure', 'fast', 2, 'DEX log scan failed', fault_env)
    tee.unlink()
    invoke('log read recovery', 'fast', 0)
    grep = binaries / 'grep'
    grep.write_text('#!/usr/bin/env bash\nexit 2\n')
    grep.chmod(0o755)
    invoke('grep execution failure', 'fast', 2, 'DEX log scan failed', fault_env)
    grep.unlink()
    invoke('grep recovery', 'fast', 0)
    invoke('DEX target', 'fast', 1, 'DEX diagnostics require review', {'MOCK_OUTPUT': 'WARNING: D8 metadata'})
    for task in ('lintAnalyzeDebug', 'verifyDependencyEvidence', 'verifyHost', 'verifyJniObject',
                 'configureCMakeDebug', 'buildNativeArtifact', 'assembleDebug', 'packageDebug'):
        # Final graph output includes all tasks even for indirect dependencies.
        graph = f':tools:{task} SKIPPED\n:verifyArchitecture SKIPPED\n:verifyFast SKIPPED'
        invoke('graph forbidden ' + task, 'graph-fast', 1, 'forbidden heavy task', {'MOCK_OUTPUT': graph})
    invoke('unknown core task', 'graph-fast', 1, 'unreviewed task path',
           {'MOCK_OUTPUT': ':core:downloadArtifact SKIPPED\n:verifyFast SKIPPED'})
    for prefix in (' ', '\t'):
        invoke('leading whitespace task ' + repr(prefix), 'graph-fast', 1, 'unrecognized',
               {'MOCK_OUTPUT': prefix + ':core:compileJava SKIPPED\n:verifyFast SKIPPED'})
    invoke('graph recovery', 'graph-fast', 0, extra={'MOCK_OUTPUT': ':verifyFast SKIPPED'})
if failures:
    raise AssertionError('target fault integration failures: ' + ', '.join(failures))
print('PASS real-entry executable-fault/graph target refusals and recovery (mock Gradle)')
