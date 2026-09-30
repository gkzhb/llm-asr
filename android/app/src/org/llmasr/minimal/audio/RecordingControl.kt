package org.llmasr.minimal.audio

/** One linearization gate for microphone start, cancel and inference ownership.
 * No lock is held over the read loop, WAV encoding, model loading or inference.
 * Cancellation signals the worker; released() is its hardware-cleanup acknowledgement.
 */
class RecordingControl {
    private var stopped = false
    private var cancelled = false
    private var started = false
    private var released = false
    private var committed = false

    @Synchronized
    fun start(startBackend: Runnable?): Boolean {
        if (stopped || released || started || committed) return false
        // cancel() cannot win between eligibility and the actual backend start.
        startBackend!!.run()
        started = true
        return true
    }

    @Synchronized fun stop() { if (!committed) stopped = true }
    @Synchronized fun cancel(): Boolean {
        if (committed) return false
        cancelled = true
        stopped = true
        return true
    }
    @Synchronized fun captureReleased() { released = true }
    @Synchronized fun tryCommitInference(): Boolean {
        if (cancelled || !released || !started || committed) return false
        committed = true
        stopped = true
        return true
    }
    @Synchronized fun stopped(): Boolean = stopped
    @Synchronized fun cancelled(): Boolean = cancelled
    @Synchronized fun released(): Boolean = released
    @Synchronized fun committed(): Boolean = committed
}
