#!/usr/bin/env python3
"""Real complete LogsActivity consumer on JVM platform fakes, NOT Android runtime.

Default execution includes the button/launch/Refresh lifecycle cases and three
fresh-compiling behavior mutants. --activity-source supports isolated old/fixed
replays without reverting the checkout; --evidence-dir keeps closed raw evidence.
No production methods/visibility are replaced and no persistence drain is run.
"""
import argparse
import ctypes
import hashlib
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
ACTIVITY = 'android/app/src/org/llmasr/minimal/LogsActivity.java'
DIAGNOSTICS = 'android/app/src/org/llmasr/minimal/diagnostics/'
SOURCES = tuple(DIAGNOSTICS + name for name in (
    'LogExportController.java', 'LogExportPage.java', 'RuntimeLogEvent.java',
    'RuntimeLogClock.java', 'RuntimeLogSink.java', 'RuntimeLogStore.java',
    'RuntimeLogCodec.java', 'RuntimeLogPersistence.java', 'RuntimeLogWorker.java',
    'RuntimeLogText.java', 'RuntimeLogEventKind.kt', 'RuntimeLogSource.kt'))
REASON = 'picker-unavailable reason must survive foreground refresh'
STUBS = {
    'android/os/Bundle.java': 'package android.os; public class Bundle {}\n',
    'android/os/Looper.java': '''package android.os;
public class Looper { public static Looper getMainLooper(){return new Looper();} }
''',
    'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
public static final List<Runnable> pending=new ArrayList<>();
public static long delay; public static int delayed;
public Handler(Looper loop){} public boolean post(Runnable r){pending.add(r);return true;}
public boolean postDelayed(Runnable r,long ms){delay=ms;delayed++;return post(r);}
public void removeCallbacks(Runnable r){pending.removeIf(x -> x==r);}
public static void tick(){if(pending.size()!=1)throw new AssertionError("one pending refresh");
pending.remove(0).run();}
}
''',
    'android/net/Uri.java': '''package android.net;
public final class Uri { public static Uri parse(String ignored){return new Uri();} }
''',
    'android/content/Intent.java': '''package android.content;
import android.net.Uri;
public class Intent {
public static final String ACTION_CREATE_DOCUMENT="create",CATEGORY_OPENABLE="open",EXTRA_TITLE="title";
public String action,type,category,title; private Uri data;
public Intent(){} public Intent(String a){action=a;}
public Intent setType(String t){type=t;return this;}
public Intent addCategory(String c){category=c;return this;}
public Intent putExtra(String k,String v){title=v;return this;}
public Intent setData(Uri u){data=u;return this;} public Uri getData(){return data;}
}
''',
    'android/graphics/Color.java': '''package android.graphics;
public class Color {public static int rgb(int r,int g,int b){return (r<<16)|(g<<8)|b;}}
''',
    'android/util/DisplayMetrics.java': '''package android.util;
public class DisplayMetrics {public float density=1;}
''',
    'android/content/res/Resources.java': '''package android.content.res;
public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}
''',
    'android/view/View.java': '''package android.view;
public class View {
public static final int FOCUS_DOWN=130;
public interface OnClickListener {void onClick(View v);}
public void setPadding(int l,int t,int r,int b){} public void setBackgroundColor(int c){}
public boolean post(Runnable r){r.run();return true;}
}
''',
    'android/widget/TextView.java': '''package android.widget;
public class TextView extends android.view.View {
private String text=""; public TextView(android.app.Activity a){}
public void setText(CharSequence t){text=t.toString();} public CharSequence getText(){return text;}
public void setTextSize(int size){} public void setTextColor(int c){}
public void setTextIsSelectable(boolean b){}
}
''',
    'android/widget/Button.java': '''package android.widget;
public class Button extends TextView {
private android.view.View.OnClickListener listener; private boolean enabled=true;
public Button(android.app.Activity a){super(a);} public void setAllCaps(boolean b){}
public void setMinHeight(int n){} public void setEnabled(boolean b){enabled=b;}
public boolean isEnabled(){return enabled;}
public void setOnClickListener(android.view.View.OnClickListener l){listener=l;}
public boolean performClick(){if(!enabled)return false;listener.onClick(this);return true;}
}
''',
    'android/widget/LinearLayout.java': '''package android.widget;
