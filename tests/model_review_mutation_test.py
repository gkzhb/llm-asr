#!/usr/bin/env python3
"""Compiled semantic mutations. Compilation/timeouts never count as killed mutants."""
import re
import json
import hashlib
from pathlib import Path
import os
import subprocess
import tempfile
from mixed_compiler_lifecycle import run as owned_run, guard

ROOT = Path(__file__).resolve().parents[1]
CLASSES = Path(os.environ['MODEL_REVIEW_CLASSES']).resolve(strict=True)
RUNTIME = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
if not RUNTIME.is_file() or not CLASSES.is_dir():
    raise RuntimeError('current compiled classes and fixed Kotlin runtime required')
guard(CLASSES)
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
    base = SRC_BASE / sub / cls
    matches = [base.with_suffix(ext) for ext in ('.java', '.kt') if base.with_suffix(ext).is_file()]
    if len(matches) != 1:
        raise RuntimeError('missing/duplicate model mutant source ' + cls)
    return matches[0]
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
    caller = [ROOT / ('tests/' + test[0] + suffix) for suffix in ('.java', '.kt')
              if (ROOT / ('tests/' + test[0] + suffix)).is_file()]
    if len(caller) != 1 or caller[0].suffix != '.kt':
        raise RuntimeError('missing/duplicate migrated model caller ' + test[0])
    caller_identity = {'path': str(caller[0]), 'sha256': hashlib.sha256(caller[0].read_bytes()).hexdigest()}
    work = Path(tempfile.mkdtemp(prefix=name+'-', dir=OUT))
    (work / 'caller-identity.json').write_text(json.dumps(caller_identity, indent=2) + '\n')
    production = src_of(cls)
    source = production.read_text()
    if production.suffix == '.kt':
        if name != 'provider-cancel-trust':
            raise RuntimeError('unapproved Kotlin model mutant ' + name)
        old = 'catch (e: IOException) { throw IOException(safeMessage) }'
        new = ('catch (e: java.util.concurrent.CancellationException) { throw e }\n            '
               + old)
    if source.count(old) != 1:
        raise RuntimeError(name + ' mutation no longer unique')
    target = work / (cls + production.suffix)
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
    output = work / 'classes'
    compile_result = owned_run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'),
                                str(output), str(CLASSES), str(target)],
                               work, 'compile', 15, dependency=CLASSES)
    if compile_result.returncode != 0:
        raise RuntimeError((name, 'compile failure is not behavioral red', compile_result.stderr.decode(errors='replace')))
    result = owned_run(['java', '-cp', str(output)+':'+str(CLASSES)+':'+str(RUNTIME), *test],
                       work, 'run', 5, dependency=CLASSES)
    text = (result.stdout + result.stderr).decode(errors='replace')
    if result.returncode == 0 or expected not in text:
        raise RuntimeError((name, result.returncode, text))
    if hashlib.sha256(caller[0].read_bytes()).hexdigest() != caller_identity['sha256']:
        raise RuntimeError('model caller changed during actual route')
    print('PASS current Kotlin model caller:', test[0], caller_identity['sha256'])
    print('PASS compiled mutant killed:', name, 'exit', result.returncode, 'logs', work.relative_to(ROOT))
