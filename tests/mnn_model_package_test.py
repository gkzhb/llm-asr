#!/usr/bin/env python3
"""Real CLI, tiny synthetic assets only; no model loading or downloads."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from concurrent.futures import ThreadPoolExecutor

ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / 'scripts/package-mnn-model.py'
SPEC = importlib.util.spec_from_file_location('package_mnn', CLI)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='mnn-package-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.entries = []
        for index in range(7):
            name = f'asset{index}.bin'
            # Include more than one chunk without anything approaching real weights.
            data = bytes([index + 1]) * (131077 if index == 0 else index + 3)
            (self.source / name).write_bytes(data)
            self.entries.append({'file': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
        self.policy = {'schema': 1, 'modelId': 'GENERIC-NOT-IMPORTABLE-test', 'engine': 'mnn', 'files': self.entries}
        self.manifest = self.root / 'trusted-policy.json'
        self.save_policy(self.policy)
        self.output = self.root / 'package.zip'

    def save_policy(self, policy):
        self.manifest.write_text(json.dumps(policy))

    def command(self, *extra):
        return [sys.executable, '-B', str(CLI), '--source', str(self.source), '--output', str(self.output),
                '--mode', 'generic', '--manifest', str(self.manifest), *extra]

    def run_cli(self, *extra, good=True):
        result = subprocess.run(self.command(*extra), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
        if good:
            self.assertEqual(result.returncode, 0, result.stderr.decode())
            return json.loads(result.stdout)
        self.assertEqual(result.returncode, 1, result.stderr.decode())
        self.assertIn(b'ERROR:', result.stderr)
        self.assertNotIn(b'Traceback', result.stderr)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.root.glob('.mnn-package-*.part')), [])
        return result.stderr.decode()

    def test_deterministic_roundtrip(self):
        receipt = self.run_cli()
        raw = self.output.read_bytes()
        self.assertEqual(receipt['sha256'], hashlib.sha256(raw).hexdigest())
        self.assertEqual(receipt['bytes'], len(raw))
        self.assertEqual(receipt['compatibility'], 'GENERIC-NOT-IMPORTABLE')
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(archive.namelist(), ['model-package.json'] + [e['file'] for e in self.entries])
            self.assertIsNone(archive.testzip())
            self.assertEqual(json.loads(archive.read('model-package.json')), self.policy)
            for info in archive.infolist():
                self.assertEqual(info.extra, b'')
                self.assertEqual(info.comment, b'')
                self.assertEqual(info.flag_bits, 0)
                self.assertEqual(info.create_system, 3)
                self.assertEqual(info.external_attr, 0o100600 << 16)
                self.assertEqual(info.date_time, (2024, 1, 1, 0, 0, 0))
                self.assertEqual(info.compress_type, zipfile.ZIP_STORED)
        # Metadata and input JSON key ordering do not affect byte identity.
        self.policy = dict(reversed(list(self.policy.items())))
        self.policy['files'] = [dict(reversed(list(e.items()))) for e in self.entries]
        self.save_policy(self.policy)
        self.output = self.root / 'second.zip'
        os.chmod(self.source / 'asset0.bin', 0o400)
        os.utime(self.source / 'asset0.bin', (1, 1))
        self.run_cli()
        self.assertEqual(self.output.read_bytes(), raw)

    def test_no_clobber(self):
        self.output.write_bytes(b'USER OUTPUT')
        result = subprocess.run(self.command(), capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 1)
        self.assertIn(b'refusing clobber', result.stderr)
        self.assertEqual(self.output.read_bytes(), b'USER OUTPUT')

    def test_concurrent_publication(self):
        with ThreadPoolExecutor(2) as pool:
            results = list(pool.map(lambda _: subprocess.run(self.command(), capture_output=True, timeout=20), range(2)))
        self.assertEqual(sorted(r.returncode for r in results), [0, 1])
        winner = next(json.loads(r.stdout) for r in results if r.returncode == 0)
        self.assertEqual(winner['sha256'], hashlib.sha256(self.output.read_bytes()).hexdigest())
        self.assertEqual(list(self.root.glob('.mnn-package-*.part')), [])

    def test_source_inventory(self):
        for kind in ['extra', 'missing', 'directory', 'fifo', 'symlink']:
            with self.subTest(kind=kind):
                path = self.source / 'asset6.bin'
                original = path.read_bytes()
                if kind == 'extra':
                    (self.source / 'extra').write_bytes(b'x')
                else:
                    path.unlink()
                    if kind == 'directory': path.mkdir()
                    if kind == 'fifo': os.mkfifo(path)
                    if kind == 'symlink': path.symlink_to(self.source / 'asset5.bin')
                self.run_cli(good=False)
                if kind == 'extra': (self.source / 'extra').unlink()
                elif kind == 'directory': path.rmdir()
                elif kind != 'missing': path.unlink()
                if kind != 'extra': path.write_bytes(original)

    def test_symlink_components_and_output(self):
        original = self.source
        alias = self.root / 'alias'
        alias.symlink_to(original, target_is_directory=True)
        self.source = alias
        self.run_cli(good=False)
        self.source = original
        self.output = alias / 'out.zip'
        self.run_cli(good=False)
        self.output = self.root / 'dangling.zip'
        self.output.symlink_to(self.root / 'absent')
        result = subprocess.run(self.command(), capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 1)
        self.assertTrue(self.output.is_symlink())
        self.assertFalse((self.root / 'absent').exists())

    def test_external_manifest_and_output_placement(self):
        self.output = self.source / 'out.zip'
        self.assertIn('outside', self.run_cli(good=False))
        self.output = self.root / 'package.zip'
        self.manifest = self.source / 'policy.json'
        self.save_policy(self.policy)
        self.assertIn('external', self.run_cli(good=False))
        self.manifest.unlink()
        self.manifest = self.root / 'symlink-policy'
        real = self.root / 'policy-real'
        real.write_text(json.dumps(self.policy))
        self.manifest.symlink_to(real)
        self.run_cli(good=False)

    def test_wrong_payload_size_and_sha(self):
        path = self.source / 'asset6.bin'
        original = path.read_bytes()
        for data in [b'', original + b'x', b'X' * len(original)]:
            path.write_bytes(data)
            self.run_cli(good=False)
        path.write_bytes(original)

    def test_policy_rejections(self):
        mutations = [
            lambda p: p.update(schema=True), lambda p: p.update(schema=1.0),
            lambda p: p.update(engine='other'), lambda p: p.update(modelId='qwen3-asr-0.6b-mnn-fp16-p0-1'),
            lambda p: p.update(unknown=1), lambda p: p['files'].pop(),
            lambda p: p['files'].append(p['files'][0]),
            lambda p: p['files'][0].update(bytes=True), lambda p: p['files'][0].update(bytes=-1),
            lambda p: p['files'][0].update(bytes=0), lambda p: p['files'][0].update(bytes=2147483647),
            lambda p: p['files'][0].update(sha256='A' * 64),
            lambda p: p['files'][0].update(unknown=1),
            lambda p: p['files'][0].update(file='asset1.bin'),
            lambda p: p['files'][0].update(file='asset1.bin.part'),
        ]
        for mutate in mutations:
            policy = copy.deepcopy(self.policy)
            mutate(policy)
            self.save_policy(policy)
            self.run_cli(good=False)
        for name in ['../asset0.bin', '/absolute', 'C:foo', 'a/b', 'a\\b', '..', '.', 'a\x00b', 'é', 'model-package.json', 'x' * 129]:
            policy = copy.deepcopy(self.policy)
            policy['files'][0]['file'] = name
            self.save_policy(policy)
            self.run_cli(good=False)

    def test_json_rejections(self):
        text = json.dumps(self.policy)
        for bad in ['{"schema":1,' + text[1:], text + 'x', '\ufeff' + text, ' ' * 16385,
                    text.replace('"bytes": 131077', '"bytes": 1e5'), text.replace('"schema": 1', '"schema": NaN')]:
            self.manifest.write_text(bad)
            self.run_cli(good=False)

    def test_current_app_trust_is_not_self_reported(self):
        # Generic synthetic bytes cannot be made importable by self-reporting SHA.
        error = self.run_cli('--mode', 'current-app', good=False)
        self.assertIn('differs from current APK trust', error)
        result = subprocess.run([sys.executable, '-B', str(CLI), '--source', str(self.source),
                                 '--output', str(self.output)], capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 1)
        self.assertIn(b'exactly seven', result.stderr)
        self.assertEqual(MODULE.fingerprint(MODULE.current_policy()['files']), MODULE.FINGERPRINT)

    def test_streaming_read_bound(self):
        class BoundedReader:
            def __init__(self, original): self.original = original
            def fileno(self): return self.original.fileno()
            def read(self, size):
                self_test.assertLessEqual(size, 65536)
                self_test.assertGreater(size, 0)
                return self.original.read(size)
        self_test = self
        with (self.source / 'asset0.bin').open('rb') as original:
            MODULE.stream_asset(BoundedReader(original), self.entries[0])

    def test_final_verification_rejects_corruption(self):
        self.run_cli()
        with self.output.open('r+b') as stream:
            stream.seek(0, 2)
            stream.write(b'x')
            with self.assertRaises(ValueError):
                MODULE.verify_archive(stream, self.policy, MODULE.json_bytes(self.policy))

    def test_failure_cleanup_and_no_publication(self):
        for point in ['write_archive', 'verify_archive']:
            with self.subTest(point=point), patch.object(MODULE, point, side_effect=OSError('injected IO failure')):
                with self.assertRaisesRegex(OSError, 'injected IO failure'):
                    MODULE.package(self.source, self.output, self.policy, self.manifest, 'generic')
                self.assertFalse(self.output.exists())
                self.assertEqual(list(self.root.glob('.mnn-package-*.part')), [])

    def test_final_payload_crc_failure(self):
        self.run_cli()
        with zipfile.ZipFile(self.output) as archive:
            info = archive.getinfo('asset0.bin')
            at = info.header_offset + 30 + len(info.filename)
        with self.output.open('r+b') as stream:
            stream.seek(at)
            stream.write(b'X')
            with self.assertRaises(zipfile.BadZipFile):
                MODULE.verify_archive(stream, self.policy, MODULE.json_bytes(self.policy))

    def test_generic_manifest_required(self):
        result = subprocess.run([sys.executable, '-B', str(CLI), '--source', str(self.source), '--output',
                                 str(self.output), '--mode', 'generic'], capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 1)
        self.assertIn(b'explicit external', result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
