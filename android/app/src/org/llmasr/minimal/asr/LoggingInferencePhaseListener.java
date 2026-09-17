package org.llmasr.minimal.asr;


import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;

/** One request's telemetry state, independent of inference control flow.
 * Access means managed-boundary validation plus lazy SHA readiness (possibly
 * cached), NOT MNN memory loading and NOT a claim that SHA ran on every call.
 * Invalid/missing callbacks fail telemetry closed, never the returned ASR data.
 * All state moves precede publication, so reentrant/faulty sinks cannot mint a
 * second terminal. Only the invoking thread may deliver native phases.
 */
public final class LoggingInferencePhaseListener implements InferencePhaseListener {
    private enum Stage { ACCESS, WAIT_LOAD, LOADING, LOADED, INFERRING, DONE }
    private final RuntimeLogStore store;
    private final RuntimeLogSource source;
    private final String requestId;
    private final Thread owner = Thread.currentThread();
    private Stage stage = Stage.ACCESS;
    private boolean invalid, closed;
    private Long requestStart, stageStart;

    public LoggingInferencePhaseListener(RuntimeLogStore store, RuntimeLogSource source, String requestId) {
        this.store = store;
        this.source = source;
        this.requestId = requestId;
    }

    // Throwable isolation is deliberately limited to optional instrumentation.
    // Readiness, loading, native invocation and parsing errors are NOT swallowed.
    private Long now() {
        try { return store == null ? null : store.clock().monotonicNanos(); }
        catch (Throwable ignored) { return null; }
    }
    private long elapsed(Long start) {
        Long end = now();
        if (start == null || end == null) return -1;
        long duration = end - start;
        return duration < 0 ? -1 : duration;
    }
    private void emit(RuntimeLogEventKind kind, String detail, long duration) {
        try {
            if (store != null && !store.append(kind, source, requestId, detail, duration)) invalidate();
        } catch (Throwable ignored) {
            // The event may already have been inserted before a sink threw.
            // Do not retry it or allow later success telemetry on this request.
            invalidate();
        }
    }
    synchronized void accessStarted() {
        requestStart = stageStart = now();
        emit(RuntimeLogEventKind.MODEL_ACCESS_STARTED, "ok", -1);
    }
    synchronized void accessCompleted() {
        long duration = elapsed(stageStart);
        stage = Stage.WAIT_LOAD;
        emit(RuntimeLogEventKind.MODEL_ACCESS_COMPLETED, "ok", duration);
    }
    private void invalidate() {
        if (invalid) return;
        invalid = true;
        emit(RuntimeLogEventKind.LOG_TELEMETRY_FAILED, "invalid", -1);
    }
    @Override public synchronized void onPhase(int code) {
        if (closed || invalid) return; // including stale callbacks after return/failure
        if (Thread.currentThread() != owner) { invalidate(); return; }
        RuntimeLogEventKind event;
        long duration = -1;
        if (stage == Stage.WAIT_LOAD && code == InferencePhase.MODEL_LOAD_STARTED.code) {
            stage = Stage.LOADING;
            stageStart = now();
            event = RuntimeLogEventKind.MODEL_LOAD_STARTED;
        } else if (stage == Stage.LOADING && code == InferencePhase.MODEL_LOAD_COMPLETED.code) {
            duration = elapsed(stageStart);
            stage = Stage.LOADED;
            event = RuntimeLogEventKind.MODEL_LOAD_COMPLETED;
        } else if (stage == Stage.LOADED && code == InferencePhase.INFERENCE_STARTED.code) {
            stage = Stage.INFERRING;
            stageStart = now();
            event = RuntimeLogEventKind.INFERENCE_STARTED;
        } else if (stage == Stage.INFERRING && code == InferencePhase.INFERENCE_COMPLETED.code) {
            duration = elapsed(stageStart);
            stage = Stage.DONE;
            event = RuntimeLogEventKind.INFERENCE_COMPLETED;
        } else { invalidate(); return; }
        emit(event, "ok", duration);
    }
    /** Adapter successful validated return, NOT report/export/UI/IME commit. */
    synchronized void returned() {
        if (closed) return;
        closed = true;
        if (stage != Stage.DONE) invalidate();
        if (!invalid) emit(RuntimeLogEventKind.REQUEST_SUCCESS, "ok", elapsed(requestStart));
    }
    synchronized void failed(String detail) {
        if (closed) return;
        closed = true;
        if (!invalid) {
            if (stage == Stage.ACCESS)
                emit(RuntimeLogEventKind.MODEL_ACCESS_FAILED, "verify-failed", elapsed(stageStart));
            else if (stage == Stage.LOADING)
                emit(RuntimeLogEventKind.MODEL_LOAD_FAILED, "load-failed", elapsed(stageStart));
            else if (stage == Stage.INFERRING)
                emit(RuntimeLogEventKind.INFERENCE_FAILED, "inference-failed", elapsed(stageStart));
        }
        emit(RuntimeLogEventKind.REQUEST_FAILURE, detail, elapsed(requestStart));
    }
}
