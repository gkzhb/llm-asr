package org.llmasr.minimal.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Bounded store. Sequence assignment, insertion and recovery share one lock.
 * One publisher coalesces concurrent/reentrant changes, never calling sinks
 * under the store lock and never delivering an older snapshot after a newer one.
 * Sinks must be quick/nonblocking (post UI work, schedule IO); intermediate
 * snapshots may be skipped. Invalid events and listener RuntimeExceptions are isolated. */
public final class RuntimeLogStore {
    public static final int DEFAULT_CAPACITY = 1000;
    private final int capacity;
    private final RuntimeLogClock clock;
    private final List<RuntimeLogSink> sinks = new CopyOnWriteArrayList<>();
    private final Object monitor = new Object();
    private List<RuntimeLogEvent> events = new ArrayList<>();
    private long droppedCount;
    private long nextSequence;
    private boolean recovered;
    private boolean publishing;
    private boolean pending;

    public RuntimeLogStore() { this(DEFAULT_CAPACITY, new RuntimeLogClock.System()); }
    public RuntimeLogStore(int capacity) { this(capacity, new RuntimeLogClock.System()); }
    public RuntimeLogStore(int capacity, RuntimeLogClock clock) {
        if (capacity < 1 || capacity > DEFAULT_CAPACITY) throw new IllegalArgumentException("invalid capacity");
        if (clock == null) throw new IllegalArgumentException("clock required");
        this.capacity = capacity;
        this.clock = clock;
    }
    public int capacity() { return capacity; }
    public RuntimeLogClock clock() { return clock; }
    public long droppedCount() { synchronized (monitor) { return droppedCount; } }

    public boolean append(RuntimeLogEventKind kind, RuntimeLogSource source, String requestId, String detail) {
        return append(kind, source, requestId, detail, -1);
    }

    /** elapsedNanos is -1 or a caller-measured monotonic duration, not wall time. */
    public boolean append(RuntimeLogEventKind kind, RuntimeLogSource source, String requestId,
                          String detail, long elapsedNanos) {
        synchronized (monitor) {
            try {
                RuntimeLogEvent event = new RuntimeLogEvent(nextSequence, clock.wallTimeMillis(),
                    clock.monotonicNanos(), kind, source, requestId, detail, elapsedNanos);
                nextSequence++;
                events.add(event);
                trim();
                pending = true;
            } catch (RuntimeException invalid) { return false; }
        }
        publish();
        return true;
    }

    private void trim() {
        int excess = events.size() - capacity;
        if (excess > 0) {
            events.subList(0, excess).clear();
            droppedCount += excess;
        }
    }

    public List<RuntimeLogEvent> snapshot() {
        synchronized (monitor) { return immutableCopy(); }
    }
    private List<RuntimeLogEvent> immutableCopy() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
    public int size() { synchronized (monitor) { return events.size(); } }

    /** One worker-owned recovery only. History precedes early live appends;
     * their sequence numbers are rebased above history, their times unchanged.
     * Previously captured snapshots remain immutable but IDs are not recovery tickets. */
    void restore(List<RuntimeLogEvent> history) {
        synchronized (monitor) {
            if (recovered) return;
            long previous = -1;
            for (RuntimeLogEvent e : history) {
                if (e == null || e.sequence <= previous) throw new IllegalArgumentException("invalid history");
                previous = e.sequence;
            }
            if (previous >= Long.MAX_VALUE - nextSequence - 1)
                throw new IllegalArgumentException("sequence overflow");
            long offset = previous + 1;
            List<RuntimeLogEvent> merged = new ArrayList<>(history);
            for (RuntimeLogEvent e : events) merged.add(e.withSequence(e.sequence + offset));
            nextSequence += offset;
            events = merged;
            recovered = true;
            trim();
            pending = true;
        }
        publish();
    }

    public void addSink(RuntimeLogSink sink) { if (sink != null) sinks.add(sink); }
    public void removeSink(RuntimeLogSink sink) { sinks.remove(sink); }

    private void publish() {
        synchronized (monitor) {
            if (publishing) return;
            publishing = true;
        }
        boolean released = false;
        try {
            while (true) {
                List<RuntimeLogEvent> snapshot;
                int dropped;
                synchronized (monitor) {
                    // Normal release and the empty check must be atomic. An
                    // append after this point must acquire its own publisher.
                    if (!pending) { publishing = false; released = true; return; }
                    snapshot = immutableCopy();
                    pending = false;
                    dropped = (int) Math.min(Integer.MAX_VALUE, droppedCount);
                }
                for (RuntimeLogSink sink : sinks) {
                    try { sink.onSnapshot(snapshot, dropped); }
                    catch (RuntimeException ignored) { /* preserve observer isolation policy */ }
                }
            }
        } finally {
            // Do not release on the normal path a second time: another thread
            // may already own publication. Exceptional exit has no successor yet.
            if (!released) synchronized (monitor) {
                pending = true;
                publishing = false;
            }
        }
    }

    /** Milliseconds and explicit local UTC offset; Locale-independent numeric duration. */
    public static String formatEvent(RuntimeLogEvent e) {
        if (e == null) return "";
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS XXX", java.util.Locale.ROOT);
        StringBuilder s = new StringBuilder(fmt.format(new java.util.Date(e.wallMillis)))
            .append(" [").append(e.source).append("] ").append(e.kind.name());
        if (e.requestId != null) s.append(" req=").append(e.requestId);
        if (e.detail != null) s.append(" detail=").append(e.detail);
        if (e.elapsedNanos >= 0) {
            s.append(" elapsed_ms=").append(e.elapsedNanos / 1_000_000).append('.')
                .append(String.format(java.util.Locale.ROOT, "%06d", e.elapsedNanos % 1_000_000));
        }
        return s.toString();
    }
}
