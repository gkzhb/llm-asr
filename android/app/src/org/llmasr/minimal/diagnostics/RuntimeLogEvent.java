package org.llmasr.minimal.diagnostics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable privacy boundary: enums, canonical UUID, fixed detail vocabulary,
 * and numeric times only. No exception messages or user text are accepted. */
public final class RuntimeLogEvent {
    private static final Set<String> DETAILS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "ok", "failed", "cancelled", "unknown", "verify-failed", "load-failed",
        "inference-failed", "native-runtime", "native-null", "parse-failed",
        "io", "invalid", "unsafe-path", "corrupt", "oversized",
        "queue-full", "closed", "open-failed", "export-cap", "export-io", "export-invalid")));

    public final long sequence;
    public final long wallMillis;
    /** Process-local time; never subtract a recovered event's time in a new process. */
    public final long monotonicNanos;
    /** -1 means not supplied; otherwise a duration measured within one process. */
    public final long elapsedNanos;
    public final RuntimeLogEventKind kind;
    public final RuntimeLogSource source;
    public final String requestId;
    public final String detail;

    public RuntimeLogEvent(long sequence, long wallMillis, long monotonicNanos,
                           RuntimeLogEventKind kind, RuntimeLogSource source,
                           String requestId, String detail) {
        this(sequence, wallMillis, monotonicNanos, kind, source, requestId, detail, -1);
    }

    public RuntimeLogEvent(long sequence, long wallMillis, long monotonicNanos,
                           RuntimeLogEventKind kind, RuntimeLogSource source,
                           String requestId, String detail, long elapsedNanos) {
        if (sequence < 0 || sequence == Long.MAX_VALUE || elapsedNanos < -1)
            throw new IllegalArgumentException("invalid numeric field");
        if (kind == null || source == null) throw new IllegalArgumentException("enum required");
        this.sequence = sequence;
        this.wallMillis = wallMillis;
        this.monotonicNanos = monotonicNanos;
        this.elapsedNanos = elapsedNanos;
        this.kind = kind;
        this.source = source;
        this.requestId = sanitizeRequestId(requestId);
        this.detail = sanitizeDetail(detail);
    }

    public static String sanitizeRequestId(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("invalid request id");
        return value;
    }

    public static String sanitizeDetail(String value) {
        if (value == null || value.isEmpty()) return null;
        if (!DETAILS.contains(value)) throw new IllegalArgumentException("unknown detail");
        return value;
    }

    RuntimeLogEvent withSequence(long sequence) {
        return new RuntimeLogEvent(sequence, wallMillis, monotonicNanos, kind, source,
            requestId, detail, elapsedNanos);
    }

    public String formatLocal() {
        return String.format(Locale.ROOT, "seq=%d %s", sequence, RuntimeLogStore.formatEvent(this));
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeLogEvent)) return false;
        RuntimeLogEvent other = (RuntimeLogEvent) o;
        return sequence == other.sequence && wallMillis == other.wallMillis
            && monotonicNanos == other.monotonicNanos && elapsedNanos == other.elapsedNanos
            && kind == other.kind && source == other.source
            && Objects.equals(requestId, other.requestId) && Objects.equals(detail, other.detail);
    }
    @Override public int hashCode() {
        return Objects.hash(sequence, wallMillis, monotonicNanos, elapsedNanos, kind, source, requestId, detail);
    }
    @Override public String toString() { return formatLocal(); }
}