import java.util.*;
public class LinearLayout extends android.view.View {
public static final int VERTICAL=1; public final List<android.view.View> children=new ArrayList<>();
public LinearLayout(android.app.Activity a){} public void setOrientation(int n){}
public void addView(android.view.View v){children.add(v);}
}
''',
    'android/widget/ScrollView.java': '''package android.widget;
public class ScrollView extends android.view.View {
public android.view.View child; public ScrollView(android.app.Activity a){}
public void setFillViewport(boolean b){} public void addView(android.view.View v){child=v;}
public boolean fullScroll(int direction){return true;}
}
''',
    'android/app/Activity.java': '''package android.app;
import android.content.Intent; import android.os.Bundle;
public class Activity {
public static final int RESULT_OK=-1,RESULT_CANCELED=0;
public android.view.View content; public RuntimeException launchFailure;
public final java.util.List<Integer> requests=new java.util.ArrayList<>(); public Intent launched;
private boolean finishing,destroyed;
protected void onCreate(Bundle saved){} protected void onResume(){} protected void onPause(){}
protected void onDestroy(){destroyed=true;}
protected void onActivityResult(int request,int result,Intent data){}
public void onBackPressed(){finish();} public void finish(){finishing=true;}
public boolean isFinishing(){return finishing;} public boolean isDestroyed(){return destroyed;}
public Object getApplicationContext(){return this;}
public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public void setContentView(android.view.View v){content=v;}
public void startActivityForResult(Intent i,int code){launched=i;requests.add(code);
if(launchFailure!=null)throw launchFailure;}
}
''',
    'org/llmasr/minimal/platform/ActivityWindow.java': '''package org.llmasr.minimal.platform;
public final class ActivityWindow {
public static void configure(android.app.Activity a,android.view.View v){}
public static final class BackRegistration {
public BackRegistration(android.app.Activity a,Runnable action){}
public void resumed(){} public void paused(){}
}}
''',
    'org/llmasr/minimal/AppGraph.java': '''package org.llmasr.minimal;
