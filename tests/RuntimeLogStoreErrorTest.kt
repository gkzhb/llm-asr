package org.llmasr.minimal

import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import org.llmasr.minimal.diagnostics.*

/** Real store recovery/publisher sequencing, bounded joins and no reflection. */
class RuntimeLogStoreErrorTest {
    companion object {
        private var checks = 0
        private fun check(b: Boolean, message: String) { checks++; if (!b) throw AssertionError(message) }
        private fun append(s: RuntimeLogStore) { s.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok") }
        private fun await(l: CountDownLatch) { if (!l.await(3, TimeUnit.SECONDS)) throw AssertionError("latch timeout") }
        private fun join(t: Thread) { t.join(3000); check(!t.isAlive, "producer must terminate") }
        private fun recovery() {
            val s = RuntimeLogStore(); val broken = RuntimeLogSink { _, _ -> throw AssertionError("observer") }
            s.addSink(broken)
            try { append(s) } catch (oldImplementation: AssertionError) { /* original recovery boundary */ }
            s.removeSink(broken); val delivered = AtomicInteger(); s.addSink { _, _ -> delivered.incrementAndGet() }; append(s)
            check(delivered.get() == 1, "later append must notify after failed sink is removed"); check(s.size() == 2, "both events retained")
            for (error in arrayOf<Error>(AssertionError("assertion"), LinkageError("linkage"))) {
                val bad = RuntimeLogSink { _, _ -> throw error }; s.addSink(bad); var seen: Error? = null
                try { append(s) } catch (e: Error) { seen = e }; check(seen === error, "observer Error still signals incomplete telemetry")
                s.removeSink(bad); append(s)
            }
            check(delivered.get() == 5, "subsequent delivery recovers after observer Error")
        }
        private fun fatalRecovery() {
            for (fatal in arrayOf<Error>(OutOfMemoryError("synthetic"), ThreadDeath())) {
                val s = RuntimeLogStore(); val bad = RuntimeLogSink { _, _ -> throw fatal }; s.addSink(bad); var seen: Error? = null
                try { append(s) } catch (e: Error) { seen = e }; check(seen === fatal, "fatal identity propagated")
                s.removeSink(bad); val deliveries = AtomicInteger(); s.addSink { _, _ -> deliveries.incrementAndGet() }; append(s)
                check(deliveries.get() == 1 && s.size() == 2, "publisher released after fatal error")
            }
        }
        private fun concurrentAndReentrant() {
            val s = RuntimeLogStore(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val first = AtomicBoolean(true); val reenter = AtomicBoolean(true); val failure = AtomicReference<Throwable>()
            val seen: MutableList<Long> = CopyOnWriteArrayList()
            s.addSink { _, _ ->
                if (first.compareAndSet(true, false)) {
                    entered.countDown(); try { await(release) } catch (t: Throwable) { failure.compareAndSet(null, t) }
                }
            }
            s.addSink { events, _ ->
                val receiver = seen; val list = events!!; receiver.add(list[list.size - 1]!!.sequence)
                if (reenter.compareAndSet(true, false)) append(s)
            }
            val publisher = Thread { try { append(s) } catch (t: Throwable) { failure.compareAndSet(null, t) } }
            publisher.isDaemon = true; publisher.start(); val producers = ArrayList<Thread>()
            try {
                await(entered)
                for (i in 0 until 4) {
                    val t = Thread {
                        try { s.snapshot(); for (n in 0 until 50) append(s) } catch (e: Throwable) { failure.compareAndSet(null, e) }
                    }
                    t.isDaemon = true; producers.add(t); t.start()
                }
                for (t in producers) join(t)
            } finally { release.countDown(); join(publisher) }
            check(failure.get() == null, "worker failure: " + failure.get()); check(s.size() == 202, "initial + 200 concurrent + one reentrant append")
            check(!seen.isEmpty() && seen[seen.size - 1] == 201L, "latest coalesced snapshot delivered")
            var previous = -1L
            for (seq in seen) { check(seq > previous, "strictly ordered snapshots"); previous = seq }
            append(s); check(seen[seen.size - 1] == 202L, "idle publisher restarts")
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            recovery(); fatalRecovery(); concurrentAndReentrant()
            println("PASS RuntimeLogStoreErrorTest: " + checks + " recovery/fatal/concurrent/reentrant checks")
        }
    }
}
