#!/usr/bin/env python3
"""Actual shared collectors on temporary trees: additions, mutation, omissions, format."""
import importlib.util
import json
from pathlib import Path
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result)
    return result


c = load('test_input_checker', 'scripts/verify-android-test-apk.py')
p = load('product_collector', 'scripts/verify-gradle-apk.py')
f = load('format_collector', 'scripts/check-maintained-text.py')


def reject(call, diagnostic):
    try:
        call()
    except ValueError as error:
        c.require(diagnostic in str(error), 'wrong input rejection: ' + str(error))
    else:
        raise ValueError('input negative accepted: ' + diagnostic)


with tempfile.TemporaryDirectory(prefix='android-inputs-') as temp:
    root = Path(temp)
    for relative in f.INVENTORY:
        dest = root / relative; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_text('clean\n')
    # Real product collector's old inventory, no model/audio data copied.
    baseline = json.loads((ROOT / 'gradle/baseline-inputs.json').read_text())
    needed = set(baseline['files']) | set(p.collect_build_inputs(ROOT))
    for relative in needed:
        dest = root / relative
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True); dest.write_text('placeholder\n')
    (root / 'gradle/baseline-inputs.json').write_text(json.dumps({'files': {k: 'unused' for k in baseline['files']}}))
    original = c.module_inputs(root)
    c.check_inputs(root, original)
    f.check_inventory(root)
    collected = p.collect_build_inputs(root)
    c.require(all(k in collected for k in original), 'product collector omitted module')
    added = root / 'android/test-host/src/test/java/NewUntracked.java'
    added.parent.mkdir(parents=True, exist_ok=True)
    added.write_text('new source\n')
    reject(lambda: c.check_inputs(root, original), 'SHA/inventory mismatch')
    c.require(str(added.relative_to(root)) in p.collect_build_inputs(root), 'product collector missed new file')
    c.require(str(added.relative_to(root)) in f.maintained_inventory(root), 'format collector missed new file')
    added.write_text('bad trailing whitespace \n')
    reject(lambda: f.check_inventory(root), 'trailing whitespace')
    added.unlink()
    key = 'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FakePorts.kt'
    path = root / key; old = path.read_bytes(); path.write_bytes(b'changed\n')
    reject(lambda: c.check_inputs(root, original), 'SHA/inventory mismatch')
    c.require(p.collect_build_inputs(root)[key] != collected[key], 'product collector SHA mutation missed')
    path.write_bytes(old)
    omitted = dict(original); omitted.pop(key)
    reject(lambda: c.check_inputs(root, omitted), 'SHA/inventory mismatch')
    wrong = dict(original); wrong[key] = '0' * 64
    reject(lambda: c.check_inputs(root, wrong), 'SHA/inventory mismatch')
    for relative in ['android/test-host/build/generated/Fake.java', 'android/test-host/.gradle/cache',
                     'android/test-host/.cache/generated']:
        dest = root / relative; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_text('ignored\n')
    c.check_inputs(root, original)
    c.require(p.collect_build_inputs(root) == collected, 'generated build/cache polluted product inventory')
    # Every migrated owner and the retained provider are individually required.
    for relative in c.T5_SOURCES:
        path = root / 'android/test-host' / relative; data = path.read_bytes()
        path.unlink()
        reject(lambda: c.module_inputs(root), 'required T5 module source')
        path.write_bytes(b'')
        reject(lambda: c.module_inputs(root), 'required T5 module source')
        path.write_bytes(data)
    duplicate = root / 'android/test-host/fixtures/java/org/llmasr/minimal/testhost/FakePorts.java'
    duplicate.write_text('duplicate owner\n')
    reject(lambda: c.module_inputs(root), 'duplicate T5')
    duplicate.unlink()
    # Missing required lock/config fails instead of silently shrinking the inventory.
    (root / 'android/test-host/gradle.lockfile').unlink()
    reject(lambda: c.module_inputs(root), 'missing/symlink')
print('PASS shared live module/product/format inventory add/change/omit/SHA/build-exclusion negatives')