import org.llmasr.minimal.diagnostics.*;
public final class AppGraph {
public static AppGraph current;
public final RuntimeLogStore store; public final RuntimeLogWorker worker;
public final LogExportController<android.net.Uri> exports;
public AppGraph(RuntimeLogStore s,RuntimeLogWorker w,LogExportController<android.net.Uri> e){store=s;worker=w;exports=e;}
public static void install(Object context) throws java.io.IOException {}
public static AppGraph get(){return current;}
public RuntimeLogStore runtimeLogStore(){return store;}
public RuntimeLogWorker runtimeLogWorker(){return worker;}
public LogExportController<android.net.Uri> logExports(){return exports;}
}
''',
}
HARNESS = '''package org.llmasr.minimal;
import android.os.*; import android.content.Intent; import android.net.Uri; import android.widget.*;
import org.llmasr.minimal.diagnostics.*;
import java.io.*; import java.util.*; import java.util.concurrent.Executor;
public final class PickerContractTest {
static int checks;
static final String NOTICE="无法打开保存选择器，请返回后重试。";
static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
static final class Queue implements Executor {
final List<Runnable> work=new ArrayList<>(); int ran;
public void execute(Runnable r){work.add(r);}
void run(){ran++;work.remove(0).run();}
}
static final class Fixture implements AutoCloseable {
final RuntimeLogStore store=new RuntimeLogStore(); final Queue io=new Queue(),persist=new Queue();
final ByteArrayOutputStream output=new ByteArrayOutputStream(); int opens;
final LogExportController<Uri> controller=new LogExportController<>(target -> {opens++;return output;},io);
final RuntimeLogWorker worker=new RuntimeLogWorker(store,new RuntimeLogPersistence(new File("never-open-picker-fixture")),null,persist);
final List<LogsActivity> pages=new ArrayList<>();
Fixture(){check(Handler.pending.isEmpty(),"no leaked refresh");
store.append(RuntimeLogEventKind.APP_STARTUP,RuntimeLogSource.APP,null,"ok");
AppGraph.current=new AppGraph(store,worker,controller);}
LogsActivity create(Bundle saved){LogsActivity a=new LogsActivity();pages.add(a);a.onCreate(saved);a.onResume();tick();return a;}
void noIO(){check(io.work.isEmpty() && opens==0 && controller.state().bytes==0,"failure zero export IO/bytes");}
void fail(LogsActivity a){a.launchFailure=new SecurityException("synthetic content://PRIVATE/secret");
check(button(a).performClick(),"enabled failure button");check(status(a).equals(NOTICE),"immediate safe picker reason");
check(controller.state().phase==LogExportController.Phase.EXPIRED && !controller.state().busy(),"failure releases selecting slot");noIO();}
public void close(){for(LogsActivity a:pages){a.onPause();a.onDestroy();}
worker.close();check(Handler.pending.isEmpty(),"refresh cleanup");
check(persist.ran==0 && persist.work.size()==1,"persistence queue never run/no disk IO");
check(io.work.isEmpty(),"no queued export at cleanup");persist.work.clear();AppGraph.current=null;}
}
static LinearLayout root(LogsActivity a){return (LinearLayout)((ScrollView)a.content).child;}
static Button button(LogsActivity a){Button b=(Button)root(a).children.get(3);
check(b.getText().toString().equals("导出日志文件（UTF-8 TXT）"),"locate actual export button");return b;}
static String status(LogsActivity a){return ((TextView)root(a).children.get(4)).getText().toString();}
static String body(LogsActivity a){return ((TextView)root(a).children.get(8)).getText().toString();}
static void tick(){int before=Handler.delayed;Handler.tick();
check(Handler.pending.size()==1 && Handler.delay==1000 && Handler.delayed==before+1,"real refresh reschedules once at 1000ms");}
static int code(LogsActivity a){return a.requests.get(a.requests.size()-1);}
static Intent target(){return new Intent().setData(Uri.parse("content://SYNTHETIC/output"));}
static void phase(Fixture f,LogsActivity a,LogExportController.Phase p,String message){
check(f.controller.state().phase==p && status(a).equals(RuntimeLogText.export(f.controller.state())),message);}
static void refreshFailure(){try(Fixture f=new Fixture()){
LogsActivity a=f.create(null);f.fail(a);int launches=a.requests.size();String oldBody=body(a);
tick();check(status(a).equals(NOTICE),"picker-unavailable reason must survive foreground refresh");
tick();check(status(a).equals(NOTICE) && body(a).equals(oldBody),"unchanged snapshot reason stable");
f.store.append(RuntimeLogEventKind.APP_READY,RuntimeLogSource.APP,null,"ok");tick();
check(status(a).equals(NOTICE) && !body(a).equals(oldBody),"append snapshot preserves picker reason");
check(a.requests.size()==launches,"no automatic picker retry");f.noIO();
// A hidden click and a rejected/busy begin do not clear this page's notice.
a.onPause();check(Handler.pending.isEmpty(),"pause removes tick");button(a).performClick();
check(a.requests.size()==launches,"hidden begin no launch");a.onResume();tick();
check(status(a).equals(NOTICE),"failure reason survives pause resume");
LogExportController.Ticket other=f.controller.begin(f.controller.newPage(),f.store.snapshot());
check(other!=null,"reserve other page");button(a).performClick();tick();
check(status(a).equals(NOTICE) && a.requests.size()==launches,"rejected ticket must retain notice");
f.controller.abandon(other,true);tick();f.noIO();
}}
static void retryAndDeferred(){try(Fixture f=new Fixture()){
LogsActivity a=f.create(null);f.fail(a);int failed=code(a);long page=f.controller.state().pageId;
a.launchFailure=null;button(a).performClick();int retry=code(a);tick();
check(retry!=failed && f.controller.state().pageId==page,"retry new code same page");
phase(f,a,LogExportController.Phase.SELECTING,"valid retry must clear picker notice");
check(a.launched.action.equals(Intent.ACTION_CREATE_DOCUMENT) && a.launched.type.equals("text/plain")
&& a.launched.category.equals(Intent.CATEGORY_OPENABLE),"real intent contract");
a.onActivityResult(failed,android.app.Activity.RESULT_OK,target());tick();
phase(f,a,LogExportController.Phase.SELECTING,"old callback cannot consume retry");f.noIO();
a.onActivityResult(retry,android.app.Activity.RESULT_CANCELED,null);tick();
phase(f,a,LogExportController.Phase.CANCELLED,"cancel after retry is visible");f.noIO();
button(a).performClick();int selected=code(a);check(selected!=retry,"next retry identity");
a.onPause();check(Handler.pending.isEmpty(),"normal picker pause stops refresh");
a.onActivityResult(selected,android.app.Activity.RESULT_OK,target());
check(f.io.work.isEmpty() && f.opens==0 && f.controller.state().phase==LogExportController.Phase.SELECTING,"paused result deferred without IO");
a.onResume();tick();phase(f,a,LogExportController.Phase.WRITING,"resume admits deferred export");
check(f.io.work.size()==1 && f.opens==0,"single queued export before worker");f.io.run();tick();
phase(f,a,LogExportController.Phase.SUCCEEDED,"success after real queued write");
check(f.opens==1 && f.output.size()>0 && f.controller.state().bytes==f.output.size(),"actual in-memory export bytes");
}}
static void repeatedFailurePause(){try(Fixture f=new Fixture()){
LogsActivity a=f.create(null);f.fail(a);int first=code(a);f.fail(a);
check(code(a)!=first,"repeated failed launch fresh identity");a.onPause();
check(Handler.pending.isEmpty(),"failed pause removes refresh");a.onResume();tick();
check(status(a).equals(NOTICE),"failure reason survives pause resume");tick();f.noIO();
}}
static void recreate(boolean failed){try(Fixture f=new Fixture()){
LogsActivity old=f.create(null);if(failed)f.fail(old);else button(old).performClick();int stale=code(old);
old.onPause();if(!failed)old.onActivityResult(stale,android.app.Activity.RESULT_OK,target());old.onDestroy();
check(f.controller.state().phase==LogExportController.Phase.EXPIRED && !f.controller.state().busy(),"destroy expires selection/deferred");f.noIO();
LogsActivity next=f.create(new Bundle());phase(f,next,LogExportController.Phase.EXPIRED,"new Activity keeps EXPIRED not old notice");
check(!status(next).equals(NOTICE),"notice never saved across recreation");
next.onActivityResult(stale,android.app.Activity.RESULT_OK,target());tick();f.noIO();
button(next).performClick();check(code(next)!=stale,"recreated retry new code");
next.onActivityResult(stale,android.app.Activity.RESULT_OK,target());tick();f.noIO();
next.onActivityResult(code(next),android.app.Activity.RESULT_CANCELED,null);tick();
phase(f,next,LogExportController.Phase.CANCELLED,"recreated current callback works");
}}
public static void main(String[] args){refreshFailure();retryAndDeferred();repeatedFailurePause();recreate(false);recreate(true);
System.out.println("PASS LogsActivity picker consumer "+checks+" checks (host platform fakes; NOT Android runtime)");}
}
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


