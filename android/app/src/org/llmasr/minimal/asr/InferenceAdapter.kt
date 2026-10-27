package org.llmasr.minimal.asr

import java.io.IOException
import org.llmasr.minimal.diagnostics.RuntimeLogSource
import org.llmasr.minimal.diagnostics.RuntimeLogStore

/** Actual synchronous App/IME orchestration. Caller retains owner and external side effects. */
class InferenceAdapter(private val store: RuntimeLogStore?, private val readiness: Readiness?,
                       private val library: Library?, private val nativeBridge: NativeTranscription?,
                       private val session: Session?) {
    constructor(store: RuntimeLogStore?, readiness: Readiness?, library: Library?, nativeBridge: NativeTranscription?) :
        this(store, readiness, library, nativeBridge, null)
    interface Session {
        @Throws(Exception::class) fun enter(): Any
        @Throws(Exception::class) fun finish(token: Any, success: Boolean)
    }
    fun interface Readiness { @Throws(IOException::class) fun requireReady() }
    fun interface Library { fun load() }
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun transcribe(source: RuntimeLogSource?, requestId: String?, config: String?, wav: String?, language: String?, cache: String?): NativeResponse {
        val phases = LoggingInferencePhaseListener(store, source, requestId)
        var failure = "verify-failed"
        var token: Any? = null
        try {
            phases.accessStarted()
            readiness!!.requireReady()
            phases.accessCompleted()
            failure = "native-runtime"
            if (session == null) library!!.load()
            else { token = session.enter(); phases.residentReady() }
            val output = nativeBridge!!.invokeWithListener(config, wav, language, cache, phases)
            if (output == null) { failure = "native-null"; throw IOException("Native returned null") }
            failure = "parse-failed"
            val result = parse(output)
            val completed = token
            if (completed != null) { session!!.finish(completed, true); token = null }
            phases.returned()
            return result
        } catch (original: Throwable) {
            val owned = token
            if (owned != null) {
                token = null
                try { session!!.finish(owned, false) } catch (secondary: Throwable) {
                    if (secondary !== original && original.suppressed.none { it === secondary }) original.addSuppressed(secondary)
                }
            }
            phases.failed(failure)
            throw original
        }
    }
    private fun parse(output: ByteArray): NativeResponse {
        try { return NativeResponse.parse(output) }
        catch (malformed: IllegalArgumentException) { throw IOException("Invalid native response protocol") }
    }
}
