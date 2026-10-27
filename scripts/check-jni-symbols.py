#!/usr/bin/env python3
"""Compare compiled Java native declarations/descriptors with real NDK symbols.
Accepts an object or linked DSO; never loads JNI or writes package reports.
"""
import os
from pathlib import Path
import re
import subprocess
import sys

CLASS = 'org.llmasr.minimal.asr.JniNativeTranscription'
STRING = 'Ljava/lang/String;'
LISTENER = 'Lorg/llmasr/minimal/asr/InferencePhaseListener;'
EXPECTED = {
    'prepareNative': '(' + STRING * 2 + LISTENER + ')D',
    'releaseNative': '()V',
    'transcribe': '(' + STRING * 4 + ')[B',
    'transcribeWithListener': '(' + STRING * 4 + LISTENER + ')[B',
}

def declarations(text):
    result = {}
    returns = {'byte[]': '[B', 'double': 'D', 'void': 'V'}
    for returned, method, descriptor in re.findall(
            r'private static native (byte\[\]|double|void) (\w+)\([^\n]*\);\s+descriptor: (\S+)', text):
        if method in result:
            raise AssertionError('overloaded/duplicate native method')
        if not descriptor.endswith(')' + returns[returned]):
            raise AssertionError('native return descriptor differs')
        result[method] = descriptor
    if text.count(' native ') != len(result):
        raise AssertionError('unexpected native declaration shape')
    if result != EXPECTED:
        raise AssertionError('Java native ABI differs: ' + str(result))
    return result

def check_symbols(methods, text):
    actual = {line.split()[-1] for line in text.splitlines() if line.split() and line.split()[-1].startswith('Java_')}
    expected = {'Java_' + CLASS.replace('.', '_') + '_' + method for method in methods}
    if actual != expected:
        raise AssertionError('JNI exported names differ: ' + str(actual ^ expected))


def main():
    classes, binary = map(Path, sys.argv[1:])
    # Inspect every compiled production class, not a handwritten Java symbol list.
    native_classes = []
    for path in sorted(classes.rglob('*.class')):
        name = '.'.join(path.relative_to(classes).with_suffix('').parts)
        text = subprocess.check_output(['javap', '-p', '-s', '-classpath', str(classes), name], text=True, timeout=30)
        if ' native ' in text:
            native_classes.append(name)
            print(text, end='')
            methods = declarations(text)
    if native_classes != [CLASS]:
        raise AssertionError('unexpected/missing native owner: ' + str(native_classes))
    nm = Path(os.environ['ANDROID_NDK_ROOT']) / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
    args = [str(nm), '--defined-only', '--extern-only']
    if binary.suffix == '.so': args.append('--dynamic')
    text = subprocess.check_output(args + [str(binary)], text=True, timeout=30)
    check_symbols(methods, text)
    print('\n'.join(line for line in text.splitlines() if 'Java_' in line))
    print('PASS compiled Java native descriptors / actual JNI symbols:', binary)

if __name__ == '__main__':
    main()
