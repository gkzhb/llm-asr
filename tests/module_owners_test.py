#!/usr/bin/env python3
"""Verify actual class outputs have exactly one declared production owner/module."""
import argparse
import contextlib
import hashlib
import io
import json
import shutil
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = {'AppGraph', 'AsrImeService', 'MainActivity', 'LogsActivity',
       'ModelManagementActivity', 'ActivityWindow', 'AsrOperation', 'AppReportWriter'}
RUNTIME = {'JniNativeTranscription', 'ForegroundRecorder', 'SafModelSource', 'SafModelZipSource', 'AndroidModelVerificationIo', 'OperationContext', 'ImeBackend'}


# Reviewed baseline source inventory: original 73 plus the three core IME owners.
# Keep paths/extensions explicit: an arbitrary same-sized baseline is not authorization.
REVIEWED_SOURCES = frozenset("""
android/app/src/org/llmasr/minimal/AppGraph.kt
android/app/src/org/llmasr/minimal/AsrImeService.kt
android/app/src/org/llmasr/minimal/LogsActivity.kt
android/app/src/org/llmasr/minimal/MainActivity.java
android/app/src/org/llmasr/minimal/ModelManagementActivity.java
android/app/src/org/llmasr/minimal/asr/InferenceAdapter.kt
android/app/src/org/llmasr/minimal/asr/InferencePhase.kt
android/app/src/org/llmasr/minimal/asr/InferencePhaseListener.kt
android/app/src/org/llmasr/minimal/asr/JniNativeTranscription.java
android/app/src/org/llmasr/minimal/asr/LoggingInferencePhaseListener.java
android/app/src/org/llmasr/minimal/asr/NativeResponse.kt
android/app/src/org/llmasr/minimal/asr/NativeTranscription.kt
android/app/src/org/llmasr/minimal/audio/AsrText.kt
android/app/src/org/llmasr/minimal/audio/ForegroundRecorder.kt
android/app/src/org/llmasr/minimal/audio/PcmWave.kt
android/app/src/org/llmasr/minimal/audio/RecordingControl.java
android/app/src/org/llmasr/minimal/audio/WaveInput.java
android/app/src/org/llmasr/minimal/diagnostics/LogExportController.kt
android/app/src/org/llmasr/minimal/diagnostics/LogExportPage.kt
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogClock.kt
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogCodec.kt
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogEvent.java
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogEventKind.kt
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogPersistence.java
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogSink.kt
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogSource.kt
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogStore.java
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogText.kt
android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogWorker.kt
android/app/src/org/llmasr/minimal/ime/ImeBackend.kt
android/app/src/org/llmasr/minimal/ime/ImeCapture.kt
android/app/src/org/llmasr/minimal/ime/ImeController.kt
android/app/src/org/llmasr/minimal/ime/ImeFieldPolicy.kt
android/app/src/org/llmasr/minimal/ime/ImeHistory.java
android/app/src/org/llmasr/minimal/ime/ImeHistoryStore.java
android/app/src/org/llmasr/minimal/ime/ImeSession.java
android/app/src/org/llmasr/minimal/model/AndroidModelVerificationIo.kt
android/app/src/org/llmasr/minimal/model/FileSafety.kt
android/app/src/org/llmasr/minimal/model/FixedModelPackage.kt
android/app/src/org/llmasr/minimal/model/ModelAccess.kt
android/app/src/org/llmasr/minimal/model/ModelCatalog.kt
android/app/src/org/llmasr/minimal/model/ModelDeployment.kt
android/app/src/org/llmasr/minimal/model/ModelEntry.kt
android/app/src/org/llmasr/minimal/model/ModelManifest.kt
android/app/src/org/llmasr/minimal/model/ModelPackagePolicy.kt
android/app/src/org/llmasr/minimal/model/ModelProviderBoundary.kt
android/app/src/org/llmasr/minimal/model/ModelReadiness.java
android/app/src/org/llmasr/minimal/model/ModelReports.kt
android/app/src/org/llmasr/minimal/model/ModelRepository.java
android/app/src/org/llmasr/minimal/model/ModelResidency.kt
android/app/src/org/llmasr/minimal/model/ModelSource.kt
android/app/src/org/llmasr/minimal/model/ModelVerification.kt
android/app/src/org/llmasr/minimal/model/ModelZipArchive.java
android/app/src/org/llmasr/minimal/model/ModelZipSource.kt
android/app/src/org/llmasr/minimal/model/SafModelSource.kt
android/app/src/org/llmasr/minimal/model/SafModelZipSource.kt
android/app/src/org/llmasr/minimal/modelmanagement/ModelManagementController.kt
android/app/src/org/llmasr/minimal/modelmanagement/ModelManagementState.java
android/app/src/org/llmasr/minimal/modelmanagement/ModelOperationControl.java
android/app/src/org/llmasr/minimal/modelmanagement/ModelPageSession.kt
android/app/src/org/llmasr/minimal/modelmanagement/ModelPickerTickets.kt
android/app/src/org/llmasr/minimal/modelmanagement/ModelUiText.kt
android/app/src/org/llmasr/minimal/platform/ActivityWindow.kt
android/app/src/org/llmasr/minimal/platform/OperationContext.kt
android/app/src/org/llmasr/minimal/task/RequestContext.java
android/app/src/org/llmasr/minimal/task/RequestRunner.kt
android/app/src/org/llmasr/minimal/task/TaskCoordinator.kt
android/app/src/org/llmasr/minimal/task/TaskKind.kt
android/app/src/org/llmasr/minimal/transcription/AppReportWriter.java
android/app/src/org/llmasr/minimal/transcription/AppRequestPolicy.java
android/app/src/org/llmasr/minimal/transcription/AppState.kt
android/app/src/org/llmasr/minimal/transcription/AsrOperation.kt
android/app/src/org/llmasr/minimal/transcription/ResultFiles.kt
android/app/src/org/llmasr/minimal/transcription/ResultState.java
android/app/src/org/llmasr/minimal/transcription/TextExportController.kt
android/app/src/org/llmasr/minimal/transcription/TextExportPage.kt
""".split())


