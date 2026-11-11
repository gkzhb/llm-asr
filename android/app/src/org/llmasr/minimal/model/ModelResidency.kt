package org.llmasr.minimal.model

import java.io.IOException

/** Process-local native ownership. No Android, task, file IO or inference SDK types. */
class ModelResidency(private val native: Native, private val worker: Worker) : ModelVerification.MaintenanceGuard {
    interface Native {
        @Throws(Exception::class) fun prepare(config: String, cache: String): Double
        @Throws(Exception::class) fun release()
    }
    fun interface Worker { fun isWorker(): Boolean }
    enum class State { EMPTY, LOADING, LOADED, ACTIVE, EVICTING }
    data class Snapshot(val state: State, val pendingRelease: Boolean)
    private val monitor = Any()
    private var state = State.EMPTY
    private var generation: String? = null
    private var pending = false
    private var releaseEpoch = 0L
    private var maintenance = 0
    private var active: Any? = null
    fun snapshot(): Snapshot = synchronized(monitor) { Snapshot(state, pending) }
    private fun workerOnly() { if (!worker.isWorker()) throw IOException("模型操作必须在共享工作线程执行") }

    @Throws(Exception::class)
    fun prepare(nextGeneration: String, config: String, cache: String, validate: () -> Unit): Double {
        workerOnly()
        val admission = synchronized(monitor) {
            if (pending || maintenance != 0 || state == State.LOADING || state == State.ACTIVE || state == State.EVICTING)
                throw IOException("模型正在释放或维护")
            val reuse = state == State.LOADED && generation == nextGeneration
            val evictOld = state == State.LOADED && !reuse
            // Capture the external release winner in the SAME admission critical
            // section. Internal generation eviction does not consume its epoch.
            val epoch = releaseEpoch
            if (evictOld) { pending = true; state = State.EVICTING; generation = null }
            else if (!reuse) state = State.LOADING
            Triple(reuse, evictOld, epoch)
        }
        if (admission.first) {
            try {
                validate()
                synchronized(monitor) { if (pending || releaseEpoch != admission.third || maintenance != 0 || state != State.LOADED || generation != nextGeneration) throw IOException("模型准备期间已变化") }
                return 0.0
            } catch (failure: Throwable) {
                requestRelease()
                try { drainRelease() } catch (secondary: Throwable) { suppress(failure, secondary) }
                throw failure
            }
        }
        if (admission.second) {
            drainRelease()
            synchronized(monitor) {
                if (pending || releaseEpoch != admission.third || maintenance != 0 || state != State.EMPTY) throw IOException("模型世代已变化")
                state = State.LOADING
            }
        }
        try {
            val seconds = native.prepare(config, cache)
            if (!seconds.isFinite() || seconds < 0) throw IOException("模型准备返回无效")
            validate()
            synchronized(monitor) {
                if (pending || releaseEpoch != admission.third || maintenance != 0) throw IOException("模型准备期间已请求释放")
                generation = nextGeneration; state = State.LOADED
            }
            return seconds
        } catch (failure: Throwable) {
            synchronized(monitor) { pending = true; state = State.EVICTING; generation = null }
            try { drainRelease() } catch (secondary: Throwable) { suppress(failure, secondary) }
            throw failure
        }
    }
    @Throws(IOException::class)
    fun requireReady(expected: String) {
        synchronized(monitor) {
            if (pending || maintenance != 0 || state != State.LOADED || generation != expected)
                throw IOException("模型未加载，请到模型管理显式准备")
        }
    }
    /** Short capture/start admission, ordered with requestRelease by this same monitor.
     * Caller supplies only the hardware start/control gate: no dispatch wait, file IO or model load. */
    @Throws(IOException::class)
    fun startCapture(expected: String, start: () -> Boolean): Boolean = synchronized(monitor) {
        requireReady(expected)
        start()
    }
    /** File-qualified IME capture may precede native loading, but never a release/maintenance winner. */
    fun captureEpoch(): Long = synchronized(monitor) { releaseEpoch }
    @Throws(IOException::class)
    fun startEarlyCapture(epoch: Long, start: () -> Boolean): Boolean = synchronized(monitor) {
        if (epoch != releaseEpoch || pending || maintenance != 0 || state == State.ACTIVE || state == State.EVICTING)
            throw IOException("模型正在释放或维护")
        start()
    }
    /** Lease remains owned until adapter parsing and native cleanup have really returned. */
    @Throws(IOException::class)
    fun enter(expected: String): Any = synchronized(monitor) {
        requireReady(expected)
        val token = Any(); active = token; state = State.ACTIVE; token
    }
    @Throws(Exception::class)
    fun finish(token: Any, success: Boolean) {
        workerOnly()
        synchronized(monitor) {
            if (active !== token || state != State.ACTIVE) throw IOException("模型请求租约失效")
            active = null
            if (!success) { pending = true; generation = null; state = State.EVICTING }
            else state = State.LOADED
        }
        // Success + pending waits for outer RequestRunner/coordinator completion.
        // Failed parse/bridge must evict before this adapter can return.
        if (!success) drainRelease()
    }
    fun requestRelease() { synchronized(monitor) { releaseEpoch++; pending = true } }
    @Throws(Exception::class)
    fun drainRelease() {
        workerOnly()
        val drainingEpoch: Long
        val unload = synchronized(monitor) {
            if (!pending) return
            drainingEpoch = releaseEpoch
            if (state == State.LOADING || state == State.ACTIVE) throw IOException("模型仍在执行，等待实际收尾")
            if (state == State.EMPTY) { pending = false; generation = null; false }
            else { state = State.EVICTING; generation = null; true }
        }
        if (!unload) return // In particular, never call unloaded JNI from EMPTY.
        native.release() // failure leaves EVICTING + pending, never counterfeit EMPTY.
        synchronized(monitor) { state = State.EMPTY; pending = releaseEpoch != drainingEpoch }
    }
    override fun acquire(): ModelVerification.MaintenanceLease {
        workerOnly()
        synchronized(monitor) {
            if (state == State.LOADING || state == State.ACTIVE) throw IOException("模型仍在执行，禁止修改模型")
            maintenance++
            pending = true
        }
        try { drainRelease() }
        catch (failure: Throwable) { synchronized(monitor) { maintenance-- }; throw failure }
        return object : ModelVerification.MaintenanceLease {
            private var closed = false
            override fun close() { synchronized(monitor) { if (!closed) { closed = true; maintenance-- } } }
        }
    }
    override fun invalidate() { requestRelease() }
    companion object {
        private fun suppress(primary: Throwable, secondary: Throwable) {
            if (primary !== secondary && primary.suppressed.none { it === secondary }) primary.addSuppressed(secondary)
        }
    }
}
