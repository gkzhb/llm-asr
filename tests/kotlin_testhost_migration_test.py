#!/usr/bin/env python3
"""T5 exact source/ownership guards and real fixture host contracts.

Host JVM behavior only; no Android runner/Binder/provider or signed APK claim.
The Java oracle is recovered from a fixed Git object; an explicitly recorded
memory-only interface bridge adapts it to the new admission method, not old algorithms.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = '843dc146e1a38db6a184980fe46f60a254081e10'
PACKAGE = 'org/llmasr/minimal/testhost/'
OWNERS = {
    'FakePorts': ('fixtures/java/', '089898c434c859b2810869315bbb73d948e7df71b51174a7dc10248b156d44ca'),
    'FixtureChecks': ('fixtures/java/', '01fdd2d448bfbbf7a0e3d5eeeebbb9d4a4386c6873ed98f737e714c04d799e22'),
    'FixtureContractTest': ('src/test/kotlin/', '46989a1adddf70d9131f21e5fe0b56ef95f17e2f1c800c4d8f48d8740a38e259'),
    'ImeHostActivity': ('src/main/kotlin/', 'e8bc98043ce0ba468d266c95922f04b6432203ffe3bae5b14246ab24a142a35d'),
    'FixtureInstrumentationTest': ('src/androidTest/kotlin/', '056680aa2240b2f7fee1d243d422914e8848c03d7137dd2bf0cf24933cf34036'),
}
PROVIDER = 'src/androidTest/java/' + PACKAGE + 'FakeDocumentsProvider.java'
PROVIDER_SHA = '5e4eeaa7b6860cfcc884952476b1fd19a376cff8a13320fbb0dd635d6a7a381a'
LOCK_SHA = 'f52803fcc340682c8baf4fdf068e9056bd1dce4da7a471199111eb8a3723cd79'


def require(value, diagnostic):
    if not value:
        raise ValueError(diagnostic)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def paths():
    return {name: 'android/test-host/' + directory + PACKAGE + name + '.kt'
            for name, (directory, _) in OWNERS.items()}


def semantic_check(texts):
    """Independent narrow semantics, not only a snapshot hash of migrated bodies."""
    ports = texts['FakePorts']; checks = texts['FixtureChecks']; suite = texts['FixtureInstrumentationTest']
    for token in ('captures++; this.control = control', 'finally { control!!.captureReleased() }',
                  'inferences++; this.request = request', 'duringInference!!.run()',
                  'thread!!.join(6000)', 'thread!!.interrupt()', 'thread!!.join(1000)',
                  'throw AssertionError("worker did not finish")', '@JvmField @Volatile var cleanupCompleted'):
        require(token in ports, 'T5 nullable/worker/field semantic contract: ' + token)
    require(ports.index('captures++; this.control = control') < ports.index('control!!.start'),
            'T5 capture stores before dereference')
    require(ports.index('inferences++; this.request = request') < ports.index('request!!.kind'),
            'T5 transcribe stores before dereference')
    for token in ('override fun requireModelReady()', 'admissions++; order.add("ready")',
                  'if (rejectModel) throw IOException("synthetic model admission rejection")',
                  'FixtureChecks.require(admitted, "model admission must precede capture")',
                  'admitted = false; order.add("capture")', 'admitted = false; order.add("cleanup")'):
        require(token in ports, 'T5 model admission semantic contract: ' + token)
    require(ports.index('admitted = true') < ports.index('override fun capture')
            and ports.index('admitted = false; order.add("capture")') < ports.index('captures++; this.control = control'),
            'T5 admission grant before capture and consumed before capture counter')
    for token in ('backend.admissions == 1 && backend.captures == 0 && backend.inferences == 0 && backend.cleanups == 1',
                  'Arrays.asList("ready", "cleanup") && !owner.isBusy() && !controller.ownsTask()',
                  'backend.rejectModel = false', 'backend.admissions == 2 && backend.captures == 1',
                  'b.admissions == 1 && b.captures == 1 && b.inferences == 1 && b.cleanups == 2',
                  '"ready", "capture", "inference", "cleanup", "cleanup"'):
        require(token in checks, 'T5 admission rejection/retry/queued-cancel semantic contract: ' + token)
    require(checks.index('backend.rejectModel = true') < checks.index('controller.start(session, "English")')
            < checks.index('backend.rejectModel = false'), 'T5 rejection before explicit prepared retry')
    require('(primary as java.lang.Throwable).addSuppressed(closing)' in checks
            and '.use {' not in checks, 'T5 Java member TWR suppression')
    require('public override fun onCreate(state: Bundle?)' in texts['ImeHostActivity'], 'T5 Activity public nullable override')
    require('private fun <T> unchanged(value: T?): T = value as T' in suite
            and 'digest(unchanged<ByteArray>(bytes))' in suite, 'T5 erased digest forwarding')
    methods = re.findall(r'open fun (test\w+)\(\)', suite)
    require(len(methods) == 14 and len(set(methods)) == 14, 'T5 14 public instance instrumentation methods')
    contract = texts['FixtureContractTest']
    require(contract.count('@Test') == 7 and contract.count('@Throws(Exception::class)') == 4,
            'T5 seven JUnit methods/four checked Throws')


def source_check(root):
    expected = paths()
    for name, relative in expected.items():
        path = root / relative
        require(path.is_file() and not path.is_symlink(), 'T5 Kotlin source missing: ' + name)
        require(not (root / relative.replace('/kotlin/', '/java/')).with_suffix('.java').exists(),
                'T5 duplicate Java owner: ' + name)
        require(sha(path) == OWNERS[name][1], 'T5 source/body/fields/Throws/null/TWR identity: ' + name)
    semantic_check({name: (root / relative).read_text() for name, relative in expected.items()})
    module = root / 'android/test-host'
    provider = module / PROVIDER
    require(provider.is_file() and not provider.is_symlink() and sha(provider) == PROVIDER_SHA,
            'T5 unchanged Java provider identity')
    inventory = {str(p.relative_to(root)) for d in ('src', 'fixtures') for p in (module / d).rglob('*')
                 if p.suffix in ('.java', '.kt') and p.is_file()}
    require(inventory == set(expected.values()) | {'android/test-host/' + PROVIDER},
            'T5 exact five Kotlin plus one Java ownership')
    require(sha(module / 'gradle.lockfile') == LOCK_SHA, 'T5 normal single module lock identity')
    dsl = (module / 'build.gradle.kts').read_text()
    for token in ('id("org.jetbrains.kotlin.android")', 'allWarningsAsErrors.set(true)',
                  'jvmTarget.set(JvmTarget.JVM_1_8)', 'freeCompilerArgs.add("-Xjvm-default=all")',
                  'element.file.extension == "kt" && element.file.canonicalFile !in allowed',
                  '"compileDebugKotlin"', '"compileDebugUnitTestKotlin"', '"compileDebugAndroidTestKotlin"'):
        require(token in dsl, 'T5 Kotlin compiler policy: ' + token)
    for role, names, roots in (
            ('main', ['ImeHostActivity'], ['src/main/kotlin']),
            ('test', ['FixtureContractTest', 'FakePorts', 'FixtureChecks'], ['src/test/kotlin', 'fixtures/java']),
            ('androidTest', ['FixtureInstrumentationTest', 'FakePorts', 'FixtureChecks'], ['src/androidTest/kotlin', 'fixtures/java'])):
        entry = re.search(r'"' + role + r'" to listOf\((.*?)\)', dsl, re.S)
        require(entry is not None and re.findall(r'"([^\"]+)"', entry[1]) == [PACKAGE + n + '.kt' for n in names],
                'T5 exact role owners: ' + role)
        source = re.search(r'kotlin.sourceSets.named\("' + role + r'"\) \{(.*?)\n\}', dsl, re.S)
        require(source is not None and 'kotlin.setSrcDirs(listOf(' + ', '.join('"' + p + '"' for p in roots) + '))' in source[1]
                and 'kotlin.include(javafinalKotlinOwners.getValue("' + role + '"))' in source[1],
                'T5 exact role roots: ' + role)
    require('gradle.taskGraph' not in dsl and 'javafinalPrepare' not in dsl, 'T5 no probe guard/temporary task')
    host = (root / 'scripts/test-minimal-apk.sh').read_text()
    for command in ('python3 -B tests/kotlin_testhost_migration_test.py --source',
                    'python3 -O tests/kotlin_testhost_migration_test.py --source',
                    'python3 -B tests/kotlin_testhost_migration_test.py --self-test',
                    'python3 -O tests/kotlin_testhost_migration_test.py --self-test',
                    'python3 -B tests/kotlin_testhost_migration_test.py --contracts "$classes"',
                    'python3 -O tests/kotlin_testhost_migration_test.py --contracts "$classes"',
                    'python3 -B tests/kotlin_testhost_migration_test.py --faults "$classes"'):
        require(host.count(command) == 1, 'T5 formal host gate wiring: ' + command)
    return expected


def rejected(call, diagnostic):
    try:
        call()
    except ValueError as error:
        if diagnostic not in str(error):
            raise RuntimeError('wrong T5 negative diagnostic: ' + str(error)) from error
    else:
        raise RuntimeError('T5 checker accepted negative: ' + diagnostic)


def self_test():
    texts = {name: (ROOT / relative).read_text() for name, relative in paths().items()}
    semantic_check(texts)
    for name, old, new, diagnostic in (
            ('FakePorts', 'captures++; this.control = control', 'control!!; captures++', 'semantic contract'),
            ('FakePorts', 'thread!!.join(6000)', 'thread!!.join(1)', 'semantic contract'),
            ('FakePorts', 'if (rejectModel) throw IOException("synthetic model admission rejection")', 'if (false) throw IOException("synthetic model admission rejection")', 'model admission semantic contract'),
            ('FakePorts', 'admitted = false; order.add("capture")', 'order.add("capture")', 'model admission semantic contract'),
            ('FakePorts', 'admitted = false; order.add("cleanup")', 'order.add("cleanup")', 'model admission semantic contract'),
            ('FixtureChecks', 'backend.admissions == 1 && backend.captures == 0', 'backend.admissions == 1 && backend.captures >= 0', 'rejection/retry/queued-cancel'),
            ('FixtureChecks', 'backend.rejectModel = false', 'backend.rejectModel = true', 'rejection/retry/queued-cancel'),
            ('FakePorts', '@JvmField @Volatile', '@JvmField', 'semantic contract'),
            ('FixtureChecks', '(primary as java.lang.Throwable).addSuppressed(closing)', 'primary.addSuppressed(closing)', 'member TWR'),
            ('ImeHostActivity', 'public override', 'protected override', 'Activity public'),
            ('FixtureInstrumentationTest', 'digest(unchanged<ByteArray>(bytes))', 'digest(bytes!!)', 'erased digest')):
        require(texts[name].count(old) >= 1, 'semantic negative anchor: ' + name)
        bad = dict(texts); bad[name] = texts[name].replace(old, new)
        rejected(lambda: semantic_check(bad), diagnostic)
    # Meta-check the same rejection helper: a no-op checker cannot make a negative pass.
    try:
        rejected(lambda: None, 'weakened-checker')
    except RuntimeError as error:
        if 'checker accepted negative' not in str(error):
            raise
    else:
        raise RuntimeError('T5 rejection meta-check weakened')
    with tempfile.TemporaryDirectory(prefix='testhost-guard-') as directory:
        root = Path(directory)
        files = list(paths().values()) + ['android/test-host/' + PROVIDER, 'android/test-host/build.gradle.kts',
                                         'android/test-host/gradle.lockfile', 'scripts/test-minimal-apk.sh']
        for relative in files:
            p = root / relative; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes((ROOT / relative).read_bytes())
        source_check(root)
        for name, relative in paths().items():
            p = root / relative; data = p.read_bytes(); p.unlink()
            rejected(lambda: source_check(root), 'source missing'); p.write_bytes(data)
            old = (root / relative.replace('/kotlin/', '/java/')).with_suffix('.java')
            old.parent.mkdir(parents=True, exist_ok=True); old.write_text('duplicate\n')
            rejected(lambda: source_check(root), 'duplicate Java'); old.unlink()
            p.write_bytes(data + b'// fields/null/TWR weakened\n')
            rejected(lambda: source_check(root), 'identity'); p.write_bytes(data)
        dsl = root / 'android/test-host/build.gradle.kts'; data = dsl.read_text()
        for old, new, diagnostic in (
                ('"src/test/kotlin", "fixtures/java"', '"src/main/kotlin"', 'exact role roots'),
                ('"org/llmasr/minimal/testhost/FakePorts.kt", ', '', 'exact role owners'),
                ('"compileDebugAndroidTestKotlin"', '"wrongTask"', 'compiler policy'),
                ('allWarningsAsErrors.set(true)', 'allWarningsAsErrors.set(false)', 'compiler policy'),
                ('element.file.canonicalFile !in allowed', 'false', 'compiler policy')):
            require(old in data, 'T5 self fixture anchor'); dsl.write_text(data.replace(old, new))
            rejected(lambda: source_check(root), diagnostic); dsl.write_text(data)
        host = root / 'scripts/test-minimal-apk.sh'; data = host.read_bytes(); host.write_text('')
        rejected(lambda: source_check(root), 'formal host'); host.write_bytes(data)
        source_check(root)
    # Use the real fixture XML checker, with explicit raises that remain under -O.
    import android_test_fixture_test as fixture
    import xml.etree.ElementTree as ET
    good = ET.Element('testsuite', name=fixture.CLASS, tests='7', failures='0', errors='0', skipped='0')
    for name in fixture.METHODS:
        ET.SubElement(good, 'testcase', classname=fixture.CLASS, name=name)
    with tempfile.TemporaryDirectory(prefix='testhost-xml-') as directory:
        p = Path(directory) / 'suite.xml'
        for tag in ('skipped', 'error', 'failure'):
            tree = ET.fromstring(ET.tostring(good)); ET.SubElement(tree[0], tag)
            ET.ElementTree(tree).write(p)
            try:
                fixture.xml_result(p)
            except AssertionError:
                pass
            else:
                raise RuntimeError('T5 XML negative accepted: ' + tag)
        ET.ElementTree(good).write(p); fixture.xml_result(p)
    print('PASS T5 same-checker source/duplicate/body/role/root/compiler/host/XML negatives; explicit raises under -O')


def execution(work, label, argv, timeout, dependency):
    import mixed_compiler_lifecycle as life
    result = life.run(argv, work, label, timeout, dependency=dependency)
    state = json.loads((work / (label + '.json')).read_text())
    require(state.get('owned_exited') and state.get('root_reaped') and not state.get('timeout'),
            'T5 owned exit/timeout is not behavior evidence: ' + label)
    return result


def contracts(classpath, evidence):
    import mixed_compiler_lifecycle as life
    require(evidence is not None, 'T5 fresh evidence directory required')
    evidence.mkdir(parents=True, exist_ok=False)
    dependency = classpath.resolve(strict=True); life.guard(dependency)
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    caller = evidence / 'T5Contracts.java'
    caller.write_text('public class T5Contracts { public static void main(String[] a) throws Exception { '
                      'switch(a[0]) { case "readiness": org.llmasr.minimal.testhost.FixtureChecks.readinessRejectsBeforeLoad(); break; '
                      'case "bytes": org.llmasr.minimal.testhost.FixtureChecks.badBytesAreSafe(); break; '
                      'case "commit": org.llmasr.minimal.testhost.FixtureChecks.singleConfirmationAndPassword(); break; '
                      'default: throw new AssertionError("unknown contract"); } System.out.println("PASS T5 external production caller"); }}\n')
    mutations = (
        ('asr/InferenceAdapter.kt',
         '            readiness!!.requireReady()\n            phases.accessCompleted()\n            failure = "native-runtime"\n            library!!.load()',
         '            library!!.load()\n            readiness!!.requireReady()\n            phases.accessCompleted()\n            failure = "native-runtime"',
         'readiness', 'readiness zero load/native'),
        ('asr/InferenceAdapter.kt', 'throw IOException("Invalid native response protocol")',
         'throw IOException("Invalid native response protocol", malformed)', 'bytes', 'bad bytes fixed safe exception'),
        ('ime/ImeController.kt', 'connection != null && connection.write(text)',
         'connection != null && run { connection.write(text); connection.write(text) }', 'commit', 'single confirmation only'))
    records = []
    for index, (relative, old, new, method, diagnostic) in enumerate(mutations):
        source = ROOT / 'android/app/src/org/llmasr/minimal' / relative
        original = source.read_bytes(); text = original.decode()
        require(text.count(old) == 1, 'T5 unique real production mutant anchor: ' + relative)
        for phase, content in (('green', original), ('red', text.replace(old, new, 1).encode()), ('restored', original)):
            work = evidence / (str(index) + '-' + phase); work.mkdir()
            target = work / source.name; target.write_bytes(content); classes = work / 'classes'
            compiled = execution(work, 'compile', ['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'),
                str(classes), str(dependency), str(target), str(caller),
                str(ROOT / paths()['FakePorts']), str(ROOT / paths()['FixtureChecks'])], 270, dependency)
            require(compiled.returncode == 0, 'T5 production mutant compile failure NOT target red: ' + relative)
            ran = execution(work, 'run', ['java', '-cp', str(classes) + ':' + str(dependency) + ':' + str(stdlib),
                                         'T5Contracts', method], 45, dependency)
            output = (ran.stdout + ran.stderr).decode(errors='replace')
            require((ran.returncode == 1 and 'java.lang.AssertionError: ' + diagnostic in output) if phase == 'red'
                    else (ran.returncode == 0 and 'PASS T5 external' in output),
                    'T5 exact external production result missing: ' + relative + '/' + phase + '\n' + output)
            require(source.read_bytes() == original, 'T5 production original drift')
            records.append({'owner': relative, 'phase': phase, 'compile_exit': compiled.returncode, 'run_exit': ran.returncode,
                            'source_sha256': hashlib.sha256(content).hexdigest(), 'caller_sha256': sha(caller),
                            'diagnostic': diagnostic, 'not_Android': True})
            (evidence / 'routes.json').write_text(json.dumps(records, indent=2) + '\n')
        print('PASS T5 production compile0/exact red1/restored0', relative, method, flush=True)
    require(len(records) == 9, 'T5 exactly nine production routes')


# External Java consumer only; no copied production/fixture implementation body.
FAULT_CALLER = r'''
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.llmasr.minimal.testhost.FakePorts;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.task.*;
public class T5Faults {
 interface Action { void run() throws Throwable; }
 static int checks;
 static void check(boolean b,String s) { checks++; if(!b)throw new AssertionError(s); }
 static Throwable thrown(Action a) { try {a.run();}catch(Throwable t){return t;}throw new AssertionError("expected Throwable"); }
 static void simple(Throwable t,Class<?> k,String label) {check(t.getClass()==k && t.getCause()==null && t.getSuppressed().length==0,label);}
 static void await(CountDownLatch l) throws Exception {check(l.await(3,TimeUnit.SECONDS),"fault latch");}
 static RequestContext request() {return new RequestContext("fault",TaskKind.INFERENCE);}
 static RecordingControl prepared(FakePorts.Backend b) throws Throwable { RecordingControl c=new RecordingControl(); b.requireModelReady(); b.capture(c,x->{});check(c.tryCommitInference(),"real commit gate");return c; }
 static Thread worker(FakePorts.Worker w)throws Exception {java.lang.reflect.Field f=w.getClass().getDeclaredField("thread");f.setAccessible(true);return (Thread)f.get(w);}
 static void capture() throws Throwable {
  FakePorts.Backend b=new FakePorts.Backend();
  b.requireModelReady();
  simple(thrown(()->b.capture(null,x->{})),NullPointerException.class,"capture null control shape");
  check(b.captures==1 && b.control==null,"capture stores before first null read");
  RecordingControl c=new RecordingControl();
  b.requireModelReady();
  simple(thrown(()->b.capture(c,null)),NullPointerException.class,"capture null progress shape");
  check(b.captures==2 && b.control==c && c.released(),"capture finally release after progress null");
  RecordingControl d=new RecordingControl();Error sentinel=new LinkageError("external progress");
  b.requireModelReady();
  Throwable t=thrown(()->b.capture(d,x->{throw sentinel;}));
  check(t==sentinel && d.released() && b.control==d,"progress exact Throwable identity/finally");
  RecordingControl stopped=new RecordingControl();stopped.stop();
  b.requireModelReady();
  Throwable rejected=thrown(()->b.capture(stopped,x->{throw new AssertionError("progress must not run");}));
  check(rejected.getClass()==AssertionError.class && "capture admitted by real gate".equals(rejected.getMessage()) && stopped.released(),"first start refusal order");
 }
 static void transcribe() throws Throwable {
  FakePorts.Backend b=new FakePorts.Backend();
  simple(thrown(()->b.transcribe(null,null,null)),NullPointerException.class,"transcribe null request shape");
  check(b.inferences==1 && b.request==null,"transcribe stores before null read");
  RequestContext r=request();Throwable t=thrown(()->b.transcribe(r,b.samples,"English"));
  simple(t,NullPointerException.class,"transcribe null control");check(b.request==r && b.inferences==2,"transcribe stored request");
  prepared(b);AtomicInteger calls=new AtomicInteger();b.duringInference=()->calls.incrementAndGet();
  for(int mode=0;mode<2;mode++) {final int m=mode;Throwable bad=thrown(()->b.transcribe(r,m==0?null:b.samples,m==0?"English":null));
   check(bad.getClass()==AssertionError.class && "backend arguments".equals(bad.getMessage()) && calls.get()==0,"nullable wav/language gate before callback");}
  b.duringInference=null;simple(thrown(()->b.transcribe(r,b.samples,"English")),NullPointerException.class,"late callback null");
  Error fatal=new AssertionError("callback identity");b.duringInference=()->{calls.incrementAndGet();throw fatal;};
  check(thrown(()->b.transcribe(r,b.samples,"English"))==fatal && calls.get()==1 && b.request==r,"callback exact fatal identity");
 }
 static void queues() throws Throwable {
  FakePorts.Queue q=new FakePorts.Queue();q.execute(null);
  Throwable missing=thrown(q::drain);check(missing.getClass()==AssertionError.class && "missing queued task".equals(missing.getMessage()),"queue null no early throw");
  AtomicInteger runs=new AtomicInteger();q.execute(()->runs.incrementAndGet());q.drain();check(runs.get()==1,"queue null leaves slot free");
  FakePorts.Worker w=new FakePorts.Worker();w.execute(null);Throwable failed=thrown(w::close);
  check(failed.getClass()==AssertionError.class && "worker failure".equals(failed.getMessage()) && failed.getCause().getClass()==NullPointerException.class && failed.getSuppressed().length==0,"worker null captured not submission");
  Error exact=new LinkageError("worker exact");FakePorts.Worker f=new FakePorts.Worker();f.execute(()->{throw exact;});
  Throwable e=thrown(f::close);check(e.getClass()==AssertionError.class && e.getCause()==exact,"worker exact cause identity");
 }
 static void interruptedClose() throws Throwable {
  FakePorts.Worker w=new FakePorts.Worker();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),closing=new CountDownLatch(1);
  AtomicReference<Throwable> outcome=new AtomicReference<>();AtomicBoolean flag=new AtomicBoolean(true);
  w.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}});await(entered);
  Thread closer=new Thread(()->{closing.countDown();try{w.close();}catch(Throwable e){outcome.set(e);flag.set(Thread.currentThread().isInterrupted());}},"t5-close-interrupt");
  try {closer.start();await(closing);closer.interrupt();closer.join(2000);check(!closer.isAlive() && outcome.get() instanceof InterruptedException && !flag.get(),"close wait InterruptedException identity category/status");}
  finally {release.countDown();closer.interrupt();closer.join(2000);worker(w).join(2000);check(!closer.isAlive()&&!worker(w).isAlive(),"interrupted close teardown");}
  w.close();
 }
 static void boundedClose(boolean resist) throws Throwable {
  FakePorts.Worker w=new FakePorts.Worker();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),interrupted=new CountDownLatch(1);
  w.execute(()->{entered.countDown();while(true){try{release.await();return;}catch(InterruptedException e){interrupted.countDown();if(!resist)return;}}});await(entered);
  ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
  if(resist)scheduler.schedule(release::countDown,6250,TimeUnit.MILLISECONDS);
  long start=System.nanoTime();Throwable got=null;
  try {got=thrown(w::close);long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
   check(got.getClass()==AssertionError.class && "worker did not finish".equals(got.getMessage()) && got.getCause()==null,"alive first join remains failure after second join");
   check(ms>=5800 && ms<9000 && interrupted.getCount()==0,"real 6000 plus bounded 1000 join/interrupt");}
  finally {release.countDown();scheduler.shutdownNow();check(scheduler.awaitTermination(2,TimeUnit.SECONDS),"scheduler teardown");Thread thread=worker(w);thread.interrupt();thread.join(2000);check(!thread.isAlive(),"bounded worker teardown");}
 }
 public static void main(String[] args)throws Throwable {capture();transcribe();queues();interruptedClose();boundedClose(false);boundedClose(true);System.out.println("PASS T5 faults1-4 external checks="+checks+"; TWR self/distinct/Error/interrupt actual fixture injection NOTRUN; SAF NOTRUN");}
}
'''


def faults(classpath, evidence):
    import mixed_compiler_lifecycle as life
    require(evidence is not None, 'T5 fresh fault evidence required')
    evidence.mkdir(parents=True, exist_ok=False)
    dependency = classpath.resolve(strict=True); life.guard(dependency)
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    caller = evidence / 'T5Faults.java'; caller.write_text(FAULT_CALLER)
    original = evidence / 'original'; original.mkdir()
    adapted = evidence / 'adapted-historical'; adapted.mkdir()
    oracle_records = []
    for name in ('FakePorts', 'FixtureChecks'):
        relative = 'android/test-host/fixtures/java/' + PACKAGE + name + '.java'
        result = subprocess.run(['git', 'show', BASE + ':' + relative], cwd=ROOT, capture_output=True, timeout=15)
        require(result.returncode == 0, 'T5 fixed original Git oracle unavailable')
        (original / (name + '.java')).write_bytes(result.stdout)
        data = result.stdout
        before = '    public static final class Backend implements ImeController.Backend {'
        bridge = '\n        /** Interface-only memory compatibility bridge; no old algorithm is changed. */\n        @Override public void requireModelReady() { }'
        if name == 'FakePorts':
            text = data.decode(); require(text.count(before) == 1, 'T5 adapted historical interface unique anchor')
            data = text.replace(before, before + bridge, 1).encode()
        target = adapted / (name + '.java'); target.write_bytes(data)
        oracle_records.append({'base': BASE, 'git_path': relative, 'original_sha256': hashlib.sha256(result.stdout).hexdigest(),
                               'original_bytes': len(result.stdout), 'adapted_sha256': sha(target), 'adapted_bytes': len(data),
                               'transformation': {'anchor': before, 'insert': bridge} if name == 'FakePorts' else None,
                               'scope': 'adapted historical Java oracle: new interface only, old capture/transcribe/cleanup/worker algorithms unchanged'})
    (evidence / 'historical-oracle.json').write_text(json.dumps(oracle_records, indent=2) + '\n')
    records = []
    for route in ('original-java', 'candidate-kotlin', 'early-null-bad'):
        work = evidence / route; work.mkdir(); classes = work / 'classes'
        sources = [adapted / (n + '.java') for n in ('FakePorts', 'FixtureChecks')] if route == 'original-java' else [ROOT / paths()[n] for n in ('FakePorts', 'FixtureChecks')]
        if route == 'early-null-bad':
            p = work / 'FakePorts.kt'; data = sources[0].read_text(); anchor = 'captures++; this.control = control'
            require(data.count(anchor) == 1, 'T5 nullable bad unique anchor')
            p.write_text(data.replace(anchor, 'control!!; captures++; this.control = control')); sources[0] = p
        compiled = execution(work, 'compile', ['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes),
                    str(dependency), *map(str, sources), str(caller)], 270, dependency)
        require(compiled.returncode == 0, 'T5 fault compile failure is NOT red: ' + route)
        ran = execution(work, 'run', ['java', '-cp', str(classes) + ':' + str(dependency) + ':' + str(stdlib), 'T5Faults'], 45, dependency)
        output = (ran.stdout + ran.stderr).decode(errors='replace')
        if route == 'early-null-bad':
            require(ran.returncode == 1 and 'java.lang.AssertionError: capture stores before first null read' in output,
                    'T5 compilable early-null exact red missing\n' + output)
        else:
            require(ran.returncode == 0 and 'PASS T5 faults1-4 external' in output, 'T5 same-caller faults failed: ' + route + '\n' + output)
        records.append({'route': route, 'oracle_policy': 'adapted historical Java interface bridge' if route == 'original-java' else 'current audited Kotlin fixture', 'sources': {str(p): sha(p) for p in sources}, 'caller_sha256': sha(caller),
                        'compile_exit': compiled.returncode, 'run_exit': ran.returncode,
                        'stdout': ran.stdout.decode(errors='replace'), 'stderr': ran.stderr.decode(errors='replace')})
        (evidence / 'routes.json').write_text(json.dumps(records, indent=2) + '\n')
    (evidence / 'boundaries.json').write_text(json.dumps({'fault1_4': 'actual Java/Kotlin fixture same external consumer',
        'fault5': {'actual_cleanupRetainsOwner_injected_primary_self': 'NOTRUN: private local worker/backend, no authorized seam',
                   'distinct_close_Error_InterruptedException': 'NOTRUN: no direct deterministic actual-fixture injection'},
        'fault6_SAF_Android': 'NOTRUN: no Android runtime/device authorization', 'routes': len(records)}, indent=2) + '\n')
    print('PASS T5 faults1-4 and compilable early-null exact red; fault5/6 precise NOTRUN, not all faults passed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--source', action='store_true')
    group.add_argument('--self-test', action='store_true')
    group.add_argument('--contracts', type=Path)
    group.add_argument('--faults', type=Path)
    parser.add_argument('--evidence-dir', type=Path)
    args = parser.parse_args()
    if args.source:
        print('PASS T5 exact source identities/SDK roles/normal lock/host wiring', len(source_check(ROOT)))
    elif args.self_test:
        self_test()
    elif args.contracts is not None:
        contracts(args.contracts, args.evidence_dir)
    else:
        faults(args.faults, args.evidence_dir)


if __name__ == '__main__':
    main()
