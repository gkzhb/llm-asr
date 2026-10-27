"""Checker negative fixtures only; real object compilation is a separate command."""
from pathlib import Path
import importlib.util
import os
import sys
import tempfile
from unittest.mock import patch
spec = importlib.util.spec_from_file_location('checker', Path(__file__).resolve().parents[1] / 'scripts/check-jni-symbols.py')
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)
fixture = '\n'.join('private static native ' + {'D': 'double', 'V': 'void', '[B': 'byte[]'}[desc.split(')')[1]] + ' ' + name + '(arguments);\n descriptor: ' + desc
                    for name, desc in c.EXPECTED.items())
methods = c.declarations(fixture)
# R6: JNI symbols use org.llmasr.minimal.asr.JniNativeTranscription.
prefix = 'Java_' + c.CLASS.replace('.', '_') + '_'
symbols = '\n'.join('000 T ' + prefix + name for name in methods)
c.check_symbols(methods, symbols)
for bad in [fixture.replace(')[B', ')V'), fixture.replace(')D', ')V'),
            fixture.replace('()V', '(' + c.STRING + ')V'),
            fixture.replace('native double prepareNative', 'native void prepareNative'),
            fixture.replace('private static', 'public static'),
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
for bad, diagnostic in [(fixture + '\n' + fixture.splitlines()[0] + '\n descriptor: ' + c.EXPECTED['prepareNative'], 'overloaded/duplicate native method'),
                        (fixture.replace('native double', 'native int'), 'unexpected native declaration shape')]:
    try: c.declarations(bad)
    except AssertionError as failure:
        if diagnostic not in str(failure): raise AssertionError('wrong exact declaration diagnostic')
    else: raise AssertionError('accepted duplicate/malformed declaration')
# Exercise actual main owner discovery and nm policy, not a copied implementation.
with tempfile.TemporaryDirectory(prefix='jni-owner-') as temporary:
    root = Path(temporary); classes = root / 'classes'; classes.mkdir(); binary = root / 'bridge.o'; binary.touch()
    owner = classes / (c.CLASS.replace('.', '/') + '.class'); owner.parent.mkdir(parents=True)
    extra = classes / 'Extra.class'
    def check_output(argv, **kwargs):
        if kwargs.get('timeout') != 30: raise AssertionError('unbounded checker subprocess')
        if argv[0] == 'javap': return fixture if argv[-1] == c.CLASS else 'public class Extra {}'
        return symbols
    with patch.object(sys, 'argv', ['checker', str(classes), str(binary)]), patch.dict(os.environ, {'ANDROID_NDK_ROOT': str(root)}):
        with patch.object(c.subprocess, 'check_output', side_effect=check_output):
            try: c.main()
            except AssertionError as failure:
                if 'unexpected/missing native owner' not in str(failure): raise
            else: raise AssertionError('accepted missing owner')
            owner.touch(); c.main()
            extra.touch()
            def extra_output(argv, **kwargs):
                if argv[0] == 'javap' and argv[-1] == 'Extra': return fixture
                return check_output(argv, **kwargs)
            with patch.object(c.subprocess, 'check_output', side_effect=extra_output):
                try: c.main()
                except AssertionError as failure:
                    if 'unexpected/missing native owner' not in str(failure): raise
                else: raise AssertionError('accepted extra native owner')
print('PASS JNI symbol checker Java ABI/duplicate/malformed/missing-extra owner/export negatives (ordinary and optimized safe)')