class CleanupRefused(RuntimeError):
    """Owned exit is unconfirmed: preserve generated inputs and evidence."""


def enable_subreaper():
    # Linux/Nix only. Adopt orphaned tool descendants so zombies can be reaped.
    # No daemon/permission change or third-party dependency; local process only.
    if not sys.platform.startswith('linux') or not hasattr(signal, 'pidfd_send_signal'):
        raise RuntimeError('owned runner requires Linux /proc and pidfd signals')
    if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0) != 0:
        raise RuntimeError('cannot enable owned child subreaper')


def process_identity(pid):
    try:
        fields = (Path('/proc') / str(pid) / 'stat').read_text().rsplit(')', 1)[1].split()
    except (FileNotFoundError, ProcessLookupError):
        return None
    return {'pid': int(pid), 'state': fields[0], 'parent': int(fields[1]),
            'session': int(fields[3]), 'start': int(fields[19])}


def session_members(session):
    # Read only stat identities, never command lines/environment/private files.
    members = []
    for path in Path('/proc').iterdir():
        if path.name.isdecimal():
            identity = process_identity(path.name)
            if identity is not None and identity['session'] == session:
                members.append(identity)
    return members


def reap_members(process, members):
    process.poll()  # Popen must reap its own direct child first.
    for member in members:
        if member['pid'] == process.pid:
            continue
        current = process_identity(member['pid'])
        if current is None or current['start'] != member['start']:
            continue
        try:
            os.waitpid(member['pid'], os.WNOHANG)
        except ChildProcessError:
            pass  # Still owned by its live parent; later adopted by subreaper.


def observe_exit(process, known):
    members = session_members(process.pid)
    for member in members:
        known[(member['pid'], member['start'])] = member
    reap_members(process, list(known.values()))
    alive = []
    for member in known.values():
        current = process_identity(member['pid'])
        if current is not None and current['start'] == member['start']:
            alive.append(current)
    return process.poll() is not None and not alive and not session_members(process.pid)


