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
class ImeController(private val coordinator: TaskCoordinator?, private val backend: Backend?, private val changed: Runnable?) {
    interface Backend {
        @Throws(IOException::class) fun requireModelReady()
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
    @Synchronized open fun capturing(): Boolean = phase == Phase.CAPTURING && recording != null && !recording!!.committed() && !recording!!.cancelled()
    @Synchronized fun phase(): Phase = phase
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun stop() { if (capturing()) recording?.stop() }
    private fun live(session: ImeSession, revision: Long): Boolean = current === session && session.valid && session.revision == revision
    private fun notifyChanged() { try { changed!!.run() } catch (ignored: RuntimeException) { /* Preserve optional observer policy. */ } }
    @Synchronized private fun status(session: ImeSession, rev: Long, text: String) {
        if (live(session, rev)) { session.status = text; notifyChanged() }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun start(session: ImeSession?, language: String?): Boolean = start(session, language, CaptureIntent { true })
    @Synchronized fun start(session: ImeSession?, language: String?, intent: CaptureIntent): Boolean {
        if (session == null || session !== current || !session.valid || coordinator!!.isBusy()) return false
        if (language != "Chinese" && language != "English" && language != "auto") return false
        val control = RecordingControl()
        val rev = session.revision + 1
        val policy = object : RequestRunner.Lifecycle {
            override fun admitted(ctx: RequestContext) { status(session, rev, "正在准备模型，可丢弃取消；尚未录音……") }
            override fun rejected(ctx: RequestContext) { status(session, rev, "任务执行器已关闭，无法启动新任务。") }
            override fun cancelled(ctx: RequestContext) { status(session, rev, "已取消录音，音频已丢弃。") }
            override fun failed(ctx: RequestContext, failure: Throwable) { status(session, rev, "失败：" + failure.message + "；模型缺失或损坏请打开模型管理导入 / 修复。") }
            override fun cleanupFailed(ctx: RequestContext, failure: Throwable) {
                synchronized(this@ImeController) { status(session, rev, session.status + "\n临时文件清理失败：" + failure.message) }
            }
        }
        val cleanupBackend = backend!! // Java bound method reference dereferences before runner construction.
        val runner = RequestRunner(coordinator, { 0 }, policy, { request -> cleanupBackend.cleanup(request) })
        return runner.submit(TaskKind.INFERENCE, { ctx ->
            if (control.cancelled()) throw CancellationException()
            backend!!.prepare(control) { stage -> status(session, rev, stage) }
            backend.requireModelReady()
            // The Service gate runs on its main thread, outside this monitor. Invalidation
            // may win afterwards; RecordingControl.start still arbitrates actual hardware start.
            if (!intent.allowed()) control.cancel()
            synchronized(this@ImeController) {
                if (!live(session, rev) || control.cancelled()) throw CancellationException()
                phase = Phase.CAPTURING
            }
            status(session, rev, "正在录音，停止后转写……")
            val wav = backend!!.captureWithStart(control, Progress { seconds -> status(session, rev, String.format(Locale.ROOT, "录音 %.1f / 30 秒", seconds)) },
                RecordingControl.StartGate { hardware -> intent.start { if (!control.start(hardware)) throw CancellationException() } })
            if (!control.tryCommitInference()) throw CancellationException()
            synchronized(this@ImeController) { phase = Phase.TRANSCRIBING }
            status(session, rev, "正在转写，请稍候……")
            val text = backend.transcribe(ctx, wav, language)
            synchronized(this@ImeController) {
                if (live(session, rev)) {
                    session.preview = text ?: ""
                    session.status = if (session.preview.isEmpty()) "未识别到文本，请重新录音。" else "请检查预览，点击确认输入。"
                    notifyChanged()
                }
            }
        }, {
            session.revision = rev
            session.preview = ""
            session.consumed = false
            recording = control
            phase = Phase.PREPARING
        }, {
            synchronized(this@ImeController) { if (recording === control) { recording = null; phase = Phase.IDLE } }
            notifyChanged()
        })
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
        session.status = if (success) "已提交。可以录制下一句。" else "目标未确认接收，未自动重试；请检查输入框。"
        notifyChanged()
        return success
    }
}
