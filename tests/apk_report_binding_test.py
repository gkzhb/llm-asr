#!/usr/bin/env python3
"""Run the actual checker on isolated synthetic archive/report fixtures.
Metadata is deliberately fake: tests integrity binding, NOT signing or Android policy.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import tempfile
import zipfile
import struct

ROOT=Path(__file__).resolve().parents[1]
REPORTS=('permissions.txt','badging.txt','signature.txt','manifest-tree.txt','ime-method-tree.txt','native-link-provenance.json')
work=Path(tempfile.mkdtemp(prefix='apk-binding-',dir=ROOT/'.work/model-review-fixes'))
(work/'dist').mkdir();(work/'reports/apk').mkdir(parents=True);(work/'reports/p0').mkdir(parents=True)
# Self-contained checker inputs: the host suite must also work before the first APK build.
# No production runtime/security clause is weakened to accommodate these fake reports.
manifest=(ROOT/'reports/p0/mnn-model-manifest.json').read_bytes()
(work/'reports/p0/mnn-model-manifest.json').write_bytes(manifest)
apk=work/'dist/qwen-asr-minimal-debug.apk'; reports=work/'reports/apk'
native=b'synthetic native fixture; never executed'
(work/'android/app/assets').mkdir(parents=True)
licenses = {name:(ROOT/'android/app/assets'/name).read_bytes() for name in ('KOTLIN-LICENSE.txt', 'KOTLIN-NOTICES.txt')}
for name, data in licenses.items(): (work/'android/app/assets'/name).write_bytes(data)
# Minimal synthetic class/string/type tables exercise ownership, not executable DEX.
def fixture_dex(include_runtime=True, only_runtime=False):
    descriptors = [b'Lkotlin/jvm/internal/Intrinsics;', b'Lorg/llmasr/minimal/audio/AsrText;',
        b'Lorg/llmasr/minimal/audio/PcmWave;', b'Lorg/llmasr/minimal/audio/RecordingControl;',
        b'Lorg/llmasr/minimal/asr/NativeResponse;']
    if only_runtime:
        descriptors = descriptors[:1]
    n = len(descriptors)
    strings_offset, types_offset, classes_offset = 112, 112 + n*4, 112 + n*8
    data = bytearray(classes_offset + n*32)
    data[:8] = b'dex\n035\0'
    struct.pack_into('<I', data, 40, 0x12345678)
    struct.pack_into('<II', data, 56, n, strings_offset)
    struct.pack_into('<II', data, 64, n, types_offset)
    struct.pack_into('<II', data, 96, n if include_runtime else n-1,
                     classes_offset if include_runtime else classes_offset+32)
    for i, descriptor in enumerate(descriptors):
        struct.pack_into('<I', data, strings_offset+i*4, len(data))
        struct.pack_into('<I', data, types_offset+i*4, i)
        struct.pack_into('<I', data, classes_offset+i*32, i)
        data.extend(bytes([len(descriptor)]) + descriptor + b'\0')
    return bytes(data)
dex = fixture_dex()
with zipfile.ZipFile(apk,'w') as z:
    for name,data in {'classes.dex':dex,'AndroidManifest.xml':b'fixture',
        'res/xml/method.xml':b'fixture','assets/model-manifest.json':manifest,
        'assets/sample.wav':(ROOT/'bench/audio/zh-original.wav').read_bytes(),
        'lib/arm64-v8a/libqwen_asr_jni.so':native,
        **{'assets/'+name:data for name,data in licenses.items()}}.items():z.writestr(name,data)
(reports/'native-link-provenance.json').write_text(json.dumps({'output_sha256':hashlib.sha256(native).hexdigest()}))
(reports/'permissions.txt').write_text("uses-permission: name='android.permission.RECORD_AUDIO'\n")
(reports/'badging.txt').write_text("package: name='org.llmasr.minimal' versionCode='9' versionName='0.9-debug'\nminSdkVersion:'29'\ntargetSdkVersion:'35'\nnative-code: 'arm64-v8a'\nprovides-component:'ime'\n")
(reports/'signature.txt').write_text('Verifies (synthetic fixture, not signature evidence)\n')
(reports/'manifest-tree.txt').write_text('''  E: application
    E: activity
      A: android:name="org.llmasr.minimal.MainActivity"
      A: android:exported=true
    E: activity
      A: android:name="org.llmasr.minimal.ModelManagementActivity"
      A: android:exported=false
    E: activity
      A: android:name="org.llmasr.minimal.LogsActivity"
      A: android:exported=false
    E: service
      A: android:name="org.llmasr.minimal.AsrImeService"
      A: android:permission="android.permission.BIND_INPUT_METHOD"
      A: android:exported=true
      E: intent-filter
        A: android:name="android.view.InputMethod"
      E: meta-data
        A: android:name="android.view.im"
''')
(reports/'ime-method-tree.txt').write_text('E: input-method\n  E: subtype\n    A: voice\n')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def bind():
    result=subprocess.run(['python3',str(ROOT/'scripts/apk_report_binding.py'),'--root',str(work)],capture_output=True,text=True,timeout=15)
    assert result.returncode==0,result.stderr
    assert json.loads((reports/'apk-report-binding.json').read_text())=={'schema':1,'apk_sha256':sha(apk),'reports':{n:sha(reports/n) for n in REPORTS}}
def run(name,pass_expected,marker=''):
    r=subprocess.run(['python3',str(ROOT/'scripts/check-minimal-apk.py'),'--root',str(work)],capture_output=True,text=True,timeout=15)
    text=r.stdout+r.stderr;(work/(name+'.log')).write_text(text)
    assert (r.returncode==0)==pass_expected and (not marker or marker in text),(name,r.returncode,text)
    print('PASS actual checker fixture',name,'exit',r.returncode)
bind();run('bound-fixture',True)
# Keep report binding valid: each stale version field must fail the actual
# version oracle independently, not a detached-report or unrelated ZIP check.
badging=reports/'badging.txt';current_badging=badging.read_text()
for label, current, stale in [('version-code', "versionCode='9'", "versionCode='8'"),
                               ('version-name', "versionName='0.9-debug'", "versionName='0.8-debug'")]:
    assert current_badging.count(current)==1,'unique version fixture'
    badging.write_text(current_badging.replace(current,stale));bind()
    run(label,False,'AssertionError: '+current)
    badging.write_text(current_badging);bind()
run('version-restored',True)
original=apk.read_bytes()
# Missing runtime must fail even when report binding is recomputed for that APK.
with zipfile.ZipFile(apk) as z: entries = {n:z.read(n) for n in z.namelist()}
with zipfile.ZipFile(apk, 'w') as z:
    for name, data in entries.items():
        z.writestr(name, fixture_dex(False) if name == 'classes.dex' else data)
bind();run('missing-kotlin-runtime',False,'Kotlin runtime/owner missing')
apk.write_bytes(original);bind()
# AGP debug splits owners across multiple DEX files. Actual definitions in
# secondary DEX must count; dangling references still never count.
with zipfile.ZipFile(apk, 'w') as z:
    for name, data in entries.items():
        z.writestr(name, fixture_dex(only_runtime=True) if name == 'classes.dex' else data)
    z.writestr('classes2.dex', fixture_dex(False))
bind();run('multidex-definitions',True)
with zipfile.ZipFile(apk, 'w') as z:
    for name, data in entries.items():
        z.writestr(name, fixture_dex(only_runtime=True) if name == 'classes.dex' else data)
bind();run('multidex-missing-owners',False,'Kotlin runtime/owner missing')
with zipfile.ZipFile(apk, 'w') as z:
    for name, data in entries.items(): z.writestr(name, data)
    z.writestr('classes2.dex', fixture_dex())
bind();run('multidex-duplicate-owners',False,'Duplicate class_def')
apk.write_bytes(original);bind()
with zipfile.ZipFile(apk,'a') as z:z.writestr('fixture-marker.txt',b'changed ZIP; metadata detached')
run('stale-apk',False,'APK report binding mismatch');apk.write_bytes(original)
for name in REPORTS:
    path=reports/name;original_report=path.read_bytes();path.write_bytes(original_report+b'\nmodified detached report\n')
    run('modified-'+name,False,'APK report hash mismatch');path.write_bytes(original_report)
(reports/'apk-report-binding.json').unlink();run('missing-binding',False)
print('PASS archive/report binding rejects stale and every modified report; logs',work.relative_to(ROOT))