def signal_members(process, known, number, signals):
    observe_exit(process, known)
    for member in list(known.values()):
        current = process_identity(member['pid'])
        if current is None or current['start'] != member['start']:
            continue
        # pidfd binds identity across PID reuse between lookup and signal.
        try:
            fd = os.pidfd_open(member['pid'])
            try:
                current = process_identity(member['pid'])
                if current is not None and current['start'] == member['start']:
                    signal.pidfd_send_signal(fd, number)
                    signals.append({'pid': member['pid'], 'start': member['start'], 'signal': number})
            finally:
                os.close(fd)
        except ProcessLookupError:
            pass


def await_exit(process, known, seconds):
    deadline = time.monotonic() + seconds
    while True:
        if observe_exit(process, known):
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(0.02)


def terminate_owned(process, known, signals):
    # Nested GNU timeout/tool launchers may create other process groups but stay
    # in our session. Do not assume killpg(direct Bash) covers that lineage.
    signal_members(process, known, signal.SIGTERM, signals)
    if await_exit(process, known, 0.25):
        return True
    signal_members(process, known, signal.SIGKILL, signals)
    return await_exit(process, known, 3)


def execute(argv, directory, label, timeout, *, interrupt=None, exit_probe=None):
    enable_subreaper()
    record = {'runner': 'owned-linux-session-v1', 'argv': argv, 'exit': None,
              'owned_exited': False, 'reason': 'launching', 'timeout': False,
              'signals': [], 'known': [], 'root_reaped': False}
    ledger = directory / (label + '.json')
    write_json(ledger, record)  # A failed launch/observer never silently loses evidence.
    process = None
    primary = None
    known = {}
    with (directory / (label + '.stdout')).open('wb') as stdout, \
            (directory / (label + '.stderr')).open('wb') as stderr:
        try:
            process = subprocess.Popen(argv, cwd=ROOT, stdout=stdout, stderr=stderr,
                                       start_new_session=True)
            record['session'] = process.pid
            record['root_identity'] = process_identity(process.pid)
            write_json(ledger, record)
            deadline = time.monotonic() + timeout
            while not observe_exit(process, known):
                if interrupt is not None:
                    interrupt()
                if time.monotonic() >= deadline:
                    raise subprocess.TimeoutExpired(argv, timeout)
                time.sleep(0.02)
            record['reason'] = 'exited'
        except BaseException as failure:
            primary = failure
            record['reason'] = type(failure).__name__
            record['timeout'] = isinstance(failure, subprocess.TimeoutExpired)
        finally:
            if process is not None:
                try:
                    exited = observe_exit(process, known)
                    if not exited:
                        exited = terminate_owned(process, known, record['signals'])
                    if exit_probe is not None:
                        exited = exited and exit_probe()
                    record['owned_exited'] = exited
                    record['exit'] = process.poll()
                    record['root_reaped'] = process.returncode is not None
                except BaseException as cleanup_failure:
                    if primary is None:
                        primary = cleanup_failure
                    else:
                        primary.add_note('owned teardown also failed: ' + repr(cleanup_failure))
            else:
                record['owned_exited'] = True  # No process was launched.
                record['root_reaped'] = True
            record['known'] = list(known.values())
    output, errors = b'', b''
    try:
        output = (directory / (label + '.stdout')).read_bytes()
        errors = (directory / (label + '.stderr')).read_bytes()
    except BaseException as output_failure:
        if primary is None:
            primary = output_failure
        else:
            primary.add_note('partial output capture also failed: ' + repr(output_failure))
    # Only hash closed, confirmed-quiescent streams, never a live writer's file.
    record['stdout_sha256'] = sha(output) if record['owned_exited'] else None
    record['stderr_sha256'] = sha(errors) if record['owned_exited'] else None
    record['primary'] = None if primary is None else type(primary).__name__
    try:
        write_json(ledger, record)
    except BaseException as evidence_failure:
        if primary is None:
            primary = evidence_failure
        else:
            primary.add_note('terminal evidence write also failed: ' + repr(evidence_failure))
    if not record['owned_exited']:
        failure = CleanupRefused('owned exit unconfirmed; retain directory: ' + str(directory))
        if primary is None:
            primary = failure
        else:
            primary.add_note(str(failure))
    if primary is not None:
        if isinstance(primary, subprocess.TimeoutExpired):
            primary.output, primary.stderr = output, errors
        raise primary
    return subprocess.CompletedProcess(argv, record['exit'], output, errors)


