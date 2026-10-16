#!/usr/bin/env python3
"""Bounded faults through actual driver.run/owned_tree and the unchanged base wrapper.
The PATH shim scales only GNU timeout's 120s to 0.35s (not a 120s wall test).
Ordinary shell/non-exec child chains stay in the driver's owned SID, not one PGID.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
DRIVER = ROOT / 'tests/runtime_log_enum_mutant_test.py'
WRAPPER = ROOT / 'scripts/compile-jvm-sources.sh'


def require(value, message):
    if not value:
        raise RuntimeError(message)


def identity(pid):
    try:
        text = Path('/proc', str(pid), 'stat').read_text()
    except FileNotFoundError:
        return None
    fields = text[text.rfind(')') + 2:].split()
    return {'pid': pid, 'start': fields[19], 'state': fields[0],
            'pgid': int(fields[2]), 'sid': int(fields[3])}


def alive(record):
    now = identity(record['pid'])
    return now is not None and now['start'] == record['start'] and now['state'] not in ('Z', 'X')


def wait_for(predicate, seconds, label):
    # Bounded process observation within a fault test, not agent-task polling.
    deadline = time.monotonic() + seconds
    while not predicate():
        require(time.monotonic() < deadline, 'fault fixture deadline: ' + label)
        time.sleep(0.01)


def worker(base, tool, fault):
    spec = importlib.util.spec_from_file_location('actual_enum_driver', DRIVER)
    driver = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(driver)

    def interrupted(signum, _frame):
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGHUP, interrupted)
    if fault == 'main-unconfirmed':
        main_fault(driver, base)
        return
    if fault == 'unconfirmed':
        # Actual observation failure, not a fake alive/success result. Inject only
        # after the real worker is ready, at the production stop-confirmation seam.
        def unavailable(*_args):
            raise OSError('controlled stop observation unavailable')
        if hasattr(driver, 'session_members'):
            driver.session_members = unavailable
        else:
            def fail_stop(_process):
                raise driver.StopUnconfirmed('controlled stop observation unavailable')
            driver.finish_group = fail_stop
    records = []
    tree = None
    try:
        with driver.owned_tree(base) as tree:
            (base / 'tree.json').write_text(json.dumps(str(tree)))
            (tree / 'Probe.kt').write_text('class Probe\n')
            (tree / 'Probe.java').write_text('class Probe {}\n')
            command = ([str(base / 'bin/java')] if tool == 'java' else
                       ['bash', str(WRAPPER), str(tree / 'classes'), '',
                        str(tree / ('Probe.kt' if tool == 'kotlinc' else 'Probe.java'))])
            try:
                code, _ = driver.run(command, base / 'actual-run.log', records, tool + '-' + fault,
                                     0.8 if fault in ('timeout', 'unconfirmed') else 8)
                require(code != 0, 'fault must propagate a nonzero command exit')
                raise SystemExit(code if code > 0 else 128 - code)
            finally:
                identities = json.loads((base / 'ready.json').read_text()) if (base / 'ready.json').exists() else []
                (base / 'before-cleanup.json').write_text(json.dumps({'workers': identities,
                    'live_before_cleanup': [p for p in identities if alive(p)], 'tree_exists': tree.exists(),
                    'records': records}, indent=2) + '\n')
    finally:
        (base / 'after-scope.json').write_text(json.dumps({'tree_exists': tree is not None and tree.exists()}) + '\n')


def main_fault(driver, base):
    # Execute actual main/mutant/finally; skip only earlier successful tool work.
    # The failing mutant invokes actual run + wrapper with a live worker.
    actual_run = driver.run
    calls = []
    state = {}

    def unavailable(*_args):
        raise OSError('controlled main stop observation unavailable')

    def fault_run(command, log, records, label, timeout):
        calls.append(label)
        if label == 'green0-compile':
            Path(command[2]).mkdir()
            return 0, 'PASS mixed JVM compile:'
        if label == 'green0-run':
            return 0, 'PASS runtime log enums Java ABI/order/switch/errors and production persistence/codec'
        require(label == 'RuntimeLogSource-red-compile', 'unexpected subsequent compile/run: ' + label)
        classes = Path(command[2])
        classes.mkdir()
        (classes / 'live-marker').write_text('compiler still owns classes\n')
        target = next(Path(p) for p in command if p.endswith('/RuntimeLogSource.kt'))
        tree = classes.parent
        state.update(target=str(target), tree=str(tree), classes=str(classes),
                     mutant_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),
                     original_sha256=hashlib.sha256((ROOT / target.relative_to(tree)).read_bytes()).hexdigest())
        (base / 'tree.json').write_text(json.dumps(str(tree)))
        driver.session_members = unavailable
        return actual_run(command, log, records, label, 0.8)

    driver.run = fault_run
    saved = sys.argv
    sys.argv = [str(DRIVER)]
    caught = None
    try:
        driver.main()
    except driver.StopUnconfirmed as error:
        caught = error
        raise
    finally:
        sys.argv = saved
        require(state, 'actual main never reached mutant compiler')
        tree = Path(state['tree'])
        identities = json.loads((base / 'ready.json').read_text())
        (base / 'before-cleanup.json').write_text(json.dumps({'workers': identities,
            'live_before_cleanup': [p for p in identities if alive(p)], 'tree_exists': tree.exists(),
            'records': []}, indent=2) + '\n')
        (base / 'after-scope.json').write_text(json.dumps({'tree_exists': tree.exists()}) + '\n')
        state.update(calls=calls, stop_unconfirmed_propagated=caught is not None,
                     exception=str(caught), source_sha256_after=hashlib.sha256(Path(state['target']).read_bytes()).hexdigest(),
                     classes_retained=Path(state['classes']).is_dir(),
                     marker_retained=(Path(state['classes']) / 'live-marker').read_text() == 'compiler still owns classes\n')
        (base / 'main-observation.json').write_text(json.dumps(state, indent=2) + '\n')


FAKE = r'''#!/usr/bin/env python3
import json, os, signal, sys
from pathlib import Path
base = Path(os.environ['ENUM_CLEANUP_CASE'])
signal.signal(signal.SIGTERM, signal.SIG_IGN)
signal.signal(signal.SIGHUP, signal.SIG_IGN)
def ident(pid):
    s=Path('/proc',str(pid),'stat').read_text(); f=s[s.rfind(')')+2:].split()
    return {'pid':pid,'start':f[19],'state':f[0],'pgid':int(f[2]),'sid':int(f[3])}
# Parent is the actual ordinary non-exec shell, analogous to kotlinc -> JVM.
(base/'ready.json').write_text(json.dumps([ident(os.getppid()),ident(os.getpid())]))
while True:
    signal.pause()
'''


def case(parent, tool, fault):
    base = parent / (tool + '-' + fault)
    base.mkdir()
    bindir = base / 'bin'
    bindir.mkdir()
    worker_file = bindir / 'equivalent-jvm.py'
    worker_file.write_text(FAKE)
    for name in ('kotlinc', 'javac', 'java'):
        p = bindir / name
        # Explicitly no exec: retain a normal shell between compiler and worker.
        p.write_text('#!' + shutil.which('bash') + '\ntrap "" TERM HUP\n' +
                     shutil.which('python3') + ' "' + str(worker_file) + '"\n')
        p.chmod(0o755)
    home = base / 'kotlin/lib'
    home.mkdir(parents=True)
    for name in ('kotlin-stdlib.jar', 'annotations-13.0.jar'):
        (home / name).touch()
    real_timeout = shutil.which('timeout')
    require(real_timeout is not None, 'GNU timeout missing')
    if fault == 'deadline':
        # Keep production argv; shorten the timer and let shell exit on TERM,
        # while its ordinary worker ignores it. No --foreground/kill-after added.
        for name in ('kotlinc', 'javac'):
            p = bindir / name
            # Bash defers traps while a foreground external command runs. An
            # explicit ordinary child + wait lets the shell exit on TERM while
            # its TERM-ignoring equivalent JVM remains, exercising SID cleanup.
            p.write_text(p.read_text().replace('trap "" TERM HUP', 'trap "exit 143" TERM HUP').rstrip() + ' &\nwait\n')
        shim = bindir / 'timeout'
        shim.write_text('#!' + sys.executable + '\nimport os,sys,json\nfrom pathlib import Path\n'
                        'args=sys.argv[1:]\nPath(os.environ["ENUM_CLEANUP_CASE"],"timeout-args.json").write_text(json.dumps(args))\n'
                        'scaled=["0.35" if a=="120" else a for a in args]\n'
                        'os.execv(' + repr(real_timeout) + ',[' + repr(real_timeout) + ']+scaled)\n')
        shim.chmod(0o755)
    env = dict(os.environ, PATH=str(bindir) + ':' + os.environ['PATH'],
               KOTLIN_HOME=str(home.parent), ENUM_CLEANUP_CASE=str(base))
    records = []
    reclaim = []
    process = None
    tree = None
    try:
        with (base / 'worker.log').open('xb') as log:
            process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), '--worker', str(base), tool, fault],
                                       env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            wait_for(lambda: (base / 'ready.json').exists() or process.poll() is not None, 5, 'worker readiness')
            require((base / 'ready.json').exists(), 'fixture failed before worker ready: ' + (base / 'worker.log').read_text())
            records = json.loads((base / 'ready.json').read_text())
            # Include the real wrapper/GNU timeout identities in test-only finally
            # reclamation, particularly when observation failure leaves them live.
            for entry in Path('/proc').iterdir():
                if entry.name.isdecimal():
                    p = identity(int(entry.name))
                    if p is not None and p['sid'] == records[0]['sid']:
                        reclaim.append(p)
            (base / 'reclaim-identities.json').write_text(json.dumps(reclaim, indent=2) + '\n')
            require(records[0]['sid'] == records[1]['sid'], 'ordinary shell/worker changed SID')
            require(records[0]['pid'] != records[1]['pid'], 'fixture shell execed worker')
            if fault in ('term', 'hup'):
                os.kill(process.pid, signal.SIGTERM if fault == 'term' else signal.SIGHUP)
            code = process.wait(timeout=15)
        require(code != 0, 'fault swallowed by driver')
        observation = json.loads((base / 'before-cleanup.json').read_text())
        tree = Path(json.loads((base / 'tree.json').read_text()))
        require(observation['tree_exists'], 'fixture lost tree before cleanup observation')
        retained = json.loads((base / 'after-scope.json').read_text())['tree_exists']
        if fault in ('unconfirmed', 'main-unconfirmed'):
            require(retained and tree.exists(), 'R1 stop-unconfirmed deleted owned tree')
            diagnostic = 'controlled main stop observation unavailable' if fault == 'main-unconfirmed' else 'controlled stop observation unavailable'
            require(diagnostic in (base / 'worker.log').read_text(), 'missing exact stop failure')
            if fault == 'main-unconfirmed':
                observed = json.loads((base / 'main-observation.json').read_text())
                require(observed['stop_unconfirmed_propagated'], 'actual main swallowed StopUnconfirmed')
                require(observation['live_before_cleanup'], 'main fault did not retain actual live workers')
                require(observed['mutant_sha256'] != observed['original_sha256'], 'actual main did not mutate production copy')
                require(observed['source_sha256_after'] == observed['mutant_sha256'], 'R1 main restored live mutant source after StopUnconfirmed')
                require(observed['calls'] == ['green0-compile', 'green0-run', 'RuntimeLogSource-red-compile'], 'main continued tool execution')
                require(observed['classes_retained'] and observed['marker_retained'], 'main cleaned live classes')
        else:
            require(not observation['live_before_cleanup'], 'R1 live owned worker before tree cleanup: ' + repr(observation['live_before_cleanup']))
            require(not any(alive(p) for p in records), 'R1 owned worker survived driver exit')
            require(not retained and not tree.exists(), 'temporary tree not cleaned after confirmed stop')
        if tool != 'java':
            require(records[0]['pgid'] != records[0]['sid'], 'GNU timeout did not create a different owned PGID')
        if fault == 'deadline':
            args = json.loads((base / 'timeout-args.json').read_text())
            require(args[0] == '120' and '--foreground' not in args, 'base deadline strategy changed')
            require(code == 124, 'base GNU timeout deadline exit not propagated')
        if fault == 'timeout':
            require('timeout is infrastructure failure, not behavioral red' in (base / 'worker.log').read_text(), 'driver timeout not propagated')
        if fault in ('term', 'hup'):
            require(code == 128 + (signal.SIGTERM if fault == 'term' else signal.SIGHUP), 'cancellation exit changed')
        (base / 'result.json').write_text(json.dumps({'tool': tool, 'fault': fault, 'exit': code,
            'worker_identities': records, 'observation': observation, 'tree_retained': retained,
            'scaled_inner_deadline': fault == 'deadline'}, indent=2) + '\n')
        print('PASS cleanup ' + tool + '/' + fault + ' actual-driver/base-wrapper; ' +
              ('unconfirmed stop retains tree/source' if fault in ('unconfirmed', 'main-unconfirmed') else 'no live work before deletion'), flush=True)
    finally:
        # Even red and confirmation-failure tests reclaim actual PID/start-owned work.
        if not records and (base / 'ready.json').exists():
            records = json.loads((base / 'ready.json').read_text())
        reclaim = reclaim or records
        for p in reclaim:
            if alive(p):
                fd = os.pidfd_open(p['pid'])
                try:
                    if alive(p):
                        signal.pidfd_send_signal(fd, signal.SIGKILL)
                finally:
                    os.close(fd)
        if process is not None:
            if process.poll() is None:
                process.kill()
            process.wait(timeout=5)
        wait_for(lambda: not any(alive(p) for p in reclaim), 5, 'finally reclaims fault workers')
        # Only after actual stopped identities is it safe to remove a retained tree.
        if tree is None and (base / 'tree.json').exists():
            tree = Path(json.loads((base / 'tree.json').read_text()))
        if tree is not None and tree.exists():
            shutil.rmtree(tree)
        (base / 'finally.json').write_text(json.dumps({'matching_live_workers': [p for p in reclaim if alive(p)],
            'tree_exists': tree is not None and tree.exists()}) + '\n')


def main():
    if len(sys.argv) > 1 and sys.argv[1] == '--worker':
        worker(Path(sys.argv[2]), sys.argv[3], sys.argv[4])
        return
    parser = argparse.ArgumentParser()
    parser.add_argument('--case', choices=['term-kotlinc', 'unconfirmed-kotlinc', 'main-unconfirmed-kotlinc'])
    args = parser.parse_args()
    parent = ROOT / '.work/p2enum-build/r1-main/cleanup'
    parent.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix='run-', dir=parent))
    (evidence / 'inputs.json').write_text(json.dumps({str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (DRIVER, WRAPPER, Path(__file__).resolve())}, indent=2) + '\n')
    pairs = [('kotlinc', args.case.rsplit('-', 1)[0])] if args.case else [(tool, fault) for tool in ('kotlinc', 'javac', 'java')
        for fault in ('term', 'hup', 'timeout')] + [(tool, 'deadline') for tool in ('kotlinc', 'javac')] + [('kotlinc', 'unconfirmed'), ('kotlinc', 'main-unconfirmed')]
    for tool, fault in pairs:
        case(evidence, tool, fault)
    print('PASS enum cleanup lifecycle cases=' + str(len(pairs)) + ' evidence=' + str(evidence.relative_to(ROOT)))


if __name__ == '__main__':
    main()
