package org.llmasr.minimal.asr;

/** Explicit JNI ABI, NOT enum ordinals. Failures/terminals belong to the Java
 * request owner, not callbacks. Keep codes in sync with asr_jni.cpp. */
public enum InferencePhase {
    MODEL_LOAD_STARTED(1),
    MODEL_LOAD_COMPLETED(2),
    INFERENCE_STARTED(3),
    INFERENCE_COMPLETED(4);

    public final int code;
    InferencePhase(int code) { this.code = code; }
}
