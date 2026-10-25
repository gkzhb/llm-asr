import org.llmasr.minimal.ime.ImeController
import org.llmasr.minimal.ime.ImeFieldPolicy
import org.llmasr.minimal.ime.ImeSession
import org.llmasr.minimal.audio.RecordingControl
import org.llmasr.minimal.task.RequestContext
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.task.TaskKind
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Calls production IME controller/runner/gate; no Android fake implementation. */
class ImeSessionTest {
    private class Queue : Executor {
        var next: Runnable? = null
        override fun execute(command: Runnable?) { if (next != null) throw AssertionError("overlap"); next = command }
        fun run() { val r = next; next = null; r!!.run() }
    }
    private class Backend : ImeController.Backend {
        var captures = 0; var inference = 0; var cleanup = 0; var fail = false; var failCleanup = false
        var control: RecordingControl? = null; var request: RequestContext? = null
        var duringCapture = Runnable {}; var duringInference = Runnable {}; var duringCleanup = Runnable {}
        var audio = byteArrayOf(1, 2, 3)
        override fun capture(control: RecordingControl?, progress: ImeController.Progress?): ByteArray {
            captures++; this.control = control
            try {
                if (!control!!.start {}) throw CancellationException()
                duringCapture.run(); progress!!.update(1.0)
                if (fail) throw IOException("fake capture failure")
                return audio
            } finally { control!!.captureReleased() }
        }
        override fun transcribe(request: RequestContext?, wav: ByteArray?, language: String?): String {
            inference++; this.request = request
            check(request!!.kind == TaskKind.INFERENCE, "IME inference metadata, not maintenance bypass")
            check(wav === audio && language!!.equals("Chinese"), "fake capture byte reference/language forwarded")
            val receiver = request; val input = File.createTempFile("ime-test-", ".wav"); receiver.inputWav = input
            duringInference.run(); return "测试文本"
        }
        override fun cleanup(request: RequestContext?) {
            cleanup++; if (this.request != null) check(this.request === request, "same request cleaned")
            duringCleanup.run(); if (failCleanup) throw IOException("fake cleanup failure before deletion")
            if (request!!.inputWav != null) check(request.inputWav!!.delete(), "fake temporary removed")
        }
    }
    private class Fixture {
        val queue = Queue(); val backend = Backend(); val notifications = AtomicInteger(); val owner = TaskCoordinator(queue)
        init { owner.addListener { notifications.incrementAndGet() } }
        val c = ImeController(owner, backend, { notifications.incrementAndGet() })
        var s: ImeSession? = c.begin(1, 0, "editor")
        fun complete() { check(c.start(s, "Chinese"), "admitted"); queue.run() }
    }
    companion object {
        private var checks = 0
        private fun check(value: Boolean, label: String) { checks++; if (!value) throw AssertionError(label) }
        private fun externalExitRetainsOwner() {
            for (stage in arrayOf("capture", "native", "cleanup", "late-failure")) {
                val entered = CountDownLatch(1); val release = CountDownLatch(1)
                val failure = AtomicReference<Throwable>(); val worker = AtomicReference<Thread>()
                val activeControl = AtomicReference<RecordingControl>(); val events = ArrayList<String>()
                val owner = TaskCoordinator(Executor { task ->
                    val thread = Thread { try { task.run() } catch (t: Throwable) { failure.set(t) } }
                    worker.set(thread); thread.start()
                })
                fun block() {
                    entered.countDown()
                    if (!release.await(5, TimeUnit.SECONDS)) throw AssertionError("external exit worker deadline")
                }
                val backend = object : ImeController.Backend {
                    override fun capture(control: RecordingControl?, progress: ImeController.Progress?): ByteArray {
                        activeControl.set(control)
                        control!!.start {}; try { if (stage == "capture") block() }
                        finally { control.captureReleased() }; return byteArrayOf(1)
                    }
                    override fun transcribe(request: RequestContext?, wav: ByteArray?, language: String?): String {
                        if (stage == "native" || stage == "late-failure") block()
                        if (stage == "late-failure") throw IOException("synthetic late failure")
                        return "late preview"
                    }
                    override fun cleanup(request: RequestContext?) { if (stage == "cleanup") block() }
                }
                val c = ImeController(owner, backend, {}); val old = c.begin(1, 0, "old")
                try {
                    check(c.start(old, "Chinese"), "external exit admitted " + stage)
                    check(entered.await(5, TimeUnit.SECONDS), "external exit entered " + stage)
                    var invalidAtHide = false; var cancelledAtHide = false
                    c.leaveForExternalUi({
                        invalidAtHide = !old!!.valid() && old.preview().isEmpty()
                        cancelledAtHide = activeControl.get().cancelled(); events.add("hide")
                        throw IllegalStateException("hide sentinel")
                    }, { events.add("launch"); throw SecurityException("launch sentinel") })
                    check(invalidAtHide, "external exit invalidates before hide " + stage)
                    check(cancelledAtHide == (stage == "capture"), "cancel gate preserves committed native " + stage)
                    check(events == listOf("hide", "launch"), "hide failure must not suppress external attempt")
                    val fresh = c.begin(1, 0, "fresh"); val initialStatus = fresh!!.status()
                    check(owner.isBusy() && c.ownsTask() && !c.start(fresh, "Chinese") && !owner.submit {},
                        "external exit retains real owner through " + stage)
                    release.countDown(); worker.get().join(5000)
                    check(!worker.get().isAlive && failure.get() == null, "actual external worker terminal " + stage)
                    check(!owner.isBusy() && !c.ownsTask(), "actual completion alone frees owner " + stage)
                    check(!old!!.valid() && old.preview().isEmpty() && fresh.preview().isEmpty() && fresh.status() == initialStatus,
                        "late success/failure/cleanup cannot revive preview " + stage)
                } finally {
                    release.countDown(); worker.get()?.join(5000)
                    check(worker.get()?.isAlive != true, "external exit finally joins actual worker " + stage)
                }
            }
            val queued = Fixture(); val order = ArrayList<String>()
            check(queued.c.start(queued.s, "Chinese"), "external queued admitted")
            queued.c.leaveForExternalUi({ order.add("hide"); throw IllegalStateException() }, { order.add("launch") })
            check(queued.owner.isBusy() && queued.c.ownsTask(), "queued external exit retains owner until actual worker")
            queued.queue.run()
            check(queued.backend.captures == 0 && queued.backend.inference == 0 && queued.backend.cleanup == 1 && !queued.owner.isBusy(),
                "queued external cancellation prevents capture/native and cleans")
            check(order == listOf("hide", "launch"), "queued hide failure still launches")
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            for (t in intArrayOf(0, 0x1000, 0x81, 0x91, 0xe1, 0x12, 0x10081)) check(ImeFieldPolicy.isSensitive(t, 0), "sensitive " + t)
            for (t in intArrayOf(1, 2, 3, 4, 0x21, 0x20001)) check(!ImeFieldPolicy.isSensitive(t, 0), "ordinary " + t)
            check(ImeFieldPolicy.isSensitive(1, 0x01000000), "private flag")
            val f = Fixture(); f.complete(); check(f.backend.inference == 1 && f.backend.cleanup == 1, "full capture inference cleanup")
            check(f.s!!.preview().equals("测试文本"), "real pipeline preview")
            val commits = AtomicInteger()
            check(f.c.commit(f.s, "editor", 1, 0) { text -> check(text!!.equals("测试文本"), "exact commit text"); commits.incrementAndGet(); true }, "explicit commit")
            check(f.s!!.preview().isEmpty(), "consume clears preview")
            check(!f.c.commit(f.s, "editor", 1, 0) { commits.incrementAndGet(); true } && commits.get() == 1, "once only")
            f.complete(); check(!f.s!!.preview().isEmpty(), "second utterance same editor")
            check(!f.c.commit(f.s, "other", 1, 0) { true }, "wrong field denied")
            check(!f.c.commit(f.s, "editor", 0x91, 0) { true }, "sensitive at commit denied")
            check(!f.c.commit(f.s, "editor", 1, 0, null), "null connection")
            check(!f.c.commit(f.s, "editor", 1, 0) { true }, "null failure consumed"); f.complete()
            check(!f.c.commit(f.s, "editor", 1, 0) { throw IllegalStateException() }, "throwing connection caught")
            check(f.s!!.preview().isEmpty(), "exception not retried"); f.complete()
            check(!f.c.commit(f.s, "editor", 1, 0) { false }, "rejected connection")
            check(!f.c.commit(f.s, "editor", 1, 0) { true }, "rejected not retried")
            f.complete(); val old = f.s; f.s = f.c.begin(1, 0, "editor")
            check(!old!!.valid() && old.preview().isEmpty(), "same-field restart invalidation")
            check(!f.c.commit(old, "editor", 1, 0) { true }, "old commit rejected")
            check(f.c.begin(0x81, 0, "secret") == null && !f.s!!.valid(), "sensitive begin clears previous")
            val queued = Fixture(); check(queued.c.start(queued.s, "Chinese"), "queue accepted"); queued.c.invalidate(); queued.queue.run()
            check(queued.backend.captures == 0 && queued.backend.inference == 0 && queued.backend.cleanup == 1, "cancel before worker no capture/infer")
            check(!queued.owner.isBusy(), "queued owner released")
            val handoff = Fixture(); handoff.backend.duringCapture = Runnable { handoff.c.invalidate() }; handoff.complete()
            check(handoff.backend.inference == 0 && handoff.backend.control!!.cancelled(), "handoff cancel prevents native")
            val late = Fixture()
            late.backend.duringInference = Runnable {
                check(late.owner.isBusy(), "owner held throughout native"); late.c.invalidate(); val next = late.c.begin(1, 0, "next")
                check(!late.c.start(next, "Chinese"), "cannot start while invalid native still running"); check(next!!.preview().isEmpty(), "new session starts empty")
            }
            late.complete(); check(late.s!!.preview().isEmpty() && !late.s!!.valid(), "late result discarded"); check(!late.owner.isBusy(), "native completion frees owner")
            val busy = Fixture(); busy.owner.submit {}; check(!busy.c.start(busy.s, "Chinese") && busy.backend.captures == 0, "App owner excludes IME"); busy.queue.run()
            check(busy.c.start(busy.s, "Chinese"), "IME gets owner"); check(!busy.owner.submit { throw AssertionError() }, "IME excludes App"); busy.queue.run()
            val failed = Fixture(); failed.backend.fail = true; failed.complete()
            check(failed.backend.cleanup == 1 && failed.backend.inference == 0 && failed.s!!.preview().isEmpty(), "capture failure cleanup no result")
            check(failed.s!!.status().contains("失败") && !failed.owner.isBusy(), "failure status owner release")
            val cleanup = Fixture(); cleanup.backend.failCleanup = true; cleanup.complete()
            check(cleanup.s!!.status().contains("清理失败") && !cleanup.owner.isBusy(), "cleanup failure surfaced")
            check(cleanup.backend.request!!.inputWav!!.exists(), "failed fake cleanup retains file")
            check(cleanup.backend.request!!.inputWav!!.delete(), "test teardown removes retained fake WAV")
            val staleError = Fixture(); val fresh = arrayOfNulls<ImeSession>(1)
            staleError.backend.duringCapture = Runnable { fresh[0] = staleError.c.begin(1, 0, "fresh") }; staleError.backend.fail = true; staleError.complete()
            check(fresh[0]!!.preview().isEmpty() && !fresh[0]!!.status().contains("失败"), "stale failure cannot overwrite new session")
            val stopped = Fixture(); stopped.backend.duringCapture = Runnable { stopped.c.stop() }; stopped.complete()
            check(stopped.backend.control!!.stopped() && stopped.backend.inference == 1, "stop hands off to inference")
            for (mode in 0 until 4) {
                val picker = Fixture(); if (mode == 1) picker.complete()
                val leave = Runnable {
                    val order = StringBuilder()
                    picker.c.leaveForExternalUi({ check(!picker.s!!.valid() && picker.s!!.preview().isEmpty(), "invalidate before hide"); order.append("hide") }, {
                        check(order.toString().equals("hide"), "hide before external launch"); order.append("/picker")
                    })
                    check(order.toString().equals("hide/picker"), "external command sequence")
                }
                if (mode == 2) { picker.backend.duringCapture = leave; picker.complete() }
                else if (mode == 3) { picker.backend.duringInference = leave; picker.complete() } else leave.run()
                check(picker.s!!.preview().isEmpty(), "external UI clears old preview/late result")
                val captures = picker.backend.captures; val returned = picker.c.begin(1, 0, "editor")
                check(returned!!.valid() && returned.preview().isEmpty(), "return creates fresh eligible session")
                check(picker.backend.captures == captures, "return never auto records")
                check(picker.c.start(returned, "Chinese"), "manual record after fresh return"); picker.queue.run()
            }
            val failedLaunch = Fixture(); failedLaunch.complete(); failedLaunch.c.leaveForExternalUi({}, { throw IllegalStateException() })
            check(!failedLaunch.s!!.valid() && failedLaunch.s!!.preview().isEmpty(), "failed external launch keeps old session invalid")
            val notices = AtomicInteger(); val reject = TaskCoordinator(Executor { throw RejectedExecutionException() })
            reject.addListener { notices.incrementAndGet() }; val rejected = ImeController(reject, Backend(), { notices.incrementAndGet() }); val rs = rejected.begin(1, 0, "r")
            check(!rejected.start(rs, "Chinese") && !reject.isBusy() && !rejected.capturing(), "executor rejection rollback")
            val entered = CountDownLatch(1); val resume = CountDownLatch(1); val threadedBackend = Backend()
            val threadedOwner = TaskCoordinator(Executor { Thread(it).start() }); val threaded = ImeController(threadedOwner, threadedBackend, {})
            val ts = threaded.begin(1, 0, "t")
            threadedBackend.duringInference = Runnable {
                entered.countDown(); try { if (!resume.await(5, TimeUnit.SECONDS)) throw AssertionError("timeout") }
                catch (e: InterruptedException) { throw AssertionError(e) }
            }
            check(threaded.start(ts, "Chinese"), "threaded admitted"); check(entered.await(5, TimeUnit.SECONDS), "native reached")
            threaded.invalidate(); check(threadedOwner.isBusy(), "invalidate does not release owner"); resume.countDown(); threadedOwner.awaitFree()
            check(ts!!.preview().isEmpty() && !ts.valid(), "threaded late native dropped")
            val cleaning = CountDownLatch(1); val releaseCleanup = CountDownLatch(1); val cb = Backend(); val co = TaskCoordinator(Executor { Thread(it).start() })
            val cc = ImeController(co, cb, {}); val cs = cc.begin(1, 0, "cleanup")
            cb.duringCleanup = Runnable {
                cleaning.countDown(); try { if (!releaseCleanup.await(5, TimeUnit.SECONDS)) throw AssertionError("cleanup timeout") }
                catch (e: InterruptedException) { throw AssertionError(e) }
            }
            check(cc.start(cs, "Chinese"), "cleanup task admitted"); check(cleaning.await(5, TimeUnit.SECONDS), "cleanup reached")
            check(co.isBusy() && !co.submit {}, "blocked cleanup retains exclusive owner")
            releaseCleanup.countDown(); co.awaitFree(); check(!co.isBusy(), "cleanup completion releases owner")
            externalExitRetainsOwner()
            println("PASS " + checks + " IME production controller checks (not Android hardware/InputConnection tests)")
        }
    }
}
