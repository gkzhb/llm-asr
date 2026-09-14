#!/usr/bin/env python3
"""Download only pinned model files with upstream sizes and SHA256 verification."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', default='model-tools/model-lock.json')
    parser.add_argument('--output', default='models/raw/Qwen3-ASR-0.6B')
    args = parser.parse_args()
    manifest = json.loads(Path(args.manifest).read_text())
    root = Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    for item in manifest['files']:
        name = item['path']
        if Path(name).name != name or name in ('.', '..'):
            raise ValueError('Manifest filename must be a basename')
        path = root / name
        if path.exists() and path.stat().st_size == item['bytes'] and digest(path) == item['sha256']:
            print('verified cached:', name, flush=True)
            continue
        partial = path.with_name(path.name + '.part')
        url = f"https://modelscope.cn/models/{manifest['model']}/resolve/{manifest['revision']}/{name}"
        print('downloading:', name, item['bytes'], flush=True)
        subprocess.run(['curl', '--fail', '--location', '--show-error', '--silent',
                        '--connect-timeout', '20', '--max-time', '1800',
                        '--retry', '2', '--retry-delay', '3', '--continue-at', '-',
                        '--output', str(partial), url], check=True)
        if partial.stat().st_size != item['bytes'] or digest(partial) != item['sha256']:
            raise RuntimeError(f'Size/hash mismatch: {name}; retained .part for diagnosis')
        partial.replace(path)
        print('verified:', name, flush=True)


if __name__ == '__main__':
    main()
