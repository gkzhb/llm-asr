#!/usr/bin/env python3
"""Independent bounded actual-production verification mutants (JVM, not Android).

Each ordinary/-O invocation: at most 9 mixed compilations x180s, 20 JVM x30s,
-Xmx256m, outer entry 2400s. Uses canonical owned-session and dependency lease.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'android/app/src/org/llmasr/minimal'


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


owned = load('_verification_owned', ROOT / 'tests/logs_activity_picker_test.py')
lifecycle = load('_verification_lifecycle', ROOT / 'tests/mixed_compiler_lifecycle.py')


def require(ok, message):
    if not ok:
        raise AssertionError(message)


def identity(path):
    data = path.read_bytes()
    return {'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def replace(path, old, new):
    text = path.read_text()
    require(text.count(old) == 1, 'mutant exact anchor ' + str(path))
    path.write_text(text.replace(old, new))


def suite(classes, directory):
    require(classes.is_dir(), 'canonical dependency tree missing')
    directory.mkdir(parents=True, exist_ok=False)
    source_dir = directory / 'source'
    source_dir.mkdir()
    names = ('model/ModelVerification.kt', 'model/ModelAccess.kt', 'model/ModelRepository.java',
             'model/ModelReadiness.java', 'modelmanagement/ModelOperationControl.java')
    sources = []
    selected = {}
    for name in names:
        source = BASE / name
        target = source_dir / source.name
        shutil.copyfile(source, target)
        selected[source.name] = target
        sources.append(target)
    for name in ('ModelAndroidHelpersTest.kt', 'ModelRepositoryCancelTest.kt'):
        target = source_dir / name
        shutil.copyfile(ROOT / 'tests' / name, target)
        sources.append(target)
    (directory / 'inputs.json').write_text(json.dumps([identity(p) for p in sources], indent=2) + '\n')
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed Kotlin stdlib required')
    count = {'compile': 0, 'jvm': 0}

    def compile(label):
        count['compile'] += 1
        require(count['compile'] <= 9, 'compile budget exceeded')
        out = directory / ('classes-' + label)
        require(not out.exists(), 'fresh classes only')
        result = lifecycle.run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(out), str(classes),
                               *map(str, sources)], directory, label + '-compile', 180, dependency=classes)
        require(result.returncode == 0, 'compile/tool failure is not target red ' + label + ': ' + result.stderr.decode())
        return out

    def run(out, label, args, diagnostic=None):
        count['jvm'] += 1
        require(count['jvm'] <= 20, 'JVM budget exceeded')
        # Mutated production must be first, ahead of canonical dependency classes.
        cp = str(out) + ':' + str(classes) + ':' + str(stdlib)
        result = lifecycle.run(['java', '-Xmx256m', '-cp', cp, 'ModelAndroidHelpersTest', *args],
                              directory, label + '-jvm', 30, dependency=classes)
        if diagnostic is None:
            require(result.returncode == 0, label + ' failed ' + result.stderr.decode())
        else:
            error = result.stderr.decode()
            require(result.returncode == 1 and 'java.lang.AssertionError: ' + diagnostic in error,
                    'not exact production target red ' + label + ': ' + error)
        return result

    primary = None
    try:
        # lifecycle.run owns the canonical dependency for each sequential command.
        def cases():
            green = compile('green')
            run(green, 'green', [])
            shared = directory / 'cross-jvm'
            run(green, 'writer', ['write', str(shared)])
            run(green, 'reader', ['read', str(shared)])
            mutations = [
                ('missing', 'ModelVerification.kt',
                 'val ledger = io.read("ledger", LIMIT) ?: return false',
                 'val ledger = io.read("ledger", LIMIT) ?: run { val token = readiness.beginVerify("mutant"); readiness.commitVerified(token); return true }',
                 'missing proof must not restore READY'),
                ('revoke', 'ModelRepository.java',
                 '        mutationGuard.beforeMutation();\n        long freed = 0;',
                 '        if (!plan.isEmpty()) deleteManaged(plan.get(0));\n        mutationGuard.beforeMutation();\n        long freed = 0;',
                 'durable revoke must precede mutation'),
                ('cancel', 'ModelVerification.kt',
                 'if (!gate.reserve(token.epoch)) return false',
                 '// Mutant: skip reservation until after durable publication.',
                 'cancel winner must not leave restorable proof'),
                ('request', 'ModelAccess.kt',
                 '        proof.requireReady(repo)',
                 '        repo.verifyAll(null, ModelRepository.NEVER_CANCEL)\n        proof.requireReady(repo)',
                 'request must not hash'),
            ]
            for mode, name, old, new, diagnostic in mutations:
                path = selected[name]
                original = path.read_bytes()
                replace(path, old, new)
                if mode == 'missing':
                    # Keep the production anchor/ledger path, alter the missing-proof qualification.
                    replace(path, 'val a = anchor(false) ?: return false', 'val a = anchor(true)!!')
                if mode == 'cancel':
                    # Actual disk publication occurs before the late reservation; leave a
                    # restorable committed journal even though cancel already won.
                    replace(path, 'if (!gate.succeed(token.epoch)) throw IOException("模型校验世代已过期")',
                            'if (!gate.reserve(token.epoch)) return false\n            if (!gate.succeed(token.epoch)) return false')
                (directory / (mode + '-mutant.json')).write_text(json.dumps(identity(path), indent=2) + '\n')
                mutated = compile(mode + '-mutant')
                run(mutated, mode + '-mutant', [mode], diagnostic)
                path.write_bytes(original)
                require(identity(path)['sha256'] == hashlib.sha256(original).hexdigest(), 'exact source restore')
                restored = compile(mode + '-restored')
                run(restored, mode + '-restored', [mode])
                print('PASS verification production mutant compile0/exact AssertionError1/restored0 ' + mode, flush=True)
            (directory / 'budget.json').write_text(json.dumps(count, indent=2) + '\n')
        cases()
    except BaseException as failure:
        primary = failure
        raise
    finally:
        try:
            owned.cleanup_workspace(directory, remove=False)
        except BaseException as cleanup_failure:
            if primary is None:
                raise
            primary.add_note('cleanup refused; retained inputs: ' + repr(cleanup_failure))
    print('PASS independent model verification contracts ' + str(directory), flush=True)


def core_fault_suite(classes, directory):
    """Independent new matrix budget: one mixed compile180, <=12 JVM30, outer600."""
    directory.mkdir(parents=True, exist_ok=False)
    names = ('model/ModelVerification.kt', 'model/ModelReadiness.java', 'model/ModelRepository.java',
             'model/ModelAccess.kt', 'modelmanagement/ModelOperationControl.java',
             'modelmanagement/ModelManagementController.kt')
    sources = [BASE / name for name in names]
    sources += [ROOT / 'tests' / name for name in ('ModelAndroidHelpersTest.kt', 'ModelRepositoryCancelTest.kt',
                                                 'ModelManagementControllerTest.kt', 'RequestRunnerTest.kt')]
    (directory / 'inputs.json').write_text(json.dumps([identity(p) for p in sources], indent=2) + '\n')
    primary = None
    try:
        result = lifecycle.run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(directory / 'classes'), str(classes), *map(str, sources)], directory, 'corefault-compile', 180, dependency=classes)
        require(result.returncode == 0, 'corefault compile failed ' + result.stderr.decode())
        cp = str(directory / 'classes') + ':' + str(classes) + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'
        cases = [('ModelAndroidHelpersTest', mode) for mode in ('scoped', 'listeners', 'workers')]
        cases.append(('ModelManagementControllerTest', 'corefault'))
        require(len(cases) <= 12, 'corefault JVM budget exceeded')
        for index, (name, mode) in enumerate(cases):
            result = lifecycle.run(['java', '-Xmx256m', '-cp', cp, name, mode], directory, 'corefault-' + str(index), 30, dependency=classes)
            print(result.stdout.decode(), flush=True)
            require(result.returncode == 0, 'corefault behavior failed ' + result.stderr.decode())
        (directory / 'budget.json').write_text(json.dumps({'compile': 1, 'jvm': len(cases)}, indent=2) + '\n')
    except BaseException as failure:
        primary = failure
        raise
    finally:
        try:
            owned.cleanup_workspace(directory, remove=False)
        except BaseException as failure:
            if primary is None:
                raise
            primary.add_note('corefault cleanup refused; inputs retained: ' + repr(failure))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--contracts', required=True, type=Path)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--corefault', action='store_true')
    args = parser.parse_args()
    if args.corefault:
        core_fault_suite(args.contracts.resolve(), args.evidence_dir.resolve())
    else:
        suite(args.contracts.resolve(), args.evidence_dir.resolve())


if __name__ == '__main__':
    main()
