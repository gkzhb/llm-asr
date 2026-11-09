#!/usr/bin/env python3
"""Fixed ZIP production contracts; synthetic recipe only, no weights or Android runtime."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import sys
import time
import zipfile
from mixed_compiler_lifecycle import run as owned_run, guard

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'android/app/src/org/llmasr/minimal'


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def recipe(path, entries, method=zipfile.ZIP_STORED):
    # Same seekable-output recipe as docs/model-package-format.md.
    with path.open('w+b') as output:
        with zipfile.ZipFile(output, 'w', compression=method, allowZip64=False) as package:
            package.comment = b''
            for name, data in entries:
                info = zipfile.ZipInfo(name, date_time=(2024, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = (0o100600 << 16)
                info.compress_type = method
                info.extra = b''
                info.comment = b''
                info.file_size = len(data)
                with package.open(info, 'w', force_zip64=False) as entry:
                    entry.write(data)


def fixtures(out):
    out.mkdir()
    payloads = [(f'f{i}.bin', bytes([65+i]) * (3+i)) for i in range(7)]
    manifest = {'schema': 1, 'modelId': 'fixture', 'engine': 'mnn', 'files': [
        {'file': n, 'bytes': len(b), 'sha256': hashlib.sha256(b).hexdigest()} for n, b in payloads]}
    def entries(m=None, data=None):
        return [('model-package.json', json.dumps(manifest if m is None else m, separators=(',', ':')).encode())] + (payloads if data is None else data)
    recipe(out/'stored.zip', entries())
    recipe(out/'deflated.zip', list(reversed(entries())), zipfile.ZIP_DEFLATED)
    raw = (out/'stored.zip').read_bytes()
    def binary(name, edit):
        b = bytearray(raw)
        edit(b)
        (out/(name+'.zip')).write_bytes(b)
    cen = raw.index(b'PK\1\2')
    lfh = 0
    binary('symlink', lambda b: struct.pack_into('<I', b, cen+38, 0o120777 << 16))
    binary('fifo', lambda b: struct.pack_into('<I', b, cen+38, 0o010600 << 16))
    binary('dosdir', lambda b: struct.pack_into('<I', b, cen+38, 0x10))
    binary('host', lambda b: b.__setitem__(cen+5, 7))
    # Two trusted basenames of equal length isolate LFH/CEN mismatch.
    second = raw.index(b'PK\3\4', 4)
    binary('local-name', lambda b: b.__setitem__(slice(second+30, second+36), b'f1.bin'))
    def crc_bad(b):
        second_cen = raw.index(b'PK\1\2', cen+4)
        struct.pack_into('<I', b, second+14, 1234)
        struct.pack_into('<I', b, second_cen+16, 1234)
    binary('crc', crc_bad)
    binary('dd', lambda b: struct.pack_into('<H', b, 6, 8))
    binary('encrypted', lambda b: struct.pack_into('<H', b, cen+8, 1))
    binary('count', lambda b: struct.pack_into('<H', b, len(b)-12, 9))
    binary('offset', lambda b: struct.pack_into('<I', b, len(b)-6, cen+1))
    binary('zip64', lambda b: struct.pack_into('<I', b, cen+20, 0xffffffff))
    binary('extra', lambda b: struct.pack_into('<H', b, cen+30, 1))
    binary('comment', lambda b: struct.pack_into('<H', b, cen+32, 1))
    binary('method', lambda b: struct.pack_into('<H', b, cen+10, 99))
    binary('disk', lambda b: struct.pack_into('<H', b, len(b)-18, 1))
    binary('gap', lambda b: struct.pack_into('<I', b, cen+42, 1))
    (out/'trailing.zip').write_bytes(raw+b'x')
    (out/'truncated.zip').write_bytes(raw[:-1])
    binary('cen-truncated', lambda b: struct.pack_into('<I', b, len(b)-10, 45))
    binary('overlap', lambda b: struct.pack_into('<I', b, raw.index(b'PK\1\2',cen+4)+42, 0))
    binary('lfh-size', lambda b: struct.pack_into('<I', b, second+22, 4))
    binary('lfh-method', lambda b: struct.pack_into('<H', b, second+8, 8))
    binary('lfh-crc', lambda b: struct.pack_into('<I', b, second+14, 0))
    binary('cen-max', lambda b: struct.pack_into('<I', b, len(b)-10, 4097))
    binary('name-max', lambda b: struct.pack_into('<H', b, cen+28, 129))
    binary('flags', lambda b: struct.pack_into('<H', b, cen+8, 2))
    binary('directory-mode', lambda b: struct.pack_into('<I', b, cen+38, 0o040700 << 16))
    binary('device-mode', lambda b: struct.pack_into('<I', b, cen+38, 0o020600 << 16))
    # Valid structure but short/garbage DEFLATE payload must fail actual inflater termination.
    def deflate_payload(label, suffix):
        data = bytearray((out/'deflated.zip').read_bytes())
        end = len(data)-22
        c = struct.unpack_from('<I',data,end+16)[0]
        at = c
        # Select last physical entry: its data abuts CEN; preserve all structure ranges.
        last = None
        for _ in range(8):
            n = struct.unpack_from('<H',data,at+28)[0]
            offset = struct.unpack_from('<I',data,at+42)[0]
            if last is None or offset > last[0]: last=(offset,at)
            at += 46+n
        offset,central = last
        packed = struct.unpack_from('<I',data,central+20)[0]
        if suffix:
            data[c:c] = b'x'
            struct.pack_into('<I',data,central+1+20,packed+1)
            struct.pack_into('<I',data,offset+18,packed+1)
            struct.pack_into('<I',data,len(data)-6,c+1)
        else:
            del data[c-1]
            struct.pack_into('<I',data,central-1+20,packed-1)
            struct.pack_into('<I',data,offset+18,packed-1)
            struct.pack_into('<I',data,len(data)-6,c-1)
        (out/(label+'.zip')).write_bytes(data)
    deflate_payload('deflate-truncated',False)
    deflate_payload('deflate-suffix',True)
    wrong = json.loads(json.dumps(manifest))
    wrong['files'][0]['sha256'] = hashlib.sha256(b'xxx').hexdigest()
    recipe(out/'self-trust.zip', entries(wrong, [('f0.bin', b'xxx')]+payloads[1:]))
    recipe(out/'sha.zip', entries(data=[('f0.bin', b'xxx')]+payloads[1:]))
    recipe(out/'unknown.zip', entries()+[('other', b'x')])
    recipe(out/'missing.zip', entries()[:-1])
    # Duplicate central-name fixture without relying on ZipFile's duplicate writer policy.
    duplicate=bytearray(raw)
    first_model=raw.index(b'PK\1\2',cen+4)
    next_model=raw.index(b'PK\1\2',first_model+4)
    duplicate[next_model+46:next_model+52]=b'f0.bin'
    (out/'duplicate.zip').write_bytes(duplicate)
    recipe(out/'too-large-output.zip',entries(data=[('f0.bin',b'AAAA')]+payloads[1:]))
    # Advertise trusted length but actually inflate one extra byte: header-only limits are insufficient.
    recipe(out/'actual-output.zip',entries(data=[('f0.bin',b'AAAA')]+payloads[1:]),zipfile.ZIP_DEFLATED)
    output=bytearray((out/'actual-output.zip').read_bytes())
    position=struct.unpack_from('<I',output,len(output)-6)[0]
    for _ in range(8):
        n=struct.unpack_from('<H',output,position+28)[0]
        name=bytes(output[position+46:position+46+n])
        if name==b'f0.bin':
            local=struct.unpack_from('<I',output,position+42)[0]
            struct.pack_into('<I',output,position+24,3)
            struct.pack_into('<I',output,local+22,3)
            break
        position+=46+n
    (out/'actual-output.zip').write_bytes(output)
    for name in ['../f0.bin', '/f0.bin', 'C:f0.bin', 'f0\\bin', 'F0.bin', 'f0.bin/', '%2ef0.bin', 'é.bin']:
        label = hashlib.sha256(name.encode()).hexdigest()[:8]
        recipe(out/('path-'+label+'.zip'), entries(data=[(name, b'AAA')]+payloads[1:]))
    good_json = json.dumps(manifest, separators=(',', ':')).encode()
    (out/'good.json').write_bytes(good_json)
    bad_json = [b'{"schema":1,'+good_json[1:], good_json+b'x', b'\xef\xbb\xbf'+good_json,
                good_json.replace(b'"schema":1', b'"schema":01'),
                good_json.replace(b'"schema":1', b'"schema":1e0'),
                good_json.replace(b'"schema":1', b'"schema":1.0'),
                good_json.replace(b'"bytes":3', b'"bytes":"3"'),
                good_json.replace(b'"bytes":3', b'"bytes":-3'),
                good_json.replace(b'"bytes":3', b'"bytes":9223372036854775808'),
                good_json.replace(b'"bytes":3', b'"bytes":null'),
                good_json.replace(b'"engine":"mnn"', b'"engine":"other"'),
                good_json.replace(b'"modelId":"fixture"', b'"modelId":"other"'),
                good_json.replace(b'"file":"f0.bin"', b'"file":"f0.bin","file":"f0.bin"'),
                good_json.replace(b'"file":"f0.bin"', b'"file":"f1.bin"'),
                good_json.replace(b'"file":"f0.bin"', b'"file":"f0\\u002ebin"'),
                good_json.replace(b'"schema":1', b'"unknown":1'),
                good_json.replace(b'"sha256":"',b'"sha256":"A',1),
                good_json.replace(b'"schema":1',b'"schema":true'),
                good_json.replace(b'"schema":1',b'"schema":null'),
                good_json.replace(b'"modelId":"fixture"',b'"modelId":"fi\\xffxture"'), b' '*16385]
    for i,b in enumerate(bad_json):
        (out/f'bad-{i}.json').write_bytes(b)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--quick', action='store_true', help='incremental parser/source checkpoint, not final matrix')
    parser.add_argument('--catalog-cleanup-only', action='store_true', help='six tiny actual catalog ZIP cleanup regressions only')
    parser.add_argument('--regressions', action='store_true', help='same fresh snapshot plus original core regression entrypoints')
    args = parser.parse_args()
    out = args.evidence_dir.resolve()
    require(not out.exists(), 'fresh evidence directory required')
    out.mkdir(parents=True)
    start = time.monotonic()
    fixture = fixtures(out/'fixtures')
    script = (ROOT/'scripts/test-minimal-apk.sh').read_text()
    names = script.split('host_classes=(',1)[1].split(')',1)[0].split()
    names = list(dict.fromkeys(names + ['model.FixedModelPackage', 'model.ModelZipArchive', 'model.ModelZipSource']))
    sources = []
    for fq in names:
        p = BASE / fq.replace('.', '/')
        found = [p.with_suffix(ext) for ext in ('.kt','.java') if p.with_suffix(ext).is_file()]
        require(len(found)==1, 'exact production owner '+fq)
        sources.append(found[0])
    sources += [ROOT/'tests'/name for name in ('ModelZipImportTest.kt','ModelAndroidHelpersTest.kt','ModelRepositoryCancelTest.kt')]
    if args.regressions:
        import re
        callers = re.findall(r'^  (tests/\S+\.(?:kt|java))',script,re.M)
        require('tests/ModelRepositoryTest.kt' in callers and len(callers) >= 30, 'canonical original regression caller inventory')
        sources += [ROOT/name for name in callers]
        sources = list(dict.fromkeys(sources))
    (out/'inputs.json').write_text(json.dumps([{'path':str(p.relative_to(ROOT)), 'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources],indent=2)+'\n')
    snapshot = out/'sources'
    snapshot.mkdir()
    frozen = []
    for source in sources:
        target = snapshot/source.name
        target.write_bytes(source.read_bytes())
        frozen.append(target)
    counts = {'compile': 0, 'jvm': 0}
    attempted = []
    def execute(argv, label, timeout):
        attempted.append(label)
        (out/'attempted.json').write_text(json.dumps(attempted,indent=2)+'\n')
        return owned_run(argv,out,label,timeout)
    def safe_restore(path, original):
        guard(out)
        for label in attempted:
            state=json.loads((out/(label+'.json')).read_text())
            require(state.get('owned_exited') is True and state.get('root_reaped') is True,'retain mutant with absent/unknown attempted child exit '+label)
        path.write_bytes(original)
    stdlib = Path(os.environ['KOTLIN_HOME'])/'lib/kotlin-stdlib.jar'
    def compile_source(label):
        counts['compile'] += 1
        require(counts['compile'] <= 24 and time.monotonic()-start < 6600, 'ZIP compile/total budget')
        classes = out/('classes-'+label)
        result = execute(['bash',str(ROOT/'scripts/compile-jvm-sources.sh'),str(classes),'',*map(str,frozen)],label+'-compile',180)
        require(result.returncode==0, 'compile/tool failure not target red '+label+': '+result.stderr.decode(errors='replace'))
        return classes
    def run_suite(classes, label, diagnostic=None, mode=None):
        counts['jvm'] += 1
        require(counts['jvm'] <= 64 and time.monotonic()-start < 6600, 'ZIP JVM/total budget')
        cp = str(classes)+':'+str(stdlib)
        result = execute(['java','-Xmx256m','-cp',cp,'ModelZipImportTest',str(fixture),mode or ('quick' if args.quick else 'all')],label+'-jvm',30)
        text = (result.stdout+result.stderr).decode(errors='replace')
        if diagnostic is None:
            require(result.returncode==0, 'production contract failed '+label+': '+text)
        else:
            require(result.returncode==1 and 'java.lang.AssertionError: '+diagnostic in text, 'not exact compiled production target red '+label+': '+text)
    def catalog_cleanup(classes):
        cp = str(classes)+':'+str(stdlib)
        for mode in ('remove', 'close', 'blocked', 'cancel-normal', 'cancel-blocked', 'cancel-throw'):
            counts['jvm'] += 1
            require(counts['jvm'] <= 64 and time.monotonic()-start < 6600, 'ZIP catalog JVM/total budget')
            result = execute(['java','-Xmx256m','-cp',cp,'ModelZipImportTest','catalog-cleanup',mode,str(out/('catalog-'+mode))], 'catalog-'+mode, 30)
            require(result.returncode==0, 'catalog ZIP cleanup regression '+mode+': '+result.stderr.decode(errors='replace'))
            print(result.stdout.decode(), end='', flush=True)
    green = compile_source('green')
    if args.catalog_cleanup_only or not args.quick:
        catalog_cleanup(green)
    if args.catalog_cleanup_only:
        guard(out)
        return
    run_suite(green,'green')
    if args.regressions:
        for caller in ('ModelRepositoryTest','ModelPrivateRootTest','ModelRepositoryCancelTest','ModelRepositoryDeleteTest','ModelAndroidHelpersTest','ModelManagementControllerTest','ModelProviderBoundaryTest','ModelReadinessTest','ModelOperationControlTest'):
            counts['jvm'] += 1
            require(counts['jvm'] <= 64, 'ZIP regression JVM budget')
            cp = str(green)+':'+str(stdlib)
            result = execute(['java','-Xmx256m','-cp',cp,caller],caller+'-jvm',30)
            require(result.returncode==0,'original production regression failed '+caller+': '+result.stderr.decode(errors='replace'))
            print('PASS original core regression '+caller,flush=True)
    if not args.quick:
        mutations = [
            ('trust','FixedModelPackage.kt','tuple.second != entry.sha256()','false','ZIP trusted manifest cannot be replaced by package hashes'),
            ('type','ModelZipArchive.java','if (host == 3 && type != 0 && type != 0100000) throw bad();','if (false) throw bad();','ZIP CEN symbolic link must be refused before model mutation'),
            ('local','ModelZipArchive.java','!localName.equals(item.name) ||','false ||','ZIP local and central names must agree'),
            ('crc','ModelZipArchive.java','count != item.size || crc.getValue() != item.crc','count != item.size','ZIP bad CRC cannot pass even with trusted SHA'),
            ('actual','ModelZipSource.kt','if (n > archiveLimit - count) throw IOException("模型包超过输入上限")','if (false) throw IOException("模型包超过输入上限")','ZIP actual byte budget must stop before overflow write'),
            ('close','ModelRepository.java','if (primary == null) session.close();','if (primary == null) { /* omitted source close */ }','ZIP session cleanup must finish before final SHA and proof commit'),
            ('dirty','ModelRepository.java','            mutationGuard.beforeMutation();\n            gate.beforeMutation();','            /* skipped durable dirty */\n            gate.beforeMutation();','ZIP durable dirty must precede first model mutation'),
            ('owner','ModelManagementController.kt','            b.phase = ModelManagementState.Phase.CANCELLING','            control.complete(control.peekActive(), false)\n            b.phase = ModelManagementState.Phase.CANCELLING','ZIP blocked provider close retains shared owner and archive'),
        ]
        for boundary in ('archive','checked','dirty','part','rename','receipt','committed'):
            shared = out/('crash-'+boundary)
            for mode in ('crash','recover'):
                counts['jvm'] += 1
                require(counts['jvm'] <= 64 and time.monotonic()-start < 6600, 'ZIP crash JVM budget')
                cp = str(green)+':'+str(stdlib)
                label = mode+'-'+boundary
                result = execute(['java','-Xmx256m','-cp',cp,'ModelZipImportTest',str(fixture),label,str(shared)],label+'-jvm',30)
                require(result.returncode == (23 if mode == 'crash' else 0), 'ZIP actual independent JVM '+label+' failed: '+result.stderr.decode(errors='replace'))
                record = json.loads((out/(label+'-jvm.json')).read_text())
                require(record.get('owned_exited') is True and record.get('root_reaped') is True,'ZIP crash child actual exit before recovery')
            print('PASS ZIP actual independent JVM halt23/recovery0 '+boundary,flush=True)
        for name,filename,old,new,diagnostic in mutations:
            path = snapshot/filename
            original = path.read_bytes()
            source = original.decode()
            require(source.count(old)==1, 'unique production mutation anchor '+name)
            primary = None
            try:
                path.write_text(source.replace(old,new))
                (out/(name+'-mutation.json')).write_text(json.dumps({'source':filename,'original_sha256':hashlib.sha256(original).hexdigest(),'mutant_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'diagnostic':diagnostic},indent=2)+'\n')
                mutant = compile_source(name+'-mutant')
                run_suite(mutant,name+'-mutant',diagnostic)
            except BaseException as failure:
                primary = failure
                raise
            finally:
                # Never restore bytes while any attempted child or dependency is alive/unknown.
                try:
                    safe_restore(path,original)
                except BaseException as cleanup:
                    if primary is None:
                        raise
                    primary.add_note('source restoration refused; retained inputs: '+repr(cleanup))
            require(hashlib.sha256(path.read_bytes()).hexdigest()==hashlib.sha256(original).hexdigest(),'exact source restoration')
            restored = compile_source(name+'-restored')
            run_suite(restored,name+'-restored')
            print('PASS ZIP compiled production mutant '+name+' compile0/exact AssertionError1/restored0',flush=True)
    require(time.monotonic()-start < 6600, 'ZIP driver deadline exceeded')
    guard(out)
    (out/'summary.json').write_text(json.dumps({'checkpoint':args.quick,**counts,'owned_exited':True,'elapsed':time.monotonic()-start},indent=2)+'\n')
    print('PASS ZIP '+('checkpoint (not full matrix)' if args.quick else 'production suite'), flush=True)


if __name__ == '__main__':
    main()
