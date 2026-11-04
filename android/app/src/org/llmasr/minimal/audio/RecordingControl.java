package org.llmasr.minimal.audio;

import org.jetbrains.annotations.Nullable;

/** One linearization gate for microphone start, cancel and inference ownership.
 * No lock is held over the read loop, WAV encoding, model loading or inference.
 * Cancellation signals the worker; released() is its hardware-cleanup acknowledgement.
 */
public final class RecordingControl {
    /** Dispatcher invoked without this monitor; its callback must use start() for actual start. */
    public interface StartGate { boolean start(Runnable hardwareStart); }
    private boolean stopped;
    private boolean cancelled;
    private boolean started;
    private boolean released;
    private boolean committed;

    public final synchronized boolean start(@Nullable Runnable startBackend) {
        if (stopped || released || started || committed) return false;
        // Preserve the original eligible-null NPE's null message/cause/suppressed shape.
        // Rejected starts must not inspect the nullable backend.
        if (startBackend == null) throw new NullPointerException();
        // cancel() cannot win between eligibility and the actual backend start.
        startBackend.run();
        started = true;
        return true;
    }

    public final synchronized void stop() { if (!committed) stopped = true; }
    public final synchronized boolean cancel() {
        if (committed) return false;
        cancelled = true;
        stopped = true;
        return true;
    }
    public final synchronized void captureReleased() { released = true; }
    public final synchronized boolean tryCommitInference() {
        if (cancelled || !released || !started || committed) return false;
        committed = true;
        stopped = true;
        return true;
    }
    public final synchronized boolean stopped() { return stopped; }
    public final synchronized boolean cancelled() { return cancelled; }
    public final synchronized boolean released() { return released; }
    public final synchronized boolean committed() { return committed; }
}
