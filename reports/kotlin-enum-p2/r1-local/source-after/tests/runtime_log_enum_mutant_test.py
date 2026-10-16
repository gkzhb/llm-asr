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
    args = parser.parse_args()
    kotlin = Path(os.environ['KOTLIN_HOME'])
    stdlib = kotlin / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed Kotlin stdlib missing')
    parent = ROOT / '.work/p2enum-build/mutants'
    parent.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix='run-', dir=parent))
    records = []
    mutations = []
    sources = host_sources() + [ROOT / 'tests/KotlinMigrationTest.java']
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
                code, text = run(['java', '-cp', str(classes) + ':' + str(stdlib), 'KotlinMigrationTest'],
                                 evidence / (label + '-run.log'), records, label + '-run', 45)
                if diagnostic is None:
                    require(code == 0 and 'PASS runtime log enums Java ABI/order/switch/errors and production persistence/codec' in text,
                            label + ' green failed')
                else:
                    require(code == 1 and 'java.lang.AssertionError: ' + diagnostic in text,
                            label + ' did not hit exact runtime assertion: ' + diagnostic)
                shutil.rmtree(classes)

            compile_and_run('green0')
            if not args.green_only:
                for name, first, second in [('RuntimeLogSource', 'APP', 'IME'),
                                            ('RuntimeLogEventKind', 'APP_STARTUP', 'APP_READY')]:
                    candidates = [p for p in copies if p.name == name + '.kt']
                    require(len(candidates) == 1, 'mutant requires migrated production Kotlin ' + name)
                    target = candidates[0]
                    original = target.read_bytes()
                    old = ('    ' + first + ',\n    ' + second + ',').encode()
                    new = ('    ' + second + ',\n    ' + first + ',').encode()
                    require(original.count(old) == 1, 'enum swap anchor ambiguous ' + name)
                    original_sha = sha(target)
                    try:
                        target.write_bytes(original.replace(old, new, 1))
                        mutated_sha = sha(target)
                        require(mutated_sha != original_sha, 'mutant did not change production bytes ' + name)
                        compile_and_run(name + '-red', name + ' inventory/order mismatch')
                    finally:
                        target.write_bytes(original)
                    require(target.read_bytes() == original, 'source restoration failed ' + name)
                    compile_and_run(name + '-restored')
                    mutations.append({'owner': name, 'swap': [first, second], 'original_sha256': original_sha,
                                      'mutant_sha256': mutated_sha, 'restored_sha256': sha(target),
                                      'diagnostic': name + ' inventory/order mismatch'})
                    print('PASS enum mutant ' + name + ' compile=0 target-red=1 exact-source-restored-green=0')
        require(all(sha(ROOT / p) == s for p, s in original_hashes.items()), 'working source drift')
        result = {'green_only': args.green_only, 'inputs': original_hashes,
                  'driver_sha256': sha(Path(__file__)), 'compiler_wrapper_sha256': sha(ROOT / 'scripts/compile-jvm-sources.sh'),
                  'stdlib_sha256': sha(stdlib), 'records': records, 'mutations': mutations,
                  'temporary_tree_removed': True}
        (evidence / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print('PASS runtime log enum real mixed compilation evidence=' + str(evidence.relative_to(ROOT)))
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
