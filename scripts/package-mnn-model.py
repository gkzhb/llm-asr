#!/usr/bin/env python3
"""Stream an audited seven-asset MNN directory into a deterministic strict ZIP32.

Default current-app mode is pinned to this repository's APK tuple. Generic mode
is packaging only, not model validation or Android catalog admission. Stdlib only;
inputs must remain quiescent until the command returns. No dependencies/network.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import struct
import sys
import zipfile
import zlib
from contextlib import contextmanager

ROOT = Path(__file__).resolve().parents[1]
MODEL_ID = 'qwen3-asr-0.6b-mnn-fp16-p0-1'
FINGERPRINT = '02624d4f3f12ce44ce8d1191d925ff2dddd3eedbe731692716bb017a284521a6'
JSON_NAME = 'model-package.json'
JSON_LIMIT = 16384
CHUNK = 65536
MODE = 0o100600 << 16
DATE = (2024, 1, 1, 0, 0, 0)
NAME = re.compile(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z')


def require(condition, message):
    if not condition:
        raise ValueError(message)


@contextmanager
def directory(path):
    """Anchor every component using no-follow directory descriptors (POSIX)."""
    path = Path(os.path.abspath(path))
    fd = os.open(path.anchor, os.O_RDONLY | os.O_DIRECTORY)
    try:
        for part in path.parts[1:]:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = child
        yield fd, path
    finally:
        os.close(fd)


@contextmanager
def regular(fd, name):
    # NONBLOCK prevents a raced FIFO from hanging before the regular-file check.
    opened = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd)
    try:
        require(stat.S_ISREG(os.fstat(opened).st_mode), 'regular non-symlink file required: ' + name)
        stream = os.fdopen(opened, 'rb')
        opened = None
        with stream:
            yield stream
    finally:
        if opened is not None:
            os.close(opened)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON key: ' + key)
        result[key] = value
    return result


def load_json(path):
    path = Path(os.path.abspath(path))
    with directory(path.parent) as (fd, _), regular(fd, path.name) as stream:
        data = stream.read(JSON_LIMIT + 1)
    require(len(data) <= JSON_LIMIT, 'policy exceeds 16384 bytes')
    return json.loads(data.decode('ascii'), object_pairs_hook=unique_object)


def validate_policy(policy):
    require(isinstance(policy, dict) and set(policy) == {'schema', 'modelId', 'engine', 'files'},
            'policy requires exact schema/modelId/engine/files keys')
    require(type(policy['schema']) is int and policy['schema'] == 1, 'schema must be integer 1')
    require(policy['engine'] == 'mnn', 'engine must be mnn')
    require(isinstance(policy['modelId'], str) and NAME.fullmatch(policy['modelId']), 'unsafe modelId')
    entries = policy['files']
    require(isinstance(entries, list) and len(entries) == 7, 'exactly seven assets required')
    names = {JSON_NAME}
    for entry in entries:
        require(isinstance(entry, dict) and set(entry) == {'file', 'bytes', 'sha256'}, 'invalid asset keys')
        name = entry['file']
        require(isinstance(name, str) and NAME.fullmatch(name) and name not in ('.', '..'), 'unsafe asset name')
        require(name not in names, 'duplicate/reserved asset name')
        names.add(name)
        require(type(entry['bytes']) is int and 0 < entry['bytes'] < zipfile.ZIP64_LIMIT, 'invalid asset size')
        require(isinstance(entry['sha256'], str) and re.fullmatch('[0-9a-f]{64}', entry['sha256']), 'invalid asset SHA256')
    require(not any(e['file'] + '.part' in names for e in entries), 'asset/.part name collision')
    require(sum(len(n) for n in names) <= 1024, 'name budget exceeded')
    require(sum(e['bytes'] for e in entries) + JSON_LIMIT + 4096 < zipfile.ZIP64_LIMIT,
            'archive exceeds conservative strict ZIP32 writer limit (2 GiB)')
    return policy


def fingerprint(entries):
    canonical = ''.join(f"{e['file']}:{e['bytes']}:{e['sha256']}\n" for e in entries)
    return hashlib.sha256(canonical.encode('ascii')).hexdigest()


def current_policy():
    manifest = load_json(ROOT / 'reports/p0/mnn-model-manifest.json')
    entries = manifest['files']
    require(fingerprint(entries) == FINGERPRINT, 'repository manifest differs from APK trusted tuple')
    return validate_policy({'schema': 1, 'modelId': MODEL_ID, 'engine': 'mnn', 'files': entries})


def validate_variant(policy):
    require(isinstance(policy, dict) and set(policy) == {'schema', 'modelId', 'engine', 'architecture', 'runtimePolicy', 'precision', 'displayName', 'contentSha256', 'files'}, 'exact v2 descriptor required')
    require(type(policy['schema']) is int and policy['schema'] == 2 and policy['engine'] == 'mnn' and policy['architecture'] == 'qwen3-asr-0.6b' and policy['runtimePolicy'] == 'qwen3-asr-mnn-cpu-v2', 'unsupported variant architecture/policy')
    require(isinstance(policy['modelId'], str) and re.fullmatch('[a-z0-9][a-z0-9._-]{0,95}', policy['modelId']) and policy['modelId'] != MODEL_ID, 'unsafe/reserved variant modelId')
    require(policy['precision'] in ('FP16', 'INT8', 'INT4', 'MIXED'), 'unsupported declared precision')
    label = policy['displayName']
    require(isinstance(label, str) and 1 <= len(label) <= 96 and all(32 <= ord(c) <= 126 and c not in '\\"\\\\' for c in label), 'invalid display name')
    adapted = {'schema': 1, 'modelId': policy['modelId'], 'engine': 'mnn', 'files': policy['files']}
    validate_policy(adapted)
    require({e['file'] for e in policy['files']} == {'audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight', 'tokenizer.txt', 'llm_config.json', 'config.json'}, 'exact variant leaves required')
    require(sum(e['bytes'] for e in policy['files']) <= 2013265920, 'variant total exceeds 1920MiB')
    fixed = {e['file']: e for e in current_policy()['files']}
    for e in policy['files']:
        if e['file'] in ('tokenizer.txt', 'config.json'):
            require(e == fixed[e['file']], 'fixed tokenizer/runtime configuration contract required')
        maximum = 1610612736 if e['file'].endswith('.weight') else 67108864 if e['file'].endswith('.mnn') else 16384 if e['file'] == 'llm_config.json' else fixed[e['file']]['bytes']
        require(e['bytes'] <= maximum, 'variant asset bound exceeded')
    canonical = 'llm-asr-content-v2\nmnn\nqwen3-asr-0.6b\nqwen3-asr-mnn-cpu-v2\n' + ''.join(f"{e['file']}:{e['bytes']}:{e['sha256']}\n" for e in sorted(policy['files'], key=lambda e: e['file']))
    require(policy['contentSha256'] == hashlib.sha256(canonical.encode('ascii')).hexdigest(), 'content identity mismatch')
    names = ['audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight', 'tokenizer.txt', 'llm_config.json', 'config.json']
    policy['files'] = sorted(policy['files'], key=lambda e: names.index(e['file']))
    return policy


def variant_config(data, policy):
    require(len(data) <= JSON_LIMIT, 'llm_config exceeds limit')
    config = json.loads(data.decode('ascii'), object_pairs_hook=unique_object)
    lines = [line[len('P0_EFFECTIVE_CONFIG '):] for line in (ROOT / 'reports/p0/device-inference-final-patches.txt').read_text().splitlines() if line.startswith('P0_EFFECTIVE_CONFIG ')]
    require(len(lines) == 1, 'approved configuration missing')
    combined = json.loads(lines[0], object_pairs_hook=unique_object)
    baseline = dict(list(combined.items())[list(combined).index('model_type'):])
    require(set(config) == set(baseline) and {k: v for k, v in config.items() if k != 'tie_embeddings'} == {k: v for k, v in baseline.items() if k != 'tie_embeddings'}, 'fixed architecture/template contract mismatch')
    tie = config['tie_embeddings']
    require(isinstance(tie, dict) and set(tie) == {'weight_offset', 'alpha_offset', 'alpha_size', 'quant_bit', 'quant_block', 'alpha_dtype'}, 'unsupported tie layout fields')
    for k in ('weight_offset', 'alpha_offset', 'alpha_size', 'quant_bit', 'quant_block'):
        require(type(tie[k]) is int, 'integer tie layout required')
    start, alpha, size, bits, block = (tie[k] for k in ('weight_offset', 'alpha_offset', 'alpha_size', 'quant_bit', 'quant_block'))
    dtype = tie['alpha_dtype']; require(bits in (4, 8, 16) and block in (32, 64, 128, 256, 512, 1024) and dtype in ('fp16', 'fp32'), 'unsupported tie layout')
    width = 2 if dtype == 'fp16' else 4
    weight = next(e['bytes'] for e in policy['files'] if e['file'] == 'llm.mnn.weight')
    require(start > 0 and start % width == 0 and alpha == start + 151936 * 1024 * bits // 8 and 0 <= size <= weight - alpha, 'tie range exceeds paired weight / separate embedding unsupported')
    if bits == 16:
        require(size == 0 and dtype == 'fp16' and block == 64, 'invalid FP16 tie layout')
    else:
        count = 151936 * (1024 // block)
        require(size in (count * width, count * width * 2) and size <= 67108864 and size // width * 4 <= 134217728, 'invalid/bounded alpha layout required')
    baseline['tie_embeddings'] = {k: tie[k] for k in ('weight_offset', 'alpha_offset', 'alpha_size', 'quant_bit', 'quant_block', 'alpha_dtype')}
    require(data == json.dumps(baseline, ensure_ascii=False, indent=4).encode('ascii'), 'noncanonical llm_config bytes; source is never rewritten')


def select_policy(mode, manifest):
    if mode == 'variant-v2':
        require(manifest is not None, 'variant-v2 requires complete external --manifest')
        return validate_variant(load_json(manifest))
    if mode == 'current-app':
        expected = current_policy()
        if manifest is not None:
            require(validate_policy(load_json(manifest)) == expected, 'external policy differs from current APK trust')
        return expected
    require(manifest is not None, 'generic mode requires an explicit external --manifest')
    policy = validate_policy(load_json(manifest))
    require(policy['modelId'].startswith('GENERIC-NOT-IMPORTABLE-'),
            'generic modelId must begin GENERIC-NOT-IMPORTABLE-; current APK admission is not implied')
    return policy


def json_bytes(policy):
    # Rebuild key order rather than allowing input key ordering to affect identity.
    ordered = {'schema': policy['schema'], 'modelId': policy['modelId'], 'engine': 'mnn'}
    if policy['schema'] == 2:
        ordered.update({k: policy[k] for k in ('architecture', 'runtimePolicy', 'precision', 'displayName', 'contentSha256')})
    entries = policy['files']
    if policy['schema'] == 2:
        names = ['audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight', 'tokenizer.txt', 'llm_config.json', 'config.json']
        entries = sorted(entries, key=lambda e: names.index(e['file']))
    ordered['files'] = [{k: e[k] for k in ('file', 'bytes', 'sha256')} for e in entries]
    data = (json.dumps(ordered, ensure_ascii=True, separators=(',', ':')) + '\n').encode('ascii')
    require(len(data) <= JSON_LIMIT, 'package JSON too large')
    return data


def source_names(fd, entries):
    require(set(os.listdir(fd)) == {e['file'] for e in entries},
            'source directory must contain exactly seven declared assets (no extras or package JSON)')


def stream_asset(source, expected, target=None):
    require(os.fstat(source.fileno()).st_size == expected['bytes'], 'source size mismatch: ' + expected['file'])
    count, crc = 0, 0
    digest = hashlib.sha256()
    while block := source.read(CHUNK):
        count += len(block)
        require(count <= expected['bytes'], 'asset grew: ' + expected['file'])
        digest.update(block)
        crc = zlib.crc32(block, crc)
        if target is not None:
            target.write(block)
    require(count == expected['bytes'] and digest.hexdigest() == expected['sha256'],
            'asset size/SHA256 mismatch: ' + expected['file'])
    return crc


def zip_info(name, size):
    info = zipfile.ZipInfo(name, DATE)
    info.create_system = 3
    info.create_version = info.extract_version = 20
    info.external_attr = MODE
    info.compress_type = zipfile.ZIP_STORED
    info.file_size = size
    return info


def write_archive(target, source_fd, policy, data):
    with zipfile.ZipFile(target, 'w', allowZip64=False) as archive:
        archive.writestr(zip_info(JSON_NAME, len(data)), data)
        for entry in policy['files']:
            info = zip_info(entry['file'], entry['bytes'])
            with regular(source_fd, entry['file']) as source, archive.open(info, 'w', force_zip64=False) as member:
                stream_asset(source, entry, member)
    target.flush()


def verify_headers(target, infos, length):
    """Verify exact writer subset: contiguous LFH/data/CEN/EOCD, no DD/extra/etc."""
    cursor = 0
    for info in infos:
        name = info.filename.encode('ascii')
        require(info.header_offset == cursor, 'non-contiguous local entries')
        target.seek(cursor)
        expected = struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, 0, 0, 0, 22561,
                               info.CRC, info.file_size, info.file_size, len(name), 0) + name
        require(target.read(len(expected)) == expected, 'unsupported local ZIP header')
        cursor += len(expected) + info.file_size
    central_start = cursor
    for info in infos:
        name = info.filename.encode('ascii')
        target.seek(cursor)
        expected = struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 788, 20, 0, 0, 0, 22561,
                               info.CRC, info.file_size, info.file_size, len(name), 0, 0, 0, 0,
                               MODE, info.header_offset) + name
        require(target.read(len(expected)) == expected, 'unsupported central ZIP header')
        cursor += len(expected)
    central_size = cursor - central_start
    require(central_size <= 4096, 'central directory budget exceeded')
    target.seek(cursor)
    expected = struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, 8, 8, central_size, central_start, 0)
    require(target.read(23) == expected and cursor + 22 == length, 'unsupported ZIP end/ZIP64/trailing bytes')


def verify_archive(target, policy, data):
    target.seek(0, os.SEEK_END)
    length = target.tell()
    names = [JSON_NAME] + [e['file'] for e in policy['files']]
    with zipfile.ZipFile(target, 'r', allowZip64=False) as archive:
        infos = archive.infolist()
        require([i.filename for i in infos] == names and not archive.comment, 'archive entry set/order/comment mismatch')
        verify_headers(target, infos, length)
        require(archive.read(JSON_NAME) == data, 'package JSON differs')
        crcs = {}
        for entry in policy['files']:
            count, crc = 0, 0
            digest = hashlib.sha256()
            with archive.open(entry['file']) as stream:
                while block := stream.read(CHUNK):
                    count += len(block)
                    require(count <= entry['bytes'], 'archive member too large')
                    digest.update(block)
                    crc = zlib.crc32(block, crc)
            require(count == entry['bytes'] and digest.hexdigest() == entry['sha256'], 'archive payload mismatch')
            require(crc == archive.getinfo(entry['file']).CRC, 'archive CRC mismatch')
            crcs[entry['file']] = f'{crc:08x}'
    target.seek(0)
    digest = hashlib.sha256()
    while block := target.read(CHUNK):
        digest.update(block)
    return {'bytes': length, 'sha256': digest.hexdigest(), 'crc32': crcs, 'entries': names,
            'profile': 'strict-ZIP32-STORED-no-DD-extra-comment-ZIP64'}


def package(source, output, policy, manifest, mode):
    output = Path(os.path.abspath(output))
    source = Path(os.path.abspath(source))
    require(not output.is_relative_to(source), 'output must be outside source directory')
    if manifest is not None:
        require(not Path(os.path.abspath(manifest)).is_relative_to(source), 'trusted manifest must be external to source')
        require(output != Path(os.path.abspath(manifest)), 'output cannot be trusted manifest')
    data = json_bytes(policy)
    with directory(source) as (source_fd, _), directory(output.parent) as (out_fd, _):
        source_names(source_fd, policy['files'])
        if mode == 'variant-v2':
            with regular(source_fd, 'llm_config.json') as config:
                variant_config(config.read(JSON_LIMIT + 1), policy)
        # Reject even dangling symlinks and do not spend a full read on an existing output.
        require(output.name not in os.listdir(out_fd), 'output exists; refusing clobber')
        for entry in policy['files']:
            with regular(source_fd, entry['file']) as original:
                stream_asset(original, entry)
        temporary = '.mnn-package-' + secrets.token_hex(16) + '.part'
        fd = os.open(temporary, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=out_fd)
        try:
            with os.fdopen(fd, 'w+b') as target:
                write_archive(target, source_fd, policy, data)
                source_names(source_fd, policy['files'])
                result = verify_archive(target, policy, data)
                os.fsync(target.fileno())
            # Hard-link publication is atomic and fails if ANY destination already exists.
            os.link(temporary, output.name, src_dir_fd=out_fd, dst_dir_fd=out_fd, follow_symlinks=False)
        finally:
            os.unlink(temporary, dir_fd=out_fd)
    result.update({'output': str(output), 'mode': mode, 'modelId': policy['modelId'], 'engine': 'mnn',
                   'files': policy['files'], 'tuple_sha256': fingerprint(policy['files']),
                   'compatibility': 'CURRENT-APP-TUPLE-MATCH' if mode == 'current-app' else 'CUSTOM-INTEGRITY-ONLY' if mode == 'variant-v2' else 'GENERIC-NOT-IMPORTABLE',
                   'model_numerical_validation': 'NOT RUN (packaging only)'})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True, help='directory containing exactly seven assets')
    parser.add_argument('--output', type=Path, required=True, help='new seekable ZIP outside source; parent must exist')
    parser.add_argument('--mode', choices=['current-app', 'generic', 'variant-v2'], default='current-app')
    parser.add_argument('--manifest', type=Path, help='EXTERNAL trusted policy JSON; required for generic mode')
    args = parser.parse_args()
    try:
        policy = select_policy(args.mode, args.manifest)
        result = package(args.source, args.output, policy, args.manifest, args.mode)
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, zipfile.LargeZipFile) as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
