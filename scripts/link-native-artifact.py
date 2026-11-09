#!/usr/bin/env python3
"""Link the CMake-generated explicit object inventory; no Ninja parsing or old tree."""
import argparse
import json
from pathlib import Path
import shlex
import subprocess
from importlib.util import spec_from_file_location, module_from_spec

spec = spec_from_file_location('native_sources', Path(__file__).with_name('prepare-native-sources.py'))
sources = module_from_spec(spec)
spec.loader.exec_module(sources)


def link(args):
    build = args.objects.parent.resolve()
    objects = [Path(p) for p in args.objects.read_text().splitlines() if p]
    if len(objects) != 578 or len(set(objects)) != len(objects):
        raise ValueError('Expected 578 unique explicitly built MNN objects')
    for path in objects:
        sources.safe_path(path)
        if not path.is_file() or not path.resolve().is_relative_to(build):
            raise ValueError('Object outside current build/missing')
    for path in [args.output, args.header, args.source, args.mnn_source, args.compiler]:
        sources.safe_path(path)
    if not args.header.is_file():
        raise ValueError('Generated JNI header missing')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    bridge = args.output.parent / 'asr_jni.o'
    subprocess.run([str(args.compiler), '-std=c++17', '-O2', '-fPIC', '-c', str(args.source),
                    '-I' + str(args.mnn_source / 'include'),
                    '-I' + str(args.mnn_source / 'transformers/llm/engine/include'),
                    '-I' + str(args.mnn_source / 'schema/current'),
                    '-I' + str(args.mnn_source / '3rd_party/flatbuffers/include'),
                    '-include', str(args.header), '-o', str(bridge)], check=True)
    flags = ['-shared', '-static-libstdc++', '-Wl,--no-undefined', '-Wl,-z,max-page-size=16384',
             '-Wl,--gc-sections', '-Wl,--build-id=sha1', '-Wl,-soname,libqwen_asr_jni.so',
             '-o', str(args.output), str(bridge)] + [str(p) for p in objects] + [
                 '-pthread', '-llog', '-ldl', '-lm', '-landroid', '-latomic', '-s']
    response = args.output.parent / 'native-link.rsp'
    response.write_text('\n'.join(shlex.quote(f) for f in flags) + '\n')
    subprocess.run([str(args.compiler), '@' + str(response)], check=True)
    report = {'schema': 1, 'strategy': 'CMake explicit OBJECT targets + JNI; one DSO/static libc++',
              'object_count': len(objects),
              'objects': {str(p.relative_to(build)): sources.sha(p) for p in objects},
              'graph_policy_sha256': sources.sha(args.source.parent / 'model_graph_policy.h'),
              'graph_schema_headers': {str(p.relative_to(args.mnn_source)): sources.sha(p) for p in sorted(list((args.mnn_source / 'schema/current').glob('*.h')) + list((args.mnn_source / '3rd_party/flatbuffers/include/flatbuffers').glob('*.h')))},
              'jni_source_sha256': sources.sha(args.source),
              'jni_header_sha256': sources.sha(args.header),
              'compiler_sha256': sources.sha(args.compiler),
              'output_sha256': sources.sha(args.output),
              'limitations': ['No device execution; static native identity is not runtime acceptance']}
    (args.output.parent / 'native-link-provenance.json').write_text(json.dumps(report, indent=2) + '\n')
    print('PASS explicit native link', report['output_sha256'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['objects', 'compiler', 'source', 'mnn-source', 'header', 'output']:
        parser.add_argument('--' + name, required=True, type=Path)
    link(parser.parse_args())
