#!/usr/bin/env python3
"""Fail-closed source preparation tests against the production module."""
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('native_sources', ROOT / 'scripts/prepare-native-sources.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class SourcesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        # Small archive fixtures use their own lock; real committed lock is tested separately.
        self.lock_check = patch.object(m, 'locked_trees')
        self.lock_check.start()
        self.addCleanup(self.lock_check.stop)

    def archive(self, members):
        path = self.root / 'source.tar.gz'
        with tarfile.open(path, 'w:gz') as tar:
            for name, contents, kind in members:
                info = tarfile.TarInfo(name)
                if kind == 'link':
                    info.type = tarfile.SYMTYPE
                    info.linkname = '/outside'
                    tar.addfile(info)
                elif kind == 'fifo':
                    info.type = tarfile.FIFOTYPE
                    tar.addfile(info)
                else:
                    info.size = len(contents)
                    tar.addfile(info, io.BytesIO(contents))
        return path

    def test_extract_regular_without_upstream_links_or_private(self):
        archive = self.archive([('repo/include/file', b'public', 'file'),
                                ('repo/link', b'', 'link'),
                                ('repo/source/internal/private', b'restricted', 'file')])
        output = self.root / 'out'
        m.extract(archive, output)
        self.assertEqual((output / 'include/file').read_bytes(), b'public')
        self.assertFalse((output / 'link').exists())
        self.assertFalse((output / 'source/internal').exists())

    def test_archive_parent_traversal(self):
        archive = self.archive([('repo/../../escape', b'bad', 'file')])
        with self.assertRaisesRegex(ValueError, 'Unsafe archive'):
            m.extract(archive, self.root / 'out')
        self.assertFalse((self.root / 'escape').exists())

    def test_duplicate_and_special_members(self):
        for members in [[('repo/file', b'x', 'file'), ('repo/file', b'y', 'file')],
                        [('repo/fifo', b'', 'fifo')]]:
            with self.subTest(members=members):
                archive = self.archive(members)
                with self.assertRaisesRegex(ValueError, 'Unsupported/duplicate'):
                    m.extract(archive, self.root / ('out' + str(len(members))))

    def test_bad_archive_hash_before_output(self):
        archive = self.archive([('repo/file', b'x', 'file')])
        with patch.dict(m.ARCHIVES, {'mnn': (archive.name, '0' * 64)}, clear=True):
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                m.prepare(self.root, self.root / 'out')
        self.assertFalse((self.root / 'out').exists())

    def test_real_prepare_recheck_and_drift(self):
        archive = self.archive([('repo/file', b'x', 'file')])
        with patch.dict(m.ARCHIVES, {'mnn': (archive.name, m.sha(archive))}, clear=True), \
             patch.object(m, 'PATCHES', []):
            output = self.root / 'out'
            m.prepare(self.root, output)
            before = (output / 'mnn/file').stat().st_mtime_ns
            m.prepare(self.root, output)
            self.assertEqual((output / 'mnn/file').stat().st_mtime_ns, before)
            (output / 'mnn/file').write_bytes(b'drift')
            with self.assertRaisesRegex(ValueError, 'source drift'):
                m.prepare(self.root, output)
            self.assertEqual((output / 'mnn/file').read_bytes(), b'drift')

    def test_source_symlink_and_parent_hop(self):
        target = self.root / 'target'
        target.mkdir()
        (self.root / 'link').symlink_to(target)
        for path in [self.root / 'link/out', self.root / 'target/../out']:
            with self.subTest(path=path):
                with self.assertRaises(ValueError):
                    m.prepare(self.root, path)
        self.assertEqual(list(target.iterdir()), [])

    def test_actual_committed_source_lock(self):
        self.lock_check.stop()
        trees = {name: {} for name in m.ARCHIVES}
        with self.assertRaisesRegex(ValueError, 'committed source lock'):
            m.locked_trees(trees)

    def test_modified_report_cannot_bypass_committed_tree_lock(self):
        archive = self.archive([('repo/file', b'x', 'file')])
        with patch.dict(m.ARCHIVES, {'mnn': (archive.name, m.sha(archive))}, clear=True), \
             patch.object(m, 'PATCHES', []):
            output = self.root / 'out'
            m.prepare(self.root, output)
            (output / 'mnn/file').write_bytes(b'changed')
            report = json.loads((output / 'sources.json').read_text())
            report['trees']['mnn'] = m.inventory(output / 'mnn')
            (output / 'sources.json').write_text(json.dumps(report))
            self.lock_check.stop()
            with self.assertRaisesRegex(ValueError, 'committed source lock'):
                m.validate(output)

    def test_added_source_or_symlink_rejected(self):
        archive = self.archive([('repo/file', b'x', 'file')])
        with patch.dict(m.ARCHIVES, {'mnn': (archive.name, m.sha(archive))}, clear=True), \
             patch.object(m, 'PATCHES', []):
            output = self.root / 'out'
            m.prepare(self.root, output)
            extra = output / 'mnn/extra'
            extra.write_bytes(b'extra')
            with self.assertRaisesRegex(ValueError, 'source drift'):
                m.validate(output)
            extra.unlink()
            extra.symlink_to(archive)
            with self.assertRaisesRegex(ValueError, 'symlink'):
                m.validate(output)


if __name__ == '__main__':
    unittest.main()
