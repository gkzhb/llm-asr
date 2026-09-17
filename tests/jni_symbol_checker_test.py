"""Checker negative fixtures only; real object compilation is a separate command."""
from pathlib import Path
import importlib.util
spec = importlib.util.spec_from_file_location('checker', Path(__file__).resolve().parents[1] / 'scripts/check-jni-symbols.py')
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)
fixture = '\n'.join('private static native byte[] ' + name + '(arguments);\n descriptor: ' + desc
                    for name, desc in c.EXPECTED.items())
methods = c.declarations(fixture)
# R6: JNI symbols use org.llmasr.minimal.asr.JniNativeTranscription.
prefix = 'Java_' + c.CLASS.replace('.', '_') + '_'
symbols = '\n'.join('000 T ' + prefix + name for name in methods)
c.check_symbols(methods, symbols)
for bad in [fixture.replace(')[B', ')V'), fixture.replace('private static', 'public static'),
            fixture.replace('transcribeWithListener', 'wrong'), fixture + '\npublic native void extra();']:
    try: c.declarations(bad)
    except AssertionError: pass
    else: raise AssertionError('accepted wrong Java native ABI')
for bad in [symbols.replace('asr_JniNativeTranscription', 'JniNativeTranscription'),
            symbols.splitlines()[0],
            symbols + '\n000 T Java_unexpected_extra']:
    try: c.check_symbols(methods, bad)
    except AssertionError: pass
    else: raise AssertionError('accepted missing/stale/extra JNI export')
print('PASS JNI symbol checker Java ABI and missing/stale/extra export negative fixtures')
