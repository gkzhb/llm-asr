import org.llmasr.minimal.task.TaskCoordinator
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Direct tests of the production owner, subscriptions, fatal and rejection paths. */
class TaskCoordinatorTest {
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check " + checks) }
        private fun await(latch: CountDownLatch) {
            try { if (!latch.await(3, TimeUnit.SECONDS)) throw AssertionError("latch timeout") }
            catch (e: InterruptedException) { throw AssertionError(e) }
        }
        private fun awaitRelease(c: TaskCoordinator) {
            try { c.awaitFree() } catch (e: InterruptedException) { throw AssertionError(e) }
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            val c1 = TaskCoordinator(); val enter = CountDownLatch(1); val finish = CountDownLatch(1)
            val accepted = c1.submit { enter.countDown(); await(finish) }
            check(accepted); await(enter); check(c1.isBusy()); finish.countDown(); awaitRelease(c1); check(!c1.isBusy())
            val c2 = TaskCoordinator(); val r2Enter = CountDownLatch(1); val r2Finish = CountDownLatch(1)
            val first = c2.submit { r2Enter.countDown(); await(r2Finish) }; check(first && c2.isBusy()); await(r2Enter)
            val second = c2.submit {}; check(!second && c2.isBusy()); r2Finish.countDown(); awaitRelease(c2); check(!c2.isBusy())
            val c3 = TaskCoordinator(); val result = c3.withOwnership { check(c3.isBusy()); "ok" }
            check("ok".equals(result)); check(!c3.isBusy())
            val c4 = TaskCoordinator(); val r4Enter = CountDownLatch(1); val r4Finish = CountDownLatch(1)
            c4.submit { r4Enter.countDown(); await(r4Finish) }; await(r4Enter)
            var threw = false
            try { c4.withOwnership { "should not run" } } catch (expected: IllegalStateException) { threw = true }
            check(threw && c4.isBusy()); r4Finish.countDown(); awaitRelease(c4)
            val c5 = TaskCoordinator(); var supplierThrew = false
            try { c5.withOwnership { throw RuntimeException("boom") } } catch (expected: RuntimeException) { supplierThrew = true }
            check(supplierThrew && !c5.isBusy())
            val dead: ExecutorService = object : ThreadPoolExecutor(0, 1, 0, TimeUnit.MILLISECONDS, SynchronousQueue()) {
                override fun execute(command: Runnable?) { throw RejectedExecutionException("dead") }
            }
            val c6 = TaskCoordinator(dead); var rejected = false
            try { c6.submit {} } catch (expected: RejectedExecutionException) { rejected = true }
            check(rejected && !c6.isBusy())
            val c7 = TaskCoordinator(Executor { it.run() }); var taskFailed = false
            try { c7.submit { throw RuntimeException("task fail") } } catch (expected: RuntimeException) { taskFailed = true }
            check(taskFailed && !c7.isBusy())
            val c8 = TaskCoordinator(); val r8A = CountDownLatch(1); val r8B = CountDownLatch(1)
            c8.submit { r8A.countDown() }; await(r8A); awaitRelease(c8)
            c8.submit { r8B.countDown() }; await(r8B); awaitRelease(c8); check(!c8.isBusy())
            val queue = AdmissionBoundaryTest.Queue(); val observed = TaskCoordinator(queue)
            val removed = AtomicInteger(); val good = AtomicInteger(); val finalized = AtomicInteger()
            val busy = java.util.ArrayList<Boolean>(); val finalizedAtRelease = java.util.ArrayList<Int>()
            val detached = TaskCoordinator.Listener { removed.incrementAndGet() }
            val bad = TaskCoordinator.Listener { throw AssertionError("observer fault") }
            val runtimeBad = TaskCoordinator.Listener { throw IllegalStateException("observer fault") }
            val listener = TaskCoordinator.Listener {
                good.incrementAndGet(); busy.add(observed.isBusy())
                if (!observed.isBusy()) finalizedAtRelease.add(finalized.get())
            }
            observed.addListener(detached); observed.removeListener(detached)
            observed.addListener(bad); observed.addListener(runtimeBad); observed.addListener(listener); observed.addListener(listener)
            check(observed.submit { finalized.incrementAndGet() }); check(!observed.submit { throw AssertionError() })
            queue.drain(); observed.awaitFree(); check(removed.get() == 0 && good.get() == 2)
            check(busy.equals(java.util.Arrays.asList(true, false))); check(finalizedAtRelease.equals(java.util.Collections.singletonList(1)))
            observed.removeListener(listener); observed.removeListener(bad); observed.removeListener(runtimeBad)
            observed.submit {}; queue.drain(); check(good.get() == 2)
            val fatal = TaskCoordinator.Listener { throw InternalError("synthetic fatal") }
            observed.addListener(fatal); val rollback = AtomicInteger()
            try {
                observed.submit({ throw AssertionError("must not execute") }, {}, { rollback.incrementAndGet() })
                throw AssertionError("fatal swallowed")
            } catch (expected: InternalError) { check(!observed.isBusy() && rollback.get() == 1) }
            observed.awaitFree(); observed.removeListener(fatal)
            observed.submit {}; observed.addListener(fatal)
            try { queue.drain(); throw AssertionError("release fatal swallowed") }
            catch (expected: InternalError) { check(!observed.isBusy()) }
            observed.awaitFree(); observed.removeListener(fatal)
            println("PASS " + checks + " task coordinator checks (host only; no Thread.sleep busy polling)")
        }
    }
}
