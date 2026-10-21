#!/usr/bin/env python3
"""Narrow Linux owner for PartRecovery/model-review child IO; not a general runner.

Reuse the reviewed picker session/pidfd/subreaper implementation unchanged.
A dependency lease survives any unconfirmed exit so host cleanup fails closed.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import select
import signal
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
_spec = importlib.util.spec_from_file_location('_mixed_owned_picker', ROOT / 'tests/logs_activity_picker_test.py')
owned = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(owned)
UNCONFIRMED = 'MIXED_EXIT_UNCONFIRMED.json'


def guard(directory, reservation=None):
    directory = Path(directory)
    markers = [p for p in directory.glob('.mixed-lease-*.json') if p != reservation]
    markers += list(directory.glob(UNCONFIRMED))
    if markers:
        raise owned.CleanupRefused('mixed exit unconfirmed; refuse cleanup/reuse: ' + str(directory))


def safe_remove(directory):
    directory = Path(directory)
    guard(directory)
    if directory.exists():
        import shutil
        shutil.rmtree(directory)


def run(argv, work, label, timeout, *, dependency=None, cancel=None, watch_stdin=False, reservation=None, dependency_reservation=None):
    """Hold input/dependency ownership until the reused runner confirms all exits."""
    work = Path(work).resolve()
    work.mkdir(parents=True, exist_ok=True)
    reservation = Path(reservation).resolve() if reservation is not None else None
    if reservation is not None and (reservation.parent != work or not reservation.name.startswith('.mixed-lease-') or not reservation.is_file()):
        raise RuntimeError('invalid Java ownership reservation')
    guard(work, reservation)
    dependency = Path(dependency).resolve() if dependency is not None else None
    dependency_reservation = Path(dependency_reservation).resolve() if dependency_reservation is not None else None
    if dependency_reservation is not None and (dependency is None or dependency_reservation.parent != dependency or not dependency_reservation.name.startswith('.mixed-lease-') or not dependency_reservation.is_file()):
        raise RuntimeError('invalid dependency reservation')
    if dependency is not None:
        guard(dependency, dependency_reservation)
    token = '.mixed-lease-' + uuid.uuid4().hex + '.json'
    leases = [work / token]
    if dependency is not None and dependency != work:
        leases.append(dependency / token)
    for lease in leases:
        owned.write_json(lease, {'supervisor': owned.process_identity(os.getpid()),
                                 'work': str(work), 'label': label, 'state': 'running'})
    interrupted = []
    previous = {}

    def interrupted_signal(number, _frame):
        # Let the reused execute finally complete; do not kill its direct shell.
        interrupted.append(number)

    def checkpoint():
        if interrupted:
            raise InterruptedError('mixed caller received signal ' + str(interrupted[0]))
        if cancel is not None and Path(cancel).exists():
            raise InterruptedError('mixed Java caller cancelled')
        if watch_stdin and select.select([sys.stdin.buffer], [], [], 0)[0]:
            if os.read(sys.stdin.fileno(), 1) == b'':
                raise InterruptedError('mixed Java parent pipe closed')

    for number in (signal.SIGTERM, signal.SIGHUP, signal.SIGINT):
        previous[number] = signal.signal(number, interrupted_signal)
    record = work / (label + '.json')
    try:
        return owned.execute(argv, work, label, timeout, interrupt=checkpoint)
    finally:
        # A dead supervisor / unreadable record never authorizes cleanup.
        confirmed = False
        try:
            state = json.loads(record.read_text())
            confirmed = state.get('owned_exited') is True and state.get('root_reaped') is True
        except (OSError, ValueError):
            confirmed = False
        finally:
            if confirmed:
                for lease in leases:
                    lease.unlink()
                if reservation is not None:
                    reservation.unlink()
                if dependency_reservation is not None:
                    dependency_reservation.unlink()
            else:
                owned.write_json(work / UNCONFIRMED, {'label': label, 'leases': list(map(str, leases)),
                                                       'reason': 'owned exit not confirmed; preserve inputs/dependencies'})
            for number, handler in previous.items():
                signal.signal(number, handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    clean = commands.add_parser('safe-remove')
    clean.add_argument('directory', type=Path)
    check = commands.add_parser('guard')
    check.add_argument('directory', type=Path)
    child = commands.add_parser('run')
    child.add_argument('--work', required=True, type=Path)
    child.add_argument('--label', required=True)
    child.add_argument('--timeout', required=True, type=float)
    child.add_argument('--dependency', type=Path)
    child.add_argument('--cancel', type=Path)
    child.add_argument('--watch-stdin', action='store_true')
    child.add_argument('--reservation', type=Path)
    child.add_argument('--dependency-reservation', type=Path)
    child.add_argument('argv', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if args.action == 'safe-remove':
        safe_remove(args.directory)
        return
    if args.action == 'guard':
        guard(args.directory)
        return
    argv = args.argv[1:] if args.argv[:1] == ['--'] else args.argv
    if not argv:
        parser.error('child argv required')
    result = run(argv, args.work, args.label, args.timeout, dependency=args.dependency,
                 cancel=args.cancel, watch_stdin=args.watch_stdin, reservation=args.reservation,
                 dependency_reservation=args.dependency_reservation)
    sys.stdout.buffer.write(result.stdout)
    sys.stderr.buffer.write(result.stderr)
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
