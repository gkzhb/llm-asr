#!/usr/bin/env python3
"""Bind serially generated detached tool reports to the signed APK.
Integrity/provenance evidence, not an independent cryptographic signature verifier:
an actor able to rewrite both artifacts and this binding can forge the evidence.
"""
import hashlib
import json
from pathlib import Path

REPORT_NAMES=('permissions.txt','badging.txt','signature.txt','manifest-tree.txt',
              'ime-method-tree.txt','native-link-provenance.json')

def sha(path):
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):digest.update(chunk)
    return digest.hexdigest()

def payload(apk,report):
    return {'schema':1,'apk_sha256':sha(apk),'reports':{name:sha(report/name) for name in REPORT_NAMES}}

def verify(apk,report):
    recorded=json.loads((report/'apk-report-binding.json').read_text())
    current=payload(apk,report)
    assert recorded.get('schema')==1,'Unknown APK report binding schema'
    assert recorded.get('apk_sha256')==current['apk_sha256'],'APK report binding mismatch'
    assert recorded.get('reports')==current['reports'],'APK report hash mismatch'

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    args=parser.parse_args()
    report=args.root/'reports/apk'
    # Called only after verify/aapt commands succeeded, before the standalone checker.
    (report/'apk-report-binding.json').write_text(json.dumps(payload(args.root/'dist/qwen-asr-minimal-debug.apk',report),indent=2)+'\n')
