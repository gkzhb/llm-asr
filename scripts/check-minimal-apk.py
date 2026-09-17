#!/usr/bin/env python3
"""Fail-closed post-signing archive/manifest/ABI evidence check (not device test)."""
import hashlib
import json
import re
from pathlib import Path
import zipfile
import argparse
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1],
                    help='artifact root (offline fixture support; all security checks remain enabled)')
ROOT=parser.parse_args().root.resolve()
report=ROOT/'reports/apk'
from apk_report_binding import verify
verify(ROOT/'dist/qwen-asr-minimal-debug.apk', report)
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
    # Phase 13: res/xml/method.xml for the IME subtype must be present.
    assert 'res/xml/method.xml' in names, 'IME method metadata resource missing'
permissions=(report/'permissions.txt').read_text()
assert re.findall(r"uses-permission[^\n]*name='([^']+)'",permissions)==['android.permission.RECORD_AUDIO'], permissions
badging=(report/'badging.txt').read_text()
for text in ["package: name='org.llmasr.minimal'", "versionCode='6'", "versionName='0.6-debug'", "minSdkVersion:'29'", "targetSdkVersion:'35'", "native-code: 'arm64-v8a'"]:
    assert text in badging, text
# IME service must show up as a 'provides-component: ime' entry in badging,
# and the manifest tree must contain the AsrImeService node with the
# BIND_INPUT_METHOD permission and android.view.InputMethod intent filter.
assert "provides-component:'ime'" in badging, badging
assert 'Verifies' in (report/'signature.txt').read_text()
tree = (report/'manifest-tree.txt').read_text()
# AAPT2 indentation reflects tree depth; extract exactly one service subtree.
lines = tree.splitlines()
service_nodes = []
for i, line in enumerate(lines):
    if re.match(r'^\s+E: service(?:\s|$)', line):
        depth = len(line) - len(line.lstrip())
        end = i + 1
        while end < len(lines) and (not lines[end].strip() or len(lines[end])-len(lines[end].lstrip()) > depth):
            end += 1
        service_nodes.append('\n'.join(lines[i:end]))
assert len(service_nodes) == 1, service_nodes
service = service_nodes[0]
for text in ['AsrImeService', 'android.permission.BIND_INPUT_METHOD', 'android.view.InputMethod', 'android.view.im']:
    assert text in service, text
assert re.search(r'android:exported[^\n]*=true(?:\s|$)', service), 'IME must be exported for system binding'
assert 'android:process' not in tree, 'App and IME must share one process'
# Require exactly one internal model page, no filters or independent process.
activity_nodes = []
for i, line in enumerate(lines):
    if re.match(r'^\s+E: activity(?:\s|$)', line):
        depth = len(line) - len(line.lstrip())
        end = i + 1
        while end < len(lines) and (not lines[end].strip() or len(lines[end])-len(lines[end].lstrip()) > depth):
            end += 1
        activity_nodes.append('\n'.join(lines[i:end]))
assert len(activity_nodes) == 3, activity_nodes
pages = [node for node in activity_nodes if re.search(r'android:name[^\n]*[".]ModelManagementActivity["\s]', node)]
logs = [node for node in activity_nodes if re.search(r'android:name[^\n]*[".]LogsActivity["\s]', node)]
assert len(pages) == 1, activity_nodes
assert len(logs) == 1, activity_nodes
page = pages[0]
log_page = logs[0]
assert re.search(r'android:exported[^\n]*=false(?:\s|$)', page), 'Model page must be nonexported'
assert re.search(r'android:exported[^\n]*=false(?:\s|$)', log_page), 'Logs page must be nonexported'
assert 'E: intent-filter' not in page and 'android:process' not in page
assert 'E: intent-filter' not in log_page and 'android:process' not in log_page
method = (report/'ime-method-tree.txt').read_text()
assert 'E: input-method' in method and 'E: subtype' in method and 'voice' in method
print('PASS signed APK archive, one combined arm64 DSO, exact assets, RECORD_AUDIO-only permission, API29/35, IME service declared (0.6-debug)')
