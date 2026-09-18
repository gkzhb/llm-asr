#!/usr/bin/env python3
"""Offline config preparer tests; temporary outputs only, no SDK/model/network."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/prepare-model-configs.py'
spec = importlib.util.spec_from_file_location('prepare_configs', SCRIPT)
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class ConfigPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'repo'
        for name in (p.LOG, p.MANIFEST):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / name).read_bytes())
        self.out = self.base / 'output'

    def run_prepare(self, check=False):
        p.prepare(self.root, self.out, check)

    def test_clean_and_idempotent(self):
        self.run_prepare()
        before = {x.name: (x.read_bytes(), x.stat().st_mtime_ns, x.stat().st_ino)
                  for x in self.out.iterdir()}
        self.assertEqual(set(before), set(p.NAMES))
        self.assertEqual([len(before[n][0]) for n in p.NAMES], [617, 1104])
        self.run_prepare()
        self.run_prepare(check=True)
        self.assertEqual(before, {x.name: (x.read_bytes(), x.stat().st_mtime_ns, x.stat().st_ino)
                                  for x in self.out.iterdir()})

    def test_check_missing_creates_nothing(self):
        with self.assertRaises(FileNotFoundError):
            self.run_prepare(check=True)
        self.assertFalse(self.out.exists())

    def test_missing_partner_check_and_repair(self):
        self.run_prepare()
        (self.out / p.NAMES[1]).unlink()
        with self.assertRaises(ValueError):
            self.run_prepare(check=True)
        self.assertFalse((self.out / p.NAMES[1]).exists())
        self.run_prepare()
        self.assertTrue((self.out / p.NAMES[1]).is_file())

    def test_mismatch_preflight_no_first_file(self):
        self.out.mkdir()
        second = self.out / p.NAMES[1]
        second.write_bytes(b'keep me')
        with self.assertRaises(ValueError):
            self.run_prepare()
        self.assertEqual(second.read_bytes(), b'keep me')
        self.assertFalse((self.out / p.NAMES[0]).exists())

    def test_manifest_mismatch_no_output(self):
        mpath = self.root / p.MANIFEST
        m = json.loads(mpath.read_text())
        next(e for e in m['files'] if e['file'] == p.NAMES[1])['sha256'] = '0' * 64
        mpath.write_text(json.dumps(m))
        with self.assertRaises(ValueError):
            self.run_prepare()
        self.assertFalse(self.out.exists())

    def test_duplicate_manifest_entry(self):
        mpath = self.root / p.MANIFEST
        m = json.loads(mpath.read_text())
        m['files'].append(next(e for e in m['files'] if e['file'] == p.NAMES[0]))
        mpath.write_text(json.dumps(m))
        with self.assertRaises(ValueError):
            self.run_prepare()
        self.assertFalse(self.out.exists())

    def test_duplicate_record_and_json_key(self):
        log = self.root / p.LOG
        original = log.read_text()
        record = next(s for s in original.splitlines() if s.startswith(p.PREFIX))
        for content in [original + '\n' + record, p.PREFIX + '{"a":1,"a":2}']:
            with self.subTest(content=content[:40]):
                log.write_text(content)
                with self.assertRaises(ValueError):
                    self.run_prepare()
                self.assertFalse(self.out.exists())

    def test_leaf_symlink_live_and_dangling(self):
        self.out.mkdir()
        target = self.base / 'target'
        for live in (True, False):
            if live:
                target.write_bytes(b'untouched')
            elif target.exists():
                target.unlink()
            link = self.out / p.NAMES[1]
            link.symlink_to(target)
            with self.assertRaises(OSError):
                self.run_prepare()
            self.assertFalse((self.out / p.NAMES[0]).exists())
            self.assertTrue(link.is_symlink())
            if live:
                self.assertEqual(target.read_bytes(), b'untouched')
            else:
                self.assertFalse(target.exists())
            link.unlink()

    def test_parent_symlink_rejected(self):
        target = self.base / 'target'
        target.mkdir()
        self.out.symlink_to(target, target_is_directory=True)
        with self.assertRaises(OSError):
            self.run_prepare()
        self.assertEqual(list(target.iterdir()), [])

    def test_dotdot_paths_rejected_before_any_output(self):
        actual = self.base / 'actual'
        (actual / 'child').mkdir(parents=True)
        (self.base / 'link').symlink_to(actual / 'child', target_is_directory=True)
        (self.base / 'plain').mkdir()
        (self.base / 'not-directory').write_bytes(b'keep')
        # Each prefix used to disappear in abspath, hiding links, missing paths
        # and even a non-directory. The policy deliberately rejects all '..'.
        for prefix in ('link', 'plain', 'missing', 'not-directory', 'new/deep'):
            for absolute in (False, True):
                for check in (False, True):
                    with self.subTest(prefix=prefix, absolute=absolute, check=check):
                        output = Path(prefix) / '..' / 'configs'
                        if absolute:
                            output = self.base / output
                        command = [sys.executable, '-O', str(SCRIPT), '--output', str(output)]
                        if check:
                            command.append('--check')
                        result = subprocess.run(command, cwd=self.base, capture_output=True,
                                                text=True, timeout=5)
                        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                        self.assertIn('Parent traversal', result.stderr)
                        self.assertNotIn('Traceback', result.stderr)
                        self.assertFalse((self.base / 'configs').exists())
                        self.assertFalse((actual / 'configs').exists())
                        self.assertFalse((self.base / 'new').exists())
        self.assertTrue((self.base / 'link').is_symlink())
        self.assertEqual((self.base / 'not-directory').read_bytes(), b'keep')

    def test_dotdot_check_cannot_accept_normalized_existing_output(self):
        self.run_prepare()
        command = [sys.executable, str(SCRIPT), '--check', '--output',
                   str(self.base / 'missing' / '..' / self.out.name)]
        before = {x.name: (x.read_bytes(), x.stat().st_mtime_ns, x.stat().st_ino)
                  for x in self.out.iterdir()}
        result = subprocess.run(command, cwd=self.base, capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn('Parent traversal', result.stderr)
        self.assertEqual(before, {x.name: (x.read_bytes(), x.stat().st_mtime_ns, x.stat().st_ino)
                                  for x in self.out.iterdir()})

    def test_relative_cli_output_and_check(self):
        command = [sys.executable, str(SCRIPT), '--output', './nested/configs']
        for check in (False, True):
            result = subprocess.run(command + (['--check'] if check else []), cwd=self.base,
                                    capture_output=True, text=True, timeout=5)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual({x.name: x.read_bytes() for x in (self.base / 'nested/configs').iterdir()},
                         p.expected_configs(self.root))

    def test_directory_and_fifo_rejected_without_blocking(self):
        self.out.mkdir()
        leaf = self.out / p.NAMES[1]
        leaf.mkdir()
        with self.assertRaises((OSError, ValueError)):
            self.run_prepare()
        leaf.rmdir()
        os.mkfifo(leaf)
        with self.assertRaises(ValueError):
            self.run_prepare()
        self.assertFalse((self.out / p.NAMES[0]).exists())

    def test_concurrent_destination_never_replaced(self):
        real_link = os.link
        def race(src, dst, **kwargs):
            # Competitor publishes after preflight, before this process's link.
            (self.out / dst).write_bytes(b'competitor')
            return real_link(src, dst, **kwargs)
        with mock.patch.object(p.os, 'link', side_effect=race):
            with self.assertRaises(ValueError):
                self.run_prepare()
        self.assertEqual((self.out / p.NAMES[0]).read_bytes(), b'competitor')
        self.assertEqual([x.name for x in self.out.iterdir()], [p.NAMES[0]])

    def test_failed_publish_cleans_temp(self):
        with mock.patch.object(p.os, 'link', side_effect=OSError('injected IO failure')):
            with self.assertRaises(OSError):
                self.run_prepare()
        self.assertEqual(list(self.out.iterdir()), [])

    def test_parallel_cli_and_optimized_python(self):
        command = [sys.executable, '-O', str(SCRIPT), '--output', str(self.out)]
        children = [subprocess.Popen(command, cwd=self.base, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE) for _ in range(3)]
        for child in children:
            stdout, stderr = child.communicate(timeout=15)
            self.assertEqual(child.returncode, 0, (stdout, stderr))
        self.assertEqual(set(x.name for x in self.out.iterdir()), set(p.NAMES))
        (self.out / p.NAMES[1]).write_bytes(b'wrong')
        r = subprocess.run(command, cwd=self.base, capture_output=True, timeout=15)
        self.assertEqual(r.returncode, 1)
        self.assertIn(b'ERROR:', r.stderr)
        self.assertEqual((self.out / p.NAMES[1]).read_bytes(), b'wrong')


if __name__ == '__main__':
    unittest.main()
