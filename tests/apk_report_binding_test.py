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
with zipfile.ZipFile(apk,'w') as z:
    for name,data in {'classes.dex':b'fixture','AndroidManifest.xml':b'fixture',
        'res/xml/method.xml':b'fixture','assets/model-manifest.json':manifest,
        'assets/sample.wav':(ROOT/'bench/audio/zh-original.wav').read_bytes(),
        'lib/arm64-v8a/libqwen_asr_jni.so':native}.items():z.writestr(name,data)
(reports/'native-link-provenance.json').write_text(json.dumps({'output_sha256':hashlib.sha256(native).hexdigest()}))
(reports/'permissions.txt').write_text("uses-permission: name='android.permission.RECORD_AUDIO'\n")
(reports/'badging.txt').write_text("package: name='org.llmasr.minimal' versionCode='6' versionName='0.6-debug'\nminSdkVersion:'29'\ntargetSdkVersion:'35'\nnative-code: 'arm64-v8a'\nprovides-component:'ime'\n")
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
original=apk.read_bytes()
with zipfile.ZipFile(apk,'a') as z:z.writestr('fixture-marker.txt',b'changed ZIP; metadata detached')
run('stale-apk',False,'APK report binding mismatch');apk.write_bytes(original)
for name in REPORTS:
    path=reports/name;original_report=path.read_bytes();path.write_bytes(original_report+b'\nmodified detached report\n')
    run('modified-'+name,False,'APK report hash mismatch');path.write_bytes(original_report)
(reports/'apk-report-binding.json').unlink();run('missing-binding',False)
print('PASS archive/report binding rejects stale and every modified report; logs',work.relative_to(ROOT))
