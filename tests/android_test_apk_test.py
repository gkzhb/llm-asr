#!/usr/bin/env python3
"""Shared checker target negatives; parsed-report fixtures, NOT signed mutant APKs."""
import copy
import importlib.util
from pathlib import Path
import struct
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
spec = importlib.util.spec_from_file_location('android_apk', ROOT / 'scripts/verify-android-test-apk.py')
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)


def node(tag, **attrs):
    return {'tag': tag, 'attrs': attrs, 'children': []}


def fixture():
    result = {}
    for label, package in c.PACKAGES.items():
        root = node('manifest', package=package, compileSdkVersion='35')
        app = node('application', allowBackup='false')
        root['children'] = [node('uses-sdk', minSdkVersion='29', targetSdkVersion='35'), app]
        if label == 'host':
            root['attrs'].update(versionCode='1', versionName='p1b-testhost')
            app['children'].append(node('activity', name='org.llmasr.minimal.testhost.ImeHostActivity', exported='false'))
        if label != 'product':
            app['children'] += [node('uses-library', name=n, required='true')
                                for n in ['android.test.base', 'android.test.runner']]
        if label == 'test':
            root['children'].append(node('instrumentation', name='android.test.InstrumentationTestRunner',
                                         targetPackage=c.PACKAGES['host']))
        classes = {'product': ['Lorg/llmasr/minimal/MainActivity;'],
                   'host': [c.PREFIX + 'ImeHostActivity;'],
                   'test': [c.PREFIX + n + ';' for n in ['FixtureInstrumentationTest', 'FakeDocumentsProvider',
                                                        'FakePorts', 'FixtureChecks']]}[label]
        result[label] = {'manifest': root, 'classes': classes, 'methods': list(c.METHODS) if label == 'test' else [],
                         'entries': ['AndroidManifest.xml', 'classes.dex'], 'certificates': ['approved'], 'v3': True}
    return result


def reject(change, diagnostic):
    p = fixture(); change(p)
    try:
        c.check_packages(p, 'approved')
    except ValueError as error:
        c.require(diagnostic in str(error), 'wrong rejection: ' + str(error))
    else:
        raise ValueError('checker accepted: ' + diagnostic)


def app(p, label):
    return c.only(p[label]['manifest']['children'], 'application')


def instrument(p):
    return c.only(p['test']['manifest']['children'], 'instrumentation')['attrs']


c.check_packages(fixture(), 'approved')
with_resources = fixture()
with_resources['host']['classes'] += [c.PREFIX + 'R;', c.PREFIX + 'R$string;']
c.check_packages(with_resources, 'approved')
reject(lambda p: p['host']['classes'].append(c.PREFIX + 'FakePorts;'), 'host fixture class leaked')
reject(lambda p: instrument(p).update(name='wrong.Runner'), 'runner mismatch')
reject(lambda p: instrument(p).update(targetPackage='wrong.target'), 'target mismatch')
reject(lambda p: p['host']['manifest']['children'].append(node('uses-permission', name='android.permission.INTERNET')),
       'permission forbidden')
reject(lambda p: app(p, 'host')['children'][0]['attrs'].update(exported='true'), 'component/export')
reject(lambda p: app(p, 'test')['children'].append(node('provider', exported='true')), 'component/export')
reject(lambda p: app(p, 'host')['attrs'].update(allowBackup='true'), 'backup policy')
reject(lambda p: app(p, 'test')['children'][0]['attrs'].update(required='false'), 'shared libraries')
for name in ['lib/arm64-v8a/fake.so', 'assets/model.bin']:
    reject(lambda p: p['test']['entries'].append(name), 'native/assets')
reject(lambda p: p['test']['classes'].pop(0), 'required fixture class missing')
reject(lambda p: p['test']['classes'].append(p['test']['classes'][0]), 'duplicate/missing')
reject(lambda p: p['product']['classes'].append(c.PREFIX + 'FakePorts;'), 'product fixture')
reject(lambda p: p['product']['classes'].append('Ljunit/framework/TestCase;'), 'runner/JUnit/mock')
reject(lambda p: p['test']['classes'].append('Lorg/llmasr/minimal/MainActivity;'), 'production class')
reject(lambda p: p['test']['methods'].pop(), 'method inventory')
reject(lambda p: p['test']['methods'].append(c.METHODS[0]), 'method inventory')
reject(lambda p: p['host'].update(certificates=['wrong']), 'certificate/v3')

# Exercise actual DEX parser with minimal legal class_def fixture and malformed/duplicate data.
def dex():
    data = bytearray(160)
    data[:8] = b'dex\n035\0'
    struct.pack_into('<I', data, 40, 0x12345678)
    struct.pack_into('<II', data, 56, 1, 112)
    struct.pack_into('<II', data, 64, 1, 116)
    struct.pack_into('<II', data, 72, 0, 0)
    struct.pack_into('<II', data, 88, 0, 0)
    struct.pack_into('<II', data, 96, 1, 120)
    struct.pack_into('<I', data, 112, 152)
    data[152:156] = b'\x02LX;'
    data[156] = 0
    return bytes(data)


