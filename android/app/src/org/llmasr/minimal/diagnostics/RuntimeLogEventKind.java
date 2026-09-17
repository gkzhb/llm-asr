package org.llmasr.minimal.diagnostics;

/** Fixed runtime log event names. The set is closed: a new event requires a
 * code review, not a string parameter. Use one of these values; never
 * surface raw exception messages, URIs, paths, transcripts or audio. */
public enum RuntimeLogEventKind {
    APP_STARTUP,
    APP_READY,
    MODEL_VERIFY_STARTED,
    MODEL_VERIFY_COMPLETED,
    MODEL_VERIFY_FAILED,
    MODEL_VERIFY_CANCELLED,
    // Managed-path validation and lazy/cached readiness, not a SHA-only timer.
    MODEL_ACCESS_STARTED,
    MODEL_ACCESS_COMPLETED,
    MODEL_ACCESS_FAILED,
    // Optional instrumentation lost a valid phase sequence; ASR may still succeed.
    LOG_TELEMETRY_FAILED,
    MODEL_LOAD_STARTED,
    MODEL_LOAD_COMPLETED,
    MODEL_LOAD_FAILED,
    INFERENCE_STARTED,
    INFERENCE_COMPLETED,
    INFERENCE_FAILED,
    REQUEST_SUCCESS,
    REQUEST_FAILURE,
    MODEL_IMPORT_STARTED,
    MODEL_IMPORT_COMPLETED,
    MODEL_IMPORT_FAILED,
    MODEL_IMPORT_CANCELLED,
    MODEL_DELETE_STARTED,
    MODEL_DELETE_COMPLETED,
    MODEL_DELETE_FAILED,
    MODEL_DELETE_CANCELLED,
    LOG_PERSISTENCE_FAILED,
    LOG_EXPORT_COMPLETED,
    LOG_EXPORT_FAILED
}
