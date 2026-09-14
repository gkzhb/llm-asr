#!/usr/bin/env python3
"""Relink final P0 object files + JNI into ONE DSO, with one static libc++.

No source rebuild or mutation of the P0 build. Records every object identity.
Ninja's explicit libMNN link edge is the authoritative final object list.
"""
import hashlib
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'.work/build/mnn-android'
NDK=Path(os.environ['ANDROID_NDK_ROOT'])
compiler=NDK/'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++'
source=ROOT/'.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36'
out=Path(sys.argv[1]).resolve(); out.parent.mkdir(parents=True,exist_ok=True)
lines=(BUILD/'build.ninja').read_text().splitlines()
edges=[line for line in lines if line.startswith('build libMNN.so: ')]
if len(edges)!=1: raise RuntimeError('Expected one explicit MNN link edge')
tokens=shlex.split(edges[0]); objects=[]
for token in tokens[3:]:
    if token in ('|','||'): break
    if not token.endswith('.o'): raise RuntimeError('Unexpected link input '+token)
    obj=BUILD/token
    if not obj.is_file(): raise RuntimeError('Missing '+str(obj))
    objects.append(obj)
if len(objects)<100: raise RuntimeError('Incomplete MNN object set')
bridge=out.parent/'asr_jni.o'
subprocess.run([str(compiler),'-std=c++17','-O2','-fPIC','-c',str(ROOT/'native/apk/asr_jni.cpp'),'-I'+str(source/'include'),'-I'+str(source/'transformers/llm/engine/include'),'-o',str(bridge)],check=True)
args=['-shared','-static-libstdc++','-Wl,--no-undefined','-Wl,-z,max-page-size=16384','-Wl,--gc-sections','-Wl,--build-id=sha1','-Wl,-soname,libqwen_asr_jni.so','-o',str(out),str(bridge)]+list(map(str,objects))+['-pthread','-llog','-ldl','-lm','-landroid','-latomic','-s']
response=out.parent/'native-link.rsp'; response.write_text('\n'.join(shlex.quote(a) for a in args)+'\n')
subprocess.run([str(compiler),'@'+str(response)],check=True)
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
report={'strategy':'single-DSO JNI + exact existing final-P0 object files, one static libc++','object_count':len(objects),'objects':{str(p.relative_to(ROOT)):digest(p) for p in objects},'jni_source_sha256':digest(ROOT/'native/apk/asr_jni.cpp'),'output_sha256':digest(out),'limitations':['New link product, not the identical P0 libMNN.so; APK UID inference still requires independent device validation']}
(ROOT/'reports/apk/native-link-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
print('SINGLE_DSO_LINK_OK',len(objects),'objects',out)
