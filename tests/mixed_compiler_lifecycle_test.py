#!/usr/bin/env python3
"""Actual two callers + unchanged wrapper, synthetic bounded Linux compiler faults."""
import argparse
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
sys.path.insert(0, str(ROOT / 'tests'))
import mixed_compiler_lifecycle as life


def check(value, message):
    if not value:
        raise AssertionError(message)


def wait_for(predicate, seconds=5):
    end = time.monotonic() + seconds
    while not predicate():
        check(time.monotonic() < end, 'bounded fixture observation timeout')
        time.sleep(.02)


def active(identity):
    current = life.owned.process_identity(identity['pid'])
    return current is not None and current['start'] == identity['start'] and current['state'] not in ('Z', 'X')


def emergency(directory):
    # Independent emergency finalizer runs AFTER the externally recorded assertion.
    for p in directory.rglob('worker.json'):
        identity = json.loads(p.read_text())
        if active(identity):
            fd = os.pidfd_open(identity['pid'])
            try:
                signal.pidfd_send_signal(fd, signal.SIGKILL)
            finally:
                os.close(fd)
            wait_for(lambda: not active(identity))
        try:
            os.waitpid(identity['pid'], os.WNOHANG)
        except ChildProcessError:
            pass
    # Reap any adopted launcher/timeout descendants after the held worker exits.
    sessions = set()
    for p in directory.rglob('*.json'):
        value = json.loads(p.read_text())
        if isinstance(value, dict) and value.get('runner') == 'owned-linux-session-v1' and 'session' in value:
            sessions.add(value['session'])
    for session in sessions:
        deadline = time.monotonic() + 3
        while life.owned.session_members(session):
            check(time.monotonic() < deadline, 'emergency ordinary descendants failed to reap')
            for member in life.owned.session_members(session):
                if member['state'] not in ('Z', 'X'):
                    try:
                        fd = os.pidfd_open(member['pid'])
                        try:
                            signal.pidfd_send_signal(fd, signal.SIGKILL)
                        finally:
                            os.close(fd)
                    except ProcessLookupError:
                        pass
                try:
                    os.waitpid(member['pid'], os.WNOHANG)
                except ChildProcessError:
                    pass
            time.sleep(.02)


FAKE = '''#!/usr/bin/env python3
import os,sys,time,json,signal,subprocess
from pathlib import Path
base=Path(os.environ['FAULT_DIRECTORY'])
if '--worker' in sys.argv:
 os.setpgid(0,0)
 signal.signal(signal.SIGTERM,signal.SIG_IGN)
 fields=Path('/proc/self/stat').read_text().rsplit(')',1)[1].split()
 (base/'worker.json').write_text(json.dumps({'pid':os.getpid(),'start':int(fields[19]),'pgid':int(fields[2]),'session':int(fields[3])}))
 while True:
  (base/'heartbeat').write_text(str(time.monotonic()))
  time.sleep(.02)
if Path(sys.argv[0]).name=='java':
 print('readiness listener lock regression CancellationException: Private account late reentrant cancel must not overwrite terminal')
 sys.exit(1)
if Path(sys.argv[0]).name=='javac': sys.exit(0)
p=subprocess.Popen([sys.executable,__file__,'--worker'],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
while not (base/'worker.json').exists(): time.sleep(.01)
if os.environ.get('FAULT_MODE')=='parent-first': sys.exit(0)
p.wait()
'''


HARNESS = '''import java.io.*;import java.util.*;
public class LifecycleProbe {
 public static void main(String[] a)throws Exception {
  File work=new File(a[0]);work.mkdirs();
  List<String> cmd=Arrays.asList("bash","scripts/compile-jvm-sources.sh",new File(work,"out").getPath(),a[1],a[2]);
  if(a.length>3 && a[3].equals("interrupt")) {
   Thread owner=Thread.currentThread();Thread t=new Thread(()->{try{Thread.sleep(300);owner.interrupt();}catch(Exception e){throw new RuntimeException(e);}});t.start();
  }
  CALL
 }
}
'''


def compiler_environment(directory, mode):
    tools = directory / 'tools'
    tools.mkdir()
    for name in ('kotlinc', 'javac', 'java'):
        p = tools / name
        p.write_text(FAKE)
        p.chmod(0o755)
    env = dict(os.environ, PATH=str(tools) + ':' + os.environ['PATH'],
               FAULT_DIRECTORY=str(directory), FAULT_MODE=mode)
    return env


