package org.llmasr.minimal.asr

import java.io.IOException

/** Injected synchronous JNI port shared by App and IME via InferenceAdapter.
 * Same byte protocol/settings as before. The four integer phase codes describe
 * real load/response boundaries. JNI isolates callback faults; it never owns
 * request success, which requires Java validation of the returned bytes.
 */
fun interface NativeTranscription {
    @Throws(IOException::class)
    fun invokeWithListener(config: String?, wav: String?, language: String?, cache: String?,
                           listener: InferencePhaseListener?): ByteArray?
}
