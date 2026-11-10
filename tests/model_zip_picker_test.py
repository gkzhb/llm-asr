#!/usr/bin/env python3
"""Complete production ModelManagementActivity + tickets/page on controlled host fakes, NOT Android."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from mixed_compiler_lifecycle import run as owned_run, guard
import kotlin_next_migration_test as fixtures

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT/'android/app/src/org/llmasr/minimal'


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def stubs():
    s = fixtures.stubs()
    # Only compile this complete Activity, not the original NextApp caller/Graph.
    s.pop('WindowContractTest.java', None)
    s.pop('org/llmasr/minimal/transcription/AppReportWriter.java', None)
    s['android/R.java'] = 'package android;public class R {public static class attr {public static int state_enabled=1;}}'
    s['android/content/res/ColorStateList.java'] = 'package android.content.res;public class ColorStateList {public ColorStateList(int[][] s,int[] c){}}'
    s['android/net/Uri.java'] = '''package android.net;public class Uri {private String value;private Uri(String s){value=s;}public static Uri parse(String s){return new Uri(s);}public String getScheme(){int p=value.indexOf(':');return p<0?null:value.substring(0,p);}public String toString(){return value;}}'''
    s['android/os/Bundle.java'] = '''package android.os;public class Bundle {private java.util.Map<String,String> values=new java.util.HashMap<>();public String getString(String k){return values.get(k);}public void putString(String k,String v){values.put(k,v);}}'''
    s['android/os/PowerManager.java'] = 'package android.os;public class PowerManager {public boolean isInteractive(){return true;}}'
    s['android/app/KeyguardManager.java'] = 'package android.app;public class KeyguardManager {public boolean isKeyguardLocked(){return false;}}'
    s['android/os/SystemClock.java'] = 'package android.os;public class SystemClock {public static long uptimeMillis(){return System.nanoTime()/1000000;}}'
    s['android/content/BroadcastReceiver.java'] = 'package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}'
    s['android/content/IntentFilter.java'] = 'package android.content;public class IntentFilter {public IntentFilter(String s){}}'
    s['android/content/ClipData.java'] = 'package android.content;public class ClipData {int n;public ClipData(int n){this.n=n;}public int getItemCount(){return n;}}'
    s['android/content/Intent.java'] = '''package android.content;public class Intent {
public static final String ACTION_OPEN_DOCUMENT="open-document",ACTION_OPEN_DOCUMENT_TREE="tree",ACTION_SCREEN_OFF="off",CATEGORY_OPENABLE="openable",EXTRA_ALLOW_MULTIPLE="multiple",EXTRA_MIME_TYPES="mimes";
public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_ACTIVITY_CLEAR_TOP=2;public String action,category,type;public int flags;public boolean multiple=true;public String[] mimes;private android.net.Uri uri;private ClipData clip;
public Intent(){}public Intent(String a){action=a;}public Intent(Context c,Class<?> cl){}
public Intent addFlags(int f){flags|=f;return this;}public Intent addCategory(String c){category=c;return this;}public Intent setType(String t){type=t;return this;}
public Intent putExtra(String k,boolean b){multiple=b;return this;}public Intent putExtra(String k,String[] v){mimes=v;return this;}
public android.net.Uri getData(){return uri;}public Intent setData(android.net.Uri u){uri=u;return this;}public ClipData getClipData(){return clip;}public Intent setClipData(ClipData c){clip=c;return this;}}
'''
    s['android/content/Context.java'] = '''package android.content;public class Context {
public static final String KEYGUARD_SERVICE="key",POWER_SERVICE="power";public static final int RECEIVER_NOT_EXPORTED=4;
public Object getSystemService(String n){return n.equals(KEYGUARD_SERVICE)?new android.app.KeyguardManager():new android.os.PowerManager();}
public Context getApplicationContext(){return this;}public ContentResolver getContentResolver(){return new ContentResolver();}public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public void registerReceiver(BroadcastReceiver r,IntentFilter i){}public void registerReceiver(BroadcastReceiver r,IntentFilter i,int f){}public void unregisterReceiver(BroadcastReceiver r){}public void startActivity(Intent i){} }
'''
    s['android/app/Activity.java'] = '''package android.app;public class Activity extends android.content.Context {
public static final int RESULT_OK=-1,RESULT_CANCELED=0;public android.view.View content;public android.content.Intent launched;public int request;public RuntimeException launchFailure;public boolean changing;
public void onCreate(android.os.Bundle b){}protected void onResume(){}protected void onPause(){}protected void onStop(){}protected void onDestroy(){}protected void onSaveInstanceState(android.os.Bundle b){}protected void onActivityResult(int a,int b,android.content.Intent i){}
public boolean isTaskRoot(){return false;}public boolean isDestroyed(){return false;}public boolean isFinishing(){return false;}public boolean isChangingConfigurations(){return changing;}public void onBackPressed(){}public void finish(){}
public void setContentView(android.view.View v){content=v;}public android.view.Window getWindow(){return new android.view.Window();}public void startActivityForResult(android.content.Intent i,int c){if(launchFailure!=null)throw launchFailure;launched=i;request=c;}}
'''
    s['android/app/AlertDialog.java'] = '''package android.app;public class AlertDialog {public static Click positive;public interface Click {void click(Object d,int w);}public static class Builder {public Builder(Activity a){}public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}public Builder setNegativeButton(String s,Click c){return this;}public Builder setPositiveButton(String s,Click c){positive=c;return this;}public void show(){}}}'''
    s['android/view/Window.java'] = 'package android.view;public class Window {public void clearFlags(int n){}public void addFlags(int n){}}'
    s['android/view/WindowManager.java'] = 'package android.view;public class WindowManager {public static class LayoutParams {public static final int FLAG_KEEP_SCREEN_ON=128;}}'
    s['android/view/View.java'] = '''package android.view;public class View {public static final int VISIBLE=0,GONE=8;public interface OnClickListener {void onClick(View v);}private int visibility;public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;}public void setContentDescription(CharSequence s){}public void setPadding(int a,int b,int c,int d){}public void setBackgroundColor(int c){} }'''
    s['android/widget/TextView.java'] = '''package android.widget;public class TextView extends android.view.View {String text="";public TextView(android.content.Context c){}public void setText(CharSequence s){text=s.toString();}public CharSequence getText(){return text;}public void setTextSize(int n){}public void setTextColor(int n){}public void setTextColor(android.content.res.ColorStateList c){}public void setTextIsSelectable(boolean b){} }'''
    s['android/widget/Button.java'] = '''package android.widget;public class Button extends TextView {android.view.View.OnClickListener listener;boolean enabled=true;public Button(android.content.Context c){super(c);}public void setAllCaps(boolean b){}public void setMinHeight(int n){}public void setMinimumHeight(int n){}public void setSingleLine(boolean b){}public void setEnabled(boolean b){enabled=b;}public boolean isEnabled(){return enabled;}public void setOnClickListener(android.view.View.OnClickListener l){listener=l;}public boolean performClick(){if(!enabled)return false;listener.onClick(this);return true;}}'''
    s['android/widget/LinearLayout.java'] = '''package android.widget;public class LinearLayout extends android.view.View {public static final int VERTICAL=1;public java.util.List<android.view.View> children=new java.util.ArrayList<>();public LinearLayout(android.content.Context c){}public void setOrientation(int n){}public void removeAllViews(){children.clear();}public void addView(android.view.View v){children.add(v);}public void addView(android.view.View v,LayoutParams p){children.add(v);}public static class LayoutParams {public LayoutParams(int a,int b){}}}'''
    s['android/widget/Toast.java'] = 'package android.widget;public class Toast {public static final int LENGTH_LONG=1;public static Toast makeText(android.content.Context c,CharSequence s,int n){return new Toast();}public void show(){}}'
    s['android/widget/ScrollView.java'] = '''package android.widget;public class ScrollView extends android.view.View {public android.view.View child;public ScrollView(android.content.Context c){}public void setFillViewport(boolean b){}public void addView(android.view.View v){child=v;}}'''
    s['org/llmasr/minimal/platform/ActivityWindow.java'] = '''package org.llmasr.minimal.platform;public class ActivityWindow {public static void configure(android.app.Activity a,android.view.View v){}public static class BackRegistration {public BackRegistration(android.app.Activity a,Runnable r){}public void resumed(){}public void paused(){}}}'''
    s['android/content/ContentResolver.java'] = s['android/content/ContentResolver.java'].replace('public static int calls;', 'public android.database.Cursor query(android.net.Uri u,String[] p,String a,String[] b,String c){return null;}public static int calls;')
    s['android/provider/DocumentsContract.java'] = '''package android.provider;public class DocumentsContract {public static String getTreeDocumentId(android.net.Uri u){return "tree";}public static android.net.Uri buildChildDocumentsUriUsingTree(android.net.Uri u,String s){return u;}public static android.net.Uri buildDocumentUriUsingTree(android.net.Uri u,String s){return u;}public static class Document {public static final String COLUMN_DOCUMENT_ID="id",COLUMN_DISPLAY_NAME="name",COLUMN_MIME_TYPE="mime",COLUMN_FLAGS="flags",MIME_TYPE_DIR="directory";public static final int FLAG_VIRTUAL_DOCUMENT=512;}}'''
    s['android/database/Cursor.java'] = '''package android.database;public interface Cursor {boolean moveToNext();String getString(int i);boolean isNull(int i);long getLong(int i);void close();}'''
    s['org/llmasr/minimal/LogsActivity.java'] = 'package org.llmasr.minimal;public class LogsActivity {}'
    s['org/llmasr/minimal/AppGraph.java'] = '''package org.llmasr.minimal;
import org.llmasr.minimal.model.*;import org.llmasr.minimal.modelmanagement.*;import org.llmasr.minimal.task.*;
public class AppGraph {public static AppGraph current;public static void install(android.content.Context c)throws java.io.IOException{}public static AppGraph get(){return current;}
public ModelSource zipSource;public final ModelManagementController controller;public final ModelRepository repository;public final ModelReadiness ready;public final TaskCoordinator owner;public final ModelPickerTickets tickets=new ModelPickerTickets();public int zipCalls;
public AppGraph(ModelManagementController c,ModelRepository r,ModelReadiness s,TaskCoordinator o){controller=c;repository=r;ready=s;owner=o;}
public ModelManagementController modelManagement(){return controller;}public ModelRepository modelRepository(){return repository;}public ModelReadiness readiness(){return ready;}public TaskCoordinator coordinator(){return owner;}public ModelPickerTickets pickerTickets(){return tickets;}public ModelAccess modelAccess(){return new ModelAccess(repository,ready);}public ModelSource modelZipSource(android.net.Uri uri){zipCalls++;if(zipSource!=null)return zipSource;return new SafModelSource(new android.content.ContentResolver(),uri);}public Asr asrOperation(){return new Asr();}public static class Asr {public boolean startPrepare(){return false;}public void startRelease(){}}}
'''
    s['org/llmasr/minimal/ZipPickerContractTest.java'] = HARNESS
    return s


HARNESS = '''package org.llmasr.minimal;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import android.content.*;import android.net.Uri;import android.widget.*;
import org.llmasr.minimal.model.*;import org.llmasr.minimal.modelmanagement.*;import org.llmasr.minimal.task.*;
public class ZipPickerContractTest {
static final List<Thread> workers=new ArrayList<>();
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static class Queue implements Executor {Runnable task;public void execute(Runnable r){check(task==null,"one owner");task=r;}void run(){Runnable r=task;task=null;r.run();}}
static String visibleText(android.view.View root){if(root.getVisibility()==android.view.View.GONE)return "";String s=root instanceof TextView?((TextView)root).getText().toString():"";if(root instanceof LinearLayout)for(android.view.View v:((LinearLayout)root).children)s+="\\n"+visibleText(v);return s;}
static Button find(android.view.View root,String text){if(root.getVisibility()==android.view.View.GONE)return null;if(root instanceof Button&&((Button)root).getText().toString().contains(text))return(Button)root;if(root instanceof LinearLayout)for(android.view.View v:((LinearLayout)root).children){Button b=find(v,text);if(b!=null)return b;}return null;}
static Button button(ModelManagementActivity a,String text){Button found=find(((ScrollView)a.content).child,text);if(found==null)throw new AssertionError(text);return found;}
static String text(ModelManagementActivity a){return visibleText(((ScrollView)a.content).child);}
static void render(ModelManagementActivity a)throws Exception{java.lang.reflect.Method m=ModelManagementActivity.class.getDeclaredMethod("render");m.setAccessible(true);m.invoke(a);}
static void drain(Queue q,ModelManagementActivity a)throws Exception{render(a);for(int n=0;q.task!=null&&n<5;n++){q.run();render(a);}check(q.task==null,"finite inspect drain");}
static byte[] tinyZip(FixedModelPackage fixed)throws Exception{
 ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(java.util.zip.ZipOutputStream out=new java.util.zip.ZipOutputStream(bytes)){
 for(int i=-1;i<7;i++){String name=i<0?"model-package.json":"f"+i;byte[] b=i<0?ModelPackagePolicy.legacy(fixed).json():new byte[]{(byte)i};java.util.zip.ZipEntry e=new java.util.zip.ZipEntry(name);e.setMethod(0);e.setSize(b.length);e.setCompressedSize(b.length);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(b);e.setCrc(crc.getValue());out.putNextEntry(e);out.write(b);out.closeEntry();}}
 return bytes.toByteArray();
}
static ModelPackagePolicy capPolicy(ModelPackagePolicy base,FixedModelPackage fixed,int number)throws Exception{
 List<ModelEntry> entries=new ArrayList<>();StringBuilder files=new StringBuilder();for(ModelEntry e:base.manifest.entries()){String hash=e.file().equals("audio.mnn")?ModelPackagePolicy.sha(new byte[]{(byte)(number+10)}):e.sha256();entries.add(new ModelEntry(e.file(),e.bytes(),hash));if(files.length()>0)files.append(',');files.append("{\\\"file\\\":\\\"").append(e.file()).append("\\\",\\\"bytes\\\":").append(e.bytes()).append(",\\\"sha256\\\":\\\"").append(hash).append("\\\"}");}
 String json="{\\\"schema\\\":2,\\\"modelId\\\":\\\"cap-"+number+"\\\",\\\"engine\\\":\\\"mnn\\\",\\\"architecture\\\":\\\"qwen3-asr-0.6b\\\",\\\"runtimePolicy\\\":\\\"qwen3-asr-mnn-cpu-v2\\\",\\\"precision\\\":\\\"INT4\\\",\\\"displayName\\\":\\\"Cap "+number+"\\\",\\\"contentSha256\\\":\\\""+ModelPackagePolicy.content(new ModelManifest(entries))+"\\\",\\\"files\\\":["+files+"]}";return ModelPackagePolicy.parse(json.getBytes(java.nio.charset.StandardCharsets.US_ASCII),fixed);
}
static void emptySlotUi(Path root,ModelPackagePolicy policy,FixedModelPackage fixed)throws Exception{
 Path files=Files.createDirectory(root.resolve("slot-files")),cache=Files.createDirectory(root.resolve("slot-cache"));Path unrelated=files.resolve("unrelated.txt");Files.write(unrelated,new byte[]{42});
 final boolean[] fail={false};ModelCatalog.Io io=new ModelCatalog.Io(){byte[]doc,rec;public byte[]readCatalog(){return doc;}public void writeCatalog(byte[]b){if(fail[0])throw new IllegalStateException("catalog injected durable failure");doc=b.clone();}public void restoreCatalog(byte[]b){doc=b;}public byte[]readRecovery(){return rec;}public void writeRecovery(byte[]b){rec=b;}public void clearRecovery(){rec=null;}public ModelVerification.Io proof(String k){return new ModelVerification.Io(){public String identity(File f,boolean d){return f.toString();}public boolean exists(File f){return f.exists();}public byte[]read(String n,int l){return null;}public void write(String n,byte[]b){}};}};
 ModelReadiness projected=new ModelReadiness();ModelResidency resident=new ModelResidency(new ModelResidency.Native(){public double prepare(String c,String t){throw new AssertionError("not inference");}public void release(){}},()->true);
 ModelCatalog first=new ModelCatalog(fixed,files.toFile(),cache.toFile(),()->Long.MAX_VALUE,resident,io,projected);first.load();ModelDeployment admitted=first.admit(policy);check(!admitted.repository.modelDir().exists(),"durable admitted descriptor before first asset");
 ModelCatalog catalog=new ModelCatalog(fixed,files.toFile(),cache.toFile(),()->Long.MAX_VALUE,resident,io,projected);catalog.load();ModelDeployment custom=catalog.require(policy.contentSha256);check(!custom.repository.modelDir().exists(),"empty slot survives restart");
 Queue q=new Queue();TaskCoordinator owner=new TaskCoordinator(q);ModelManagementController c=new ModelManagementController(owner,catalog.legacy().repository,catalog.legacy().readiness,new ModelOperationControl(),new ModelManagementState());c.bindCatalog(catalog);AppGraph.current=new AppGraph(c,catalog.legacy().repository,projected,owner);ModelManagementActivity a=new ModelManagementActivity();a.onCreate(null);a.onResume();drain(q,a);
 button(a,"Qwen3-ASR 固定部署").performClick();drain(q,a);check(!button(a,"删除… / 重试删除…").isEnabled(),"absent legacy delete stays disabled");a.onBackPressed();render(a);button(a,"SECOND MODEL").performClick();drain(q,a);check(button(a,"删除… / 重试删除…").isEnabled(),"registered empty custom delete must be enabled after restart");
 List<ModelDeployment> cap=new ArrayList<>();for(int n=0;n<15;n++)cap.add(catalog.admit(capPolicy(policy,fixed,n)));boolean capped=false;try{catalog.admit(capPolicy(policy,fixed,15));}catch(Exception expected){capped=expected.getMessage().contains("16");}check(capped&&catalog.rows().size()==17,"finite sixteen custom slots full before UI deletion");
 android.app.AlertDialog.positive=null;button(a,"删除… / 重试删除…").performClick();check(android.app.AlertDialog.positive!=null&&!owner.isBusy()&&catalog.rows().size()==17,"empty slot requires actual confirmation before deletion");android.app.AlertDialog.positive.click(null,0);drain(q,a);check(catalog.rows().size()==16&&Files.readAllBytes(unrelated)[0]==42,"confirmed empty slot frees descriptor and preserves unrelated data");cap.add(catalog.admit(capPolicy(policy,fixed,15)));check(catalog.rows().size()==17,"UI empty deletion actually frees cap admission");for(ModelDeployment extra:cap)catalog.deleted(extra);check(catalog.rows().size()==1,"finite cap fixtures removed before next case");
 custom=catalog.admit(policy);render(a);button(a,"SECOND MODEL").performClick();drain(q,a);Files.createDirectories(custom.repository.modelDir().toPath());Files.write(custom.repository.modelDir().toPath().resolve("audio.mnn"),new byte[]{1});c.refreshInspect();drain(q,a);fail[0]=true;button(a,"删除… / 重试删除…").performClick();android.app.AlertDialog.Click stale=android.app.AlertDialog.positive;stale.click(null,0);drain(q,a);check(c.state().current().phase==ModelManagementState.Phase.FAILED&&!custom.repository.modelDir().exists()&&catalog.rows().size()==2,"actual assets removed but catalog durable write failed retains slot");check(button(a,"删除… / 重试删除…").isEnabled(),"empty custom durable-delete failure stays retryable");fail[0]=false;stale.click(null,0);check(!owner.isBusy()&&catalog.rows().size()==2,"old epoch dialog cannot retry without fresh confirmation");
 android.os.Bundle savedSlot=new android.os.Bundle();a.onSaveInstanceState(savedSlot);a.onPause();a.onDestroy();catalog=new ModelCatalog(fixed,files.toFile(),cache.toFile(),()->Long.MAX_VALUE,resident,io,projected);catalog.load();c=new ModelManagementController(owner,catalog.legacy().repository,catalog.legacy().readiness,new ModelOperationControl(),new ModelManagementState());c.bindCatalog(catalog);AppGraph.current=new AppGraph(c,catalog.legacy().repository,projected,owner);a=new ModelManagementActivity();a.onCreate(savedSlot);a.onResume();drain(q,a);check(button(a,"删除… / 重试删除…").isEnabled(),"catalog write failure empty retry survives restart");
 button(a,"删除… / 重试删除…").performClick();android.app.AlertDialog.Click emptyInstallation=android.app.AlertDialog.positive;emptyInstallation.click(null,0);drain(q,a);check(c.state().current().phase==ModelManagementState.Phase.SUCCEEDED&&catalog.rows().size()==1,"fresh confirmed retry releases catalog cap slot");check(Files.readAllBytes(unrelated)[0]==42,"unrelated data preserved through retry");
 ModelDeployment reinstalled=catalog.admit(policy);render(a);emptyInstallation.click(null,0);check(!owner.isBusy()&&catalog.require(reinstalled.key).installationId.equals(reinstalled.installationId),"stale installation dialog cannot remove reinstalled empty slot");
 render(a);button(a,"SECOND MODEL").performClick();drain(q,a);Files.createDirectories(reinstalled.repository.modelDir().toPath());Path unknown=reinstalled.repository.modelDir().toPath().resolve("unknown.txt");Files.write(unknown,new byte[]{7});c.refreshInspect();drain(q,a);button(a,"删除… / 重试删除…").performClick();android.app.AlertDialog.positive.click(null,0);drain(q,a);check(Files.readAllBytes(unknown)[0]==7&&catalog.rows().size()==2&&c.state().current().phase==ModelManagementState.Phase.FAILED,"unknown custom contents preserved and record retained");
 a.onPause();a.onDestroy();System.out.println("PASS actual Activity empty custom admission/restart/delete durable failure/retry/stale/unknown host fake NOT Android");
}
static void importUi(Path root,String mode)throws Exception{
 Path files=Files.createDirectory(root.resolve("import-files-"+mode)),cache=Files.createDirectory(root.resolve("import-cache-"+mode));
 List<ModelEntry> entries=new ArrayList<>();for(int i=0;i<7;i++)entries.add(new ModelEntry("f"+i,1,ModelPackagePolicy.sha(new byte[]{(byte)i})));
 FixedModelPackage fixed=new FixedModelPackage(new ModelManifest(entries),"import-ui","mnn",1048576);
 ModelCatalog.Io io=new ModelCatalog.Io(){byte[] doc,rec;public byte[]readCatalog(){return doc;}public void writeCatalog(byte[]b){doc=b.clone();}public void restoreCatalog(byte[]b){doc=b;}public byte[]readRecovery(){return rec;}public void writeRecovery(byte[]b){rec=b;}public void clearRecovery(){rec=null;}public ModelVerification.Io proof(String k){return new ModelVerification.Io(){public String identity(File f,boolean d){return f.toString();}public boolean exists(File f){return f.exists();}public byte[]read(String n,int l){return null;}public void write(String n,byte[]b){}};}};
 ModelReadiness projected=new ModelReadiness();ModelResidency resident=new ModelResidency(new ModelResidency.Native(){public double prepare(String c,String t){throw new AssertionError("not inference");}public void release(){}},()->true);
 ModelCatalog catalog=new ModelCatalog(fixed,files.toFile(),cache.toFile(),()->Long.MAX_VALUE,resident,io,projected);catalog.load();Queue q=new Queue();TaskCoordinator owner=new TaskCoordinator(q);ModelManagementController c=new ModelManagementController(owner,catalog.legacy().repository,catalog.legacy().readiness,new ModelOperationControl(),new ModelManagementState());c.bindCatalog(catalog);
 AppGraph.current=new AppGraph(c,catalog.legacy().repository,projected,owner);ModelManagementActivity a=new ModelManagementActivity();a.onCreate(null);a.onResume();drain(q,a);
 boolean staged=mode.equals("stage-unknown");Path stage=files.resolve("model-zip-staging"),archive=stage.resolve("archive.part"),sentinel=stage.resolve("unknown");byte[] stale={11,12,13};if(staged){Files.createDirectory(stage);Files.write(archive,stale);Files.write(sentinel,new byte[]{42});}
 byte[] bytes=tinyZip(fixed);if(mode.equals("crc")||staged){for(int at=0;at<bytes.length-33;at++)if(bytes[at]=='P'&&bytes[at+1]=='K'&&bytes[at+2]==3&&bytes[at+3]==4&&bytes[at+26]==2&&bytes[at+30]=='f'&&bytes[at+31]=='0'){bytes[at+32]^=1;break;}}
 boolean cancelling=mode.startsWith("cancel");CountDownLatch blocked=new CountDownLatch(1),proceed=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<Throwable> thrown=new java.util.concurrent.atomic.AtomicReference<>();java.util.concurrent.atomic.AtomicInteger closed=new java.util.concurrent.atomic.AtomicInteger(),opened=new java.util.concurrent.atomic.AtomicInteger(),stageClosed=new java.util.concurrent.atomic.AtomicInteger();
 AppGraph.current.zipSource=new ModelZipSource(()->{opened.incrementAndGet();return new ByteArrayInputStream(bytes){int reads;public synchronized int read(byte[]b,int off,int len){if(mode.equals("cancel")&&reads++==1){blocked.countDown();try{if(!proceed.await(5,TimeUnit.SECONDS))throw new AssertionError("provider unblock timeout");}catch(InterruptedException e){throw new AssertionError(e);}}return super.read(b,off,Math.min(len,128));}public void close()throws IOException{super.close();if(mode.equals("cancel-close")){blocked.countDown();try{if(!proceed.await(5,TimeUnit.SECONDS))throw new AssertionError("close unblock timeout");}catch(InterruptedException e){throw new AssertionError(e);}}closed.incrementAndGet();}};},()->files.toFile(),()->fixed,()->System.nanoTime()){
  protected DirectoryStream<Path> openStage(File directory){DirectoryStream<Path> actual=super.openStage(directory);if(!staged||!Files.exists(sentinel))return actual;return new DirectoryStream<Path>(){public Iterator<Path> iterator(){return actual.iterator();}public void close()throws IOException{blocked.countDown();try{if(!proceed.await(5,TimeUnit.SECONDS))throw new AssertionError("stage close unblock timeout");}catch(InterruptedException e){throw new AssertionError(e);}finally{actual.close();stageClosed.incrementAndGet();}}};}
 };
 button(a,"导入 ZIP").performClick();a.onActivityResult(a.request,-1,new Intent().setData(Uri.parse("content://synthetic")));Runnable job=q.task;q.task=null;check(job!=null,"list ZIP worker admitted");Thread worker=new Thread(()->{try{job.run();}catch(Throwable t){thrown.set(t);}},"import-ui");workers.add(worker);worker.start();
 try{
  if(staged){check(blocked.await(5,TimeUnit.SECONDS),"stage directory close actually blocked before terminal");render(a);check(owner.isBusy()&&stageClosed.get()==0&&opened.get()==0&&closed.get()==0,"unknown-stage owner waits for actual directory close before opener");check(c.importSnapshot().cleanupOutcome!=ModelManagementState.Outcome.SUCCEEDED&&!text(a).contains("受管残片检查完成"),"blocked stage cannot report cleanup complete");check(Arrays.equals(Files.readAllBytes(archive),stale)&&Files.readAllBytes(sentinel)[0]==42,"blocked stage retains managed and unknown data");}
  if(cancelling){check(blocked.await(5,TimeUnit.SECONDS),"provider actually blocked in read or close after bytes");render(a);String shown=text(a);check(shown.contains("复制私有模型包")&&shown.contains(mode.equals("cancel")?"128 bytes":bytes.length+" bytes")&&shown.contains("取消操作"),"list discovery phase bytes and cancel must be visible");check(!shown.contains("SHA-256:"),"global import has no model file evidence");ModelManagementActivity observer=new ModelManagementActivity();observer.onCreate(null);observer.onResume();render(observer);check(!text(observer).contains("取消操作"),"another page cannot cancel unowned global import");observer.onPause();observer.onStop();observer.onDestroy();check(!c.opControl().peekActive().isCancelRequested(),"unowned page stop cannot cancel owner operation");button(a,"0/7").performClick();render(a);check(c.snapshotFor(ModelDeployment.LEGACY)==null,"discovery cannot retag another card evidence");button(a,"取消操作").performClick();render(a);check(owner.isBusy()&&closed.get()==0&&text(a).contains("正在取消"),"cancel retains owner until provider close");check(!button(a,"取消操作").isEnabled(),"cancel pending no repeated admission");}
 }finally{proceed.countDown();worker.join(10000);}
 check(!worker.isAlive()&&thrown.get()==null&&!owner.isBusy()&&closed.get()==(staged?0:1),"import worker and actual close exited");render(a);
 if(staged){check(stageClosed.get()==1&&opened.get()==0,"stage refused before opener and owner released only after directory close");check(Arrays.equals(Files.readAllBytes(archive),stale)&&Files.readAllBytes(sentinel)[0]==42,"unknown-stage preserves exact stale archive and sentinel");check(c.importSnapshot().cleanupOutcome==ModelManagementState.Outcome.FAILED&&!text(a).contains("受管残片检查完成"),"unknown-stage cleanup must not report complete while stale archive remains");}
 else check(!Files.exists(archive),"prebinding archive removed");
 check(c.state().current().phase==(cancelling?ModelManagementState.Phase.CANCELLED:ModelManagementState.Phase.FAILED),"actual terminal import outcome");
 String id=c.state().current().activeOperationId;String diagnostic=c.state().current().errorCode;check(diagnostic!=null,"prebinding diagnostic retained");if(staged)check(diagnostic.equals("模型包暂存目录含未知文件，已保留"),"unknown-stage exact diagnostic");if(mode.equals("crc"))check(diagnostic.equals("模型 ZIP 格式不支持/校验失败"),"prebinding CRC must preserve actual archive diagnostic");a.onPause();a.onStop();a.onResume();drain(q,a);render(a);check(text(a).contains(id)&&text(a).contains(ModelUiText.safeError(diagnostic))&&text(a).contains(staged?"清理未完全成功":"受管残片检查完成"),"global prebinding terminal diagnostic cleanup survives reentry inspect");
 ModelManagementState.Snapshot summary=c.importSnapshot();check(summary.files.isEmpty()&&summary.fileResults.isEmpty()&&summary.importPlan==null&&!summary.ready,"global summary excludes install-bound file/proof data");
 if(staged){check(summary.cleanupOutcome==ModelManagementState.Outcome.FAILED&&!text(a).contains("受管残片检查完成")&&Arrays.equals(Files.readAllBytes(archive),stale)&&Files.readAllBytes(sentinel)[0]==42,"unknown-stage failed cleanup and data retained after reentry inspect");check(c.snapshotFor(ModelDeployment.LEGACY)==null||!id.equals(c.snapshotFor(ModelDeployment.LEGACY).activeOperationId),"discovery error cannot become legacy model history");Files.delete(sentinel);button(a,"导入 ZIP").performClick();a.onActivityResult(a.request,-1,new Intent().setData(Uri.parse("content://synthetic-retry")));check(q.task!=null,"fixture-authorized stage retry admitted");q.run();drain(q,a);check(!owner.isBusy()&&opened.get()==1&&closed.get()==1&&!Files.exists(archive),"after fixture sentinel removal stale archive reclaimed and provider really closed");check(c.importSnapshot().cleanupOutcome==ModelManagementState.Outcome.SUCCEEDED&&"模型 ZIP 格式不支持/校验失败".equals(c.importSnapshot().errorCode)&&text(a).contains("受管残片检查完成"),"normal prebinding CRC cleanup remains truthful after stage retry");}
 a.onPause();a.onDestroy();System.out.println("PASS actual Activity global import "+mode+" host fake NOT Android");
}
public static void main(String[]args)throws Exception {
Path root=Files.createTempDirectory("zip-picker-");Queue q=new Queue();TaskCoordinator owner=new TaskCoordinator(q);
ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("a",0,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")));
ModelRepository repo=new ModelRepository(manifest,root.resolve("model").toFile(),()->Long.MAX_VALUE);ModelReadiness ready=new ModelReadiness();ModelManagementController c=new ModelManagementController(owner,repo,ready,new ModelOperationControl(),new ModelManagementState());
AppGraph.current=new AppGraph(c,repo,ready,owner);
try {
if(args.length==1&&args[0].equals("stage")){importUi(root,"stage-unknown");return;}
ModelManagementActivity a=new ModelManagementActivity();a.onCreate(null);a.onResume();if(q.task!=null)q.run();
check(find(((ScrollView)a.content).child,"选用")==null&&find(((ScrollView)a.content).child,"返回模型列表")==null,"detail actions not reachable from collapsed list");
check(!visibleText(((ScrollView)a.content).child).contains("SHA-256:"),"collapsed list must not display file hashes");
button(a,"导入 ZIP").performClick();int zip=a.request;check(a.launched.action.equals(Intent.ACTION_OPEN_DOCUMENT)&&a.launched.category.equals(Intent.CATEGORY_OPENABLE)&&!a.launched.multiple&&(a.launched.flags&1)==1,"ZIP single read/openable Intent");
a.onActivityResult(zip,-1,new Intent().setData(Uri.parse("file:///private.zip")));check(AppGraph.current.zipCalls==0&&!owner.isBusy(),"non-content picker zero IO");
button(a,"导入 ZIP").performClick();int next=a.request;check(next!=zip,"new request code");
a.onActivityResult(zip,-1,new Intent().setData(Uri.parse("content://old.zip")));check(AppGraph.current.zipCalls==0,"ZIP stale or mismatched picker cannot import");
a.onActivityResult(next,-1,new Intent().setData(Uri.parse("content://multi.zip")).setClipData(new ClipData(2)));check(AppGraph.current.zipCalls==0,"multiple clip zero IO");
a.launchFailure=new RuntimeException("launch");button(a,"导入 ZIP").performClick();a.launchFailure=null;button(a,"兼容入口").performClick();button(a,"兼容目录…").performClick();int directory=a.request;check(a.launched.action.equals(Intent.ACTION_OPEN_DOCUMENT_TREE),"directory compatibility");
a.onActivityResult(directory,0,null);check(!owner.isBusy(),"cancel picker no mutation");
button(a,"导入 ZIP").performClick();int deferred=a.request;a.onPause();a.onActivityResult(deferred,-1,new Intent().setData(Uri.parse("content://x.no-suffix")));check(AppGraph.current.zipCalls==0&&!owner.isBusy(),"background defers typed URI");a.onResume();check(AppGraph.current.zipCalls==1&&owner.isBusy(),"foreground consumes ZIP type without suffix guess");
button(a,"导入 ZIP").performClick();check(AppGraph.current.zipCalls==1,"busy cannot launch/import");q.run();a.onDestroy();
ModelManagementActivity rotated=new ModelManagementActivity();rotated.onCreate(null);rotated.onResume();if(q.task!=null)q.run();button(rotated,"导入 ZIP").performClick();int obsolete=rotated.request;android.os.Bundle saved=new android.os.Bundle();rotated.onSaveInstanceState(saved);rotated.onPause();rotated.changing=true;rotated.onStop();rotated.onDestroy();ModelManagementActivity replacement=new ModelManagementActivity();replacement.onCreate(saved);replacement.onResume();if(q.task!=null)q.run();int before=AppGraph.current.zipCalls;replacement.onActivityResult(obsolete,-1,new Intent().setData(Uri.parse("content://stale")));check(AppGraph.current.zipCalls==before&&!owner.isBusy(),"rotation does not revive picker capability");replacement.onDestroy();
// Actual catalog-backed compact card -> details -> back, same production Activity/controller.
Path catalogFiles=root.resolve("catalog-files");Files.createDirectory(catalogFiles);Path catalogCache=root.resolve("catalog-cache");Files.createDirectory(catalogCache);
java.util.List<ModelEntry> catalogEntries=new ArrayList<>();for(int i=0;i<7;i++)catalogEntries.add(new ModelEntry("f"+i,1,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
FixedModelPackage catalogFixed=new FixedModelPackage(new ModelManifest(catalogEntries),"catalog-fixture","mnn",1048576);
ModelReadiness projected=new ModelReadiness();ModelResidency resident=new ModelResidency(new ModelResidency.Native(){public double prepare(String c,String t){throw new AssertionError("UI must not native load");}public void release(){}},()->true);
ModelCatalog.Io catalogIo=new ModelCatalog.Io(){byte[] recovery;byte[] descriptor;public byte[]readRecovery(){return recovery;}public void writeRecovery(byte[]b){recovery=b.clone();}public void clearRecovery(){recovery=null;}public byte[]readCatalog(){return descriptor;}public void writeCatalog(byte[]b){descriptor=b.clone();}public void restoreCatalog(byte[]b){descriptor=b==null?null:b.clone();}public ModelVerification.Io proof(String key){return new ModelVerification.Io(){public String identity(File f,boolean d){return f.toString();}public boolean exists(File f){return f.exists();}public byte[]read(String n,int l){return null;}public void write(String n,byte[]b){}};}};
ModelCatalog catalog=new ModelCatalog(catalogFixed,catalogFiles.toFile(),catalogCache.toFile(),()->Long.MAX_VALUE,resident,catalogIo,projected);catalog.load();
ModelManagementController managed=new ModelManagementController(owner,catalog.legacy().repository,catalog.legacy().readiness,new ModelOperationControl(),new ModelManagementState());managed.bindCatalog(catalog);
AppGraph.current=new AppGraph(managed,catalog.legacy().repository,projected,owner);ModelManagementActivity cards=new ModelManagementActivity();cards.onCreate(null);cards.onResume();if(q.task!=null)q.run();java.lang.reflect.Method actualRender=ModelManagementActivity.class.getDeclaredMethod("render");actualRender.setAccessible(true);actualRender.invoke(cards);
String compact=visibleText(((ScrollView)cards.content).child);check(compact.contains("0/7")&&!compact.contains("f0")&&!compact.contains("SHA-256:"),"compact model card only verification count no file list");
button(cards,"0/7").performClick();if(q.task!=null)q.run();actualRender.invoke(cards);button(cards,"文件详情").performClick();String detailed=visibleText(((ScrollView)cards.content).child);check(!button(cards,"已选用").isEnabled(),"current model selection action disabled");check(detailed.contains("f0")&&detailed.contains("f6")&&detailed.contains("SHA-256:"),"click actual details shows all seven files and hashes");
cards.onBackPressed();actualRender.invoke(cards);check(!visibleText(((ScrollView)cards.content).child).contains("SHA-256:"),"detail back returns compact list");
// Second genuine custom identity. Deliberately missing assets distinguish it from legacy history.
StringBuilder secondFiles=new StringBuilder();for(String name:ModelPackagePolicy.NAMES){long size=name.equals("config.json")?617:name.equals("tokenizer.txt")?1:1;String h=name.equals("config.json")?ModelPackagePolicy.BASE_CONFIG_SHA:"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";if(secondFiles.length()>0)secondFiles.append(',');secondFiles.append("{\\\"file\\\":\\\"").append(name).append("\\\",\\\"bytes\\\":").append(size).append(",\\\"sha256\\\":\\\"").append(h).append("\\\"}");}
List<ModelEntry> secondEntries=new ArrayList<>();for(String name:ModelPackagePolicy.NAMES)secondEntries.add(new ModelEntry(name,name.equals("config.json")?617:1,name.equals("config.json")?ModelPackagePolicy.BASE_CONFIG_SHA:"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
FixedModelPackage secondFixed=new FixedModelPackage(new ModelManifest(secondEntries),"fixture-v1","mnn",1048576);
String descriptor="{\\\"schema\\\":2,\\\"modelId\\\":\\\"second-fixture\\\",\\\"engine\\\":\\\"mnn\\\",\\\"architecture\\\":\\\"qwen3-asr-0.6b\\\",\\\"runtimePolicy\\\":\\\"qwen3-asr-mnn-cpu-v2\\\",\\\"precision\\\":\\\"INT4\\\",\\\"displayName\\\":\\\"SECOND MODEL\\\",\\\"contentSha256\\\":\\\""+ModelPackagePolicy.content(new ModelManifest(secondEntries))+"\\\",\\\"files\\\":["+secondFiles+"]}";
ModelDeployment second=catalog.admit(ModelPackagePolicy.parse(descriptor.getBytes(java.nio.charset.StandardCharsets.US_ASCII),secondFixed));
// Queue A inspection, browse B while A owns worker, then complete A. View/action remains B.
managed.showDetail(ModelDeployment.LEGACY);button(cards,"Qwen3-ASR 固定部署").performClick();cards.onBackPressed();actualRender.invoke(cards);button(cards,"SECOND MODEL").performClick();check(owner.isBusy(),"A inspect still owns worker");q.run();actualRender.invoke(cards);
check(managed.detailKey().equals(second.key),"older admitted worker must not overwrite newer detail key");check(!managed.snapshotMatches(second.key),"A snapshot must not be retagged to B");check(managed.snapshotFor(second.key)==null,"identity-bound storage snapshot not available for B until B inspected");
String secondText=visibleText(((ScrollView)cards.content).child);check(secondText.contains("SECOND MODEL")&&!secondText.contains("f0\\n")&&!secondText.contains("f6\\n"),"B detail must not render A cached files/history: "+secondText);
button(cards,"维护与说明").performClick();button(cards,"恢复证明").performClick();if(q.task!=null)q.run();actualRender.invoke(cards);check(managed.detailKey().equals(second.key),"restore targets visible B");
// Actual dialog callback must capture B; a later navigation cannot delete A instead.
Files.createDirectories(second.repository.modelDir().toPath());Files.write(second.repository.modelDir().toPath().resolve("audio.mnn"),new byte[]{1});managed.refreshInspect();q.run();actualRender.invoke(cards);
button(cards,"删除… / 重试删除…").performClick();android.app.AlertDialog.Click late=android.app.AlertDialog.positive;check(late!=null,"actual delete callback captured");cards.onBackPressed();actualRender.invoke(cards);button(cards,"Qwen3-ASR 固定部署").performClick();if(q.task!=null)q.run();actualRender.invoke(cards);late.click(null,0);check(!owner.isBusy()&&Files.exists(second.repository.modelDir().toPath().resolve("audio.mnn")),"late B dialog while viewing A cannot delete either target");
cards.onBackPressed();actualRender.invoke(cards);button(cards,"SECOND MODEL").performClick();if(q.task!=null)q.run();actualRender.invoke(cards);android.os.Bundle restoreDetail=new android.os.Bundle();cards.onSaveInstanceState(restoreDetail);cards.onPause();cards.onDestroy();
ModelManagementController reconstructed=new ModelManagementController(owner,catalog.legacy().repository,catalog.legacy().readiness,new ModelOperationControl(),new ModelManagementState());reconstructed.bindCatalog(catalog);AppGraph.current=new AppGraph(reconstructed,catalog.legacy().repository,projected,owner);ModelManagementActivity recreated=new ModelManagementActivity();recreated.onCreate(restoreDetail);recreated.onResume();if(q.task!=null)q.run();actualRender.invoke(recreated);for(int drain=0;q.task!=null&&drain<3;drain++){q.run();actualRender.invoke(recreated);}check(!owner.isBusy(),"recreated detail inspection drained before user action");check(reconstructed.detailKey().equals(second.key)&&visibleText(((ScrollView)recreated.content).child).contains("SECOND MODEL"),"restored B detail safely rebinds reconstructed controller");
// Same-model inspect preserves explicit failed verification history; different target does not inherit it.
check(button(recreated,"校验").performClick(),"visible B verification button admitted");if(q.task!=null)q.run();actualRender.invoke(recreated);check(reconstructed.state().current().phase==ModelManagementState.Phase.FAILED,"B verification failed on incomplete own assets: phase="+reconstructed.state().current().phase+" enabled="+button(recreated,"校验").isEnabled()+" error="+reconstructed.state().current().errorCode);String failureId=reconstructed.state().current().activeOperationId;reconstructed.refreshInspect();q.run();actualRender.invoke(recreated);check(failureId.equals(reconstructed.state().current().activeOperationId)&&reconstructed.state().current().phase==ModelManagementState.Phase.FAILED,"same model INSPECT retains its own explicit history");
button(recreated,"删除… / 重试删除…").performClick();android.app.AlertDialog.Click oldInstallation=android.app.AlertDialog.positive;oldInstallation.click(null,0);if(q.task!=null)q.run();check(!Files.exists(second.repository.modelDir().toPath().resolve("audio.mnn"))&&!owner.isBusy(),"actual dialog deletes explicitly visible B after confirmed identity");
ModelDeployment reinstalled=catalog.admit(ModelPackagePolicy.parse(descriptor.getBytes(java.nio.charset.StandardCharsets.US_ASCII),secondFixed));Files.createDirectories(reinstalled.repository.modelDir().toPath());Files.write(reinstalled.repository.modelDir().toPath().resolve("audio.mnn"),new byte[]{2});actualRender.invoke(recreated);oldInstallation.click(null,0);check(!owner.isBusy()&&Files.readAllBytes(reinstalled.repository.modelDir().toPath().resolve("audio.mnn"))[0]==2,"old installation dialog callback cannot mutate reinstalled visible content key");
final boolean[] notified={false};ModelManagementState.Listener reentrant=()->{if(!notified[0]){notified[0]=true;reconstructed.showDetail(ModelDeployment.LEGACY);}};reconstructed.state().addListener(reentrant);reconstructed.showDetail(reinstalled.key);if(q.task!=null)q.run();reconstructed.state().removeListener(reentrant);check(notified[0]&&reconstructed.detailKey().equals(ModelDeployment.LEGACY),"reentrant explicit navigation not overwritten by earlier worker");recreated.onPause();recreated.onDestroy();AppGraph.current=new AppGraph(c,repo,ready,owner);
// Actual runtime adapter: one resolver stream, no metadata query, sanitized open/read/close faults.
java.util.List<ModelEntry> seven=new java.util.ArrayList<>();for(int i=0;i<7;i++)seven.add(new ModelEntry("f"+i,0,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));ModelManifest sevenManifest=new ModelManifest(seven);FixedModelPackage policy=new FixedModelPackage(sevenManifest,"fixture","mnn",22);
for(String failure:new String[]{"open","read","close","linkage"}) {
 final String kind=failure;final int[] opens={0};ContentResolver resolver=new ContentResolver(){public InputStream openInputStream(Uri uri)throws IOException {opens[0]++;if(kind.equals("open"))throw new RuntimeException("content://private/account");if(kind.equals("linkage"))throw new LinkageError("private");return new ByteArrayInputStream(new byte[1]){public int read(byte[]b,int o,int n){if(kind.equals("read"))throw new IllegalStateException("private");return super.read(b,o,n);}public void close()throws IOException{super.close();if(kind.equals("close"))throw new IOException("private");}};}};
 SafModelZipSource source=new SafModelZipSource(resolver,Uri.parse("content://model"),()->root.toFile(),()->policy);Throwable caught=null;try{source.openSession(sevenManifest,()->Long.MAX_VALUE,null,ModelRepository.NEVER_CANCEL);}catch(Throwable t){caught=t;}check(caught instanceof IOException&&opens[0]==1&&!caught.toString().contains("private"),"SAF ZIP provider failures sanitized");check(!Files.exists(root.resolve("model-zip-staging/archive.part")),"SAF ZIP fault cleanup");}
ModelPickerTickets tickets=new ModelPickerTickets();ModelPageSession<String> page=new ModelPageSession<>(tickets,c,null);page.foreground(true);int code=page.beginPicker(ModelPickerTickets.Mode.ZIP);check(!page.result(code,ModelPickerTickets.Mode.DIRECTORY,"bad"),"ZIP stale or mismatched picker cannot import");check(page.result(code,ModelPickerTickets.Mode.ZIP,"good"),"typed result");page.destroy();check(!page.consumeTyped(v->true),"destroy stale result");
if(args.length==0||!args[0].equals("global"))emptySlotUi(root,ModelPackagePolicy.parse(descriptor.getBytes(java.nio.charset.StandardCharsets.US_ASCII),secondFixed),secondFixed);
importUi(root,"stage-unknown");importUi(root,"cancel");importUi(root,"cancel-close");importUi(root,"crc");
System.out.println("PASS complete production Activity typed picker host fakes (NOT Android)");
} finally {if(q.task!=null)q.run();check(!owner.isBusy(),"owner exit before cleanup");for(Thread w:workers)check(!w.isAlive(),"retain root while actual worker still alive");try(java.util.stream.Stream<Path>s=Files.walk(root)){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
}}
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir',required=True,type=Path)
    args=parser.parse_args()
    out=args.evidence_dir.resolve()
    require(not out.exists(),'fresh evidence dir required')
    out.mkdir(parents=True)
    start=time.monotonic()
    generated=stubs()
    host=(ROOT/'scripts/test-minimal-apk.sh').read_text().split('host_classes=(',1)[1].split(')',1)[0].split()
    names=list(dict.fromkeys(host+['model.SafModelSource','model.SafModelZipSource','ModelManagementActivity']))
    sources=[]
    for name in names:
        p=BASE/name.replace('.','/')
        found=[p.with_suffix(ext) for ext in ('.kt','.java') if p.with_suffix(ext).is_file()]
        require(len(found)==1,'exact owner '+name)
        relative=str(found[0].relative_to(BASE))
        generated['org/llmasr/minimal/'+relative]=found[0].read_text()
    for relative,body in generated.items():
        path=out/'src'/relative
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(body+'\n')
        sources.append(path)
    (out/'inputs.json').write_text(json.dumps([{'path':str(p.relative_to(out)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}for p in sources],indent=2)+'\n')
    attempted=[]
    def execute(argv,label,timeout):
        attempted.append(label)
        (out/'attempted.json').write_text(json.dumps(attempted,indent=2)+'\n')
        return owned_run(argv,out,label,timeout)
    def route(label, diagnostic=None):
        require(time.monotonic()-start<1800,'picker deadline exceeded')
        classes=out/('classes-'+label)
        result=execute(['bash',str(ROOT/'scripts/compile-jvm-sources.sh'),str(classes),'',*map(str,sources)],label+'-compile',180)
        require(result.returncode==0,'picker compile/tool failure not behavioral red: '+result.stderr.decode(errors='replace'))
        cp=str(classes)+':'+str(Path(os.environ['KOTLIN_HOME'])/'lib/kotlin-stdlib.jar')
        result=execute(['java','-Xmx256m','-cp',cp,'org.llmasr.minimal.ZipPickerContractTest'],label+'-jvm',30)
        text=(result.stdout+result.stderr).decode(errors='replace')
        if diagnostic is None:
            require(result.returncode==0,'picker behavior failed: '+text)
        else:
            require(result.returncode==1 and 'java.lang.AssertionError: '+diagnostic in text,'not exact picker production red: '+text)
    route('green')
    page=out/'src/org/llmasr/minimal/modelmanagement/ModelPageSession.kt'
    original=page.read_bytes()
    anchor='if (ticket?.mode != mode) return false'
    require(original.decode().count(anchor)==1,'unique typed picker production mutation')
    primary=None
    try:
        page.write_text(original.decode().replace(anchor,'if (false) return false'))
        route('typed-mutant','ZIP stale or mismatched picker cannot import')
    except BaseException as failure:
        primary=failure
        raise
    finally:
        try:
            guard(out)
            for label in attempted:
                state=json.loads((out/(label+'.json')).read_text())
                require(state.get('owned_exited') is True and state.get('root_reaped') is True,'retain typed mutant with absent/unknown attempted exit '+label)
            page.write_bytes(original)
        except BaseException as cleanup:
            if primary is None:
                raise
            primary.add_note('typed restoration refused; retained input: '+repr(cleanup))
    require(hashlib.sha256(page.read_bytes()).digest()==hashlib.sha256(original).digest(),'typed source exact restore')
    route('typed-restored')
    require(time.monotonic()-start<1800,'picker deadline exceeded')
    guard(out)
    print('PASS production ZIP picker',flush=True)


if __name__=='__main__':
    main()