def compile_probe(directory, legacy):
    cp = str(ROOT / 'core/build/classes/java/test') + ':' + str(ROOT / 'core/build/classes/kotlin/main') + ':' + str(ROOT / 'core/build/classes/java/main') + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'
    # Core test classpath may be absent during the first host run; host supplies its actual classes.
    cp = os.environ.get('MIXED_LIFECYCLE_CLASSES', cp) + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'
    original = ROOT / '.work/ap5batch-r1-fix/old/tests/PartRecoveryTest.java'
    if legacy:
        check(original.is_file(), 'legacy replay requires the frozen authorized old caller copy')
        source = original.read_text().replace('p.waitFor(20,', 'p.waitFor(1,')
        call = 'Process p=new ProcessBuilder(cmd).inheritIO().start(); PartRecoveryTest.waitBounded(p);'
    else:
        source = (ROOT / 'tests/PartRecoveryTest.java').read_text()
        call = 'PartRecoveryTest.supervised(cmd,work,"probe");'
    production = directory / 'PartRecoveryTest.java'
    production.write_text(source)
    probe = directory / 'LifecycleProbe.java'
    probe.write_text(HARNESS.replace('CALL', call))
    classes = directory / 'probe-classes'
    r = subprocess.run(['javac', '--release', '8', '-cp', cp, '-d', str(classes), str(production), str(probe)], capture_output=True, timeout=30)
    (directory / 'probe-compile.log').write_bytes(r.stdout + r.stderr)
    check(r.returncode == 0, 'actual PartRecovery probe compile must succeed: ' + r.stderr.decode(errors='replace'))
    return str(classes) + ':' + cp


def part_case(base, mode, legacy=False):
    directory = base / ('part-' + mode)
    directory.mkdir()
    directory = directory.resolve()
    cp = compile_probe(directory, legacy)
    env = compiler_environment(directory, 'parent-first' if mode == 'parent-first' else 'hold')
    source = directory / 'Probe.kt'
    source.write_text('class Probe\n')
    dependency = directory / 'dependency'
    dependency.mkdir()
    (dependency / 'keep').write_text('dependency')
    work = directory / 'input'
    java = shutil.which('java')
    cmd = [java, '-DpartRecovery.childSeconds=0.6', '-cp', cp, 'LifecycleProbe', str(work), str(dependency), str(source)]
    if mode == 'interrupt':
        cmd.append('interrupt')
    with (directory / 'caller.log').open('wb') as log:
        process = subprocess.Popen(cmd, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            wait_for(lambda: (directory / 'worker.json').exists())
            worker = json.loads((directory / 'worker.json').read_text())
            if mode == 'term':
                process.terminate()
            process.wait(timeout=8)
            if not legacy:
                wait_for(lambda: not active(worker))
            alive = active(worker)
            # Fixed Part waits after Java exit: this is bounded post-return observation,
            # not proof that the compiler stopped before the caller returned.
            evidence = {'caller_exit': process.returncode, 'worker_alive_before_emergency': alive,
                        'observation_timing': 'after caller exit and bounded worker observation' if not legacy else 'after caller exit before emergency',
                        'worker': worker, 'input_exists': work.exists(), 'dependency_exists': dependency.exists()}
            (directory / 'observed.json').write_text(json.dumps(evidence, indent=2) + '\n')
            check(not alive, 'actual PartRecovery returned while a different-PGID compiler still uses inputs')
            check(worker['pgid'] != worker['session'], 'fault must exercise a different PGID')
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=3)
            emergency(directory)


