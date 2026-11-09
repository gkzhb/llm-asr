#!/usr/bin/env python3
"""Native artifact/source/config/ELF gate, including up-to-date build rechecks."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sources', ROOT / 'scripts/prepare-native-sources.py')
s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)

EXPECTED_CONFIG = {
    'ANDROID_ABI': 'arm64-v8a', 'ANDROID_PLATFORM': 'android-29', 'ANDROID_STL': 'c++_static',
    'CMAKE_BUILD_TYPE': 'Release', 'MNN_BUILD_LLM': 'ON', 'MNN_BUILD_LLM_OMNI': 'ON',
    'MNN_BUILD_AUDIO': 'ON', 'MNN_BUILD_OPENCV': 'ON', 'MNN_IMGCODECS': 'ON',
    'MNN_LOW_MEMORY': 'ON', 'MNN_SUPPORT_TRANSFORMER_FUSE': 'ON', 'MNN_ARM82': 'ON',
    'MNN_KLEIDIAI': 'ON', 'MNN_KLEIDIAI_DEFAULT_ON': 'OFF', 'MNN_SME2': 'ON',
    'MNN_OPENCL': 'OFF', 'MNN_VULKAN': 'OFF', 'MNN_OPENGL': 'OFF', 'MNN_CUDA': 'OFF',
    'MNN_NNAPI': 'OFF', 'MNN_QNN': 'OFF', 'MNN_INTERNAL': 'OFF', 'MNN_JNI': 'OFF',
    'MNN_SEP_BUILD': 'OFF', 'MNN_BUILD_SHARED_LIBS': 'ON', 'LLM_SUPPORT_HTTP_RESOURCE': 'OFF',
}


def elf_check(path):
    data = path.read_bytes()
    if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
        raise ValueError('Expected ELF64 little-endian AArch64')
    phoff = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    loads = []
    relro = False
    stack = False
    for i in range(count):
        kind, flags, offset, address, _, filesz, memsz, align = struct.unpack_from('<IIQQQQQQ', data, phoff + size * i)
        if kind == 1:
            if align < 16384 or address % 16384 != offset % 16384 or flags & 3 == 3:
                raise ValueError('ELF LOAD 16KiB/W^X policy mismatch')
            loads.append({'offset': offset, 'vaddr': address, 'alignment': align})
        elif kind == 0x6474e552:
            relro = True
        elif kind == 0x6474e551:
            stack = not flags & 1
    if not loads or not relro or not stack:
        raise ValueError('ELF RELRO/nonexec-stack policy missing')
    return loads


def input_hashes():
    paths = [ROOT / 'flake.nix', ROOT / 'flake.lock', ROOT / 'native/source-lock.json',
             ROOT / 'scripts/compile-jni-object.sh', ROOT / 'scripts/compile-jvm-sources.sh',
             ROOT / 'scripts/check-jni-symbols.py',
             ROOT / 'scripts/prepare-native-sources.py', ROOT / 'scripts/build-native-artifact.sh',
             ROOT / 'scripts/link-native-artifact.py', ROOT / 'scripts/verify-native-artifact.py']
    for folder in ['native/cmake', 'native/apk', 'android/app/src', 'patches']:
        paths.extend(p for p in (ROOT / folder).rglob('*') if p.is_file())
    return {str(p.relative_to(ROOT)): s.sha(p) for p in sorted(set(paths))}


def verify(build, prepared):
    s.safe_path(build)
    s.validate(prepared)
    artifact = build / 'artifact/libqwen_asr_jni.so'
    provenance = build / 'artifact/native-link-provenance.json'
    for path in [artifact, provenance, build / 'qwen-objects.txt', build / 'CMakeCache.txt']:
        s.safe_path(path)
    report = json.loads(provenance.read_text())
    baseline = json.loads((ROOT / 'gradle/baseline-inputs.json').read_text())
    actual = s.sha(artifact)
    if actual != baseline['native']['sha256'] or report['output_sha256'] != actual:
        raise ValueError('Native identity mismatch')
    objects = [Path(p) for p in (build / 'qwen-objects.txt').read_text().splitlines()]
    if len(objects) != 578 or len(set(objects)) != 578 or report['object_count'] != 578:
        raise ValueError('Explicit object count/uniqueness mismatch')
    for path in objects:
        s.safe_path(path)
        if not path.resolve().is_relative_to(build.resolve()):
            raise ValueError('Object outside current build')
    hashes = {str(p.relative_to(build.resolve())): s.sha(p) for p in objects}
    if hashes != report['objects']:
        raise ValueError('Native object/provenance drift')
    if report['jni_source_sha256'] != s.sha(ROOT / 'native/apk/asr_jni.cpp'):
        raise ValueError('JNI source drift')
    if report.get('graph_policy_sha256') != s.sha(ROOT / 'native/apk/model_graph_policy.h'):
        raise ValueError('Native graph policy header drift')
    mnn = prepared / 'mnn'
    graph_headers = {str(p.relative_to(mnn)): s.sha(p) for p in sorted(list((mnn / 'schema/current').glob('*.h')) + list((mnn / '3rd_party/flatbuffers/include/flatbuffers').glob('*.h')))}
    if not graph_headers or report.get('graph_schema_headers') != graph_headers:
        raise ValueError('Native graph schema/include closure drift')
    header = build / 'jni-check/headers/org_llmasr_minimal_asr_JniNativeTranscription.h'
    if report['jni_header_sha256'] != s.sha(header):
        raise ValueError('JNI generated header drift')
    ndk = Path(os.environ['ANDROID_NDK_ROOT'])
    compiler = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++'
    if report['compiler_sha256'] != s.sha(compiler):
        raise ValueError('NDK compiler drift')
    cache = dict(re.findall(r'^([^#/:\n]+):[^=\n]+=(.*)$', (build / 'CMakeCache.txt').read_text(), re.M))
    if any(cache.get(key) != value for key, value in EXPECTED_CONFIG.items()):
        raise ValueError('Effective MNN configuration mismatch')
    if Path(cache['KLEIDIAI_SRC_DIR']).resolve() != (prepared / 'kleidiai').resolve():
        raise ValueError('KleidiAI source boundary mismatch')
    readelf = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
    dynamic = subprocess.check_output([str(readelf), '-d', str(artifact)], text=True)
    needed = re.findall(r'\(NEEDED\).*?\[(.*?)\]', dynamic)
    if set(needed) != {'liblog.so', 'libdl.so', 'libm.so', 'libandroid.so', 'libc.so'}:
        raise ValueError('Single DSO/static C++ runtime dependency mismatch: ' + str(needed))
    loads = elf_check(artifact)
    subprocess.run(['python3', str(ROOT / 'scripts/check-jni-symbols.py'),
                    str(build / 'jni-check/classes'), str(artifact)], check=True)
    result = {'schema': 1, 'native_sha256': actual, 'inputs': input_hashes(),
              'source_lock_sha256': s.sha(ROOT / 'native/source-lock.json'),
              'sources_report_sha256': s.sha(prepared / 'sources.json'),
              'effective_config': {k: cache[k] for k in EXPECTED_CONFIG},
              'ndk_source_properties_sha256': s.sha(ndk / 'source.properties'),
              'compiler_sha256': s.sha(compiler), 'readelf_sha256': s.sha(readelf),
              'build_tools': {tool: {'sha256': s.sha(Path(shutil.which(tool))),
                                    'version': subprocess.check_output([tool, '--version'], text=True).splitlines()[0]}
                              for tool in ['cmake', 'ninja']},
              'needed': needed, 'elf_loads': loads, 'device_4k': 'not run', 'device_16k': 'not run'}
    (build / 'artifact/verification.json').write_text(json.dumps(result, indent=2) + '\n')
    print('PASS native byte identity/config/explicit objects/JNI/ELF16KiB/static runtime')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--sources', type=Path, required=True)
    args = parser.parse_args()
    verify(args.build.absolute(), args.sources.absolute())
