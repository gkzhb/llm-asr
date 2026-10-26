import org.llmasr.minimal.model.ModelReadiness
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Shared production readiness state, listeners and token identity; host only. */
class ModelReadinessTest {
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check " + checks) }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            run {
                val r = ModelReadiness(); val old = r.beginVerify("old-generation")
                r.invalidate(); val current = r.beginVerify("new-generation")
                check(!r.accepts(old)); check(!r.commitVerified(old)); check(r.accepts(current)); check(r.commitVerified(current))
            }
            run {
                val r = ModelReadiness(); val s = r.snapshot()
                check(s.state == ModelReadiness.State.UNKNOWN); check(s.epoch == 0L); check(!s.ready()); check(!r.isReady)
            }
            run {
                val r = ModelReadiness(); val t = r.tryBeginVerify(); check(t != null)
                check(r.snapshot().state == ModelReadiness.State.VERIFYING); r.markVerified(t)
                check(r.snapshot().state == ModelReadiness.State.READY); check(r.tryBeginVerify() == null)
            }
            run {
                val r = ModelReadiness(); val t = r.tryBeginVerify(); r.markVerified(t)
                check(r.snapshot().epoch == 0L); check(r.snapshot().verifiedEpoch == 0L); r.invalidate(); val after = r.snapshot()
                check(after.epoch == 1L); check(after.state == ModelReadiness.State.UNKNOWN); check(!after.ready()); check(!r.isReady)
            }
            run {
                val r = ModelReadiness(); val t = r.tryBeginVerify(); r.invalidate(); r.markVerified(t)
                check(!r.isReady); check(r.snapshot().state == ModelReadiness.State.UNKNOWN)
            }
            run {
                val r = ModelReadiness(); r.markInvalid(); check(r.snapshot().state == ModelReadiness.State.INVALID)
                check(r.snapshot().epoch == 1L); val t = r.tryBeginVerify(); check(t != null); r.markVerified(t)
                check(r.snapshot().state == ModelReadiness.State.READY)
            }
            run {
                val r = ModelReadiness(); val count = AtomicInteger()
                val counting = ModelReadiness.Listener { count.incrementAndGet() }
                val throwing = ModelReadiness.Listener { throw IllegalStateException("listener boom") }
                r.addListener(counting); r.addListener(throwing); val t = r.tryBeginVerify(); r.markVerified(t); r.invalidate()
                check(count.get() >= 3); r.removeListener(counting); r.removeListener(throwing)
            }
            run {
                val r = ModelReadiness(); val count = AtomicInteger()
                val l = object : ModelReadiness.Listener { override fun onChange() { count.incrementAndGet() } }
                r.addListener(l); r.invalidate(); check(count.get() == 1); r.removeListener(l); r.invalidate(); check(count.get() == 1)
            }
            run {
                val r = ModelReadiness(); val t = r.tryBeginVerify(); r.markVerified(t); r.invalidate()
                check(r.snapshot().state == ModelReadiness.State.UNKNOWN); check(r.snapshot().epoch == 1L)
            }
            run {
                val r = ModelReadiness(); val a = r.snapshot(); check(a.verifiedEpoch == -1L)
                val t = r.tryBeginVerify(); r.markVerified(t); check(r.snapshot().verifiedEpoch == 0L)
            }
            run { val r = ModelReadiness(); r.markVerified(null); check(r.snapshot().state == ModelReadiness.State.UNKNOWN) }
            run {
                val r = ModelReadiness(); val first = r.beginVerify(); val second = r.beginVerify()
                r.markVerified(first); check(!r.isReady); r.finishUnverified(second); r.markVerified(second); check(!r.isReady)
                r.markInvalid(); val cancelled = r.beginVerify(); r.finishUnverified(cancelled); check(r.state() == ModelReadiness.State.INVALID)
                val other = ModelReadiness(); r.markVerified(other.beginVerify()); check(!r.isReady)
            }
            run {
                val r = ModelReadiness(); val failure = AtomicReference<Throwable>(); val workers = java.util.ArrayList<Thread>()
                val callbacks = AtomicInteger()
                val listener = ModelReadiness.Listener {
                    callbacks.incrementAndGet()
                    val t = Thread { try { r.snapshot() } catch (e: Throwable) { failure.compareAndSet(null, e) } }
                    t.isDaemon = true; workers.add(t); t.start()
                    try {
                        t.join(500)
                        if (t.isAlive) failure.compareAndSet(null, AssertionError("readiness listener held monitor"))
                    } catch (e: Throwable) { failure.compareAndSet(null, e) }
                }
                r.addListener(listener)
                try { r.invalidate(); r.tryBeginVerify(); r.markInvalid() }
                finally {
                    r.removeListener(listener)
                    for (worker in workers) {
                        worker.join(1500)
                        if (worker.isAlive) failure.compareAndSet(null, AssertionError("listener worker teardown timeout"))
                    }
                }
                if (failure.get() != null) throw AssertionError("readiness listener lock regression", failure.get())
                check(callbacks.get() == 3)
            }
            println("PASS " + checks + " model readiness state checks (shared ASR/IME truth)")
        }
    }
}
