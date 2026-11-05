#!/usr/bin/env python3
"""Linux-only owned subprocess bounds for offline model conversion (not a sandbox)."""
from contextlib import contextmanager
import hashlib
import os
from pathlib import Path
import signal
import subprocess
import time


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def process_inventory():
    """PID -> (PPID, group, RSS, state, start ticks); tolerate concurrent exits."""
    result = {}
    for path in Path('/proc').glob('[0-9]*/stat'):
        try:
            fields = path.read_text().rsplit(')', 1)[1].split()
            result[int(path.parent.name)] = (int(fields[1]), int(fields[2]),
                                             int(fields[21]) * os.sysconf('SC_PAGE_SIZE'), fields[0], fields[19])
        except (OSError, ValueError, IndexError):
            continue
    return result


def owned_processes(pid, inventory):
    owned = {pid}
    while True:
        previous = len(owned)
        owned.update(key for key, info in inventory.items()
                     if info[0] in owned or info[1] == pid)
        if previous == len(owned):
            return {key for key in owned if key in inventory and inventory[key][3] != 'Z'}


class Cancellation:
    """Only latch ordinary TERM/INT; repeated signals cannot unwind owned cleanup."""
    def __init__(self):
        self.signum = None

    def receive(self, signum, frame):
        if self.signum is None:
            self.signum = signum


@contextmanager
def cancellation_signals(finalize=None):
    cancellation = Cancellation()
    previous = {}
    try:
        for sig in (signal.SIGTERM, signal.SIGINT):
            previous[sig] = signal.signal(sig, cancellation.receive)
        yield cancellation
    finally:
        # Close the handler-restoration/final-write window while all owned work is stopped.
        blocked = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM, signal.SIGINT})
        try:
            pending = signal.sigpending() & {signal.SIGTERM, signal.SIGINT}
            for sig in pending:
                signal.sigwait({sig})
                cancellation.receive(sig, None)
            if finalize:
                finalize(cancellation)
            # A signal queued during serialization must also terminalize before handlers restore.
            pending = signal.sigpending() & {signal.SIGTERM, signal.SIGINT}
            for sig in pending:
                signal.sigwait({sig})
                cancellation.receive(sig, None)
            if finalize and pending:
                finalize(cancellation)
        finally:
            for sig, handler in previous.items():
                signal.signal(sig, handler)
            signal.pthread_sigmask(signal.SIG_SETMASK, blocked)


def signal_if_present(sender, identity, sig):
    try:
        sender(identity, sig)
    except ProcessLookupError:
        pass


def stop_owned(process, identities):
    """Bounded direct wait and observed PID-start cleanup; NOT arbitrary descendant reaping."""
    for sig in (signal.SIGTERM, signal.SIGKILL):
        current = process_inventory()
        live = {pid for pid, start in identities.items()
                if pid in current and current[pid][4] == start}
        # Popen handle binds an unreaped direct child even before its first /proc sample.
        if process.poll() is None:
            signal_if_present(os.killpg, process.pid, sig)
            signal_if_present(os.kill, process.pid, sig)
        elif any(current[pid][1] == process.pid for pid in live):
            signal_if_present(os.killpg, process.pid, sig)
        for pid in live:
            signal_if_present(os.kill, pid, sig)
        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            continue
    current = process_inventory()
    survivors = [pid for pid, start in identities.items()
                 if pid in current and current[pid][4] == start and current[pid][3] != 'Z']
    reaped = process.poll() is not None
    return {'direct_child_reaped': reaped, 'observed_live_pids': survivors,
            'confirmed_observed_terminal': reaped and not survivors,
            'boundary': 'direct child reaped + sampled PID-start identities only; not all descendants'}


def run_bounded(argv, log, seconds, rss_bytes, env, cancellation=None, update=None):
    """Real execution, launch ownership and terminal evidence; no success inferred from cancellation."""
    if cancellation is None:
        with cancellation_signals() as current:
            return run_bounded(argv, log, seconds, rss_bytes, env, current, update)
    start = time.monotonic()
    record = {'argv': list(map(str, argv)), 'status': 'RUNNING', 'started': False, 'exit': None,
              'timeout_seconds': seconds, 'rss_limit_bytes': rss_bytes,
              'peak_owned_tree_rss_bytes': 0, 'failure': None}
    process = None
    identities = {}
    with Path(log).open('xb') as stream:
        try:
            if not cancellation.signum:
                # Handler never throws during Popen: preserve direct handle before first sample.
                process = subprocess.Popen(record['argv'], stdout=stream, stderr=subprocess.STDOUT,
                                           start_new_session=True, env=env)
            if process is not None:
                record.update(started=True, pid=process.pid)
                launch = process_inventory().get(process.pid)
                record['pid_start_ticks'] = launch[4] if launch else None
                identities[process.pid] = record['pid_start_ticks']
                if update:
                    update(record)
                supervise(process, start, seconds, rss_bytes, record, identities, cancellation)
        except OSError as error:
            record.update(status='FAILED', failure=type(error).__name__ + ': ' + str(error))
        finally:
            if process is not None:
                record['cleanup'] = stop_owned(process, identities)
                record['exit'] = process.poll()
    if cancellation.signum:
        record.update(status='CANCELLED', signal=cancellation.signum, failure='catchable cancellation')
    elif record['exit'] == 0 and record['failure'] is None:
        record['status'] = 'PASS'
    else:
        record['status'] = 'FAILED'
    if process is not None and not record['cleanup']['confirmed_observed_terminal']:
        record.update(status='FAILED', failure='cleanup uncertain; retain ownership and inputs')
    record['seconds'] = time.monotonic() - start
    record['log_sha256'] = digest(log)
    return record


def supervise(process, start, seconds, rss_limit, record, identities, cancellation):
    """Sampling bounds ordinary owned work, not a universal detached-process sandbox."""
    while process.poll() is None:
        inventory = process_inventory()
        owned = owned_processes(process.pid, inventory)
        identities.update({pid: inventory[pid][4] for pid in owned})
        rss = sum(inventory[pid][2] for pid in owned)
        record['peak_owned_tree_rss_bytes'] = max(record['peak_owned_tree_rss_bytes'], rss)
        if cancellation.signum or time.monotonic() - start > seconds or rss > rss_limit:
            record['failure'] = 'catchable cancellation' if cancellation.signum else 'timeout' if rss <= rss_limit else 'RSS budget exceeded'
            return
        try:
            process.wait(timeout=0.25)
        except subprocess.TimeoutExpired:
            continue
    inventory = process_inventory()
    survivors = owned_processes(process.pid, inventory)
    survivors.update(pid for pid, stamp in identities.items()
                     if pid in inventory and inventory[pid][4] == stamp and inventory[pid][3] != 'Z')
    if survivors:
        identities.update({pid: inventory[pid][4] for pid in survivors})
        record['failure'] = 'child exited with live descendants'
