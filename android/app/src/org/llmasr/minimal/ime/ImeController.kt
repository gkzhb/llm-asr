package org.llmasr.minimal.ime

import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import org.llmasr.minimal.audio.RecordingControl
import org.llmasr.minimal.task.RequestContext
import org.llmasr.minimal.task.RequestRunner
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.task.TaskKind

/** Private voice IME transaction. Worker results retain editor and request identity. */
class ImeController(private val coordinator: TaskCoordinator?, private val backend: Backend?, private val changed: Runnable?,
    private val history: ImeHistoryStore?) {
    /** Keep the original Kotlin trailing-lambda and Java three-argument source contract. */
    constructor(coordinator: TaskCoordinator?, backend: Backend?, changed: Runnable?) : this(coordinator, backend, changed, null)
    interface Backend {
        @Throws(IOException::class) fun requireModelReady()
        @Throws(Exception::class) fun qualify(control: RecordingControl, progress: Preparation) { requireModelReady() }
        @Throws(Exception::class) fun prepare(control: RecordingControl, progress: Preparation) {}
        @Throws(IOException::class) fun capture(control: RecordingControl?, progress: Progress?): ByteArray?
        @Throws(IOException::class) fun captureWithStart(control: RecordingControl, progress: Progress, gate: RecordingControl.StartGate): ByteArray? = capture(control, progress)
        @Throws(Exception::class) fun transcribe(request: RequestContext?, wav: ByteArray?, language: String?): String?
        @Throws(IOException::class) fun cleanup(request: RequestContext?)
    }
    fun interface Progress { fun update(seconds: Double) }
    fun interface Preparation { fun update(stage: String) }
    fun interface CaptureIntent {
        fun allowed(): Boolean
        fun start(hardwareStart: Runnable): Boolean { if (!allowed()) return false; hardwareStart.run(); return true }
    }
    enum class Phase { IDLE, PREPARING, CAPTURING, TRANSCRIBING }
    private var phase = Phase.IDLE
    enum class UiState { IDLE, PREPARING, RECORDING, TRANSCRIBING, CANCELLING, ERROR, OTHER_BUSY, UNAVAILABLE }
    data class Presentation(val state: UiState, val busy: Boolean, val ownsTask: Boolean,
                            val hasPreview: Boolean, val detail: String, val discarded: Boolean)
    private var failed = false
    private var taskSession: ImeSession? = null
    /** One typed snapshot; never infer a phase or failure from translated status text. */
    @Synchronized fun presentation(): Presentation {
        val owned = recording != null
        val busy = owned || coordinator!!.isBusy()
        val active = current?.takeIf { it.valid }
        val state = when {
            owned && recording!!.cancelled() -> UiState.CANCELLING
            owned && phase == Phase.PREPARING -> UiState.PREPARING
            owned && phase == Phase.CAPTURING && recording!!.released() -> UiState.PREPARING
            owned && phase == Phase.CAPTURING -> UiState.RECORDING
            owned && phase == Phase.TRANSCRIBING -> UiState.TRANSCRIBING
            busy -> UiState.OTHER_BUSY
            active == null -> UiState.UNAVAILABLE
            failed -> UiState.ERROR
            else -> UiState.IDLE
        }
        return Presentation(state, busy, owned, active != null && active.preview.isNotEmpty(),
            if (!owned || active === taskSession) active?.status ?: "" else "",
            owned && active !== taskSession)
    }
    fun interface Commit { fun write(text: String?): Boolean }
    private var current: ImeSession? = null
    private var recording: RecordingControl? = null
    private var counter = 0L
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun begin(type: Int, options: Int, key: String?): ImeSession? {
        invalidate()
        if (ImeFieldPolicy.isSensitive(type, options)) return null
        val session = ImeSession(++counter, key)
        current = session
        return session
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun invalidate() {
        val session = current
        if (session != null) { session.valid = false; session.preview = ""; session.status = "" }
        current = null
        failed = false
        recording?.cancel()
        notifyChanged()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun leaveForExternalUi(hide: Runnable?, launch: Runnable?) {
        invalidate()
        try { hide!!.run() } catch (ignored: RuntimeException) { /* Best-effort hide must not suppress the explicit action. */ }
        try { launch!!.run() } catch (ignored: RuntimeException) { /* Keep the old session invalid. */ }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun busy(): Boolean = coordinator!!.isBusy()
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun ownsTask(): Boolean = recording != null
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun capturing(): Boolean = phase == Phase.CAPTURING && recording != null && !recording!!.stopped() && !recording!!.released() && !recording!!.committed() && !recording!!.cancelled()
    @Synchronized fun phase(): Phase = phase
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun stop() { if (capturing()) recording?.stop() }
    private fun live(session: ImeSession, revision: Long): Boolean = current === session && session.valid && session.revision == revision
    private fun notifyChanged() { try { changed!!.run() } catch (ignored: RuntimeException) { /* Preserve optional observer policy. */ } }
    @Synchronized private fun status(session: ImeSession, rev: Long, text: String) {
        if (live(session, rev)) { session.status = text; notifyChanged() }
    }
    @Synchronized private fun failure(session: ImeSession, rev: Long, text: String) {
        if (live(session, rev)) { failed = true; status(session, rev, text) }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun start(session: ImeSession?, language: String?): Boolean = start(session, language, CaptureIntent { true })
    fun start(session: ImeSession?, language: String?, intent: CaptureIntent): Boolean {
        if (session == null || (language != "Chinese" && language != "English" && language != "auto")) return false
        val rev = synchronized(this) {
            if (session !== current || !session.valid || coordinator!!.isBusy()) return false
            session.revision + 1
        }
        val control = RecordingControl()
        val policy = object : RequestRunner.Lifecycle {
            override fun admitted(ctx: RequestContext) { status(session, rev, "正在准备模型，可丢弃取消；尚未录音……") }
            override fun rejected(ctx: RequestContext) { failure(session, rev, "任务执行器已关闭，无法启动新任务。") }
            override fun cancelled(ctx: RequestContext) { status(session, rev, "已取消录音，音频已丢弃。") }
            override fun failed(ctx: RequestContext, failure: Throwable) { failure(session, rev, "失败：" + failure.message + "；模型缺失或损坏请返回首页→模型。") }
            override fun cleanupFailed(ctx: RequestContext, failure: Throwable) {
                synchronized(this@ImeController) { failure(session, rev, session.status + "\n临时文件清理失败：" + failure.message) }
            }
        }
        val cleanupBackend = backend!! // Java bound method reference dereferences before runner construction.
        val runner = RequestRunner(coordinator, { 0 }, policy, { request -> cleanupBackend.cleanup(request) })
        return runner.submit(TaskKind.INFERENCE, { ctx -> runRequest(ctx, session, rev, control, language, intent) }, {
            synchronized(this@ImeController) {
                if (session !== current || !session.valid || session.revision + 1 != rev) control.cancel()
                session.revision = rev
                failed = false
                taskSession = session
                session.preview = ""
                session.consumed = false
                recording = control
                phase = Phase.PREPARING
            }
        }, {
            synchronized(this@ImeController) { if (recording === control) { recording = null; taskSession = null; phase = Phase.IDLE } }
            notifyChanged()
        })
    }
    private fun runRequest(ctx: RequestContext, session: ImeSession, rev: Long, control: RecordingControl, language: String?, intent: CaptureIntent) {
        if (control.cancelled()) throw CancellationException()
        backend!!.qualify(control) { stage -> status(session, rev, stage) }
        if (!intent.allowed()) control.cancel()
        synchronized(this) {
            if (!live(session, rev) || control.cancelled()) throw CancellationException()
            phase = Phase.CAPTURING
        }
        status(session, rev, "正在录音，停止后转写……")
        val wav = captureAndPrepare(session, rev, control, intent)
        if (!intent.allowed()) control.cancel()
        synchronized(this) { if (!live(session, rev)) control.cancel() }
        if (!control.tryCommitInference()) throw CancellationException()
        synchronized(this) { phase = Phase.TRANSCRIBING }
        status(session, rev, "正在转写，请稍候……")
        val text = backend.transcribe(ctx, wav, language)
        val publishAllowed = intent.allowed()
        synchronized(this) {
            if (publishAllowed && live(session, rev)) {
                session.preview = text ?: ""
                if (session.preview.isNotEmpty()) history?.append(session.preview)
                session.status = if (session.preview.isEmpty()) "未识别到文本，请重新录音。" else "请检查预览，点击确认输入。"
                notifyChanged()
            }
        }
    }
    private fun captureAndPrepare(session: ImeSession, rev: Long, control: RecordingControl, intent: CaptureIntent): ByteArray? {
        val capture = ImeCapture(control) { started ->
            backend!!.captureWithStart(control, Progress { seconds -> status(session, rev, String.format(Locale.ROOT, "录音 %.1f / 30 秒", seconds)) },
                RecordingControl.StartGate { hardware -> intent.start {
                    if (!control.start { started.start(hardware) }) throw CancellationException()
                } })
        }
        capture.start()
        var wav: ByteArray? = null
        var primary: Throwable? = null
        try {
            capture.awaitStart()
            if (control.cancelled()) { capture.finish(); throw CancellationException() }
            backend!!.prepare(control) { stage -> status(session, rev, stage) } // Backend validates resident/generation before returning.
        } catch (caught: Throwable) { primary = caught; control.cancel(); throw caught }
        finally {
            try { wav = capture.finish() }
            catch (caught: Throwable) { if (primary == null) throw caught else if (primary !== caught) primary.addSuppressed(caught) }
        }
        return wav
    }
    /** A selected record is only a preview; no host write and no history append. */
    @Synchronized fun restoreHistory(session: ImeSession?, revision: Long, record: ImeHistory): Boolean {
        if (session == null || !live(session, revision) || coordinator!!.isBusy()) return false
        session.revision++
        session.preview = record.originalText
        session.consumed = false
        session.status = "历史已恢复到预览，请检查后确认输入。"
        failed = false
        notifyChanged()
        return true
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun commit(session: ImeSession?, key: String?, type: Int, options: Int, connection: Commit?): Boolean {
        if (session == null || current !== session || !session.valid || session.consumed || session.preview.isEmpty() ||
            coordinator!!.isBusy() || !session.fieldKey!!.equals(key) || ImeFieldPolicy.isSensitive(type, options)) return false
        val text = session.preview
        session.consumed = true
        session.preview = ""
        var success = false
        try { success = connection != null && connection.write(text) }
        catch (ignored: RuntimeException) { /* Never retry an uncertain editor write. */ }
        failed = !success
        session.status = if (success) "已提交。可以录制下一句。" else "目标未确认接收，未自动重试；请检查输入框。"
        notifyChanged()
        return success
    }
}