def model_case(base, mode, legacy=False):
    directory = base / ('model-' + mode)
    directory.mkdir()
    directory = directory.resolve()
    env = compiler_environment(directory, 'parent-first' if mode == 'parent-first' else 'hold')
    classes = directory / 'dependency'
    classes.mkdir()
    (classes / 'keep').write_text('dependency')
    env.update(MODEL_REVIEW_CLASSES=str(classes), MODEL_REVIEW_MUTATION_OUT=str(directory / 'mutants'))
    script = ROOT / 'tests/model_review_mutation_test.py'
    if legacy:
        script = ROOT / '.work/ap5batch-r1-fix/old/tests/model_review_mutation_test.py'
    # Execute the real caller with only the deadline value shortened; no compiler/runner clone.
    if legacy:
        text = script.read_text().replace('timeout=270', 'timeout=.6')
    else:
        text = script.read_text().replace("work, 'compile', 15,", "work, 'compile', .6,")
    text = text.replace('ROOT = Path(__file__).resolve().parents[1]', 'ROOT = Path(' + repr(str(ROOT)) + ')')
    copied = directory / 'caller.py'
    copied.write_text(text)
    env['PYTHONPATH'] = str(ROOT / 'tests')
    cmd = [sys.executable, '-B', str(copied)]
    if mode == 'unconfirmed':
        launcher = directory / 'unconfirmed-launcher.py'
        launcher.write_text('import runpy\nimport mixed_compiler_lifecycle as life\n'
                            'life.owned.terminate_owned=lambda *a: False\n'
                            'runpy.run_path(' + repr(str(copied.resolve())) + ',run_name="__main__")\n')
        cmd = [sys.executable, '-B', str(launcher)]
    # Real GNU outer timeout, shortened only for the failure fixture.
    if mode == 'outer-timeout':
        cmd = ['timeout', '.4', *cmd]
    with (directory / 'caller.log').open('wb') as log:
        process = subprocess.Popen(cmd, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            wait_for(lambda: (directory / 'worker.json').exists())
            worker = json.loads((directory / 'worker.json').read_text())
            if mode == 'term':
                process.terminate()
            if mode == 'interrupt':
                process.send_signal(signal.SIGINT)
            process.wait(timeout=8)
            alive = active(worker)
            (directory / 'observed.json').write_text(json.dumps({'caller_exit': process.returncode, 'worker_alive_before_emergency': alive, 'worker': worker, 'dependency_exists': classes.exists()}, indent=2) + '\n')
            if mode == 'unconfirmed':
                check(alive, 'unconfirmed fixture must leave an actual compiler alive before emergency cleanup')
                check(list(classes.glob('.mixed-lease-*')), 'actual model caller must retain its dependency lease')
                try:
                    life.safe_remove(classes)
                except life.owned.CleanupRefused:
                    pass
                else:
                    raise AssertionError('host cleanup must refuse live model compiler dependency')
                again = subprocess.run([sys.executable, '-B', str(copied)], cwd=ROOT, env=env, capture_output=True, timeout=3)
                check(again.returncode != 0 and b'refuse cleanup/reuse' in again.stderr, 'next actual model caller must refuse retained dependencies')
            else:
                check(not alive, 'actual model-review returned while a different-PGID compiler still reads dependency classes')
                life.guard(classes)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=3)
            emergency(directory)


def unconfirmed_case(base):
    directory = base / 'unconfirmed'
    directory.mkdir()
    dependency = directory / 'dependency'
    dependency.mkdir()
    work = directory / 'input'
    original_execute = life.owned.execute
    child = None

    def unconfirmed(argv, where, label, timeout, **kwargs):
        nonlocal child
        child = subprocess.Popen([sys.executable, '-c', 'import time;time.sleep(30)'])
        life.owned.write_json(where / (label + '.json'), {'runner': 'owned-linux-session-v1', 'owned_exited': False, 'root_reaped': False})
        raise life.owned.CleanupRefused('synthetic stop observation unavailable')

    life.owned.execute = unconfirmed
    try:
        try:
            life.run(['unused'], work, 'compile', .1, dependency=dependency)
        except life.owned.CleanupRefused:
            pass
        else:
            raise AssertionError('unconfirmed stop must fail')
        check(child.poll() is None, 'fault must hold a real live process before cleanup refusal')
        for path in (work, dependency):
            try:
                life.safe_remove(path)
            except life.owned.CleanupRefused:
                pass
            else:
                raise AssertionError('unconfirmed dependency/input cleanup must refuse')
        # The real Java entry also refuses the retained fixed directory before writing inputs.
        cp = compile_probe(directory, False)
        probe = directory / 'RetryProbe.java'
        probe.write_text('public class RetryProbe { public static void main(String[] a)throws Exception { PartRecoveryTest.runRedEvidenceAgainstBuggyCopy(); } }\n')
        built = subprocess.run(['javac', '--release', '8', '-cp', cp, '-d', str(directory / 'probe-classes'), str(probe)], capture_output=True, timeout=30)
        check(built.returncode == 0, 'actual Java retry probe must compile')
        retried = subprocess.run([shutil.which('java'), '-DpartRecovery.work=' + str(work), '-cp', cp, 'RetryProbe'], cwd=ROOT, capture_output=True, timeout=3)
        check(retried.returncode != 0 and b'refuse recovery directory reuse' in retried.stderr, 'next actual PartRecovery call must refuse retained live inputs')
        # Recovery happens only after independent actual exit proof, not marker deletion while alive.
        child.kill()
        child.wait(timeout=3)
        for path in (work, dependency):
            for marker in list(path.glob('.mixed-lease-*')) + list(path.glob(life.UNCONFIRMED)):
                marker.unlink()
            life.safe_remove(path)
    finally:
        life.owned.execute = original_execute
        if child is not None and child.poll() is None:
            child.kill()
            child.wait(timeout=3)