def cleanup_workspace(directory, *, probe=None, remove=True):
    records = []
    for path in directory.rglob('*.json'):
        record = json.loads(path.read_text())
        if isinstance(record, dict) and record.get('runner') == 'owned-linux-session-v1':
            records.append(record)
    safe = all(r['owned_exited'] and r['root_reaped'] for r in records)
    if probe is not None:
        safe = safe and probe()
    if not safe:
        write_json(directory / 'cleanup.json', {'removed': False, 'retained': str(directory),
                                               'reason': 'owned exit unconfirmed'})
        raise CleanupRefused('owned exit unconfirmed; retain directory: ' + str(directory))
    if remove:
        shutil.rmtree(directory)
    else:
        write_json(directory / 'cleanup.json', {'removed': False, 'retained': str(directory),
                                               'reason': 'failure evidence; owned exit confirmed'})


class Workspace:
    def __init__(self, parent=None, probe=None):
        self.path = Path(tempfile.mkdtemp(prefix='logs-activity-picker-', dir=parent))
        self.probe = probe

    def __enter__(self):
        return self.path

    def __exit__(self, kind, primary, traceback):
        try:
            cleanup_workspace(self.path, probe=self.probe, remove=primary is None)
        except BaseException as cleanup_failure:
            if primary is None:
                raise
            primary.add_note('cleanup refused; retained ' + str(self.path) + ': ' + repr(cleanup_failure))
        return False


def tool_identity(directory):
    identities = {}
    for name in ('java', 'javac', 'kotlinc'):
        path = shutil.which(name)
        if path is None:
            raise RuntimeError('missing cached tool: ' + name)
        resolved = Path(path).resolve()
        result = execute([path, '-version'], directory, 'identity-' + name, 20)
        if result.returncode:
            raise RuntimeError('tool identity failed: ' + name)
        identities[name] = {'path': path, 'resolved': str(resolved),
                            'sha256': sha(resolved.read_bytes()),
                            'version': (result.stdout + result.stderr).decode()}
    return identities


def run_contract(activity_bytes, directory):
    directory.mkdir(parents=True, exist_ok=False)
    generated = dict(STUBS)
    generated['org/llmasr/minimal/PickerContractTest.java'] = HARNESS
    generated['org/llmasr/minimal/LogsActivity.java'] = activity_bytes.decode('utf-8')
    sources = []
    manifest = []
    for relative, content in generated.items():
        path = directory / 'src' / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
        sources.append(str(path))
        manifest.append({'path': str(path), 'sha256': sha(path.read_bytes()), 'bytes': path.stat().st_size})
    for relative in SOURCES:
        path = ROOT / relative
        sources.append(str(path))
        manifest.append({'path': relative, 'sha256': sha(path.read_bytes()), 'bytes': path.stat().st_size})
    compiler = ROOT / 'scripts/compile-jvm-sources.sh'
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    write_json(directory / 'inputs.json', {
        'test_sha256': sha(Path(__file__).read_bytes()), 'activity_sha256': sha(activity_bytes),
        'sources': manifest, 'compiler_sha256': sha(compiler.read_bytes()),
        'stdlib_sha256': sha(stdlib.read_bytes()), 'tools': tool_identity(directory)})
    compiled = execute(['bash', str(compiler), str(directory / 'classes'), '', *sources],
                       directory, 'compile', 180)
    if compiled.returncode:
        raise RuntimeError('fixture/production compile failed; NOT behavioral red:\n' + compiled.stderr.decode())
    return execute(['java', '-cp', str(directory / 'classes') + ':' + str(stdlib),
                    'org.llmasr.minimal.PickerContractTest'], directory, 'run', 20)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def stop_synthetic(identity):
    if identity is None:
        return
    current = process_identity(identity['pid'])
    if current is not None and current['start'] == identity['start']:
        fd = os.pidfd_open(identity['pid'])
        try:
            signal.pidfd_send_signal(fd, signal.SIGKILL)
        finally:
            os.close(fd)
    deadline = time.monotonic() + 3
    while process_identity(identity['pid']) is not None:
        try:
            os.waitpid(identity['pid'], os.WNOHANG)
        except ChildProcessError:
            pass
        if time.monotonic() >= deadline:
            raise CleanupRefused('synthetic child exit unconfirmed; keep fixture')
        time.sleep(0.02)


