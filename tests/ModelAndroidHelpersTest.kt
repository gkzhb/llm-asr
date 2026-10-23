import org.llmasr.minimal.model.*
import org.llmasr.minimal.modelmanagement.*
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.transcription.TextExportController
import java.io.*
import java.nio.file.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Real production page policy/owner/controller/repository/lazy SHA; not Android runtime. */
class ModelAndroidHelpersTest {
    private class Queue : Executor {
        var task: Runnable? = null
        override fun execute(command: Runnable?) { if (task != null) throw AssertionError("duplicate task"); task = command }
        fun run() { val r = task; task = null; r!!.run() }
    }
    private class Fixture : AutoCloseable {
        val queue = Queue(); val releases = AtomicInteger(); val owner = TaskCoordinator(queue)
        init { owner.addListener { releases.incrementAndGet() } }
        val ready = ModelReadiness(); val state = ModelManagementState(); val control = ModelOperationControl(); val tickets = ModelPickerTickets()
        val dir = Files.createTempDirectory("model-android-policy-").toFile()
        val repo = ModelRepository(ModelRepositoryCancelTest.manifest(DATA, "a", "b"), dir, { 123456789 })
        val controller = ModelManagementController(owner, repo, ready, control, state)
        fun source(): ModelSource = ModelRepositoryCancelTest.Source(DATA, "a", "b")
        fun page(id: String?): ModelPageSession<ModelSource> = ModelPageSession(tickets, controller, id)
        fun install() { for (n in arrayOf("a", "b")) Files.write(File(dir, n).toPath(), DATA) }
        override fun close() {
            if (queue.task != null) queue.run()
            if (!dir.exists()) return
            val paths = Files.walk(dir.toPath()); var primary: Throwable? = null
            try { val it = paths.sorted(Comparator.reverseOrder()).iterator(); while (it.hasNext()) Files.deleteIfExists(it.next()) }
            catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) paths.close() else try { paths.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
    }
    companion object {
        private var checks = 0; private val DATA = byteArrayOf(1, 2, 3)
        private fun check(b: Boolean) { checks++; if (!b) throw AssertionError("check " + checks) }
        private inline fun fixture(body: (Fixture) -> Unit) {
            val f = Fixture(); var primary: Throwable? = null
            try { body(f) } catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) f.close() else try { f.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            fixture { f ->
                val p = f.page(null); p.foreground(true); val code = p.beginPicker(); check(!f.owner.isBusy()); check(!TextExportController.isRequest(code)); check(code != 11 && code != 12)
                p.foreground(false); check(p.result(code, f.source())); check(p.hasDeferred()); check(!p.consume(f.controller::startImport)); check(!f.owner.isBusy())
                check(!p.result(code, f.source())); p.foreground(true); check(p.consume(f.controller::startImport)); check(!p.hasDeferred()); check(f.owner.isBusy()); check(p.ownsActive())
                check(!p.consume(f.controller::startImport)); check(!p.result(code, f.source())); f.queue.run(); check(f.ready.isReady); check(!f.owner.isBusy()); p.destroy()
            }
            fixture { f ->
                val old = f.page(null); old.foreground(true); val code = old.beginPicker(); val fresh = f.page(null); fresh.foreground(true); check(!fresh.result(code, f.source()))
                try { fresh.beginPicker(); throw AssertionError() } catch (expected: IllegalStateException) { check(true) }
                old.destroy(); val next = fresh.beginPicker(); check(next != code); old.destroy(); check(fresh.result(next, f.source())); check(fresh.consume(f.controller::startImport)); f.queue.run()
                check(!old.result(code, f.source())); fresh.destroy()
            }
            fixture { f ->
                f.install(); val access = ModelAccess(f.repo, f.ready); check(f.owner.submit { access.requireReady() }); f.queue.run(); check(f.ready.isReady); val epoch = f.ready.epoch()
                val p = f.page(null); p.foreground(true); val code = p.beginPicker(); check(p.result(code, null)); check(!p.consume(f.controller::startImport)); check(f.ready.isReady); check(f.ready.epoch() == epoch)
                val next = p.beginPicker(); check(f.owner.submit {}); check(p.result(next, f.source())); check(!p.consume(f.controller::startImport)); check(!p.hasDeferred()); f.queue.run()
                check(!p.consume(f.controller::startImport)); check(f.ready.isReady); p.destroy()
            }
            fixture { f ->
                val old = f.page(null); old.foreground(true); check(f.controller.startImport(f.source())); old.adoptStarted(); val id = old.ownedId()
                old.foreground(false); check(!f.control.peekActive().isCancelRequested); old.stopped(true); old.destroy(); check(!f.control.peekActive().isCancelRequested)
                val stranger = f.page(null); stranger.stopped(false); check(!f.control.peekActive().isCancelRequested)
                val fresh = f.page(id); check(fresh.ownsActive()); check(fresh.cancellable()); fresh.stopped(false)
                check(f.control.peekActive().isCancelRequested); check(f.owner.isBusy()); check(!fresh.cancellable()); f.queue.run()
                check(f.state.current().phase == ModelManagementState.Phase.CANCELLED); check(!f.owner.isBusy()); fresh.destroy(); stranger.destroy()
            }
            fixture { f ->
                f.install(); val p = f.page(null); val epoch = f.ready.epoch(); f.ready.invalidate(); check(!f.controller.startDelete(epoch, true)); check(!f.owner.isBusy())
                check(f.controller.startDelete(f.ready.epoch(), true)); p.adoptStarted(); check(!p.cancellable()); p.stopped(false)
                check(!f.control.peekActive().isCancelRequested); check(f.owner.isBusy()); f.queue.run(); check(f.ready.state() == ModelReadiness.State.NOT_INSTALLED); check(f.state.current().deleteFailed == 0); p.destroy()
            }
            fixture { f ->
                f.install(); val hashCalls = AtomicInteger(); val notifications = AtomicInteger()
                val repo = object : ModelRepository(f.repo.manifest(), f.dir, { 123456789 }) { override fun verifyAll(p: Progress?, c: CancelGate?) { hashCalls.incrementAndGet(); super.verifyAll(p, c) } }
                val listener = ModelReadiness.Listener { notifications.incrementAndGet() }; f.ready.addListener(listener); val access = ModelAccess(repo, f.ready)
                f.ready.markVerified(null); check(!f.ready.isReady); check(f.owner.submit { access.requireReady() }); f.queue.run(); check(hashCalls.get() == 1); check(f.ready.isReady)
                check(f.owner.submit { access.requireReady() }); f.queue.run(); check(hashCalls.get() == 1)
                f.ready.invalidate(); check(!f.ready.isReady); Files.write(File(f.dir, "a").toPath(), byteArrayOf(9, 9, 9)); val failed = AtomicBoolean(); val before = notifications.get()
                check(f.owner.submit { try { access.requireReady() } catch (e: IOException) { failed.set(true); check(e.message!!.contains("模型管理")) } }); f.queue.run()
                check(failed.get()); check(!f.ready.isReady); check(f.ready.state() == ModelReadiness.State.INVALID); check(notifications.get() > before); f.ready.removeListener(listener)
            }
            fixture { f ->
                f.install(); val continuation = AtomicBoolean(); val failures = AtomicInteger()
                val repo = object : ModelRepository(f.repo.manifest(), f.dir, { 123456789 }) {
                    override fun verifyAll(p: Progress?, c: CancelGate?) { check(f.ready.state() == ModelReadiness.State.VERIFYING); super.verifyAll(p, c); f.ready.invalidate() }
                }
                val access = ModelAccess(repo, f.ready); check(f.owner.submit { try { access.requireReady(); continuation.set(true) } catch (e: IOException) { failures.incrementAndGet() } }); f.queue.run()
                check(!continuation.get()); check(failures.get() == 1); check(!f.ready.isReady)
            }
            fixture { f ->
                f.install(); val entered = CountDownLatch(1); val release = CountDownLatch(1); val fault = AtomicReference<Throwable>()
                val repo = object : ModelRepository(f.repo.manifest(), f.dir, { 123456789 }) {
                    override fun verifyAll(p: Progress?, c: CancelGate?) {
                        entered.countDown(); try { if (!release.await(3, TimeUnit.SECONDS)) throw IOException("gate timeout") }
                        catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException(e) }; super.verifyAll(p, c)
                    }
                }
                val access = ModelAccess(repo, f.ready); check(f.owner.submit { access.requireReady() })
                val worker = Thread({ try { f.queue.run() } catch (t: Throwable) { fault.set(t) } }, "lazy-test-worker"); worker.start()
                try { check(entered.await(2, TimeUnit.SECONDS)); check(f.owner.isBusy()); check(!f.controller.startDelete(f.ready.epoch(), true)); check(!f.controller.startVerify()) }
                finally { release.countDown(); worker.join(4000) }
                check(!worker.isAlive); check(fault.get() == null); check(f.ready.isReady); check(!f.owner.isBusy())
            }
            fixture { f ->
                val access = ModelAccess(f.repo, f.ready); val notifications = AtomicInteger(); val listener = ModelReadiness.Listener { notifications.incrementAndGet() }; f.ready.addListener(listener)
                check(f.owner.submit { try { access.requireReady(); throw AssertionError() } catch (expected: IOException) { check(true) } }); f.queue.run()
                check(f.ready.state() == ModelReadiness.State.NOT_INSTALLED); check(notifications.get() >= 2); check(!f.owner.isBusy())
            }
            fixture { f ->
                val poison = object : File(f.dir, "not-statted-at-construction") {
                    override fun getCanonicalPath(): String { throw AssertionError("constructor IO") }
                    override fun exists(): Boolean { throw AssertionError("constructor IO") }
                }
                check(ModelRepository.forWorker(f.repo.manifest(), poison, { 0 }).modelDir() === poison)
                val link = File(f.dir, "linked"); val outside = Files.createTempDirectory("model-root-boundary-").toFile()
                try {
                    Files.createSymbolicLink(link.toPath(), outside.toPath()); val deferred = ModelRepository.forWorker(f.repo.manifest(), link, { 0 })
                    try { deferred.inspect(ModelRepository.NEVER_CANCEL); throw AssertionError() } catch (expected: IOException) { check(true) }
                    try { deferred.verifyAll(); throw AssertionError() } catch (expected: IOException) { check(true) }
                    try { deferred.importFrom(f.source(), null); throw AssertionError() } catch (expected: IOException) { check(true) }
                    try { deferred.deleteAll(); throw AssertionError() } catch (expected: IOException) { check(true) }; check(outside.list()!!.size == 0)
                } finally { Files.deleteIfExists(link.toPath()); Files.deleteIfExists(outside.toPath()) }
            }
            val tickets = ModelPickerTickets()
            for (code in ModelPickerTickets.FIRST..ModelPickerTickets.LAST) {
                val t = tickets.begin(); if (t.requestCode != code || TextExportController.isRequest(code)) throw AssertionError(); tickets.release(t)
            }
            try { tickets.begin(); throw AssertionError() } catch (expected: IllegalStateException) { check(true) }
            check(ModelUiText.bytes(-1).contains("未知")); check(!ModelUiText.bytes(0).contains("未知")); check(ModelUiText.readiness(ModelReadiness.State.UNVERIFIED).contains("不等于"))
            check(ModelUiText.operation(ModelManagementState.OperationKind.INSPECT).contains("不进行 SHA")); check(ModelUiText.phase(ModelManagementState.Phase.COPYING).contains("仍需"))
            check(!ModelUiText.safeError("open /data/user/0/private").contains("/data")); check(!ModelUiText.safeError("content://provider/private").contains("content:"))
            check(ModelUiText.safeError("正在取消，等待文件读取/清理结束").contains("正在取消"))
            println("PASS Android production policy/lazy helpers " + checks + " checks (host, not Android runtime)")
        }
    }
}
