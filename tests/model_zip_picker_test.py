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
public static final int FLAG_GRANT_READ_URI_PERMISSION=1;public String action,category,type;public int flags;public boolean multiple=true;public String[] mimes;private android.net.Uri uri;private ClipData clip;
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
public boolean isDestroyed(){return false;}public boolean isFinishing(){return false;}public boolean isChangingConfigurations(){return changing;}public void onBackPressed(){}public void finish(){}
public void setContentView(android.view.View v){content=v;}public android.view.Window getWindow(){return new android.view.Window();}public void startActivityForResult(android.content.Intent i,int c){if(launchFailure!=null)throw launchFailure;launched=i;request=c;}}
'''
    s['android/app/AlertDialog.java'] = '''package android.app;public class AlertDialog {public interface Click {void click(Object d,int w);}public static class Builder {public Builder(Activity a){}public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}public Builder setNegativeButton(String s,Click c){return this;}public Builder setPositiveButton(String s,Click c){return this;}public void show(){}}}'''
    s['android/view/Window.java'] = 'package android.view;public class Window {public void clearFlags(int n){}public void addFlags(int n){}}'
    s['android/view/WindowManager.java'] = 'package android.view;public class WindowManager {public static class LayoutParams {public static final int FLAG_KEEP_SCREEN_ON=128;}}'
    s['android/view/View.java'] = '''package android.view;public class View {public static final int VISIBLE=0,GONE=8;public interface OnClickListener {void onClick(View v);}private int visibility;public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;}public void setPadding(int a,int b,int c,int d){}public void setBackgroundColor(int c){} }'''
    s['android/widget/TextView.java'] = '''package android.widget;public class TextView extends android.view.View {String text="";public TextView(android.content.Context c){}public void setText(CharSequence s){text=s.toString();}public CharSequence getText(){return text;}public void setTextSize(int n){}public void setTextColor(int n){}public void setTextColor(android.content.res.ColorStateList c){}public void setTextIsSelectable(boolean b){} }'''
    s['android/widget/Button.java'] = '''package android.widget;public class Button extends TextView {android.view.View.OnClickListener listener;boolean enabled=true;public Button(android.content.Context c){super(c);}public void setAllCaps(boolean b){}public void setMinHeight(int n){}public void setMinimumHeight(int n){}public void setSingleLine(boolean b){}public void setEnabled(boolean b){enabled=b;}public boolean isEnabled(){return enabled;}public void setOnClickListener(android.view.View.OnClickListener l){listener=l;}public boolean performClick(){if(!enabled)return false;listener.onClick(this);return true;}}'''
    s['android/widget/LinearLayout.java'] = '''package android.widget;public class LinearLayout extends android.view.View {public static final int VERTICAL=1;public java.util.List<android.view.View> children=new java.util.ArrayList<>();public LinearLayout(android.content.Context c){}public void setOrientation(int n){}public void addView(android.view.View v){children.add(v);}public void addView(android.view.View v,LayoutParams p){children.add(v);}public static class LayoutParams {public LayoutParams(int a,int b){}}}'''
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
public final ModelManagementController controller;public final ModelRepository repository;public final ModelReadiness ready;public final TaskCoordinator owner;public final ModelPickerTickets tickets=new ModelPickerTickets();public int zipCalls;
public AppGraph(ModelManagementController c,ModelRepository r,ModelReadiness s,TaskCoordinator o){controller=c;repository=r;ready=s;owner=o;}
public ModelManagementController modelManagement(){return controller;}public ModelRepository modelRepository(){return repository;}public ModelReadiness readiness(){return ready;}public TaskCoordinator coordinator(){return owner;}public ModelPickerTickets pickerTickets(){return tickets;}public ModelAccess modelAccess(){return new ModelAccess(repository,ready);}public ModelSource modelZipSource(android.net.Uri uri){zipCalls++;return new SafModelSource(new android.content.ContentResolver(),uri);}public Asr asrOperation(){return new Asr();}public static class Asr {public boolean startPrepare(){return false;}public void startRelease(){}}}
'''
    s['org/llmasr/minimal/ZipPickerContractTest.java'] = HARNESS
    return s


