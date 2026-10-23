import org.llmasr.minimal.transcription.AppRequestPolicy
import org.llmasr.minimal.audio.RecordingControl
import org.llmasr.minimal.task.RequestContext
import org.llmasr.minimal.task.RequestRunner
import org.llmasr.minimal.transcription.ResultState
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.task.TaskKind
import org.llmasr.minimal.transcription.TextExportController
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.*

/** Deterministic production admission/runner/owner integration, no Android runtime. */
class AdmissionBoundaryTest {
    internal class Queue : Executor {
        private var task: Runnable? = null
        override fun execute(command: Runnable?) { if (task != null) throw AssertionError("duplicate"); task = command }
        internal fun drain() { val next = task; task = null; next!!.run() }
    }
    private class State : AppRequestPolicy.State {
        var text: String? = "old"; var status: String? = ""; var verified = false
        override fun lastText(): String? = text
        override fun setLastText(text: String?) { this.text = text }
        override fun lastStatus(): String? = status
        override fun setLastStatus(status: String?) { this.status = status }
    }
    private class Reports : AppRequestPolicy.Reports {
        val states = ArrayList<String?>(); val contexts = ArrayList<RequestContext?>()
        private fun add(state: String?, ctx: RequestContext?) { states.add(state); contexts.add(ctx) }
        override fun writePending(ctx: RequestContext?) { add("pending", ctx) }
        override fun writeTerminal(ctx: RequestContext?) { add("complete", ctx) }
        override fun writeFailure(ctx: RequestContext?, cause: Throwable?) { add("failed", ctx) }
        override fun writeCancel(ctx: RequestContext?) { add("cancelled", ctx) }
    }
    companion object {
        private var checks = 0
        private fun check(value: Boolean) { checks++; if (!value) throw AssertionError("check " + checks) }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            val queue = Queue(); val notifications = ArrayList<Boolean>(); val owner = AtomicReference<TaskCoordinator>()
            val coordinator = TaskCoordinator(queue)
            coordinator.addListener { notifications.add(owner.get().isBusy()) }; owner.set(coordinator)
            val state = State(); val reports = Reports(); val active = AtomicReference<RecordingControl>()
            val starts = AtomicInteger(); val inference = AtomicInteger(); val cleans = AtomicInteger()
            val runner = RequestRunner(coordinator, { 7 }, AppRequestPolicy(reports, state), { cleans.incrementAndGet() })
            val control = RecordingControl()
            check(runner.submit(TaskKind.INFERENCE, {
                try { control.start { starts.incrementAndGet() } } finally { control.captureReleased() }
                if (!control.tryCommitInference()) throw java.util.concurrent.CancellationException()
                inference.incrementAndGet()
            }, { active.set(control) }, { active.compareAndSet(control, null) }))
            check(active.get() === control && coordinator.isBusy()); check(state.text!!.isEmpty()); active.get().cancel()
            val rejectedHooks = AtomicInteger()
            check(!runner.submit(TaskKind.MAINTENANCE, {}, { rejectedHooks.incrementAndGet() }, { rejectedHooks.incrementAndGet() }))
            check(rejectedHooks.get() == 0 && active.get() === control); queue.drain()
            check(starts.get() == 0 && inference.get() == 0 && active.get() == null)
            check(reports.states.equals(Arrays.asList("pending", "cancelled")) && cleans.get() == 1)
            check(reports.contexts[0] === reports.contexts[1]); check(!coordinator.isBusy() && notifications.equals(Arrays.asList(true, false)))
            check(runner.submit(TaskKind.MAINTENANCE) {}); check(coordinator.isBusy()); queue.drain(); check(!coordinator.isBusy())
            val rs = ResultState(); rs.setText("snapshot")
            val exports = TextExportController<String>({ ByteArrayOutputStream() }, Executor { it.run() })
            val ticket = exports.begin(exports.newPage(), rs); check(ticket != null)
            check(exports.state().phase == TextExportController.Phase.SELECTING)
            val reads = AtomicInteger()
            check(runner.submit(TaskKind.MAINTENANCE) { check(exports.begin(exports.newPage(), rs) == null); reads.incrementAndGet() })
            queue.drain(); check(reads.get() == 1); exports.revoke(ticket)
            check(exports.state().phase == TextExportController.Phase.CANCELLED); check(!exports.state().busy())
            reports.states.clear(); state.text = "stale"
            val broken = RequestRunner(coordinator, { throw IOException("preflight") }, AppRequestPolicy(reports, state), { cleans.incrementAndGet() })
            check(broken.submit(TaskKind.INFERENCE) { throw AssertionError("body ran") }); check(state.text!!.isEmpty()); queue.drain()
            check(reports.states.equals(Arrays.asList("failed")) && state.status!!.contains("preflight"))
            val rejectedSession = AtomicReference<RecordingControl>()
            val rejecting = TaskCoordinator(Executor { throw RejectedExecutionException("reject") })
            val rejectRunner = RequestRunner(rejecting, { 0 }, AppRequestPolicy(Reports(), state), {})
            check(!rejectRunner.submit(TaskKind.INFERENCE, {}, { rejectedSession.set(control) }, { rejectedSession.set(null) }))
            check(rejectedSession.get() == null && !rejecting.isBusy() && state.status!!.contains("执行器"))
            check(coordinator.withOwnership { "owned" }.equals("owned") && !coordinator.isBusy())
            for (stage in 0 until 6) {
                val where = stage; val q = Queue(); val c = TaskCoordinator(q); val order = ArrayList<String>()
                val boom: Error = if (where % 2 == 0) AssertionError("synthetic") else InternalError("synthetic")
                val port = object : AppRequestPolicy.Reports {
                    override fun writePending(ctx: RequestContext?) { if (where == 1) throw boom }
                    override fun writeTerminal(ctx: RequestContext?) { if (where == 3) throw boom }
                    override fun writeFailure(ctx: RequestContext?, cause: Throwable?) { if (where == 4) throw boom }
                    override fun writeCancel(ctx: RequestContext?) { throw AssertionError("unexpected cancel") }
                }
                val r = RequestRunner(c, { if (where == 0) throw boom; 0 }, AppRequestPolicy(port, State()), {
                    check(c.isBusy()); order.add("cleanup"); if (where == 5) throw boom
                })
                c.addListener { if (!c.isBusy()) order.add("release") }
                check(r.submit(TaskKind.INFERENCE, {
                    if (where == 2) throw boom
                    if (where == 4) throw IOException("primary")
                }, {}, { check(c.isBusy()); order.add("finish") }))
                try { q.drain(); throw AssertionError("error swallowed") } catch (expected: Error) { check(expected === boom) }
                c.awaitFree(); check(!c.isBusy()); check(order.equals(Arrays.asList("cleanup", "finish", "release")))
            }
            val once = AtomicInteger()
            val admissionFailure = RequestRunner(coordinator, { 0 }, AppRequestPolicy(reports, state), { throw AssertionError("worker never starts") })
            try {
                admissionFailure.submit(TaskKind.INFERENCE, {}, { throw AssertionError("admit") }, { once.incrementAndGet() })
                throw IllegalStateException("error swallowed")
            } catch (expected: AssertionError) { check(once.get() == 1 && !coordinator.isBusy()) }
            coordinator.awaitFree(); once.set(0)
            check(!rejectRunner.submit(TaskKind.INFERENCE, {}, {}, { once.incrementAndGet() })); check(once.get() == 1 && !rejecting.isBusy())
            for (inline in booleanArrayOf(false, true)) {
                val q = Queue(); val c = TaskCoordinator(if (inline) Executor { it.run() } else q)
                val finishes = AtomicInteger(); val cleanups = AtomicInteger()
                val r = RequestRunner(c, { 0 }, AppRequestPolicy(Reports(), State()), { cleanups.incrementAndGet() })
                try {
                    r.submit(TaskKind.INFERENCE, {}, {}, { check(c.isBusy()); finishes.incrementAndGet(); throw AssertionError("finalizer") })
                    if (!inline) q.drain()
                    throw IllegalStateException("finalizer error swallowed")
                } catch (expected: AssertionError) { check("finalizer".equals(expected.message)) }
                c.awaitFree(); check(!c.isBusy() && finishes.get() == 1 && cleanups.get() == 1)
            }
            println("PASS " + checks + " production admission/owner/recording/export boundary checks (host only)")
        }
    }
}
