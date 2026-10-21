package org.llmasr.minimal.transcription

import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Independent process TXT lane. Lock order is controller lock -> ResultState. */
class TextExportController<T>(backend: Backend<T>?, executor: Executor?) {
    fun interface Backend<T> { @Throws(IOException::class) fun open(target: T?): OutputStream? }
    fun interface Listener { fun onChange() }
    enum class Phase { IDLE, SELECTING, QUEUED, WRITING, SUCCEEDED, FAILED, CANCELLED, EXPIRED }
    class State private constructor(ticket: Ticket?, @JvmField val phase: Phase, @JvmField val bytes: Long) {
        @JvmField val pageId: Long = ticket?.pageId ?: 0L
        @JvmField val requestCode: Int = ticket?.requestCode ?: 0
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun busy(): Boolean = phase == Phase.SELECTING || phase == Phase.QUEUED || phase == Phase.WRITING
        companion object {
            @JvmSynthetic internal fun create(ticket: Ticket?, phase: Phase, bytes: Long): State = State(ticket, phase, bytes)
        }
    }
    class Ticket private constructor(@JvmField val pageId: Long, @JvmField val requestCode: Int,
                                     private val source: ResultState, @JvmField val snapshot: ResultState.Snapshot) {
        private var revoked = false
        companion object {
            @JvmSynthetic internal fun create(pageId: Long, code: Int, source: ResultState, snapshot: ResultState.Snapshot): Ticket = Ticket(pageId, code, source, snapshot)
            @JvmSynthetic internal fun source(ticket: Ticket): ResultState = ticket.source
            @JvmSynthetic internal fun revoke(ticket: Ticket) { ticket.revoked = true }
            @JvmSynthetic internal fun revoked(ticket: Ticket): Boolean = ticket.revoked
        }
    }
    private val lock = Any()
    private val backend: Backend<T>
    private val executor: Executor
    private val listeners = CopyOnWriteArrayList<WeakReference<Listener>>()
    private var active: Ticket? = null
    private var nextPage = 0L
    private var nextCode = FIRST_CODE
    private var state = State.create(null, Phase.IDLE, 0)
    constructor(backend: Backend<T>?) : this(backend, executor())
    init {
        if (backend == null || executor == null) throw IllegalArgumentException("export dependencies required")
        this.backend = backend
        this.executor = executor
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun newPage(): Long = synchronized(lock) {
        if (nextPage == Long.MAX_VALUE) throw IllegalStateException("page limit")
        ++nextPage
    }
    private fun expirePicker() {
        val ticket = active
        if (ticket != null && state.phase == Phase.SELECTING && !Ticket.source(ticket).exportValid(ticket.snapshot)) {
            state = State.create(ticket, Phase.EXPIRED, 0)
            active = null
        }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun state(): State = synchronized(lock) { expirePicker(); state }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun owns(ticket: Ticket?): Boolean = synchronized(lock) { expirePicker(); ticket != null && active === ticket }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun begin(pageId: Long, result: ResultState?): Ticket? {
        if (result == null) return null
        val ticket: Ticket
        synchronized(lock) {
            expirePicker()
            if (active != null || pageId <= 0 || pageId > nextPage || nextCode > LAST_CODE) return null
            synchronized(result) {
                val snapshot = result.begin()
                if (snapshot.isEmpty || snapshot.text.length > 100000) return null
                ticket = Ticket.create(pageId, nextCode++, result, snapshot)
                active = ticket
                state = State.create(ticket, Phase.SELECTING, 0)
            }
        }
        notifyChange()
        return ticket
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun revoke(ticket: Ticket?) {
        synchronized(lock) {
            if (ticket == null || active !== ticket || state.phase == Phase.WRITING) return
            Ticket.revoke(ticket)
            if (state.phase == Phase.SELECTING) { state = State.create(ticket, Phase.CANCELLED, 0); active = null }
        }
        notifyChange()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun admit(ticket: Ticket?, target: T?): Boolean {
        if (ticket == null || target == null) return false
        synchronized(lock) {
            expirePicker()
            if (active !== ticket || state.phase != Phase.SELECTING) return false
            val source = Ticket.source(ticket)
            synchronized(source) {
                if (!source.exportValid(ticket.snapshot)) { state = State.create(ticket, Phase.EXPIRED, 0); active = null; return false }
                state = State.create(ticket, Phase.QUEUED, 0)
            }
        }
        try { executor.execute { write(ticket, target) } }
        catch (rejected: RuntimeException) { complete(ticket, Phase.FAILED, 0); return false }
        catch (rejected: LinkageError) { complete(ticket, Phase.FAILED, 0); return false }
        catch (fatal: Error) { complete(ticket, Phase.FAILED, 0); throw fatal }
        notifyChange()
        return true
    }
    private fun write(ticket: Ticket, target: T?) {
        var terminal = Phase.FAILED
        var bytes = 0L
        try {
            synchronized(lock) {
                if (active !== ticket) return
                val source = Ticket.source(ticket)
                synchronized(source) {
                    if (Ticket.revoked(ticket)) { terminal = Phase.CANCELLED; return }
                    if (!source.exportValid(ticket.snapshot)) { terminal = Phase.EXPIRED; return }
                    state = State.create(ticket, Phase.WRITING, 0)
                }
            }
            val out = backend.open(target) ?: throw IOException("open-failed")
            var primary: Throwable? = null
            try {
                ResultFiles.writeText(out, ticket.snapshot.text)
                bytes = ticket.snapshot.text.toByteArray(StandardCharsets.UTF_8).size.toLong()
            } catch (failure: IOException) { primary = failure; throw failure }
            catch (failure: RuntimeException) { primary = failure; throw failure }
            catch (failure: Error) { primary = failure; throw failure }
            finally {
                try { out.close() }
                catch (closing: IOException) { closeFailure(primary, closing) }
                catch (closing: RuntimeException) { closeFailure(primary, closing) }
                catch (closing: Error) { closeFailure(primary, closing) }
            }
            terminal = Phase.SUCCEEDED
        } catch (failure: IOException) { /* No provider diagnostics cross this boundary. */ }
        catch (failure: RuntimeException) { /* Fixed state only. */ }
        catch (failure: LinkageError) { /* Recoverable linkage failure. */ }
        finally { complete(ticket, terminal, bytes) }
    }
    private fun closeFailure(primary: Throwable?, closing: Throwable) {
        if (primary == null) throw closing
        if (closing is Error && closing !is LinkageError && !(primary is Error && primary !is LinkageError)) {
            if (primary !== closing) closing.addSuppressed(primary)
            throw closing
        }
        if (primary !== closing) primary.addSuppressed(closing)
    }
    private fun complete(ticket: Ticket, terminal: Phase, bytes: Long) {
        synchronized(lock) {
            if (active !== ticket) return
            state = State.create(ticket, terminal, if (terminal == Phase.SUCCEEDED) bytes else 0)
            active = null
        }
        notifyChange()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun addListener(listener: Listener?) {
        if (listener == null) return
        removeListener(listener)
        listeners.add(WeakReference(listener))
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun removeListener(listener: Listener?) { listeners.removeIf { ref -> ref.get() == null || ref.get() === listener } }
    private fun notifyChange() {
        for (ref in listeners) {
            val listener = ref.get()
            if (listener == null) listeners.remove(ref)
            else try { listener.onChange() } catch (ignored: Throwable) { /* Observation cannot change ownership. */ }
        }
    }
    companion object {
        const val FIRST_CODE: Int = 5000
        const val LAST_CODE: Int = 65534
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun isRequest(code: Int): Boolean = code >= FIRST_CODE && code <= LAST_CODE
        private fun executor(): Executor = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable>(1), { r -> Thread(r, "text-export-worker").apply { isDaemon = true } })
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun statusText(s: State?): String = when (s!!.phase) {
            Phase.SELECTING -> "TXT：等待选择保存位置。"
            Phase.QUEUED -> "TXT：等待或即将写入；仅尚未开始的写入可被清除结果或关闭页面撤销。"
            Phase.WRITING -> "TXT：正在写入；已开始的外部写入无法撤回。"
            Phase.SUCCEEDED -> "TXT：已导出所选快照（UTF-8，" + s.bytes + " 字节）。"
            Phase.FAILED -> "TXT：导出失败；外部位置可能留下空或部分文件，请自行检查。"
            Phase.CANCELLED -> "TXT：已取消，未写入文本。"
            Phase.EXPIRED -> "TXT：结果已清除，旧导出失效，未写入文本。"
            else -> "TXT：导出独立于转写；云存储提供方可能联网同步。"
        }
    }
}
