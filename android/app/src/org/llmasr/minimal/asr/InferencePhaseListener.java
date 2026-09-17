package org.llmasr.minimal.asr;

/** Per-request synchronous native-worker callback. No Activity, filesystem or
 * UI work here. JNI resolves onPhase(I)V and isolates ANY new Java throwable;
 * it must never clear an exception that was pending before logging began.
 * No native terminal callback: success is decided only after Java parsing. */
public interface InferencePhaseListener {
    void onPhase(int code);
    InferencePhaseListener NONE = code -> { };
}
