package org.llmasr.minimal.diagnostics;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Process-owned export lane, independent of ASR and persistence. No UI callbacks.
 * One slot from picker reservation through provider CLOSE. Busy requests are rejected,
 * never queued/retried. Production backend must contain only application context.
 * Targets/payloads are never part of observable state, saved state or diagnostics. */
public final class LogExportController<T> {
    public interface Backend<T> { OutputStream open(T target) throws IOException; }
    public enum Phase { IDLE, SELECTING, WRITING, SUCCEEDED, FAILED, CANCELLED, EXPIRED }
    public static final class State {
        public final long pageId;
        public final int requestCode;
        public final Phase phase;
        public final long bytes;
        private State(Ticket ticket, Phase phase, long bytes) {
            pageId = ticket == null ? 0 : ticket.pageId;
            requestCode = ticket == null ? 0 : ticket.requestCode;
            this.phase = phase; this.bytes = bytes;
        }
        public boolean busy() { return phase == Phase.SELECTING || phase == Phase.WRITING; }
    }
    public static final class Ticket {
        public final long pageId;
        public final int requestCode;
        public final List<RuntimeLogEvent> snapshot;
        private Ticket(long pageId, int requestCode, List<RuntimeLogEvent> snapshot) {
            this.pageId = pageId; this.requestCode = requestCode;
            this.snapshot = Collections.unmodifiableList(new ArrayList<>(snapshot));
        }
    }
    private final Backend<T> backend;
    private final Executor executor;
    private Ticket active;
    private long nextPage;
    private int nextCode = 8000;
    private volatile State state = new State(null, Phase.IDLE, 0);

    public LogExportController(Backend<T> backend) { this(backend, executor()); }
    /** Inject only an asynchronous executor in production. No executor shutdown owned by a page. */
    public LogExportController(Backend<T> backend, Executor executor) {
        if (backend == null || executor == null) throw new IllegalArgumentException("export dependencies required");
        this.backend = backend; this.executor = executor;
    }
    private static Executor executor() {
        return new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1), r -> {
                Thread t = new Thread(r, "runtime-log-export"); t.setDaemon(true); return t;
            });
    }
    public synchronized long newPage() { return ++nextPage; }
    public State state() { return state; }
    public synchronized Ticket begin(long pageId, List<RuntimeLogEvent> snapshot) {
        // Android legacy request codes are 16-bit. Never reuse a code in this process.
        if (active != null || nextCode > 65535 || pageId <= 0 || snapshot == null
                || snapshot.isEmpty() || snapshot.size() > RuntimeLogStore.DEFAULT_CAPACITY) return null;
        active = new Ticket(pageId, nextCode++, snapshot);
        state = new State(active, Phase.SELECTING, 0);
        return active;
    }
    public synchronized void abandon(Ticket ticket, boolean expired) {
        if (ticket == null || active != ticket || state.phase != Phase.SELECTING) return;
        state = new State(ticket, expired ? Phase.EXPIRED : Phase.CANCELLED, 0);
        active = null;
    }
    public boolean submit(Ticket ticket, T target) {
        synchronized (this) {
            if (ticket == null || active != ticket || state.phase != Phase.SELECTING || target == null) return false;
            state = new State(ticket, Phase.WRITING, 0);
        }
        try { executor.execute(() -> write(ticket, target)); }
        catch (RuntimeException | LinkageError rejected) { complete(ticket, false, 0); }
        catch (Error fatal) { complete(ticket, false, 0); throw fatal; }
        return true;
    }
    private void write(Ticket ticket, T target) {
        boolean success = false;
        long bytes = 0;
        try {
            OutputStream out = backend.open(target);
            if (out == null) throw new IOException("open-failed");
            Throwable primary = null;
            try {
                bytes = RuntimeLogCodec.writeSnapshot(ticket.snapshot, out);
            } catch (IOException | RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                try { out.close(); }
                catch (IOException | RuntimeException | Error closing) {
                    if (primary == null) throw closing;
                    // Preserve a primary fatal; otherwise a fatal close outranks
                    // recoverable write/flush failure instead of being swallowed.
                    if (closing instanceof Error && !(closing instanceof LinkageError)
                            && !(primary instanceof Error && !(primary instanceof LinkageError))) {
                        if (primary != closing) closing.addSuppressed(primary);
                        throw (Error)closing;
                    }
                    if (primary != closing) primary.addSuppressed(closing);
                }
            }
            success = true; // close, not merely write/flush, is part of success.
        } catch (IOException | RuntimeException | LinkageError failure) {
            // No provider text/URI/path enters state. Partial output may remain.
        } finally {
            // Hard VM errors may propagate, but cannot retain the process slot forever.
            complete(ticket, success, bytes);
        }
    }
    private synchronized void complete(Ticket ticket, boolean success, long bytes) {
        if (active != ticket) return;
        state = new State(ticket, success ? Phase.SUCCEEDED : Phase.FAILED, success ? bytes : 0);
        active = null;
    }
}
