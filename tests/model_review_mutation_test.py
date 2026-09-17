#!/usr/bin/env python3
"""Compiled semantic mutations. Compilation/timeouts never count as killed mutants."""
import re
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CLASSES = Path(os.environ['MODEL_REVIEW_CLASSES']).resolve(strict=True)
assert CLASSES.is_dir(), 'MODEL_REVIEW_CLASSES must name this run\'s compiled class directory'
OUT = Path(os.environ.get('MODEL_REVIEW_MUTATION_OUT', str(ROOT / '.work/model-review-fixes/mutants')))
OUT.mkdir(parents=True, exist_ok=True)
# R6: classes moved into per-feature subpackages. Resolve the path the same
# way tests/model_android_source_test.py does, so the mutations target the
# actual production sources.
PACKAGE_MAP = {
    'ModelReadiness': 'model',
    'ModelProviderBoundary': 'model',
    'ModelOperationControl': 'modelmanagement',
    'ModelManagementController': 'modelmanagement',
    'ModelManagementState': 'modelmanagement',
    'ModelRepository': 'model',
    'ModelSource': 'model',
    'TaskCoordinator': 'task',
}
SRC_BASE = ROOT / 'android/app/src/org/llmasr/minimal'
def src_of(cls):
    sub = PACKAGE_MAP[cls]
    return SRC_BASE / sub / f'{cls}.java'
mutations = [
    ('readiness-monitor', 'ModelReadiness', 'public void notifyChange() {',
     'public synchronized void notifyChange() {', ['ModelReadinessTest'], 'readiness listener lock regression'),
    ('provider-cancel-trust', 'ModelProviderBoundary',
     'catch (IOException | RuntimeException | LinkageError e) { throw new IOException(safeMessage); }',
     'catch (java.util.concurrent.CancellationException e) { throw e; }\n        catch (IOException | RuntimeException | LinkageError e) { throw new IOException(safeMessage); }',
     ['ModelProviderBoundaryTest'], 'CancellationException: Private account'),
    ('terminal-cancel', 'ModelOperationControl', 'if (active == op && op != null) op.terminated = true;',
     'if (active == op && op != null) { /* mutant forgets terminal decision */ }',
     ['ModelReviewFixTest', 'terminal'], 'late reentrant cancel must not overwrite terminal'),
]
for name, cls, old, new, test, expected in mutations:
    work = Path(tempfile.mkdtemp(prefix=name+'-', dir=OUT))
    source = src_of(cls).read_text()
    assert source.count(old) == 1, (name, 'mutation no longer unique')
    target = work / (cls+'.java')
    # Same source body, but with package decl updated to match its new home
    # so the copy is self-contained when compiled on its own.
    sub = PACKAGE_MAP[cls]
    pkg = 'org.llmasr.minimal.' + sub
    if sub:
        mutated_source = re.sub(r'^package\s+org\.llmasr\.minimal\s*;', f'package {pkg};', source, count=1, flags=re.M)
    else:
        mutated_source = source
    mutated_source = mutated_source.replace(old, new)
    target.write_text(mutated_source)
    compile_result = subprocess.run(['javac', '--release', '8', '-encoding', 'UTF-8', '-cp', str(CLASSES), '-d', str(work), str(target)],
                                    capture_output=True, text=True, timeout=30)
    (work/'compile.log').write_text(compile_result.stdout+compile_result.stderr)
    assert compile_result.returncode == 0, (name, 'compile failure is not behavioral red', compile_result.stderr)
    result = subprocess.run(['java', '-cp', str(work)+':'+str(CLASSES), *test], capture_output=True, text=True, timeout=20)
    (work/'run.log').write_text(result.stdout+result.stderr)
    assert result.returncode != 0 and expected in result.stdout+result.stderr, (name, result.returncode, result.stdout, result.stderr)
    print('PASS compiled mutant killed:', name, 'exit', result.returncode, 'logs', work.relative_to(ROOT))
