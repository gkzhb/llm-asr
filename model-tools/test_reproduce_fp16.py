#!/usr/bin/env python3
"""Production CLI orchestration tests with real tiny child processes, NOT numeric proof."""
import contextlib
import ctypes
import io
import json
import os
from pathlib import Path
import select
import signal
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import reproduce_fp16 as export
from fp16_process import cancellation_signals, owned_processes, process_inventory, run_bounded

FAKE = r'''#!PYTHON
import hashlib,json,os,pathlib,sys
args=sys.argv[1:]
if args and args[0].endswith('.py'): script=pathlib.Path(args.pop(0)).name
elif '-f' in args: script='converter'
else: script='probe'
def option(name):return pathlib.Path(args[args.index(name)+1])
def write(p,data):p.write_text(json.dumps(data))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
mode=os.environ.get('FP16_TEST_MODE','')
if mode=='child-fail' and script=='check_prompt.py':sys.exit(7)
if script=='check_audio_parity.py':
 rows=[dict(frames=n,finite=True,max_abs=0) for n in (20,99,100,101,800,801)]
 if mode=='bad-python':rows[0]['max_abs']=0.1
 write(option('--output'),{'results':rows})
elif script=='check_prompt.py':write(option('--output'),[{'equal':True}]*3)
elif script=='onnx_audio_gate.py':
 root=option('--directory')
 if args[0]=='export':(root/'audio.onnx').write_bytes(b'new-onnx')
 else:
  rows=[dict(frames=n,finite=True,passed=True,max_abs=0,relative_l2=0) for n in (801,20,99,100,101,800,801)]
  write(option('--output'),{'onnx_sha256':sha(root/'audio.onnx') if mode!='stale-onnx' else '0'*64,'results':rows})
elif script=='converter':
 graph=option('--MNNModel');graph.write_bytes(b'new-audio');pathlib.Path(str(graph)+'.weight').write_bytes(b'new-weight')
elif script=='mnn_audio_gate.py' and args[0]=='validate':
 graph=option('--model');rows=[dict(frames=n,finite=True,passed=True,max_abs=0.001,relative_l2=0.01) for n in (20,99,100,101,800,801)]
 assets=[dict(file=p.name,bytes=p.stat().st_size,sha256=sha(p)) for p in [graph,pathlib.Path(str(graph)+'.weight')]]
 if mode=='stale-native':assets[0]['sha256']='0'*64
 write(option('--output'),{'results':rows,'artifacts':assets})
elif script=='convert_fp16.py':
 root=option('--output');evidence=option('--evidence')
 for name in ['llm.mnn','llm.mnn.weight','tokenizer.txt','llm_config.json','config.json']:(root/name).write_bytes(name.encode())
 names=['audio.mnn','audio.mnn.weight','llm.mnn','llm.mnn.weight','tokenizer.txt','llm_config.json','config.json']
 rows=[dict(file=n,bytes=(root/n).stat().st_size,sha256=sha(root/n)) for n in names]
 if mode=='stale-assets':rows[-1]['sha256']='0'*64
 write(evidence/'exported-assets.json',{'files':rows})
'''


class ReproductionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='fp16 interface spaces ')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        model = self.root / 'raw';model.mkdir()
        for name in ('config.json', 'model.safetensors'):
            (model / name).write_bytes(b'fixture')
        source = self.root / 'source'
        for name in ('utils/audio.py', 'llmexport.py'):
            path = source / 'transformers/llm/export' / name
            path.parent.mkdir(parents=True, exist_ok=True);path.write_text('fixture')
        self.tool = self.root / 'fake tool'
        self.tool.write_text(FAKE.replace('PYTHON', sys.executable));self.tool.chmod(0o700)
        self.argv = ['--model', str(model), '--source', str(source), '--converter', str(self.tool),
                     '--audio-probe', str(self.tool), '--python', str(self.tool),
                     '--output', str(self.root / 'output'), '--evidence', str(self.root / 'evidence')]

    def run_cli(self, mode=''):
        previous = os.environ.get('FP16_TEST_MODE')
        os.environ['FP16_TEST_MODE'] = mode
        try:
            with contextlib.redirect_stderr(io.StringIO()):
                return export.main(self.argv)
        finally:
            if previous is None:os.environ.pop('FP16_TEST_MODE', None)
            else:os.environ['FP16_TEST_MODE'] = previous

    def receipt(self):
        return json.loads((self.root / 'evidence/reproduction.json').read_text())

    def test_real_child_order_paths_and_complete_assets(self):
        self.assertEqual(self.run_cli(), 0)
        data = self.receipt()
        self.assertEqual(data['status'], 'PASS')
        self.assertEqual([r['name'] for r in data['stages']],
                         ['python-parity', 'prompt-parity', 'onnx-export', 'ort-validate',
                          'encoder-convert', 'native-prepare', 'native-run', 'native-validate', 'decoder-export'])
        self.assertTrue(all(r['exit'] == 0 and r['status'] == 'PASS' and r['log_sha256'] for r in data['stages']))
        self.assertEqual(len(data['assets']), 7)
        self.assertIn(str(self.root / 'output/audio.mnn'), data['stages'][4]['argv'])
        self.assertIn('--transformerFuseC4=0', data['stages'][4]['argv'])
        self.assertFalse((self.root / 'output/exported-assets.json').exists())
        self.assertEqual(self.run_cli(), 1)  # same directory cannot be run again

    def assert_failed_stage(self, mode, stage):
        self.assertEqual(self.run_cli(mode), 1)
        data = self.receipt();self.assertEqual(data['status'], 'FAILED')
        index = next(i for i, row in enumerate(data['stages']) if row['name'] == stage)
        self.assertEqual(data['stages'][index]['status'], 'FAILED')
        self.assertTrue(all(r['status'] == 'NOT_RUN' and r['exit'] is None for r in data['stages'][index + 1:]))
        return data['stages'][index]

    def test_nonzero_child_stops_and_retains_previous_evidence(self):
        row = self.assert_failed_stage('child-fail', 'prompt-parity')
        self.assertEqual(row['exit'], 7)
        self.assertTrue((self.root / 'evidence/audio-python-parity.json').is_file())
        self.assertFalse((self.root / 'evidence/encoder/audio.onnx').exists())

    def test_zero_exit_numeric_gate_failure_stops(self):
        row = self.assert_failed_stage('bad-python', 'python-parity')
        self.assertEqual(row['exit'], 0)
        self.assertIn('gate', row['failure'])

    def test_stale_onnx_binding_rejected(self):
        self.assert_failed_stage('stale-onnx', 'ort-validate')

    def test_stale_native_binding_rejected_before_decoder(self):
        self.assert_failed_stage('stale-native', 'native-validate')
        self.assertFalse((self.root / 'output/llm.mnn').exists())

    def test_stale_final_manifest_rejected(self):
        self.assert_failed_stage('stale-assets', 'decoder-export')

    def test_existing_directory_is_not_modified(self):
        output = self.root / 'output';output.mkdir();sentinel = output / 'user';sentinel.write_bytes(b'keep')
        self.assertEqual(self.run_cli(), 1)
        self.assertEqual(sentinel.read_bytes(), b'keep')
        self.assertFalse((self.root / 'evidence').exists())

    def test_overlap_and_symlink_refused(self):
        self.argv[self.argv.index('--output') + 1] = str(self.root / 'raw/new')
        self.assertEqual(self.run_cli(), 1)
        self.argv[self.argv.index('--output') + 1] = str(self.root / 'output')
        (self.root / 'output').symlink_to(self.root / 'missing')
        self.assertEqual(self.run_cli(), 1)

    def test_dry_run_is_not_execution(self):
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            self.assertEqual(export.main(self.argv + ['--dry-run']), 0)
        data = json.loads(stream.getvalue());self.assertEqual(data['status'], 'NOT_RUN')
        self.assertTrue(all(row['status'] == 'NOT_RUN' for row in data['stages']))
        self.assertFalse((self.root / 'output').exists());self.assertFalse((self.root / 'evidence').exists())

    def test_python_venv_symlink_path_preserved(self):
        link = self.root / 'venv/bin/python';link.parent.mkdir(parents=True);link.symlink_to(self.tool)
        self.argv[self.argv.index('--python') + 1] = str(link)
        self.assertEqual(self.run_cli(), 0)
        self.assertEqual(self.receipt()['stages'][0]['argv'][0], str(link))

    def test_bounds_are_not_silently_loosened(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(export.main(self.argv + ['--rss-mib', '9217']), 1)
        self.assertFalse((self.root / 'evidence').exists())

    def test_recursive_inventory_includes_detached_descendant(self):
        inventory = {10: (1, 10, 100, 'S', 'a'), 11: (10, 11, 200, 'S', 'b'),
                     12: (11, 11, 300, 'S', 'c'), 99: (1, 99, 999, 'S', 'd')}
        self.assertEqual(owned_processes(10, inventory), {10, 11, 12})

    def test_real_rss_failure_and_orphaned_child_cleanup(self):
        env = dict(os.environ)
        rss = run_bounded([sys.executable, '-c', 'import time;x=bytearray(20000000);time.sleep(10)'],
                          self.root / 'rss.log', 2, 1, env)
        self.assertEqual(rss['failure'], 'RSS budget exceeded')
        script = 'import subprocess,sys,time;subprocess.Popen([sys.executable,"-c","import time;time.sleep(10)"]);time.sleep(0.1)'
        orphan = run_bounded([sys.executable, '-c', script], self.root / 'orphan.log', 2, 10**9, env)
        self.assertEqual(orphan['failure'], 'child exited with live descendants')
        self.assertEqual(orphan['status'], 'FAILED')

    def test_signal_after_popen_before_first_sample_and_handler_restoration(self):
        previous = {sig: signal.getsignal(sig) for sig in (signal.SIGINT, signal.SIGTERM)}
        real_popen = subprocess.Popen
        def launch(*args, **kwargs):
            process = real_popen(*args, **kwargs)
            os.kill(os.getpid(), signal.SIGTERM)
            return process
        with mock.patch('fp16_process.subprocess.Popen', side_effect=launch):
            record = run_bounded([sys.executable, '-c', 'import time;time.sleep(10)'],
                                 self.root / 'spawn-cancel.log', 2, 10**9, dict(os.environ))
        self.assertEqual(record['status'], 'CANCELLED')
        self.assertTrue(record['started']);self.assertIsInstance(record['exit'], int)
        self.assertTrue(record['cleanup']['direct_child_reaped'])
        self.assertEqual(previous, {sig: signal.getsignal(sig) for sig in previous})

    def test_signal_queued_during_finalizer_and_failure_restores_handlers(self):
        previous = {sig: signal.getsignal(sig) for sig in (signal.SIGINT, signal.SIGTERM)}
        observed = []
        def finalize(cancellation):
            observed.append(cancellation.signum)
            if len(observed) == 1:os.kill(os.getpid(), signal.SIGINT)
        with cancellation_signals(finalize) as cancellation:
            pass
        self.assertEqual(cancellation.signum, signal.SIGINT)
        self.assertEqual(observed, [None, signal.SIGINT])
        def broken(cancellation):raise OSError('terminal disk failed')
        with self.assertRaises(OSError), cancellation_signals(broken):
            pass
        self.assertEqual(previous, {sig: signal.getsignal(sig) for sig in previous})

    def test_signal_during_terminal_write_is_recorded(self):
        original = export.save;sent = False
        def terminal_save(path, data):
            nonlocal sent
            original(path, data)
            if data['status'] == 'PASS' and not sent:
                sent = True;os.kill(os.getpid(), signal.SIGINT)
        with mock.patch.object(export, 'save', side_effect=terminal_save):
            self.assertEqual(self.run_cli(), 130)
        self.assertTrue(sent);self.assertEqual(self.receipt()['status'], 'CANCELLED')
        self.assertTrue(all(row['status']=='PASS' for row in self.receipt()['stages']))

    def test_missing_executable_and_timeout_record_real_failure(self):
        env = dict(os.environ)
        missing = run_bounded([str(self.root / 'missing')], self.root / 'missing.log', 1, 10**9, env)
        self.assertIsNone(missing['exit']);self.assertEqual(missing['status'], 'FAILED')
        timed = run_bounded([sys.executable, '-c', 'import time;time.sleep(10)'],
                            self.root / 'timeout.log', 0.05, 10**9, env)
        self.assertEqual(timed['failure'], 'timeout');self.assertNotEqual(timed['exit'], 0)


class CancellationCliTest(unittest.TestCase):
    """External signals/readiness and survival assertions; no model library loaded."""
    def test_external_term_and_int_terminal_receipts(self):
        for sig in (signal.SIGTERM, signal.SIGINT):
            with self.subTest(signal=signal.Signals(sig).name):
                self.check_cancellation(sig)

    def test_repeated_signal_during_real_child_cleanup(self):
        self.check_cancellation(signal.SIGTERM, repeated=True)

    def check_cancellation(self, sig, repeated=False):
        libc = ctypes.CDLL(None)
        previous = ctypes.c_int()
        self.assertEqual(libc.prctl(37, ctypes.byref(previous), 0, 0, 0), 0)
        self.assertEqual(libc.prctl(36, 1, 0, 0, 0), 0)
        self.addCleanup(lambda: libc.prctl(36, previous.value, 0, 0, 0))
        with tempfile.TemporaryDirectory(prefix='fp16 cli cancel ') as temporary:
            root = Path(temporary);model = root / 'raw';model.mkdir()
            source = root / 'source/transformers/llm/export/utils';source.mkdir(parents=True)
            for path in (model / 'config.json', model / 'model.safetensors',
                         source / 'audio.py', source.parent / 'llmexport.py'):
                path.write_bytes(b'tiny fixture')
            tool = root / 'ready child'
            tool.write_text('#!' + sys.executable + '\nimport os,json,time,signal\n'
                            'fd=os.open(os.environ["FP16_READY_FIFO"],os.O_WRONLY)\n'
                            + ('signal.signal(signal.SIGTERM,lambda *a:os.write(fd,b"CLEANUP\\n"))\n' if repeated else '')
                            + 'os.write(fd,json.dumps({"pid":os.getpid()}).encode()+b"\\n")\n'
                            'time.sleep(20)\n')
            tool.chmod(0o700)
            entry = Path(os.environ.get('FP16_CANCEL_ENTRY', export.__file__))
            argv = [sys.executable, '-B', str(entry), '--model', str(model), '--source', str(source.parents[3]),
                    '--converter', str(tool), '--audio-probe', str(tool), '--python', str(tool),
                    '--output', str(root / 'output'), '--evidence', str(root / 'evidence')]
            fifo = root / 'ready.fifo';os.mkfifo(fifo)
            env = dict(os.environ, FP16_READY_FIFO=str(fifo))
            fd = os.open(fifo, os.O_RDONLY | os.O_NONBLOCK)
            child = None;process = None
            try:
                with (root / 'cli.log').open('wb') as stream:
                    process = subprocess.Popen(argv, stdout=stream, stderr=subprocess.STDOUT,
                                               env=env, start_new_session=True)
                    ready, _, _ = select.select([fd], [], [], 5)
                    self.assertTrue(ready, 'child readiness timeout, not target red')
                    data = os.read(fd, 4096);self.assertTrue(data, 'no actual child readiness')
                    child = json.loads(data)['pid'];identity = process_inventory()[child][4]
                    self.assertNotEqual(process_inventory()[child][3], 'Z')
                    os.kill(process.pid, sig)
                    if repeated:
                        ready, _, _ = select.select([fd], [], [], 3)
                        self.assertTrue(ready, 'child did not observe cleanup TERM')
                        self.assertIn(b'CLEANUP', os.read(fd, 4096))
                        os.kill(process.pid, signal.SIGINT)
                        os.kill(process.pid, signal.SIGTERM)
                    code = process.wait(timeout=5)
                inventory = process_inventory();live = child in inventory and inventory[child][4] == identity and inventory[child][3] != 'Z'
                receipt = json.loads((root / 'evidence/reproduction.json').read_text())
                row = receipt['stages'][0]
                observed = {'signal':signal.Signals(sig).name,'actual_cli_exit':code,
                            'child_pid':child,'child_alive_before_emergency_cleanup':live,
                            'receipt_status':receipt['status'],'stage_status':row['status'],
                            'stage_started':row.get('started'),'stage_exit':row.get('exit'),
                            'stage_log_sha256':row.get('log_sha256'),'cleanup':row.get('cleanup')}
                evidence = os.environ.get('FP16_CANCEL_EVIDENCE')
                if evidence:
                    destination = Path(evidence);destination.mkdir(parents=True,exist_ok=True)
                    (destination / (signal.Signals(sig).name + ('-repeated' if repeated else '') + '.json')).write_text(json.dumps(observed,indent=2)+'\n')
                    (destination / (signal.Signals(sig).name + ('-repeated' if repeated else '') + '.receipt.json')).write_text(json.dumps(receipt,indent=2)+'\n')
                # These assertions occur after live-state observation, before finally emergency cleanup.
                self.assertFalse(live, observed)
                self.assertEqual(code, 128 + sig, observed)
                self.assertEqual(receipt['status'], 'CANCELLED', observed)
                self.assertEqual(row['status'], 'CANCELLED', observed)
                self.assertTrue(row['started'], observed)
                self.assertIsInstance(row['exit'], int, observed)
                self.assertEqual(row['log_sha256'], export.digest(root / 'evidence/python-parity.log'))
                self.assertTrue(row['cleanup']['direct_child_reaped'])
                self.assertTrue(all(r['status']=='NOT_RUN' and not r.get('started',False) for r in receipt['stages'][1:]))
            finally:
                os.close(fd)
                if process is not None and process.poll() is None:
                    os.killpg(process.pid, signal.SIGKILL);process.wait(timeout=5)
                if child is not None:
                    try:os.killpg(child, signal.SIGKILL)
                    except ProcessLookupError:pass
                    try:os.waitpid(child, 0)
                    except ChildProcessError:pass


if __name__ == '__main__':
    unittest.main(verbosity=2)
