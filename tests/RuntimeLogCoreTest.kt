package org.llmasr.minimal.diagnostics

import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Real production logging core, filesystem, symlinks and threads; not Android/native runtime. */
class RuntimeLogCoreTest {
    private fun interface Action { fun run() }
    private class ManualExecutor : Executor {
        val queue: Deque<Runnable?> = ArrayDeque(); var submissions = 0; var highWater = 0
        @Synchronized override fun execute(command: Runnable?) { submissions++; queue.add(command); highWater = Math.max(highWater, queue.size) }
        @Synchronized fun pending(): Int = queue.size
        fun runOne() { val task = synchronized(this) { queue.remove() }; task!!.run() }
    }
    private class SequenceClock : RuntimeLogClock {
        var wall = 1700000000123L; var mono = -900L; var walls = 0; var monos = 0; var broken = false
        override fun wallTimeMillis(): Long { walls++; if (broken) throw IllegalStateException("synthetic clock"); return wall-- }
        override fun monotonicNanos(): Long { monos++; return mono++ }
    }
    private class SequenceScope : AutoCloseable {
        val root = Files.createTempDirectory("runtime-sequence-"); val threads = ArrayList<Thread>(); val gates = ArrayList<CountDownLatch>()
        val workers = ArrayList<RuntimeLogWorker>(); val executors = ArrayList<ManualExecutor>()
        fun gate(): CountDownLatch { val gate = CountDownLatch(1); gates.add(gate); return gate }
        fun start(thread: Thread) { threads.add(thread); thread.start() }
        fun worker(store: RuntimeLogStore, persistence: RuntimeLogPersistence, executor: ManualExecutor): RuntimeLogWorker {
            val worker = RuntimeLogWorker(store, persistence, null, executor); workers.add(worker); executors.add(executor); return worker
        }
        override fun close() {
            for (gate in gates) gate.countDown()
            var failure: Throwable? = null; var interrupted = Thread.interrupted(); var alive = false; val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            for (thread in threads) {
                while (thread.isAlive) {
                    val left = deadline - System.nanoTime(); if (left <= 0) break
                    try { thread.join(left / 1000000, (left % 1000000).toInt()) }
                    catch (problem: InterruptedException) { interrupted = true; if (failure == null) failure = problem else (failure as java.lang.Throwable).addSuppressed(problem) }
                }
                alive = alive or thread.isAlive
            }
            if (alive) {
                val problem = IOException("sequence worker not terminated; preserving fixture")
                if (failure == null) failure = problem else (failure as java.lang.Throwable).addSuppressed(problem)
            } else {
                for (i in workers.indices) try {
                    workers[i].close(); val executor = executors[i]; var remaining = 32
                    while (executor.pending() > 0 && remaining-- > 0) executor.runOne()
                    check(executor.pending() == 0, "sequence cleanup drain bounded")
                } catch (problem: Throwable) { if (failure == null) failure = problem else (failure as java.lang.Throwable).addSuppressed(problem) }
                try { deleteTree(root) } catch (problem: Throwable) { if (failure == null) failure = problem else (failure as java.lang.Throwable).addSuppressed(problem) }
            }
            if (interrupted) Thread.currentThread().interrupt()
            if (failure is Error) throw failure
            if (failure is Exception) throw failure
        }
    }
    companion object {
        private const val ID = "12345678-abcd-1234-9876-123456789abc"
        private var groups = 0
        private fun check(value: Boolean, message: String) { if (!value) throw AssertionError(message) }
        private fun rejects(action: Action, type: Class<out Exception>) {
            try { action.run() } catch (expected: Exception) { if (type.isInstance(expected)) return; throw expected }
            throw AssertionError("expected " + type.simpleName)
        }
        private fun event(sequence: Long): RuntimeLogEvent = RuntimeLogEvent(sequence, 1700000000123L, -100, RuntimeLogEventKind.INFERENCE_COMPLETED, RuntimeLogSource.IME, ID, "ok", 1234567890L)
        private fun append(store: RuntimeLogStore): Boolean = store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok")
        private fun passed(message: String) { groups++; println("PASS " + message) }
        private fun await(latch: CountDownLatch) { check(latch.await(5, TimeUnit.SECONDS), "latch timeout") }
        private fun join(thread: Thread) { thread.join(5000); check(!thread.isAlive, "thread timeout") }
        private fun waitBlocked(thread: Thread) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (thread.state != Thread.State.BLOCKED && System.nanoTime() < until) Thread.sleep(1)
            check(thread.state == Thread.State.BLOCKED, "worker not blocked on persistence monitor")
        }
        private fun deleteTree(root: Path) {
            val paths = Files.walk(root); var primary: Throwable? = null
            try { val it = paths.sorted(Comparator.reverseOrder()).iterator(); while (it.hasNext()) Files.deleteIfExists(it.next()) }
            catch (problem: Throwable) { primary = problem; throw problem }
            finally { if (primary == null) paths.close() else try { paths.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        private inline fun scoped(body: (SequenceScope) -> Unit) {
            val scope = SequenceScope(); var primary: Throwable? = null
            try { body(scope) } catch (problem: Throwable) { primary = problem; throw problem }
            finally { if (primary == null) scope.close() else try { scope.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        private inline fun outputScope(stream: OutputStream, body: (OutputStream) -> Unit) {
            var primary: Throwable? = null
            try { body(stream) } catch (problem: Throwable) { primary = problem; throw problem }
            finally { if (primary == null) stream.close() else try { stream.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        private fun eventBoundary() {
            val clock = RuntimeLogClock.Fake(1700000000123L, 500); val store = RuntimeLogStore(3, clock)
            for (secret in arrayOf("秘密内容", "secret", "deadbeef", "用户音频", "file:///private/model", "/private/path", "https://provider", "exception-message", "ok\nsecret", "OK")) {
                check(!store.append(RuntimeLogEventKind.REQUEST_FAILURE, RuntimeLogSource.APP, ID, secret), "private detail rejected")
                rejects({ RuntimeLogEvent.sanitizeDetail(secret) }, IllegalArgumentException::class.java)
            }
            for (bad in arrayOf("", "deadbeef", "------------------------------------", "1234567-abcd-1234-9876-123456789abcd", ID.uppercase(Locale.ROOT), "秘密")) check(!store.append(RuntimeLogEventKind.REQUEST_FAILURE, RuntimeLogSource.APP, bad, "ok"), "UUID exact shape")
            check(!store.append(null, RuntimeLogSource.APP, ID, "ok"), "null enum")
            check(!store.append(RuntimeLogEventKind.REQUEST_SUCCESS, RuntimeLogSource.APP, ID, "ok", -2), "negative duration")
            rejects({ RuntimeLogEvent(-1, 0, 0, RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, null, null) }, IllegalArgumentException::class.java)
            rejects({ RuntimeLogStore(1001) }, IllegalArgumentException::class.java)
            check(store.append(RuntimeLogEventKind.MODEL_LOAD_STARTED, RuntimeLogSource.APP, ID, "ok"), "start")
            val start = clock.monotonicNanos(); clock.advance(-10000, 1234567890L)
            check(store.append(RuntimeLogEventKind.MODEL_LOAD_COMPLETED, RuntimeLogSource.APP, ID, "ok", clock.monotonicNanos() - start), "duration append")
            val snapshot = store.snapshot(); check(snapshot[0].sequence == 0L && snapshot[1].sequence == 1L, "rejects do not consume sequence")
            val original = TimeZone.getDefault()
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+05:30")); val line = RuntimeLogStore.formatEvent(snapshot[1])
                check(line.startsWith("2023-11-15 03:43:10.123 +05:30"), "timestamp milliseconds/offset: " + line)
                check(line.contains("elapsed_ms=1234.567890"), "monotonic duration despite wall rollback")
                check(!RuntimeLogStore.formatEvent(snapshot[0]).contains("elapsed_ms"), "unknown duration omitted")
            } finally { TimeZone.setDefault(original) }
            val e = snapshot[1]; check(e.equals(e.withSequence(e.sequence)) && e.hashCode() == e.withSequence(e.sequence).hashCode(), "value equality")
            check(!e.equals(RuntimeLogEvent(e.sequence, e.wallMillis, e.monotonicNanos, e.kind, e.source, e.requestId, e.detail, 1)), "duration equality")
            rejects({ snapshot.clear() }, UnsupportedOperationException::class.java)
            for (i in 0 until 10) append(store)
            check(snapshot.size == 2 && snapshot[0].sequence == 0L, "snapshot frozen"); check(store.size() == 3 && store.droppedCount() == 9L, "bounded eviction")
            for (detail in arrayOf("native-runtime", "native-null", "parse-failed")) check(store.append(RuntimeLogEventKind.REQUEST_FAILURE, RuntimeLogSource.IME, ID, detail), "existing fixed caller label")
            passed("typed privacy boundary, canonical UUID, timestamp/elapsed formatting, immutable bounded snapshots")
        }
        private fun publication() {
            val store = RuntimeLogStore(); val delivered = Collections.synchronizedList(ArrayList<Long>()); val drops = Collections.synchronizedList(ArrayList<Int>())
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val first = AtomicBoolean(true)
            store.addSink { _, _ -> if (first.compareAndSet(true, false)) { entered.countDown(); try { await(release) } catch (failure: Exception) { throw AssertionError(failure) } } }
            store.addSink { _, _ -> throw IllegalStateException("private listener error") }
            store.addSink { snapshot, dropped -> delivered.add(snapshot!![snapshot.size - 1]!!.sequence); drops.add(dropped) }
            val failure = AtomicReference<Throwable>(); val publisher = Thread { try { check(append(store), "first append") } catch (t: Throwable) { failure.set(t) } }
            publisher.start(); await(entered); val producers = arrayOfNulls<Thread>(6)
            for (i in producers.indices) { producers[i] = Thread { try { for (n in 0 until 500) check(append(store), "concurrent append") } catch (t: Throwable) { failure.set(t) } }; producers[i]!!.start() }
            for (producer in producers) join(producer!!)
            check(store.size() == 1000 && store.droppedCount() == 2001L, "blocked listener does not lock producers"); release.countDown(); join(publisher)
            check(failure.get() == null, "thread failure: " + failure.get()); check(delivered.equals(Arrays.asList(0L, 3000L)), "serialized latest-only publication: " + delivered)
            check(drops.equals(Arrays.asList(0, 2001)), "cumulative sink drop count"); var previous = 2000L
            for (e in store.snapshot()) check(e.sequence == ++previous, "contiguous insertion sequence")
            val reentrant = RuntimeLogStore(3); val nested = AtomicBoolean(); val order = ArrayList<Long>()
            reentrant.addSink { _, _ -> if (nested.compareAndSet(false, true)) append(reentrant) }; reentrant.addSink { s, _ -> order.add(s!![s.size - 1]!!.sequence) }; append(reentrant)
            check(order.equals(Arrays.asList(0L, 1L)), "reentrant publication ordered")
            passed("real concurrent sequence/eviction, serialized coalescing sinks, reentrancy and listener fault isolation")
        }
        private fun persistence(base: Path) {
            val root = Files.createDirectory(base.resolve("persist")); val p = RuntimeLogPersistence(root.toFile())
            check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.MISSING, "missing")
            val events = Arrays.asList(event(2), event(5)); check(p.save(events, null) == 2, "save"); check(RuntimeLogPersistence(root.toFile()).load().equals(events), "reopen exact values")
            val file = p.file().toPath(); val valid = Files.readAllBytes(file); val text = String(valid, StandardCharsets.UTF_8); val corrupt = ArrayList<ByteArray>()
            for (i in valid.indices) corrupt.add(Arrays.copyOf(valid, i))
            for (bad in arrayOf(text + "junk", text.replace("END", "END\nEND"), text.replace("V1\t2", "V1\t1"), text.replace("\tok\n", "\t秘密\n"), text.replace("\tok\n", "\tsecret\n"), text.replace(ID, "deadbeef"), text.replace("INFERENCE_COMPLETED", "UNKNOWN"), text.replace("\n5\t", "\n2\t"), text.replace("\n5\t", "\n1\t"), text.replace("\n2\t", "\n02\t"), text.replace("\t1234567890\t", "\t-2\t"), text.replace("\tok\n", "\t\n"), text.replace("\tIME\t", "\tIME\textra\t"), "[]", "[{\"seq\":0}]garbage")) corrupt.add(bad.toByteArray(StandardCharsets.UTF_8))
            corrupt.add(byteArrayOf(0xc3.toByte(), 0x28))
            for (bad in corrupt) { Files.write(file, bad); check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.CORRUPT, "reject entire malformed file") }
            Files.write(file, ByteArray(RuntimeLogPersistence.MAX_FILE_BYTES.toInt() + 1)); check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.OVERSIZED, "oversized load")
            val noisy = ArrayList<RuntimeLogEvent>(); for (i in 0 until 20000) noisy.add(event(i.toLong()))
            check(p.save(noisy, null) == 1000, "large input suffix bounded before encode")
            check(Files.size(file) <= RuntimeLogPersistence.MAX_FILE_BYTES && p.load()[0].sequence == 19000L, "file cap/newest suffix")
            val saved = Files.readAllBytes(file); check(p.save(Arrays.asList(event(4), event(3))) { throw IllegalStateException() } == -1, "invalid sequence and throwing failure sink isolated")
            check(Arrays.equals(saved, Files.readAllBytes(file)), "invalid save leaves final intact")
            Files.write(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part"), "incomplete secret".toByteArray(StandardCharsets.UTF_8)); Files.write(root.resolve("unrelated"), byteArrayOf(9))
            check(p.load().size == 1000, "stale part never recovered"); check(p.save(events, null) == 2 && !Files.exists(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part")), "stale regular part replaced atomically")
            check(Files.readAllBytes(root.resolve("unrelated"))[0] == 9.toByte(), "unknown files untouched")
            passed("real file reopen, strict complete parser (all truncated prefixes), corrupt/private/oversize rejection, hard caps and stale part")
        }
        private fun paths(base: Path) {
            val real = Files.createDirectories(base.resolve("user/0/app/files")); val alias = base.resolve("data"); Files.createSymbolicLink(alias, base.resolve("user/0"))
            val noIo = object : File(alias.resolve("app/files").toString()) { override fun getCanonicalFile(): File { throw AssertionError("constructor disk IO") } }; RuntimeLogPersistence(noIo)
            val p = RuntimeLogPersistence(alias.resolve("app/files").toFile()); val events = Collections.singletonList(event(0))
            check(p.save(events, null) == 1 && p.load().equals(events), "trusted ancestor alias allowed")
            val finalFile = real.resolve(RuntimeLogPersistence.FILE_NAME); val outside = Files.write(base.resolve("outside"), byteArrayOf(7, 8))
            for (leaf in arrayOf(RuntimeLogPersistence.FILE_NAME, RuntimeLogPersistence.FILE_NAME + ".part")) {
                val managed = real.resolve(leaf)
                for (target in arrayOf(outside, base.resolve("dangling-target"))) {
                    Files.deleteIfExists(managed); Files.createSymbolicLink(managed, target)
                    check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.UNSAFE_PATH, "symlink load rejected")
                    check(p.save(events, null) == -1 && p.status() == RuntimeLogPersistence.Status.UNSAFE_PATH, "symlink save rejected")
                    check(Arrays.equals(Files.readAllBytes(outside), byteArrayOf(7, 8)) && !Files.exists(base.resolve("dangling-target")), "outside untouched")
                    check(Files.isSymbolicLink(managed), "link not removed"); Files.delete(managed)
                }
                Files.deleteIfExists(managed); Files.createDirectory(managed); check(p.save(events, null) == -1 && p.load().isEmpty(), "managed directory rejected")
                Files.delete(managed); check(p.save(events, null) == 1, "recovery after unsafe leaf removed")
            }
            val good = Files.readAllBytes(finalFile); Files.createDirectory(real.resolve(RuntimeLogPersistence.FILE_NAME + ".part")); check(p.save(Collections.singletonList(event(1)), null) == -1, "disk failure")
            check(Arrays.equals(good, Files.readAllBytes(finalFile)), "failed save final unchanged"); Files.delete(real.resolve(RuntimeLogPersistence.FILE_NAME + ".part"))
            val alternate = Files.createDirectories(base.resolve("alternate/app/files")); Files.delete(alias); Files.createSymbolicLink(alias, alternate.parent.parent)
            check(p.save(events, null) == -1 && p.status() == RuntimeLogPersistence.Status.UNSAFE_PATH, "pinned parent alias retarget refused"); check(!Files.exists(alternate.resolve(RuntimeLogPersistence.FILE_NAME)), "no retargeted write")
            val missing = RuntimeLogPersistence(base.resolve("absent-parent").toFile()); val reason = AtomicReference<String>()
            check(missing.save(events, reason::set) == -1 && "unsafe-path".equals(reason.get()), "missing trusted root fixed failure")
            val readonly = Files.createDirectory(base.resolve("readonly")); val permissions = Files.getPosixFilePermissions(readonly)
            try {
                Files.setPosixFilePermissions(readonly, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------")); val denied = RuntimeLogPersistence(readonly.toFile())
                check(denied.save(events, null) == -1 && denied.status() == RuntimeLogPersistence.Status.IO_ERROR, "real filesystem permission denied (run as non-root)")
            } finally { Files.setPosixFilePermissions(readonly, permissions) }
            passed("real final/part symlinks including dangling, leaf directories, trusted parent alias pinning, failed atomic staging and permission-denied IO")
        }
        private fun workers(base: Path) {
            val root = Files.createDirectory(base.resolve("worker")); val p = RuntimeLogPersistence(root.toFile()); p.save(Arrays.asList(event(40), event(41)), null)
            val store = RuntimeLogStore(4); append(store); val early = store.snapshot(); val executor = ManualExecutor(); val worker = RuntimeLogWorker(store, p, null, executor)
            check(executor.pending() == 1 && store.size() == 1, "constructor schedules but does not load"); append(store); worker.restoreFromDisk(); check(executor.pending() == 1, "startup coalesced")
            executor.runOne(); val restored = store.snapshot()
            check(restored.size == 4 && restored[0].sequence == 40L && restored[2].sequence == 42L && restored[3].sequence == 43L, "history precedes and preserves early appends")
            check(early[0].sequence == 0L && restored[2].wallMillis == early[0].wallMillis, "early snapshot stable after rebase"); check(p.load().equals(restored), "merged recovery persisted")
            worker.restoreFromDisk(); executor.runOne(); check(store.snapshot().equals(restored), "one recovery only"); val before = executor.submissions; val blocked: Thread
            synchronized(p) {
                append(store); blocked = Thread { executor.runOne() }; blocked.start(); waitBlocked(blocked)
                for (i in 0 until 10000) check(append(store), "noisy append while disk blocked")
                check(executor.pending() == 0 && executor.submissions == before + 1, "only running drain and dirty bit")
            }
            join(blocked); check(p.load().equals(store.snapshot()), "latest dirty snapshot saved after blocked write")
            check(executor.highWater == 1 && worker.droppedSchedules() == 0L, "bounded task count"); check(store.droppedCount() == 10001L, "recovery plus noisy rotation drops")
            append(store); worker.close(); worker.close(); check(!worker.schedulePersist(), "closed rejects new work"); executor.runOne(); check(p.load().equals(store.snapshot()), "close drains accepted work")
            append(store); check(executor.pending() == 0, "close removes sink")
            val injected = Executors.newSingleThreadExecutor(); val external = RuntimeLogWorker(RuntimeLogStore(), p, null, injected); external.close(); check(!injected.isShutdown, "injected executor not owned")
            injected.shutdown(); check(injected.awaitTermination(5, TimeUnit.SECONDS), "external drained")
            val rejecting = AtomicBoolean(true); val retry = ManualExecutor(); val rejectedStore = RuntimeLogStore()
            val rejected = RuntimeLogWorker(rejectedStore, p, { throw IllegalStateException() }, Executor { task -> if (rejecting.get()) throw RejectedExecutionException(); retry.execute(task) })
            check(rejected.droppedSchedules() == 1L && "queue-full".equals(rejected.lastFailure()), "plain rejected executor safe")
            rejecting.set(false); append(rejectedStore); retry.runOne(); check(rejected.successfulSaves() > 0 && rejected.lastFailure() == null, "retry after executor rejection"); rejected.close()
            val direct = RuntimeLogWorker(RuntimeLogStore(), p, null, Executor { it.run() }); check(direct.successfulSaves() == 1L, "plain executor executed"); direct.close()
            passed("plain/injected executors, first asynchronous recovery preserving early events, blocked write + 10000 appends, latest coalescing/close/retry")
        }
        private fun ownedWorker(base: Path) {
            val p = RuntimeLogPersistence(Files.createDirectory(base.resolve("owned")).toFile()); val store = RuntimeLogStore(); val worker: RuntimeLogWorker
            synchronized(p) { worker = RuntimeLogWorker(store, p, null); check(append(store), "owned worker constructor and append never wait for disk"); val start = System.nanoTime(); worker.close(); check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "close never waits on blocked IO") }
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); while (worker.successfulSaves() == 0L && System.nanoTime() < until) Thread.sleep(1)
            check(worker.successfulSaves() > 0 && p.load().equals(store.snapshot()), "owned executor drains accepted startup after close")
            passed("production-owned background executor: no synchronous startup disk, nonblocking close and graceful accepted drain")
        }
        private fun workerFailures(base: Path) {
            val root = Files.createDirectory(base.resolve("failure")); val part = Files.createDirectory(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part")); val store = RuntimeLogStore(); val p = RuntimeLogPersistence(root.toFile())
            val executor = ManualExecutor(); val failures = AtomicInteger()
            val worker = RuntimeLogWorker(store, p, { reason -> failures.incrementAndGet(); store.append(RuntimeLogEventKind.LOG_PERSISTENCE_FAILED, RuntimeLogSource.SHARED, null, reason) }, executor)
            executor.runOne(); check(failures.get() == 1 && "unsafe-path".equals(worker.lastFailure()) && worker.failedSaves() == 1L, "failure callback append cannot create recursive storm")
            for (i in 0 until 10) append(store)
            executor.runOne(); check(failures.get() == 1 && worker.failedSaves() == 2L, "same failure deduped"); Files.delete(part); append(store); executor.runOne()
            check(worker.lastFailure() == null && p.load().equals(store.snapshot()), "IO repaired, status and persisted store recover")
            Files.createDirectory(part); append(store); executor.runOne(); check(failures.get() == 2 && worker.failedSaves() == 4L, "new failure episode observable, single callback + finite dirty retry"); worker.close()
            val overflow = Files.createDirectory(base.resolve("overflow")); val max = RuntimeLogPersistence(overflow.toFile()); max.save(Collections.singletonList(event(Long.MAX_VALUE - 1)), null)
            val live = RuntimeLogStore(); append(live); val tasks = ManualExecutor(); val overflowWorker = RuntimeLogWorker(live, max, null, tasks); tasks.runOne()
            check(live.size() == 2 && live.snapshot()[0].sequence == 0L && live.snapshot()[1].sequence == 1L && append(live), "legal extreme history and early event retained")
            check(overflowWorker.recoveryStatus() == RuntimeLogPersistence.Status.OK, "legal extreme recovery remains OK after save"); overflowWorker.close(); tasks.runOne()
            passed("observable/deduped disk failure, recursive callback isolation, recovery from failure and hostile sequence overflow")
        }
        private fun ordered(values: List<RuntimeLogEvent>) { var previous = -1L; for (value in values) { check(value.sequence > previous && value.sequence < Long.MAX_VALUE, "sequence snapshot strictly ordered"); previous = value.sequence } }
        private fun sameFields(expected: RuntimeLogEvent, actual: RuntimeLogEvent) { check(expected.withSequence(actual.sequence).equals(actual), "sequence rebase preserves every other field") }
        private fun sequenceBytes(values: List<RuntimeLogEvent>): ByteArray { val out = ByteArrayOutputStream(); RuntimeLogCodec.writeSnapshot(values, out); return out.toByteArray() }
        private fun appendMany(store: RuntimeLogStore, count: Int, label: String) { for (i in 0 until count) check(append(store), label + " append rejected at i=" + i); ordered(store.snapshot()) }
        private fun nearMaxProgress(early: Boolean) = scoped { scope ->
            val capacity = 4; var p = RuntimeLogPersistence(scope.root.toFile()); val history = Collections.singletonList(event(Long.MAX_VALUE - if (early) 3 else 2))
            check(p.save(history, null) == 1, "near-max fixture save"); check(RuntimeLogPersistence(scope.root.toFile()).load().equals(history), "near-max fixture is legal V1")
            val store = RuntimeLogStore(capacity, SequenceClock()); if (early) check(append(store), "near-max early append")
            val frozenEarly = store.snapshot(); val tasks = ManualExecutor(); val worker = scope.worker(store, p, tasks); tasks.runOne(); check(worker.recoveryStatus() == RuntimeLogPersistence.Status.OK, "near-max legal recovery OK")
            val frozen = store.snapshot(); val frozenBytes = sequenceBytes(frozen); check(frozen[0].equals(history[0]), "near-max representable history unchanged")
            appendMany(store, 4 * capacity + 7, "near-max " + if (early) "early" else "no-early")
            check(store.size() == capacity && store.droppedCount() == (history.size + frozenEarly.size + 4 * capacity + 7 - capacity).toLong(), "near-max real evictions only")
            check(frozen[0].equals(history[0]) && Arrays.equals(frozenBytes, sequenceBytes(frozen)), "high snapshot stays frozen"); if (early) check(frozenEarly[0].sequence == 0L, "early captured ID unchanged")
            tasks.runOne(); check(p.load().equals(store.snapshot()), "near-max final snapshot persisted"); worker.close()
            for (restart in 0 until 2) {
                val saved = p.load(); val reopened = RuntimeLogStore(capacity, SequenceClock()); if (restart == 1) check(append(reopened), "restart early event")
                val disk = RuntimeLogPersistence(scope.root.toFile()); val queued = ManualExecutor(); val next = scope.worker(reopened, disk, queued); queued.runOne()
                check(next.recoveryStatus() == RuntimeLogPersistence.Status.OK && next.lastFailure() == null, "restart still OK, not corrupt fallback")
                val loaded = reopened.snapshot(); val skip = if (restart == 1) 1 else 0
                for (i in 0 until saved.size - skip) check(loaded[i].equals(saved[i + skip]), "restart preserves history suffix")
                appendMany(reopened, 4 * capacity + 7, "restart " + restart); queued.runOne(); check(disk.load().equals(reopened.snapshot()), "restart latest persisted exact"); next.close(); p = disk
            }
        }
        private fun legalMaxRecovery() = scoped { scope ->
            val p = RuntimeLogPersistence(scope.root.toFile()); val history = Collections.singletonList(event(Long.MAX_VALUE - 1)); check(p.save(history, null) == 1, "max history save legal")
            val store = RuntimeLogStore(4, SequenceClock()); check(append(store), "max early"); val early = store.snapshot()[0]; val tasks = ManualExecutor(); val worker = scope.worker(store, p, tasks); tasks.runOne()
            check(worker.recoveryStatus() == RuntimeLogPersistence.Status.OK && store.size() == 2, "legal max history was discarded or marked corrupt")
            check(store.snapshot()[0].sequence == 0L && store.snapshot()[1].sequence == 1L, "unrepresentable recovery dense order")
            sameFields(history[0], store.snapshot()[0]); sameFields(early, store.snapshot()[1]); check(store.droppedCount() == 0L && p.load().equals(store.snapshot()), "normalize is not drop; persisted V1 exact")
            appendMany(store, 4 * store.capacity() + 7, "max restored"); tasks.runOne(); worker.close()
            val reopened = RuntimeLogStore(4); val queued = ManualExecutor(); val next = scope.worker(reopened, RuntimeLogPersistence(scope.root.toFile()), queued); queued.runOne()
            check(next.recoveryStatus() == RuntimeLogPersistence.Status.OK, "max normalized history restarts OK"); appendMany(reopened, 23, "max restarted"); queued.runOne()
        }
        private fun sequenceRecoveryMatrix() {
            for (capacity in intArrayOf(1, 4, RuntimeLogStore.DEFAULT_CAPACITY)) {
                val ordinary = RuntimeLogStore(capacity, SequenceClock()); for (i in 0 until capacity + 2) check(append(ordinary), "ordinary early")
                val early = ordinary.snapshot(); val oldDrop = ordinary.droppedCount(); ordinary.restore(Arrays.asList(event(2), event(5))); var current = ordinary.snapshot()
                check(current[current.size - 1].sequence == capacity + 7L, "ordinary offset uses next, not retained size"); check(ordinary.droppedCount() == oldDrop + 2, "ordinary merge drops exact")
                check(early[0].sequence == 2L, "ordinary early snapshot unchanged"); val once = ordinary.snapshot(); ordinary.restore(Collections.singletonList(event(1))); check(ordinary.snapshot().equals(once), "restore once only")
                val dense = RuntimeLogStore(capacity, SequenceClock()); for (i in 0 until capacity + 2) check(append(dense), "dense early")
                val before = dense.snapshot(); dense.restore(Arrays.asList(event(7), event(Long.MAX_VALUE - 1))); current = dense.snapshot()
                check(dense.droppedCount() == 4L && current.size == capacity, "dense trim only counts two merge evictions plus prior two")
                for (i in 0 until capacity) { check(current[i].sequence == i + 2L, "dense-before-trim exact indices"); sameFields(before[i], current[i]) }
                appendMany(dense, 4 * capacity + 7, "dense capacity " + capacity)
                val single = RuntimeLogStore(capacity, SequenceClock()); single.restore(Collections.singletonList(event(Long.MAX_VALUE - 1))); check(single.snapshot()[0].sequence == 0L, "max no-early normalized"); appendMany(single, 4 * capacity + 7, "no-early capacity " + capacity)
            }
            val gaps = RuntimeLogStore(4); gaps.restore(Arrays.asList(event(2), event(5))); check(gaps.snapshot().equals(Arrays.asList(event(2), event(5))) && append(gaps) && gaps.snapshot()[2].sequence == 6L, "ordinary gap values exact")
            val empty = RuntimeLogStore(4); empty.restore(Collections.emptyList()); check(append(empty) && empty.snapshot()[0].sequence == 0L, "empty restore")
            val nearGap = RuntimeLogStore(4); nearGap.restore(Arrays.asList(event(7), event(Long.MAX_VALUE - 2))); check(nearGap.snapshot()[0].sequence == 7L && append(nearGap) && append(nearGap), "gapped legal history progresses across exhaustion"); ordered(nearGap.snapshot())
        }
        private fun sequenceRejectsAndSnapshots() {
            val clock = SequenceClock(); val store = RuntimeLogStore(4, clock); store.restore(Arrays.asList(event(7), event(Long.MAX_VALUE - 2)))
            check(store.append(RuntimeLogEventKind.REQUEST_SUCCESS, RuntimeLogSource.APP, ID, "ok", 17), "reach max next"); val before = store.snapshot(); val bytes = sequenceBytes(before)
            val notifications = AtomicInteger(); store.addSink { _, _ -> notifications.incrementAndGet() }; var walls = clock.walls; var monos = clock.monos
            check(!store.append(null, RuntimeLogSource.APP, ID, "ok"), "invalid enum at max"); check(!store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, "bad", "ok"), "invalid UUID at max")
            check(!store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, ID, "private-sentinel"), "invalid detail at max"); check(!store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, ID, "ok", -2), "invalid duration at max")
            check(clock.walls == walls + 4 && clock.monos == monos + 4, "invalid fields retain one clock read per attempt"); clock.broken = true; walls = clock.walls; monos = clock.monos
            check(!append(store) && clock.walls == walls + 1 && clock.monos == monos, "clock failure reads once, rejects"); clock.broken = false
            check(store.snapshot().equals(before) && store.droppedCount() == 0L && notifications.get() == 0, "invalid max append has no state or notification side effects")
            walls = clock.walls; monos = clock.monos; check(append(store) && clock.walls == walls + 1 && clock.monos == monos + 1, "valid max rebase reads clocks only once")
            val rebased = store.snapshot(); for (i in before.indices) { check(rebased[i].sequence == i.toLong(), "append dense retained order"); sameFields(before[i], rebased[i]) }
            check(notifications.get() == 1 && store.droppedCount() == 0L, "rebase emits only completed append")
            check(before[1].sequence == Long.MAX_VALUE - 2 && before[2].sequence == Long.MAX_VALUE - 1, "old high IDs unchanged"); check(Arrays.equals(bytes, sequenceBytes(before)), "old captured codec bytes unchanged")
            rejects({ before.clear() }, UnsupportedOperationException::class.java); ordered(rebased)
            val full = RuntimeLogStore(1); full.restore(Collections.singletonList(event(Long.MAX_VALUE - 2)))
            check(append(full) && append(full) && full.snapshot()[0].sequence == 1L && full.droppedCount() == 2L, "full append dense then trim exact")
            val invalid = RuntimeLogStore(2); check(append(invalid), "invalid history early"); val early = invalid.snapshot()
            for (bad in Arrays.asList(Arrays.asList(event(7), null), Arrays.asList(event(7), event(7)), Arrays.asList(event(7), event(2)))) {
                rejects({ invalid.restore(bad) }, IllegalArgumentException::class.java); check(invalid.snapshot().equals(early) && invalid.droppedCount() == 0L, "invalid full history rejected before mutation")
            }
            invalid.restore(Arrays.asList(event(2), event(5))); check(invalid.snapshot()[1].sequence == 6L, "invalid restore did not consume one-shot")
            scoped { scope ->
                val p = RuntimeLogPersistence(scope.root.toFile()); val legal = Arrays.asList(event(2), event(5)); check(p.save(legal, null) == 2, "invalid file fixture")
                val text = String(Files.readAllBytes(p.file().toPath()), StandardCharsets.UTF_8)
                for (changed in arrayOf(text.replace("\n5\t", "\n2\t"), text.replace("\n5\t", "\n1\t"), text.replace("\n5\t", "\n" + Long.MAX_VALUE + "\t"))) {
                    Files.write(p.file().toPath(), changed.toByteArray(StandardCharsets.UTF_8)); val live = RuntimeLogStore(4); check(append(live), "corrupt file early")
                    val tasks = ManualExecutor(); val worker = scope.worker(live, p, tasks); tasks.runOne()
                    check(worker.recoveryStatus() == RuntimeLogPersistence.Status.CORRUPT && live.size() == 1 && live.snapshot()[0].sequence == 0L, "actual invalid V1 still CORRUPT; early retained"); worker.close()
                }
            }
        }
        private fun sequenceSinks() {
            val store = RuntimeLogStore(4); store.restore(Collections.singletonList(event(Long.MAX_VALUE - 2))); check(append(store), "sink reach max"); val seen = AtomicInteger()
            store.addSink { _, _ -> throw IllegalStateException("synthetic sink") }; store.addSink { _, _ -> seen.incrementAndGet() }; check(append(store) && seen.get() == 1, "RuntimeException isolated at rollover")
            val fatalStore = RuntimeLogStore(4); fatalStore.restore(Collections.singletonList(event(Long.MAX_VALUE - 2))); check(append(fatalStore), "fatal reach max")
            val injected: Error = AssertionError("synthetic sequence sink"); val bad = RuntimeLogSink { _, _ -> throw injected }; fatalStore.addSink(bad); var caught: Error? = null
            try { append(fatalStore) } catch (failure: Error) { caught = failure }
            check(caught === injected && fatalStore.size() == 3 && fatalStore.snapshot()[2].sequence == 2L, "rollover commits then propagates exact sink Error")
            fatalStore.removeSink(bad); fatalStore.addSink { _, _ -> seen.incrementAndGet() }; check(append(fatalStore) && seen.get() == 2, "publisher recovers after rollover fatal")
            val reentrant = RuntimeLogStore(4); reentrant.restore(Collections.singletonList(event(Long.MAX_VALUE - 2))); check(append(reentrant), "reentrant reach max")
            val once = AtomicBoolean(); val delivered = ArrayList<List<RuntimeLogEvent?>?>()
            reentrant.addSink { _, _ -> if (once.compareAndSet(false, true)) append(reentrant) }; reentrant.addSink { s, _ -> delivered.add(s) }; check(append(reentrant), "rollover with reentry")
            check(delivered.size == 2 && delivered[0]!!.size == 3 && delivered[1]!!.size == 4 && delivered[1]!![3]!!.sequence == 3L, "one reentrant append drains newer completed snapshot only")
        }
        private fun sequencePublication() = scoped { scope ->
            val store = RuntimeLogStore(8, SequenceClock()); store.restore(Collections.singletonList(event(Long.MAX_VALUE - 2)))
            val entered = scope.gate(); val release = scope.gate(); val once = AtomicBoolean(); val failure = AtomicReference<Throwable>(); val delivered: MutableList<List<RuntimeLogEvent?>?> = CopyOnWriteArrayList()
            store.addSink { _, _ -> if (once.compareAndSet(false, true)) { entered.countDown(); try { await(release) } catch (problem: Throwable) { failure.compareAndSet(null, problem) } } }
            store.addSink { s, _ -> delivered.add(s) }
            val publisher = Thread { try { check(append(store), "cross-limit initial append") } catch (problem: Throwable) { failure.compareAndSet(null, problem) } }
            scope.start(publisher); await(entered); val producers = ArrayList<Thread>()
            for (i in 0 until 3) {
                val producer = Thread { try { store.snapshot(); for (j in 0 until 11) check(append(store), "cross-limit concurrent append") } catch (problem: Throwable) { failure.compareAndSet(null, problem) } }
                producers.add(producer); scope.start(producer)
            }
            for (producer in producers) join(producer)
            check(failure.get() == null && store.size() == 8 && store.droppedCount() == 27L, "cross-limit producers finish while sink blocked, exact eviction")
            val latest = store.snapshot(); ordered(latest); release.countDown(); join(publisher)
            check(failure.get() == null && delivered.size == 2, "cross-limit publisher drains exactly old then latest")
            check(delivered[0]!![1]!!.sequence == Long.MAX_VALUE - 1 && delivered[1]!!.equals(latest), "numeric decrease is not stale publication")
            check(delivered[0]!![1]!!.wallMillis > latest[latest.size - 1].wallMillis, "payload clock proves newer causal snapshot after numeric rollover")
            check(append(store) && delivered.size == 3, "publisher restarts after extreme coalescing")
        }
        private fun sequenceCase(name: String?) {
            when (name!!) {
                "seq-noearly" -> nearMaxProgress(false); "seq-early" -> nearMaxProgress(true); "seq-overflow" -> legalMaxRecovery()
                "seq-protection" -> { sequenceRecoveryMatrix(); sequenceRejectsAndSnapshots(); sequenceSinks(); sequencePublication() }
                else -> throw IllegalArgumentException("unknown sequence case")
            }
            passed("sequence case " + name)
        }
        @Suppress("UNUSED_PARAMETER")
        private fun export(base: Path) {
            val store = RuntimeLogStore(); store.append(RuntimeLogEventKind.INFERENCE_COMPLETED, RuntimeLogSource.IME, ID, "ok", 1234567890L); val ticket = store.snapshot(); append(store)
            val out = ByteArrayOutputStream(); val bytes = RuntimeLogCodec.writeSnapshot(ticket, out); val expected = (RuntimeLogCodec.formatLine(ticket[0]) + "\n").toByteArray(StandardCharsets.UTF_8)
            check(bytes == expected.size.toLong() && Arrays.equals(out.toByteArray(), expected), "captured UTF8 snapshot, actual byte count not event count")
            val untouched = ByteArrayOutputStream(); rejects({ RuntimeLogCodec.writeSnapshot(Collections.nCopies(1001, event(0)), untouched) }, IOException::class.java); check(untouched.size() == 0, "oversized snapshot preflight before any write")
            rejects({ RuntimeLogCodec.writeSnapshot(ticket, object : OutputStream() { override fun write(value: Int) { throw IOException("private provider message") } }) }, IOException::class.java)
            rejects({ RuntimeLogCodec.writeSnapshot(ticket, object : ByteArrayOutputStream() { override fun flush() { throw IOException("flush") } }) }, IOException::class.java)
            val closed = AtomicBoolean(); val success = AtomicBoolean()
            try { outputScope(object : ByteArrayOutputStream() { override fun close() { closed.set(true); throw IOException("close") } }) { RuntimeLogCodec.writeSnapshot(ticket, it) } }
            catch (failure: IOException) { check(closed.get(), "failure from owner close") }
            try { outputScope(object : ByteArrayOutputStream() { override fun close() { throw IOException("close") } }) { RuntimeLogCodec.writeSnapshot(ticket, it) }; success.set(true) } catch (expectedFailure: IOException) { /* fixed owner failure */ }
            check(!success.get(), "owner never claims success before close"); val capped = ByteArrayOutputStream()
            rejects({ RuntimeLogCodec.writeSnapshot(Collections.singletonList(event(0)), object : OutputStream() {
                override fun write(b: Int) { capped.write(b) }
                override fun write(b: ByteArray?, off: Int, len: Int) { super.write(b, off, Math.min(len.toLong(), RuntimeLogCodec.MAX_EXPORT_BYTES).toInt()); throw IOException("malicious-oversize") }
            }) }, IOException::class.java)
            rejects({ RuntimeLogCodec.writeSnapshot(null, ByteArrayOutputStream()) }, IllegalArgumentException::class.java)
            rejects({ RuntimeLogCodec.writeSnapshot(ticket, null) }, IllegalArgumentException::class.java)
            rejects({ RuntimeLogCodec.writeSnapshot(Arrays.asList(event(0), null), ByteArrayOutputStream()) }, IOException::class.java)
            passed("immutable picker-time UTF8 export through RuntimeLogCodec, exact byte preflight/cap, write/flush failures, close owned by caller")
        }
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            if (args!!.isNotEmpty()) { sequenceCase(args[0]); return }
            val base = Files.createTempDirectory("runtime-log-core-")
            try {
                eventBoundary(); publication(); persistence(base); paths(base); workers(base); ownedWorker(base); workerFailures(base); export(base)
                sequenceCase("seq-noearly"); sequenceCase("seq-early"); sequenceCase("seq-overflow"); sequenceCase("seq-protection")
                RuntimeLogWorkerErrorTest.main(emptyArray()); println("PASS RuntimeLogCoreTest: " + groups + " groups")
            } finally { deleteTree(base) }
        }
    }
}