c.require(c.dex_inventory([dex()])[0] == ['LX;'], 'DEX fixture green')
for blobs, diagnostic in [([b'bad'], 'invalid DEX header'), ([dex(), dex()], 'duplicate APK DEX')]:
    try:
        c.dex_inventory(blobs)
    except ValueError as error:
        c.require(diagnostic in str(error), 'wrong DEX rejection')
    else:
        raise ValueError('DEX negative accepted')

# Minimal real class_data for the suite: wrong flags/signature/code must be rejected
# by the same parser used on signed APKs, not just by editing a method-name list.
def suite_dex(flags=1, code=300, return_type=2):
    data = bytearray(640)
    data[:8] = b'dex\n035\0'
    struct.pack_into('<I', data, 40, 0x12345678)
    struct.pack_into('<II', data, 56, 4, 112)
    struct.pack_into('<II', data, 64, 3, 128)
    struct.pack_into('<II', data, 72, 1, 140)
    struct.pack_into('<II', data, 88, 1, 152)
    struct.pack_into('<II', data, 96, 1, 160)
    struct.pack_into('<III', data, 128, 0, 1, 2)
    struct.pack_into('<III', data, 140, 2, return_type, 0)
    struct.pack_into('<HHI', data, 152, 0, 0, 3)
    struct.pack_into('<I', data, 160 + 8, 1)
    struct.pack_into('<I', data, 160 + 24, 200)
    # fields=0/0, methods=0/1; delta=0, flags, code ULEB (only these small fixture values).
    encoded_code = bytes([code]) if code < 128 else bytes([(code & 127) | 128, code >> 7])
    data[200:206 + len(encoded_code)] = bytes([0, 0, 0, 1, 0, flags]) + encoded_code
    offset = 320
    for i, text in enumerate([c.SUITE, 'Landroid/test/InstrumentationTestCase;', 'V', 'testProbe']):
        raw = text.encode(); struct.pack_into('<I', data, 112 + 4 * i, offset)
        encoded = bytes([len(raw)]) + raw + b'\0'
        data[offset:offset + len(encoded)] = encoded; offset += len(encoded)
    return bytes(data)


c.require(c.dex_suite_methods(suite_dex()) == ['testProbe'], 'suite class_data green')
for data in [suite_dex(flags=9), suite_dex(code=0), suite_dex(return_type=0)]:
    try:
        c.dex_suite_methods(data)
    except ValueError as error:
        c.require('signature/code mismatch' in str(error), 'wrong method parser rejection')
    else:
        raise ValueError('bad concrete JUnit3 signature/code accepted')

# Real AGP single-variant metadata selection rejects stale extras and unsafe names.
with tempfile.TemporaryDirectory(prefix='apk-selection-') as temp:
    import json
    directory = Path(temp)
    metadata = {'version': 3, 'applicationId': c.PACKAGES['host'], 'variantName': 'debug',
                'artifactType': {'type': 'APK'}, 'elements': [{'type': 'SINGLE', 'filters': [],
                'versionCode': 1, 'versionName': 'p1b-testhost', 'outputFile': 'host.apk'}]}
    path = directory / 'output-metadata.json'; path.write_text(json.dumps(metadata))
    (directory / 'host.apk').write_bytes(b'package placeholder; selection test only')
    c.select_apk(directory, c.PACKAGES['host'], 'debug')
    (directory / 'old.apk').write_bytes(b'old')
    try:
        c.select_apk(directory, c.PACKAGES['host'], 'debug')
    except ValueError as error:
        c.require('missing/extra/symlink' in str(error), 'wrong stale APK refusal')
    else:
        raise ValueError('extra stale APK accepted')
    (directory / 'old.apk').unlink()
    metadata['elements'][0]['outputFile'] = '../escape.apk'; path.write_text(json.dumps(metadata))
    try:
        c.select_apk(directory, c.PACKAGES['host'], 'debug')
    except ValueError as error:
        c.require('path unsafe' in str(error), 'wrong metadata path refusal')
    else:
        raise ValueError('unsafe metadata output accepted')

# Parse real output syntax and ensure direct attributes cannot be borrowed from siblings.
tree = c.manifest_nodes('E: manifest\n  A: package="sample"\n  E: application\n    A: android:allowBackup(0x1)=false\n    E: activity\n      A: android:exported(0x2)=false\n')
c.require(c.only(tree['children'], 'application')['attrs']['allowBackup'] == 'false', 'tree parser')
print('PASS shared APK policy/DEX target negatives (parsed fixtures, not signed APK mutants)')
