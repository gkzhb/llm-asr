package org.llmasr.minimal.task

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/** One owner for requests and synchronous snapshots. Invalidations carry no stale busy flag.
 * Admission runs synchronously under ownership before the worker can execute.
 */
class TaskCoordinator(executor: Executor?) {
    fun interface Task { @Throws(Exception::class) fun run() }
    fun interface Listener { fun onChange() }
    private val running = AtomicBoolean()
    private val executor: Executor
    private val listeners = CopyOnWriteArrayList<Listener>()
    @Volatile private var idle = CountDownLatch(0)

    init {
        if (executor == null) throw IllegalArgumentException("executor required")
        this.executor = executor
    }
    constructor() : this(Executors.newSingleThreadExecutor { r ->
        Thread(r, "asr-task-worker").apply { isDaemon = true }
    })

    fun addListener(listener: Listener?) { if (listener != null) listeners.addIfAbsent(listener) }
    fun removeListener(listener: Listener?) { listeners.remove(listener) }
    fun isBusy(): Boolean = running.get()
    @Throws(InterruptedException::class)
    fun awaitFree() {
        if (!idle.await(5, TimeUnit.SECONDS)) throw IllegalStateException("task did not finish within 5s")
    }
    fun submit(task: Task?): Boolean = submit(task, Runnable {})
    fun submit(task: Task?, admitted: Runnable?): Boolean = submit(task, admitted, Runnable {})
    /** Roll admitted resources back before releasing ownership on submission failure. */
    fun submit(task: Task?, admitted: Runnable?, abandoned: Runnable?): Boolean {
        if (task == null || admitted == null || abandoned == null)
            throw IllegalArgumentException("task/admission/abandon required")
        if (!running.compareAndSet(false, true)) return false
        val completion = CountDownLatch(1)
        idle = completion
        val released = AtomicBoolean()
        val finish = Runnable { if (released.compareAndSet(false, true)) release(completion) }
        try {
            admitted.run()
            notifyChanged()
            executor.execute {
                try { task.run() }
                catch (e: Exception) { throw RuntimeException("Unreported task failure", e) }
                finally { finish.run() }
            }
            return true
        } catch (failure: RuntimeException) {
            try { abandoned.run() } finally { finish.run() }
            throw failure
        } catch (failure: Error) {
            try { abandoned.run() } finally { finish.run() }
            throw failure
        }
    }
    fun <T> withOwnership(work: Supplier<T>?): T {
        if (work == null) throw IllegalArgumentException("work required")
        if (!running.compareAndSet(false, true)) throw IllegalStateException("已有任务执行中，请等待。")
        val completion = CountDownLatch(1)
        idle = completion
        try { return work.get() }
        finally { release(completion) }
    }
    private fun release(completion: CountDownLatch) {
        running.set(false)
        try { notifyChanged() } finally { completion.countDown() }
    }
    private fun notifyChanged() {
        for (listener in listeners) {
            try { listener.onChange() }
            catch (fatal: VirtualMachineError) { throw fatal }
            catch (fatal: ThreadDeath) { throw fatal }
            catch (ignored: Throwable) { /* presentation must not retain ownership or skip observers */ }
        }
    }
}
