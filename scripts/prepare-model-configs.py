#!/usr/bin/env python3
"""Restore the two pinned MNN configs offline; never export models or replace files.

Requires POSIX directory fds, O_NOFOLLOW and hard links (Linux/Nix build host).
The repository inputs are trusted; manifest hashes are integrity checks, not an
independent signature. Neither the log nor the manifest is modified.
"""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import secrets
import stat
import sys

ROOT = Path(__file__).resolve().parents[1]
NAMES = ('config.json', 'llm_config.json')
LOG = Path('reports/p0/device-inference-final-patches.txt')
MANIFEST = Path('reports/p0/mnn-model-manifest.json')
PREFIX = 'P0_EFFECTIVE_CONFIG '


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key: ' + key)
        result[key] = value
    return result


def load_json(text):
    return json.loads(text, object_pairs_hook=unique_object)


def expected_configs(root):
    """Validate BOTH payloads before creating any output directory or file."""
    lines = [s[len(PREFIX):] for s in (root / LOG).read_text().splitlines()
             if s.startswith(PREFIX)]
    if len(lines) != 1:
        raise ValueError('Expected exactly one P0_EFFECTIVE_CONFIG record')
    config = load_json(lines[0])
    items = list(config.items())
    split = list(config).index('model_type')
    payloads = {
        NAMES[0]: (json.dumps(dict(items[:split]), ensure_ascii=False, indent=2) + '\n').encode('utf-8'),
        NAMES[1]: json.dumps(dict(items[split:]), ensure_ascii=False, indent=4).encode('utf-8'),
    }
    entries = load_json((root / MANIFEST).read_text())['files']
    for name, data in payloads.items():
        matches = [e for e in entries if e['file'] == name]
        if len(matches) != 1:
            raise ValueError('Expected one manifest entry for ' + name)
        entry = matches[0]
        if len(data) != entry['bytes'] or hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise ValueError('Pinned size/SHA-256 mismatch: ' + name)
    return payloads


@contextmanager
def output_directory(path, create):
    """Open each component without following links; pin output via directory fd."""
    # abspath would erase link/.. before O_NOFOLLOW could inspect link.
    # Reject traversal lexically, before opening or creating any directory.
    if '..' in Path(path).parts:
        raise ValueError('Parent traversal (..) is not allowed in output paths')
    path = Path(os.path.abspath(path))
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    fd = os.open('/', flags)
    try:
        for part in path.parts[1:]:
            if create:
                try:
                    os.mkdir(part, mode=0o755, dir_fd=fd)
                except FileExistsError:
                    pass
            child = os.open(part, flags, dir_fd=fd)
            os.close(fd)
            fd = child
        yield fd
    finally:
        os.close(fd)


def existing_matches(directory, name, data):
    try:
        fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
    except FileNotFoundError:
        return False
    with os.fdopen(fd, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size != len(data):
            raise ValueError('Refusing non-regular or different existing file: ' + name)
        if stream.read(len(data) + 1) != data:
            raise ValueError('Refusing different existing file: ' + name)
    return True


def publish(directory, name, data):
    """Complete temp file -> exclusive hard-link publication, never os.replace."""
    temp = '.prepare-model-configs-' + secrets.token_hex(16) + '.tmp'
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                 0o600, dir_fd=directory)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        try:
            os.link(temp, name, src_dir_fd=directory, dst_dir_fd=directory,
                    follow_symlinks=False)
        except FileExistsError:
            # Another preparer may have won. Accept only identical complete data.
            if not existing_matches(directory, name, data):
                raise ValueError('Concurrent target disappeared: ' + name)
    finally:
        os.unlink(temp, dir_fd=directory)


def prepare(root, output, check_only=False):
    payloads = expected_configs(root)
    with output_directory(output, create=not check_only) as directory:
        # Preflight BOTH files before publishing either. No force/overwrite mode.
        present = {name: existing_matches(directory, name, data)
                   for name, data in payloads.items()}
        if check_only and not all(present.values()):
            raise ValueError('Missing config(s): ' + ', '.join(n for n in NAMES if not present[n]))
        for name, data in payloads.items():
            if not present[name]:
                publish(directory, name, data)
        for name, data in payloads.items():
            if not existing_matches(directory, name, data):
                raise ValueError('Config disappeared during final verification: ' + name)
            print('VERIFIED', name, len(data), hashlib.sha256(data).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'models/mnn-16',
                        help='Output directory (default: repository models/mnn-16; relative paths use cwd; no .. components)')
    parser.add_argument('--check', action='store_true', help='Verify only; create nothing')
    args = parser.parse_args()
    try:
        prepare(ROOT, args.output, args.check)
    except (OSError, ValueError, KeyError, TypeError) as error:
        print('ERROR:', error, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
