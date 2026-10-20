package org.llmasr.minimal.diagnostics;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** One drain task plus one dirty bit, not one task/snapshot per event. First
 * drain recovers history before writing, merging any early appends. Ordinary
 * store publications automatically dirty the worker. Production IO is always
 * on the owned background executor; an injected executor must be asynchronous
 * unless deliberately running synchronously in tests. No main-thread disk API.
 * close detaches immediately, drains accepted work, and never waits on IO.
 *
 * Each admission owns an explicit slot token. Normal handoff, a drain's
 * abnormal exit and an execute that never ran release ONLY the token they were
 * admitted with, so a stale outer frame (direct executors run the task inline)
 * can never clear a successor's ownership. A fatal Error keeps its original
 * identity and propagates after its own release; ordinary RuntimeException is
 * isolated (known persistence/store/failure boundaries already record status).
 * Nothing auto-retries here: one drain attempt per schedule, and a later append
 * retries dirty work. */
public final class RuntimeLogWorker implements AutoCloseable {
    public static final int MAX_QUEUED = 1;

    private final RuntimeLogStore store;
    private final RuntimeLogPersistence persistence;
    private final RuntimeLogPersistence.FailureSink failureSink;
    private final Executor executor;
    private final boolean ownedExecutor;
    private final RuntimeLogSink sink;
    private final Object monitor = new Object();
    private boolean closed;
    /** Identity of the admission currently owning the single drain slot; 0 = free.
     * A schedule/drain/execute frame releases only its own token (see releaseSlot),
     * so one slot is never cleared by a frame that no longer owns it. */
    private long owner;
    private long nextOwner;
    private boolean dirty;
    private boolean recovered;
    /** Fixed status only; UI can poll this even without a failure callback. */
    private volatile String lastFailure;
    private volatile RuntimeLogPersistence.Status recoveryStatus = RuntimeLogPersistence.Status.MISSING;
    private final AtomicLong droppedSchedules = new AtomicLong();
    private final AtomicLong successfulSaves = new AtomicLong();
    private final AtomicLong failedSaves = new AtomicLong();

