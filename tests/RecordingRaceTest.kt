import org.llmasr.minimal.audio.RecordingControl
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Deterministic session-gate interleavings; no Android microphone or sleeps. */
class RecordingRaceTest {
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check " + checks) }
        private fun await(latch: CountDownLatch) {
            try { if (!latch.await(3, TimeUnit.SECONDS)) throw AssertionError("latch timeout") }
            catch (e: InterruptedException) { throw AssertionError(e) }
        }
        private fun join(t: Thread) { t.join(4000); check(!t.isAlive) }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            val error = AtomicReference<Throwable>()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> error.compareAndSet(null, e) }
            // R1: cancel/pause wins before the fake backend start gate.
            val before = RecordingControl(); val starts = AtomicInteger()
            val ready = CountDownLatch(1); val go = CountDownLatch(1)
            var worker = Thread {
                ready.countDown(); await(go)
                try { before.start { starts.incrementAndGet() } } finally { before.captureReleased() }
            }
            worker.start(); await(ready); check(before.cancel()); go.countDown(); join(worker)
            check(starts.get() == 0 && before.released() && before.cancelled()); check(!before.tryCommitInference())
            val active = RecordingControl(); val entered = CountDownLatch(1); val finishStart = CountDownLatch(1)
            val starting = AtomicBoolean(); val overlap = AtomicBoolean()
            val cancelled = CountDownLatch(1); val cancelAttempt = CountDownLatch(1)
            worker = Thread {
                try {
                    active.start { starting.set(true); entered.countDown(); await(finishStart); starting.set(false) }
                    await(cancelled)
                } finally { active.captureReleased() }
            }
            worker.start(); await(entered)
            val cancel = Thread { cancelAttempt.countDown(); active.cancel(); overlap.set(starting.get()); cancelled.countDown() }
            cancel.start(); await(cancelAttempt); finishStart.countDown(); join(cancel); join(worker)
            check(!overlap.get() && active.cancelled() && active.released()); check(!active.tryCommitInference())
            val handoff = RecordingControl(); val inference = AtomicInteger(); val wavs = AtomicInteger()
            val released = CountDownLatch(1); val commit = CountDownLatch(1)
            worker = Thread {
                handoff.start {}; handoff.stop(); handoff.captureReleased(); released.countDown(); await(commit)
                if (handoff.tryCommitInference()) { wavs.incrementAndGet(); inference.incrementAndGet() }
            }
            worker.start(); await(released); check(handoff.cancel()); commit.countDown(); join(worker)
            check(inference.get() == 0 && wavs.get() == 0 && !handoff.committed())
            val committed = RecordingControl(); committed.start {}
            check(!committed.tryCommitInference()); committed.stop(); committed.captureReleased()
            check(committed.tryCommitInference()); check(!committed.cancel()); check(!committed.cancelled())
            check(!committed.tryCommitInference()); check(!committed.start { throw AssertionError("restarted") })
            val failed = RecordingControl(); var threw = false
            try { failed.start { throw IllegalStateException("fake start failure") } }
            catch (expected: IllegalStateException) { threw = true } finally { failed.captureReleased() }
            check(threw && failed.released()); check(!failed.tryCommitInference())
            check(!failed.start { throw AssertionError("start after release") })
            if (error.get() != null) throw AssertionError("worker failure", error.get())
            println("PASS " + checks + " recording lifecycle gate checks (fake backend; not device release latency)")
        }
    }
}
