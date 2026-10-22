#!/usr/bin/env python3
"""Exercise real graph parser on complete output fixtures (not real Gradle)."""
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('graph', ROOT / 'scripts/check-fast-task-graph.py')
graph = importlib.util.module_from_spec(spec)
spec.loader.exec_module(graph)
good = ':core:compileJava SKIPPED\n:asr-runtime:packageDebugResources SKIPPED\n:asr-runtime:bundleLibCompileToJarDebug SKIPPED\n:asr-runtime:bundleLibRuntimeToJarDebug SKIPPED\n:verifyFast SKIPPED\nBUILD SUCCESSFUL'
graph.check_text(good)
for name in ('lintDebug', 'lintAnalyzeDebug', 'lintReportDebug', 'verifyLintEvidence',
             'verifyDependencyEvidence', 'checksumMutation', 'verifyHost', 'verifyJni',
             'compileJniObject', 'buildNativeArtifact', 'prepareInputs', 'verifyNativeInputs',
             'externalNativeBuildDebug', 'configureCMakeDebug', 'buildCMakeDebug',
             'configureNdkBuildDebug', 'buildNdkBuildDebug', 'assembleDebug', 'bundleDebug',
             'packageDebug', 'packageDebugApk', 'installDebug', 'connectedDebugAndroidTest', 'verifyApkChecker',
             'verifyDebugPackage', 'verifyBuildInputs'):
    for prefix in (':', ':core:', ':asr-runtime:', ':helper:nested:'):
        text = prefix + name + ' SKIPPED\n' + good
        try:
            graph.check_text(text)
        except ValueError as error:
            if 'forbidden heavy task' not in str(error) or prefix + name not in str(error):
                raise AssertionError('wrong heavy diagnostic: ' + str(error)) from error
        else:
            raise AssertionError('heavy graph accepted: ' + prefix + name)
for text, diagnostic in (
                         (':core:downloadArtifact SKIPPED\n:verifyFast SKIPPED', 'unreviewed task path'),
                         (':asr-runtime:resolveUnknown SKIPPED\n:verifyFast SKIPPED', 'unreviewed task path'),
                         (' :core:compileJava SKIPPED\n:verifyFast SKIPPED', 'unrecognized'),
                         ('\t:core:compileJava SKIPPED\n:verifyFast SKIPPED', 'unrecognized'),
                         (' :app:assembleDebug SKIPPED\n:verifyFast SKIPPED', 'unrecognized'),
                         ('', 'exactly one'), (':verifyFast EXECUTED', 'unrecognized'),
                         (':verifyFast SKIPPED\n:verifyFast SKIPPED', 'exactly one'),
                         (':helper SKIPPED\n:verifyFast SKIPPED', 'unreviewed root'),
                         (':app:compileJava SKIPPED\n:verifyFast SKIPPED', 'unreviewed module')):
    try:
        graph.check_text(text)
    except ValueError as error:
        if diagnostic not in str(error):
            raise AssertionError('wrong graph rejection: ' + str(error)) from error
    else:
        raise AssertionError('malformed/unreviewed graph accepted')
graph.check_text(good)
# P1 observed and explicitly accepted runtime Kotlin task paths, not a name prefix.
for name in ('checkKotlinGradlePluginConfigurationErrors', 'compileDebugKotlin', 'compileDebugUnitTestKotlin'):
    graph.check_text(':asr-runtime:' + name + ' SKIPPED\n' + good)
for name in ('compileFutureKotlin', 'kotlinDownloadArtifact'):
    try:
        graph.check_text(':asr-runtime:' + name + ' SKIPPED\n' + good)
    except ValueError as error:
        if 'unreviewed task path' not in str(error):
            raise AssertionError('wrong unknown Kotlin task rejection') from error
    else:
        raise AssertionError('Kotlin task prefix was accepted: ' + name)
print('PASS complete graph parser heavy-family/scope/malformed target negatives and recovery')