    public RuntimeLogWorker(RuntimeLogStore store, RuntimeLogPersistence persistence,
                            RuntimeLogPersistence.FailureSink failureSink) {
        this(store, persistence, failureSink, defaultExecutor(), true);
    }
    public RuntimeLogWorker(RuntimeLogStore store, RuntimeLogPersistence persistence,
                            RuntimeLogPersistence.FailureSink failureSink, Executor executor) {
        this(store, persistence, failureSink, executor, false);
    }
    private RuntimeLogWorker(RuntimeLogStore store, RuntimeLogPersistence persistence,
                             RuntimeLogPersistence.FailureSink failureSink, Executor executor, boolean owned) {
        if (store == null || persistence == null || executor == null)
            throw new IllegalArgumentException("worker dependencies required");
        this.store = store;
        this.persistence = persistence;
        this.failureSink = failureSink;
        this.executor = executor;
        this.ownedExecutor = owned;
        sink = (snapshot, dropped) -> schedulePersist();
        store.addSink(sink);
        schedulePersist();
    }
    private static ThreadPoolExecutor defaultExecutor() {
        return new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(MAX_QUEUED), r -> {
                Thread t = new Thread(r, "runtime-log-worker");
                t.setDaemon(true);
                return t;
            });
    }

    public boolean schedulePersist() {
        long token;
        synchronized (monitor) {
            if (closed) return false;
            dirty = true;
            if (owner != 0) return true;
            token = ++nextOwner;
            owner = token;
        }
        try {
            executor.execute(() -> drain(token));
            return true;
        } catch (RuntimeException rejected) {
            releaseSlot(token);
            droppedSchedules.incrementAndGet();
            fail("queue-full");
            return false;
        } catch (Error fatal) {
            // Not a rejection: preserve identity and propagate, but release this
            // admission's own slot so a later append can schedule again. Never
            // reported as queue-full and never counted as a dropped schedule.
            releaseSlot(token);
            throw fatal;
        }
    }

    private void drain(long token) {
        try {
            drainBody(token);
        } catch (RuntimeException unexpected) {
            // Known ordinary faults are already recorded by the persistence/store/
            // failure boundaries (status, -1, fixed labels); this guard only makes
            // an unforeseen non-fatal fault release its own admission, so a later
            // append can retry. It does not claim or invent a status for such a
            // fault, and never swallows or rethrows an Error.
            releaseSlot(token);
        } catch (Error fatal) {
            // Fatal faults preserve identity and propagate unchanged. This frame
            // releases only its own token and touches no state afterwards.
            releaseSlot(token);
            throw fatal;
        }
    }

    private void drainBody(long token) {
        if (!recovered) {
            List<RuntimeLogEvent> history = persistence.load();
            RuntimeLogPersistence.Status status = persistence.status();
            recoveryStatus = status;
            if (status != RuntimeLogPersistence.Status.OK && status != RuntimeLogPersistence.Status.MISSING)
                fail(RuntimeLogPersistence.reason(status));
            try { store.restore(history); }
            catch (RuntimeException invalid) {
                store.restore(Collections.emptyList());
                recoveryStatus = RuntimeLogPersistence.Status.CORRUPT;
                fail("corrupt");
            }
            recovered = true;
        }
        while (true) {
            synchronized (monitor) {
                if (!dirty) {
                    releaseLocked(token);
                    return;
                }
                dirty = false;
            }
            int result = persistence.save(store.snapshot(), null);
            if (result < 0) {
                failedSaves.incrementAndGet();
                fail(RuntimeLogPersistence.reason(persistence.status()));
            } else {
                successfulSaves.incrementAndGet();
                lastFailure = null;
            }
        }
    }

    /** Compatibility name: now only schedules; NEVER synchronously reads disk. */
    public void restoreFromDisk() { schedulePersist(); }

    private void fail(String reason) {
        synchronized (monitor) {
            if (reason.equals(lastFailure)) return;
            lastFailure = reason;
        }
        if (failureSink != null) {
            try { failureSink.onFailure(reason); }
            catch (RuntimeException ignored) { /* fixed observable status still available */ }
        }
    }
    public String lastFailure() { return lastFailure; }
    /** Retains initial recovery outcome even after a successful replacement save. */
    public RuntimeLogPersistence.Status recoveryStatus() { return recoveryStatus; }
    public long droppedSchedules() { return droppedSchedules.get(); }
    public long successfulSaves() { return successfulSaves.get(); }
    public long failedSaves() { return failedSaves.get(); }

    /** Detach and gracefully drain accepted work. Injected executors are NOT owned.
     * No awaitTermination on an Android caller. Abrupt process death may lose dirty events. */
    @Override public void close() {
        synchronized (monitor) {
            if (closed) return;
            closed = true;
            // A scheduled task may not yet have reached executor.execute().
            // Let that drain shut down the pool, rather than rejecting accepted work.
            if (owner == 0) shutdownOwned();
        }
        store.removeSink(sink);
    }
    private void shutdownOwned() {
        if (ownedExecutor) ((ThreadPoolExecutor) executor).shutdown();
    }
    /** Releases only the admission still owned by this frame's token; a stale
     * execute/drain frame (direct executors run the task inline) cannot clear a
     * successor. Runs before fail()/rethrow: the failure callback may append and
     * schedule fresh work, so the token must already be released. */
    private void releaseSlot(long token) {
        synchronized (monitor) {
            releaseLocked(token);
        }
    }
    /** Caller must hold monitor. Normal empty-dirty handoff and abnormal exits. */
    private void releaseLocked(long token) {
        if (owner != token) return;
        owner = 0;
        if (closed) shutdownOwned();
    }

}