def source_inventory(root):
    baseline = json.loads((root / 'gradle/baseline-inputs.json').read_text())
    sources = [Path(p) for p in baseline['files'] if p.endswith(('.java', '.kt'))]
    if ({str(p) for p in sources} != REVIEWED_SOURCES
            or len(sources) != len(REVIEWED_SOURCES)
            or len({p.stem for p in sources}) != len(REVIEWED_SOURCES)):
        raise AssertionError('production source inventory changed: review module ownership')
    for path in sources:
        source = root / path
        if (not source.is_file() or source.is_symlink()
                or hashlib.sha256(source.read_bytes()).hexdigest() != baseline['files'][str(path)]):
            raise AssertionError('production source input drift: ' + str(path))
    return sources

def check(root=ROOT):
    sources = source_inventory(root)
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


def self_test():
    """Run this same checker on finite copies of the actual compiled classes."""
    check(ROOT)
    failures = 0
    with tempfile.TemporaryDirectory(prefix='owner-check-') as temporary:
        root = Path(temporary)
        baseline = root / 'gradle/baseline-inputs.json'
        baseline.parent.mkdir(parents=True)
        baseline.write_bytes((ROOT / 'gradle/baseline-inputs.json').read_bytes())
        for relative in REVIEWED_SOURCES:
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
        for relative in ['android/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',
                         'android/app/build/tmp/kotlin-classes/debug',
                         'android/asr-runtime/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes',
                         'android/asr-runtime/build/tmp/kotlin-classes/debug',
                         'core/build/classes/java/main', 'core/build/classes/kotlin/main']:
            shutil.copytree(ROOT / relative, root / relative)
        pristine = json.loads(baseline.read_text())

        def reject(label, diagnostic):
            nonlocal failures
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    check(root)
            except AssertionError as failure:
                if diagnostic not in str(failure):
                    raise AssertionError('wrong owner negative ' + label + ': ' + str(failure)) from failure
            else:
                raise AssertionError('owner checker accepted negative: ' + label)
            failures += 1
            print('PASS same-checker owner negative:', label)

        for name in ['ImeCapture', 'ImeHistory', 'ImeHistoryStore']:
            relative, = [p for p in REVIEWED_SOURCES if Path(p).stem == name]
            changed = json.loads(json.dumps(pristine))
            del changed['files'][relative]
            baseline.write_text(json.dumps(changed))
            try:
                reject('baseline omission ' + name, 'production source inventory changed')
            finally:
                baseline.write_text(json.dumps(pristine))
        changed = json.loads(json.dumps(pristine))
        relative = 'android/app/src/org/llmasr/minimal/ime/ImeHistory.java'
        changed['files'][relative.replace('.java', '.kt')] = changed['files'][relative]
        baseline.write_text(json.dumps(changed))
        try:
            reject('duplicate source owner', 'production source inventory changed')
        finally:
            baseline.write_text(json.dumps(pristine))
        source = root / relative
        original = source.read_bytes()
        try:
            source.write_bytes(original + b'\n// synthetic input drift\n')
            reject('source hash drift', 'production source input drift')
        finally:
            source.write_bytes(original)
        core = root / 'core/build/classes/java/main'
        kotlin = root / 'core/build/classes/kotlin/main'
        runtime = root / 'android/asr-runtime/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
        for name in ['ImeCapture', 'ImeHistory', 'ImeHistoryStore']:
            classes = list(core.rglob(name + '.class')) + list(kotlin.rglob(name + '.class'))
            current, = classes
            saved = current.read_bytes()
            try:
                current.unlink()
                reject('direct class omission ' + name, 'core direct production owner missing/extra')
            finally:
                current.write_bytes(saved)
        current, = list(core.rglob('ImeHistory.class'))
        duplicate = kotlin / current.relative_to(core)
        duplicate.parent.mkdir(parents=True, exist_ok=True)
        try:
            shutil.copyfile(current, duplicate)
            reject('duplicate actual class', 'duplicate actual class owner')
        finally:
            duplicate.unlink()
        moved = runtime / current.relative_to(core)
        moved.parent.mkdir(parents=True, exist_ok=True)
        try:
            shutil.move(current, moved)
            reject('wrong module class', 'asr-runtime direct production owner missing/extra')
        finally:
            shutil.move(moved, current)
        for filename, payload, diagnostic in [
                ('org/llmasr/minimal/ime/Unknown.class', b'synthetic', 'core direct production owner missing/extra'),
                ('org/llmasr/minimal/ime/Unknown$Nested.class', b'synthetic', 'core actual source owner mismatch'),
                ('fixture/Leak.class', b'synthetic', 'unexpected compiled class')]:
            added = core / filename
            added.parent.mkdir(parents=True, exist_ok=True)
            try:
                added.write_bytes(payload)
                reject(filename, diagnostic)
            finally:
                added.unlink()
        original = current.read_bytes()
        try:
            current.write_bytes(original + b'android/synthetic')
            reject('platform leakage', 'platform reference in actual core class')
        finally:
            current.write_bytes(original)
        with contextlib.redirect_stdout(io.StringIO()):
            check(root)
    print('PASS owner checker self-test: ' + str(failures) + ' exact rejects and restored green')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        check()
