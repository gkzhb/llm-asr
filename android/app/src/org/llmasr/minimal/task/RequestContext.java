package org.llmasr.minimal.task;

import java.io.File;
import kotlin.jvm.JvmField;
import org.jetbrains.annotations.Nullable;

/** Per-request runtime state, constructed at submit and dropped at finalization. */
public final class RequestContext {
    @JvmField @Nullable public final String requestId;
    @JvmField @Nullable public final TaskKind kind;
    /** Preflight cleanup count, filled before the body. */
    @JvmField public int cleanedTemporary = 0;
    /** Body wrote its own terminal report. */
    @JvmField public boolean inferenceReported = false;
    /** Request temp WAV, deleted by cleanup when present. */
    @JvmField @Nullable public File inputWav = null;

    public RequestContext(@Nullable String requestId, @Nullable TaskKind kind) {
        this.requestId = requestId;
        this.kind = kind;
    }
}
