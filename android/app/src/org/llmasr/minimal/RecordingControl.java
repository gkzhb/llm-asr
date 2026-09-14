package org.llmasr.minimal;

/** One linearization gate for microphone start, cancel and inference ownership.
 * No lock is held over the read loop, WAV encoding, model loading or inference.
 * Cancellation signals the worker; released() is its hardware-cleanup acknowledgement.
 */
public final class RecordingControl {
    private boolean stopped, cancelled, started, released, committed;

    public synchronized boolean start(Runnable startBackend) {
        if(stopped || released || started || committed)return false;
        // cancel() cannot win between eligibility and the actual backend start.
        startBackend.run();
        started=true;
        return true;
    }
    public synchronized void stop() { if(!committed)stopped=true; }
    public synchronized boolean cancel() {
        if(committed)return false;
        cancelled=true; stopped=true; return true;
    }
    public synchronized void captureReleased() { released=true; }
    public synchronized boolean tryCommitInference() {
        if(cancelled || !released || !started || committed)return false;
        committed=true; stopped=true; return true;
    }
    public synchronized boolean stopped() { return stopped; }
    public synchronized boolean cancelled() { return cancelled; }
    public synchronized boolean released() { return released; }
    public synchronized boolean committed() { return committed; }
}
