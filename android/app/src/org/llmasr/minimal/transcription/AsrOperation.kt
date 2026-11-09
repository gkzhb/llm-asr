package org.llmasr.minimal.transcription

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.CancellationException
import org.llmasr.minimal.asr.InferenceAdapter
import org.llmasr.minimal.audio.ForegroundRecorder
import org.llmasr.minimal.audio.RecordingControl
import org.llmasr.minimal.audio.WaveInput
import org.llmasr.minimal.diagnostics.RuntimeLogSource
import org.llmasr.minimal.model.ModelRepository
import org.llmasr.minimal.model.ModelAccess
import org.llmasr.minimal.platform.OperationContext
import org.llmasr.minimal.task.RequestContext
import org.llmasr.minimal.task.RequestRunner
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.task.TaskKind

/** Named app commands; worker closures retain only application-level ports. */
class AsrOperation(private val context: OperationContext?, private val coordinator: TaskCoordinator?,
    private val modelRepository: ModelRepository?, private val appState: AppState?,
    private val textExport: TextExportController<Uri>?, private val inference: InferenceAdapter?,
    private val reports: AppReportWriter?, private val modelAccess: ModelAccess) {
    // Preserve Java null forwarding to this SDK parameter, not a general non-null conversion.
    @Suppress("UNCHECKED_CAST") private fun <T> unchanged(value: Any?): T = value as T
    private val runner = RequestRunner(coordinator, { ResultFiles.cleanTemporary(context!!.filesDir()) },
        AppRequestPolicy(reports, StateImpl()), { ctx ->
            try {
                if (ctx.inputWav != null && ctx.inputWav!!.exists() && !ctx.inputWav!!.delete()) throw IOException("无法删除请求临时WAV")
            } finally {
                val binding = requestModel; requestModel = null; modelAccess.unpin(binding)
            }
        })
    private var requestModel: ModelAccess.Binding? = null
    private val releaseDispatch = java.util.concurrent.atomic.AtomicBoolean()
    init { coordinator!!.addListener { dispatchRelease() } }
    private fun dispatchRelease() {
        if (modelAccess.residentSnapshot()?.pendingRelease != true || coordinator!!.isBusy() || !releaseDispatch.compareAndSet(false, true)) return
        try {
            val completed = java.util.concurrent.atomic.AtomicBoolean()
            val accepted = runner.submit(TaskKind.MAINTENANCE, { modelAccess.release(); completed.set(true) }, {}, {
                if (completed.get()) releaseDispatch.set(false)
            })
            if (!accepted) releaseDispatch.set(false)
        } catch (failure: RuntimeException) {
            // Do not spin synchronous rejection/reentrant coordinator listeners.
            // Pending stays closed; a later explicit release permits one retry.
        }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startRelease(): Boolean {
        if (!coordinator!!.isBusy()) releaseDispatch.set(false)
        modelAccess.requestRelease()
        dispatchRelease()
        return true
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startPrepare(): Boolean = runner.submit(TaskKind.MAINTENANCE, {
        val binding = modelAccess.pin()
        try {
            val load = modelAccess.prepare(modelAccess.configPath(File(modelRepository!!.modelDir(), "config.json").absolutePath), modelAccess.cachePath(context!!.cacheDir()!!.absolutePath))
            appState!!.setLastStatus(String.format(Locale.ROOT, "模型已显式准备 · 加载 %.2fs；App / 输入法共用内存实例。", load))
        } finally { modelAccess.unpin(binding) }
    })
    @Volatile private var recording: RecordingControl? = null
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun recording(): RecordingControl? = recording
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun textExport(): TextExportController<Uri>? = textExport
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun cancelRecording() { val r = recording; if (r != null) r.cancel() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startSample(language: String?): Boolean = runner.submit(TaskKind.INFERENCE, { ctx ->
        requestModel = modelAccess.pin()
        modelAccess.requireResidentReady()
        val input = context!!.openAsset("sample.wav")
        var primary: Throwable? = null
        try {
            if (input == null) throw IOException("无法读取内置音频")
            transcribeFromStream(ctx, input, language, "public-zh-example")
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            if (input != null) {
                if (primary == null) input.close()
                else try { input.close() } catch (secondary: Throwable) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    (primary as java.lang.Throwable).addSuppressed(secondary)
                }
            }
        }
    })
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startWave(uri: Uri?, language: String?): Boolean = runner.submit(TaskKind.INFERENCE, { ctx ->
        requestModel = modelAccess.pin()
        modelAccess.requireResidentReady()
        val cr = context!!.contentResolver()
        val input = cr!!.openInputStream(unchanged<Uri>(uri))
        var primary: Throwable? = null
        try {
            if (input == null) throw IOException("无法读取音频")
            transcribeFromStream(ctx, input, language, "user-selected-wav")
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            if (input != null) {
                if (primary == null) input.close()
                else try { input.close() } catch (secondary: Throwable) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    (primary as java.lang.Throwable).addSuppressed(secondary)
                }
            }
        }
    })
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startRecording(language: String?): Boolean {
        val control = RecordingControl()
        return runner.submit(TaskKind.INFERENCE, { ctx ->
            val wav: ByteArray
            try {
                requestModel = modelAccess.pin()
                modelAccess.requireResidentReady()
                wav = ForegroundRecorder.capture(control) { seconds -> appState!!.setLastStatus(String.format(Locale.ROOT,
                    "正在录音 %.1f / 30.0 秒 · 停止后转写，离开界面会取消", seconds)) }
                if (!control.tryCommitInference()) throw CancellationException("已取消录音，音频已丢弃")
            } finally { clearRecording(control) }
            transcribeFromStream(ctx, ByteArrayInputStream(wav), language, "user-microphone")
        }, { recording = control; appState!!.notifyChange() }, { clearRecording(control) })
    }
    private fun clearRecording(control: RecordingControl) { if (recording === control) { recording = null; appState!!.notifyChange() } }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun clearResults(): Boolean = runner.submit(TaskKind.MAINTENANCE, { ctx ->
        appState!!.clearText()
        try {
            val cleared = ResultFiles.clearResults(context!!.filesDir())
            appState.setLastStatus("已清除应用内结果（" + (ctx.cleanedTemporary + cleared) + "个文件），模型未删除。")
        } catch (ioe: IOException) { throw IOException("清除结果失败：" + ioe.message) }
    })
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun editResult(expectedRevision: Long, edited: String?): Boolean = runner.submit(TaskKind.MAINTENANCE, {
        if (appState!!.applyEdit(expectedRevision, edited) < 0) throw IOException("结果已变化，请重新打开编辑。")
        appState.setLastStatus("已更新当前文本；编辑仅保留在内存，请导出保存。原始推理报告未改写。")
    })
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startupOnce(): Boolean = runner.submit(TaskKind.MAINTENANCE, { ctx ->
        val restored = try { modelAccess.restoreFast() } catch (unavailable: IOException) { false }
        appState!!.setLastStatus(ResultFiles.startupStatus(ctx.cleanedTemporary) + if (restored)
            "\n已快速恢复校验证明（未做 SHA）；仍需到模型管理显式准备内存模型。"
            else "\n模型尚未准备，请到模型管理完整校验；已有证明可快速恢复。")
    })
    @Throws(IOException::class)
    private fun transcribeFromStream(ctx: RequestContext, input: InputStream, language: String?, source: String) {
        val wav = File(context!!.filesDir(), "input-" + ctx.requestId + ".wav")
        ctx.inputWav = wav
        val seconds = WaveInput.canonicalize(input, wav)
        runInference(ctx, wav, seconds, language, source)
    }
    @Throws(IOException::class)
    private fun runInference(ctx: RequestContext, wav: File, seconds: Double, language: String?, source: String) {
        appState!!.setLastStatus("使用已准备模型转写中（CPU）……")
        val r = inference!!.transcribe(RuntimeLogSource.APP, ctx.requestId,
            modelAccess.configPath(File(modelRepository!!.modelDir(), "config.json").absolutePath), wav.absolutePath, language, modelAccess.cachePath(context!!.cacheDir()!!.absolutePath))
        reports!!.writeSuccess(ctx, r, wav, seconds, language, source)
        ctx.inferenceReported = true
        appState.setLastText(r.display)
        appState.setLastStatus(String.format(Locale.ROOT,
            "完成 · 加载 %.2fs / 推理 %.2fs / RTF %.3f\n复用已准备模型；未作设备性能验收。", r.loadSeconds, r.inferenceSeconds, r.inferenceSeconds / seconds))
    }
    private inner class StateImpl : AppRequestPolicy.State {
        override fun lastText(): String? = appState!!.lastText()
        override fun setLastText(value: String?) { appState!!.setLastText(value) }
        override fun lastStatus(): String? = appState!!.lastStatus()
        override fun setLastStatus(value: String?) { appState!!.setLastStatus(value) }
    }
}
