import org.llmasr.minimal.diagnostics.*
import org.llmasr.minimal.task.TaskCoordinator
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Production page tickets/export owner. No Android picker/runtime claim. */
class LogExportTest {
    private class Queue : Executor {
        val work = ArrayList<Runnable?>()
        override fun execute(command: Runnable?) { work.add(command) }
        fun run() { work.removeAt(0)!!.run() }
    }
    companion object {
        private var checks = 0
        private fun check(ok: Boolean, message: String) { checks++; if (!ok) throw AssertionError(message) }
        private fun logs(): RuntimeLogStore {
            val s = RuntimeLogStore(); s.append(RuntimeLogEventKind.APP_STARTUP, RuntimeLogSource.APP, null, "ok"); return s
        }
        private fun abandonedPickerProtection() {
            val s = logs(); val q = Queue(); val opens = AtomicInteger()
            val c = LogExportController<String>({ opens.incrementAndGet(); ByteArrayOutputStream() }, q)
            val page = LogExportPage(c); page.foreground(true)
            val failed = page.begin(s.snapshot()); page.abandon(true)
            check(c.state().phase == LogExportController.Phase.EXPIRED && !c.state().busy(), "picker abandon releases slot")
            check(c.state().bytes == 0L && q.work.isEmpty() && opens.get() == 0, "picker abandon no IO")
            val retry = page.begin(s.snapshot())
            check(retry != null && retry.pageId == failed!!.pageId && retry.requestCode != failed.requestCode, "picker retry fresh code same page")
            check(!page.result(failed!!.requestCode, "stale"), "failed picker callback rejected")
            check(c.state().phase == LogExportController.Phase.SELECTING && q.work.isEmpty(), "old callback cannot submit retry")
            page.foreground(false); check(page.result(retry!!.requestCode, "deferred"), "picker result deferred"); page.destroy()
            val next = LogExportPage(c); next.foreground(true)
            check(c.state().phase == LogExportController.Phase.EXPIRED && !next.result(retry.requestCode, "stale"), "recreated page rejects old result")
            check(q.work.isEmpty() && opens.get() == 0, "destroyed deferred picker never writes"); next.destroy()
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            abandonedPickerProtection()
            val s = logs(); val q = Queue(); val out = ByteArrayOutputStream(); val opens = AtomicInteger()
            val c = LogExportController<String>({ opens.incrementAndGet(); out }, q)
            val page = LogExportPage(c)
            check(page.begin(s.snapshot()) == null, "not foreground"); page.foreground(true)
            val first = page.begin(s.snapshot()); check(first != null, "begin")
            s.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, null, "ok")
            check(first!!.snapshot.size == 1, "frozen")
            try { (first.snapshot as MutableList<RuntimeLogEvent?>).clear(); throw AssertionError("mutable") }
            catch (expected: UnsupportedOperationException) { checks++ }
            page.foreground(false); check(page.result(first.requestCode, "target"), "result before resume deferred")
            check(!page.result(first.requestCode, "target"), "duplicate result"); check(opens.get() == 0 && q.work.isEmpty(), "no background admission")
            page.foreground(true); check(c.state().phase == LogExportController.Phase.WRITING, "queued")
            page.destroy(); check(c.state().busy(), "destroy does not release IO slot")
            val next = LogExportPage(c); next.foreground(true); check(next.begin(s.snapshot()) == null, "busy no queue")
            q.run(); check(c.state().phase == LogExportController.Phase.SUCCEEDED, "success")
            val text = String(out.toByteArray(), StandardCharsets.UTF_8)
            check(text.contains("APP_STARTUP") && !text.contains("APP_READY"), "actual selected snapshot not live")
            check(c.state().bytes == out.size().toLong() && out.size() > 1, "byte count")
            val second = next.begin(s.snapshot()); check(second!!.requestCode != first.requestCode, "unique request")
            check(!next.result(first.requestCode, "stale"), "old callback cannot consume new ticket")
            check(next.result(second.requestCode, null), "cancel"); check(opens.get() == 1 && !c.state().busy(), "cancel no IO")
            val rotation = next.begin(s.snapshot()); next.foreground(false); next.result(rotation!!.requestCode, "deferred"); next.destroy()
            check(c.state().phase == LogExportController.Phase.EXPIRED, "destroy invalidates deferred")
            next.foreground(true); check(q.work.isEmpty(), "destroy never revive")
            for (mode in 0 until 8) {
                val fault = mode; val tasks = Queue(); val expectedFatal = AssertionError("fatal")
                val failing = LogExportController<String>({
                    when (fault) {
                        0 -> null
                        1 -> throw IOException("private-content://secret")
                        2 -> throw SecurityException("private")
                        3 -> throw LinkageError("private")
                        else -> object : ByteArrayOutputStream() {
                            override fun write(b: ByteArray?, off: Int, len: Int) {
                                if (fault == 4) throw IllegalStateException("private")
                                super.write(b, off, len)
                            }
                            override fun flush() { if (fault == 5) throw IOException("private") }
                            override fun close() { if (fault == 6) throw IOException("private"); if (fault == 7) throw expectedFatal }
                        }
                    }
                }, tasks)
                val t = failing.begin(failing.newPage(), s.snapshot()); check(failing.submit(t, "user-selected-uri"), "failure admitted")
                var caught: Throwable? = null
                try { tasks.run() } catch (failure: Throwable) { caught = failure }
                check(caught === if (fault == 7) expectedFatal else null, "exact fatal propagation outside capture")
                check(failing.state().phase == LogExportController.Phase.FAILED && !failing.state().busy(), "failure releases slot including close")
                check(!RuntimeLogText.export(failing.state()).contains("private"), "safe failure status")
                check(failing.state().bytes == 0L, "failure no success bytes")
            }
            for (primaryFatal in booleanArrayOf(false, true)) {
                val tasks = Queue(); val expectedFatal: Error = InternalError("fatal-close-sentinel"); val closes = AtomicInteger()
                val compound = LogExportController<String>({ object : OutputStream() {
                    override fun write(b: Int) { if (primaryFatal) throw expectedFatal; throw IOException("ordinary-write") }
                    override fun close() { closes.incrementAndGet(); if (primaryFatal) throw IOException("ordinary-close"); throw expectedFatal }
                } }, tasks)
                val ticket = compound.begin(compound.newPage(), s.snapshot()); compound.submit(ticket, "target")
                var caught: Throwable? = null; try { tasks.run() } catch (failure: Throwable) { caught = failure }
                check(caught === expectedFatal, "compound exact fatal propagation")
                check(closes.get() == 1 && !compound.state().busy() && compound.state().phase == LogExportController.Phase.FAILED, "compound close/release")
            }
            val rejected = LogExportController<String>({ out }, Executor { throw RejectedExecutionException("private") })
            var t = rejected.begin(rejected.newPage(), s.snapshot()); rejected.submit(t, "target")
            check(rejected.state().phase == LogExportController.Phase.FAILED && !rejected.state().busy(), "executor failure")
            val closing = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val background = Executor { r -> Thread { try { r.run() } catch (e: Throwable) { failure.set(e) } finally { finished.countDown() } }.start() }
            val blocked = LogExportController<String>({ object : ByteArrayOutputStream() {
                override fun close() {
                    closing.countDown()
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw IOException("timeout") }
                    catch (e: InterruptedException) { throw IOException(e) }
                }
            } }, background)
            t = blocked.begin(blocked.newPage(), s.snapshot()); blocked.submit(t, "target")
            try {
                check(closing.await(5, TimeUnit.SECONDS), "close reached"); check(blocked.state().busy(), "slot held through close")
                check(blocked.begin(blocked.newPage(), s.snapshot()) == null, "blocked close no second export")
                val owner = TaskCoordinator(Executor { it.run() }); check(owner.submit {}, "export independent ASR owner")
            } finally { release.countDown() }
            check(finished.await(5, TimeUnit.SECONDS) && failure.get() == null, "bounded background completes")
            check(blocked.state().phase == LogExportController.Phase.SUCCEEDED, "success after close only")
            for (kind in RuntimeLogEventKind.values()) check(!RuntimeLogText.event(kind).isEmpty(), "all event meanings")
            check(RuntimeLogText.event(RuntimeLogEventKind.LOG_TELEMETRY_FAILED).contains("不代表"), "telemetry not ASR failure")
            println("PASS LogExportTest: " + checks + " checks (production tickets/owner/streams; not Android runtime)")
        }
    }
}
