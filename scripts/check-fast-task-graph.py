#!/usr/bin/env python3
"""Parse complete Gradle plain dry-run output; reject heavy task families."""
import re
import sys
from pathlib import Path

TASK_LINE = re.compile(r'(:[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*)[ \t]+SKIPPED[ \t]*')
ROOT_TASKS = {'verifyFast', 'verifyArchitecture', 'verifyFormat', 'verifyLocalGuards', 'clean'}
# These are dependency JAR staging, not Android APK/AAB assembly.
LIBRARY_JARS = {'bundleLibCompileToJarDebug', 'bundleLibRuntimeToJarDebug'}
# Observed main fast graph: library resource staging, no APK/packageDebug task.
LIBRARY_RESOURCE_STAGE = {(':asr-runtime', 'packageDebugResources')}
# Approved from graph-fast-r67jJS (35 tasks). Adding/changing a path requires
# an actual graph, task semantic review and this explicit approval point.
APPROVED_PATHS = {':' + task for task in ROOT_TASKS} | {
    ':core:' + task for task in (
        'checkKotlinGradlePluginConfigurationErrors', 'compileKotlin', 'compileJava',
        'processResources', 'classes', 'jar', 'compileTestKotlin', 'compileTestJava',
        'processTestResources', 'testClasses', 'verifyModuleBoundary', 'test')
} | {
    ':asr-runtime:' + task for task in (
        'preBuild', 'preDebugBuild', 'generateDebugResValues', 'generateDebugResources',
        'packageDebugResources', 'parseDebugLocalResources', 'generateDebugRFile',
        'javaPreCompileDebug', 'compileDebugJavaWithJavac', 'bundleLibRuntimeToJarDebug',
        'bundleLibCompileToJarDebug', 'preDebugUnitTestBuild', 'generateDebugUnitTestStubRFile',
        'javaPreCompileDebugUnitTest', 'compileDebugUnitTestJavaWithJavac', 'processDebugJavaRes',
        'processDebugUnitTestJavaRes', 'verifyModuleBoundary', 'testDebugUnitTest')
}
HEAVY = re.compile(
    r'lint|checksum|dependencyEvidence|host|jni|native|cmake|ndkbuild|'
    r'^(?:assemble|bundle|package|install|uninstall|connected|device)|'
    r'^verify(?:Apk|Debug|BuildInputs)|^prepareInputs$', re.I)


def parse_tasks(text):
    tasks = []
    for line in text.splitlines():
        if line.lstrip().startswith(':'):
            match = TASK_LINE.fullmatch(line)
            if not match:
                raise ValueError('unrecognized dry-run task line: ' + line)
            tasks.append(match[1])
    if tasks.count(':verifyFast') != 1:
        raise ValueError('dry-run must contain exactly one :verifyFast task')
    if len(tasks) != len(set(tasks)):
        raise ValueError('duplicate dry-run task path')
    return tasks


def check_text(text):
    tasks = parse_tasks(text)
    # Inspect the entire graph for heavy families before any scope diagnostic.
    for task in tasks:
        module, name = task.rsplit(':', 1)
        library_jar = module in (':core', ':asr-runtime') and name in LIBRARY_JARS
        if HEAVY.search(name) and not library_jar and (module, name) not in LIBRARY_RESOURCE_STAGE:
            raise ValueError('forbidden heavy task in fast graph: ' + task)
    for task in tasks:
        module, name = task.rsplit(':', 1)
        if module == '' and name not in ROOT_TASKS:
            raise ValueError('unreviewed root task in fast graph: ' + task)
        if module not in ('', ':core', ':asr-runtime'):
            raise ValueError('unreviewed module in fast graph: ' + task)
        if task not in APPROVED_PATHS:
            raise ValueError('unreviewed task path in fast graph: ' + task)
    print(f'PASS fast graph: {len(tasks)} complete dry-run tasks; no heavy families')
    return tasks


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise ValueError('expected one dry-run log path')
        check_text(Path(sys.argv[1]).read_text(encoding='utf-8'))
    except (ValueError, OSError) as error:
        print('FATAL: ' + str(error), file=sys.stderr)
        sys.exit(1)
