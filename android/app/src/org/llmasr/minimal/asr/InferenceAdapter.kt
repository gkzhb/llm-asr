package org.llmasr.minimal.asr

import java.io.IOException
import org.llmasr.minimal.diagnostics.RuntimeLogSource
import org.llmasr.minimal.diagnostics.RuntimeLogStore

/** Actual synchronous App/IME orchestration. Caller retains owner and external side effects. */
class InferenceAdapter(private val store: RuntimeLogStore?, private val readiness: Readiness?,
                       private val library: Library?, private val nativeBridge: NativeTranscription?) {
    fun interface Readiness { @Throws(IOException::class) fun requireReady() }
    fun interface Library { fun load() }
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun transcribe(source: RuntimeLogSource?, requestId: String?, config: String?, wav: String?, language: String?, cache: String?): NativeResponse {
        val phases = LoggingInferencePhaseListener(store, source, requestId)
        var failure = "verify-failed"
        try {
            phases.accessStarted()
            readiness!!.requireReady()
            phases.accessCompleted()
            failure = "native-runtime"
            library!!.load()
            val output = nativeBridge!!.invokeWithListener(config, wav, language, cache, phases)
            if (output == null) { failure = "native-null"; throw IOException("Native returned null") }
            failure = "parse-failed"
            val result = parse(output)
            phases.returned()
            return result
        } catch (original: Throwable) {
            phases.failed(failure)
            throw original
        }
    }
    private fun parse(output: ByteArray): NativeResponse {
        try { return NativeResponse.parse(output) }
        catch (malformed: IllegalArgumentException) { throw IOException("Invalid native response protocol") }
    }
}
