import org.llmasr.minimal.task.RequestContext
import org.llmasr.minimal.task.RequestRunner
import org.llmasr.minimal.transcription.AppRequestPolicy
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.task.TaskKind
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Production RequestRunner fake ports; all original success/failure/owner boundaries. */
class RequestRunnerTest {
    private class FakeState : AppRequestPolicy.State {
        val text = AtomicReference<String?>(""); val status = AtomicReference<String?>("")
        override fun lastText(): String? = text.get()
        override fun setLastText(v: String?) { text.set(v) }
        override fun lastStatus(): String? = status.get()
        override fun setLastStatus(v: String?) { status.set(v) }
    }
    internal class FakeReports : AppRequestPolicy.Reports {
        internal val log: MutableList<String> = CopyOnWriteArrayList()
        internal var failPending = false; internal var failTerminal = false; internal var failFailure = false; internal var failCancel = false
        override fun writePending(ctx: RequestContext?) { log.add("pending:" + ctx!!.kind); if (failPending) throw IOException("pending failed") }
        override fun writeTerminal(ctx: RequestContext?) { log.add("terminal:" + ctx!!.kind); if (failTerminal) throw IOException("terminal failed") }
        override fun writeFailure(ctx: RequestContext?, cause: Throwable?) {
            val receiver = log; val kind = ctx!!.kind; val type = cause!!.javaClass.simpleName
            receiver.add("failure:" + kind + ":" + type); if (failFailure) throw IOException("failure failed")
        }
        override fun writeCancel(ctx: RequestContext?) { log.add("cancel:" + ctx!!.kind); if (failCancel) throw IOException("cancel failed") }
    }
    private class FakeCleanup : RequestRunner.Cleanup {
        val log: MutableList<String> = CopyOnWriteArrayList(); val contexts: MutableList<RequestContext> = CopyOnWriteArrayList()
        override fun run(ctx: RequestContext) { contexts.add(ctx); log.add("cleanup:" + ctx.requestId + ":" + ctx.kind) }
    }
    private open class Harness {
        val coordinator = TaskCoordinator(); val state = FakeState(); val reports = FakeReports(); val cleanup = FakeCleanup()
        val preflightCalls = AtomicInteger()
        val preflight = RequestRunner.Preflight { preflightCalls.incrementAndGet(); 7 }
        var runner = RequestRunner(coordinator, preflight, AppRequestPolicy(reports, state), cleanup)
        fun submit(kind: RequestKind, body: RequestRunner.TaskBody): Boolean = runner.submit(
            if (kind == RequestKind.INF) TaskKind.INFERENCE else if (kind == RequestKind.MODEL) TaskKind.MODEL_OPERATION else TaskKind.MAINTENANCE, body)
        fun awaitIdle() { try { coordinator.awaitFree() } catch (e: InterruptedException) { throw AssertionError(e) } }
    }
    private enum class RequestKind { INF, MODEL, MAINT }
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check " + checks) }
        private fun await(latch: CountDownLatch) {
            try { if (!latch.await(3, TimeUnit.SECONDS)) throw AssertionError("latch timeout") }
            catch (e: InterruptedException) { throw AssertionError(e) }
        }
        private fun awaitRelease(c: TaskCoordinator) { try { c.awaitFree() } catch (e: InterruptedException) { throw AssertionError(e) } }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            run {
                val h = Harness(); val bodyEnter = CountDownLatch(1); val bodyFinish = CountDownLatch(1)
                val accepted = h.submit(RequestKind.INF) { ctx ->
                    check(ctx.kind == TaskKind.INFERENCE); check(ctx.cleanedTemporary == 7); check("".equals(h.state.text.get()))
                    bodyEnter.countDown(); await(bodyFinish)
                }
                check(accepted); await(bodyEnter); check("执行中，请保持应用在前台……".equals(h.state.status.get()))
                check(h.reports.log.contains("pending:INFERENCE")); bodyFinish.countDown(); h.awaitIdle()
                check(h.reports.log.contains("terminal:INFERENCE")); check(h.cleanup.log.size == 1); check(h.preflightCalls.get() == 1)
            }
            run {
                val h = Harness(); h.state.setLastText("untouched"); h.state.setLastStatus("preserved"); val done = CountDownLatch(1)
                val accepted = h.submit(RequestKind.MAINT) { done.countDown() }; check(accepted); await(done); h.awaitIdle()
                check("untouched".equals(h.state.text.get())); check(h.state.status.get()!!.startsWith("执行中"))
                check(h.reports.log.isEmpty()); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.state.setLastText("previous text"); val done = CountDownLatch(1)
                h.submit(RequestKind.MODEL) { done.countDown() }; await(done); h.awaitIdle(); check("".equals(h.state.text.get()))
                check(h.reports.log.contains("pending:MODEL_OPERATION")); check(h.reports.log.contains("terminal:MODEL_OPERATION"))
            }
            run {
                val h = Harness(); h.submit(RequestKind.INF) { throw CancellationException("cancelled by user") }; h.awaitIdle()
                check(h.reports.log.contains("cancel:INFERENCE")); check("已取消录音，音频已丢弃。".equals(h.state.status.get())); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.submit(RequestKind.INF) { throw IllegalStateException("boom") }; h.awaitIdle()
                check(h.reports.log.stream().anyMatch { it.startsWith("failure:INFERENCE:IllegalStateException") })
                check(h.state.status.get()!!.startsWith("失败：")); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.reports.failTerminal = true; val done = CountDownLatch(1)
                h.submit(RequestKind.MODEL) { done.countDown() }; await(done); h.awaitIdle()
                check(h.state.status.get()!!.startsWith("失败：")); check(h.reports.log.stream().anyMatch { it.startsWith("failure:") }); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.reports.failFailure = true; h.submit(RequestKind.INF) { throw RuntimeException("primary") }; h.awaitIdle()
                check(h.state.status.get()!!.startsWith("失败且结果无法持久化")); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); val r8Enter = CountDownLatch(1); val r8Finish = CountDownLatch(1)
                val first = h.submit(RequestKind.MAINT) { r8Enter.countDown(); await(r8Finish) }; check(first); await(r8Enter)
                val second = h.submit(RequestKind.MAINT) {}; check(!second); check(h.coordinator.isBusy()); r8Finish.countDown(); h.awaitIdle()
            }
            run {
                val h = Harness(); val done = CountDownLatch(1); h.submit(RequestKind.INF) { done.countDown() }; await(done); h.awaitIdle()
                check(h.cleanup.log.size == 1); val entry = h.cleanup.log[0]; check(entry.startsWith("cleanup:") && entry.endsWith(":INFERENCE"))
            }
            run {
                val h = Harness(); val ids = arrayOfNulls<String>(2); val d1 = CountDownLatch(1); val d2 = CountDownLatch(1)
                h.submit(RequestKind.MAINT) { ids[0] = it.requestId; d1.countDown() }; await(d1); h.awaitIdle()
                h.submit(RequestKind.MAINT) { ids[1] = it.requestId; d2.countDown() }; await(d2); h.awaitIdle()
                check(ids[0] != null && !ids[0]!!.equals(ids[1]))
            }
            run {
                val h = Harness(); val fake = java.io.File("."); val bodyContext = AtomicReference<RequestContext>()
                h.submit(RequestKind.MAINT) { bodyContext.set(it); it.inputWav = fake }; h.awaitIdle(); val entry = h.cleanup.log[0]
                check(entry.startsWith("cleanup:")); check(h.cleanup.contexts[0] === bodyContext.get()); check(h.cleanup.contexts[0].inputWav === fake)
            }
            run {
                val h = Harness(); h.runner = RequestRunner(h.coordinator, { throw IOException("preflight fail") }, AppRequestPolicy(h.reports, h.state), h.cleanup)
                val d = CountDownLatch(1); val accepted = h.submit(RequestKind.INF) { d.countDown() }; check(accepted); awaitRelease(h.coordinator)
                check(h.reports.log.size == 1 && h.reports.log[0].startsWith("failure:")); check(h.state.status.get()!!.startsWith("失败：")); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.reports.failPending = true; val d = CountDownLatch(1); h.submit(RequestKind.INF) { d.countDown() }; awaitRelease(h.coordinator)
                check(h.state.status.get()!!.startsWith("失败：")); check(h.reports.log.size == 2 && h.reports.log[1].startsWith("failure:")); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.reports.failCancel = true; h.submit(RequestKind.INF) { throw CancellationException() }; h.awaitIdle()
                check(h.state.lastStatus()!!.equals("取消完成，但结果无法持久化：cancel failed")); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.submit(RequestKind.INF) { it.inferenceReported = true }; h.awaitIdle()
                check(h.reports.log.equals(java.util.Arrays.asList("pending:INFERENCE"))); check(h.cleanup.log.size == 1)
            }
            for (cancel in booleanArrayOf(false, true)) {
                val h = Harness(); h.state.setLastText("preserved")
                h.submit(RequestKind.MAINT) { if (cancel) throw CancellationException(); throw IOException("maintenance") }; h.awaitIdle()
                check(h.reports.log.isEmpty()); check(h.state.lastText()!!.equals("preserved")); check(h.cleanup.log.size == 1)
            }
            run {
                val h = Harness(); h.runner = RequestRunner(h.coordinator, h.preflight, AppRequestPolicy(h.reports, h.state), { throw IOException("cleanup denied") })
                h.submit(RequestKind.INF) { throw UnsatisfiedLinkError("native missing") }; h.awaitIdle()
                check(h.state.lastStatus()!!.equals("失败：native missing\n临时文件清理失败：cleanup denied"))
                check(h.reports.log.equals(java.util.Arrays.asList("pending:INFERENCE", "failure:INFERENCE:UnsatisfiedLinkError"))); check(!h.coordinator.isBusy())
            }
            println("PASS " + checks + " request runner checks (host only; covers success/failure/cancel/report-failure/owner/cleanup-id/clear-text policy)")
        }
    }
}
