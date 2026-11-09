#!/usr/bin/env python3
"""Actual bridge compiled with real JNI/Llm headers; controlled port only."""
from pathlib import Path
import argparse,os,subprocess,sys,json
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tests'))
from mixed_compiler_lifecycle import run
p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,required=True);args=p.parse_args();out=args.evidence.resolve();out.mkdir(parents=True,exist_ok=False)
source=Path(os.environ.get('JNI_MNN_SOURCE',ROOT/'.work/native-session-reuse/sources/mnn')).resolve()
frozen=Path(os.environ.get('MNN_CLEANUP_HOST_BUILD',ROOT/'.work/residentb-session-reuse/build')).resolve()
# Bind every consumed bridge/header/library input, not only a controller summary.
import hashlib
input_paths=[ROOT/'native/apk/asr_jni.cpp',ROOT/'native/apk/model_graph_policy.h',ROOT/'tests/native_residency_bridge_probe.cpp',Path(__file__),source/'transformers/llm/engine/include/llm/llm.hpp',source/'transformers/llm/engine/src/llmconfig.hpp',frozen/'mnn/libllm.so',frozen/'mnn/libMNN.so',frozen/'mnn/express/libMNN_Express.so']
input_paths+=sorted((source/'schema/current').glob('*.h'))+sorted((source/'3rd_party/flatbuffers/include/flatbuffers').glob('*.h'))
(out/'inputs.json').write_text(json.dumps({str(x.relative_to(ROOT)):hashlib.sha256(x.read_bytes()).hexdigest()for x in input_paths},indent=2)+'\n')
require=lambda v,m:None if v else (_ for _ in ()).throw(RuntimeError(m))
require(source.is_dir() and (frozen/'mnn/libllm.so').is_file(),'prepared audited host libs unavailable; not permission to rebuild/download')
java=Path(os.environ['JAVA_HOME'])
cmd=['c++','-std=c++17','-fno-rtti','-O1',str(ROOT/'tests/native_residency_bridge_probe.cpp'),'-o',str(out/'probe')]
for inc in [java/'include',java/'include/linux',source/'include',source/'transformers/llm/engine/include',source/'transformers/llm/engine/src',source/'3rd_party',source/'schema/current',source/'3rd_party/flatbuffers/include']:cmd+=['-I'+str(inc)]
for d in [frozen/'mnn',frozen/'mnn/express',frozen/'mnn/tools/audio',frozen/'mnn/tools/cv']:cmd+=['-L'+str(d),'-Wl,-rpath,'+str(d)]
cmd+=['-lllm','-lMNN_Express','-lMNN','-pthread','-ldl']
r=run(cmd,out,'compile',900);require(r.returncode==0,'actual bridge compile/link failed: '+r.stderr.decode())
cases=['normal','unprepared','loadfail','allocation','copy','utf','pending','response','cleanup','cleanupthrow','callback',
       'reentry-1-release','reentry-2-prepare','reentry-2-request','reentry-3-release','reentry-4-prepare','reentry-4-request','legacy','thread-serial']
require(len(cases)<=20,'bounded bridge scenario inventory')
for c in cases:
 r=run([str(out/'probe'),c],out,c,60);require(r.returncode==0,'bridge control failed '+c+': '+r.stderr.decode());print(r.stdout.decode(),end='')
(out/'verification.json').write_text(json.dumps({'cases':cases,'actual_bridge':True,'real_JNI_and_patched_Llm_header':True,'controlled_engine_port_only':True,'actual_ASR_Android':False},indent=2)+'\n')
print('PASS actual JNI bridge controlled fail-closed matrix; not Android/Llm inference')
