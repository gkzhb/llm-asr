#!/usr/bin/env python3
"""Offline native source preparation from pinned archives; never use old objects."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ARCHIVES = {
    'mnn': ('mnn.tar.gz', '4bfeaff68193a58f097d1d549a9b31f5ef588ba796c9768990e0afd0b439c59d'),
    'kleidiai': ('kleidiai-v1.16.0.tar.gz', '57b0a95c559ec6a4b1460bc742af600bfb6cdff8dc39154b376c25347d7b0891'),
}
PATCHES = ['mnn-p0.patch', 'mnn-whisper-periodic-hann.patch', 'mnn-asr-boundary-positions.patch',
           'mnn-asr-request-cleanup.patch']


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def safe_path(path):
    if '..' in path.parts:
        raise ValueError('Parent traversal forbidden')
    for item in [path, *path.parents]:
        if item.is_symlink():
            raise ValueError('Symlink path forbidden: ' + str(item))


def inventory(directory):
    safe_path(directory)
    if not directory.is_dir():
        raise ValueError('Prepared source directory missing')
    result = {}
    for path in sorted(directory.rglob('*')):
        if path.is_symlink():
            raise ValueError('Source symlink forbidden')
        if path.is_file():
            result[str(path.relative_to(directory))] = sha(path)
        elif not path.is_dir():
            raise ValueError('Source special file forbidden')
    return result


def tree_sha(files):
    return hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def locked_trees(trees):
    lock = json.loads((ROOT / 'native/source-lock.json').read_text())
    if {name: tree_sha(files) for name, files in trees.items()} != lock['tree_sha256']:
        raise ValueError('Prepared tree differs from committed source lock')


def extract(archive, output):
    """Only regular files/directories; irrelevant upstream symlinks omitted."""
    with tarfile.open(archive) as tar:
        seen = set()
        for member in tar:
            parts = PurePosixPath(member.name).parts
            if not parts or member.name.startswith('/') or '..' in parts:
                raise ValueError('Unsafe archive path')
            relative = PurePosixPath(*parts[1:])
            # Respect upstream restrictions; these features are disabled.
            if str(relative).startswith(('schema/private/', 'source/internal/')):
                continue
            if not relative.parts or member.isdir():
                continue
            if member.issym():
                continue
            if not member.isfile() or str(relative) in seen:
                raise ValueError('Unsupported/duplicate archive member')
            seen.add(str(relative))
            destination = output.joinpath(*relative.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            with tar.extractfile(member) as source, destination.open('xb') as target:
                while block := source.read(1024 * 1024):
                    target.write(block)


def read_source_lock():
    return json.loads((ROOT / 'native/source-lock.json').read_text())


def validate_patch_lock():
    expected = {p: sha(ROOT / 'patches' / p) for p in PATCHES}
    if read_source_lock()['patches'] != expected:
        raise ValueError('Patch lock mismatch')
    return expected


def validate(output):
    safe_path(output)
    safe_path(output / 'sources.json')
    report = json.loads((output / 'sources.json').read_text())
    if report.get('schema') != 1:
        raise ValueError('Source report schema mismatch')
    locked_trees(report['trees'])
    expected = {name: value[1] for name, value in ARCHIVES.items()}
    if report['archives'] != expected:
        raise ValueError('Archive lock mismatch')
    expected_patches = validate_patch_lock()
    if report['patches'] != expected_patches:
        raise ValueError('Patch lock mismatch')
    for name in ARCHIVES:
        if inventory(output / name) != report['trees'][name]:
            raise ValueError('Prepared source drift: ' + name)
    return report


def prepare(cache, output):
    safe_path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    lock = output.parent / ('.' + output.name + '.lock')
    safe_path(lock)
    with lock.open('a') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX)
        return prepare_locked(cache, output)


def prepare_locked(cache, output):
    safe_path(cache)
    safe_path(output)
    if output.exists():
        return validate(output)
    validate_patch_lock()
    for name, (filename, expected) in ARCHIVES.items():
        path = cache / filename
        safe_path(path)
        if not path.is_file() or sha(path) != expected:
            raise ValueError('Pinned archive missing/hash mismatch: ' + name)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.native-sources-', dir=output.parent) as temporary:
        stage = Path(temporary) / 'prepared'
        stage.mkdir()
        for name, (filename, _) in ARCHIVES.items():
            (stage / name).mkdir()
            extract(cache / filename, stage / name)
        for patch in PATCHES:
            subprocess.run(['patch', '--batch', '--fuzz=0', '-p1', '-i',
                            str(ROOT / 'patches' / patch)], cwd=stage / 'mnn', check=True)
        report = {'schema': 1, 'archives': {k: v[1] for k, v in ARCHIVES.items()},
                  'patches': {p: sha(ROOT / 'patches' / p) for p in PATCHES},
                  'trees': {name: inventory(stage / name) for name in ARCHIVES}}
        locked_trees(report['trees'])
        (stage / 'sources.json').write_text(json.dumps(report, indent=2) + '\n')
        # Atomic publication into a nonexistent destination; never replace a tree.
        if output.exists():
            raise ValueError('Concurrent source destination appeared')
        os.rename(stage, output)
    return validate(output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / '.work/sources')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.check:
        validate(args.output)
    else:
        prepare(args.cache, args.output)
    print('PASS pinned offline native sources/patches/tree inventory')
