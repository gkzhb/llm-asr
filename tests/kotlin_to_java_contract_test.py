#!/usr/bin/env python3
"""Selected real production Kotlin-to-Java contracts; JVM only, never device evidence.

Use the canonical host inventory and unchanged reviewed owned runner/Workspace.
Compiler failures, timeouts and wrong owner loading are never behavioral reds.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


lifecycle = load('_k2j_lifecycle', 'tests/mixed_compiler_lifecycle.py')
canonical = load('_k2j_canonical', 'tests/runtime_log_enum_mutant_test.py')
SELECTED = ('audio/RecordingControl', 'task/RequestContext')
MARKER = b'PASS K2J selected production ABI/null/monitor/state/identity'


def require(value, diagnostic):
    if not value:
        raise AssertionError(diagnostic)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def identity(path):
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': sha(path)}


def selected_sources():
    result = []
    for name in SELECTED:
        base = ROOT / 'android/app/src/org/llmasr/minimal' / name
        matches = [base.with_suffix(ext) for ext in ('.java', '.kt') if base.with_suffix(ext).is_file()]
        require(len(matches) == 1, 'missing/duplicate selected owner: ' + name)
        result.append(matches[0])
    return result


def restore_confirmed(target, original_bytes, tree, dependency, attempted_records):
    """Never rewrite owned input unless every attempted child has a fresh terminal."""
    try:
        lifecycle.guard(tree)
        if dependency is not None:
            lifecycle.guard(dependency)
        require(attempted_records, 'restoration requires attempted child records')
        for record_path in attempted_records:
            record = json.loads(record_path.read_text())
            require(record.get('owned_exited') is True and record.get('root_reaped') is True,
                    'restoration exit unconfirmed: ' + str(record_path))
    except BaseException:
        # Keep both scratch and dependency fail-closed even for missing/bad ledgers.
        lifecycle.owned.write_json(tree / lifecycle.UNCONFIRMED,
                                   {'reason': 'restoration refused; preserve mutant inputs'})
        if dependency is not None:
            lifecycle.owned.write_json(dependency / '.mixed-lease-k2j-restoration.json',
                                       {'reason': 'restoration exit unconfirmed', 'work': str(tree)})
        raise
    target.write_bytes(original_bytes)


def suite(args):
    evidence = args.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    dependency = args.contracts.resolve() if args.contracts else None
    if dependency is not None:
        lifecycle.guard(dependency)
        require(dependency.is_dir(), 'fresh current host dependency missing')
    sources = (canonical.host_sources() if args.baseline else selected_sources())
    caller = ROOT / 'tests/KotlinMigrationTest.java'
    sources = sources + [caller]
    original = {str(p): sha(p) for p in sources}
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed cached stdlib missing')
    records = []
    attempted_records = []
    tools = {}

    def run(argv, label, timeout, work=evidence):
        record_path = work / (label + '.json')
        require(not record_path.exists(), 'fresh owned record required: ' + str(record_path))
        attempted_records.append(record_path)
        result = lifecycle.run(argv, work, label, timeout, dependency=dependency)
        record = json.loads(record_path.read_text())
        require(record['owned_exited'] and record['root_reaped'], 'owned exit unconfirmed: ' + label)
        records.append({'label': label, 'record': str(record_path), 'exit': result.returncode,
                        'stdout_sha256': record['stdout_sha256'], 'stderr_sha256': record['stderr_sha256']})
        return result

    try:
        for name in ('java', 'javac', 'kotlinc', 'javap'):
            binary = shutil.which(name)
            require(binary is not None, 'cached tool missing: ' + name)
            version = run([binary, '-version'], 'identity-' + name, 20)
            require(version.returncode == 0, 'tool identity failed: ' + name)
            tools[name] = {**identity(Path(binary).resolve()), 'version': (version.stdout + version.stderr).decode()}
        with lifecycle.owned.Workspace(parent=evidence) as tree:
            copies = []
            for source in sources:
                target = tree / source.relative_to(ROOT)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
                copies.append(target)
            copy_original = {str(p): sha(p) for p in copies}
            selected = [p for p in copies if p.stem in ('RecordingControl', 'RequestContext')]
            require(len(selected) == 2, 'selected copies inventory')

            def route(label, diagnostic=None):
                classes = evidence / ('classes-' + label)
                require(not classes.exists(), 'fresh classes required: ' + label)
                classpath = str(dependency) if dependency is not None else ''
                result = run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes),
                              classpath, *map(str, copies)], label + '-compile', 270, tree)
                require(result.returncode == 0 and b'PASS mixed JVM compile:' in result.stdout,
                        'compilation failure NOT target red: ' + label + '\n' + result.stderr.decode())
                cp = str(classes) + (':' + str(dependency) if dependency is not None else '') + ':' + str(stdlib)
                result = run(['java', '-Dk2j.owner.dir=' + str(classes), '-cp', cp, 'KotlinMigrationTest'],
                             label + '-run', 45, tree)
                require(result.stdout.count(b'K2J_OWNER ') == 3, 'loaded owner identity missing: ' + label)
                if diagnostic is None:
                    require(result.returncode == 0 and MARKER in result.stdout,
                            'green contract failed: ' + label + '\n' + result.stderr.decode())
                else:
                    require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic + '\n').encode() in result.stderr,
                            'not exact behavioral red: ' + label + '\n' + result.stderr.decode())
                if label in ('baseline', 'cancel-green'):
                    dump = run(['javap', '-p', '-s', '-v', '-classpath', str(classes),
                                'org.llmasr.minimal.audio.RecordingControl',
                                'org.llmasr.minimal.task.RequestContext'], label + '-javap', 45, tree)
                    require(dump.returncode == 0, 'selected javap failed')
                return classes

            if args.baseline:
                route('baseline')
            else:
                require(all(p.suffix == '.java' for p in selected), 'mutants require candidate Java production')
                gate = next(p for p in selected if p.stem == 'RecordingControl')
                context = next(p for p in selected if p.stem == 'RequestContext')
                mutants = [
                    ('cancel', gate, 'if (cancelled || !released || !started || committed)',
                     'if (!released || !started || committed)', 'K2J cancelled capture must never commit'),
                    ('early', gate, 'startBackend.run();\n        started = true;',
                     'started = true;\n        startBackend.run();', 'K2J backend failure must permit retry'),
                    ('monitor', gate, 'public final synchronized boolean cancelled()',
                     'public final boolean cancelled()', 'K2J RecordingControl synchronized ABI: cancelled'),
                    ('defaults', context, 'public int cleanedTemporary = 0;',
                     'public int cleanedTemporary = 1;', 'K2J RequestContext zero defaults'),
                ]
                for name, target, before, after, diagnostic in mutants:
                    original_bytes = target.read_bytes()
                    require(original_bytes.decode().count(before) == 1, 'unique production mutant anchor: ' + name)
                    route(name + '-green')
                    target.write_text(original_bytes.decode().replace(before, after))
                    primary = None
                    try:
                        route(name + '-red', diagnostic)
                    except BaseException as failure:
                        primary = failure
                        raise
                    finally:
                        try:
                            restore_confirmed(target, original_bytes, tree, dependency, attempted_records)
                        except BaseException as restoration_failure:
                            if primary is None:
                                raise
                            primary.add_note('mutant restoration refused; inputs retained: ' + repr(restoration_failure))
                    require(sha(target) == copy_original[str(target)], 'restored production SHA: ' + name)
                    route(name + '-restored')
            require(all(sha(p) == copy_original[str(p)] for p in copies), 'isolated source restoration drift')
            # Keep closed raw evidence before reviewed Workspace removes successful scratch.
            closed = evidence / 'closed-raw'
            closed.mkdir()
            for path in tree.glob('*'):
                if path.is_file():
                    shutil.copy2(path, closed / path.name)
            records[:] = [{**r, 'record': str(closed / Path(r['record']).name)
                           if Path(r['record']).parent == tree else r['record']} for r in records]
        require(all(sha(Path(p)) == value for p, value in original.items()), 'main source/caller drift')
        lifecycle.guard(evidence)
        if dependency is not None:
            lifecycle.guard(dependency)
        print('PASS K2J ' + ('original-language baseline compile0/run0' if args.baseline else
                            'four real production mutants compile0/green0/exact-red1/restored0; 12 routes'))
    finally:
        (evidence / 'receipt.json').write_text(json.dumps({
            'baseline': args.baseline, 'dependency': str(dependency), 'inputs': original,
            'caller_sha256': original[str(caller)], 'driver': identity(Path(__file__)),
            'compiler': identity(ROOT / 'scripts/compile-jvm-sources.sh'), 'stdlib': identity(stdlib),
            'tools': tools, 'records': records,
        }, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--baseline', action='store_true')
    mode.add_argument('--contracts', type=Path)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    suite(parser.parse_args())


if __name__ == '__main__':
    main()
