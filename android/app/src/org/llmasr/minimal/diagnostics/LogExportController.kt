package org.llmasr.minimal.diagnostics

import java.io.IOException
import java.io.OutputStream
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process export lane. One slot from picker reservation through provider close. */
class LogExportController<T>(backend: Backend<T>?, executor: Executor?) {
    fun interface Backend<T> { @Throws(IOException::class) fun open(target: T?): OutputStream? }
    enum class Phase { IDLE, SELECTING, WRITING, SUCCEEDED, FAILED, CANCELLED, EXPIRED }
    class State private constructor(ticket: Ticket?, @JvmField val phase: Phase, @JvmField val bytes: Long) {
        @JvmField val pageId: Long = ticket?.pageId ?: 0L
        @JvmField val requestCode: Int = ticket?.requestCode ?: 0
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun busy(): Boolean = phase == Phase.SELECTING || phase == Phase.WRITING
        companion object {
            @JvmSynthetic internal fun create(ticket: Ticket?, phase: Phase, bytes: Long): State = State(ticket, phase, bytes)
        }
    }
    class Ticket private constructor(@JvmField val pageId: Long, @JvmField val requestCode: Int, snapshot: List<RuntimeLogEvent?>) {
        @JvmField val snapshot: List<RuntimeLogEvent?> = Collections.unmodifiableList(ArrayList(snapshot))
        companion object {
            @JvmSynthetic internal fun create(pageId: Long, code: Int, snapshot: List<RuntimeLogEvent?>): Ticket = Ticket(pageId, code, snapshot)
        }
    }
    private val backend: Backend<T>
    private val executor: Executor
    private var active: Ticket? = null
    private var nextPage = 0L
    private var nextCode = 8000
    @Volatile private var state = State.create(null, Phase.IDLE, 0)
    constructor(backend: Backend<T>?) : this(backend, executor())
    init {
        if (backend == null || executor == null) throw IllegalArgumentException("export dependencies required")
        this.backend = backend
        this.executor = executor
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun newPage(): Long = ++nextPage
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun state(): State = state
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun begin(pageId: Long, snapshot: List<RuntimeLogEvent?>?): Ticket? {
        if (active != null || nextCode > 65535 || pageId <= 0 || snapshot == null || snapshot.isEmpty() || snapshot.size > RuntimeLogStore.DEFAULT_CAPACITY) return null
        active = Ticket.create(pageId, nextCode++, snapshot)
        state = State.create(active, Phase.SELECTING, 0)
        return active
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun abandon(ticket: Ticket?, expired: Boolean) {
        if (ticket == null || active !== ticket || state.phase != Phase.SELECTING) return
        state = State.create(ticket, if (expired) Phase.EXPIRED else Phase.CANCELLED, 0)
        active = null
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun submit(ticket: Ticket?, target: T?): Boolean {
        synchronized(this) {
            if (ticket == null || active !== ticket || state.phase != Phase.SELECTING || target == null) return false
            state = State.create(ticket, Phase.WRITING, 0)
        }
        try { executor.execute { write(ticket!!, target) } }
        catch (rejected: RuntimeException) { complete(ticket, false, 0) }
        catch (rejected: LinkageError) { complete(ticket, false, 0) }
        catch (fatal: Error) { complete(ticket, false, 0); throw fatal }
        return true
    }
    private fun write(ticket: Ticket, target: T?) {
        var success = false
        var bytes = 0L
        try {
            val out = backend.open(target) ?: throw IOException("open-failed")
            var primary: Throwable? = null
            try {
                bytes = RuntimeLogCodec.writeSnapshot(ticket.snapshot, out)
            } catch (failure: IOException) { primary = failure; throw failure }
            catch (failure: RuntimeException) { primary = failure; throw failure }
            catch (failure: Error) { primary = failure; throw failure }
            finally {
                try { out.close() }
                catch (closing: IOException) { closeFailure(primary, closing) }
                catch (closing: RuntimeException) { closeFailure(primary, closing) }
                catch (closing: Error) { closeFailure(primary, closing) }
            }
            success = true
        } catch (failure: IOException) { /* Fixed state only. */ }
        catch (failure: RuntimeException) { /* No provider message. */ }
        catch (failure: LinkageError) { /* Recoverable linkage failure. */ }
        finally { complete(ticket, success, bytes) }
    }
    private fun closeFailure(primary: Throwable?, closing: Throwable) {
        if (primary == null) throw closing
        if (closing is Error && closing !is LinkageError && !(primary is Error && primary !is LinkageError)) {
            if (primary !== closing) closing.addSuppressed(primary)
            throw closing
        }
        if (primary !== closing) primary.addSuppressed(closing)
    }
    @Synchronized private fun complete(ticket: Ticket?, success: Boolean, bytes: Long) {
        if (active !== ticket) return
        state = State.create(ticket, if (success) Phase.SUCCEEDED else Phase.FAILED, if (success) bytes else 0)
        active = null
    }
    companion object {
        private fun executor(): Executor = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable>(1), { r -> Thread(r, "runtime-log-export").apply { isDaemon = true } })
    }
}
