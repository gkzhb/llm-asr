#!/usr/bin/env python3
"""Fail-closed post-signing archive/manifest/ABI evidence check (not device test)."""
import hashlib
import json
import re
from pathlib import Path
import zipfile
ROOT=Path(__file__).resolve().parents[1]
report=ROOT/'reports/apk'
with zipfile.ZipFile(ROOT/'dist/qwen-asr-minimal-debug.apk') as z:
    names=z.namelist()
    assert len(names)==len(set(names)), 'Duplicate ZIP entries'
    libs=[n for n in names if n.startswith('lib/') and not n.endswith('/')]
    assert libs==['lib/arm64-v8a/libqwen_asr_jni.so'], libs
    assert 'classes.dex' in names and 'AndroidManifest.xml' in names
    assert not any(n.endswith(('.weight','.mnn','.o','.rsp')) for n in names)
    assert z.read('assets/model-manifest.json')==(ROOT/'reports/p0/mnn-model-manifest.json').read_bytes()
    assert hashlib.sha256(z.read('assets/sample.wav')).hexdigest()=='46dbc998c9d1d48111267c40741dd3200f2e5bcf4075f8c4c97f4451160dce50'
    assert hashlib.sha256(z.read(libs[0])).hexdigest()==json.loads((report/'native-link-provenance.json').read_text())['output_sha256']
    assert z.testzip() is None
permissions=(report/'permissions.txt').read_text()
assert re.findall(r"uses-permission[^\n]*name='([^']+)'",permissions)==['android.permission.RECORD_AUDIO'], permissions
badging=(report/'badging.txt').read_text()
for text in ["package: name='org.llmasr.minimal'", "versionCode='2'", "versionName='0.2-debug'", "minSdkVersion:'29'", "targetSdkVersion:'35'", "native-code: 'arm64-v8a'"]:
    assert text in badging, text
assert 'Verifies' in (report/'signature.txt').read_text()
print('PASS signed APK archive, one combined arm64 DSO, exact assets, RECORD_AUDIO-only permission, API29/35')
