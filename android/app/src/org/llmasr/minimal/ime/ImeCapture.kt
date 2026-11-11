package org.llmasr.minimal.ime

import java.util.concurrent.CountDownLatch
import org.llmasr.minimal.audio.RecordingControl

/** Exactly one request-local capture worker. Outer owner drains it before cleanup, even on Error. */
internal class ImeCapture(private val control: RecordingControl, private val capture: (RecordingControl.StartGate) -> ByteArray?) {
    private val attempted = CountDownLatch(1)
    private var wav: ByteArray? = null
    private var failure: Throwable? = null
    private val worker = Thread({
        try { wav = capture(RecordingControl.StartGate { hardware ->
            try { hardware.run(); true } finally { attempted.countDown() }
        }) } catch (caught: Throwable) { failure = caught; control.cancel() }
        finally { attempted.countDown() }
    }, "ime-capture").apply { isDaemon = true }
    fun start() { worker.start() }
    /** Native prepare starts only after actual start (or capture terminal failure), not merely dispatch. */
    fun awaitStart() {
        var interrupted = false
        while (true) {
            try { attempted.await(); break }
            catch (caught: InterruptedException) { interrupted = true; control.cancel() }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
    fun finish(): ByteArray? {
        var interrupted = false
        while (worker.isAlive) {
            try { worker.join() }
            catch (caught: InterruptedException) { interrupted = true; control.cancel() }
        }
        if (interrupted) Thread.currentThread().interrupt()
        failure?.let { throw it }
        return wav
    }
}