def synthetic_case(directory, name, runner=execute, *, parent_exits=False, interruption=False):
    case = directory / name
    case.mkdir()
    marker = case / 'identity.json'
    child = ('import os,signal,time,json,pathlib,sys; os.setpgrp(); '
             'signal.signal(signal.SIGTERM,signal.SIG_IGN); '
             's=pathlib.Path("/proc/self/stat").read_text().rsplit(")",1)[1].split(); '
             'pathlib.Path(' + repr(str(marker)) + ').write_text(json.dumps('
             '{"pid":os.getpid(),"start":int(s[19])})); '
             'print("synthetic-child-partial",flush=True); '
             'print("synthetic-error-partial",file=sys.stderr,flush=True); time.sleep(15)')
    parent = ('import subprocess,sys,time; '
              'subprocess.Popen([sys.executable,"-c",' + repr(child) + ']); '
              'print("synthetic-parent-partial",flush=True); ' +
              ('sys.exit(0)' if parent_exits else 'time.sleep(15)'))
    sentinel = KeyboardInterrupt('synthetic-interrupt-identity')
    def interrupt():
        if marker.exists():
            raise sentinel
    caught = None
    identity = None
    try:
        try:
            kwargs = {'interrupt': interrupt} if interruption else {}
            runner([sys.executable, '-c', parent], case, 'synthetic', 0.6, **kwargs)
        except (subprocess.TimeoutExpired, KeyboardInterrupt) as failure:
            caught = failure
        identity = json.loads(marker.read_text())
        record_path = case / 'synthetic.json'
        record = json.loads(record_path.read_text()) if record_path.exists() else {}
        # Specific behavioral red, not TimeoutExpired itself. Checked BEFORE the
        # test's emergency finally kills any orphan left by an old runner.
        require(process_identity(identity['pid']) is None and record.get('owned_exited') is True,
                'owned descendant must be reaped before generated directory cleanup')
        require(record['root_reaped'] and record['exit'] is not None,
                'runner must persist terminal exit and reap evidence')
        require(b'synthetic-child-partial' in (case / 'synthetic.stdout').read_bytes()
                and b'synthetic-error-partial' in (case / 'synthetic.stderr').read_bytes(),
                'partial output must survive timeout/interruption')
        if interruption:
            require(caught is sentinel and record['reason'] == 'KeyboardInterrupt',
                    'preserve primary interrupt identity')
        else:
            require(isinstance(caught, subprocess.TimeoutExpired) and record['timeout'],
                    'descendant lifetime must trigger owned timeout')
        require(any(s['signal'] == signal.SIGKILL for s in record['signals']),
                'TERM-resistant child must require KILL')
    finally:
        if identity is None and marker.exists():
            identity = json.loads(marker.read_text())
        stop_synthetic(identity)


