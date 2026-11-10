#!/usr/bin/env python3
"""Compile the three real Activities and call their listeners/Back/lifecycle on bounded host fakes.

This is not Android task, Binder, grant, microphone or native acceptance. Each
mutant is a copied production edit, compiled successfully before an exact JVM red.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'android/app/src/org/llmasr/minimal'


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


picker = load('_nav_picker', ROOT / 'tests/model_zip_picker_test.py')
mixed = picker.fixtures
owned = mixed.owned
lifecycle = load('_nav_lifecycle', ROOT / 'tests/mixed_compiler_lifecycle.py')


def require(value, message):
    if not value:
        raise AssertionError(message)


def stubs():
    s = picker.stubs()
    for name in ('org/llmasr/minimal/ZipPickerContractTest.java', 'org/llmasr/minimal/MainActivity.java',
                 'org/llmasr/minimal/LogsActivity.java', 'org/llmasr/minimal/ModelManagementActivity.java'):
        s.pop(name, None)
    s['android/R.java'] = 'package android;public class R {public static class attr{public static int state_enabled=1;}public static class layout{public static int simple_spinner_dropdown_item=1;}}'
    s['android/os/Bundle.java'] = '''package android.os;public class Bundle {public final java.util.Map<String,Object> values=new java.util.HashMap<>();public String getString(String k){return (String)values.get(k);}public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public void putString(String k,String v){values.put(k,v);}public void putInt(String k,int v){values.put(k,v);}}'''
    s['android/content/Intent.java'] = '''package android.content;public class Intent {
public static final String ACTION_OPEN_DOCUMENT="open-document",ACTION_OPEN_DOCUMENT_TREE="tree",ACTION_CREATE_DOCUMENT="create",ACTION_SEND="send",ACTION_SCREEN_OFF="off",CATEGORY_OPENABLE="openable",EXTRA_ALLOW_MULTIPLE="multiple",EXTRA_MIME_TYPES="mimes",EXTRA_TEXT="text",EXTRA_TITLE="title";
public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_ACTIVITY_CLEAR_TOP=2,FLAG_ACTIVITY_NEW_TASK=4;
public Class<?> target;public String action,type;public int flags;private android.net.Uri uri;private ClipData clip;
public Intent(){}public Intent(String a){action=a;}public Intent(Context c,Class<?> t){target=t;}public Intent addFlags(int f){flags|=f;return this;}public Intent addCategory(String c){return this;}public Intent setType(String t){type=t;return this;}
public Intent putExtra(String k,boolean v){return this;}public Intent putExtra(String k,String[] v){return this;}public Intent putExtra(String k,String v){return this;}public static Intent createChooser(Intent i,String t){return i;}
public android.net.Uri getData(){return uri;}public Intent setData(android.net.Uri u){uri=u;return this;}public ClipData getClipData(){return clip;}public Intent setClipData(ClipData c){clip=c;return this;}}
'''
    s['android/content/Context.java'] = '''package android.content;public class Context {
public static final String KEYGUARD_SERVICE="key",POWER_SERVICE="power",INPUT_METHOD_SERVICE="ime",CLIPBOARD_SERVICE="clip";public static final int RECEIVER_NOT_EXPORTED=4;
public Object getSystemService(String n){if(n.equals(KEYGUARD_SERVICE))return new android.app.KeyguardManager();if(n.equals(POWER_SERVICE))return new android.os.PowerManager();if(n.equals(CLIPBOARD_SERVICE))return new ClipboardManager();return new android.view.inputmethod.InputMethodManager();}
public Context getApplicationContext(){return this;}public int permission;public int checkSelfPermission(String s){return permission;}public ContentResolver getContentResolver(){return new ContentResolver();}public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public void registerReceiver(BroadcastReceiver r,IntentFilter i){}public void registerReceiver(BroadcastReceiver r,IntentFilter i,int f){}public void unregisterReceiver(BroadcastReceiver r){}public void startActivity(Intent i){} }
'''
    s['android/app/Activity.java'] = '''package android.app;public class Activity extends android.content.Context {
public static final int RESULT_OK=-1,RESULT_CANCELED=0;public android.view.View content;public android.content.Intent launched;public int request,permissions,resultLaunches;public RuntimeException launchFailure;public boolean changing,root,finished,destroyed;public final java.util.List<android.content.Intent> starts=new java.util.ArrayList<>();
protected void onCreate(android.os.Bundle b){}protected void onResume(){}protected void onPause(){}protected void onStop(){}protected void onDestroy(){destroyed=true;}protected void onNewIntent(android.content.Intent i){}protected void onSaveInstanceState(android.os.Bundle b){}protected void onActivityResult(int a,int b,android.content.Intent i){}
public void onRequestPermissionsResult(int r,String[] p,int[] g){}public void requestPermissions(String[] p,int r){permissions++;}
public boolean isTaskRoot(){return root;}public boolean isDestroyed(){return destroyed;}public boolean isFinishing(){return finished;}public boolean isChangingConfigurations(){return changing;}public void onBackPressed(){finish();}public void finish(){finished=true;}
public void setContentView(android.view.View v){content=v;}public android.view.Window getWindow(){return new android.view.Window();}public void startActivity(android.content.Intent i){starts.add(i);}public void startActivityForResult(android.content.Intent i,int c){if(launchFailure!=null)throw launchFailure;launched=i;request=c;resultLaunches++;}}
'''
    s['android/app/AlertDialog.java'] = '''package android.app;public class AlertDialog {public static Click positive,neutral,negative;public static String title,message;public static int shows,dismisses;private Dismiss listener;private boolean showing;private static long eventOrder;public long shownOrder,deliveredOrder;public int deliveredCount;public Dismiss lastDeliveredListener;public AlertDialog lastDeliveredDialog;public interface Click{void click(Object d,int w);}public interface Dismiss{void dismiss(Object dialog);}public void setOnDismissListener(Dismiss d){listener=d;}public Dismiss pendingDismissListener(){return listener;}public void show(){shows++;showing=true;shownOrder=++eventOrder;}public boolean isShowing(){return showing;}public void closeWindowWithoutNotification(){showing=false;dismisses++;}public void deliverDismissNotification(){Dismiss d=listener;listener=null;if(d!=null){d.dismiss(this);deliveredCount++;lastDeliveredListener=d;lastDeliveredDialog=this;deliveredOrder=++eventOrder;}}public void dismiss(){if(!showing)return;closeWindowWithoutNotification();deliverDismissNotification();}public static class Builder {public Builder(android.content.Context a){positive=neutral=negative=null;}public Builder setTitle(String s){title=s;return this;}public Builder setMessage(String s){message=s;return this;}public Builder setView(android.view.View v){return this;}public Builder setNegativeButton(String s,Click c){negative=c;return this;}public Builder setNeutralButton(String s,Click c){neutral=c;return this;}public Builder setPositiveButton(String s,Click c){positive=c;return this;}public AlertDialog create(){return new AlertDialog();}public void show(){create().show();}}}'''
    s['android/content/ActivityNotFoundException.java'] = 'package android.content;public class ActivityNotFoundException extends RuntimeException {}'
    s['android/content/ClipData.java'] = 'package android.content;public class ClipData {public int getItemCount(){return 1;}public static ClipData newPlainText(String l,CharSequence s){return new ClipData();}}'
    s['android/content/ClipboardManager.java'] = 'package android.content;public class ClipboardManager {public void setPrimaryClip(ClipData c){}}'
    s['android/provider/Settings.java'] = 'package android.provider;public class Settings {public static final String ACTION_INPUT_METHOD_SETTINGS="ime-settings";}'
    s['android/text/InputFilter.java'] = 'package android.text;public interface InputFilter {public static class LengthFilter implements InputFilter {public LengthFilter(int n){}}}'
    s['android/widget/EditText.java'] = 'package android.widget;public class EditText extends TextView {public EditText(android.content.Context c){super(c);}public void setMinLines(int n){}public void setFilters(android.text.InputFilter[] f){}}'
    s['android/widget/Spinner.java'] = 'package android.widget;public class Spinner extends android.view.View {private int selected;public Spinner(android.content.Context c){}public void setAdapter(ArrayAdapter<?> a){}public void setSelection(int n){selected=n;}public int getSelectedItemPosition(){return selected;}public void setEnabled(boolean b){}public void setMinimumHeight(int n){}}'
    s['android/widget/ScrollView.java'] = s['android/widget/ScrollView.java'].replace('public void setFillViewport', 'public boolean fullScroll(int n){return true;}public void setFillViewport')
    s['android/view/View.java'] = s['android/view/View.java'].replace('VISIBLE=0,GONE=8', 'VISIBLE=0,GONE=8,FOCUS_DOWN=130').replace('public void setContentDescription(CharSequence s){}', 'public CharSequence description;public CharSequence getContentDescription(){return description;}public void setContentDescription(CharSequence s){description=s;}public boolean post(Runnable r){r.run();return true;}')
    s['android/widget/TextView.java'] = s['android/widget/TextView.java'].replace('setTextSize(int n)', 'setTextSize(float n)')
    s['org/llmasr/minimal/AppGraph.java'] = GRAPH
    s['org/llmasr/minimal/NavigationContractTest.java'] = HARNESS
    return s


GRAPH = '''package org.llmasr.minimal;
import java.util.*;import org.llmasr.minimal.model.*;import org.llmasr.minimal.modelmanagement.*;import org.llmasr.minimal.task.*;import org.llmasr.minimal.transcription.*;import org.llmasr.minimal.diagnostics.*;import org.llmasr.minimal.audio.*;
public class AppGraph {public static AppGraph current;public static void install(android.content.Context c)throws java.io.IOException{}public static AppGraph get(){return current;}
public final ModelManagementController controller;public final ModelRepository repository;public final ModelReadiness ready;public final TaskCoordinator owner;public final ModelPickerTickets tickets=new ModelPickerTickets();public final AppState state=new AppState();public final Asr asr=new Asr();public final List<Runnable> exports=new ArrayList<>();public volatile int opens;public volatile boolean blockClose,failClose;public final java.util.concurrent.CountDownLatch closeEntered=new java.util.concurrent.CountDownLatch(1),allowClose=new java.util.concurrent.CountDownLatch(1);public Thread exportWorker;public final java.util.concurrent.atomic.AtomicReference<Throwable> exportFailure=new java.util.concurrent.atomic.AtomicReference<>();
public final TextExportController<android.net.Uri> txt=new TextExportController<>(u->{opens++;return new java.io.ByteArrayOutputStream(){public void close()throws java.io.IOException {closeEntered.countDown();if(blockClose)try{if(!allowClose.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("bounded close");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}if(failClose)throw new java.io.IOException("synthetic close failure");super.close();}};},exports::add);
public final RuntimeLogStore store=new RuntimeLogStore();public final RuntimeLogWorker worker=new RuntimeLogWorker(store,new RuntimeLogPersistence(new java.io.File("never-navigation-log")),null,r->{});public final LogExportController<android.net.Uri> logs=new LogExportController<>(u->{opens++;return new java.io.ByteArrayOutputStream();},exports::add);
public AppGraph(ModelManagementController c,ModelRepository r,ModelReadiness s,TaskCoordinator o){controller=c;repository=r;ready=s;owner=o;store.append(RuntimeLogEventKind.APP_STARTUP,RuntimeLogSource.APP,null,"ok");}
public AppState appState(){return state;}public TextExportController<android.net.Uri> textExport(){return txt;}public LogExportController<android.net.Uri> logExports(){return logs;}public RuntimeLogStore runtimeLogStore(){return store;}public RuntimeLogWorker runtimeLogWorker(){return worker;}
public ModelManagementController modelManagement(){return controller;}public ModelRepository modelRepository(){return repository;}public ModelReadiness readiness(){return ready;}public TaskCoordinator coordinator(){return owner;}public ModelPickerTickets pickerTickets(){return tickets;}public ModelAccess modelAccess(){return new ModelAccess(repository,ready);}public ModelSource modelZipSource(android.net.Uri u){return new SafModelSource(new android.content.ContentResolver(),u);}public Asr asrOperation(){return asr;}
public class Asr {public RecordingControl control;public int wave,sample,record,clears;public boolean startPrepare(){return false;}public void startRelease(){}public RecordingControl recording(){return control;}public void cancelRecording(){if(control!=null)control.cancel();}public boolean startWave(android.net.Uri u,String l){wave++;return true;}public boolean startSample(String l){sample++;return true;}public boolean startRecording(String l){record++;return true;}public void clearResults(){clears++;state.setLastText("");}public void editResult(long r,String t){state.resultState().applyEdit(r,t);}}
}
'''

HARNESS = r'''package org.llmasr.minimal;
import java.io.*;import java.nio.file.*;import java.util.*;import java.lang.reflect.*;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import android.view.View;import android.net.Uri;
import org.llmasr.minimal.model.*;import org.llmasr.minimal.modelmanagement.*;import org.llmasr.minimal.task.*;import org.llmasr.minimal.audio.*;
public class NavigationContractTest {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static Object field(Object a,String n)throws Exception{Field f=a.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(a);}
static void render(Object a)throws Exception{Method m=a.getClass().getDeclaredMethod("render");m.setAccessible(true);m.invoke(a);}
static Button find(View v,String s){if(v.getVisibility()==View.GONE)return null;if(v instanceof Button&&((Button)v).getText().toString().equals(s))return(Button)v;if(v instanceof LinearLayout)for(View c:((LinearLayout)v).children){Button b=find(c,s);if(b!=null)return b;}if(v instanceof ScrollView)return find(((ScrollView)v).child,s);return null;}
static String visibleText(View v){if(v.getVisibility()==View.GONE)return "";String s=v instanceof TextView?((TextView)v).getText().toString():"";if(v instanceof LinearLayout)for(View c:((LinearLayout)v).children)s+="\n"+visibleText(c);if(v instanceof ScrollView)s+="\n"+visibleText(((ScrollView)v).child);return s;}
static void click(Activity a,String s){Button b=find(a.content,s);check(b!=null&&b.isEnabled(),"visible enabled action "+s);b.performClick();}
static String page(MainActivity a)throws Exception{return field(a,"page").toString();}
static Intent target(){return new Intent().setData(Uri.parse("content://synthetic/wave"));}
static void clearDialogs(){AlertDialog.positive=AlertDialog.neutral=AlertDialog.negative=null;}
static class Fixture implements AutoCloseable {
final Path dir=Files.createTempDirectory("nav-contract-");final List<Runnable> jobs=new ArrayList<>();final TaskCoordinator owner=new TaskCoordinator(jobs::add);final AppGraph g;
Fixture()throws Exception{List<ModelEntry> entries=new ArrayList<>();for(int i=0;i<7;i++)entries.add(new ModelEntry("f"+i,0,ModelPackagePolicy.sha(new byte[0])));ModelManifest manifest=new ModelManifest(entries);ModelReadiness ready=new ModelReadiness();ModelRepository repo=ModelRepository.forWorker(manifest,dir.resolve("model").toFile(),()->Long.MAX_VALUE);ModelManagementController c=new ModelManagementController(owner,repo,ready,new ModelOperationControl(),new ModelManagementState());g=new AppGraph(c,repo,ready,owner);AppGraph.current=g;}
MainActivity main(Bundle b){MainActivity a=new MainActivity();a.onCreate(b);a.onResume();return a;}
void drain(){for(int n=0;!jobs.isEmpty()&&n<12;n++)jobs.remove(0).run();check(jobs.isEmpty(),"bounded actual owner drain");}
public void close(){g.allowClose.countDown();if(g.exportWorker!=null){try{g.exportWorker.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}check(!g.exportWorker.isAlive(),"do not delete input before actual TXT worker exit");if(g.exportFailure.get()!=null)throw new AssertionError("TXT worker failure",g.exportFailure.get());}drain();for(int n=0;!g.exports.isEmpty()&&n<4;n++)g.exports.remove(0).run();check(g.exports.isEmpty(),"exports bounded terminal");g.worker.close();Handler.pending.clear();AppGraph.current=null;try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}catch(IOException e){throw new RuntimeException(e);}}
}
static void tree()throws Exception{try(Fixture f=new Fixture()){
MainActivity a=f.main(null);check(page(a).equals("HOME"),"cold HOME");click(a,"转写");check(page(a).equals("TRANSCRIBE"),"home transcription child");check(find(a.content,"模型")==null&&find(a.content,"日志")==null,"no transcribe sibling shortcuts");click(a,"返回首页");check(page(a).equals("HOME")&&!a.finished,"transcription button back HOME");click(a,"输入法");a.onBackPressed();check(page(a).equals("HOME"),"IME settings parent HOME");
click(a,"模型");check(a.starts.size()==1&&a.starts.get(0).target==ModelManagementActivity.class,"HOME models intent");
ModelManagementActivity m=new ModelManagementActivity();m.onCreate(null);m.onResume();f.drain();render(m);check(find(m.content,"日志")==null,"models no logs shortcut");
Field detail=ModelManagementActivity.class.getDeclaredField("detailModel");detail.setAccessible(true);detail.set(m,"legacy");clearDialogs();m.onBackPressed();check(detail.get(m)==null&&!m.finished,"detail back must remain models list");
m.onBackPressed();check(m.finished&&m.starts.isEmpty(),"models back must reveal HOME without sibling launch");m.onPause();m.onStop();m.onDestroy();
LogsActivity l=new LogsActivity();l.onCreate(null);l.onResume();click(l,"返回");check(l.finished&&l.starts.isEmpty(),"logs button uses parent Back");l.onPause();l.onDestroy();
LogsActivity root=new LogsActivity();root.root=true;root.onCreate(null);root.onBackPressed();check(root.finished&&root.starts.size()==1&&root.starts.get(0).target==MainActivity.class,"orphan logs establishes HOME");root.onDestroy();
a.onPause();a.onDestroy();}}
static void entry()throws Exception{try(Fixture f=new Fixture()){
MainActivity a=f.main(null);click(a,"转写");a.onPause();a.onNewIntent(new Intent().putExtra("page","TRANSCRIBE"));a.onResume();check(page(a).equals("HOME"),"external app entry must normalize HOME through capture guard");click(a,"转写");a.onPause();a.onResume();check(page(a).equals("TRANSCRIBE"),"ordinary resume preserves page");
Bundle saved=new Bundle();a.onSaveInstanceState(saved);check(saved.values.keySet().equals(new HashSet<>(Arrays.asList("page","language","waveHighWater"))),"saved state no URI body owner proof");a.onPause();a.onDestroy();MainActivity b=f.main(saved);check(page(b).equals("TRANSCRIBE"),"valid logical reconstruction");b.onPause();b.onDestroy();saved.putString("page","arbitrary");MainActivity c=f.main(saved);check(page(c).equals("HOME"),"illegal page fails HOME");c.onPause();c.onDestroy();}}
static void capture()throws Exception{try(Fixture f=new Fixture()){
MainActivity a=f.main(null);click(a,"转写");RecordingControl r=new RecordingControl();r.start(()->{});f.g.asr.control=r;check(f.owner.submit(()->{r.captureReleased();f.g.asr.control=null;}),"capture owns real coordinator");
a.onBackPressed();check(page(a).equals("TRANSCRIBE")&&!r.cancelled()&&AlertDialog.neutral!=null,"capture back three choices");AlertDialog.neutral.click(null,0);check(r.stopped()&&!r.cancelled()&&page(a).equals("TRANSCRIBE"),"stop stays TRANSCRIBE");a.onBackPressed();check(page(a).equals("TRANSCRIBE")&&AlertDialog.positive!=null,"voluntary back retains capture guard");AlertDialog.positive.click(null,0);check(page(a).equals("HOME")&&r.cancelled()&&f.owner.isBusy(),"cancel leaves owner until actual exit");f.drain();check(!f.owner.isBusy(),"capture real release");
click(a,"转写");RecordingControl nativeControl=new RecordingControl();nativeControl.start(()->{});nativeControl.captureReleased();check(nativeControl.tryCommitInference(),"synthetic committed native");f.g.asr.control=nativeControl;f.owner.submit(()->{f.g.asr.control=null;});clearDialogs();a.onBackPressed();check(page(a).equals("HOME")&&f.owner.isBusy()&&!nativeControl.cancelled(),"native back continues without fake cancellation");a.onBackPressed();check(!a.finished&&AlertDialog.positive!=null,"busy root confirms platform exit");f.drain();
click(a,"转写");f.g.state.setLastText("synthetic export during capture");Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(a);RecordingControl ext=new RecordingControl();ext.start(()->{});f.g.asr.control=ext;f.owner.submit(()->{ext.captureReleased();f.g.asr.control=null;});int launches=a.resultLaunches;click(a,"导出 TXT");check(a.resultLaunches==launches&&AlertDialog.positive!=null&&!ext.cancelled(),"external TXT also requires capture decision");AlertDialog.positive.click(null,0);check(ext.cancelled()&&f.owner.isBusy()&&a.resultLaunches==launches+1,"explicit external discard preserves actual capture owner");a.onActivityResult(a.request,0,null);f.drain();a.onPause();a.onDestroy();}}
static void waveAndText()throws Exception{try(Fixture f=new Fixture()){
MainActivity a=f.main(null);click(a,"转写");click(a,"选文件");int old=a.request;check(old>=20&&old<=99,"finite wave request range");a.onPause();a.onActivityResult(old,-1,target());check(f.g.asr.wave==0,"background picker defers");a.onResume();check(f.g.asr.wave==1,"foreground same page starts once");a.onActivityResult(old,-1,target());check(f.g.asr.wave==1,"stale picker must start zero transcription");a.onActivityResult(11,-1,target());a.onActivityResult(5000,-1,target());check(f.g.asr.wave==1,"old and wrong-mode request zero transcription");
click(a,"选文件");int stale=a.request;a.onBackPressed();a.onActivityResult(stale,-1,target());check(f.g.asr.wave==1,"logical leave revokes WAV");click(a,"转写");click(a,"选文件");int recreatedCode=a.request;Bundle saved=new Bundle();a.onSaveInstanceState(saved);a.onPause();a.onDestroy();a=f.main(saved);a.onActivityResult(recreatedCode,-1,target());check(f.g.asr.wave==1,"recreated WAV no ticket");click(a,"选文件");check(a.request>recreatedCode,"high water prevents reuse");a.onActivityResult(a.request,0,null);
f.g.state.setLastText("synthetic public result");f.owner.submit(()->{});Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(a);check(find(a.content,"导出 TXT").isEnabled(),"TXT independent of ASR busy");click(a,"导出 TXT");int txt=a.request;a.onBackPressed();a.onActivityResult(txt,-1,target());check(f.g.exports.isEmpty()&&f.g.opens==0,"logical leave revokes unsubmitted TXT");f.drain();
click(a,"转写");click(a,"导出 TXT");int writing=a.request;a.onActivityResult(writing,-1,target());check(f.g.exports.size()==1,"TXT actual write queued");f.g.exports.remove(0).run();check(f.g.opens==1,"actual provider write admitted once");a.onBackPressed();check(f.g.opens==1,"completed external side effect not recalled on leave");
click(a,"转写");a.permission=1;click(a,"录音");check(a.permissions==1&&f.g.asr.record==0,"permission requests no automatic capture");a.onRequestPermissionsResult(12,new String[]{"mic"},new int[]{0});check(f.g.asr.record==0,"permission result no capture");a.onPause();a.onDestroy();
Bundle invalid=new Bundle();invalid.putInt("waveHighWater",1000);MainActivity b=f.main(invalid);click(b,"转写");click(b,"选文件");check(b.request==0,"invalid high water fail closed");b.onPause();b.onDestroy();
MainActivity cap=f.main(null);click(cap,"转写");for(int n=0;n<80;n++){int count=cap.resultLaunches;click(cap,"选文件");if(cap.resultLaunches==count)break;cap.onActivityResult(cap.request,0,null);}int count=cap.resultLaunches;click(cap,"选文件");check(cap.request==99&&cap.resultLaunches==count,"WAV tickets exhaust without reuse or wrap");cap.onPause();cap.onDestroy();}}
static void detailOwner()throws Exception{try(Fixture f=new Fixture()){
ModelManagementActivity m=new ModelManagementActivity();m.onCreate(null);m.onResume();f.drain();
ModelOperationControl.Operation op=f.g.controller.opControl().tryBegin(ModelOperationControl.Kind.IMPORT);check(op!=null,"real import operation identity");
Field owned=ModelPageSession.class.getDeclaredField("ownedId");owned.setAccessible(true);owned.set(field(m,"page"),op.id);
Field detail=ModelManagementActivity.class.getDeclaredField("detailModel");detail.setAccessible(true);detail.set(m,"legacy");m.onBackPressed();check(!op.isCancelRequested(),"detail back must not cancel owned import");f.g.controller.opControl().complete(op,false);m.onPause();m.onStop();m.onDestroy();}}
static void lifecycleEntry()throws Exception{for(String outcome:Arrays.asList("cancelled","cleared","committed"))try(Fixture f=new Fixture()){
MainActivity a=f.main(null);click(a,"转写");RecordingControl r=new RecordingControl();r.start(()->{});if(outcome.equals("committed")){r.captureReleased();check(r.tryCommitInference(),"commit before pause");}f.g.asr.control=r;f.owner.submit(()->{r.captureReleased();f.g.asr.control=null;});clearDialogs();int shown=AlertDialog.shows;a.onPause();if(outcome.equals("cleared"))f.drain();a.onNewIntent(new Intent());check(AlertDialog.shows==shown&&page(a).equals("TRANSCRIBE"),"new Intent must defer HOME without background capture dialog");a.onResume();check(page(a).equals("HOME")&&AlertDialog.shows==shown&&f.g.asr.record==0,"pause newIntent resume normalizes HOME without fake capture choices");check(outcome.equals("committed")?!r.cancelled():r.cancelled(),"pause preserves cancellation versus committed inference");if(outcome.equals("cancelled"))check(f.owner.isBusy()&&f.g.state.lastStatus().contains("已取消"),"cancelled capture retains real owner without continue option");if(outcome.equals("committed"))check(f.owner.isBusy()&&f.g.state.lastStatus().contains("继续"),"committed reentry reports continued transcription");f.drain();a.onPause();a.onDestroy();}}
static void txtLifecycle()throws Exception{
try(Fixture f=new Fixture()){MainActivity a=f.main(null);click(a,"转写");f.g.state.setLastText("synthetic queued");Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(a);click(a,"导出 TXT");a.onActivityResult(a.request,-1,target());check(f.g.txt.state().phase==org.llmasr.minimal.transcription.TextExportController.Phase.QUEUED,"real TXT queued");a.onBackPressed();check(page(a).equals("HOME")&&f.g.txt.state().busy()&&visibleText(a.content).contains("TXT"),"queued export keeps visible lane until actual worker");f.g.exports.remove(0).run();check(f.g.opens==0&&!f.g.txt.state().busy(),"queued logical leave revokes before provider open");a.onPause();a.onDestroy();}
for(boolean fail:new boolean[]{false,true})try(Fixture f=new Fixture()){MainActivity a=f.main(null);click(a,"转写");f.g.state.setLastText("synthetic blocked write");Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(a);f.g.blockClose=true;f.g.failClose=fail;click(a,"导出 TXT");a.onActivityResult(a.request,-1,target());Runnable job=f.g.exports.remove(0);f.g.exportWorker=new Thread(()->{try{job.run();}catch(Throwable t){f.g.exportFailure.set(t);}},"actual-TXT-close");f.g.exportWorker.start();try{check(f.g.closeEntered.await(5,java.util.concurrent.TimeUnit.SECONDS),"actual provider close entered");a.onBackPressed();refresh.invoke(a);String global=visibleText(a.content);check(page(a).equals("HOME")&&global.contains("TXT")&&global.contains("不能撤回")&&f.g.txt.state().phase==org.llmasr.minimal.transcription.TextExportController.Phase.WRITING&&!f.owner.isBusy(),"HOME retains visible real TXT writing and residual risk");check(f.g.txt.begin(1,f.g.state.resultState())==null,"blocked provider retains independent TXT owner");clearDialogs();a.onBackPressed();check(!a.finished&&AlertDialog.positive!=null,"root Back must include independent TXT busy");}finally{f.g.allowClose.countDown();f.g.exportWorker.join(5000);}check(!f.g.exportWorker.isAlive()&&!f.g.txt.state().busy()&&f.g.exportFailure.get()==null,"actual TXT close before lane release");refresh.invoke(a);String global=visibleText(a.content);check(global.contains("TXT")&&global.contains("不完整"),"HOME keeps TXT terminal risk visible");if(fail)check(f.g.txt.state().phase==org.llmasr.minimal.transcription.TextExportController.Phase.FAILED,"real close failure remains failed");a.onPause();a.onDestroy();}}
static void modelBackMatrix()throws Exception{for(ModelOperationControl.Kind kind:Arrays.asList(ModelOperationControl.Kind.IMPORT,ModelOperationControl.Kind.VERIFY,ModelOperationControl.Kind.DELETE))try(Fixture f=new Fixture()){
ModelManagementActivity m=new ModelManagementActivity();m.onCreate(null);m.onResume();f.drain();ModelOperationControl.Operation op=f.g.controller.opControl().tryBegin(kind);Field id=ModelPageSession.class.getDeclaredField("ownedId");id.setAccessible(true);id.set(field(m,"page"),op.id);clearDialogs();m.onBackPressed();check(!m.finished&&!op.isCancelRequested()&&AlertDialog.positive!=null,"owned model list Back requires confirmation");if(AlertDialog.negative!=null)AlertDialog.negative.click(null,0);check(!m.finished&&!op.isCancelRequested(),"stay model page does not cancel");AlertDialog.positive.click(null,0);check(m.finished&&(kind==ModelOperationControl.Kind.DELETE?!op.isCancelRequested():op.isCancelRequested()),"model cancel confirmation only cancels own IMPORT VERIFY not DELETE");f.g.controller.opControl().complete(op,false);m.onPause();m.onStop();m.onDestroy();}
try(Fixture f=new Fixture()){ModelManagementActivity observer=new ModelManagementActivity();observer.onCreate(null);observer.onResume();f.drain();ModelOperationControl.Operation op=f.g.controller.opControl().tryBegin(ModelOperationControl.Kind.IMPORT);observer.onBackPressed();check(observer.finished&&!op.isCancelRequested(),"observer Back cannot cancel another model owner");f.g.controller.opControl().complete(op,false);observer.onPause();observer.onStop();observer.onDestroy();ModelManagementActivity root=new ModelManagementActivity();root.root=true;root.onCreate(null);root.onResume();f.drain();root.onBackPressed();check(root.finished&&root.starts.size()==1&&root.starts.get(0).target==MainActivity.class,"model task root establishes HOME");root.onPause();root.onStop();root.onDestroy();}
try(Fixture f=new Fixture()){MainActivity a=f.main(null);click(a,"转写");RecordingControl r=new RecordingControl();r.start(()->{});f.g.asr.control=r;f.owner.submit(()->{f.g.asr.control=null;});a.onBackPressed();check(AlertDialog.positive!=null,"capture confirmation before commit");r.captureReleased();check(r.tryCommitInference(),"commit wins after capture dialog");AlertDialog.positive.click(null,0);check(page(a).equals("HOME")&&!r.cancelled()&&f.owner.isBusy()&&f.g.state.lastStatus().contains("继续"),"dialog discard losing commit reports continued inference");f.drain();a.onPause();a.onDestroy();}}
static void staleCaptureDialogs()throws Exception{for(String state:Arrays.asList("cancelled","cleared","committed"))for(boolean external:new boolean[]{false,true})try(Fixture f=new Fixture()){
MainActivity a=f.main(null);if(!external)click(a,"转写");RecordingControl old=new RecordingControl();old.start(()->{});f.g.asr.control=old;f.owner.submit(()->{old.captureReleased();f.g.asr.control=null;});if(external)click(a,"模型");else a.onBackPressed();check(AlertDialog.neutral!=null&&AlertDialog.positive!=null,"actual capture guard listeners retained");AlertDialog.Click neutral=AlertDialog.neutral,positive=AlertDialog.positive,negative=AlertDialog.negative;
if(state.equals("committed")){old.captureReleased();check(old.tryCommitInference(),"commit wins before pause with old dialog");}a.onPause();if(state.equals("cleared"))f.drain();a.onNewIntent(new Intent());a.onResume();check(page(a).equals("HOME"),"reentry HOME before old capture callback");int shows=AlertDialog.shows,starts=a.starts.size(),picker=a.resultLaunches,records=f.g.asr.record;neutral.click(null,0);check(page(a).equals("HOME")&&AlertDialog.shows==shows&&a.starts.size()==starts&&a.resultLaunches==picker&&f.g.asr.record==records,"expired capture dialog must not navigate or launch after HOME reentry");positive.click(null,0);check(page(a).equals("HOME")&&AlertDialog.shows==shows&&a.starts.size()==starts&&a.resultLaunches==picker&&f.g.asr.record==records,"expired positive must not execute prior external action after HOME reentry");if(negative!=null)negative.click(null,0);
RecordingControl replacement=new RecordingControl();replacement.start(()->{});f.g.asr.control=replacement;neutral.click(null,0);check(!replacement.cancelled()&&!replacement.stopped()&&page(a).equals("HOME")&&AlertDialog.shows==shows,"expired capture dialog must not touch replacement owner or revive choices");positive.click(null,0);if(negative!=null)negative.click(null,0);check(!replacement.cancelled()&&!replacement.stopped()&&page(a).equals("HOME")&&AlertDialog.shows==shows&&a.starts.size()==starts,"expired positive must not touch replacement owner or launch prior action");f.drain();f.g.asr.control=null;a.onPause();a.onDestroy();}
try(Fixture f=new Fixture()){MainActivity a=f.main(null);click(a,"转写");RecordingControl r=new RecordingControl();r.start(()->{});f.g.asr.control=r;a.onBackPressed();AlertDialog.Click neutral=AlertDialog.neutral,positive=AlertDialog.positive;neutral.click(null,0);check(page(a).equals("TRANSCRIBE")&&r.stopped()&&!r.cancelled(),"valid capture dialog stop remains available");positive.click(null,0);check(!r.cancelled()&&page(a).equals("TRANSCRIBE"),"capture dialog choice is consumed once");f.g.asr.control=null;a.onPause();a.onDestroy();}}
static void platformDismissGap()throws Exception{for(boolean external:new boolean[]{false,true})try(Fixture f=new Fixture()){
MainActivity a=f.main(null);if(!external)click(a,"转写");RecordingControl r=new RecordingControl();r.start(()->{});f.g.asr.control=r;if(external)click(a,"模型");else a.onBackPressed();AlertDialog closed=(AlertDialog)field(a,"captureDialog");AlertDialog.Click neutral=AlertDialog.neutral,positive=AlertDialog.positive;check(closed!=null&&closed.isShowing()&&neutral!=null&&positive!=null&&AlertDialog.negative==null,"capture dialog real executable listeners and passive negative");AlertDialog.Dismiss oldListener=closed.pendingDismissListener();check(oldListener!=null&&closed.deliveredCount==0,"actual old production dismiss listener registered");String originalPage=page(a);int launches=a.starts.size(),shown=AlertDialog.shows;closed.closeWindowWithoutNotification();check(!closed.isShowing(),"platform window closed before notification");neutral.click(null,0);check(!r.stopped()&&!r.cancelled()&&page(a).equals(originalPage)&&a.starts.size()==launches&&AlertDialog.shows==shown,"closed capture window must reject callbacks before dismiss notification");positive.click(null,0);check(!r.stopped()&&!r.cancelled()&&page(a).equals(originalPage)&&a.starts.size()==launches&&AlertDialog.shows==shown,"closed positive must not cancel or launch before dismiss notification");
if(external)click(a,"模型");else a.onBackPressed();AlertDialog fresh=(AlertDialog)field(a,"captureDialog");AlertDialog.Click freshNeutral=AlertDialog.neutral,freshPositive=AlertDialog.positive;check(fresh!=closed&&fresh.isShowing(),"new current capture dialog shown");check(closed.pendingDismissListener()==oldListener&&closed.deliveredCount==0,"old dismiss notification must remain pending after fresh dialog show");closed.deliverDismissNotification();check(closed.deliveredCount==1&&closed.lastDeliveredListener==oldListener&&closed.lastDeliveredDialog==closed&&closed.deliveredOrder>fresh.shownOrder&&closed.pendingDismissListener()==null,"late old listener must really execute once after fresh show");check(field(a,"captureDialog")==fresh&&fresh.isShowing(),"delayed old dismiss must not revoke new dialog");closed.deliverDismissNotification();check(closed.deliveredCount==1&&field(a,"captureDialog")==fresh&&fresh.isShowing(),"late notification replay cannot execute twice or revoke new dialog");if(external){freshPositive.click(null,0);check(r.cancelled()&&a.starts.size()==launches+1,"fresh positive executes exactly once before framework dismiss");freshPositive.click(null,0);check(a.starts.size()==launches+1,"fresh positive ticket consumed once");}else{freshNeutral.click(null,0);check(r.stopped()&&!r.cancelled()&&page(a).equals("TRANSCRIBE"),"fresh neutral executes before automatic dismiss");freshPositive.click(null,0);check(!r.cancelled(),"fresh neutral consumes alternate callback");}f.g.asr.control=null;a.onPause();a.onDestroy();}}
public static void main(String[] args)throws Exception{String mode=args.length==0?"all":args[0];switch(mode){case "dismiss-gap":platformDismissGap();break;case "dialog":staleCaptureDialogs();break;case "lifecycle":lifecycleEntry();break;case "txt":txtLifecycle();break;case "model-back":modelBackMatrix();break;case "tree":tree();break;case "entry":entry();break;case "wave":waveAndText();break;case "detail":detailOwner();break;default:tree();entry();capture();waveAndText();detailOwner();lifecycleEntry();txtLifecycle();modelBackMatrix();staleCaptureDialogs();platformDismissGap();}System.out.println("PASS real Activity navigation "+mode);}
}
'''


def prepare(directory):
    generated = stubs()
    names = (ROOT / 'scripts/test-minimal-apk.sh').read_text().split('host_classes=(', 1)[1].split(')', 1)[0].split()
    names += ['model.SafModelSource', 'model.SafModelZipSource', 'MainActivity', 'ModelManagementActivity', 'LogsActivity']
    for name in dict.fromkeys(names):
        path = mixed.source(name.replace('.', '/'))
        generated['org/llmasr/minimal/' + str(path.relative_to(BASE))] = path.read_text()
    sources = []
    for name, body in generated.items():
        p = directory / 'src' / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(body + '\n')
        sources.append(p)
    return sources


MUTATIONS = [
    ('NAV-1', 'ModelManagementActivity.java', 'else returnHome();', 'else startActivity(new Intent(this, LogsActivity.class));', 'tree', 'models back must reveal HOME without sibling launch'),
    ('NAV-2', 'ModelManagementActivity.java', 'if (detailModel != null) { detailModel = null; invalidation.onChange(); return; }', 'if (detailModel != null) { finish(); return; }', 'tree', 'detail back must remain models list'),
    ('NAV-3', 'MainActivity.java', 'super.onNewIntent(intent); revokeCaptureDialog(); pendingHome=true;', 'super.onNewIntent(intent); revokeCaptureDialog(); pendingHome=false;', 'entry', 'external app entry must normalize HOME through capture guard'),
    ('NAV-4', 'MainActivity.java', 'if(request!=wavePending || wavePending==0 || page!=Page.TRANSCRIBE)return;', 'if(request<20 || request>99)return;', 'wave', 'stale picker must start zero transcription'),
    ('NAV-5', 'ModelManagementActivity.java', 'if (detailModel != null) { detailModel = null;', 'if (detailModel != null) { page.cancelOwn(); detailModel = null;', 'detail', 'detail back must not cancel owned import'),
    ('APP-R1', 'MainActivity.java', 'super.onNewIntent(intent); revokeCaptureDialog(); pendingHome=true;', 'super.onNewIntent(intent); guardCapture(() -> changePage(Page.HOME));', 'lifecycle', 'new Intent must defer HOME without background capture dialog'),
    ('APP-R2-status', 'MainActivity.java', 'globalExportStatus.setText(exportState.phase==TextExportController.Phase.IDLE ? "" : "TXT："+TextExportController.statusText(exportState)', 'globalExportStatus.setText(true ? "" : "TXT："+TextExportController.statusText(exportState)', 'txt', 'queued export keeps visible lane until actual worker'),
    ('APP-R2-back', 'MainActivity.java', 'graph.coordinator().isBusy() || graph.textExport().state().busy()', 'graph.coordinator().isBusy()', 'txt', 'root Back must include independent TXT busy'),
    ('APP-R3-queued', 'MainActivity.java', 'revokeWave(); exportPage.destroy(); exportPage=new TextExportPage', 'revokeWave(); exportPage=new TextExportPage', 'txt', 'queued logical leave revokes before provider open'),
    ('APP-R3-model', 'ModelManagementActivity.java', 'page.cancelOwn(); returnHome();', 'returnHome();', 'model-back', 'model cancel confirmation only cancels own IMPORT VERIFY not DELETE'),
    ('APP-R1-showing', 'MainActivity.java', ' || !captureDialog.isShowing()', '', 'dismiss-gap', 'closed capture window must reject callbacks before dismiss notification'),
    ('APP-R1-dialog-token', 'MainActivity.java', 'if(generation!=captureDialogGeneration || captureDialog==null || !captureDialog.isShowing() || !foreground || isDestroyed() || isFinishing()\n            || graph.asrOperation().recording()!=expected || expected.cancelled())return false;', 'if(false)return false;', 'dialog', 'expired capture dialog must not navigate or launch after HOME reentry'),
]


def run_suite(out):
    require(not out.exists() and not out.is_symlink(), 'fresh evidence directory required')
    # Reject symlink ancestors rather than resolving into an unrelated tree.
    require(not any(p.is_symlink() for p in [out, *out.parents]), 'symlink evidence path forbidden')
    out.mkdir(parents=True)
    started = time.monotonic()
    sources = prepare(out)
    owned.write_json(out / 'inputs.json', [{'path': str(p), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources])
    tools = owned.tool_identity(out)
    owned.write_json(out / 'tools.json', tools)
    owned.write_json(out / 'driver-inputs.json', [mixed.identity(p) for p in (Path(__file__), ROOT / 'tests/model_zip_picker_test.py', ROOT / 'tests/kotlin_next_migration_test.py', ROOT / 'tests/logs_activity_picker_test.py', ROOT / 'tests/mixed_compiler_lifecycle.py', ROOT / 'scripts/compile-jvm-sources.sh')])
    attempts = []

    def execute(argv, label, budget):
        require(time.monotonic() - started < 4200, 'navigation total budget exceeded')
        attempts.append(label)
        owned.write_json(out / 'attempted.json', attempts)
        return lifecycle.run(argv, out, label, budget)

    def route(label, mode='all', diagnostic=None):
        classes = out / ('classes-' + label)
        compiled = execute(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '', *map(str, sources)], label + '-compile', 270)
        require(compiled.returncode == 0, 'compile/tool failure NOT behavioral red ' + label + '\n' + compiled.stderr.decode(errors='replace'))
        cp = str(classes) + ':' + str(Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar')
        result = execute(['java', '-Xmx256m', '-cp', cp, 'org.llmasr.minimal.NavigationContractTest', mode], label + '-jvm', 45)
        expected = ('java.lang.AssertionError: ' + diagnostic).encode() if diagnostic else b'PASS real Activity navigation'
        require(result.returncode == (1 if diagnostic else 0) and expected in (result.stderr if diagnostic else result.stdout),
                'not exact ' + ('red' if diagnostic else 'green') + ' ' + label + '\n' + result.stderr.decode(errors='replace'))

    route('green')
    for label, filename, before, after, mode, diagnostic in MUTATIONS:
        path = out / 'src/org/llmasr/minimal' / filename
        original = path.read_bytes()
        require(original.decode().count(before) == 1, 'unique compiling mutant anchor ' + label)
        primary = None
        mutant = original.decode().replace(before, after).encode()
        owned.write_json(out / (label + '-mutation.json'), {'owner': str(path), 'before_sha256': hashlib.sha256(original).hexdigest(), 'mutant_sha256': hashlib.sha256(mutant).hexdigest(), 'before': before, 'after': after, 'mode': mode, 'diagnostic': diagnostic})
        try:
            path.write_bytes(mutant)
            route(label + '-red', mode, diagnostic)
        except BaseException as failure:
            primary = failure
            raise
        finally:
            try:
                lifecycle.guard(out)
                states = [json.loads((out / (a + '.json')).read_text()) for a in attempts]
                require(all(s.get('owned_exited') is True and s.get('root_reaped') is True and type(s.get('exit')) is int and s.get('primary') is None for s in states), 'unknown exit retains input')
                path.write_bytes(original)
            except BaseException as failure:
                if primary is None:
                    raise
                primary.add_note('restoration refused: ' + repr(failure))
        require(path.read_bytes() == original, 'exact original source restoration')
        route(label + '-restored')
    owned.write_json(out / 'result.json', {'elapsed_seconds': time.monotonic() - started, 'attempts': attempts, 'ordinary_or_optimized': __debug__, 'device': 'NOT RUN'})
    print('PASS real Activity navigation driver compiling mutants and restorations')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    args = parser.parse_args()
    run_suite(args.evidence_dir.absolute())


if __name__ == '__main__':
    main()
