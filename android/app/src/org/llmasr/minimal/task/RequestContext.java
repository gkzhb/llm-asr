package org.llmasr.minimal.task;

import java.io.File;

/** Per-request runtime state. Replaces the prior permanent ConcurrentHashMap
 * of inference-reported flags and the Activity instance fields. The context is
 * constructed by the request runner at submit time, threaded through the body
 * and lifecycle hooks, and dropped when the request ends.
 */
public final class RequestContext {
    public final String requestId;
    public final TaskKind kind;
    /** Result of the preflight cleanup pass. Filled by the runner before the body. */
    public int cleanedTemporary;
    /** Set by the inference body to indicate it wrote its own terminal report. */
    public boolean inferenceReported;
    /** Temp WAV file created for this request, deleted in finally if set. */
    public File inputWav;

    public RequestContext(String requestId, TaskKind kind) {
        this.requestId = requestId;
        this.kind = kind;
    }
}
