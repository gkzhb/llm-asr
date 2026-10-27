#!/usr/bin/env bash
# Real production definitions, no models/download/device. Explicit source/build.
# --build creates an independent CPU host tree; --prebuilt consumes its audited
# current artifacts without rebuilding. Both record owned exits and full streams.
set -euo pipefail
: "${JNI_MNN_SOURCE:?locked production mnn directory required}"
: "${MNN_CLEANUP_HOST_BUILD:?explicit independent host build directory required}"
: "${NATIVE_CLEANUP_OUT:?nonexistent receipt directory required}"
mode=${1:?--build or --prebuilt}
python3 -B - "$mode" "$JNI_MNN_SOURCE" "$MNN_CLEANUP_HOST_BUILD" "$NATIVE_CLEANUP_OUT" <<'PY'
from pathlib import Path
import sys,json,hashlib,shutil,importlib.util
R=Path.cwd();mode,source,build,out=sys.argv[1:];source=Path(source).resolve();build=Path(build).resolve();out=Path(out).resolve()
if mode not in ['--build','--prebuilt']:raise ValueError('explicit mode required')
out.mkdir(parents=True,exist_ok=False);sys.path.insert(0,str(R/'tests'))
from mixed_compiler_lifecycle import run
spec=importlib.util.spec_from_file_location('native_sources',R/'scripts/prepare-native-sources.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);m.validate(source.parent)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def execute(label,argv,seconds):
 r=run(argv,out,label,seconds)
 if r.returncode:raise RuntimeError(label+': '+r.stderr.decode())
 print(r.stdout.decode(),end='',flush=True)
if mode=='--build':
 if build.exists():raise ValueError('fresh host build required')
 project=out/'project';project.mkdir();shutil.copytree(source,project/'mnn')
 for path,needle in [('express/module/StaticModule.hpp','class StaticModule : public Module {'),('source/backend/cpu/CPUAttention.hpp','class CPUAttention : public Execution {')]:
  p=project/'mnn'/path;s=p.read_text()
  if s.count(needle)!=1:raise ValueError('observer anchor differs')
  p.write_text(s.replace(needle,needle+'\n    friend struct RequestCleanupProbe;'))
 cmake='''cmake_minimum_required(VERSION 3.18)
project(RequestReuseProbe LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(MNN_BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD ON CACHE BOOL "" FORCE)
foreach(flag MNN_BUILD_TOOLS MNN_BUILD_DEMO MNN_BUILD_TEST MNN_BUILD_BENCHMARK MNN_BUILD_TRAIN MNN_BUILD_CONVERTER MNN_BUILD_QUANTOOLS MNN_EVALUATION MNN_BUILD_DIFFUSION MNN_BUILD_OPENCV MNN_BUILD_AUDIO MNN_INTERNAL MNN_JNI MNN_METAL MNN_OPENCL MNN_OPENGL MNN_VULKAN MNN_CUDA MNN_MUSA MNN_TENSORRT MNN_COREML MNN_NNAPI MNN_QNN MNN_RKNN MNN_HEXAGON MNN_ONEDNN MNN_KLEIDIAI MNN_BUILD_CODEGEN MNN_OPENMP)
 set(${flag} OFF CACHE BOOL "" FORCE)
endforeach()
set(MNN_BUILD_LLM ON CACHE BOOL "" FORCE)
set(MNN_BUILD_LLM_OMNI ON CACHE BOOL "" FORCE)
set(MNN_LLM_BUILD_DEMO OFF CACHE BOOL "" FORCE)
set(MNN_LLM_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(LLM_SUPPORT_HTTP_RESOURCE OFF CACHE BOOL "" FORCE)
set(BUILD_MLS OFF CACHE BOOL "" FORCE)
add_subdirectory(mnn mnn)
set(REPO "@REPO@")
set(LLMDIR ${CMAKE_CURRENT_SOURCE_DIR}/mnn/transformers/llm/engine)
foreach(probe native_session_reuse_probe native_llm_request_probe)
 add_executable(${probe} ${REPO}/tests/${probe}.cpp)
 target_include_directories(${probe} PRIVATE ${LLMDIR}/include ${LLMDIR}/src mnn/3rd_party mnn/include mnn/source mnn/express mnn/schema/current mnn/3rd_party/flatbuffers/include)
 target_compile_definitions(${probe} PRIVATE MNN_SUPPORT_TRANSFORMER_FUSE)
 target_link_libraries(${probe} PRIVATE llm MNN_Express MNN)
endforeach()
add_executable(native_tokenizer_request_probe ${REPO}/tests/native_tokenizer_request_probe.cpp ${LLMDIR}/src/tokenizer/unicode.cpp ${LLMDIR}/src/tokenizer/unicode_data.cpp)
target_compile_definitions(native_tokenizer_request_probe PRIVATE LLM_USE_JINJA)
target_include_directories(native_tokenizer_request_probe PRIVATE ${LLMDIR}/include ${LLMDIR}/src mnn/3rd_party mnn/include)
target_link_libraries(native_tokenizer_request_probe PRIVATE MNN_Express MNN)
'''
 (project/'CMakeLists.txt').write_text(cmake.replace('@REPO@',str(R)))
 execute('configure',['cmake','-S',str(project),'-B',str(build),'-G','Ninja','-DCMAKE_BUILD_TYPE=Release'],180)
 execute('build',['cmake','--build',str(build),'--target','MNN','MNN_Express','MNNAudio','llm','native_session_reuse_probe','native_llm_request_probe','native_tokenizer_request_probe','-j2'],7200)
# Verify the compiled source root reproduces current locked production except
# the two observer-only friends; never accept a stale old cleanup build.
cache=(build/'CMakeCache.txt').read_text().splitlines()
home=next((line.split('=',1)[1] for line in cache if line.startswith('CMAKE_HOME_DIRECTORY:INTERNAL=')),None)
if home is None:raise ValueError('missing audited CMake source root')
host=Path(home)/'sources/mnn'
if not host.is_dir():host=Path(home)/'mnn'
expected=m.inventory(source);actual=m.inventory(host)
for path,needle in [('express/module/StaticModule.hpp','class StaticModule : public Module {'),('source/backend/cpu/CPUAttention.hpp','class CPUAttention : public Execution {')]:
    canonical=(host/path).read_text().replace(needle+'\n    friend struct RequestCleanupProbe;',needle)
    if hashlib.sha256(canonical.encode()).hexdigest()!=expected[path]:raise ValueError('observer differs from production')
    actual[path]=expected[path]
if actual!=expected:raise ValueError('host source differs from current production')
# Bind exact compile declarations and dependencies, not handwritten fake cleanup.
commands=json.loads((build/'compile_commands.json').read_text());entries=[]
for row in commands:
 p=Path(row['file']).resolve()
 if p.is_file():entries.append({'path':str(p),'sha256':sha(p),'command':row.get('command',row.get('arguments'))})
inputs=[R/'native/source-lock.json',R/'patches/mnn-asr-request-cleanup.patch',source.parent/'sources.json',R/'tests/native_request_cleanup_test.sh']
inputs=[p for p in inputs if p.is_file()]+[R/'tests'/p for p in ['native_session_reuse_probe.cpp','native_llm_request_probe.cpp','native_tokenizer_request_probe.cpp','native_request_probe_support.hpp']]
inputs+=list(source.rglob('*.hpp'))+list(source.rglob('*.h'))
artifacts=[build/p for p in ['native_session_reuse_probe','native_llm_request_probe','native_tokenizer_request_probe','mnn/libMNN.so','mnn/express/libMNN_Express.so','mnn/libllm.so','mnn/tools/audio/libMNNAudio.so']]
if any(not p.is_file()for p in artifacts):raise ValueError('explicit current host artifacts missing')
(out/'inputs.json').write_text(json.dumps({'mode':mode,'source':str(source),'build':str(build),'files':{str(p):sha(p)for p in inputs},'artifacts':{str(p):sha(p)for p in artifacts},'compile_entries':entries},indent=2)+'\n')
# Generate only public synthetic BPE vocabulary; never consume user tokenizer.
vocab=out/'synthetic-vocab.mtok'
# Exact public three-token a/b/ab fixture, generated here from the production
# binary format (no normalizer/pretokenizer, one a+b merge, decoder sequence).
vocab.write_bytes(bytes.fromhex('34333020340a30203020300a0a00000003000000000001000000010061000000000200616202000000010062010000000000000001000000000000000400000000'))
(out/'vocab-input.json').write_text(json.dumps({'sha256':sha(vocab),'public_synthetic_only':True,'tokens':['a','ab','b']})+'\n')
for case in ['add','reshape','matmul','attention','session']:execute(case,[str(build/'native_session_reuse_probe'),case],60)
for case in ['llm','omni']:execute(case,[str(build/'native_llm_request_probe'),case,str(vocab)],60)
execute('bpe',[str(build/'native_tokenizer_request_probe'),str(vocab)],60)
(out/'verification.json').write_text(json.dumps({'actual_definitions':True,'normal_Module_reuse':True,'graphs':['add','reshape','matmul','attention','session'],'ASR_Android_device':False})+'\n')
PY
