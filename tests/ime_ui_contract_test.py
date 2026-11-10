#!/usr/bin/env python3
"""Real Service + typed controller presentation; host Canvas commands, not device pixels."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import time

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


mixed = load('_ime_ui_mixed', ROOT / 'tests/kotlin_next_migration_test.py')
lifecycle = load('_ime_ui_lifecycle', ROOT / 'tests/mixed_compiler_lifecycle.py')
owned = mixed.owned


def require(value, message):
    if not value:
        raise AssertionError(message)


HARNESS = r'''import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.lang.reflect.*;
import android.view.*;import android.widget.*;import org.llmasr.minimal.*;import org.llmasr.minimal.ime.*;import org.llmasr.minimal.audio.*;import org.llmasr.minimal.task.*;
public class ImeUiContractTest {
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static Object field(Object a,String n)throws Exception{Field f=a.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(a);}
static void set(Object a,String n,Object v)throws Exception{Field f=a.getClass().getDeclaredField(n);f.setAccessible(true);f.set(a,v);}
static void render(AsrImeService s)throws Exception{Method m=AsrImeService.class.getDeclaredMethod("render");m.setAccessible(true);m.invoke(s);}
static Button button(AsrImeService s,String name)throws Exception{Button b=(Button)field(s,name);check(b.isVisibleForHost(),"required IME action remains visible");return b;}
static void reachable(Button b,String message){check(b!=null&&b.isVisibleForHost()&&b.isEnabled(),message);}
static void actions(View v){if(!v.isVisibleForHost())return;if(v instanceof Button){Button b=(Button)v;check(b.getContentDescription()!=null&&!b.getContentDescription().toString().isEmpty()&&b.getContentDescription().equals(b.getTooltipText())&&b.minimumWidth>=48&&b.minimumHeight>=48,"every visible action has semantic description and 48dp target");check(b.icon!=null&&b.icon.isStateful(),"font independent stateful drawable");b.icon.setState(new int[]{android.R.attr.state_enabled});b.icon.setState(new int[]{});android.graphics.Canvas canvas=new android.graphics.Canvas();b.icon.draw(canvas);check(canvas.commands>0,"real icon emits drawing commands");}if(v instanceof LinearLayout)for(View child:((LinearLayout)v).children)actions(child);}
static Button find(View v,String d){if(!v.isVisibleForHost())return null;if(v instanceof Button&&d.equals(String.valueOf(v.getContentDescription())))return(Button)v;if(v instanceof LinearLayout)for(View c:((LinearLayout)v).children){Button b=find(c,d);if(b!=null)return b;}return null;}
static final class Fixture implements AutoCloseable {
final AsrImeService service=new AsrImeService();final List<Runnable> jobs=new ArrayList<>();final TaskCoordinator owner=new TaskCoordinator(jobs::add);final AtomicReference<Throwable> failure=new AtomicReference<>();final CountDownLatch preparing=new CountDownLatch(1),allowPrepare=new CountDownLatch(1),capturing=new CountDownLatch(1),allowCapture=new CountDownLatch(1),nativeStarted=new CountDownLatch(1),allowNative=new CountDownLatch(1);Thread worker;boolean blockPrepare,blockCapture,blockNative,failPrepare;int captures,nativeCalls,cleanups;final ImeController controller;final LinearLayout root;
Fixture()throws Exception{controller=new ImeController(owner,new ImeController.Backend(){
public void requireModelReady(){}public void prepare(RecordingControl c,ImeController.Preparation p)throws Exception{p.update("opaque localized stage");preparing.countDown();if(blockPrepare)await(allowPrepare);if(failPrepare)throw new java.io.IOException("synthetic-safe");}
public byte[] capture(RecordingControl c,ImeController.Progress p){captures++;c.start(()->{});capturing.countDown();try{if(blockCapture)await(allowCapture);c.stop();return new byte[]{1};}finally{c.captureReleased();}}
public String transcribe(RequestContext r,byte[]w,String l){nativeCalls++;nativeStarted.countDown();if(blockNative)await(allowNative);return "public preview";}public void cleanup(RequestContext r){cleanups++;}},()->{});
set(service,"controller",controller);set(service,"alive",true);service.onStartInputView(service.info,false);root=(LinearLayout)service.onCreateInputView();}
void begin(){ImeSession session;try{session=(ImeSession)field(service,"session");}catch(Exception e){throw new AssertionError(e);}check(controller.start(session,"Chinese"),"real controller admission");Runnable job=jobs.remove(0);worker=new Thread(()->{try{job.run();}catch(Throwable t){failure.set(t);}},"bva-ime-owned");worker.start();}
void join()throws Exception{worker.join(5000);check(!worker.isAlive(),"bounded owned worker exit before cleanup");if(failure.get()!=null)throw new AssertionError("worker failure",failure.get());}
public void close()throws Exception{allowPrepare.countDown();allowCapture.countDown();allowNative.countDown();if(worker!=null)join();else for(int n=0;!jobs.isEmpty()&&n<2;n++)jobs.remove(0).run();check(jobs.isEmpty()&&!owner.isBusy(),"real owner release before close");service.onDestroy();android.os.Handler.pending.clear();}}
static void await(CountDownLatch l){try{check(l.await(5,TimeUnit.SECONDS),"bounded test latch");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
static void base()throws Exception{try(Fixture f=new Fixture()){actions(f.root);Button app=find(f.root,"打开App");reachable(app,"three external tools must be visible and enabled");reachable(find(f.root,"返回上一输入法"),"three external tools must be visible and enabled");reachable(find(f.root,"选择输入法"),"three external tools must be visible and enabled");app.performClick();check(f.service.activityCalls==1&&f.service.lastIntent.target==MainActivity.class,"IME app action must target Main only");check((f.service.lastIntent.flags&android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)!=0,"App normalizes existing HOME");}}
static void preparing()throws Exception{try(Fixture f=new Fixture()){f.blockPrepare=true;f.begin();await(f.preparing);render(f.service);check(f.controller.presentation().getState()==ImeController.UiState.PREPARING&&!button(f.service,"stop").isEnabled()&&button(f.service,"cancel").isEnabled(),"preparing has cancel but no stop");button(f.service,"cancel").performClick();render(f.service);check(f.owner.isBusy()&&!button(f.service,"record").isEnabled()&&!button(f.service,"stop").isEnabled()&&!button(f.service,"cancel").isEnabled()&&f.controller.presentation().getState()==ImeController.UiState.CANCELLING,"cancel pending retains busy UI until owned exit");f.allowPrepare.countDown();f.join();check(f.captures==0&&!f.owner.isBusy(),"cancel preparation prevents late capture");}}
static void recording()throws Exception{try(Fixture f=new Fixture()){f.blockCapture=true;f.begin();await(f.capturing);render(f.service);check(button(f.service,"stop").isEnabled()&&!button(f.service,"commit").isEnabled()&&f.controller.presentation().getState()==ImeController.UiState.RECORDING,"recording stop belongs actual capture");button(f.service,"stop").performClick();f.allowCapture.countDown();f.join();render(f.service);check(f.nativeCalls==1&&button(f.service,"commit").isEnabled(),"real preview after native owner release");AtomicInteger writes=new AtomicInteger();f.service.connection=(text,n)->{writes.incrementAndGet();return true;};button(f.service,"commit").performClick();button(f.service,"commit").performClick();check(writes.get()==1,"preview commit at most once");}}
static void nativeDiscard()throws Exception{try(Fixture f=new Fixture()){f.blockNative=true;f.begin();await(f.nativeStarted);render(f.service);check(!button(f.service,"stop").isEnabled()&&String.valueOf(button(f.service,"cancel").getContentDescription()).equals("丢弃本次结果，转写继续"),"discard does not cancel native or release owner");button(f.service,"cancel").performClick();render(f.service);check(f.owner.isBusy()&&f.controller.presentation().getState()==ImeController.UiState.TRANSCRIBING&&!button(f.service,"record").isEnabled(),"discard does not cancel native or release owner");f.allowNative.countDown();f.join();ImeSession session=(ImeSession)field(f.service,"session");check(session!=null&&session.preview().isEmpty(),"late native preview cannot revive discarded session");}}
static void errorAndPolicy()throws Exception{try(Fixture f=new Fixture()){f.failPrepare=true;f.begin();f.join();render(f.service);check(f.controller.presentation().getState()==ImeController.UiState.ERROR&&((TextView)field(f.service,"status")).getText().toString().contains("synthetic-safe"),"typed failure visible without parsing status");reachable(find(f.root,"打开App"),"tools reachable in error");button(f.service,"cancel").performClick();check(f.controller.presentation().getState()==ImeController.UiState.IDLE,"explicit reset clears typed failure");f.service.info.inputType=129;f.service.onStartInputView(f.service.info,false);render(f.service);check(!button(f.service,"record").isEnabled()&&!button(f.service,"commit").isEnabled()&&find(f.root,"选择输入法").isEnabled(),"sensitive field no record or commit tools remain");f.service.info.inputType=1;f.service.onStartInputView(f.service.info,false);f.service.permission=1;render(f.service);check(!button(f.service,"record").isEnabled(),"permission no auto record");}}
public static void main(String[] args)throws Exception{String mode=args.length==0?"all":args[0];switch(mode){case "base":base();break;case "preparing":preparing();break;case "native":nativeDiscard();break;default:base();preparing();recording();nativeDiscard();errorAndPolicy();}System.out.println("PASS real IME UI "+mode);}
}
'''

MUTATIONS = [
    ('UI-1', 'ImeController', 'owned && phase == Phase.PREPARING -> UiState.PREPARING', 'owned && phase == Phase.PREPARING -> UiState.RECORDING', 'preparing', 'preparing has cancel but no stop'),
    ('UI-2', 'ImeController', 'owned && recording!!.cancelled() -> UiState.CANCELLING', 'owned && recording!!.cancelled() -> UiState.IDLE', 'preparing', 'cancel pending retains busy UI until owned exit'),
    ('UI-3', 'AsrImeService', 'semantics(b, description)', 'semantics(b, if (icon == Icon.APP) "" else description)', 'base', 'every visible action has semantic description and 48dp target'),
    ('UI-4', 'AsrImeService', 'Intent(this, MainActivity::class.java)', 'Intent(this, ModelManagementActivity::class.java)', 'base', 'IME app action must target Main only'),
    ('UI-5', 'AsrImeService', 'ImeController.UiState.TRANSCRIBING -> "丢弃本次结果，转写继续"', 'ImeController.UiState.TRANSCRIBING -> "停止推理"', 'native', 'discard does not cancel native or release owner'),
    ('UI-R2-tools', 'AsrImeService', 'val tools = LinearLayout(ui);', 'val tools = LinearLayout(ui); tools.visibility = View.GONE;', 'base', 'three external tools must be visible and enabled'),
    ('UI-R2-action', 'AsrImeService', 'stop = button(row,', 'row.visibility = View.GONE; stop = button(row,', 'preparing', 'required IME action remains visible'),
]


def run_suite(out, mutation_names=None):
    require(not out.exists() and not any(p.is_symlink() for p in [out, *out.parents]), 'fresh non-symlink evidence directory required')
    out.mkdir(parents=True)
    start = time.monotonic()
    sources = mixed.prepare(out)
    fixture = out / 'callers/ImeUiContractTest.java'
    fixture.write_text(HARNESS)
    sources.append(fixture)
    service_fake = out / 'fakes/android/inputmethodservice/InputMethodService.java'
    s = service_fake.read_text().replace('public void startActivity(android.content.Intent intent){activityCalls++;', 'public android.content.Intent lastIntent;public void startActivity(android.content.Intent intent){lastIntent=intent;activityCalls++;')
    service_fake.write_text(s)
    intent = out / 'fakes/android/content/Intent.java'
    s = intent.read_text().replace('public Intent(Context c,Class<?> t){}', 'public Class<?> target;public int flags;public Intent(Context c,Class<?> t){target=t;}').replace('public Intent addFlags(int f){return this;}', 'public Intent addFlags(int f){flags|=f;return this;}')
    intent.write_text(s)
    owned.write_json(out / 'inputs.json', [mixed.identity(p) for p in sources])
    tools = owned.tool_identity(out)
    owned.write_json(out / 'tools.json', tools)
    owned.write_json(out / 'driver-inputs.json', [mixed.identity(p) for p in (Path(__file__), ROOT / 'tests/kotlin_next_migration_test.py', ROOT / 'tests/logs_activity_picker_test.py', ROOT / 'tests/mixed_compiler_lifecycle.py', ROOT / 'tests/fixtures/kotlin-next/NextAppContractTest.java', ROOT / 'scripts/compile-jvm-sources.sh')])
    attempts = []

    def execute(argv, label, seconds):
        require(time.monotonic() - start < 4200, 'IME UI total budget exceeded')
        attempts.append(label)
        owned.write_json(out / 'attempted.json', attempts)
        return lifecycle.run(argv, out, label, seconds)

    def route(label, mode='all', diagnostic=None):
        classes = out / ('classes-' + label)
        result = execute(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '', *map(str, sources)], label + '-compile', 270)
        require(result.returncode == 0, 'compile failure NOT target red ' + label + '\n' + result.stderr.decode(errors='replace'))
        cp = str(classes) + ':' + str(Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar')
        result = execute(['java', '-Xmx256m', '-cp', cp, 'ImeUiContractTest', mode], label + '-jvm', 45)
        expected = ('java.lang.AssertionError: ' + diagnostic).encode() if diagnostic else b'PASS real IME UI'
        require(result.returncode == (1 if diagnostic else 0) and expected in (result.stderr if diagnostic else result.stdout), 'not exact behavioral result ' + label + '\n' + result.stderr.decode(errors='replace'))

    route('green')
    for label, owner, before, after, mode, diagnostic in MUTATIONS:
        if mutation_names is not None and label not in mutation_names:
            continue
        candidates = [p for p in sources if p.stem == owner and p.suffix == '.kt']
        require(len(candidates) == 1, 'unique real mutant owner')
        target = candidates[0]
        original = target.read_bytes()
        require(original.decode().count(before) == 1, 'unique mutant anchor ' + label)
        primary = None
        mutant = original.decode().replace(before, after).encode()
        owned.write_json(out / (label + '-mutation.json'), {'owner': str(target), 'before_sha256': hashlib.sha256(original).hexdigest(), 'mutant_sha256': hashlib.sha256(mutant).hexdigest(), 'before': before, 'after': after, 'mode': mode, 'diagnostic': diagnostic})
        try:
            target.write_bytes(mutant)
            route(label + '-red', mode, diagnostic)
        except BaseException as failure:
            primary = failure
            raise
        finally:
            try:
                lifecycle.guard(out)
                states = [json.loads((out / (a + '.json')).read_text()) for a in attempts]
                require(all(s.get('owned_exited') is True and s.get('root_reaped') is True and type(s.get('exit')) is int and s.get('primary') is None for s in states), 'unknown exit retains input')
                target.write_bytes(original)
            except BaseException as failure:
                if primary is None:
                    raise
                primary.add_note('restoration refused: ' + repr(failure))
        require(target.read_bytes() == original, 'exact original source restoration')
        route(label + '-restored')
    owned.write_json(out / 'result.json', {'elapsed_seconds': time.monotonic() - start, 'attempts': attempts, 'ordinary_or_optimized': __debug__, 'device': 'NOT RUN'})
    print('PASS real IME UI compiling mutants and restorations')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    args = parser.parse_args()
    run_suite(args.evidence_dir.absolute())


if __name__ == '__main__':
    main()