def cleanup_case(base, legacy=False):
    directory = (base / ('cleanup-old' if legacy else 'cleanup-fixed')).resolve()
    directory.mkdir(parents=True)
    cp = str(ROOT / 'core/build/classes/java/test') + ':' + str(ROOT / 'core/build/classes/kotlin/main') + ':' + str(ROOT / 'core/build/classes/java/main') + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'
    source = ROOT / ('.work/ap5batch-cleanup-fix/old/tests/PartRecoveryTest.java' if legacy else 'tests/PartRecoveryTest.java')
    actual = directory / 'PartRecoveryTest.java'
    actual.write_bytes(source.read_bytes())
    work = directory / 'work'
    work.mkdir()
    (work / 'old-input').write_text('preserve until ownership resolved')
    body = '''import java.io.*;import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class CleanupProbe {
 public static void main(String[] a)throws Exception {
  System.setProperty("partRecovery.work",a[0]);
  AtomicReference<Throwable> failure=new AtomicReference<>();
  SETUP
  Thread caller=new Thread(()->{try{PartRecoveryTest.runRedEvidenceAgainstBuggyCopy();}catch(Throwable t){failure.set(t);}});
  caller.start();
  OBSERVE
 }
}
'''
    if legacy:
        body = body.replace('SETUP', '').replace('OBSERVE', '''
  Path marker=Paths.get(a[1]);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(!Files.exists(marker)){if(System.nanoTime()>end)throw new AssertionError("cleanup worker did not start");Thread.sleep(10);}
  caller.interrupt();caller.join(2000);
  if(caller.isAlive() || !(failure.get() instanceof InterruptedException))throw new AssertionError("actual old cleanup interrupt observation missing");
''')
    else:
        body = body.replace('SETUP', '''
  CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
  PartRecoveryTest.cleanupObservation=()->{entered.countDown();boolean interrupted=false;while(true){try{release.await();break;}catch(InterruptedException x){interrupted=true;}}if(interrupted)Thread.currentThread().interrupt();};
''').replace('OBSERVE', '''
  try {
   if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("actual synchronous cleanup did not enter");
   caller.interrupt();
   try {PartRecoveryTest.runRedEvidenceAgainstBuggyCopy();throw new AssertionError("concurrent cleanup reuse accepted");}
   catch(IOException expected){if(!expected.getMessage().contains("still owned"))throw expected;}
   if(!Files.exists(Paths.get(a[0],"old-input")))throw new AssertionError("interrupted active cleanup input lost");
  } finally {release.countDown();caller.join(3000);PartRecoveryTest.cleanupObservation=null;}
  if(caller.isAlive() || !(failure.get() instanceof InterruptedException))throw new AssertionError("synchronous cleanup must unwind interruption before rebuilding");
  if(Files.exists(Paths.get(a[0],"ModelRepository.java")))throw new AssertionError("interrupted cleanup rebuilt inputs");
  Path root=Paths.get(a[0]);Path outside=root.resolveSibling("outside");Files.createDirectories(outside);Files.write(outside.resolve("keep"),new byte[]{1});Files.createSymbolicLink(root.resolve("link"),outside);
  PartRecoveryTest.cleanRecoveryDirectory(root);if(!Files.exists(outside.resolve("keep")))throw new AssertionError("cleanup followed symlink");
  Files.createDirectories(root);Files.write(root.resolve(".mixed-lease-held.json"),new byte[]{1});
  try{PartRecoveryTest.runRedEvidenceAgainstBuggyCopy();throw new AssertionError("retained compiler lease accepted");}catch(IOException expected){if(!expected.getMessage().contains("refuse recovery"))throw expected;}
  Files.delete(root.resolve(".mixed-lease-held.json"));PartRecoveryTest.cleanRecoveryDirectory(root);
  System.out.println("PASS actual synchronous cleanup interruption/lock/no-rebuild/symlink/lease-refusal/recovery");
''')
    probe = directory / 'CleanupProbe.java'
    probe.write_text(body)
    classes = directory / 'classes'
    built = subprocess.run(['javac', '--release', '8', '-cp', cp, '-d', str(classes), str(actual), str(probe)], capture_output=True, timeout=30)
    (directory / 'compile.log').write_bytes(built.stdout + built.stderr)
    check(built.returncode == 0, 'actual cleanup caller must compile: ' + built.stderr.decode(errors='replace'))
    env = dict(os.environ)
    marker = directory / 'worker.json'
    if legacy:
        tools = directory / 'tools'
        tools.mkdir()
        fake = tools / 'python3'
        fake.write_text('#!' + sys.executable + '\nimport os,signal,time,json\nfrom pathlib import Path\nf=os.open(os.devnull,os.O_RDWR);os.dup2(f,1);os.dup2(f,2)\nsignal.signal(signal.SIGTERM,signal.SIG_IGN)\ns=Path("/proc/self/stat").read_text().rsplit(")",1)[1].split()\nPath(' + repr(str(marker)) + ').write_text(json.dumps({"pid":os.getpid(),"start":int(s[19])}))\nwhile True: time.sleep(.02)\n')
        fake.chmod(0o755)
        env['PATH'] = str(tools) + ':' + env['PATH']
    try:
        result = subprocess.run([shutil.which('java'), '-cp', str(classes) + ':' + cp, 'CleanupProbe', str(work), str(marker)], cwd=ROOT, env=env, capture_output=True, timeout=10)
        (directory / 'caller.log').write_bytes(result.stdout + result.stderr)
        check(result.returncode == 0, 'actual cleanup harness failure: ' + result.stderr.decode(errors='replace'))
        alive = legacy and active(json.loads(marker.read_text()))
        (directory / 'observed.json').write_text(json.dumps({'legacy': legacy, 'deletion_child_alive_after_java': alive, 'work_exists': work.exists()}, indent=2) + '\n')
        check(not alive, 'actual interrupted cleanup returned while its deletion child still owns the fixed work tree')
    finally:
        emergency(directory)


