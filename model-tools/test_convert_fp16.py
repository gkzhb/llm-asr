#!/usr/bin/env python3
"""Tiny current-audio gate contract tests; no weights, torch or conversion run."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('convert_fp16', Path(__file__).with_name('convert_fp16.py'))
EXPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EXPORT)


class AudioGateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='fp16-gate-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        assets = []
        for name in ('audio.mnn', 'audio.mnn.weight'):
            data = name.encode()
            (self.root / name).write_bytes(data)
            assets.append({'file': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
        self.gate = {'results': [dict(frames=n, finite=True, passed=True, max_abs=0.001, relative_l2=0.01)
                                 for n in (20, 99, 100, 101, 800, 801)], 'artifacts': assets}
        self.path = self.root / 'gate.json'

    def check(self, gate):
        self.path.write_text(json.dumps(gate))
        EXPORT.validate_gate(self.path, self.root)

    def test_current_bound_gate(self):
        self.check(self.gate)

    def test_incomplete_failed_or_loosened_gate(self):
        mutations = [lambda g: g['results'].pop(),
                     lambda g: g['results'][0].update(passed=False),
                     lambda g: g['results'][0].update(finite=False),
                     lambda g: g['results'][0].update(max_abs=0.003),
                     lambda g: g['results'][0].update(relative_l2=0.03),
                     lambda g: g['results'][0].update(frames=100)]
        for mutate in mutations:
            gate = copy.deepcopy(self.gate)
            mutate(gate)
            with self.subTest(gate=gate), self.assertRaises(ValueError):
                self.check(gate)

    def test_unbound_or_different_encoder(self):
        mutations = [lambda g: g.pop('artifacts'), lambda g: g['artifacts'].pop(),
                     lambda g: g['artifacts'][0].update(sha256='0' * 64),
                     lambda g: g['artifacts'][0].update(bytes=999),
                     lambda g: g['artifacts'][0].update(file='other.mnn')]
        for mutate in mutations:
            gate = copy.deepcopy(self.gate)
            mutate(gate)
            with self.subTest(gate=gate), self.assertRaises(ValueError):
                self.check(gate)
        (self.root / 'audio.mnn').write_bytes(b'changed')
        with self.assertRaises(ValueError):
            self.check(self.gate)


if __name__ == '__main__':
    unittest.main(verbosity=2)
