#!/usr/bin/env python3
"""Compile positive/negative probes on actual Gradle module classpaths.
No Android runtime, source mutation or compilation failure counted as success.
"""
import argparse
import subprocess
import tempfile
from pathlib import Path


def probe(classpath, declaration, expected, label):
    with tempfile.TemporaryDirectory(prefix='module-probe-') as temporary:
        root = Path(temporary)
        source = root / 'BoundaryProbe.java'
        source.write_text('public final class BoundaryProbe { ' + declaration + ' }\n')
        result = subprocess.run(['javac', '-J-Duser.language=en', '-J-Duser.country=US', '--release', '8', '-encoding', 'UTF-8',
                                 '-sourcepath', str(root), '-classpath', classpath,
                                 '-d', str(root / 'classes'), str(source)],
                                capture_output=True, text=True, timeout=30)
        if expected is None:
            if result.returncode != 0 or not (root / 'classes/BoundaryProbe.class').is_file():
                raise AssertionError('positive compile failed: ' + label + '\n' + result.stderr)
        elif result.returncode == 0 or expected not in result.stderr:
            raise AssertionError('negative not rejected for intended dependency: ' + label + '\n' + result.stderr)
        print('PASS actual module compile boundary:', label, 'exit', result.returncode)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--module', choices=['core', 'asr-runtime'], required=True)
    parser.add_argument('--classpath', required=True)
    args = parser.parse_args()
    probe(args.classpath, 'org.llmasr.minimal.task.TaskKind kind;', None, args.module + ' -> core positive')
    probe(args.classpath, 'org.llmasr.minimal.model.ModelVerification proof;', None, args.module + ' -> pure verification positive')
    if args.module == 'core':
        for reference, diagnostic in [
            ('android.content.Context', 'package android.content does not exist'),
            ('org.json.JSONObject', 'package org.json does not exist'),
            ('org.llmasr.minimal.asr.JniNativeTranscription', 'JniNativeTranscription'),
            ('org.llmasr.minimal.AppGraph', 'AppGraph'),
            ('org.llmasr.minimal.model.AndroidModelVerificationIo', 'AndroidModelVerificationIo')]:
            probe(args.classpath, reference + ' forbidden;', diagnostic, 'core !-> ' + reference)
    else:
        probe(args.classpath, 'org.llmasr.minimal.asr.JniNativeTranscription bridge; android.content.Context context;',
              None, 'runtime -> JNI/Android positive')
        probe(args.classpath, 'org.llmasr.minimal.model.AndroidModelVerificationIo io;', None, 'runtime owns Android proof IO')
        probe(args.classpath, 'org.llmasr.minimal.AppGraph forbidden;', 'AppGraph', 'runtime !-> app')
