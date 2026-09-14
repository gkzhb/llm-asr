#!/usr/bin/env python3
"""Reproducible local wheel compatibility fixes; never weakens host security.

1. Lazy-import Japanese forced alignment's nagisa (DyNet wheel SIGILL on no-AVX
   host). ASR model math is untouched. Japanese alignment remains unsupported.
2. Clear PT_GNU_STACK PF_X on the local MNN ELF (no executable stack allowed).
   Validate imports/tests afterward; if executable stack really is needed, fail.
"""
import difflib
import hashlib
import json
from pathlib import Path
import struct
import sysconfig


def sha(data):return hashlib.sha256(data).hexdigest()


def main():
    site=Path(sysconfig.get_paths()['purelib'])
    report=[]
    path=site/'qwen_asr/inference/qwen3_forced_aligner.py'
    before=path.read_text()
    old='import nagisa\n'
    marker='        words = nagisa.tagging(text).words'
    if old in before and marker in before:
        after=before.replace(old,'# P0: nagisa imported only for optional Japanese alignment.\n',1)
        after=after.replace(marker,'        import nagisa  # optional Japanese alignment; unsupported on this host\n'+marker,1)
        compile(after,str(path),'exec')
        path.write_text(after)
        patch=Path('patches/qwen-lazy-nagisa.patch');patch.parent.mkdir(parents=True,exist_ok=True)
        patch.write_text(''.join(difflib.unified_diff(before.splitlines(True),after.splitlines(True),
            fromfile='a/qwen_asr/inference/qwen3_forced_aligner.py',tofile='b/qwen_asr/inference/qwen3_forced_aligner.py')))
        report.append({'file':'qwen_asr/inference/qwen3_forced_aligner.py','before':sha(before.encode()),'after':sha(after.encode()),'fix':'lazy nagisa import; ASR untouched'})
    elif 'P0: nagisa imported' not in before:
        raise RuntimeError('Unexpected qwen source')
    for path in site.glob('_mnncengine*.so'):
        data=bytearray(path.read_bytes());before_hash=sha(data)
        if data[:6]!=b'\x7fELF\x02\x01':raise ValueError('Expected ELF64 little endian')
        offset=struct.unpack_from('<Q',data,32)[0]
        entsize,count=struct.unpack_from('<HH',data,54)
        changed=False
        for i in range(count):
            pos=offset+i*entsize
            ptype,flags=struct.unpack_from('<II',data,pos)
            if ptype==0x6474e551 and flags&1:
                struct.pack_into('<I',data,pos+4,flags&~1);changed=True
        if changed:
            # uv may hardlink to its cache: replace, do not mutate that inode in place.
            tmp=path.with_suffix('.patched');tmp.write_bytes(data);tmp.chmod(path.stat().st_mode);tmp.replace(path)
            report.append({'file':path.name,'before':before_hash,'after':sha(data),'fix':'PT_GNU_STACK PF_X cleared'})
    output=Path('reports/p0/wheel-compatibility.json')
    if report:output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))


if __name__=='__main__':main()
