package org.llmasr.minimal.ime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.llmasr.minimal.asr.InferenceAdapter
import org.llmasr.minimal.audio.ForegroundRecorder
import org.llmasr.minimal.audio.RecordingControl
import org.llmasr.minimal.audio.WaveInput
import org.llmasr.minimal.diagnostics.RuntimeLogSource
import org.llmasr.minimal.model.ModelRepository
import org.llmasr.minimal.model.ModelAccess
import org.llmasr.minimal.model.ModelReadiness
import org.llmasr.minimal.platform.OperationContext
import org.llmasr.minimal.task.RequestContext

/** Application-context-only adapter; no Service, View, report or text store. */
class ImeBackend(private val context: OperationContext?, private val modelRepository: ModelRepository?,
                 private val inference: InferenceAdapter?, private val modelAccess: ModelAccess) : ImeController.Backend {
    @Throws(IOException::class)
    override fun requireModelReady() { modelAccess.requireResidentReady() }
    @Throws(Exception::class)
    override fun prepare(control: RecordingControl, progress: ImeController.Preparation) {
        val gate = object : ModelAccess.PreparationControl {
            private var reserved = false
            override fun cancelled(): Boolean = control.cancelled()
            // Recording cancellation and proof reservation have one short linearization point.
            // Once reserved, valid durable proof may survive cancel, but microphone intent cannot.
            override fun reserve(ready: ModelReadiness, token: ModelReadiness.VerifyToken): Boolean = synchronized(control) {
                if (reserved || control.cancelled() || !ready.accepts(token)) false
                else { reserved = true; true }
            }
            override fun succeed(ready: ModelReadiness, token: ModelReadiness.VerifyToken): Boolean =
                reserved && ready.commitVerified(token)
        }
        modelAccess.prepareForCapture(gate, File(modelRepository!!.modelDir(), "config.json").absolutePath,
            context!!.cacheDir()!!.absolutePath) { progress.update(it) }
    }
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    override open fun capture(control: RecordingControl?, progress: ImeController.Progress?): ByteArray? {
        val target = progress!!
        return ForegroundRecorder.capture(control) { seconds -> target.update(seconds) }
    }
    @Throws(IOException::class)
    override fun captureWithStart(control: RecordingControl, progress: ImeController.Progress, gate: RecordingControl.StartGate): ByteArray? {
        val generation = modelAccess.readyGeneration() // Worker-only file identity check.
        return ForegroundRecorder.capture(control, { seconds -> progress.update(seconds) }, RecordingControl.StartGate { hardware ->
            gate.start { modelAccess.startCapture(generation) { hardware.run(); true } }
        })
    }
    @Throws(Exception::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override open fun transcribe(request: RequestContext?, wav: ByteArray?, language: String?): String? {
        val targetRequest = request
        val files = context!!.filesDir()
        val inputWav = File(files, "input-" + targetRequest!!.requestId + ".wav")
        targetRequest.inputWav = inputWav
        val input = ByteArrayInputStream(wav)
        var primary: Throwable? = null
        try {
            WaveInput.canonicalize(input, targetRequest.inputWav)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            if (primary == null) input.close()
            else try { input.close() } catch (closeFailure: Throwable) { (primary as java.lang.Throwable).addSuppressed(closeFailure) }
        }
        val targetInference = inference
        val source = RuntimeLogSource.IME
        val requestId = targetRequest.requestId
        val config = File(modelRepository!!.modelDir(), "config.json").absolutePath
        val wavPath = targetRequest.inputWav!!.absolutePath
        val selectedLanguage = language
        val cache = context.cacheDir()!!.absolutePath
        val result = targetInference!!.transcribe(source, requestId, config, wavPath, selectedLanguage, cache)
        return result.display
    }
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    override open fun cleanup(request: RequestContext?) {
        if (request!!.inputWav != null && request.inputWav!!.exists() && !request.inputWav!!.delete())
            throw IOException("无法删除临时录音；下次启动将重试清理")
    }
}
