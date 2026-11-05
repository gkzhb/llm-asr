#!/usr/bin/env python3
"""Six-owner migration contracts on real production implementations; host fakes, not Android.

Reuse the reviewed picker owned-session/Workspace mechanism unchanged. Every compile
has fresh outputs and exact source identities; compiler/tool/timeouts are never red.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


owned = load('_next_owned', ROOT / 'tests/logs_activity_picker_test.py')
window = load('_next_window_fixtures', ROOT / 'tests/activity_window_test.py')
SELECTED = ('modelmanagement/ModelManagementController', 'AppGraph', 'transcription/AsrOperation',
            'AsrImeService', 'LogsActivity', 'platform/ActivityWindow')
APP_BASE = ROOT / 'android/app/src/org/llmasr/minimal'
STUBS = {
    'android/system/ErrnoException.java': 'package android.system; public class ErrnoException extends Exception {public final int errno;public ErrnoException(String name,int code){super(name);errno=code;}}',
    'android/system/StructTimespec.java': 'package android.system; public class StructTimespec {public long tv_sec,tv_nsec;public StructTimespec(long s,long n){tv_sec=s;tv_nsec=n;}}',
    'android/system/StructStat.java': 'package android.system; public class StructStat {public long st_dev,st_ino,st_size;public int st_mode;public StructTimespec st_mtim,st_ctim;}',
    'android/system/OsConstants.java': 'package android.system; public class OsConstants {public static final int ENOENT=2,O_RDONLY=0,O_WRONLY=1,O_CREAT=64,O_EXCL=128,O_NOFOLLOW=131072,O_DIRECTORY=65536;public static boolean S_ISDIR(int m){return (m&0170000)==0040000;}public static boolean S_ISREG(int m){return (m&0170000)==0100000;}}',
    'android/system/Os.java': '''package android.system; public class Os {
private static final java.util.Map<java.io.FileDescriptor,java.io.Closeable> handles=new java.util.IdentityHashMap<>();
private static final java.util.Map<java.io.FileDescriptor,String> paths=new java.util.IdentityHashMap<>();
public static StructStat fstat(java.io.FileDescriptor fd)throws ErrnoException{return lstat(paths.get(fd));}
public static StructStat lstat(String path)throws ErrnoException {
 try {java.util.Map<String,Object>a=java.nio.file.Files.readAttributes(java.nio.file.Paths.get(path),"unix:dev,ino,mode,size,lastModifiedTime,ctime",java.nio.file.LinkOption.NOFOLLOW_LINKS);StructStat s=new StructStat();s.st_dev=((Number)a.get("dev")).longValue();s.st_ino=((Number)a.get("ino")).longValue();s.st_mode=((Number)a.get("mode")).intValue();s.st_size=((Number)a.get("size")).longValue();s.st_mtim=time((java.nio.file.attribute.FileTime)a.get("lastModifiedTime"));s.st_ctim=time((java.nio.file.attribute.FileTime)a.get("ctime"));return s;}
 catch(java.nio.file.NoSuchFileException e){throw new ErrnoException("lstat",2);}catch(java.io.IOException e){throw new ErrnoException("lstat",5);}}
private static StructTimespec time(java.nio.file.attribute.FileTime f){java.time.Instant i=f.toInstant();return new StructTimespec(i.getEpochSecond(),i.getNano());}
public static java.io.FileDescriptor open(String path,int flags,int mode)throws ErrnoException {
 try {if(java.nio.file.Files.isDirectory(java.nio.file.Paths.get(path))){java.io.FileDescriptor fd=new java.io.FileDescriptor();handles.put(fd,java.nio.channels.FileChannel.open(java.nio.file.Paths.get(path),java.nio.file.StandardOpenOption.READ));paths.put(fd,path);return fd;}
 if((flags&OsConstants.O_WRONLY)!=0){if((flags&OsConstants.O_EXCL)!=0)java.nio.file.Files.createFile(java.nio.file.Paths.get(path));java.io.FileOutputStream o=new java.io.FileOutputStream(path);handles.put(o.getFD(),o);return o.getFD();}
 java.io.FileInputStream i=new java.io.FileInputStream(path);handles.put(i.getFD(),i);return i.getFD();}catch(java.io.IOException e){throw new ErrnoException("open",5);}}
public static void fsync(java.io.FileDescriptor fd)throws ErrnoException {try{java.io.Closeable c=handles.get(fd);if(c instanceof java.nio.channels.FileChannel)((java.nio.channels.FileChannel)c).force(true);else fd.sync();}catch(java.io.IOException e){throw new ErrnoException("fsync",5);}}
public static int read(java.io.FileDescriptor fd,byte[] b,int off,int len)throws ErrnoException {try{int n=((java.io.FileInputStream)handles.get(fd)).read(b,off,len);return n<0?0:n;}catch(java.io.IOException e){throw new ErrnoException("read",5);}}
public static int write(java.io.FileDescriptor fd,byte[] b,int off,int len)throws ErrnoException {try{((java.io.FileOutputStream)handles.get(fd)).write(b,off,len);return len;}catch(java.io.IOException e){throw new ErrnoException("write",5);}}
public static void close(java.io.FileDescriptor fd)throws ErrnoException {try{handles.remove(fd).close();paths.remove(fd);}catch(java.io.IOException e){throw new ErrnoException("close",5);}}
public static void rename(String a,String b)throws ErrnoException {try{java.nio.file.Files.move(java.nio.file.Paths.get(a),java.nio.file.Paths.get(b),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(java.io.IOException e){throw new ErrnoException("rename",5);}}
public static void remove(String p)throws ErrnoException {try{java.nio.file.Files.delete(java.nio.file.Paths.get(p));}catch(java.io.IOException e){throw new ErrnoException("unlink",5);}}
}''',
    'android/Manifest.java': 'package android; public final class Manifest { public static class permission {public static final String RECORD_AUDIO="mic";} }',
    'android/R.java': 'package android; public final class R {public static class style {public static final int Theme_Material_Light_NoActionBar=1;} public static class layout {public static final int simple_spinner_dropdown_item=1;} }',
    'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager {public static final int PERMISSION_GRANTED=0;}',
    'android/content/res/AssetManager.java': '''package android.content.res; public class AssetManager {
public static java.io.InputStream stream;
public java.io.InputStream open(String name)throws java.io.IOException{return stream!=null?stream:new java.io.ByteArrayInputStream("{fixture}".getBytes("UTF-8"));}}
''',
    'android/content/ContentResolver.java': '''package android.content; public class ContentResolver {
public static int calls;public static android.net.Uri seen;public static String order="";public static boolean fail;
public static java.io.InputStream stream;public static final RuntimeException SENTINEL=new RuntimeException("provider-sentinel");
public java.io.OutputStream openOutputStream(@org.jetbrains.annotations.NotNull android.net.Uri u,String mode)throws java.io.IOException{
calls++;seen=u;order+="open;";if(fail)throw SENTINEL;return new java.io.ByteArrayOutputStream();}
public java.io.InputStream openInputStream(@org.jetbrains.annotations.NotNull android.net.Uri u)throws java.io.IOException{
calls++;seen=u;order+="open;";if(fail)throw SENTINEL;return stream!=null?stream:new java.io.ByteArrayInputStream(new byte[0]);}}
''',
    'android/net/Uri.java': 'package android.net;public final class Uri {public static Uri parse(String s){return new Uri();}public String getScheme(){return "content";}}',
    'android/content/ComponentCallbacks2.java': 'package android.content; public interface ComponentCallbacks2 {void onConfigurationChanged(android.content.res.Configuration c);void onLowMemory();void onTrimMemory(int level);}',
    'android/content/res/Configuration.java': 'package android.content.res; public class Configuration {}',
    'android/content/Context.java': '''package android.content; public class Context {
private java.io.File dir; public Context(){this(new java.io.File("unused-next-context"));} public Context(java.io.File f){dir=f;}
public void registerComponentCallbacks(ComponentCallbacks2 c){} public Context getApplicationContext(){return this;} public java.io.File getFilesDir(){return dir;}
public java.io.File getNoBackupFilesDir(){return dir;}
public java.io.File getCacheDir(){return dir;} public ContentResolver getContentResolver(){ContentResolver.order+="receiver;";return new ContentResolver();}
public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}
public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public static final String INPUT_METHOD_SERVICE="ime"; public Object getSystemService(String n){return new android.view.inputmethod.InputMethodManager();}
public int permission; public int checkSelfPermission(String name){return permission;} public void startActivity(Intent intent){} }
''',
    'android/view/ContextThemeWrapper.java': 'package android.view; public class ContextThemeWrapper extends android.content.Context {public ContextThemeWrapper(android.content.Context c,int t){}}',
    'android/view/inputmethod/EditorInfo.java': 'package android.view.inputmethod; public class EditorInfo {public String packageName="fixture",fieldName="field";public int fieldId=1,inputType=1,imeOptions=0;}',
    'android/view/inputmethod/InputConnection.java': 'package android.view.inputmethod; public interface InputConnection {boolean commitText(CharSequence text,int n);}',
    'android/view/inputmethod/InputMethodManager.java': '''package android.view.inputmethod; public class InputMethodManager {
public int calls;public RuntimeException failure;public Runnable event=()->{};
public void showInputMethodPicker(){calls++;event.run();if(failure!=null)throw failure;}}
''',
    'android/widget/Toast.java': '''package android.widget; public class Toast {
public static final int LENGTH_SHORT=0;public static String text="";public static int shows;
public static RuntimeException failure;public static Error fatal;
public static Toast makeText(android.content.Context c,CharSequence t,int duration){if(fatal!=null)throw fatal;if(failure!=null)throw failure;text=t.toString();return new Toast();}
public void show(){shows++;}}
''',
    'android/inputmethodservice/InputMethodService.java': '''package android.inputmethodservice;
public class InputMethodService extends android.content.Context {
public void onCreate(){} public boolean onEvaluateFullscreenMode(){return false;}
public android.view.View onCreateInputView(){return null;} public void onStartInput(android.view.inputmethod.EditorInfo i,boolean r){}
public void onStartInputView(android.view.inputmethod.EditorInfo i,boolean r){} public void onWindowShown(){}
public void onFinishInputView(boolean f){} public void onFinishInput(){} public void onWindowHidden(){}
public void onUnbindInput(){} public void onDestroy(){}
// Opt-in host model, not an Android framework implementation: hide clears show intent;
// an accepted previous request changes selection but does not create a new show request.
public boolean visibilityModel,showRequested=true,previousVisible;public String selectedIme="asr";
public int acceptedRequests,activityCalls;public final java.util.List<String> modelEvents=new java.util.ArrayList<>();
public void startActivity(android.content.Intent intent){activityCalls++;modelEvents.add("activity");}
public int hides,previousCalls;public boolean previousResult=true,shown=true,hideChangesShown;
public Error previousFatal,hideFatal;
public RuntimeException hideFailure,previousFailure,serviceFailure;public boolean noManager;
public Runnable event=()->{};public final android.view.inputmethod.InputMethodManager manager=new android.view.inputmethod.InputMethodManager();
public android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();public android.view.inputmethod.InputConnection connection;
public void requestHideSelf(int f){hides++;event.run();if(hideFatal!=null)throw hideFatal;if(hideFailure!=null)throw hideFailure;if(hideChangesShown)shown=false;if(visibilityModel){showRequested=false;shown=false;previousVisible=false;modelEvents.add("hide:clear-show-intent");}}
public final boolean switchToPreviousInputMethod(){previousCalls++;event.run();if(previousFatal!=null)throw previousFatal;if(previousFailure!=null)throw previousFailure;if(visibilityModel){modelEvents.add("previous:"+previousResult);if(previousResult){acceptedRequests++;selectedIme="previous";previousVisible=showRequested;shown=false;}}return previousResult;}
public Object getSystemService(String n){if(serviceFailure!=null)throw serviceFailure;return noManager?null:manager;}
public android.view.inputmethod.EditorInfo getCurrentInputEditorInfo(){return info;}
public android.view.inputmethod.InputConnection getCurrentInputConnection(){return connection;} public boolean isInputViewShown(){return shown;}}
''',
    'android/widget/ArrayAdapter.java': 'package android.widget; public class ArrayAdapter<T> {public ArrayAdapter(android.content.Context c,int id,T[] values){}}',
    'android/widget/Spinner.java': 'package android.widget; public class Spinner extends android.view.View {public Spinner(android.content.Context c){} public void setAdapter(ArrayAdapter<?> a){} public Object getSelectedItem(){return "Chinese";}public void setEnabled(boolean b){}}',
    'org/json/JSONException.java': 'package org.json; public class JSONException extends Exception {public JSONException(String s){super(s);}}',
    'org/json/JSONObject.java': '''package org.json; public class JSONObject {
public JSONObject(String s)throws JSONException{} public JSONArray getJSONArray(String n)throws JSONException{return new JSONArray();}
public String getString(String n)throws JSONException{return n.equals("file")?"a":"0000000000000000000000000000000000000000000000000000000000000000";}
public long getLong(String n)throws JSONException{return 0;}}
''',
    'org/json/JSONArray.java': 'package org.json; public class JSONArray {public int length(){return 1;}public JSONObject getJSONObject(int n)throws JSONException{return new JSONObject("");}}',
    'org/llmasr/minimal/MainActivity.java': 'package org.llmasr.minimal; public class MainActivity {}',
    'org/llmasr/minimal/ModelManagementActivity.java': 'package org.llmasr.minimal; public class ModelManagementActivity {}',
    'org/llmasr/minimal/audio/ForegroundRecorder.java': '''package org.llmasr.minimal.audio; public class ForegroundRecorder {
public static Runnable beforeStart=()->{};public static int starts;public static int calls;public interface Progress{void update(double s);} public static byte[] capture(RecordingControl c,Progress p)throws java.io.IOException {
return capture(c,p,null);}
public static byte[] capture(RecordingControl c,Progress p,RecordingControl.StartGate gate)throws java.io.IOException {
calls++;try{beforeStart.run();boolean started=gate==null?c.start(()->{starts++;}):gate.start(()->{starts++;});if(!started)throw new java.util.concurrent.CancellationException();c.stop();PcmWave wave=new PcmWave();wave.append(new short[PcmWave.MIN_SAMPLES],PcmWave.MIN_SAMPLES);return wave.finish();}finally{c.captureReleased();}}}
''',
    'org/llmasr/minimal/transcription/AppReportWriter.java': '''package org.llmasr.minimal.transcription;
import org.llmasr.minimal.task.RequestContext; import org.llmasr.minimal.asr.NativeResponse;
public final class AppReportWriter implements AppRequestPolicy.Reports {
public Throwable cause;public boolean failSuccess,reportedAtSuccess;public int successes,failures;public RequestContext lastContext;
public AppReportWriter(org.llmasr.minimal.platform.OperationContext c){}
public void writePending(RequestContext c){} public void writeTerminal(RequestContext c){}public void writeCancel(RequestContext c){}
public void writeFailure(RequestContext c,Throwable t){cause=t;failures++;lastContext=c;}
public void writeSuccess(RequestContext c,NativeResponse r,java.io.File f,double s,String l,String source)throws java.io.IOException {
successes++;reportedAtSuccess=c.inferenceReported;lastContext=c;if(failSuccess)throw new java.io.IOException("synthetic report failure");}}
''',
}


def source(name):
    paths = [APP_BASE / (name + suffix) for suffix in ('.java', '.kt') if (APP_BASE / (name + suffix)).is_file()]
    require(len(paths) == 1, 'missing/duplicate source ' + name)
    return paths[0]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def stubs():
    result = dict(owned.STUBS)
    result.update(window.STUBS)
    result.pop('WindowContractTest.java')
    result.pop('org/llmasr/minimal/AppGraph.java')
    result.pop('org/llmasr/minimal/platform/ActivityWindow.java')
    result.update(STUBS)
    result['android/os/Looper.java'] = '''package android.os;
public class Looper {private static final Looper MAIN=new Looper();private static final Thread main=Thread.currentThread();
public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return Thread.currentThread()==main?MAIN:null;}}
'''
    result['android/os/Handler.java'] = '''package android.os;
public class Handler {
public static final java.util.List<Runnable> pending=new java.util.concurrent.CopyOnWriteArrayList<>();
public static final java.util.concurrent.Semaphore posted=new java.util.concurrent.Semaphore(0);
public static long delay;public static int delayed;public Handler(Looper l){}
public boolean post(Runnable r){pending.add(r);posted.release();return true;}
public boolean postDelayed(Runnable r,long ms){delay=ms;delayed++;return post(r);}
public void removeCallbacks(Runnable r){pending.removeIf(x->x==r);}
public static void tick(){if(pending.size()!=1)throw new AssertionError("one pending refresh");pending.remove(0).run();}
public static void drain(){for(Runnable r:pending.toArray(new Runnable[0]))if(pending.remove(r))r.run();}}
'''
    result['android/app/Activity.java'] = '''package android.app; public class Activity extends android.content.Context {
public android.view.Window getWindow(){return new android.view.Window();}
public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher(){return new android.window.OnBackInvokedDispatcher();}
public void setContentView(android.view.View v){} public void finish(){}public boolean isFinishing(){return false;}public boolean isDestroyed(){return false;}
protected void onCreate(android.os.Bundle b){} protected void onResume(){}protected void onPause(){}protected void onDestroy(){}
protected void onActivityResult(int a,int b,android.content.Intent i){} public void onBackPressed(){} public static final int RESULT_OK=-1;
public void startActivityForResult(android.content.Intent i,int c){} }
'''
    result['android/window/OnBackInvokedDispatcher.java'] = '''package android.window; public class OnBackInvokedDispatcher {
public static final int PRIORITY_DEFAULT=0;public static int calls;public static OnBackInvokedCallback seen;
public static final RuntimeException SENTINEL=new RuntimeException("back-sentinel");
public void registerOnBackInvokedCallback(int n,OnBackInvokedCallback c){}
public void unregisterOnBackInvokedCallback(@org.jetbrains.annotations.NotNull OnBackInvokedCallback c){calls++;seen=c;throw SENTINEL;}}
'''
    result['android/content/Intent.java'] = '''package android.content; public class Intent {
public static final String ACTION_CREATE_DOCUMENT="create",CATEGORY_OPENABLE="open",EXTRA_TITLE="title";public static final int FLAG_ACTIVITY_NEW_TASK=1;
public Intent(String a){}public Intent(Context c,Class<?> t){}public Intent addFlags(int f){return this;}
public Intent setType(String t){return this;}public Intent addCategory(String c){return this;}public Intent putExtra(String k,String v){return this;}
public android.net.Uri getData(){return null;}}
'''
    result['android/graphics/Color.java'] = 'package android.graphics; public class Color {public static final int TRANSPARENT=0,WHITE=-1;public static int rgb(int a,int b,int c){return 0;}}'
    result['android/view/View.java'] = result['android/view/View.java'].replace('public class View {', '''public class View {
public static final int FOCUS_DOWN=130;public interface OnClickListener{void onClick(View v);}
public android.content.Context getContext(){return new android.content.Context();}public void setBackgroundColor(int c){}
public boolean post(Runnable r){return true;}
''')
    result['android/widget/TextView.java'] = '''package android.widget; public class TextView extends android.view.View {
private String text="";public TextView(android.content.Context c){}public void setText(CharSequence s){text=s.toString();}public CharSequence getText(){return text;}public void setTextSize(float n){}public void setTextColor(int n){}public void setTextIsSelectable(boolean v){} }
'''
    result['android/widget/Button.java'] = '''package android.widget; public class Button extends TextView {
public Button(android.content.Context c){super(c);}public void setAllCaps(boolean v){}public void setMinHeight(int v){}
private android.view.View.OnClickListener listener;private boolean enabled=true;
public void setMinWidth(int v){}public void setMinimumWidth(int v){}public void setOnClickListener(android.view.View.OnClickListener l){listener=l;}public void setEnabled(boolean b){enabled=b;}
public boolean isEnabled(){return enabled;}public boolean performClick(){if(!enabled)return false;listener.onClick(this);return true;} }
'''
    result['android/widget/LinearLayout.java'] = '''package android.widget; public class LinearLayout extends android.view.View {
public static final int VERTICAL=1;public final java.util.List<android.view.View> children=new java.util.ArrayList<>();
public LinearLayout(android.content.Context c){}public void setOrientation(int n){}
public void addView(android.view.View v){children.add(v);}public void addView(android.view.View v,LayoutParams p){children.add(v);}
public static class LayoutParams {public LayoutParams(int w,int h){}public LayoutParams(int w,int h,float f){}} }
'''
    result['android/widget/ScrollView.java'] = '''package android.widget;public class ScrollView extends android.view.View {
public ScrollView(android.content.Context c){}public void addView(android.view.View v){}public void setFillViewport(boolean b){}public boolean fullScroll(int n){return true;}}
'''
    return result


def prepare(directory):
    sources = []
    # The canonical host list is owned by the existing shell entry, not duplicated.
    import re
    body, = re.findall(r'^host_classes=\(\n(.*?)^\)', (ROOT / 'scripts/test-minimal-apk.sh').read_text(), re.M | re.S)
    production = [source(name.replace('.', '/')) for name in body.split()]
    production += [source(name) for name in SELECTED if source(name) not in production]
    production += [source('platform/OperationContext'), source('ime/ImeBackend'), source('model/AndroidModelVerificationIo'), source('model/SafModelZipSource')]
    for path in production:
        target = directory / 'production' / path.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        sources.append(target)
    migrated = ('MinimalApkTest', 'ResultFilesTest', 'NativeResponseTest', 'ModelOperationControlTest',
                'ModelRepositoryDeleteTest', 'ModelPrivateRootTest', 'TextExportFatalTest', 'TextExportSafetyTest',
                'AdmissionBoundaryTest', 'ImeSessionTest', 'InferenceAdapterTest', 'LogExportTest', 'ModelAndroidHelpersTest', 'ModelManagementControllerTest', 'ModelNotificationTest', 'ModelProviderBoundaryTest', 'ModelReadinessTest', 'ModelRepositoryCancelTest', 'ModelRepositoryTest', 'ModelZipImportTest', 'ModelReviewFixTest', 'RecordingRaceTest', 'RequestRunnerTest', 'RuntimeLogCoreTest', 'RuntimeLogStoreErrorTest', 'RuntimeLogWorkerErrorTest', 'TaskCoordinatorTest', 'TextExportTest')
    callers = sorted((ROOT / 'tests').glob('*.java'))
    for name in migrated:
        matches = [ROOT / 'tests' / (name + suffix) for suffix in ('.java', '.kt')
                   if (ROOT / 'tests' / (name + suffix)).is_file()]
        require(len(matches) == 1 and matches[0].suffix == '.kt', 'missing/duplicate migrated caller ' + name)
        callers.append(matches[0])
    for path in callers:
        target = directory / 'callers' / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        sources.append(target)
    fixture = ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java'
    target = directory / 'callers' / fixture.name
    shutil.copyfile(fixture, target)
    sources.append(target)
    for name, text in stubs().items():
        target = directory / 'fakes' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text + '\n')
        sources.append(target)
    return sources


def identity(path):
    data = path.read_bytes()
    return {'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def suite(directory, green_only):
    directory.mkdir(parents=True, exist_ok=False)
    sources = prepare(directory)
    (directory / 'inputs.json').write_text(json.dumps([identity(p) for p in sources], indent=2) + '\n')
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed stdlib missing')

    def compile_run(label, mode='all', diagnostic=None):
        classes = directory / ('classes-' + label)
        require(not classes.exists(), 'fresh class output required')
        result = owned.execute(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '',
                                *map(str, sources)], directory, label + '-compile', 270)
        require(result.returncode == 0, 'compilation failure NOT target red ' + label + '\n' + result.stderr.decode())
        result = owned.execute(['java', '-cp', str(classes) + ':' + str(stdlib), 'NextAppContractTest', mode],
                               directory, label + '-run', 45)
        if diagnostic is None:
            require(result.returncode == 0 and b'PASS next real app/controller host contracts' in result.stdout,
                    'contract green failed ' + label + '\n' + result.stderr.decode())
        else:
            require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic).encode() in result.stderr,
                    'not exact behavioral red ' + label + '\n' + result.stderr.decode())
        return classes

    baseline_classes = compile_run('green')
    abi = owned.execute(['java', '-cp', str(baseline_classes) + ':' + str(stdlib), 'KotlinMigrationTest'],
                        directory, 'controller-java-abi', 45)
    require(abi.returncode == 0 and b'PASS next Controller Java ABI/nullable wiring' in abi.stdout,
            'Controller Java ABI failed\n' + abi.stderr.decode())
    if not green_only:
        mutations = [
            ('modelmanagement/ModelManagementController', 'fallback', 'if (fallbackKind != null) safeLog(fallbackKind, op.id, "failed")', 'if (false && fallbackKind != null) safeLog(fallbackKind, op.id, "failed")', 'maintenance rejection terminal missing: IMPORT'),
            ('modelmanagement/ModelManagementController', 'notification', 'state.notifyListeners()', 'synchronized(this) { state.notifyListeners() }', 'INSPECT listeners must be outside controller monitor'),
            ('AppGraph', 'graph', 'if (appContext == null) throw IllegalArgumentException("appContext required")', 'if (INSTANCE != null) return\n            if (appContext == null) throw IllegalArgumentException("appContext required")', 'installed Graph must still reject null context before fast return'),
            ('transcription/AsrOperation', 'operation', 'reports!!.writeSuccess(ctx, r, wav, seconds, language, source)', 'ctx.inferenceReported = true\n        reports!!.writeSuccess(ctx, r, wav, seconds, language, source)', 'success report must precede inferenceReported'),
            ('AsrImeService', 'service', 'visible = false; invalidate(); render(); super.onFinishInputView(finishingInput)', 'visible = false; render(); super.onFinishInputView(finishingInput)', 'Service finishView must invalidate old session'),
        ]
        for owner, mode, before, after, diagnostic in mutations:
            candidates = [p for p in sources if p.stem == Path(owner).name and p.suffix == '.kt']
            require(len(candidates) == 1, 'mutant requires Kotlin production ' + owner)
            target = candidates[0]
            original = target.read_bytes()
            text = original.decode()
            if mode == 'notification':
                # pending INSPECT's notification only, not arbitrary all callbacks.
                marker = 'if (startKind != null) safeLog(startKind, op.id, "ok")\n        state.notifyListeners()'
                before, after = marker, marker.replace('state.notifyListeners()', 'synchronized(this) { state.notifyListeners() }')
            require(text.count(before) == 1, 'mutant anchor not unique ' + mode)
            try:
                target.write_text(text.replace(before, after, 1))
                compile_run(mode + '-red', mode, diagnostic)
            finally:
                # execute raises on unconfirmed teardown: preserve the exact input tree.
                record = directory / (mode + '-red-compile.json')
                state = json.loads(record.read_text()) if record.exists() else {}
                run_record = directory / (mode + '-red-run.json')
                run_state = json.loads(run_record.read_text()) if run_record.exists() else state
                if state.get('owned_exited') and run_state.get('owned_exited'):
                    target.write_bytes(original)
            require(target.read_bytes() == original, 'restoration refused or failed ' + mode)
            compile_run(mode + '-restored', mode)
            print('PASS next production mutant compile0/exact assertion1/restored0 ' + mode, flush=True)
    (directory / 'result.json').write_text(json.dumps({'pass': True, 'green_only': green_only,
        'driver': identity(Path(__file__)), 'caller': identity(ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java'),
        'not_android': True}, indent=2) + '\n')
    print('PASS next real production migration evidence ' + str(directory), flush=True)


def compat_suite(directory):
    """Maintained compatibility route; 3 compiles + 6 runs, independent of default five mutants."""
    directory.mkdir(parents=True, exist_ok=False)
    sources = prepare(directory)
    (directory / 'inputs.json').write_text(json.dumps([identity(p) for p in sources], indent=2) + '\n')
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'

    def compile_current(label):
        classes = directory / ('classes-' + label)
        result = owned.execute(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '',
                                *map(str, sources)], directory, label + '-compile', 270)
        require(result.returncode == 0, 'compat compilation failure NOT target red ' + label + '\n' + result.stderr.decode())
        return classes

    def run(classes, label, mode, diagnostic=None):
        result = owned.execute(['java', '-cp', str(classes) + ':' + str(stdlib), 'NextAppContractTest', mode],
                               directory, label + '-run', 45)
        if diagnostic is None:
            require(result.returncode == 0 and b'PASS next real app/controller host contracts' in result.stdout,
                    'compat green failed ' + label + '\n' + result.stderr.decode())
        else:
            require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic).encode() in result.stderr,
                    'compat not exact external red ' + label + '\n' + result.stderr.decode())

    run(compile_current('compat-green'), 'compat-green', 'compat-all')
    mutations = {
        'AppGraph': [('unchanged<android.net.Uri>(target)', 'target!!', 2)],
        'AsrOperation': [('unchanged<Uri>(uri)', 'uri!!', 1)],
        'ActivityWindow': [('unchanged<android.window.OnBackInvokedCallback>(callback as android.window.OnBackInvokedCallback?)',
                            'callback as android.window.OnBackInvokedCallback', 1)],
    }
    originals = {}
    for name, replacements in mutations.items():
        targets = [p for p in sources if p.stem == name and p.suffix == '.kt']
        require(len(targets) == 1, 'compat Kotlin owner missing/duplicate ' + name)
        target = targets[0]
        originals[target] = target.read_bytes()
        text = originals[target].decode()
        for before, after, count in replacements:
            require(text.count(before) == count, 'compat unique target count ' + name)
            text = text.replace(before, after)
        target.write_text(text)
    try:
        classes = compile_current('earlycheck')
        for mode, diagnostic in [('graph-log', 'Graph log null must reach receiver'),
                                  ('graph-text', 'Graph text null must reach receiver'),
                                  ('wave-null', 'Wave null must reach receiver'),
                                  ('window-null', 'Window null must reach receiver')]:
            run(classes, 'earlycheck-' + mode, 'compat-' + mode, diagnostic)
            print('PASS compat production earlycheck compile0/exact external assertion1 ' + mode, flush=True)
    finally:
        # Unconfirmed owned exit preserves the exact mutant inputs for diagnosis.
        records = [json.loads(p.read_text()) for p in directory.glob('earlycheck*.json')]
        if records and all(record.get('owned_exited') for record in records):
            for target, original in originals.items():
                target.write_bytes(original)
    require(all(p.read_bytes() == original for p, original in originals.items()), 'compat restoration refused or mismatched')
    run(compile_current('compat-restored'), 'compat-restored', 'compat-all')
    (directory / 'result.json').write_text(json.dumps({'pass': True, 'compat_only': True,
        'driver': identity(Path(__file__)), 'caller': identity(ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java'),
        'compiles': 3, 'runs': 6, 'compile_timeout': 270, 'run_timeout': 45,
        'not_android': True}, indent=2) + '\n')
    print('PASS maintained production self/distinct/close compatibility and four nullable SDK receivers ' + str(directory), flush=True)


def ime_return_suite(directory):
    """Independent bounded IME slice: green + four exact reds/restores = 9 compiles/runs."""
    directory.mkdir(parents=True, exist_ok=False)
    sources = prepare(directory)
    (directory / 'inputs.json').write_text(json.dumps([identity(p) for p in sources], indent=2) + '\n')
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed stdlib missing')
    attempted = []
    java_tmp = directory / 'java-tmp'
    java_tmp.mkdir()

    def execute(argv, label, timeout):
        # Register before invoking the runner, including launch/observer failures.
        attempted.append(directory / (label + '.json'))
        (directory / 'attempted.json').write_text(json.dumps([str(p) for p in attempted], indent=2) + '\n')
        return owned.execute(argv, directory, label, timeout)

    def quiescent():
        for path in attempted:
            try:
                record = json.loads(path.read_text())
            except (OSError, ValueError):
                return False
            if record.get('owned_exited') is not True or record.get('root_reaped') is not True:
                return False
        return True

    def compile_run(label, mode='ime-return', diagnostic=None):
        classes = directory / ('classes-' + label)
        require(not classes.exists(), 'fresh IME class output required')
        compiled = execute(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '',
                            *map(str, sources)], label + '-compile', 270)
        require(compiled.returncode == 0, 'IME compilation failure NOT target red ' + label + '\n' + compiled.stderr.decode())
        result = execute(['java', '-Djava.io.tmpdir=' + str(java_tmp), '-cp', str(classes) + ':' + str(stdlib), 'NextAppContractTest', mode],
                         label + '-run', 45)
        if diagnostic is None:
            require(result.returncode == 0 and b'PASS next real app/controller host contracts' in result.stdout,
                    'IME green failed ' + label + '\n' + result.stderr.decode())
        else:
            require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic).encode() in result.stderr,
                    'IME not exact behavioral red ' + label + '\n' + result.stderr.decode())

    mutations = [
        ('AsrImeService', 'ime-fallback', 'if (!accepted) showKeyboardPicker()', 'if (false && !accepted) showKeyboardPicker()',
         'previous failure must launch picker once'),
        ('AsrImeService', 'ime-invalidation', 'visible = false; invalidate() // Return invalidates without clearing platform show intent.',
         'visible = false // MUTANT: old return transaction left live.', 'external switch must invalidate before platform request'),
        ('ImeController', 'ime-hide',
         'try { hide!!.run() } catch (ignored: RuntimeException) { /* Best-effort hide must not suppress the explicit action. */ }',
         'hide!!.run()', 'hide failure must not suppress external attempt'),
        ('AsrImeService', 'ime-restart', 'cancel!!.setEnabled(alive && controller != null)', 'cancel!!.setEnabled(allowed)',
         'visible failed switch must allow manual fresh restart'),
    ]
    primary = None
    try:
        compile_run('ime-green')
        for name, mode, before, after, diagnostic in mutations:
            targets = [p for p in sources if p.stem == name and p.suffix == '.kt']
            require(len(targets) == 1, 'IME unique production owner ' + name)
            target = targets[0]; original = target.read_bytes(); text = original.decode()
            require(text.count(before) == 1, 'IME mutant unique anchor ' + mode)
            try:
                target.write_text(text.replace(before, after, 1))
                compile_run(mode + '-red', mode, diagnostic)
            finally:
                # Missing/corrupt/unknown records are never evidence of exited readers.
                if quiescent():
                    target.write_bytes(original)
            require(target.read_bytes() == original, 'IME restoration refused or failed ' + mode)
            compile_run(mode + '-restored', mode)
            print('PASS IME production mutant compile0/exact assertion1/restored0 ' + mode, flush=True)
        require(quiescent(), 'IME owned exit unconfirmed; retain exact inputs')
        (directory / 'result.json').write_text(json.dumps({'pass': True, 'ime_return_only': True,
            'driver': identity(Path(__file__)), 'caller': identity(ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java'),
            'compiles': 9, 'runs': 9, 'compile_timeout': 270, 'run_timeout': 45, 'not_android': True}, indent=2) + '\n')
        print('PASS independent IME return production evidence ' + str(directory), flush=True)
    except BaseException as failure:
        primary = failure
        raise
    finally:
        # Keep raw evidence and preserve the primary failure even if its ledger is unknown.
        try:
            owned.cleanup_workspace(directory, probe=quiescent, remove=False)
        except BaseException as cleanup_failure:
            if primary is None:
                raise
            primary.add_note('cleanup refused; retained ' + str(directory) + ': ' + repr(cleanup_failure))


def ime_model_ready_suite(directory, service_source=None, expect_red=False, mutation_names=None, transaction_mutants=False):
    """Independent seven-compile/JVM slice; old-source replay is one exact red only."""
    directory.mkdir(parents=True, exist_ok=False)
    sources = prepare(directory)
    targets = [p for p in sources if p.stem == 'AsrImeService' and p.suffix == '.kt']
    require(len(targets) == 1, 'readiness unique real Service source')
    target = targets[0]
    if service_source is not None:
        shutil.copyfile(service_source, target)
    (directory / 'inputs.json').write_text(json.dumps([identity(p) for p in sources], indent=2) + '\n')
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed stdlib missing')
    attempted = []
    java_tmp = directory / 'java-tmp'
    java_tmp.mkdir()

    def execute(argv, label, timeout):
        attempted.append(directory / (label + '.json'))
        (directory / 'attempted.json').write_text(json.dumps([str(p) for p in attempted], indent=2) + '\n')
        return owned.execute(argv, directory, label, timeout)

    def quiescent():
        for path in attempted:
            try:
                record = json.loads(path.read_text())
            except (OSError, ValueError):
                return False
            if (not isinstance(record, dict)
                    or record.get('owned_exited') is not True or record.get('root_reaped') is not True
                    or record.get('runner') != 'owned-linux-session-v1'
                    or type(record.get('exit')) is not int
                    or 'primary' not in record
                    or (record['primary'] is not None and not isinstance(record['primary'], str))):
                return False
        return True

    def compile_run(label, diagnostic=None):
        classes = directory / ('classes-' + label)
        require(not classes.exists(), 'fresh readiness class output required')
        compiled = execute(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '',
                            *map(str, sources)], label + '-compile', 270)
        require(compiled.returncode == 0, 'readiness compilation failure NOT target red ' + label + '\n' + compiled.stderr.decode())
        result = execute(['java', '-Djava.io.tmpdir=' + str(java_tmp), '-cp', str(classes) + ':' + str(stdlib),
                          'NextAppContractTest', 'ime-model-ready'], label + '-run', 45)
        if diagnostic is None:
            require(result.returncode == 0 and b'PASS next real app/controller host contracts ime-model-ready' in result.stdout,
                    'readiness green failed ' + label + '\n' + result.stderr.decode())
        else:
            require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic).encode() in result.stderr,
                    'readiness not exact behavioral red ' + label + '\n' + result.stderr.decode())

    mutations = [
        ('proof-only-render', ' && !busy && wired)', ' && !busy && wired && graph?.readiness()?.snapshot()?.ready() == true)',
         'first-use record must be enabled without proof or resident'),
        ('stale-click-admission', 'service.isInputViewShown && service.permitted()', 'true',
         'permission change during prepare must prevent capture'),
        ('own-busy-priority', 'else if (busy && controller!!.ownsTask()) session!!.status()',
         'else if (busy && controller!!.ownsTask()) "模型未准备，请打开模型管理。"',
         'owned IME task must retain session progress while resident active'),
    ]
    if transaction_mutants:
        mutations = [
            ('omit-preparation', 'backend!!.prepare(control) { stage -> status(session, rev, stage) }',
             'backend!! // MUTANT: no explicit prepare', 'automatic SHA durable proof then prepare then capture'),
            ('repeat-restore', 'val current = try { requireReady(); true } catch (unverified: IOException) { false }',
             'val current = false', 'valid current proof reuses resident without restore or SHA'),
            ('premature-capturing', 'phase = Phase.PREPARING', 'phase = Phase.CAPTURING', 'preparing is not capturing'),
        ]
    primary = None
    try:
        if expect_red:
            compile_run('old-source-red', 'first-use record must be enabled without proof or resident')
        else:
            compile_run('ready-green')
            for mode, before, after, diagnostic in mutations:
                if mutation_names is not None and mode not in mutation_names:
                    continue
                candidates = [p for p in sources if p.suffix == '.kt' and before in p.read_text()]
                require(len(candidates) == 1, 'unique production mutation owner ' + mode)
                target = candidates[0]
                original = target.read_bytes(); text = original.decode()
                require(text.count(before) == 1, 'readiness mutant unique anchor ' + mode)
                mutant_primary = None
                try:
                    target.write_text(text.replace(before, after, 1))
                    compile_run(mode + '-red', diagnostic)
                except BaseException as failure:
                    mutant_primary = failure
                    raise
                finally:
                    try:
                        if quiescent():
                            target.write_bytes(original)
                    except BaseException as restore_failure:
                        if mutant_primary is None:
                            raise
                        mutant_primary.add_note('restoration refused; retained ' + str(target) + ': ' + repr(restore_failure))
                require(target.read_bytes() == original, 'readiness restoration refused or failed ' + mode)
                compile_run(mode + '-restored')
                print('PASS readiness production mutant compile0/exact assertion1/restored0 ' + mode, flush=True)
        require(quiescent(), 'readiness owned exit unconfirmed; retain exact inputs')
        (directory / 'result.json').write_text(json.dumps({'pass': True, 'ime_model_ready_only': True,
            'expected_old_source_red': expect_red, 'source': identity(target),
            'source_override': identity(service_source) if service_source is not None else None,
            'driver': identity(Path(__file__)), 'caller': identity(ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java'),
            'tools': {name: identity(Path(shutil.which(name)).resolve()) for name in ('java', 'javac', 'kotlinc')},
            'stdlib': identity(stdlib), 'compiles': 1 if expect_red else 1 + 2 * (3 if mutation_names is None else len(mutation_names)),
            'runs': 1 if expect_red else 1 + 2 * (3 if mutation_names is None else len(mutation_names)),
            'mutation_names': mutation_names, 'transaction_mutants': transaction_mutants,
            'compile_timeout': 270, 'run_timeout': 45, 'not_android': True}, indent=2) + '\n')
        print('PASS independent IME readiness ' + ('old-source exact RED' if expect_red else 'green/mutants/restores') + ' evidence ' + str(directory), flush=True)
    except BaseException as failure:
        primary = failure
        raise
    finally:
        try:
            owned.cleanup_workspace(directory, probe=quiescent, remove=False)
        except BaseException as cleanup_failure:
            if primary is None:
                raise
            primary.add_note('cleanup refused; retained ' + str(directory) + ': ' + repr(cleanup_failure))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--green-only', action='store_true')
    modes.add_argument('--compat-only', action='store_true')
    modes.add_argument('--ime-return-only', action='store_true')
    modes.add_argument('--ime-model-ready-only', action='store_true')
    modes.add_argument('--ime-first-use-only', action='store_true')
    parser.add_argument('--evidence-dir', type=Path)
    parser.add_argument('--ime-service-source', type=Path)
    parser.add_argument('--expect-ime-model-ready-red', action='store_true')
    args = parser.parse_args()
    if not (args.ime_model_ready_only or args.ime_first_use_only) and (args.evidence_dir is not None or args.ime_service_source is not None or args.expect_ime_model_ready_red):
        parser.error('readiness evidence/source/red options require --ime-model-ready-only')
    if args.expect_ime_model_ready_red and args.ime_service_source is None:
        parser.error('--expect-ime-model-ready-red requires explicit --ime-service-source')
    if args.ime_first_use_only and (args.ime_service_source is not None or args.expect_ime_model_ready_red):
        parser.error('first-use transaction mode does not accept Service overrides')
    if args.ime_model_ready_only or args.ime_first_use_only:
        if args.evidence_dir is None:
            parser.error('--ime-model-ready-only requires exclusive --evidence-dir')
        ime_model_ready_suite(args.evidence_dir.resolve(),
                              args.ime_service_source.resolve() if args.ime_service_source is not None else None,
                              args.expect_ime_model_ready_red, transaction_mutants=args.ime_first_use_only)
        return
    parent = ROOT / ('.work/ime-build/contracts' if args.ime_return_only else '.work/ap5next-r1-fix/contracts')
    parent.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix='run-', dir=parent)) / 'evidence'
    if args.ime_return_only:
        ime_return_suite(directory)
    elif args.compat_only:
        compat_suite(directory)
    else:
        suite(directory, args.green_only)


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, owned.interrupted_by_signal)
    main()