HARNESS = '''package org.llmasr.minimal;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import android.content.*;import android.net.Uri;import android.widget.*;
import org.llmasr.minimal.model.*;import org.llmasr.minimal.modelmanagement.*;import org.llmasr.minimal.task.*;
public class ZipPickerContractTest {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static class Queue implements Executor {Runnable task;public void execute(Runnable r){check(task==null,"one owner");task=r;}void run(){Runnable r=task;task=null;r.run();}}
static Button button(ModelManagementActivity a,String text){for(android.view.View v:((LinearLayout)((ScrollView)a.content).child).children)if(v instanceof Button&&((Button)v).getText().toString().contains(text))return(Button)v;throw new AssertionError(text);}
public static void main(String[]args)throws Exception {
Path root=Files.createTempDirectory("zip-picker-");Queue q=new Queue();TaskCoordinator owner=new TaskCoordinator(q);
ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("a",0,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")));
ModelRepository repo=new ModelRepository(manifest,root.resolve("model").toFile(),()->Long.MAX_VALUE);ModelReadiness ready=new ModelReadiness();ModelManagementController c=new ModelManagementController(owner,repo,ready,new ModelOperationControl(),new ModelManagementState());
AppGraph.current=new AppGraph(c,repo,ready,owner);
try {
ModelManagementActivity a=new ModelManagementActivity();a.onCreate(null);a.onResume();if(q.task!=null)q.run();
button(a,"选择模型 ZIP").performClick();int zip=a.request;check(a.launched.action.equals(Intent.ACTION_OPEN_DOCUMENT)&&a.launched.category.equals(Intent.CATEGORY_OPENABLE)&&!a.launched.multiple&&(a.launched.flags&1)==1,"ZIP single read/openable Intent");
a.onActivityResult(zip,-1,new Intent().setData(Uri.parse("file:///private.zip")));check(AppGraph.current.zipCalls==0&&!owner.isBusy(),"non-content picker zero IO");
button(a,"选择模型 ZIP").performClick();int next=a.request;check(next!=zip,"new request code");
a.onActivityResult(zip,-1,new Intent().setData(Uri.parse("content://old.zip")));check(AppGraph.current.zipCalls==0,"ZIP stale or mismatched picker cannot import");
a.onActivityResult(next,-1,new Intent().setData(Uri.parse("content://multi.zip")).setClipData(new ClipData(2)));check(AppGraph.current.zipCalls==0,"multiple clip zero IO");
a.launchFailure=new RuntimeException("launch");button(a,"选择模型 ZIP").performClick();a.launchFailure=null;button(a,"选择模型目录").performClick();int directory=a.request;check(a.launched.action.equals(Intent.ACTION_OPEN_DOCUMENT_TREE),"directory compatibility");
a.onActivityResult(directory,0,null);check(!owner.isBusy(),"cancel picker no mutation");
button(a,"选择模型 ZIP").performClick();int deferred=a.request;a.onPause();a.onActivityResult(deferred,-1,new Intent().setData(Uri.parse("content://x.no-suffix")));check(AppGraph.current.zipCalls==0&&!owner.isBusy(),"background defers typed URI");a.onResume();check(AppGraph.current.zipCalls==1&&owner.isBusy(),"foreground consumes ZIP type without suffix guess");
button(a,"选择模型 ZIP").performClick();check(AppGraph.current.zipCalls==1,"busy cannot launch/import");q.run();a.onDestroy();
ModelManagementActivity rotated=new ModelManagementActivity();rotated.onCreate(null);rotated.onResume();if(q.task!=null)q.run();button(rotated,"选择模型 ZIP").performClick();int obsolete=rotated.request;android.os.Bundle saved=new android.os.Bundle();rotated.onSaveInstanceState(saved);rotated.onPause();rotated.changing=true;rotated.onStop();rotated.onDestroy();ModelManagementActivity replacement=new ModelManagementActivity();replacement.onCreate(saved);replacement.onResume();if(q.task!=null)q.run();int before=AppGraph.current.zipCalls;replacement.onActivityResult(obsolete,-1,new Intent().setData(Uri.parse("content://stale")));check(AppGraph.current.zipCalls==before&&!owner.isBusy(),"rotation does not revive picker capability");replacement.onDestroy();
// Actual runtime adapter: one resolver stream, no metadata query, sanitized open/read/close faults.
java.util.List<ModelEntry> seven=new java.util.ArrayList<>();for(int i=0;i<7;i++)seven.add(new ModelEntry("f"+i,0,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));ModelManifest sevenManifest=new ModelManifest(seven);FixedModelPackage policy=new FixedModelPackage(sevenManifest,"fixture","mnn",22);
for(String failure:new String[]{"open","read","close","linkage"}) {
 final String kind=failure;final int[] opens={0};ContentResolver resolver=new ContentResolver(){public InputStream openInputStream(Uri uri)throws IOException {opens[0]++;if(kind.equals("open"))throw new RuntimeException("content://private/account");if(kind.equals("linkage"))throw new LinkageError("private");return new ByteArrayInputStream(new byte[1]){public int read(byte[]b,int o,int n){if(kind.equals("read"))throw new IllegalStateException("private");return super.read(b,o,n);}public void close()throws IOException{super.close();if(kind.equals("close"))throw new IOException("private");}};}};
 SafModelZipSource source=new SafModelZipSource(resolver,Uri.parse("content://model"),()->root.toFile(),()->policy);Throwable caught=null;try{source.openSession(sevenManifest,()->Long.MAX_VALUE,null,ModelRepository.NEVER_CANCEL);}catch(Throwable t){caught=t;}check(caught instanceof IOException&&opens[0]==1&&!caught.toString().contains("private"),"SAF ZIP provider failures sanitized");check(!Files.exists(root.resolve("model-zip-staging/archive.part")),"SAF ZIP fault cleanup");}
ModelPickerTickets tickets=new ModelPickerTickets();ModelPageSession<String> page=new ModelPageSession<>(tickets,c,null);page.foreground(true);int code=page.beginPicker(ModelPickerTickets.Mode.ZIP);check(!page.result(code,ModelPickerTickets.Mode.DIRECTORY,"bad"),"ZIP stale or mismatched picker cannot import");check(page.result(code,ModelPickerTickets.Mode.ZIP,"good"),"typed result");page.destroy();check(!page.consumeTyped(v->true),"destroy stale result");
System.out.println("PASS complete production Activity typed picker host fakes (NOT Android)");
} finally {if(q.task!=null)q.run();check(!owner.isBusy(),"owner exit before cleanup");try(java.util.stream.Stream<Path>s=Files.walk(root)){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
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
