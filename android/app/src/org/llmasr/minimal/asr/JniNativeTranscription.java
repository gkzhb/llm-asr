package org.llmasr.minimal.asr;

/** Synchronous JNI entry point. Construction does not load the library.
 * InferenceAdapter owns lazy loading and per-request listener creation.
 */
public final class JniNativeTranscription implements NativeTranscription {
    private static native double prepareNative(String config, String cache, InferencePhaseListener listener);
    private static native void releaseNative();
    public double prepare(String config, String cache, InferencePhaseListener listener) { return prepareNative(config, cache, listener); }
    public void release() { releaseNative(); }
    private static native byte[] transcribe(String config, String wav, String language, String cache);
    private static native byte[] transcribeWithListener(String config, String wav, String language, String cache, InferencePhaseListener listener);

    @Override public byte[] invokeWithListener(String config, String wav, String language, String cache,
                                               InferencePhaseListener listener) {
        if (listener == null) return transcribe(config, wav, language, cache);
        return transcribeWithListener(config, wav, language, cache, listener);
    }
}
