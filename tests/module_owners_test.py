#!/usr/bin/env python3
"""Verify actual class outputs have exactly one declared production owner/module."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = {'AppGraph', 'AsrImeService', 'MainActivity', 'LogsActivity',
       'ModelManagementActivity', 'ActivityWindow', 'AsrOperation', 'AppReportWriter'}
RUNTIME = {'JniNativeTranscription', 'ForegroundRecorder', 'SafModelSource', 'SafModelZipSource', 'AndroidModelVerificationIo', 'OperationContext', 'ImeBackend'}


def check(root=ROOT):
    baseline = json.loads((root / 'gradle/baseline-inputs.json').read_text())
    sources = [Path(p) for p in baseline['files'] if p.endswith(('.java', '.kt'))]
    if len(sources) != 70 or len({p.stem for p in sources}) != 70:
        raise AssertionError('production source inventory changed: review module ownership')
    expected = {'app': set(), 'asr-runtime': set(), 'core': set()}
    for path in sources:
        module = 'app' if path.stem in APP else 'asr-runtime' if path.stem in RUNTIME else 'core'
        expected[module].add(str(path.relative_to('android/app/src').with_suffix('')))
    roots = {
        'app': [root / 'android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',
                root / 'android/app/build/tmp/kotlin-classes/debug'],
        'asr-runtime': [root / 'android/asr-runtime/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',
                        root / 'android/asr-runtime/build/tmp/kotlin-classes/debug'],
        'core': [root / 'core/build/classes/java/main', root / 'core/build/classes/kotlin/main'],
    }
    # Java/Kotlin roots are checked together, including direct definitions: a
    # private nested class cannot conceal a missing top-level production owner.
    seen = set()
    for module, directories in roots.items():
        owners = set()
        direct_owners = set()
        for directory in directories:
            if not directory.is_dir():
                # Before this migration the declared app Kotlin output is source-less.
                if module == 'app' and directory.name == 'debug' and not any(
                        p.suffix == '.kt' and p.stem in APP for p in sources):
                    continue
                raise AssertionError('current module class output missing: ' + str(directory))
            for path in directory.rglob('*.class'):
                name = str(path.relative_to(directory).with_suffix(''))
                if name in seen:
                    raise AssertionError('duplicate actual class owner: ' + name)
                seen.add(name)
                if not name.startswith('org/llmasr/minimal/'):
                    raise AssertionError('unexpected compiled class: ' + name)
                owner = name.split('$', 1)[0]
                if owner.endswith('Kt') and owner[:-2] in expected[module]:
                    owner = owner[:-2]
                owners.add(owner)
                if '$' not in name:
                    direct_owners.add(owner)
                if module == 'core' and any(x in path.read_bytes() for x in [b'android/', b'org/json/']):
                    raise AssertionError('platform reference in actual core class: ' + name)
        if direct_owners != expected[module]:
            raise AssertionError(module + ' direct production owner missing/extra: ' +
                                 repr({'extra': sorted(direct_owners - expected[module]),
                                       'missing': sorted(expected[module] - direct_owners)}))
        if owners != expected[module]:
            raise AssertionError(module + ' actual source owner mismatch: ' +
                                 repr({'extra': sorted(owners - expected[module]),
                                       'missing': sorted(expected[module] - owners)}))
        print('PASS current unique module production owners:', module, len(owners))


if __name__ == '__main__':
    check()
