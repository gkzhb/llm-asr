#!/usr/bin/env python3
"""Bounded lifecycle faults through the real enum driver run and JVM wrapper.
Fake compiler/Java workers are test-owned, not production compiler substitutes for ABI tests.
The inner-deadline case explicitly scales 120s/kill grace with a PATH timeout shim.
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
    return {'pid': pid, 'start': fields[19], 'state': fields[0], 'pgid': int(fields[2])}


def alive(record):
    now = identity(record['pid'])
    return now is not None and now['start'] == record['start'] and now['state'] != 'Z'


def wait_for(predicate, seconds, label):
    # Deliberate bounded process observation inside a fault test, not agent-task polling.
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
    records = []
    # The observation is made immediately before deletion, not inferred from a log string.
    with tempfile.TemporaryDirectory(prefix='owned-tree-', dir=base) as temp:
        tree = Path(temp)
        (base / 'tree.json').write_text(json.dumps(str(tree)))
        (tree / 'Probe.kt').write_text('class Probe\n')
        (tree / 'Probe.java').write_text('class Probe {}\n')
        command = ([str(base / 'bin/java')] if tool == 'java' else
                   ['bash', str(WRAPPER), str(tree / 'classes'), '', str(tree / ('Probe.kt' if tool == 'kotlinc' else 'Probe.java'))])
        try:
            code, _ = driver.run(command, base / 'actual-run.log', records, tool + '-' + fault,
                                 0.35 if fault == 'timeout' else 8)
            require(code != 0, 'fault must propagate a nonzero command exit')
            raise SystemExit(code if code > 0 else 128 - code)
        finally:
            identities = json.loads((base / 'ready.json').read_text()) if (base / 'ready.json').exists() else []
            (base / 'before-cleanup.json').write_text(json.dumps({'workers': identities,
                'live_before_cleanup': [p for p in identities if alive(p)], 'tree_exists': tree.exists(),
                'records': records}, indent=2) + '\n')


FAKE = r'''#!/usr/bin/env python3
import json, os, signal, subprocess, sys
from pathlib import Path
base = Path(os.environ['ENUM_CLEANUP_CASE'])
signal.signal(signal.SIGTERM, signal.SIG_IGN)
def ident(pid):
    s=Path('/proc',str(pid),'stat').read_text(); f=s[s.rfind(')')+2:].split()
    return {'pid':pid,'start':f[19],'state':f[0],'pgid':int(f[2])}
if '--child' not in sys.argv:
    p=subprocess.Popen([sys.executable, __file__, '--child'])
    (base/'ready.json').write_text(json.dumps([ident(os.getpid()),ident(p.pid)]))
while True:
    signal.pause()
'''


def case(parent, tool, fault):
    base = parent / (tool + '-' + fault)
    base.mkdir()
    bindir = base / 'bin'
    bindir.mkdir()
    for name in ('kotlinc', 'javac', 'java'):
        p = bindir / name
        p.write_text(FAKE)
        p.chmod(0o755)
    home = base / 'kotlin/lib'
    home.mkdir(parents=True)
    for name in ('kotlin-stdlib.jar', 'annotations-13.0.jar'):
        (home / name).touch()
    real_timeout = shutil.which('timeout')
    require(real_timeout is not None, 'GNU timeout missing')
    if fault == 'deadline':
        # Exec the real GNU tool, scaling only the fixed inner compiler timer/grace.
        shim = bindir / 'timeout'
        shim.write_text('#!' + sys.executable + '\nimport os,sys,json\nfrom pathlib import Path\n'
                        'args=sys.argv[1:]\nPath(os.environ["ENUM_CLEANUP_CASE"],"timeout-args.json").write_text(json.dumps(args))\n'
                        'scaled=["0.25" if a=="120" else "--kill-after=0.15s" if a.startswith("--kill-after=") else a for a in args]\n'
                        'os.execv(' + repr(real_timeout) + ',[' + repr(real_timeout) + ']+scaled)\n')
        shim.chmod(0o755)
    env = dict(os.environ, PATH=str(bindir) + ':' + os.environ['PATH'],
               KOTLIN_HOME=str(home.parent), ENUM_CLEANUP_CASE=str(base))
    records = []
    process = None
    try:
        with (base / 'worker.log').open('xb') as log:
            process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), '--worker', str(base), tool, fault],
                                       env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            wait_for(lambda: (base / 'ready.json').exists() or process.poll() is not None, 5, 'worker readiness')
            require((base / 'ready.json').exists(), 'fixture failed before worker ready: ' + (base / 'worker.log').read_text())
            records = json.loads((base / 'ready.json').read_text())
            if fault in ('term', 'hup'):
                os.kill(process.pid, signal.SIGTERM if fault == 'term' else signal.SIGHUP)
            code = process.wait(timeout=12)
        require(code != 0, 'fault swallowed by driver')
        observation = json.loads((base / 'before-cleanup.json').read_text())
        require(observation['tree_exists'], 'fixture lost tree before cleanup observation')
        require(not observation['live_before_cleanup'], 'R1 live owned worker before tree cleanup: ' + repr(observation['live_before_cleanup']))
        require(not any(alive(p) for p in records), 'R1 owned worker survived driver exit')
        require(not Path(json.loads((base / 'tree.json').read_text())).exists(), 'temporary tree not cleaned')
        if fault == 'deadline':
            args = json.loads((base / 'timeout-args.json').read_text())
            require('120' in args, 'production inner deadline changed')
            require('--foreground' in args and any(a.startswith('--kill-after=') for a in args), 'deadline lacks foreground/KILL upgrade')
            require(code == 137, 'TERM-ignoring compiler must require deadline KILL escalation')
        if fault == 'timeout':
            require('timeout is infrastructure failure, not behavioral red' in (base / 'worker.log').read_text(), 'driver timeout not propagated')
        (base / 'result.json').write_text(json.dumps({'tool': tool, 'fault': fault, 'exit': code,
            'worker_identities': records, 'observation': observation, 'no_live_worker_after_exit': True,
            'scaled_inner_deadline': fault == 'deadline'}, indent=2) + '\n')
        print('PASS cleanup ' + tool + '/' + fault + ' actual-driver/wrapper nonzero; no live workers before tree deletion', flush=True)
    finally:
        # Even an OLD implementation red cannot leak our intentionally stuck workers.
        if not records and (base / 'ready.json').exists():
            records = json.loads((base / 'ready.json').read_text())
        for p in records:
            if alive(p):
                os.kill(p['pid'], signal.SIGKILL)
        if process is not None:
            if process.poll() is None:
                process.kill()
            process.wait(timeout=5)
        wait_for(lambda: not any(alive(p) for p in records), 5, 'finally reclaims fault workers')


def main():
    if len(sys.argv) > 1 and sys.argv[1] == '--worker':
        worker(Path(sys.argv[2]), sys.argv[3], sys.argv[4])
        return
    parser = argparse.ArgumentParser()
    parser.add_argument('--case', choices=['term-kotlinc'])
    args = parser.parse_args()
    parent = ROOT / '.work/p2enum-build/r1-fix/cleanup'
    parent.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix='run-', dir=parent))
    (evidence / 'inputs.json').write_text(json.dumps({str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (DRIVER, WRAPPER, Path(__file__).resolve())}, indent=2) + '\n')
    pairs = [('kotlinc', 'term')] if args.case else [(tool, fault) for tool in ('kotlinc', 'javac', 'java')
        for fault in ('term', 'hup', 'timeout')] + [(tool, 'deadline') for tool in ('kotlinc', 'javac')]
    for tool, fault in pairs:
        case(evidence, tool, fault)
    print('PASS enum cleanup lifecycle cases=' + str(len(pairs)) + ' evidence=' + str(evidence.relative_to(ROOT)))


if __name__ == '__main__':
    main()
