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
 * close detaches immediately, drains accepted work, and never waits on IO. */
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
    private boolean scheduled;
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
        synchronized (monitor) {
            if (closed) return false;
            dirty = true;
            if (scheduled) return true;
            scheduled = true;
        }
        try {
            executor.execute(this::drain);
            return true;
        } catch (RuntimeException rejected) {
            synchronized (monitor) {
                scheduled = false;
                if (closed) shutdownOwned();
            }
            droppedSchedules.incrementAndGet();
            fail("queue-full");
            return false;
        }
    }

    private void drain() {
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
                    scheduled = false;
                    if (closed) shutdownOwned();
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
            if (!scheduled) shutdownOwned();
        }
        store.removeSink(sink);
    }
    private void shutdownOwned() {
        if (ownedExecutor) ((ThreadPoolExecutor) executor).shutdown();
    }

}