def runner_fault_contract(directory, runner=execute):
    enable_subreaper()
    directory.mkdir(exist_ok=False)
    synthetic_case(directory, 'term-resistant', runner)
    synthetic_case(directory, 'parent-exits-holding-output', runner, parent_exits=True)
    synthetic_case(directory, 'interrupt', runner, interruption=True)
    # Fault injection denies exit confirmation AFTER real children were reaped.
    # Uses the same execute ledger and Workspace deletion policy, not a copy.
    scope = Workspace(parent=directory)
    sentinel = None
    try:
        with scope as generated:
            try:
                runner([sys.executable, '-c', 'print("refusal-partial",flush=True)'],
                       generated, 'refused', 2, exit_probe=lambda: False)
            except CleanupRefused as failure:
                sentinel = failure
                raise
    except CleanupRefused as failure:
        require(failure is sentinel, 'preserve primary refusal identity')
    require(scope.path.exists() and (scope.path / 'cleanup.json').exists(),
            'unconfirmed exit must retain generated directory and evidence')
    refusal = json.loads((scope.path / 'refused.json').read_text())
    require(not refusal['owned_exited'] and refusal['stdout_sha256'] is None,
            'unconfirmed stream must not be hashed as closed evidence')
    # Confirm actual synthetic process/session is absent before restoration.
    require(not session_members(refusal['session']), 'refusal fixture actually quiescent')
    refusal['owned_exited'] = True
    write_json(scope.path / 'refused.json', refusal)
    retained = directory / 'refusal-evidence'
    shutil.copytree(scope.path, retained)
    cleanup_workspace(scope.path)
    require(not scope.path.exists(), 'restored confirmed exit permits cleanup')
    primary = KeyboardInterrupt('workspace-primary-sentinel')
    interrupted = Workspace(parent=directory, probe=lambda: False)
    try:
        with interrupted:
            raise primary
    except KeyboardInterrupt as failure:
        require(failure is primary, 'workspace must preserve primary interruption')
    require(interrupted.path.exists(), 'cleanup refusal with primary retains directory')
    shutil.copytree(interrupted.path, directory / 'primary-refusal-evidence')
    cleanup_workspace(interrupted.path)
    with Workspace(parent=directory) as normal:
        result = runner([sys.executable, '-c', 'print("restored-green")'], normal, 'green', 2)
        require(result.returncode == 0, 'restored runner positive')
    require(not normal.exists(), 'confirmed normal exit removes workspace')
    failed = Workspace(parent=directory)
    failure = RuntimeError('preserve-failure-evidence')
    try:
        with failed as generated:
            runner([sys.executable, '-c', 'print("failure-output")'], generated, 'failure', 2)
            raise failure
    except RuntimeError as caught:
        require(caught is failure, 'preserve failure identity after confirmed cleanup')
    require(failed.path.exists() and (failed.path / 'failure.stdout').exists(),
            'failed workspace must retain terminal and partial output evidence')
    shutil.copytree(failed.path, directory / 'confirmed-failure-evidence')
    cleanup_workspace(failed.path)
    write_json(directory / 'verdict.json', {'pass': True, 'synthetic_children_reaped': True,
                                          'timeout_kill_interrupt_refusal_restoration': True})
    print('PASS owned runner timeout/KILL/parent-exit/interruption/refusal/restoration')


def require_green(result):
    if result.returncode or b'PASS LogsActivity picker consumer' not in result.stdout:
        raise AssertionError('production consumer failed:\n' + result.stderr.decode())
    print(result.stdout.decode().strip())


def run_suite(source, directory, skip_mutants):
    runner_fault_contract(directory / 'runner-faults')
    result = run_contract(source, directory / 'production')
    require_green(result)
    if skip_mutants:
        return
    text = source.decode()
    mutants = (
        ('render-priority', 'status.setText(pickerUnavailable ? PICKER_UNAVAILABLE : RuntimeLogText.export(state));',
         'status.setText(RuntimeLogText.export(state));', REASON),
        ('retry-clear', 'pickerUnavailable = false;', '', 'valid retry must clear picker notice'),
        ('pause-clear', 'windowBack.paused();', 'windowBack.paused(); pickerUnavailable = false;',
         'failure reason survives pause resume'),
    )
    for name, before, after, diagnostic in mutants:
        if text.count(before) != 1:
            raise AssertionError('mutant anchor not unique: ' + name)
        mutant = run_contract(text.replace(before, after).encode(), directory / name)
        expected = ('java.lang.AssertionError: ' + diagnostic).encode()
        if mutant.returncode != 1 or expected not in mutant.stderr:
            raise AssertionError('compiling mutant must fail exact external assertion: ' + name + '\n' + mutant.stderr.decode())
        print('PASS compile0/exact assertion1 mutant ' + name)
    require_green(run_contract(source, directory / 'restored'))


def interrupted_by_signal(number, frame):
    raise KeyboardInterrupt('adapter received signal ' + str(number))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--activity-source', type=Path, default=ROOT / ACTIVITY)
    parser.add_argument('--evidence-dir', type=Path)
    parser.add_argument('--skip-mutants', action='store_true')
    args = parser.parse_args()
    source = args.activity_source.read_bytes()
    if args.evidence_dir is not None:
        directory = args.evidence_dir.resolve()
        directory.mkdir(parents=True, exist_ok=False)
        run_suite(source, directory, args.skip_mutants)
    else:
        with Workspace() as temporary:
            run_suite(source, temporary, args.skip_mutants)


if __name__ == '__main__':
    previous_term = signal.signal(signal.SIGTERM, interrupted_by_signal)
    try:
        main()
    finally:
        signal.signal(signal.SIGTERM, previous_term)
