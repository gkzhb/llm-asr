import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.transcription.AppRequestPolicy;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Direct host tests of the production RequestRunner. Uses fake ports so
 * the lifecycle can be exercised without any Android types. Covers the
 * success / failure / cancel / report-failure / owner / cleanup-id /
 * clear-text contracts of the production AppRequestPolicy. AsrOperation
 * delegates to this runner; the test asserts the runner is the unit of
 * work, not AsrOperation's own submit logic.
 */
public final class RequestRunnerTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    static void await(CountDownLatch latch) {
        try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("latch timeout"); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }

    /** Fake ports: every event captured as a list of strings, so the test
     * can assert the exact sequence the runner drives.
     */
    static final class FakeState implements AppRequestPolicy.State {
        final AtomicReference<String> text = new AtomicReference<>("");
        final AtomicReference<String> status = new AtomicReference<>("");
        @Override public String lastText() { return text.get(); }
        @Override public void setLastText(String v) { text.set(v); }
        @Override public String lastStatus() { return status.get(); }
        @Override public void setLastStatus(String v) { status.set(v); }
    }
    static final class FakeReports implements AppRequestPolicy.Reports {
        final java.util.List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        boolean failPending, failTerminal, failFailure, failCancel;
        @Override public void writePending(RequestContext ctx) throws IOException { log.add("pending:" + ctx.kind); if (failPending) throw new IOException("pending failed"); }
        @Override public void writeTerminal(RequestContext ctx) throws IOException { log.add("terminal:" + ctx.kind); if (failTerminal) throw new IOException("terminal failed"); }
        @Override public void writeFailure(RequestContext ctx, Throwable cause) throws IOException { log.add("failure:" + ctx.kind + ":" + cause.getClass().getSimpleName()); if (failFailure) throw new IOException("failure failed"); }
        @Override public void writeCancel(RequestContext ctx) throws IOException { log.add("cancel:" + ctx.kind); if (failCancel) throw new IOException("cancel failed"); }
    }
    static final class FakeCleanup implements RequestRunner.Cleanup {
        final java.util.List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.List<RequestContext> contexts = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public void run(RequestContext ctx) { contexts.add(ctx); log.add("cleanup:" + ctx.requestId + ":" + ctx.kind); }
    }

    static class Harness {
        final TaskCoordinator coordinator = new TaskCoordinator();
        final FakeState state = new FakeState();
        final FakeReports reports = new FakeReports();
        final FakeCleanup cleanup = new FakeCleanup();
        final AtomicInteger preflightCalls = new AtomicInteger();
        RequestRunner.Preflight preflight = () -> { preflightCalls.incrementAndGet(); return 7; };
        RequestRunner runner = new RequestRunner(coordinator, preflight, new AppRequestPolicy(reports, state), cleanup);

        boolean submit(RequestKind kind, RequestRunner.TaskBody body) {
            return runner.submit(kind == RequestKind.INF ? TaskKind.INFERENCE
                              : kind == RequestKind.MODEL ? TaskKind.MODEL_OPERATION
                              : TaskKind.MAINTENANCE, body);
        }
        /** Wait for the gate to be released without going through the runner
         * (which would inflate the cleanup log). The coordinator's awaitFree
         * blocks on a latch that the running task's finally counts down
         * AFTER releasing the gate, so the wake-up and the post-condition
         * are race-free.
         */
        void awaitIdle() {
            try { coordinator.awaitFree(); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        }
    }
    enum RequestKind { INF, MODEL, MAINT }

    public static void main(String[] args) throws Exception {
        // R1: happy path for INFERENCE. preflight runs once, text cleared, pending written,
        // body runs, terminal written (because body did not mark inferenceReported), cleanup runs.
        {
            Harness h = new Harness();
            CountDownLatch bodyEnter = new CountDownLatch(1), bodyFinish = new CountDownLatch(1);
            boolean accepted = h.submit(RequestKind.INF, ctx -> {
                check(ctx.kind == TaskKind.INFERENCE);
                check(ctx.cleanedTemporary == 7);
                check("".equals(h.state.text.get()));
                bodyEnter.countDown(); await(bodyFinish);
            });
            check(accepted);
            await(bodyEnter);
            check("执行中，请保持应用在前台……".equals(h.state.status.get()));
            check(h.reports.log.contains("pending:INFERENCE"));
            bodyFinish.countDown();
            h.awaitIdle();
            check(h.reports.log.contains("terminal:INFERENCE"));
            check(h.cleanup.log.size() == 1);
            check(h.preflightCalls.get() == 1);
        }

        // R2: MAINTENANCE does not clear text, does not write pending / terminal.
        {
            Harness h = new Harness();
            h.state.setLastText("untouched");
            h.state.setLastStatus("preserved");
            CountDownLatch done = new CountDownLatch(1);
            boolean accepted = h.submit(RequestKind.MAINT, ctx -> done.countDown());
            check(accepted);
            await(done); h.awaitIdle();
            check("untouched".equals(h.state.text.get()));
            check(h.state.status.get().startsWith("执行中"));
            check(h.reports.log.isEmpty());
            check(h.cleanup.log.size() == 1);
        }

        // R3: MODEL_OPERATION clears text and writes a terminal when body did not mark
        // inferenceReported. This is the original "model维护清正文" behavior.
        {
            Harness h = new Harness();
            h.state.setLastText("previous text");
            CountDownLatch done = new CountDownLatch(1);
            h.submit(RequestKind.MODEL, ctx -> done.countDown());
            await(done); h.awaitIdle();
            check("".equals(h.state.text.get()));
            check(h.reports.log.contains("pending:MODEL_OPERATION"));
            check(h.reports.log.contains("terminal:MODEL_OPERATION"));
        }

        // R4: CancellationException writes a cancel report for INFERENCE and sets
        // status to the cancel message.
        {
            Harness h = new Harness();
            h.submit(RequestKind.INF, ctx -> { throw new CancellationException("cancelled by user"); });
            h.awaitIdle();
            check(h.reports.log.contains("cancel:INFERENCE"));
            check("已取消录音，音频已丢弃。".equals(h.state.status.get()));
            check(h.cleanup.log.size() == 1);
        }

        // R5: Exception/LinkageError writes a failure report and sets status to
        // "失败：…". The exact message is propagated.
        {
            Harness h = new Harness();
            h.submit(RequestKind.INF, ctx -> { throw new IllegalStateException("boom"); });
            h.awaitIdle();
            check(h.reports.log.stream().anyMatch(s -> s.startsWith("failure:INFERENCE:IllegalStateException")));
            check(h.state.status.get().startsWith("失败："));
            check(h.cleanup.log.size() == 1);
        }

        // R6: Report write failure is surfaced but does not block the cleanup.
        {
            Harness h = new Harness();
            h.reports.failTerminal = true;
            CountDownLatch done = new CountDownLatch(1);
            h.submit(RequestKind.MODEL, ctx -> done.countDown());
            await(done); h.awaitIdle();
            check(h.state.status.get().startsWith("失败："));
            check(h.reports.log.stream().anyMatch(v -> v.startsWith("failure:")));
            check(h.cleanup.log.size() == 1);
        }

        // R7: Failure persists a "persistence" notice if writeFailure itself throws.
        {
            Harness h = new Harness();
            h.reports.failFailure = true;
            h.submit(RequestKind.INF, ctx -> { throw new RuntimeException("primary"); });
            h.awaitIdle();
            check(h.state.status.get().startsWith("失败且结果无法持久化"));
            check(h.cleanup.log.size() == 1);
        }

        // R8: owner is rejected without disturbing the running request. Duplicate
        // submit returns false and the running request runs to completion.
        {
            Harness h = new Harness();
            CountDownLatch r8Enter = new CountDownLatch(1), r8Finish = new CountDownLatch(1);
            boolean first = h.submit(RequestKind.MAINT, ctx -> { r8Enter.countDown(); await(r8Finish); });
            check(first);
            await(r8Enter);
            boolean second = h.submit(RequestKind.MAINT, ctx -> {});
            check(!second);
            check(h.coordinator.isBusy());
            r8Finish.countDown(); h.awaitIdle();
        }

        // R9: cleanup is called exactly once per request, with the request id and kind.
        {
            Harness h = new Harness();
            CountDownLatch done = new CountDownLatch(1);
            h.submit(RequestKind.INF, ctx -> done.countDown());
            await(done); h.awaitIdle();
            check(h.cleanup.log.size() == 1);
            String entry = h.cleanup.log.get(0);
            check(entry.startsWith("cleanup:") && entry.endsWith(":INFERENCE"));
        }

        // R10: per-request id is unique across sequential requests.
        {
            Harness h = new Harness();
            String[] ids = new String[2];
            CountDownLatch d1 = new CountDownLatch(1), d2 = new CountDownLatch(1);
            h.submit(RequestKind.MAINT, ctx -> { ids[0] = ctx.requestId; d1.countDown(); });
            await(d1); h.awaitIdle();
            h.submit(RequestKind.MAINT, ctx -> { ids[1] = ctx.requestId; d2.countDown(); });
            await(d2); h.awaitIdle();
            check(ids[0] != null && !ids[0].equals(ids[1]));
        }

        // R11: inputWav field on RequestContext is mutable; runner does not touch it
        // (the body / report writer own the file). Cleanup port receives the same
        // context, so the cleanup port can read inputWav.
        {
            Harness h = new Harness();
            final java.io.File fake = new java.io.File(".");
            AtomicReference<RequestContext> bodyContext = new AtomicReference<>();
            h.submit(RequestKind.MAINT, ctx -> { bodyContext.set(ctx); ctx.inputWav = fake; });
            h.awaitIdle();
            String entry = h.cleanup.log.get(0);
            // Cleanup was called with the same context; the body set inputWav
            // before returning, so the cleanup port saw it.
            check(entry.startsWith("cleanup:"));
            check(h.cleanup.contexts.get(0) == bodyContext.get());
            check(h.cleanup.contexts.get(0).inputWav == fake);
        }

        // R12: preflight failure aborts before pending / body / terminal.
        {
            Harness h = new Harness();
            h.runner = new RequestRunner(h.coordinator,
                () -> { throw new IOException("preflight fail"); },
                new AppRequestPolicy(h.reports, h.state), h.cleanup);
            CountDownLatch d = new CountDownLatch(1);
            boolean accepted = h.submit(RequestKind.INF, ctx -> d.countDown());
            check(accepted);
            // Wait for the worker to release the gate.
            awaitRelease(h.coordinator);
            check(h.reports.log.size() == 1 && h.reports.log.get(0).startsWith("failure:"));
            check(h.state.status.get().startsWith("失败："));
            check(h.cleanup.log.size() == 1);
        }

        // R13: pending report failure aborts before body.
        {
            Harness h = new Harness();
            h.reports.failPending = true;
            CountDownLatch d = new CountDownLatch(1);
            h.submit(RequestKind.INF, ctx -> d.countDown());
            awaitRelease(h.coordinator);
            check(h.state.status.get().startsWith("失败："));
            check(h.reports.log.size() == 2 && h.reports.log.get(1).startsWith("failure:"));
            check(h.cleanup.log.size() == 1);
        }

        // Production App policy cancellation persistence notice and already-reported success.
        {
            Harness h = new Harness(); h.reports.failCancel = true;
            h.submit(RequestKind.INF, ctx -> { throw new CancellationException(); }); h.awaitIdle();
            check(h.state.lastStatus().equals("取消完成，但结果无法持久化：cancel failed"));
            check(h.cleanup.log.size() == 1);
        }
        {
            Harness h = new Harness();
            h.submit(RequestKind.INF, ctx -> ctx.inferenceReported = true); h.awaitIdle();
            check(h.reports.log.equals(java.util.Arrays.asList("pending:INFERENCE")));
            check(h.cleanup.log.size() == 1);
        }
        for (boolean cancel : new boolean[]{false, true}) {
            Harness h = new Harness(); h.state.setLastText("preserved");
            h.submit(RequestKind.MAINT, ctx -> { if (cancel) throw new CancellationException(); throw new IOException("maintenance"); });
            h.awaitIdle(); check(h.reports.log.isEmpty()); check(h.state.lastText().equals("preserved"));
            check(h.cleanup.log.size() == 1);
        }

        {
            Harness h = new Harness();
            h.runner = new RequestRunner(h.coordinator, h.preflight, new AppRequestPolicy(h.reports, h.state),
                ctx -> { throw new IOException("cleanup denied"); });
            h.submit(RequestKind.INF, ctx -> { throw new UnsatisfiedLinkError("native missing"); }); h.awaitIdle();
            check(h.state.lastStatus().equals("失败：native missing\n临时文件清理失败：cleanup denied"));
            check(h.reports.log.equals(java.util.Arrays.asList("pending:INFERENCE", "failure:INFERENCE:UnsatisfiedLinkError")));
            check(!h.coordinator.isBusy());
        }
        System.out.println("PASS " + checks + " request runner checks (host only; covers success/failure/cancel/report-failure/owner/cleanup-id/clear-text policy)");
    }

    private static void awaitRelease(TaskCoordinator c) {
        try { c.awaitFree(); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
}
