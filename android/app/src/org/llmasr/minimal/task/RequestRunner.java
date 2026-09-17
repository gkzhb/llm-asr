package org.llmasr.minimal.task;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;

/** Production request transaction, independent of Android/JSON. All preflight,
 * reports, body and cleanup execute under the coordinator's single owner. */
public final class RequestRunner {
    public interface TaskBody { void run(RequestContext ctx) throws Exception; }
    public interface Preflight { int run() throws IOException; }
    /** Business callbacks run under ownership, except busy/rejected notifications.
     * Errors still unwind cleanup/finalization and release the owner. */
    public interface Lifecycle {
        default void admitted(RequestContext ctx) { }
        default void busy(RequestContext ctx) { }
        default void rejected(RequestContext ctx) { }
        default void beforeBody(RequestContext ctx) throws Exception { }
        default void succeeded(RequestContext ctx) throws Exception { }
        default void cancelled(RequestContext ctx) { }
        default void failed(RequestContext ctx, Throwable failure) { }
        default void cleanupFailed(RequestContext ctx, Throwable failure) { }
    }
    public interface Cleanup { void run(RequestContext ctx) throws IOException; }
    private final TaskCoordinator coordinator;
    private final Preflight preflight;
    private final Lifecycle lifecycle;
    private final Cleanup cleanup;

    public RequestRunner(TaskCoordinator coordinator, Preflight preflight, Lifecycle lifecycle, Cleanup cleanup) {
        if (coordinator == null || preflight == null || lifecycle == null || cleanup == null)
            throw new IllegalArgumentException("request ports required");
        this.coordinator = coordinator;
        this.preflight = preflight;
        this.lifecycle = lifecycle;
        this.cleanup = cleanup;
    }
    public boolean submit(TaskKind kind, TaskBody body) { return submit(kind, body, () -> {}, () -> {}); }
    /** Admission/finalization bind resources such as the pending recording session.
     * Busy never calls either hook. Rejected execution calls finalization once. */
    public boolean submit(TaskKind kind, TaskBody body, Runnable admitted, Runnable finished) {
        if (kind == null || body == null || admitted == null || finished == null)
            throw new IllegalArgumentException("request arguments required");
        RequestContext ctx = new RequestContext(UUID.randomUUID().toString(), kind);
        java.util.concurrent.atomic.AtomicBoolean finalized = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable finishOnce = () -> { if (finalized.compareAndSet(false, true)) finished.run(); };
        try {
            boolean accepted = coordinator.submit(() -> {
                try { execute(ctx, body); }
                finally { finishOnce.run(); }
            }, () -> {
                admitted.run();
                lifecycle.admitted(ctx);
            }, finishOnce);
            if (!accepted) lifecycle.busy(ctx);
            return accepted;
        } catch (RejectedExecutionException rejected) {
            finishOnce.run();
            lifecycle.rejected(ctx);
            return false;
        }
    }
    private void execute(RequestContext ctx, TaskBody body) {
        try {
            ctx.cleanedTemporary = preflight.run();
            lifecycle.beforeBody(ctx);
            body.run(ctx);
            lifecycle.succeeded(ctx);
        } catch (CancellationException cancelled) {
            lifecycle.cancelled(ctx);
        } catch (Exception | LinkageError failure) {
            lifecycle.failed(ctx, failure);
        } finally {
            try { cleanup.run(ctx); }
            catch (Exception | LinkageError failure) { lifecycle.cleanupFailed(ctx, failure); }
        }
    }
}
