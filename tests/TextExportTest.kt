import org.llmasr.minimal.transcription.*
import org.llmasr.minimal.task.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Real production controller/page/result tests, not Android SAF execution. */
class TextExportTest {
    private class Queue : Executor {
        val jobs = ArrayList<Runnable?>()
        override fun execute(command: Runnable?) { jobs.add(command) }
        fun run() { jobs.removeAt(0)!!.run() }
    }
    companion object {
        private var checks = 0
        private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
        private fun result(): ResultState { val r = ResultState(); r.setText("私密 héllo"); return r }
        private fun await(l: CountDownLatch) { check(l.await(4, TimeUnit.SECONDS), "bounded latch") }
        private fun block(l: CountDownLatch) {
            try { if (!l.await(4, TimeUnit.SECONDS)) throw IOException("test timeout") } catch (e: InterruptedException) { throw IOException(e) }
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            val r = result(); val old = r.begin(); r.setText("B"); r.setText(old.text)
            check(r.applyEdit(old, "stale") < 0, "ABA rejected"); check(r.applyEdit(result().begin(), "foreign") < 0, "foreign snapshot rejected")
            check(r.applyEdit(r.begin(), "new") > 0, "current edit accepted"); r.clear(); check(r.currentText().isEmpty(), "clear empty")
            val q = Queue(); val opens = AtomicInteger(); val out = ByteArrayOutputStream()
            val c = TextExportController<String>({ opens.incrementAndGet(); out }, q); val page = c.newPage()
            check(c.begin(page, r) == null, "empty rejected"); r.setText("私密 héllo"); var t = c.begin(page, r)
            check(c.begin(page, r) == null, "one picker"); r.setText("changed after click"); check(c.admit(t, "target"), "queued")
            check(c.state().phase == TextExportController.Phase.QUEUED && opens.get() == 0, "enqueue is not write admission")
            check(!c.admit(t, "replay"), "one shot"); q.run(); check(out.toString("UTF-8").equals(t!!.snapshot.text), "immutable UTF8 snapshot")
            check(c.state().bytes == t.snapshot.text.toByteArray(StandardCharsets.UTF_8).size.toLong(), "byte count")
            check(!c.admit(t, "replay"), "finished replay rejected")
            for (queueFirst in booleanArrayOf(false, true)) {
                r.setText("before clear"); t = c.begin(page, r); val before = opens.get()
                if (queueFirst) check(c.admit(t, "target"), "queue before clear")
                r.clear(); r.setText("new result after clear")
                if (queueFirst) { check(c.state().busy(), "revoked queue holds slot until unwind"); check(c.begin(page, r) == null, "no early reuse"); q.run() }
                else check(!c.admit(t, "target"), "selected clear rejected")
                check(opens.get() == before, "clear forbids provider open"); check(c.state().phase == TextExportController.Phase.EXPIRED, "expired status")
            }
            val p = TextExportPage(c); check(p.begin(r) == null, "not foreground"); p.foreground(true); t = p.begin(r); check(t != null, "foreground begin")
            check(!p.onResult(t!!.requestCode + 1, "foreign"), "foreign ignored"); p.foreground(false)
            check(p.onResult(t.requestCode, "target"), "defer while picker paused"); check(q.jobs.isEmpty(), "no work while background"); p.foreground(true)
            check(q.jobs.size == 1, "resume queues once"); p.destroy(); var before = opens.get(); check(c.state().busy(), "destroy does not free queued slot")
            q.run(); check(opens.get() == before, "destroy queued forbids open"); check(c.state().phase == TextExportController.Phase.CANCELLED, "destroy terminal")
            val fresh = TextExportPage(c); fresh.foreground(true); val freshTicket = fresh.begin(r); check(freshTicket != null, "new page can use same controller")
            check(!fresh.onResult(t.requestCode, "old"), "old callback cannot consume new ticket"); fresh.destroy(); check(!c.state().busy(), "picker destroyed frees slot")
            val deferred = TextExportPage(c); deferred.foreground(true); t = deferred.begin(r); deferred.foreground(false)
            check(deferred.onResult(t!!.requestCode, "target"), "deferred accepts once"); check(!deferred.onResult(t.requestCode, "duplicate"), "duplicate deferred refused")
            before = opens.get(); r.clear(); r.setText("after deferred clear"); deferred.foreground(true); deferred.foreground(true)
            check(q.jobs.isEmpty() && opens.get() == before, "deferred clear forbids resume write")
            val next = deferred.begin(r); check(next != null && !deferred.onResult(t.requestCode, "stale"), "same page stale callback refused")
            deferred.foreground(false); deferred.onResult(next!!.requestCode, "target"); deferred.destroy(); deferred.foreground(true); check(q.jobs.isEmpty(), "destroy deferred no write")
            val other = TextExportController<String>({ out }, q); t = other.begin(other.newPage(), r)
            check(!c.admit(t, "foreign") && !c.admit(null, "null"), "foreign ticket refused"); other.revoke(t)
            for (stage in 0 until 3) blocked(stage)
            for (mode in 0 until 6) {
                val m = mode; val fq = Queue(); val closed = AtomicInteger()
                val f = TextExportController<String>({
                    when (m) {
                        0 -> throw IOException("secret-uri")
                        1 -> throw SecurityException("secret-path")
                        2 -> null
                        else -> object : OutputStream() {
                            override fun write(b: Int) { if (m == 3) throw IOException("secret-body"); if (m == 5) throw AssertionError("fatal") }
                            override fun close() { closed.incrementAndGet(); if (m == 4) throw IOException("secret-close") }
                        }
                    }
                }, fq)
                t = f.begin(f.newPage(), r); f.admit(t, "private-target"); var failure: Throwable? = null
                try { fq.run() } catch (e: Error) { failure = e }
                check((failure != null) == (m == 5), "fatal propagation")
                check(f.state().phase == TextExportController.Phase.FAILED && !f.state().busy(), "failure releases after cleanup")
                check(!TextExportController.statusText(f.state()).contains("secret"), "safe status")
                if (m >= 3) check(closed.get() == 1, "close even write failure/fatal")
            }
            val reject = TextExportController<String>({ out }, Executor { throw RejectedExecutionException() })
            t = reject.begin(reject.newPage(), r); check(!reject.admit(t, "target"), "executor rejects")
            check(!reject.state().busy() && reject.state().phase == TextExportController.Phase.FAILED, "rejection rollback")
            t = c.begin(page, r); c.revoke(t); val newer = c.begin(page, r)
            c.revoke(t); check(c.state().requestCode == newer!!.requestCode && c.state().busy(), "stale revoke isolated"); c.revoke(newer)
            val codes = TextExportController<String>({ out }, q); val id = codes.newPage()
            for (code in TextExportController.FIRST_CODE..TextExportController.LAST_CODE) {
                t = codes.begin(id, r); if (t == null || t.requestCode != code) throw AssertionError("code reuse"); codes.revoke(t)
            }
            check(codes.begin(id, r) == null, "exhaustion fails closed")
            r.setText(String(CharArray(50000)).replace("\u0000", "😀")); t = c.begin(page, r); check(t != null, "100000 UTF16 boundary accepted")
            c.admit(t, "target"); q.run(); check(c.state().bytes == 200000L, "supplementary UTF8 byte boundary")
            r.setText(String(CharArray(100001)).replace('\u0000', 'x')); check(c.begin(page, r) == null, "100000 char cap")
            r.setText("again"); val notices = AtomicInteger(); val reentrant = AtomicBoolean()
            val listener = TextExportController.Listener { notices.incrementAndGet(); val observed: Any? = c.state(); reentrant.set(observed != null) }
            val throwing = TextExportController.Listener { throw IllegalStateException("observer sentinel") }
            c.addListener(throwing); c.addListener(listener); t = c.begin(page, r); c.admit(t, "target"); q.run()
            check(notices.get() >= 3 && reentrant.get(), "completion notification/reentry")
            val count = notices.get(); c.removeListener(listener); t = c.begin(page, r); c.revoke(t); check(notices.get() == count, "listener detached")
            println("PASS TextExportTest: " + checks + " checks; full code exhaustion and open/write/close interleavings")
        }
        private fun blocked(stage: Int) {
            val reached = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
            val failure = AtomicReference<Throwable>(); val bytes = ByteArrayOutputStream(); val asr = AppState()
            asr.setLastText("私密 héllo"); asr.setLastStatus("asr-status"); val r = asr.resultState(); val owner = TaskCoordinator(Executor { it.run() })
            val reports = RequestRunnerTest.FakeReports()
            val runner = RequestRunner(owner, { 0 }, AppRequestPolicy(reports, object : AppRequestPolicy.State {
                override fun lastText(): String? = asr.lastText()
                override fun setLastText(s: String?) { asr.setLastText(s) }
                override fun lastStatus(): String? = asr.lastStatus()
                override fun setLastStatus(s: String?) { asr.setLastStatus(s) }
            }), {})
            val c = TextExportController<String>({
                if (stage == 0) { reached.countDown(); block(release) }
                object : OutputStream() {
                    override fun write(b: Int) { if (stage == 1) { reached.countDown(); block(release) }; bytes.write(b) }
                    override fun close() { if (stage == 2) { reached.countDown(); block(release) } }
                }
            }, Executor { job ->
                val th = Thread { try { job.run() } catch (e: Throwable) { failure.set(e) } finally { done.countDown() } }; th.isDaemon = true; th.start()
            })
            val page = TextExportPage(c); page.foreground(true); val t = page.begin(r); page.onResult(t!!.requestCode, "chosen")
            try {
                await(reached); check(c.state().phase == TextExportController.Phase.WRITING, "real write admission")
                check(runner.submit(TaskKind.MAINTENANCE) { asr.clearText(); asr.setLastStatus("clear-finished") }, "shared runner clear admitted")
                page.destroy(); check(c.state().busy(), "clear/destroy keep actual IO slot")
                check(runner.submit(TaskKind.INFERENCE) { asr.setLastText("next result"); asr.setLastStatus("new-inference-finished") }, "real policy/runner usable during IO stage " + stage)
                check(!owner.isBusy() && reports.log.size == 2, "inference reports and release completed")
                check(c.begin(c.newPage(), r) == null, "slot cannot reuse while IO blocks")
            } finally { release.countDown(); await(done) }
            check(failure.get() == null, "worker no failure"); check(c.state().phase == TextExportController.Phase.SUCCEEDED, "success after close")
            check(bytes.toString("UTF-8").equals(t.snapshot.text), "admitted snapshot finishes")
            check(r.currentText().equals("next result") && asr.lastStatus().equals("new-inference-finished"), "export cannot overwrite result or ASR status")
        }
    }
}
