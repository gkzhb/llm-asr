import org.llmasr.minimal.ime.ImeController;
import org.llmasr.minimal.ime.ImeFieldPolicy;
import org.llmasr.minimal.ime.ImeSession;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Calls production IME controller/runner/gate. No Android fake implementation. */
public final class ImeSessionTest {
    static int checks;
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    static final class Queue implements java.util.concurrent.Executor {
        Runnable next;
        public void execute(Runnable r) { if (next != null) throw new AssertionError("overlap"); next = r; }
        void run() { Runnable r = next; next = null; r.run(); }
    }
    static final class Backend implements ImeController.Backend {
        int captures, inference, cleanup; boolean fail, failCleanup;
        RecordingControl control; RequestContext request;
        Runnable duringCapture = () -> {}, duringInference = () -> {}, duringCleanup = () -> {};
        byte[] audio = new byte[]{1,2,3};
        public byte[] capture(RecordingControl c, ImeController.Progress progress) throws IOException {
            captures++; control = c;
            try {
                if (!c.start(() -> {})) throw new CancellationException();
                duringCapture.run(); progress.update(1.0);
                if (fail) throw new IOException("fake capture failure");
                return audio;
            } finally { c.captureReleased(); }
        }
        public String transcribe(RequestContext r, byte[] wav, String lang) throws IOException {
            inference++; request = r;
            check(r.kind == TaskKind.INFERENCE, "IME inference metadata, not maintenance bypass");
            check(wav == audio && lang.equals("Chinese"), "fake capture byte reference/language forwarded");
            r.inputWav = File.createTempFile("ime-test-", ".wav");
            duringInference.run(); return "测试文本";
        }
        public void cleanup(RequestContext r) throws IOException {
            cleanup++; if (request != null) check(request == r, "same request cleaned");
            duringCleanup.run();
            if (failCleanup) throw new IOException("fake cleanup failure before deletion");
            if (r.inputWav != null) check(r.inputWav.delete(), "fake temporary removed");
        }
    }
    static final class Fixture {
        Queue queue = new Queue(); Backend backend = new Backend();
        AtomicInteger notifications = new AtomicInteger();
        TaskCoordinator owner = new TaskCoordinator(queue);
        { owner.addListener(notifications::incrementAndGet); }
        ImeController c = new ImeController(owner, backend, notifications::incrementAndGet);
        ImeSession s = c.begin(1, 0, "editor");
        void complete() { check(c.start(s,"Chinese"), "admitted"); queue.run(); }
    }
    public static void main(String[] args) throws Exception {
        for (int t : new int[]{0,0x1000,0x81,0x91,0xe1,0x12,0x10081})
            check(ImeFieldPolicy.isSensitive(t,0), "sensitive " + t);
        for (int t : new int[]{1,2,3,4,0x21,0x20001})
            check(!ImeFieldPolicy.isSensitive(t,0), "ordinary " + t);
        check(ImeFieldPolicy.isSensitive(1,0x01000000), "private flag");
        Fixture f = new Fixture(); f.complete();
        check(f.backend.inference==1 && f.backend.cleanup==1, "full capture inference cleanup");
        check(f.s.preview().equals("测试文本"), "real pipeline preview");
        AtomicInteger commits = new AtomicInteger();
        check(f.c.commit(f.s,"editor",1,0,text -> { check(text.equals("测试文本"),"exact commit text"); commits.incrementAndGet(); return true; }), "explicit commit");
        check(f.s.preview().isEmpty(), "consume clears preview");
        check(!f.c.commit(f.s,"editor",1,0,text -> { commits.incrementAndGet(); return true; }) && commits.get()==1,"once only");
        f.complete(); check(!f.s.preview().isEmpty(),"second utterance same editor");
        check(!f.c.commit(f.s,"other",1,0,t -> true),"wrong field denied");
        check(!f.c.commit(f.s,"editor",0x91,0,t -> true),"sensitive at commit denied");
        check(!f.c.commit(f.s,"editor",1,0,null),"null connection");
        check(!f.c.commit(f.s,"editor",1,0,t -> true),"null failure consumed");
        f.complete();
        check(!f.c.commit(f.s,"editor",1,0,t -> { throw new IllegalStateException(); }),"throwing connection caught");
        check(f.s.preview().isEmpty(),"exception not retried");
        f.complete(); check(!f.c.commit(f.s,"editor",1,0,t -> false),"rejected connection");
        check(!f.c.commit(f.s,"editor",1,0,t -> true),"rejected not retried");
        f.complete(); ImeSession old = f.s; f.s = f.c.begin(1,0,"editor");
        check(!old.valid() && old.preview().isEmpty(),"same-field restart invalidation");
        check(!f.c.commit(old,"editor",1,0,t -> true),"old commit rejected");
        check(f.c.begin(0x81,0,"secret")==null && !f.s.valid(),"sensitive begin clears previous");
        Fixture queued = new Fixture(); check(queued.c.start(queued.s,"Chinese"),"queue accepted");
        queued.c.invalidate(); queued.queue.run();
        check(queued.backend.captures==0 && queued.backend.inference==0 && queued.backend.cleanup==1,"cancel before worker no capture/infer");
        check(!queued.owner.isBusy(),"queued owner released");
        Fixture handoff = new Fixture(); handoff.backend.duringCapture = handoff.c::invalidate; handoff.complete();
        check(handoff.backend.inference==0 && handoff.backend.control.cancelled(),"handoff cancel prevents native");
        Fixture late = new Fixture();
        late.backend.duringInference = () -> {
            check(late.owner.isBusy(),"owner held throughout native");
            late.c.invalidate();
            ImeSession next = late.c.begin(1,0,"next");
            check(!late.c.start(next,"Chinese"),"cannot start while invalid native still running");
            check(next.preview().isEmpty(),"new session starts empty");
        };
        late.complete(); check(late.s.preview().isEmpty() && !late.s.valid(),"late result discarded");
        check(!late.owner.isBusy(),"native completion frees owner");
        Fixture busy = new Fixture(); busy.owner.submit(() -> {});
        check(!busy.c.start(busy.s,"Chinese") && busy.backend.captures==0,"App owner excludes IME"); busy.queue.run();
        check(busy.c.start(busy.s,"Chinese"),"IME gets owner");
        check(!busy.owner.submit(() -> { throw new AssertionError(); }),"IME excludes App"); busy.queue.run();
        Fixture failed = new Fixture(); failed.backend.fail=true; failed.complete();
        check(failed.backend.cleanup==1 && failed.backend.inference==0 && failed.s.preview().isEmpty(),"capture failure cleanup no result");
        check(failed.s.status().contains("失败") && !failed.owner.isBusy(),"failure status owner release");
        Fixture cleanup = new Fixture(); cleanup.backend.failCleanup=true; cleanup.complete();
        check(cleanup.s.status().contains("清理失败") && !cleanup.owner.isBusy(),"cleanup failure surfaced");
        check(cleanup.backend.request.inputWav.exists(), "failed fake cleanup retains file");
        check(cleanup.backend.request.inputWav.delete(), "test teardown removes retained fake WAV");
        Fixture staleError = new Fixture();
        ImeSession[] fresh = new ImeSession[1];
        staleError.backend.duringCapture = () -> { fresh[0] = staleError.c.begin(1,0,"fresh"); };
        staleError.backend.fail = true; staleError.complete();
        check(fresh[0].preview().isEmpty() && !fresh[0].status().contains("失败"),"stale failure cannot overwrite new session");
        Fixture stopped = new Fixture(); stopped.backend.duringCapture = stopped.c::stop; stopped.complete();
        check(stopped.backend.control.stopped() && stopped.backend.inference==1,"stop hands off to inference");
        // Exercise the exact external-UI command ordering used by the Service.
        // The hide port is fake: actual Android picker/re-show remains device-only.
        for (int mode=0; mode<4; mode++) {
            Fixture picker = new Fixture();
            if (mode==1) picker.complete(); // Existing preview.
            final int scenario = mode;
            Runnable leave = () -> {
                StringBuilder order = new StringBuilder();
                picker.c.leaveForExternalUi(() -> {
                    check(!picker.s.valid() && picker.s.preview().isEmpty(),"invalidate before hide"); order.append("hide");
                }, () -> { check(order.toString().equals("hide"),"hide before external launch"); order.append("/picker"); });
                check(order.toString().equals("hide/picker"),"external command sequence");
            };
            if (mode==2) { picker.backend.duringCapture = leave; picker.complete(); }
            else if (mode==3) { picker.backend.duringInference = leave; picker.complete(); }
            else leave.run();
            check(picker.s.preview().isEmpty(),"external UI clears old preview/late result");
            int captures = picker.backend.captures;
            ImeSession returned = picker.c.begin(1,0,"editor"); // Simulated new input-view callback.
            check(returned.valid() && returned.preview().isEmpty(),"return creates fresh eligible session");
            check(picker.backend.captures==captures,"return never auto records");
            check(picker.c.start(returned,"Chinese"),"manual record after fresh return"); picker.queue.run();
        }
        Fixture failedLaunch = new Fixture(); failedLaunch.complete();
        failedLaunch.c.leaveForExternalUi(() -> {}, () -> { throw new IllegalStateException(); });
        check(!failedLaunch.s.valid() && failedLaunch.s.preview().isEmpty(),"failed external launch keeps old session invalid");
        AtomicInteger notices = new AtomicInteger();
        TaskCoordinator reject = new TaskCoordinator(r -> { throw new RejectedExecutionException(); });
        reject.addListener(notices::incrementAndGet);
        ImeController rejected = new ImeController(reject,new Backend(),notices::incrementAndGet);
        ImeSession rs = rejected.begin(1,0,"r");
        check(!rejected.start(rs,"Chinese") && !reject.isBusy() && !rejected.capturing(),"executor rejection rollback");
        // Deterministic concurrent invalidation while native is blocked.
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        Backend threadedBackend = new Backend();
        TaskCoordinator threadedOwner = new TaskCoordinator(r -> new Thread(r).start());
        ImeController threaded = new ImeController(threadedOwner,threadedBackend,() -> {});
        ImeSession ts = threaded.begin(1,0,"t");
        threadedBackend.duringInference = () -> { entered.countDown(); try { if(!resume.await(5,TimeUnit.SECONDS))throw new AssertionError("timeout"); }catch(InterruptedException e){throw new AssertionError(e);} };
        check(threaded.start(ts,"Chinese"),"threaded admitted"); check(entered.await(5,TimeUnit.SECONDS),"native reached");
        threaded.invalidate(); check(threadedOwner.isBusy(),"invalidate does not release owner"); resume.countDown(); threadedOwner.awaitFree();
        check(ts.preview().isEmpty() && !ts.valid(),"threaded late native dropped");
        // Cleanup is also part of the same exclusive transaction.
        CountDownLatch cleaning = new CountDownLatch(1), releaseCleanup = new CountDownLatch(1);
        Backend cb = new Backend(); TaskCoordinator co = new TaskCoordinator(r -> new Thread(r).start());
        ImeController cc = new ImeController(co,cb,() -> {}); ImeSession cs = cc.begin(1,0,"cleanup");
        cb.duringCleanup = () -> { cleaning.countDown(); try { if(!releaseCleanup.await(5,TimeUnit.SECONDS))throw new AssertionError("cleanup timeout"); } catch(InterruptedException e){throw new AssertionError(e);} };
        check(cc.start(cs,"Chinese"),"cleanup task admitted"); check(cleaning.await(5,TimeUnit.SECONDS),"cleanup reached");
        check(co.isBusy() && !co.submit(() -> {}),"blocked cleanup retains exclusive owner");
        releaseCleanup.countDown(); co.awaitFree(); check(!co.isBusy(),"cleanup completion releases owner");
        System.out.println("PASS " + checks + " IME production controller checks (not Android hardware/InputConnection tests)");
    }
}
