package org.llmasr.minimal.task;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** One owner for asynchronous requests and synchronous export snapshots.
 * Notifications are invalidations only: observers must read current state, never
 * apply a stale task's busy flag. Admission runs synchronously under the owner,
 * before the worker can execute (notably before onPause can miss a recording).
 */
public final class TaskCoordinator {
    public interface Task { void run() throws Exception; }
    private final AtomicBoolean running = new AtomicBoolean();
    private final Executor executor;
    public interface Listener { void onChange(); }
    private final java.util.concurrent.CopyOnWriteArrayList<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile CountDownLatch idle = new CountDownLatch(0);

    public TaskCoordinator() {
        this(Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "asr-task-worker");
            thread.setDaemon(true);
            return thread;
        }));
    }
    public TaskCoordinator(Executor executor) {
        if (executor == null) throw new IllegalArgumentException("executor required");
        this.executor = executor;
    }
    /** Lifecycle owners must detach; Android listeners retain only weak UI targets. */
    public void addListener(Listener listener) { if (listener != null) listeners.addIfAbsent(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public boolean isBusy() { return running.get(); }
    /** Bounded test/support wait for the task active when this method is called. */
    public void awaitFree() throws InterruptedException {
        if (!idle.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("task did not finish within 5s");
    }
    public boolean submit(Task task) { return submit(task, () -> {}); }
    public boolean submit(Task task, Runnable admitted) { return submit(task, admitted, () -> {}); }
    /** Roll back admitted resources before releasing ownership on submission failure. */
    public boolean submit(Task task, Runnable admitted, Runnable abandoned) {
        if (task == null || admitted == null || abandoned == null) throw new IllegalArgumentException("task/admission/abandon required");
        if (!running.compareAndSet(false, true)) return false;
        CountDownLatch completion = new CountDownLatch(1);
        idle = completion;
        AtomicBoolean released = new AtomicBoolean();
        Runnable finish = () -> { if (released.compareAndSet(false, true)) release(completion); };
        try {
            admitted.run();
            notifyChanged();
            executor.execute(() -> {
                try { task.run(); }
                catch (Exception e) { throw new RuntimeException("Unreported task failure", e); }
                finally { finish.run(); }
            });
            return true;
        } catch (RuntimeException | Error failure) {
            try { abandoned.run(); } finally { finish.run(); }
            throw failure;
        }
    }
    public <T> T withOwnership(Supplier<T> work) {
        if (work == null) throw new IllegalArgumentException("work required");
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("已有任务执行中，请等待。");
        CountDownLatch completion = new CountDownLatch(1);
        idle = completion;
        try { return work.get(); }
        finally { release(completion); }
    }
    private void release(CountDownLatch completion) {
        running.set(false);
        try { notifyChanged(); } finally { completion.countDown(); }
    }
    private void notifyChanged() {
        for (Listener listener : listeners) {
            try { listener.onChange(); }
            catch (VirtualMachineError | ThreadDeath fatal) { throw fatal; }
            catch (Throwable ignored) { /* presentation must not retain ownership or skip other observers */ }
        }
    }
}
