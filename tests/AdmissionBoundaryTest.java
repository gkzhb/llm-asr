import org.llmasr.minimal.transcription.AppRequestPolicy;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.transcription.ResultState;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;
import org.llmasr.minimal.transcription.TextExportController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.*;

/** Deterministic tests of production admission/runner/owner integration.
 * A queued executor reproduces pause before the worker even starts; no sleeps,
 * microphones, Android stubs or duplicated transaction implementation.
 */
public final class AdmissionBoundaryTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    static final class Queue implements Executor {
        Runnable task;
        public void execute(Runnable task) { if (this.task != null) throw new AssertionError("duplicate"); this.task = task; }
        void drain() { Runnable next = task; task = null; next.run(); }
    }
    static final class State implements AppRequestPolicy.State {
        String text = "old", status = ""; boolean verified;
        public String lastText() { return text; }
        public void setLastText(String text) { this.text = text; }
        public String lastStatus() { return status; }
        public void setLastStatus(String status) { this.status = status; }
    }
    static final class Reports implements AppRequestPolicy.Reports {
        final List<String> states = new ArrayList<>();
        final List<RequestContext> contexts = new ArrayList<>();
        void add(String state, RequestContext ctx) { states.add(state); contexts.add(ctx); }
        public void writePending(RequestContext ctx) { add("pending",ctx); }
        public void writeTerminal(RequestContext ctx) { add("complete",ctx); }
        public void writeFailure(RequestContext ctx,Throwable cause) { add("failed",ctx); }
        public void writeCancel(RequestContext ctx) { add("cancelled",ctx); }
    }
    public static void main(String[] args) throws Exception {
        Queue queue = new Queue();
        List<Boolean> notifications = new ArrayList<>();
        AtomicReference<TaskCoordinator> owner = new AtomicReference<>();
        TaskCoordinator coordinator = new TaskCoordinator(queue);
        coordinator.addListener(() -> notifications.add(owner.get().isBusy()));
        owner.set(coordinator);
        State state = new State(); Reports reports = new Reports();
        AtomicReference<RecordingControl> active = new AtomicReference<>();
        AtomicInteger starts = new AtomicInteger(), inference = new AtomicInteger(), cleans = new AtomicInteger();
        RequestRunner runner = new RequestRunner(coordinator, () -> 7, new AppRequestPolicy(reports, state), ctx -> cleans.incrementAndGet());
        RecordingControl control = new RecordingControl();
        check(runner.submit(TaskKind.INFERENCE, ctx -> {
            try { control.start(starts::incrementAndGet); }
            finally { control.captureReleased(); }
            if (!control.tryCommitInference()) throw new java.util.concurrent.CancellationException();
            inference.incrementAndGet();
        }, () -> active.set(control), () -> active.compareAndSet(control,null)));
        check(active.get() == control && coordinator.isBusy());
        check(state.text.isEmpty()); // admission, not delayed worker preflight
        active.get().cancel(); // onPause before executor execution must latch cancel
        AtomicInteger rejectedHooks = new AtomicInteger();
        check(!runner.submit(TaskKind.MAINTENANCE,ctx -> {},rejectedHooks::incrementAndGet,rejectedHooks::incrementAndGet));
        check(rejectedHooks.get() == 0 && active.get() == control);
        queue.drain();
        check(starts.get() == 0 && inference.get() == 0 && active.get() == null);
        check(reports.states.equals(Arrays.asList("pending","cancelled")) && cleans.get() == 1);
        check(reports.contexts.get(0) == reports.contexts.get(1));
        check(!coordinator.isBusy() && notifications.equals(Arrays.asList(true,false)));

        // No listener is needed to finish a task; completion notification observes
        // owner=false. A queued render reads current busy, not a stale terminal flag.
        check(runner.submit(TaskKind.MAINTENANCE,ctx -> {}));
        check(coordinator.isBusy());
        queue.drain(); check(!coordinator.isBusy());
        // TextExportController is independent of the ASR coordinator; the
        // coordinator's busy state does NOT block export begin. Clear()
        // invalidates pending tickets by bumping the ResultState revision.
        ResultState rs = new ResultState();
        rs.setText("snapshot");
        TextExportController<String> exports = new TextExportController<>(target -> new ByteArrayOutputStream(), Runnable::run);
        TextExportController.Ticket ticket = exports.begin(exports.newPage(), rs);
        check(ticket != null);
        check(exports.state().phase == TextExportController.Phase.SELECTING);
        // The ASR coordinator is independent: a separate task holds it, but
        // exports can still begin (its slot is held by the existing ticket,
        // so the second begin is busy for the EXPORT, not the ASR).
        AtomicInteger reads = new AtomicInteger();
        check(runner.submit(TaskKind.MAINTENANCE,ctx -> {
            // Export busy is independent of ASR busy: the export is busy
            // because the first ticket is still SELECTING.
            check(exports.begin(exports.newPage(), rs) == null);
            reads.incrementAndGet();
        }));
        queue.drain(); check(reads.get() == 1);
        // After revoking, export is no longer busy.
        exports.revoke(ticket);
        check(exports.state().phase == TextExportController.Phase.CANCELLED);
        check(!exports.state().busy());

        // Failure before body must replace stale success with request-specific failure.
        reports.states.clear(); state.text="stale";
        RequestRunner broken = new RequestRunner(coordinator,() -> { throw new IOException("preflight"); },new AppRequestPolicy(reports,state),ctx -> cleans.incrementAndGet());
        check(broken.submit(TaskKind.INFERENCE,ctx -> { throw new AssertionError("body ran"); }));
        check(state.text.isEmpty()); queue.drain();
        check(reports.states.equals(Arrays.asList("failed")) && state.status.contains("preflight"));

        // Rejected worker execution rolls back admitted recording and owner.
        AtomicReference<RecordingControl> rejectedSession = new AtomicReference<>();
        TaskCoordinator rejecting = new TaskCoordinator(command -> { throw new RejectedExecutionException("reject"); });
        RequestRunner rejectRunner = new RequestRunner(rejecting,() -> 0,new AppRequestPolicy(new Reports(),state),ctx -> {});
        check(!rejectRunner.submit(TaskKind.INFERENCE,ctx -> {},() -> rejectedSession.set(control),() -> rejectedSession.set(null)));
        check(rejectedSession.get()==null && !rejecting.isBusy() && state.status.contains("执行器"));
        check(coordinator.withOwnership(() -> "owned").equals("owned") && !coordinator.isBusy());
        // Assertion/fatal errors at each execution boundary always clean and finalize
        // before the release notification; exceptions are not mistaken for success.
        for (int stage = 0; stage < 6; stage++) {
            final int where = stage;
            Queue q = new Queue(); TaskCoordinator c = new TaskCoordinator(q);
            List<String> order = new ArrayList<>();
            Error boom = where % 2 == 0 ? new AssertionError("synthetic") : new InternalError("synthetic");
            AppRequestPolicy.Reports port = new AppRequestPolicy.Reports() {
                public void writePending(RequestContext ctx) { if (where == 1) throw boom; }
                public void writeTerminal(RequestContext ctx) { if (where == 3) throw boom; }
                public void writeFailure(RequestContext ctx, Throwable failure) { if (where == 4) throw boom; }
                public void writeCancel(RequestContext ctx) { throw new AssertionError("unexpected cancel"); }
            };
            RequestRunner r = new RequestRunner(c, () -> { if (where == 0) throw boom; return 0; },
                new AppRequestPolicy(port, new State()), ctx -> {
                    check(c.isBusy()); order.add("cleanup"); if (where == 5) throw boom;
                });
            c.addListener(() -> { if (!c.isBusy()) order.add("release"); });
            check(r.submit(TaskKind.INFERENCE, ctx -> {
                if (where == 2) throw boom;
                if (where == 4) throw new IOException("primary");
            }, () -> {}, () -> { check(c.isBusy()); order.add("finish"); }));
            try { q.drain(); throw new AssertionError("error swallowed"); }
            catch (Error expected) { check(expected == boom); }
            c.awaitFree(); check(!c.isBusy());
            check(order.equals(Arrays.asList("cleanup", "finish", "release")));
        }
        AtomicInteger once = new AtomicInteger();
        RequestRunner admissionFailure = new RequestRunner(coordinator, () -> 0,
            new AppRequestPolicy(reports, state), ctx -> { throw new AssertionError("worker never starts"); });
        try { admissionFailure.submit(TaskKind.INFERENCE, ctx -> {}, () -> { throw new AssertionError("admit"); }, once::incrementAndGet); throw new IllegalStateException("error swallowed"); }
        catch (AssertionError expected) { check(once.get() == 1 && !coordinator.isBusy()); }
        coordinator.awaitFree();
        once.set(0);
        check(!rejectRunner.submit(TaskKind.INFERENCE, ctx -> {}, () -> {}, once::incrementAndGet));
        check(once.get() == 1 && !rejecting.isBusy());
        // A throwing finalizer is still called once, including inline executors.
        for (boolean inline : new boolean[]{false, true}) {
            Queue q = new Queue(); TaskCoordinator c = new TaskCoordinator(inline ? Runnable::run : q);
            AtomicInteger finishes = new AtomicInteger(), cleanups = new AtomicInteger();
            RequestRunner r = new RequestRunner(c, () -> 0, new AppRequestPolicy(new Reports(), new State()),
                ctx -> cleanups.incrementAndGet());
            try {
                r.submit(TaskKind.INFERENCE, ctx -> {}, () -> {}, () -> {
                    check(c.isBusy()); finishes.incrementAndGet(); throw new AssertionError("finalizer");
                });
                if (!inline) q.drain();
                throw new IllegalStateException("finalizer error swallowed");
            } catch (AssertionError expected) { check("finalizer".equals(expected.getMessage())); }
            c.awaitFree(); check(!c.isBusy() && finishes.get() == 1 && cleanups.get() == 1);
        }
        System.out.println("PASS " + checks + " production admission/owner/recording/export boundary checks (host only)");
    }
}
