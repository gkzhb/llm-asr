import org.llmasr.minimal.modelmanagement.ModelManagementController
import org.llmasr.minimal.modelmanagement.ModelManagementState
import java.util.concurrent.atomic.*

/** Exercise inspect/cancel, recording observations OUTSIDE callbacks. */
class ModelNotificationTest {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            val f = ModelManagementControllerTest.Fixture(); val c: ModelManagementController = f.controller()
            val lockHeld = AtomicBoolean(); val notices = AtomicInteger()
            val l = ModelManagementState.Listener { if (Thread.holdsLock(c)) lockHeld.set(true); notices.incrementAndGet() }
            f.state.addListener(l); c.refreshInspect(); f.queue.run()
            if (lockHeld.get()) throw AssertionError("INSPECT listeners must be outside controller monitor")
            if (notices.get() != 3) throw AssertionError("INSPECT pending/terminal/finish must all notify")
            c.startImport(f.source()); c.requestCancel(f.control.peekActive().id); f.queue.run()
            if (lockHeld.get()) throw AssertionError("cancel listeners must be outside controller monitor")
            if (f.owner.isBusy() || f.control.isBusy) throw AssertionError("final ownership release")
            f.state.removeListener(l)
            val cancelled = AtomicBoolean()
            val reentrant = ModelManagementState.Listener {
                if (Thread.holdsLock(c)) lockHeld.set(true)
                val s = f.state.current()
                if (s.phase == ModelManagementState.Phase.COPYING && cancelled.compareAndSet(false, true)) c.requestCancel(s.activeOperationId)
            }
            f.state.addListener(reentrant); c.startImport(f.source()); f.queue.run()
            if (!cancelled.get() || lockHeld.get() || f.state.current().phase != ModelManagementState.Phase.CANCELLED)
                throw AssertionError("reentrant cancellation preserves newer state outside monitor")
            f.state.removeListener(reentrant)
            val acquired = AtomicBoolean(); val timedOut = AtomicBoolean()
            val crossThread = ModelManagementState.Listener {
                val done = java.util.concurrent.CountDownLatch(1)
                val thread = Thread { synchronized(c) { acquired.set(true) }; done.countDown() }
                thread.isDaemon = true; thread.start()
                try { if (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) timedOut.set(true) }
                catch (e: InterruptedException) { timedOut.set(true); Thread.currentThread().interrupt() }
            }
            f.state.addListener(crossThread); c.refreshInspect(); f.queue.run(); f.state.removeListener(crossThread)
            if (!acquired.get() || timedOut.get()) throw AssertionError("observer must permit different-thread controller acquisition")
            println("PASS controller inspect/cancel notifications outside monitor")
        }
    }
}