def lock_case(base, legacy=False):
    directory = (base / ('lock-old' if legacy else 'lock-fixed')).resolve()
    directory.mkdir(parents=True)
    cp = str(ROOT / 'core/build/classes/java/test') + ':' + str(ROOT / 'core/build/classes/kotlin/main') + ':' + str(ROOT / 'core/build/classes/java/main') + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'
    source = ROOT / ('.work/ap5batch-lock-fix/old/tests/PartRecoveryTest.java' if legacy else 'tests/PartRecoveryTest.java')
    actual = directory / 'PartRecoveryTest.java'
    actual.write_bytes(source.read_bytes())
    probe = directory / 'LockProbe.java'
    probe.write_text('''import java.io.*;import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class LockProbe {
 static Process startOther(String work,String cp,String java)throws Exception {
  return new ProcessBuilder(java,"-cp",cp,"LockProbe",work,"child").redirectErrorStream(true).start();
 }
 public static void main(String[] a)throws Exception {
  System.setProperty("partRecovery.work",a[0]);
  if(a.length>1 && a[1].equals("child")) {
   PartRecoveryTest.cleanupObservation=()->{throw new IllegalStateException("CROSS_JVM_ADMITTED");};
   try{PartRecoveryTest.runRedEvidenceAgainstBuggyCopy();throw new AssertionError("unexpected normal admission");}
   catch(IOException refused){if(!refused.getMessage().contains("still owned"))throw refused;System.out.println("CROSS_JVM_REFUSED");}
   return;
  }
  Path work=Paths.get(a[0]);Files.createDirectories(work);Files.write(work.resolve("old-input"),new byte[]{7});
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
  PartRecoveryTest.cleanupObservation=()->{entered.countDown();boolean interrupted=false;while(true){try{release.await();break;}catch(InterruptedException x){interrupted=true;}}if(interrupted)Thread.currentThread().interrupt();};
  Thread t1=new Thread(()->{try{PartRecoveryTest.runRedEvidenceAgainstBuggyCopy();}catch(Throwable x){failure.set(x);}});t1.start();
  Process c=null;
  try {
   if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("T1 did not enter actual cleanup");t1.interrupt();
   try{PartRecoveryTest.runRedEvidenceAgainstBuggyCopy();throw new AssertionError("T2 unexpectedly admitted");}
   catch(IOException refused){if(!refused.getMessage().contains("still owned"))throw refused;}
   // T2 has fully returned and closed any channel before the independent JVM attempts admission.
   c=startOther(a[0],System.getProperty("java.class.path"),a[1]);
   if(!c.waitFor(3,TimeUnit.SECONDS))throw new AssertionError("third JVM timeout");
   String output=new String(read(c.getInputStream()),"UTF-8");
   if(c.exitValue()!=0 || !output.contains("CROSS_JVM_REFUSED")) {
    Files.write(work.resolveSibling("lock-observed.json"),("{\\\"third_jvm_admitted_while_t1_alive\\\":true}").getBytes("UTF-8"));
    throw new AssertionError("T2 refusal must not release T1 cross-JVM work lock");
   }
   if(!t1.isAlive() || !Files.exists(work.resolve("old-input")) || Files.exists(work.resolve("ModelRepository.java")))throw new AssertionError("blocked T1 work changed");
   Files.write(work.resolveSibling("lock-observed.json"),("{\\\"third_jvm_admitted_while_t1_alive\\\":false}").getBytes("UTF-8"));
  } finally {if(c!=null && c.isAlive()){c.destroyForcibly();c.waitFor(3,TimeUnit.SECONDS);}release.countDown();t1.join(3000);PartRecoveryTest.cleanupObservation=null;}
  if(t1.isAlive() || !(failure.get() instanceof InterruptedException))throw new AssertionError("T1 did not finish interrupted");
  PartRecoveryTest.cleanupObservation=()->{throw new IllegalStateException("ADMITTED_AFTER_RELEASE");};
  try{PartRecoveryTest.runRedEvidenceAgainstBuggyCopy();throw new AssertionError("same JVM recovery missing");}catch(IllegalStateException expected){if(!expected.getMessage().equals("ADMITTED_AFTER_RELEASE"))throw expected;}finally{PartRecoveryTest.cleanupObservation=null;}
  c=startOther(a[0],System.getProperty("java.class.path"),a[1]);
  try{if(!c.waitFor(3,TimeUnit.SECONDS))throw new AssertionError("recovery third JVM timeout");String output=new String(read(c.getInputStream()),"UTF-8");if(c.exitValue()==0 || !output.contains("CROSS_JVM_ADMITTED"))throw new AssertionError("cross JVM recovery missing");}
  finally{if(c.isAlive()){c.destroyForcibly();c.waitFor(3,TimeUnit.SECONDS);}}
  System.out.println("PASS actual T1/T2/third-JVM lock refusal and both recovery admissions");
 }
 static byte[] read(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
}
''')
    classes = directory / 'classes'
    built = subprocess.run(['javac', '--release', '8', '-cp', cp, '-d', str(classes), str(actual), str(probe)], capture_output=True, timeout=30)
    (directory / 'compile.log').write_bytes(built.stdout + built.stderr)
    check(built.returncode == 0, 'actual lock caller must compile: ' + built.stderr.decode(errors='replace'))
    result = subprocess.run([shutil.which('java'), '-cp', str(classes) + ':' + cp, 'LockProbe', str(directory / 'work'), shutil.which('java')], cwd=ROOT, capture_output=True, timeout=12)
    (directory / 'caller.log').write_bytes(result.stdout + result.stderr)
    check(result.returncode == 0, 'actual three-party lock target: ' + result.stderr.decode(errors='replace'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--legacy-red', choices=['part', 'model'])
    parser.add_argument('--evidence-dir', type=Path)
    parser.add_argument('--cleanup-only', action='store_true')
    parser.add_argument('--legacy-cleanup-red', action='store_true')
    parser.add_argument('--lock-only', action='store_true')
    parser.add_argument('--legacy-lock-red', action='store_true')
    args = parser.parse_args()
    life.owned.enable_subreaper()
    base = args.evidence_dir or Path(tempfile.mkdtemp(prefix='mixed-life-', dir=ROOT / '.work'))
    base.mkdir(parents=True, exist_ok=True)
    try:
        if args.lock_only or args.legacy_lock_red:
            lock_case(base, args.legacy_lock_red)
        elif args.cleanup_only or args.legacy_cleanup_red:
            cleanup_case(base, args.legacy_cleanup_red)
        elif args.legacy_red:
            (part_case if args.legacy_red == 'part' else model_case)(base, 'parent-first', True)
        else:
            for mode in ('deadline', 'parent-first', 'interrupt', 'term'):
                part_case(base, mode)
            for mode in ('deadline', 'parent-first', 'interrupt', 'term', 'outer-timeout', 'unconfirmed'):
                model_case(base, mode)
            unconfirmed_case(base)
        print('PASS actual callers/wrapper lifecycle, PGID descendants, cancellation, refusal and recovery')
    finally:
        emergency(base)


if __name__ == '__main__':
    main()
