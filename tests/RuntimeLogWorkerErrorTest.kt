package org.llmasr.minimal.diagnostics

import java.io.File
import java.io.IOException
import java.nio.file.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Real worker fatal recovery/owner handoff. Real filesystem and original bounded teardown. */
class RuntimeLogWorkerErrorTest {
    private class ManualExecutor : Executor {
        val queue: Deque<Runnable?> = ArrayDeque(); var submissions = 0
        @Synchronized override fun execute(command: Runnable?) { submissions++; queue.add(command) }
        @Synchronized fun pending(): Int = queue.size
        fun runOne() { val task = synchronized(this) { queue.remove() }; task!!.run() }
    }
    private class RunCleanup {
        val unconfirmed = ArrayList<Path>()
        fun delete(root: Path, original: Throwable?) {
            try {
                for (dir in unconfirmed) if (dir.startsWith(root) || root.startsWith(dir)) throw AssertionError("cleanup refused; worker exit unconfirmed; directory preserved: " + dir)
                deleteRecursive(root)
            } catch (failure: Throwable) {
                if (failure !is IOException && failure !is RuntimeException && failure !is Error) throw failure
                if (original == null) throw failure
                (original as java.lang.Throwable).addSuppressed(failure)
            }
        }
    }
    private class ThreadScope(val cleanup: RunCleanup, val dir: Path) {
        val threads = ArrayList<Thread>(); val gates = ArrayList<CountDownLatch>()
        fun start(runnable: Runnable): Thread { val thread = Thread(runnable); threads.add(thread); thread.start(); return thread }
        fun addGate(gate: CountDownLatch) { gates.add(gate) }
        fun releaseAll() { for (gate in gates) gate.countDown() }
        fun awaitAll(budgetNanos: Long): Boolean {
            val deadline = System.nanoTime() + budgetNanos; var interrupted = Thread.currentThread().isInterrupted
            for (thread in threads) while (thread.isAlive && !interrupted) {
                val remaining = deadline - System.nanoTime(); if (remaining <= 0) break
                try { thread.join(remaining / 1000000, (remaining % 1000000).toInt()) }
                catch (stop: InterruptedException) { Thread.currentThread().interrupt(); interrupted = true }
            }
            for (thread in threads) if (thread.isAlive) return false
            return true
        }
    }
    companion object {
        private const val TEARDOWN_BUDGET_MS = 5000L
        private var checks = 0
        private fun check(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
        private fun append(store: RuntimeLogStore): Boolean = store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok")
        private fun await(latch: CountDownLatch) { if (!latch.await(5, TimeUnit.SECONDS)) throw AssertionError("latch timeout") }
        private fun awaitGate(latch: CountDownLatch) { latch.await() }
        private fun join(thread: Thread) { thread.join(5000); check(!thread.isAlive, "thread timeout") }
        private fun waitBlocked(thread: Thread) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (thread.state != Thread.State.BLOCKED && System.nanoTime() < until) Thread.sleep(1)
            check(thread.state == Thread.State.BLOCKED, "worker not blocked on the persistence monitor")
        }
        private fun deleteRecursive(root: Path) {
            val paths = Files.walk(root); var primary: Throwable? = null
            try { val it = paths.sorted(Comparator.reverseOrder()).iterator(); while (it.hasNext()) Files.deleteIfExists(it.next()) }
            catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) paths.close() else try { paths.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        private fun finishScope(scope: ThreadScope, original: Throwable?) = finishScope(scope, original, TimeUnit.MILLISECONDS.toNanos(TEARDOWN_BUDGET_MS))
        private fun finishScope(scope: ThreadScope, original: Throwable?, budgetNanos: Long) {
            scope.releaseAll()
            if (scope.awaitAll(budgetNanos)) scope.cleanup.unconfirmed.remove(scope.dir)
            else {
                if (!scope.cleanup.unconfirmed.contains(scope.dir)) scope.cleanup.unconfirmed.add(scope.dir)
                val failure = AssertionError("worker not confirmed exited within the bounded teardown budget; directory preserved: " + scope.dir)
                if (original == null) throw failure
                (original as java.lang.Throwable).addSuppressed(failure)
            }
        }
        private fun loadPathFatal(base: Path) {
            val dir = Files.createDirectory(base.resolve("load-dir")); Files.createDirectory(dir.resolve("files"))
            val injected = AssertionError("load-path"); val fault = AtomicBoolean(true)
            val filesDir = object : File(dir.toFile(), "files") { override fun getCanonicalFile(): File { if (fault.get()) throw injected; return super.getCanonicalFile() } }
            val persistence = RuntimeLogPersistence(filesDir); val store = RuntimeLogStore(); val executor = ManualExecutor(); val worker = RuntimeLogWorker(store, persistence, null, executor)
            check(executor.pending() == 1, "constructor schedules recovery"); var seen: Error? = null
            try { executor.runOne() } catch (e: Error) { seen = e }
            check(seen === injected, "load-path fatal identity preserved"); check(append(store), "append after load-path fatal")
            check(executor.pending() == 1, "append after drain fatal must schedule recovery, pending=" + executor.pending())
            fault.set(false); executor.runOne(); check(persistence.load().equals(store.snapshot()), "persisted after load-path fatal recovery")
            check(worker.successfulSaves() >= 1, "successful save after recovery"); worker.close(); check(!worker.schedulePersist(), "closed rejects new work after fatal recovery")
        }
        private fun restorePathFatal(base: Path) {
            val root = Files.createDirectory(base.resolve("restore-dir")); val persistence = RuntimeLogPersistence(root.toFile()); val store = RuntimeLogStore()
            val injected = AssertionError("restore-path"); val bad = RuntimeLogSink { _, _ -> throw injected }; store.addSink(bad)
            val executor = ManualExecutor(); val worker = RuntimeLogWorker(store, persistence, null, executor)
            check(executor.pending() == 1, "constructor schedules recovery"); var seen: Error? = null
            try { executor.runOne() } catch (e: Error) { seen = e }; check(seen === injected, "restore-path fatal identity preserved")
            store.removeSink(bad); check(append(store), "append after restore-path fatal")
            check(executor.pending() == 1, "append after drain fatal must schedule recovery, pending=" + executor.pending())
            executor.runOne(); check(persistence.load().equals(store.snapshot()), "persisted after restore-path fatal recovery"); check(worker.successfulSaves() >= 1, "successful save after recovery"); worker.close()
        }
        private fun failureCallbackFatal(base: Path) {
            val root = Files.createDirectory(base.resolve("callback-dir")); val persistence = RuntimeLogPersistence(root.toFile()); val store = RuntimeLogStore()
            val injected = AssertionError("failure-sink"); val armed = AtomicBoolean(true); val callbacks = AtomicInteger(); val executor = ManualExecutor()
            val worker = RuntimeLogWorker(store, persistence, { callbacks.incrementAndGet(); if (armed.get()) throw injected }, executor)
            check(executor.pending() == 1, "constructor schedules recovery"); executor.runOne(); check(worker.successfulSaves() == 1L && worker.lastFailure() == null, "clean first drain")
            Files.createDirectory(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part")); check(append(store), "append before failure-callback fatal")
            check(executor.pending() == 1, "append schedules the failing drain"); var seen: Error? = null
            try { executor.runOne() } catch (e: Error) { seen = e }; check(seen === injected, "failure-callback fatal identity preserved")
            check(worker.failedSaves() == 1L && "unsafe-path".equals(worker.lastFailure()), "fatal recorded with the fixed label before the callback")
            armed.set(false); Files.delete(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part")); check(append(store), "append after failure-callback fatal")
            check(executor.pending() == 1, "append after drain fatal must schedule recovery, pending=" + executor.pending())
            executor.runOne(); check(persistence.load().equals(store.snapshot()), "persisted after failure-callback fatal recovery")
            check(worker.successfulSaves() == 2L && worker.lastFailure() == null, "failure state cleared after a successful replacement save")
            check(callbacks.get() == 1, "single failure callback episode"); worker.close()
        }
        private fun savePathFatal(base: Path) {
            val dir = Files.createDirectory(base.resolve("save-dir")); Files.createDirectory(dir.resolve("files")); val injected = AssertionError("save-path"); val canonicalCalls = AtomicInteger()
            val filesDir = object : File(dir.toFile(), "files") { override fun getCanonicalFile(): File { val call = canonicalCalls.incrementAndGet(); if (call == 2) throw injected; return super.getCanonicalFile() } }
            val persistence = RuntimeLogPersistence(filesDir); val store = RuntimeLogStore(); append(store); val executor = ManualExecutor(); val worker = RuntimeLogWorker(store, persistence, null, executor)
            check(executor.pending() == 1, "constructor schedules recovery"); var seen: Error? = null
            try { executor.runOne() } catch (e: Error) { seen = e }; check(seen === injected, "save-path fatal identity preserved")
            check(!Files.exists(persistence.file().toPath()), "no partial write on fatal save"); check(append(store), "append after save-path fatal")
            check(executor.pending() == 1, "append after drain fatal must schedule recovery, pending=" + executor.pending()); executor.runOne()
            check(persistence.load().equals(store.snapshot()), "persisted after save-path fatal recovery"); check(worker.successfulSaves() == 1L, "successful save after recovery"); worker.close()
        }
        private fun executeFatal(base: Path) {
            val root = Files.createDirectory(base.resolve("execute-dir")); val persistence = RuntimeLogPersistence(root.toFile()); val store = RuntimeLogStore()
            val injected = AssertionError("execute-path"); val armed = AtomicBoolean(false); val executor = ManualExecutor()
            val worker = RuntimeLogWorker(store, persistence, null, Executor { task -> if (armed.get()) throw injected; executor.execute(task) })
            executor.runOne(); armed.set(true); var seen: Error? = null
            try { append(store) } catch (e: Error) { seen = e }; check(seen === injected, "execute fatal identity propagated to the publisher")
            check(worker.droppedSchedules() == 0L && worker.lastFailure() == null, "fatal is not a queue-full rejection and is not counted as dropped")
            armed.set(false); check(append(store), "append after execute fatal"); check(executor.pending() == 1, "append after execute fatal must schedule recovery, pending=" + executor.pending())
            executor.runOne(); check(persistence.load().equals(store.snapshot()), "persisted after execute fatal recovery"); check(worker.successfulSaves() == 2L, "constructor drain and recovery drain both saved"); worker.close()
        }
        private fun staleOuterReleaseFatal(base: Path, cleanup: RunCleanup) {
            val dir = Files.createDirectory(base.resolve("interleave-dir")); Files.createDirectory(dir.resolve("files")); val injected = AssertionError("interleave"); val fault = AtomicBoolean(false)
            val filesDir = object : File(dir.toFile(), "files") { override fun getCanonicalFile(): File { if (fault.get()) throw injected; return super.getCanonicalFile() } }
            val persistence = RuntimeLogPersistence(filesDir); val store = RuntimeLogStore(); val submissions = AtomicInteger(); val innerReturned = CountDownLatch(1); val windowOpen = CountDownLatch(1)
            val t1Problem = AtomicReference<Throwable>(); val t2Problem = AtomicReference<Throwable>(); val t3Problem = AtomicReference<Throwable>(); val t3Returned = AtomicBoolean()
            val interleave = Executor { task ->
                try { task.run() } catch (fatal: Error) {
                    innerReturned.countDown(); try { awaitGate(windowOpen) } catch (stop: InterruptedException) { throw AssertionError(stop) }; throw fatal
                }
            }
            val counting = Executor { task -> submissions.incrementAndGet(); interleave.execute(task) }; val scope = ThreadScope(cleanup, dir); scope.addGate(windowOpen); var original: Throwable? = null
            try {
                val worker = RuntimeLogWorker(store, persistence, null, counting); check(submissions.get() == 1 && worker.successfulSaves() == 1L, "constructor admission and save"); fault.set(true)
                val t1 = scope.start { try { check(worker.schedulePersist(), "T1 admitted") } catch (problem: Throwable) { t1Problem.set(problem) } }
                await(innerReturned); fault.set(false); var t2: Thread? = null; var t3: Thread? = null
                synchronized(persistence) {
                    t2 = scope.start { try { t2Problem.set(if (worker.schedulePersist()) null else AssertionError("T2 not admitted")) } catch (problem: Throwable) { t2Problem.set(problem) } }
                    waitBlocked(t2!!); check(submissions.get() == 3, "successor drain admitted while the stale frame paused, submissions=" + submissions.get())
                    windowOpen.countDown(); join(t1); check(t1Problem.get() === injected, "same fatal identity reached the outer execute frame")
                    t3 = scope.start { try { t3Returned.set(worker.schedulePersist()) } catch (problem: Throwable) { t3Problem.set(problem) } }
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (submissions.get() < 4 && !t3Returned.get() && System.nanoTime() < until) Thread.sleep(1)
                    check(submissions.get() == 3, "third schedule must not admit a new drain while the successor owns the slot, submissions=" + submissions.get())
                    check(t3Returned.get() && t3Problem.get() == null, "third schedule only marks dirty and returns")
                }
                join(t2!!); check(t2Problem.get() == null, "successor drain completed: " + t2Problem.get()); check(t3Returned.get() && t3Problem.get() == null, "third schedule returned cleanly")
                check(worker.successfulSaves() >= 3, "successor drains committed their saves"); append(store); check(submissions.get() == 4, "later append admitted a fresh drain, submissions=" + submissions.get())
                check(persistence.load().equals(store.snapshot()), "final persisted store after interleaving"); worker.close(); check(!worker.schedulePersist(), "closed rejects after interleaving")
            } catch (problem: Throwable) { original = problem; throw problem } finally { finishScope(scope, original) }
        }
        private fun closeDuringBlockedIo(base: Path, cleanup: RunCleanup) {
            val root = Files.createDirectory(base.resolve("close-block")); val persistence = RuntimeLogPersistence(root.toFile()); val store = RuntimeLogStore(); val executor = ManualExecutor()
            val worker = RuntimeLogWorker(store, persistence, null, executor); executor.runOne(); check(append(store), "append before blocked close"); val accepted = store.snapshot()
            check(executor.pending() == 1, "append scheduled the drain"); val ioProblem = AtomicReference<Throwable>(); val scope = ThreadScope(cleanup, root); var original: Throwable? = null
            try {
                val io: Thread
                synchronized(persistence) {
                    io = scope.start { try { executor.runOne() } catch (problem: Throwable) { ioProblem.set(problem) } }; waitBlocked(io)
                    val start = System.nanoTime(); worker.close(); check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "close never waits on blocked IO")
                    check(!worker.schedulePersist(), "closed rejects new admission"); check(executor.pending() == 0, "no new drain admitted during the blocked save")
                    check(append(store), "append still accepted by the store"); check(executor.pending() == 0, "removed sink does not schedule after close")
                }
                join(io); check(ioProblem.get() == null, "accepted drain completed: " + ioProblem.get()); check(persistence.load().equals(accepted), "accepted work persisted after close")
            } catch (problem: Throwable) { original = problem; throw problem } finally { finishScope(scope, original) }
        }
        private fun rejectionRecovery(base: Path) {
            val root = Files.createDirectory(base.resolve("reject-dir")); val persistence = RuntimeLogPersistence(root.toFile()); val store = RuntimeLogStore(); val rejecting = AtomicBoolean(true); val executor = ManualExecutor()
            val worker = RuntimeLogWorker(store, persistence, null, Executor { task -> if (rejecting.get()) throw RejectedExecutionException("reject"); executor.execute(task) })
            check(!worker.schedulePersist() && worker.droppedSchedules() == 2L && "queue-full".equals(worker.lastFailure()), "rejection isolated with the fixed label")
            rejecting.set(false); check(append(store), "append after rejection"); check(executor.pending() == 1, "append after rejection admitted a fresh drain")
            executor.runOne(); check(persistence.load().equals(store.snapshot()) && worker.lastFailure() == null, "recovery after rejection persisted"); worker.close()
        }
        private fun closeDuringBlockedSaveFatal(base: Path, cleanup: RunCleanup) {
            val dir = Files.createDirectory(base.resolve("close-save-fatal")); Files.createDirectory(dir.resolve("files")); val injected = AssertionError("close-save-fatal")
            val blockNext = AtomicBoolean(); val saveEntered = CountDownLatch(1); val saveOpen = CountDownLatch(1); val ioProblem = AtomicReference<Throwable>()
            val filesDir = object : File(dir.toFile(), "files") {
                override fun getCanonicalFile(): File {
                    if (blockNext.compareAndSet(true, false)) { saveEntered.countDown(); try { awaitGate(saveOpen) } catch (stop: InterruptedException) { Thread.currentThread().interrupt() }; throw injected }
                    return super.getCanonicalFile()
                }
            }
            val persistence = RuntimeLogPersistence(filesDir); val store = RuntimeLogStore(); val executor = ManualExecutor(); val worker = RuntimeLogWorker(store, persistence, null, executor)
            check(executor.pending() == 1, "constructor schedules recovery"); executor.runOne(); val beforeFatal = persistence.load(); check(append(store), "append before blocked fatal close")
            check(executor.pending() == 1, "append scheduled the failing drain"); blockNext.set(true); val scope = ThreadScope(cleanup, dir); scope.addGate(saveOpen); var original: Throwable? = null
            try {
                val io = scope.start { try { executor.runOne() } catch (problem: Throwable) { ioProblem.set(problem) } }; await(saveEntered)
                val start = System.nanoTime(); worker.close(); check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "close never waits on blocked IO")
                check(!worker.schedulePersist(), "closed rejects new admission"); check(executor.pending() == 0, "no new drain admitted while the save is blocked")
                check(worker.droppedSchedules() == 0L && worker.lastFailure() == null, "blocked-save fatal is not a queue-full rejection before it is even raised")
                saveOpen.countDown(); join(io); check(ioProblem.get() === injected, "same fatal identity preserved through the blocked close: " + ioProblem.get())
                check(!worker.schedulePersist(), "closed rejects after the fatal close"); check(executor.pending() == 0, "no auto retry after the fatal close")
                check(worker.droppedSchedules() == 0L, "fatal is never counted as dropped"); check(worker.lastFailure() == null, "fatal close never claims a queue-full label")
                check(!Files.exists(persistence.file().toPath().resolveSibling(RuntimeLogPersistence.FILE_NAME + ".part")), "no staging part on the fatal save")
                check(persistence.load().equals(beforeFatal), "failed fatal save left the persisted file unchanged")
            } catch (problem: Throwable) { original = problem; throw problem } finally { finishScope(scope, original) }
        }
        private fun rejectWindowSuccessor(base: Path, cleanup: RunCleanup) {
            val dir = Files.createDirectory(base.resolve("reject-window")); Files.createDirectory(dir.resolve("files")); val blockNext = AtomicBoolean()
            val saveEntered = CountDownLatch(1); val saveOpen = CountDownLatch(1); val rejectEntered = CountDownLatch(1); val rejectOpen = CountDownLatch(1)
            val filesDir = object : File(dir.toFile(), "files") {
                override fun getCanonicalFile(): File {
                    if (blockNext.compareAndSet(true, false)) { saveEntered.countDown(); try { awaitGate(saveOpen) } catch (stop: InterruptedException) { Thread.currentThread().interrupt() } }
                    return super.getCanonicalFile()
                }
            }
            val persistence = RuntimeLogPersistence(filesDir); val store = RuntimeLogStore(); val submissions = AtomicInteger(); val rejecting = AtomicBoolean(); val callbacks = AtomicInteger()
            val t1Problem = AtomicReference<Throwable>(); val t2Problem = AtomicReference<Throwable>(); val t3Problem = AtomicReference<Throwable>(); val t1Result = AtomicBoolean(); val t3Returned = AtomicBoolean()
            val counting = Executor { task -> submissions.incrementAndGet(); if (rejecting.compareAndSet(true, false)) throw RejectedExecutionException("reject"); task.run() }
            val worker = RuntimeLogWorker(store, persistence, { reason ->
                callbacks.incrementAndGet(); if (reason!!.equals("queue-full")) { rejectEntered.countDown(); try { awaitGate(rejectOpen) } catch (stop: InterruptedException) { Thread.currentThread().interrupt() } }
            }, counting)
            check(submissions.get() == 1 && worker.successfulSaves() == 1L, "constructor admission and save"); check(append(store), "append before rejection")
            check(submissions.get() == 2 && worker.successfulSaves() == 2L, "append drain admitted and saved")
            val scope = ThreadScope(cleanup, dir); scope.addGate(rejectOpen); scope.addGate(saveOpen); var original: Throwable? = null
            try {
                rejecting.set(true); val t1 = scope.start { try { t1Result.set(worker.schedulePersist()) } catch (problem: Throwable) { t1Problem.set(problem) } }; await(rejectEntered)
                check(submissions.get() == 3 && worker.droppedSchedules() == 1L && "queue-full".equals(worker.lastFailure()) && callbacks.get() == 1, "rejection released its own admission with the fixed label before the sink pause")
                blockNext.set(true); val t2 = scope.start { try { t2Problem.set(if (worker.schedulePersist()) null else AssertionError("T2 not admitted")) } catch (problem: Throwable) { t2Problem.set(problem) } }; await(saveEntered)
                check(submissions.get() == 4 && worker.successfulSaves() == 2L, "successor admitted into the rejection window and blocked in a real save, submissions=" + submissions.get())
                rejectOpen.countDown(); join(t1); check(!t1Result.get() && t1Problem.get() == null, "reject frame returned false without throwing: " + t1Problem.get())
                scope.start { try { t3Returned.set(worker.schedulePersist()) } catch (problem: Throwable) { t3Problem.set(problem) } }
                val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); while (!t3Returned.get() && System.nanoTime() < until) Thread.sleep(1)
                check(submissions.get() == 4, "third schedule only dirties while the successor owns the slot, submissions=" + submissions.get())
                check(t3Returned.get() && t3Problem.get() == null, "third schedule returned cleanly"); saveOpen.countDown(); join(t2)
                check(t2Problem.get() == null, "successor drain completed: " + t2Problem.get())
                check(worker.droppedSchedules() == 1L && worker.lastFailure() == null, "fixed queue-full label cleared by the successor's successful save; dropped count kept")
                check(persistence.load().equals(store.snapshot()), "successor persisted the full store"); worker.close(); check(!worker.schedulePersist(), "closed rejects after the rejection window")
            } catch (problem: Throwable) { original = problem; throw problem } finally { finishScope(scope, original) }
        }
        private fun teardownHold(base: Path, cleanup: RunCleanup, interruptFirstObservation: Boolean) {
            val dir = Files.createDirectory(base.resolve("teardown-hold")); Files.createDirectory(dir.resolve("files")); val saveEntered = CountDownLatch(1); val holdGate = CountDownLatch(1); val exitMonitor = Any()
            val canonicalCalls = AtomicInteger(); val ioProblem = AtomicReference<Throwable>()
            val filesDir = object : File(dir.toFile(), "files") {
                override fun getCanonicalFile(): File {
                    if (canonicalCalls.incrementAndGet() == 2) {
                        saveEntered.countDown(); try { awaitGate(holdGate) } catch (stop: InterruptedException) { Thread.currentThread().interrupt() }
                        synchronized(exitMonitor) { /* independent owner must release this monitor */ }
                    }
                    return super.getCanonicalFile()
                }
            }
            val persistence = RuntimeLogPersistence(filesDir); val store = RuntimeLogStore(); val executor = ManualExecutor(); val worker = RuntimeLogWorker(store, persistence, null, executor)
            val scope = ThreadScope(cleanup, dir); scope.addGate(holdGate); var original: Throwable? = null
            try {
                synchronized(exitMonitor) {
                    val io = scope.start { try { executor.runOne() } catch (problem: Throwable) { ioProblem.set(problem) } }
                    if (interruptFirstObservation) Thread.currentThread().interrupt()
                    await(saveEntered)
                    val start = System.nanoTime(); var refusal: AssertionError? = null
                    try { finishScope(scope, null, 999999) } catch (expected: AssertionError) { refusal = expected }
                    check(refusal != null && refusal.message!!.contains("worker not confirmed exited"), "shared finishScope must report the actual held worker")
                    check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "sub-millisecond remaining budget must not become infinite join(0)")
                    check(io.isAlive, "release request is not actual exit"); var deletion: AssertionError? = null
                    try { cleanup.delete(dir, null) } catch (expected: AssertionError) { deletion = expected }
                    check(deletion != null && deletion.message!!.contains("cleanup refused") && Files.exists(dir), "same main deletion guard must refuse the in-use tree")
                    println("SHARED_CLEANUP_REFUSED tiny-budget dir=" + dir)
                    var observed: InterruptedException? = null; var returned: Throwable? = null
                    try {
                        Thread.currentThread().interrupt()
                        try { await(saveEntered) } catch (stop: InterruptedException) { observed = stop; Thread.currentThread().interrupt(); throw stop }
                        finally { finishScope(scope, observed, 999999) }
                    } catch (failure: Throwable) {
                        try { try { throw failure } finally { cleanup.delete(dir, failure) } } catch (fromCleanup: Throwable) { returned = fromCleanup }
                    }
                    check(observed != null && returned === observed, "original observation Throwable identity reached host")
                    check(returned!!.suppressed.size == 2, "primary observation error has teardown and deletion suppressed")
                    check(returned.suppressed[0].message!!.contains("worker not confirmed exited") && returned.suppressed[1].message!!.contains("cleanup refused"), "real cleanup diagnostics carried to host")
                    check(Thread.currentThread().isInterrupted && Files.exists(dir), "interrupt preserved and host sees retained tree")
                    println("HOST_CAUGHT_PRIMARY_WITH_SUPPRESSED noexit dir=" + dir)
                }
            } catch (problem: Throwable) {
                original = problem; if (problem is InterruptedException) Thread.currentThread().interrupt(); throw problem
            } finally {
                val interrupted = Thread.interrupted()
                try { finishScope(scope, original); cleanup.delete(dir, original) } finally { if (interrupted) Thread.currentThread().interrupt() }
            }
            check(Thread.interrupted(), "host resumed with the observation interrupt intact"); check(scope.awaitAll(0), "already exited worker is confirmed even with zero remaining budget")
            check(ioProblem.get() == null, "held save actually completed: " + ioProblem.get()); check(!Files.exists(dir), "only independent release plus actual exit permits deletion"); worker.close()
        }
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            val base = Files.createTempDirectory("runtime-log-worker-error-"); val cleanup = RunCleanup(); var original: Throwable? = null
            try {
                val all = args!!.isEmpty()
                if (all || "load".equals(args[0])) loadPathFatal(base)
                if (all || "restore".equals(args[0])) restorePathFatal(base)
                if (all || "callback".equals(args[0])) failureCallbackFatal(base)
                if (all || "save".equals(args[0])) savePathFatal(base)
                if (all || "execute".equals(args[0])) executeFatal(base)
                if (all || "interleave".equals(args[0])) staleOuterReleaseFatal(base, cleanup)
                if (all || "close-block".equals(args[0])) closeDuringBlockedIo(base, cleanup)
                if (all || "reject".equals(args[0])) rejectionRecovery(base)
                if (all || "close-fatal".equals(args[0])) closeDuringBlockedSaveFatal(base, cleanup)
                if (all || "reject-window".equals(args[0])) rejectWindowSuccessor(base, cleanup)
                if (all || "teardown-hold".equals(args[0]) || "teardown-interrupt".equals(args[0])) teardownHold(base, cleanup, !all && "teardown-interrupt".equals(args[0]))
                println("PASS RuntimeLogWorkerErrorTest: " + checks + " checks (" + (if (all) "load/restore/callback/save/execute/interleave/close-block/reject/close-fatal/reject-window/teardown-hold" else args[0]) + ")")
            } catch (problem: Throwable) { original = problem; throw problem } finally { cleanup.delete(base, original) }
        }
    }
}
