#!/usr/bin/env python3
"""Install this debug package; reuse SHA-verified P0 model on the authorized device.

No microphone, personal files or settings. --run-sample taps only our own visible
Activity's embedded public-example button and collects its private result.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PKG = 'org.llmasr.minimal'
DEVICE = '100.64.0.3:33317'
REMOTE = '/data/local/tmp/qwen-asr-p0'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-sample', action='store_true')
    args = parser.parse_args()
    adb = os.environ.get('ADB', 'adb')
    def command(*parts, timeout=120):
        p = subprocess.run([adb, '-s', DEVICE, *parts], capture_output=True, text=True, timeout=timeout)
        if p.returncode:
            raise RuntimeError(f'adb {parts[0]} failed: {p.stderr}\n{p.stdout}')
        return p.stdout
    def shell(script, timeout=120):
        return command('shell', script, timeout=timeout).strip()
    if command('get-state').strip() != 'device':
        raise RuntimeError('Authorized device is not connected; reconnect explicitly, do not scan')
    apk = ROOT/'dist/qwen-asr-minimal-debug.apk'
    manifest = json.loads((ROOT/'reports/p0/mnn-model-manifest.json').read_text())
    expected = {'config.json','llm_config.json','audio.mnn','audio.mnn.weight','llm.mnn','llm.mnn.weight','tokenizer.txt'}
    if {e['file'] for e in manifest['files']} != expected:
        raise ValueError('Unexpected model manifest')
    print(command('install', '-r', str(apk), timeout=240), flush=True)
    shell(f'am force-stop {PKG}')
    shell(f'run-as {PKG} mkdir -p files/model')
    shell(f'run-as {PKG} rm -f files/last-result.json')
    # This exact P0 path is an existing project-only deployment, not personal storage.
    deployment = {'package':PKG, 'device':DEVICE, 'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(), 'files':[]}
    for entry in manifest['files']:
        name = entry['file']; digest = entry['sha256']
        if not re.fullmatch(r'[0-9a-f]{64}', digest): raise ValueError('Invalid SHA')
        target = f'files/model/{name}'
        existing = shell(f'run-as {PKG} sh -c "if test -f {target}; then sha256sum {target}; fi"', timeout=180)
        if existing.split()[:1] != [digest]:
            source = f'{REMOTE}/model/{name}'
            # A remote symlink could redirect reads out of the authorized project directory.
            safe = shell(f'test ! -L {REMOTE} && test ! -L {REMOTE}/model && test ! -L {source} && echo SAFE')
            if safe != 'SAFE': raise RuntimeError('Remote source symlink rejected')
            original = shell(f'sha256sum {source}', timeout=180)
            if original.split()[:1] != [digest]: raise RuntimeError(f'P0 source hash mismatch: {name}')
            # cat runs as shell on source; destination process uses the app UID.
            # Verify final hash so a failed producer in the pipe cannot pass silently.
            print(f'COPY phone-local {name}', flush=True)
            shell(f'cat {source} | run-as {PKG} sh -c "cat > {target}.part"', timeout=240)
            got = shell(f'run-as {PKG} sha256sum {target}.part', timeout=180)
            if got.split()[:1] != [digest]: raise RuntimeError(f'App copy hash mismatch: {name}')
            shell(f'run-as {PKG} mv {target}.part {target}')
        deployment['files'].append({'file':name, 'sha256':digest, 'verified':True})
        print('VERIFIED '+name, flush=True)
    out = ROOT/'reports/apk'; out.mkdir(exist_ok=True)
    (out/'device-deployment.json').write_text(json.dumps(deployment, indent=2)+'\n')
    print(shell(f'am start -W -n {PKG}/.MainActivity'), flush=True)
    if not args.run_sample: return
    # Dump only when the visible foreground Activity is ours; do not inspect other apps.
    focused = shell('dumpsys activity activities')
    if not any(PKG+'/.MainActivity' in line and ('mResumedActivity' in line or 'topResumedActivity' in line) for line in focused.splitlines()):
        raise RuntimeError('Our Activity is not resumed. Unlock/show it manually; no automatic lockscreen action.')
    xml_path = f'{REMOTE}/apk-window.xml'
    shell(f'uiautomator dump {xml_path}')
    tree = ET.fromstring(shell(f'cat {xml_path}'))
    nodes = [n for n in tree.iter('node') if n.attrib.get('package') == PKG and n.attrib.get('text') == '3. 转写内置中文示例']
    if len(nodes) != 1: raise RuntimeError('Public example button not uniquely visible')
    bounds = list(map(int,re.findall(r'\d+',nodes[0].attrib['bounds'])))
    if len(bounds)!=4: raise RuntimeError('Invalid UI bounds')
    started_ms = int(shell('date +%s')) * 1000
    shell(f'input tap {(bounds[0]+bounds[2])//2} {(bounds[1]+bounds[3])//2}')
    deadline = time.monotonic()+240
    observed_request = None
    while time.monotonic()<deadline:
        raw = shell(f'run-as {PKG} sh -c "if test -f files/last-result.json; then cat files/last-result.json; fi"')
        if raw:
            result=json.loads(raw)
            run_id=result.get('request_id','')
            if observed_request is None: observed_request=run_id
            if run_id!=observed_request: raise RuntimeError('Request changed during smoke test')
            if result.get('state') == 'pending':
                time.sleep(2)
                continue
            if not re.fullmatch(r'[0-9a-f-]{36}', result.get('request_id','')):
                raise RuntimeError('Missing request identity')
            if result.get('timestamp_ms',0) < started_ms:
                raise RuntimeError('Stale report timestamp')
            if result.get('kind') != 'inference' and result.get('success'):
                raise RuntimeError('Not an inference result')
            (out/'device-sample-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
            if not result.get('success'): raise RuntimeError('App sample failed: '+str(result))
            if result.get('uid',0)<10000: raise RuntimeError('Not an ordinary app UID')
            if result['text']!='甚至出现交易几乎停滞的情况。' or result['truncated']:
                raise RuntimeError('Sample transcript mismatch/truncation')
            print('APK_SAMPLE_PASS '+json.dumps(result,ensure_ascii=False),flush=True)
            return
        time.sleep(2)  # Bounded test observation, not an agent/task wait loop.
    raise TimeoutError('No app result within 240s; foreground loss/OOM/native crash must be investigated')

if __name__=='__main__': main()
