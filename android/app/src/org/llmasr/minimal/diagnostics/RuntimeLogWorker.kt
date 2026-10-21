package org.llmasr.minimal.diagnostics

import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** One drain admission and dirty bit. Actual IO owns its token through unwind. */
class RuntimeLogWorker private constructor(
    store: RuntimeLogStore?, persistence: RuntimeLogPersistence?,
    private val failureSink: RuntimeLogPersistence.FailureSink?, executor: Executor?, owned: Boolean
) : AutoCloseable {
    private val store: RuntimeLogStore
    private val persistence: RuntimeLogPersistence
    private val executor: Executor
    private val ownedExecutor: Boolean
    private val sink: RuntimeLogSink
    private val monitor = Any()
    private var closed = false
    private var owner = 0L
    private var nextOwner = 0L
    private var dirty = false
    private var recovered = false
    @Volatile private var lastFailure: String? = null
    @Volatile private var recoveryStatus = RuntimeLogPersistence.Status.MISSING
    private val droppedSchedules = AtomicLong()
    private val successfulSaves = AtomicLong()
    private val failedSaves = AtomicLong()
    constructor(store: RuntimeLogStore?, persistence: RuntimeLogPersistence?, failureSink: RuntimeLogPersistence.FailureSink?) :
        this(store, persistence, failureSink, defaultExecutor(), true)
    constructor(store: RuntimeLogStore?, persistence: RuntimeLogPersistence?, failureSink: RuntimeLogPersistence.FailureSink?, executor: Executor?) :
        this(store, persistence, failureSink, executor, false)
    init {
        if (store == null || persistence == null || executor == null) throw IllegalArgumentException("worker dependencies required")
        this.store = store
        this.persistence = persistence
        this.executor = executor
        ownedExecutor = owned
        sink = RuntimeLogSink { _, _ -> schedulePersist() }
        store.addSink(sink)
        schedulePersist()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun schedulePersist(): Boolean {
        val token: Long
        synchronized(monitor) {
            if (closed) return false
            dirty = true
            if (owner != 0L) return true
            token = ++nextOwner
            owner = token
        }
        try {
            executor.execute { drain(token) }
            return true
        } catch (rejected: RuntimeException) {
            releaseSlot(token)
            droppedSchedules.incrementAndGet()
            fail("queue-full")
            return false
        } catch (fatal: Error) {
            releaseSlot(token)
            throw fatal
        }
    }
    private fun drain(token: Long) {
        try { drainBody(token) }
        catch (unexpected: RuntimeException) { releaseSlot(token) }
        catch (fatal: Error) { releaseSlot(token); throw fatal }
    }
    private fun drainBody(token: Long) {
        if (!recovered) {
            val history = persistence.load()
            val status = persistence.status()
            recoveryStatus = status
            if (status != RuntimeLogPersistence.Status.OK && status != RuntimeLogPersistence.Status.MISSING)
                fail(RuntimeLogPersistence.reason(status))
            try { store.restore(history) }
            catch (invalid: RuntimeException) {
                store.restore(Collections.emptyList())
                recoveryStatus = RuntimeLogPersistence.Status.CORRUPT
                fail("corrupt")
            }
            recovered = true
        }
        while (true) {
            synchronized(monitor) {
                if (!dirty) { releaseLocked(token); return }
                dirty = false
            }
            val result = persistence.save(store.snapshot(), null)
            if (result < 0) {
                failedSaves.incrementAndGet()
                fail(RuntimeLogPersistence.reason(persistence.status()))
            } else {
                successfulSaves.incrementAndGet()
                lastFailure = null
            }
        }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun restoreFromDisk() { schedulePersist() }
    private fun fail(reason: String) {
        synchronized(monitor) {
            if (reason == lastFailure) return
            lastFailure = reason
        }
        if (failureSink != null) {
            try { failureSink.onFailure(reason) }
            catch (ignored: RuntimeException) { /* Fixed status remains observable. */ }
        }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun lastFailure(): String? = lastFailure
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun recoveryStatus(): RuntimeLogPersistence.Status = recoveryStatus
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun droppedSchedules(): Long = droppedSchedules.get()
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun successfulSaves(): Long = successfulSaves.get()
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun failedSaves(): Long = failedSaves.get()
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun close() {
        synchronized(monitor) {
            if (closed) return
            closed = true
            if (owner == 0L) shutdownOwned()
        }
        store.removeSink(sink)
    }
    private fun shutdownOwned() { if (ownedExecutor) (executor as ThreadPoolExecutor).shutdown() }
    private fun releaseSlot(token: Long) { synchronized(monitor) { releaseLocked(token) } }
    private fun releaseLocked(token: Long) {
        if (owner != token) return
        owner = 0
        if (closed) shutdownOwned()
    }
    companion object {
        const val MAX_QUEUED: Int = 1
        private fun defaultExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable>(MAX_QUEUED), { r -> Thread(r, "runtime-log-worker").apply { isDaemon = true } })
    }
}
