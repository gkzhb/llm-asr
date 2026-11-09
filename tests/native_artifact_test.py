#!/usr/bin/env python3
"""Production native gates: actual ELF mutations and explicit-object boundary negatives."""
import argparse
import importlib.util
import os
from pathlib import Path
import struct
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

v = load('native_verify', 'verify-native-artifact.py')
l = load('native_link', 'link-native-artifact.py')

class NativeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_native_provenance_inputs_exist_and_include_build_definition(self):
        inputs = v.input_hashes()
        self.assertIn('native/source-lock.json', inputs)
        self.assertIn('native/cmake/finalize-mnn.cmake', inputs)
        self.assertIn('scripts/verify-native-artifact.py', inputs)
        self.assertIn('native/apk/model_graph_policy.h', inputs)
        self.assertTrue(all(len(value) == 64 for value in inputs.values()))

    def test_real_elf_and_alignment_stack_relro_mutants(self):
        baseline = __import__('json').loads((ROOT / 'gradle/baseline-inputs.json').read_text())
        data = Path(os.environ.get('NATIVE_TEST_ARTIFACT', ROOT / baseline['native']['path'])).read_bytes()
        file = self.root / 'native.so'
        file.write_bytes(data)
        self.assertGreaterEqual(len(v.elf_check(file)), 2)
        offset = struct.unpack_from('<Q', data, 32)[0]
        size, count = struct.unpack_from('<HH', data, 54)
        cases = {}
        for i in range(count):
            p = offset + size * i
            kind = struct.unpack_from('<I', data, p)[0]
            mutant = bytearray(data)
            if kind == 1 and 'LOAD' not in cases:
                struct.pack_into('<Q', mutant, p + 48, 4096)
                cases['LOAD'] = mutant
            elif kind == 0x6474e551:
                struct.pack_into('<I', mutant, p + 4, 7)
                cases['stack'] = mutant
            elif kind == 0x6474e552:
                struct.pack_into('<I', mutant, p, 0)
                cases['RELRO'] = mutant
        self.assertEqual(len(cases), 3)
        for name, mutant in cases.items():
            with self.subTest(name=name):
                file.write_bytes(mutant)
                with self.assertRaises(ValueError):
                    v.elf_check(file)

    def args(self, objects):
        inventory = self.root / 'objects.txt'
        inventory.write_text('\n'.join(str(p) for p in objects) + '\n')
        return argparse.Namespace(objects=inventory, output=self.root / 'out/native.so',
                                  header=self.root / 'missing.h', source=self.root / 'jni.cpp',
                                  mnn_source=self.root / 'src', compiler=self.root / 'compiler')

    def test_count_and_duplicate_fail_before_compiler(self):
        for items in [[], [self.root / 'file'] * 578]:
            with self.assertRaisesRegex(ValueError, '578 unique'):
                l.link(self.args(items))
        self.assertFalse((self.root / 'out').exists())

    def test_outside_or_symlink_object_fails_before_compiler(self):
        items = []
        for i in range(578):
            path = self.root / ('object%d.o' % i)
            path.write_bytes(b'fixture')
            items.append(path)
        outside = self.root.parent / (self.root.name + '-outside.o')
        outside.write_bytes(b'fixture')
        self.addCleanup(outside.unlink)
        with self.assertRaisesRegex(ValueError, 'outside current build'):
            l.link(self.args([outside] + items[1:]))
        items[0].unlink()
        items[0].symlink_to(outside)
        with self.assertRaisesRegex(ValueError, 'Symlink'):
            l.link(self.args(items))
        self.assertFalse((self.root / 'out').exists())

if __name__ == '__main__':
    unittest.main()
