#!/usr/bin/env python3
"""Execute production ActivityWindow against controlled platform fakes.

This is an adapter contract test, NOT Android insets/gesture/visual acceptance.
Compiling mutants must fail a behavioral assertion, not merely fail to compile.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE_BASE = ROOT / 'android/app/src/org/llmasr/minimal/platform/ActivityWindow'
SOURCES = [SOURCE_BASE.with_suffix(ext) for ext in ('.java', '.kt') if SOURCE_BASE.with_suffix(ext).is_file()]
if len(SOURCES) != 1:
    raise RuntimeError('missing/duplicate ActivityWindow production source')
SOURCE = SOURCES[0]
STUBS = {
    'android/annotation/TargetApi.java': '''package android.annotation;
public @interface TargetApi { int value(); }
''',
    'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION { public static int SDK_INT; } }
''',
    'android/graphics/Color.java': '''package android.graphics;
public class Color { public static final int TRANSPARENT=0; }
''',
    'android/graphics/Insets.java': '''package android.graphics;
public class Insets { public int left,top,right,bottom;
public Insets(int l,int t,int r,int b) { left=l;top=t;right=r;bottom=b; } }
''',
    'android/view/DisplayCutout.java': '''package android.view;
public class DisplayCutout { public int getSafeInsetLeft(){return 41;}
public int getSafeInsetTop(){return 2;} public int getSafeInsetRight(){return 43;}
public int getSafeInsetBottom(){return 4;} }
''',
    'android/view/WindowInsets.java': '''package android.view;
public class WindowInsets {
public static class Type { public static int systemBars(){return 1;}
public static int displayCutout(){return 2;} public static int ime(){return 4;} }
public int requested; public DisplayCutout cutout=new DisplayCutout();
public android.graphics.Insets getInsets(int types) {
if(android.os.Build.VERSION.SDK_INT<30)throw new AssertionError("API30 insets below boundary"); requested=types;
return new android.graphics.Insets(41,22,43,(types & 4)!=0 ? 300 : 24); }
public DisplayCutout getDisplayCutout(){return cutout;}
public int getSystemWindowInsetLeft(){return 21;}
public int getSystemWindowInsetTop(){return 22;}
public int getSystemWindowInsetRight(){return 23;}
public int getSystemWindowInsetBottom(){return 24;}
}
''',
    'android/view/View.java': '''package android.view;
public class View {
public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE=1, SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN=2,
SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION=4, SYSTEM_UI_FLAG_LIGHT_STATUS_BAR=8,
SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR=16;
public interface OnApplyWindowInsetsListener { WindowInsets onApplyWindowInsets(View v, WindowInsets i); }
public int l,t,r,b,flags,requests; public OnApplyWindowInsetsListener listener;
public int getPaddingLeft(){return l;} public int getPaddingTop(){return t;}
public int getPaddingRight(){return r;} public int getPaddingBottom(){return b;}
public void setPadding(int a,int c,int d,int e){l=a;t=c;r=d;b=e;}
public int getSystemUiVisibility(){return flags;} public void setSystemUiVisibility(int f){flags=f;}
public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener x){listener=x;}
public void requestApplyInsets(){requests++;}
}
''',
    'android/view/WindowManager.java': '''package android.view;
public class WindowManager { public static class LayoutParams {
public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES=1;
public int layoutInDisplayCutoutMode; } }
''',
    'android/view/Window.java': '''package android.view;
public class Window {
public View decor=new View(); public boolean fits=true,contrast;
public int status=-1,nav=-1; public WindowManager.LayoutParams attrs=new WindowManager.LayoutParams();
public void setDecorFitsSystemWindows(boolean x){
if(android.os.Build.VERSION.SDK_INT<30)throw new AssertionError("API30 window below boundary"); fits=x;}
public View getDecorView(){return decor;}
public void setStatusBarColor(int x){status=x;} public void setNavigationBarColor(int x){nav=x;}
public void setNavigationBarContrastEnforced(boolean x){contrast=x;}
public WindowManager.LayoutParams getAttributes(){return attrs;}
public void setAttributes(WindowManager.LayoutParams x){attrs=x;}
}
''',
    'android/window/OnBackInvokedCallback.java': '''package android.window;
public interface OnBackInvokedCallback { void onBackInvoked(); }
''',
    'android/window/OnBackInvokedDispatcher.java': '''package android.window;
public class OnBackInvokedDispatcher {
public static final int PRIORITY_DEFAULT=0;
public OnBackInvokedCallback callback; public int registered,unregistered;
public void registerOnBackInvokedCallback(int p,OnBackInvokedCallback c){
if(p!=PRIORITY_DEFAULT || callback!=null) throw new AssertionError("duplicate/wrong registration");
registered++; callback=c; }
public void unregisterOnBackInvokedCallback(OnBackInvokedCallback c){
if(c!=callback)throw new AssertionError("unregister identity"); unregistered++;callback=null; }
}
''',
    'android/app/Activity.java': '''package android.app;
public class Activity { public android.view.Window window=new android.view.Window();
public android.window.OnBackInvokedDispatcher dispatcher=new android.window.OnBackInvokedDispatcher();
public android.view.Window getWindow(){return window;}
public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher(){
if(android.os.Build.VERSION.SDK_INT<33)throw new AssertionError("API33 back below boundary");return dispatcher;}
}
''',
    'WindowContractTest.java': '''import org.llmasr.minimal.platform.ActivityWindow;
import android.app.Activity; import android.os.Build; import android.view.*;
public class WindowContractTest {
static int checks;
static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
public static void main(String[] args){
for(int sdk:new int[]{29,30,31,32,33,35}){
Build.VERSION.SDK_INT=sdk; Activity a=new Activity(); View v=new View();
v.setPadding(1,2,3,4); a.window.decor.flags=64;
ActivityWindow.configure(a,v); WindowInsets i=new WindowInsets();
check(v.requests==1,"initial request"); check(v.listener.onApplyWindowInsets(v,i)==i,"do not consume");
check(v.l==42 && v.t==24 && v.r==46,"safe per-edge max and base spacing");
check(v.b==(sdk>=30 ? 304 : 28),"IME union and legacy bottom");
check((a.window.decor.flags & 64)!=0,"preserve unrelated flags");
check((a.window.decor.flags & 24)==24,"dark system icons on light page");
check(a.window.status==0 && a.window.nav==0 && a.window.contrast,"colors/contrast");
check(a.window.attrs.layoutInDisplayCutoutMode==1,"cutout edge layout");
if(sdk>=30){check(!a.window.fits,"edge-to-edge modern");check(i.requested==7,"bars/cutout/IME mask");}
else check((a.window.decor.flags & 7)==7,"edge-to-edge legacy");
v.listener.onApplyWindowInsets(v,i); check(v.l==42 && v.b==(sdk>=30?304:28),"no accumulation");
if(sdk==29){ i.cutout=null; v.listener.onApplyWindowInsets(v,i);check(v.l==22 && v.r==26,"no cutout"); }
final int[] calls={0}; ActivityWindow.BackRegistration back=new ActivityWindow.BackRegistration(a,()->calls[0]++);
back.resumed();back.resumed();
if(sdk>=33){check(a.dispatcher.registered==1,"idempotent resume");
a.dispatcher.callback.onBackInvoked();check(calls[0]==1,"delegate exactly once");}
else check(a.dispatcher.registered==0,"no API33 call on older SDK");
back.paused();back.paused();check(a.dispatcher.callback==null,"pause release");
back.resumed();if(sdk>=33)check(a.dispatcher.registered==2 && a.dispatcher.unregistered==1,"resume new registration");
back.paused();
}
System.out.println("PASS ActivityWindow fake platform "+checks+" checks (NOT Android runtime)");
}}
''',
}


def run_contract(source):
    # Only the reviewed owned-session policy may remove these compiler inputs.
    import importlib.util
    spec = importlib.util.spec_from_file_location('_window_owned', ROOT / 'tests/logs_activity_picker_test.py')
    owned = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(owned)
    with owned.Workspace() as temporary:
        root = Path(temporary)
        for relative, content in STUBS.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        production = root / ('org/llmasr/minimal/platform/ActivityWindow' + SOURCE.suffix)
        production.parent.mkdir(parents=True)
        production.write_text(source)
        paths = sorted(root.rglob('*.java')) + sorted(root.rglob('*.kt'))
        compiled = owned.execute(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(root / 'classes'), '',
                                  *map(str, paths)], root, 'compile', 120)
        if compiled.returncode:
            raise AssertionError('Fixture/production compilation failed, NOT red behavior:\n' + compiled.stderr.decode())
        stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
        result = owned.execute(['java', '-cp', str(root / 'classes') + ':' + str(stdlib), 'WindowContractTest'],
                               root, 'run', 15)
        return subprocess.CompletedProcess(result.args, result.returncode, result.stdout.decode(), result.stderr.decode())


class WindowTests(unittest.TestCase):
    def test_production_adapter(self):
        result = run_contract(SOURCE.read_text())
        self.assertEqual(result.returncode, 0, result.stderr)
        print(result.stdout.strip())

    def test_compiling_behavior_mutants(self):
        source = SOURCE.read_text()
        mutants = [
            ('IME omission', ' | WindowInsets.Type.ime()', ''),
            ('padding accumulation', 'left + safe[0]', 'view.getPaddingLeft() + safe[0]'),
            ('duplicate callbacks', 'callback == null', 'true'),
            ('API30 window guard removed', 'if (Build.VERSION.SDK_INT >= 30) Api30.edgeToEdge(window);',
             'if (true) Api30.edgeToEdge(window);'),
            ('API30 insets guard too low', 'if (Build.VERSION.SDK_INT >= 30) return Api30.safeInsets(insets);',
             'if (Build.VERSION.SDK_INT >= 29) return Api30.safeInsets(insets);'),
            ('API33 register guard too low', 'Build.VERSION.SDK_INT >= 33 && callback == null',
             'Build.VERSION.SDK_INT >= 32 && callback == null'),
            ('API33 unregister guard too high', 'Build.VERSION.SDK_INT >= 33 && callback != null',
             'Build.VERSION.SDK_INT >= 34 && callback != null'),
        ]
        if SOURCE.suffix == '.kt':
            mutants = [
                ('IME omission', ' or WindowInsets.Type.ime()', ''),
                ('padding accumulation', 'left + safe[0]', 'view.paddingLeft + safe[0]'),
                ('duplicate callbacks', 'callback == null', 'true'),
                ('API30 window guard removed', 'if (Build.VERSION.SDK_INT >= 30) Api30.edgeToEdge(window)', 'if (true) Api30.edgeToEdge(window)'),
                ('API30 insets guard too low', 'if (Build.VERSION.SDK_INT >= 30) return Api30.safeInsets(insets)', 'if (Build.VERSION.SDK_INT >= 29) return Api30.safeInsets(insets)'),
                ('API33 register guard too low', 'Build.VERSION.SDK_INT >= 33 && callback == null', 'Build.VERSION.SDK_INT >= 32 && callback == null'),
                ('API33 unregister guard too high', 'Build.VERSION.SDK_INT >= 33 && callback != null', 'Build.VERSION.SDK_INT >= 34 && callback != null'),
            ]
        for name, before, after in mutants:
            with self.subTest(name=name):
                self.assertEqual(source.count(before), 1)
                result = run_contract(source.replace(before, after))
                self.assertNotEqual(result.returncode, 0, name)
                self.assertIn('AssertionError', result.stderr)
        print('PASS 7 compiling production ActivityWindow mutants rejected by behavioral assertions')


if __name__ == '__main__':
    unittest.main()
