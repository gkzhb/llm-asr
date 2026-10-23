#!/usr/bin/env python3
"""Real mixed-compiler enum order negatives; no old classpath or source-only oracle."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]


def require(value, message):
    if not value:
        raise RuntimeError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def host_sources():
    # The existing host entry owns this list; do not maintain a divergent subset.
    script = (ROOT / 'scripts/test-minimal-apk.sh').read_text()
    blocks = re.findall(r'^host_classes=\(\n(.*?)^\)', script, re.M | re.S)
    require(len(blocks) == 1, 'canonical host_classes declaration missing/ambiguous')
    names = blocks[0].split()
    require(names and len(names) == len(set(names)), 'canonical host class inventory invalid')
    result = []
    for name in names:
        require(re.fullmatch(r'[a-z]+\.[A-Za-z][A-Za-z0-9]*', name), 'invalid host owner ' + name)
        base = ROOT / 'android/app/src/org/llmasr/minimal' / name.replace('.', '/')
        paths = [base.with_suffix(ext) for ext in ('.java', '.kt') if base.with_suffix(ext).is_file()]
        require(len(paths) == 1, 'missing/duplicate host source ' + name)
        result.append(paths[0])
    return result


class StopUnconfirmed(RuntimeError):
    """The tree may still be in use; callers must not delete it."""


@contextmanager
def owned_tree(evidence):
    tree = Path(tempfile.mkdtemp(prefix='tree-', dir=evidence))
    safe_to_remove = True
    try:
        yield tree
    except StopUnconfirmed:
        safe_to_remove = False
        print('FAIL stop unconfirmed; retained owned tree=' + str(tree), flush=True)
        raise
    finally:
        if safe_to_remove:
            shutil.rmtree(tree)


def process_identity(pid):
    try:
        stat = Path('/proc', str(pid), 'stat').read_text()
    except FileNotFoundError:
        return None
    fields = stat[stat.rfind(')') + 2:].split()
    return {'pid': pid, 'start': int(fields[19]), 'state': fields[0],
            'pgid': int(fields[2]), 'sid': int(fields[3])}


def session_members(owner):
    # A kernel SID remains reserved while any member exists. Pin its original
    # leader start time as well; never reach across sessions or into reused PIDs.
    leader = process_identity(owner['pid'])
    require(leader is None or leader['start'] == owner['start'], 'owned SID leader identity changed')
    members = []
    for entry in Path('/proc').iterdir():
        if entry.name.isdecimal():
            member = process_identity(int(entry.name))
            if member is not None and member['sid'] == owner['pid']:
                require(member['start'] >= owner['start'], 'owned SID start identity invalid')
                if member['state'] not in ('Z', 'X'):
                    members.append(member)
    return members


def signal_member(member, sig):
    # pidfd pins the actual target across the check/signal race. PGIDs are not
    # signalled numerically: GNU timeout can create another ordinary owned group.
    try:
        fd = os.pidfd_open(member['pid'])
    except ProcessLookupError:
        return
    try:
        now = process_identity(member['pid'])
        if now is not None and all(now[key] == member[key] for key in ('start', 'sid')):
            try:
                signal.pidfd_send_signal(fd, sig)
            except ProcessLookupError:
                pass
    finally:
        os.close(fd)


def finish_session(process, owner):
    try:
        deadline = time.monotonic() + 5
        grace = time.monotonic() + 0.15
        while True:
            members = session_members(owner)
            if not members:
                process.wait(timeout=max(0.01, deadline - time.monotonic()))
                # Re-check after reaping the shell, not just direct-child wait.
                require(not session_members(owner), 'owned SID acquired new running work')
                return
            require(time.monotonic() < deadline, 'owned SID failed to stop: ' + repr(members))
            sig = signal.SIGTERM if time.monotonic() < grace else signal.SIGKILL
            for member in members:
                signal_member(member, sig)
            time.sleep(0.01)
    except BaseException as error:
        raise StopUnconfirmed('owned SID stop not confirmed: ' + str(error)) from error


def run(command, log, records, label, timeout):
    timed_out = False
    with log.open('xb') as output:
        process = subprocess.Popen(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            owner = process_identity(process.pid)
            require(owner is not None and owner['sid'] == process.pid, 'new owned SID identity missing')
        except BaseException as error:
            raise StopUnconfirmed('cannot identify owned session: ' + str(error)) from error
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            code = 124
        finally:
            # Includes cancellation, deadline and unexpected wrapper exit. The
            # unchanged GNU timeout may create PGIDs, all inside this new SID.
            finish_session(process, owner)
    records.append({'phase': label, 'command': command, 'exit': code,
                    'timeout': timed_out, 'log': str(log.relative_to(ROOT)), 'log_sha256': sha(log)})
    require(not timed_out, label + ' timeout is infrastructure failure, not behavioral red')
    return code, log.read_text(errors='replace')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--green-only', action='store_true', help='old Java or migrated focused baseline, no mutants')
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--ports', action='store_true', help='Clock/Sink Java characterization and two production mutants')
    modes.add_argument('--phase', action='store_true', help='Inference phase/ports Java ABI and two production mutants')
    modes.add_argument('--batch', action='store_true', help='Twenty public boundary owners: Java characterization and production mutants')
    args = parser.parse_args()
    kotlin = Path(os.environ['KOTLIN_HOME'])
    stdlib = kotlin / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed Kotlin stdlib missing')
    parent = ROOT / ('.work/ap5batch-build/mutants' if args.batch else
                     '.work/ap5phase-build/mutants' if args.phase else
                     '.work/ap5ports-build/mutants' if args.ports else '.work/p2enum-build/mutants')
    parent.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix='run-', dir=parent))
    records = []
    mutations = []
    green_marker = ('PASS batch public boundaries Java ABI/nullable/ownership/IO' if args.batch else
                    'PASS inference phase ports Java ABI/codes/NONE/nullable/throws/SAM' if args.phase else
                    'PASS runtime log ports Java ABI/default/nullable/SAM/clock/store' if args.ports else
                    'PASS runtime log enums Java ABI/order/switch/errors and production persistence/codec')
    tools = {}
    if args.ports or args.phase or args.batch:
        for name in ('java', 'javac', 'kotlinc', 'javap'):
            binary = shutil.which(name)
            require(binary is not None, 'cached tool missing: ' + name)
            code, text = run([binary, '-version'], evidence / ('tool-' + name + '.log'), records,
                             'tool-' + name, 30)
            require(code == 0, 'tool identity failed: ' + name)
            tools[name] = {'path': binary, 'resolved': str(Path(binary).resolve()),
                           'sha256': sha(Path(binary).resolve()), 'version': text}
    sources = host_sources() + [ROOT / 'tests/KotlinMigrationTest.java']
    if args.batch:
        worker = [ROOT / ('tests/RuntimeLogWorkerErrorTest' + suffix) for suffix in ('.java', '.kt')
                  if (ROOT / ('tests/RuntimeLogWorkerErrorTest' + suffix)).is_file()]
        require(len(worker) == 1 and worker[0].suffix == '.kt', 'missing/duplicate batch Kotlin worker caller')
        sources.append(worker[0])
    original_hashes = {str(p.relative_to(ROOT)): sha(p) for p in sources}
    try:
        # Isolated production copies and fresh classes for every compilation.
        with owned_tree(evidence) as tree:
            copies = []
            for source in sources:
                target = tree / source.relative_to(ROOT)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                copies.append(target)

            def compile_and_run(label, diagnostic=None):
                classes = tree / ('classes-' + label)
                require(not classes.exists(), 'stale classes directory')
                code, text = run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '',
                                  *map(str, copies)], evidence / (label + '-compile.log'), records,
                                 label + '-compile', 270)
                require(code == 0 and 'PASS mixed JVM compile:' in text, label + ' compile must exit 0, not behavioral red')
                if args.batch and label == 'green0':
                    owners = ['asr.InferenceAdapter', 'asr.InferenceAdapter$Readiness', 'asr.InferenceAdapter$Library',
                              'ime.ImeController', 'ime.ImeController$Backend', 'ime.ImeController$Progress', 'ime.ImeController$Commit',
                              'diagnostics.RuntimeLogWorker', 'diagnostics.RuntimeLogCodec', 'diagnostics.RuntimeLogText',
                              'diagnostics.LogExportController', 'diagnostics.LogExportController$Backend',
                              'diagnostics.LogExportController$State', 'diagnostics.LogExportController$Ticket',
                              'diagnostics.LogExportPage', 'model.FileSafety', 'model.ModelAccess', 'model.ModelEntry',
                              'model.ModelManifest', 'model.ModelProviderBoundary', 'model.ModelProviderBoundary$Call',
                              'model.ModelReports', 'model.ModelReports$ImportPlan', 'model.ModelReports$FileDetail',
                              'model.ModelReports$FileOutcome', 'model.ModelSource', 'model.ModelSource$UriRef',
                              'modelmanagement.ModelPickerTickets', 'modelmanagement.ModelPickerTickets$Ticket',
                              'modelmanagement.ModelPageSession', 'modelmanagement.ModelPageSession$Importer',
                              'modelmanagement.ModelUiText', 'transcription.ResultFiles',
                              'transcription.TextExportController', 'transcription.TextExportController$Backend',
                              'transcription.TextExportController$State', 'transcription.TextExportController$Ticket',
                              'transcription.TextExportPage']
                    code, _ = run(['javap', '-p', '-s', '-v', '-classpath', str(classes),
                                   *['org.llmasr.minimal.' + name for name in owners]],
                                  evidence / 'green0-javap.log', records, 'green0-javap', 45)
                    require(code == 0, 'batch javap failed')
                if (args.ports or args.phase) and label == 'green0':
                    names = (['InferencePhase', 'InferencePhaseListener', 'NativeTranscription'] if args.phase else
                             ['RuntimeLogClock', 'RuntimeLogClock$System', 'RuntimeLogClock$Fake',
                              'RuntimeLogSink', 'RuntimeLogSink$None'])
                    package = 'org.llmasr.minimal.' + ('asr.' if args.phase else 'diagnostics.')
                    if args.phase and (classes / 'org/llmasr/minimal/asr/InferencePhaseListener$Companion.class').is_file():
                        names.append('InferencePhaseListener$Companion')
                    code, _ = run(['javap', '-p', '-s', '-v', '-classpath', str(classes),
                                   *[package + name for name in names]],
                                  evidence / 'green0-javap.log', records, 'green0-javap', 45)
                    require(code == 0, 'ports javap failed')
                worker_case = args.batch and label.startswith('RuntimeLogWorker-')
                java_test = (['org.llmasr.minimal.diagnostics.RuntimeLogWorkerErrorTest', 'interleave']
                             if worker_case else ['KotlinMigrationTest'])
                code, text = run(['java', '-cp', str(classes) + ':' + str(stdlib), *java_test],
                                 evidence / (label + '-run.log'), records, label + '-run', 45)
                if diagnostic is None:
                    marker = 'PASS RuntimeLogWorkerErrorTest:' if worker_case else green_marker
                    require(code == 0 and marker in text, label + ' green failed')
                else:
                    require(code == 1 and 'java.lang.AssertionError: ' + diagnostic in text,
                            label + ' did not hit exact runtime assertion: ' + diagnostic)
                shutil.rmtree(classes)

            compile_and_run('green0')
            if not args.green_only:
                options = ([
                    ('ModelEntry', 'hash-bytes', 'ModelEntry hash must retain byte count'),
                    ('ModelManifest', 'duplicate-names', 'ModelManifest duplicate names must fail closed'),
                    ('ModelReports', 'zero-copy-budget', 'ImportPlan zero-copy requires zero extra bytes'),
                    ('ModelSource', 'after-enumerate-cancel', 'ModelSource default must checkpoint after enumerate'),
                    ('ModelProviderBoundary', 'provider-cancel', 'Provider cancellation must be sanitized'),
                    ('ModelPickerTickets', 'foreign-ticket', 'ModelPickerTickets foreign ticket must be rejected'),
                    ('RuntimeLogCodec', 'flush-noop', 'RuntimeLogCodec must flush once without closing'),
                    ('RuntimeLogWorker', 'stale-token', 'third schedule must not admit a new drain while the successor owns the slot'),
                    ('TextExportController', 'queued-revoke', 'Revoked queued TXT must cause zero provider opens'),
                    ('InferenceAdapter', 'null-fixed-error', 'InferenceAdapter null result must use fixed failure')]
                    if args.batch else [('InferencePhase', 'explicit-code-zero', 'InferencePhase explicit JNI codes must remain 1/2/3/4'),
                            ('InferencePhaseListener', 'none-int-reject', 'InferencePhaseListener.NONE must ignore every int code')]
                           if args.phase else [('RuntimeLogClock', 'default-noop', 'RuntimeLogClock default advance exception/message'),
                            ('RuntimeLogSink', 'none-null-reject', 'RuntimeLogSink.None must ignore null snapshot')]
                           if args.ports else [('RuntimeLogSource', 'APP', 'IME'),
                                              ('RuntimeLogEventKind', 'APP_STARTUP', 'APP_READY')])
                for name, first, second in options:
                    candidates = [p for p in copies if p.name == name + '.kt']
                    require(len(candidates) == 1, 'mutant requires migrated production Kotlin ' + name)
                    target = candidates[0]
                    original = target.read_bytes()
                    if args.batch:
                        anchors = {
                            'ModelEntry': (b'file.hashCode() * 31 + java.lang.Long.hashCode(bytes)', b'file.hashCode() * 31'),
                            'ModelReports': (b'if (copyBytes == 0L) 0L else', b'if (copyBytes == 0L) ModelRepository.SPACE_PAD else'),
                            'ModelSource': (b'if (cancel.cancelled()) throw ModelRepository.CancelledException()', b'Unit'),
                            'ModelProviderBoundary': (b'catch (e: IOException) { throw IOException(safeMessage) }',
                                b'catch (e: java.util.concurrent.CancellationException) { throw e }\n            catch (e: IOException) { throw IOException(safeMessage) }'),
                            'ModelPickerTickets': (b'ticket != null && pending === ticket', b'ticket != null'),
                            'RuntimeLogCodec': (b'out.flush()', b'Unit'),
                            'RuntimeLogWorker': (b'if (owner != token) return', b'if (false && owner != token) return'),
                            'TextExportController': (b'if (Ticket.revoked(ticket)) { terminal = Phase.CANCELLED; return }',
                                b'if (false && Ticket.revoked(ticket)) { terminal = Phase.CANCELLED; return }'),
                            'InferenceAdapter': (b'IOException("Native returned null")', b'IOException("mutant native null")')}
                        if name == 'ModelManifest':
                            old, new = b'if (!fileNames.add(e.file()))', b'if (!fileNames.add(e.file()) && false)'
                        else:
                            old, new = anchors[name]
                        diagnostic = second
                    elif args.phase:
                        if name == 'InferencePhase':
                            old, new = b'MODEL_LOAD_STARTED(1)', b'MODEL_LOAD_STARTED(0)'
                        else:
                            old = b'InferencePhaseListener { }'
                            new = b'InferencePhaseListener { code -> if (code == Int.MIN_VALUE) throw IllegalStateException("mutant-none") }'
                        diagnostic = second
                    elif args.ports:
                        if name == 'RuntimeLogClock':
                            old = b'throw UnsupportedOperationException("clock does not support advance")'
                            new = b'Unit'
                        else:
                            old = b'open override fun onSnapshot(snapshot: List<RuntimeLogEvent?>?, droppedCount: Int) {}'
                            new = old[:-2] + b'{ if (snapshot == null) throw IllegalArgumentException("mutant-null") }'
                        diagnostic = second
                    else:
                        old = ('    ' + first + ',\n    ' + second + ',').encode()
                        new = ('    ' + second + ',\n    ' + first + ',').encode()
                        diagnostic = name + ' inventory/order mismatch'
                    require(original.count(old) == 1, 'production mutant anchor ambiguous ' + name)
                    original_sha = sha(target)
                    restore_source = True
                    try:
                        mutated = original.replace(old, new, 1)
                        if args.batch and name == 'ModelManifest':
                            part_old = b'if (!partNames.add(e.partName()))'
                            require(original.count(part_old) == 1, 'manifest part duplicate anchor ambiguous')
                            mutated = mutated.replace(part_old, b'if (!partNames.add(e.partName()) && false)', 1)
                        target.write_bytes(mutated)
                        mutated_sha = sha(target)
                        require(mutated_sha != original_sha, 'mutant did not change production bytes ' + name)
                        compile_and_run(name + '-red', diagnostic)
                    except StopUnconfirmed:
                        # The compiler may still read this exact mutant input.
                        restore_source = False
                        raise
                    finally:
                        if restore_source:
                            target.write_bytes(original)
                    require(target.read_bytes() == original, 'source restoration failed ' + name)
                    compile_and_run(name + '-restored')
                    mutations.append({'owner': name, ('mutation' if args.ports or args.phase or args.batch else 'swap'): [first, second],
                                      'original_sha256': original_sha, 'mutant_sha256': mutated_sha,
                                      'restored_sha256': sha(target), 'diagnostic': diagnostic})
                    print('PASS ' + ('batch' if args.batch else 'phase' if args.phase else 'ports' if args.ports else 'enum') + ' mutant ' + name
                          + ' compile=0 target-red=1 exact-source-restored-green=0')
        require(all(sha(ROOT / p) == s for p, s in original_hashes.items()), 'working source drift')
        result = {'green_only': args.green_only, 'inputs': original_hashes,
                  'driver_sha256': sha(Path(__file__)), 'compiler_wrapper_sha256': sha(ROOT / 'scripts/compile-jvm-sources.sh'),
                  'stdlib_sha256': sha(stdlib), 'records': records, 'mutations': mutations,
                  'temporary_tree_removed': True}
        if args.ports:
            result.update(ports=True, tools=tools)
        if args.phase:
            result.update(phase=True, tools=tools)
        if args.batch:
            result.update(batch=True, tools=tools)
        (evidence / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print('PASS runtime log ' + ('batch' if args.batch else 'phase' if args.phase else 'ports' if args.ports else 'enum') + ' real mixed compilation evidence=' + str(evidence.relative_to(ROOT)))
    except BaseException:
        (evidence / 'failed-records.json').write_text(json.dumps(records, indent=2) + '\n')
        raise


if __name__ == '__main__':
    def interrupted(signum, _frame):
        # Unwind owned subprocess and temporary-tree finally blocks on host cancellation.
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGHUP, interrupted)
    main()
