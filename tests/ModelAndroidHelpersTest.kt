import org.llmasr.minimal.model.*
import org.llmasr.minimal.modelmanagement.*
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.transcription.TextExportController
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Real small-file verification protocol; JVM fsync is not Android power-loss acceptance. */
class ModelAndroidHelpersTest {
    class MemoryGuard : ModelVerification.MaintenanceGuard {
        override fun acquire() = ModelVerification.MaintenanceLease { }
        override fun invalidate() { }
    }
    /** Explicit synthetic JVM implementation, shared only by core fixtures. */
    class TestIo(val root: Path) : ModelVerification.Io {
        val reads = AtomicInteger(); val identities = AtomicInteger(); val writes = AtomicInteger()
        var fail: String? = null
        var beforeWrite: ((String) -> Unit)? = null
        data class Fault(val name: String, val kind: Int, val occurrence: Int, val stage: String, val failure: Throwable)
        val faults = ArrayList<Fault>()
        var atStage: ((String, Int, Int, String) -> Unit)? = null
        private val occurrences = HashMap<String, Int>()
        private var writingName = ""; private var writingKind = 0; private var writingOccurrence = 0
        private fun fault(stage: String) {
            atStage?.invoke(writingName, writingKind, writingOccurrence, stage)
            val exact = faults.firstOrNull { it.name == writingName && it.kind == writingKind && it.occurrence == writingOccurrence && it.stage == stage }
            if (exact != null) throw exact.failure
            if (fail == stage) throw IOException("injected $stage")
        }
        override fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)
        override fun identity(file: File, directory: Boolean): String {
            identities.incrementAndGet(); fault("stat")
            val path = file.toPath()
            if (if (directory) !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) else !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw IOException("unsafe type")
            val a = Files.readAttributes(path, "unix:dev,ino,mode,size,lastModifiedTime,ctime", LinkOption.NOFOLLOW_LINKS)
            return listOf("dev", "ino", "mode", "size", "lastModifiedTime", "ctime").joinToString(":") { a[it].toString() }
        }
        override fun read(name: String, limit: Int): ByteArray? {
            reads.incrementAndGet(); fault("read")
            val path = root.resolve(name)
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw IOException("unsafe proof type")
            val bytes = ByteArrayOutputStream()
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                while (true) { val c = input.read(); if (c == -1) break; if (bytes.size() == limit) throw IOException("bounded read"); bytes.write(c) }
            }
            return bytes.toByteArray()
        }
        override fun write(name: String, bytes: ByteArray) {
            writes.incrementAndGet()
            writingName = name; writingKind = if (bytes.size >= 8 && name != "anchor") java.nio.ByteBuffer.wrap(bytes, 4, 4).int else 0
            val key = "$name:$writingKind"; writingOccurrence = (occurrences[key] ?: 0) + 1; occurrences[key] = writingOccurrence
            beforeWrite?.invoke(name); fault("open")
            Files.createDirectories(root)
            val target = root.resolve(name); val temp = root.resolve("$name.tmp")
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw IOException("unsafe proof")
            FileOutputStream(temp.toFile()).use { output ->
                fault("write"); output.write(bytes); fault("flush"); output.flush(); fault("fsync"); output.fd.sync(); fault("close")
            }
            fault("rename"); Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            fault("afterRename"); fault("dirsync"); FileChannel.open(root, StandardOpenOption.READ).use { it.force(true) }
            fault("afterSync")
        }
    }
    class PersistentFixture(val base: Path = Files.createTempDirectory("verification-contract-")) : AutoCloseable {
        val model = Files.createDirectories(base.resolve("model")); val io = TestIo(base.resolve("noBackup"))
        val ready = ModelReadiness(); val hashes = AtomicInteger()
        val manifest = ModelManifest(Collections.singletonList(ModelEntry("a", 3, "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")))
        val proof = ModelVerification(manifest, ready, io, MemoryGuard())
        val repo = object : ModelRepository(manifest, model.toFile(), { Long.MAX_VALUE }, proof) {
            override fun verifyAll(progress: Progress?, gate: CancelGate?) { hashes.incrementAndGet(); super.verifyAll(progress, gate) }
        }
        val access = ModelAccess(repo, ready)
        fun install() { Files.write(model.resolve("a"), byteArrayOf(1, 2, 3)) }
        fun verify() {
            val control = ModelOperationControl(); val op = control.tryBegin(ModelOperationControl.Kind.VERIFY)
            val token = proof.begin(repo); repo.verifyAll(null, ModelRepository.NEVER_CANCEL)
            require(proof.commit(repo, token, commitGate(control, op, ready)), "full SHA creates proof"); control.complete(op, true)
        }
        private val workers = java.util.concurrent.CopyOnWriteArrayList<Thread>()
        fun worker(body: () -> Unit): Thread = Thread(body, "proof-fixture-worker").also { workers.add(it); it.start() }
        override fun close() {
            if (workers.any { it.isAlive }) throw IOException("fixture worker exit unconfirmed; retained $base")
            remove(base)
        }
    }
    private class Queue : Executor {
        var task: Runnable? = null
        override fun execute(command: Runnable?) { require(task == null, "single worker"); task = command }
        fun run() { val next = task; task = null; next!!.run() }
    }
    private class Fixture : AutoCloseable {
        val queue = Queue(); val owner = TaskCoordinator(queue)
        val persistent = PersistentFixture(); val ready = persistent.ready; val repo = persistent.repo
        val state = ModelManagementState(); val control = ModelOperationControl(); val tickets = ModelPickerTickets()
        val controller = ModelManagementController(owner, repo, ready, control, state)
        fun source(): ModelSource = ModelRepositoryCancelTest.Source(byteArrayOf(1, 2, 3), "a")
        fun page(id: String?): ModelPageSession<ModelSource> = ModelPageSession(tickets, controller, id)
        override fun close() { if (queue.task != null) queue.run(); require(!owner.isBusy(), "owner finalized before cleanup"); persistent.close() }
    }
    companion object {
        private fun require(ok: Boolean, message: String) { if (!ok) throw AssertionError(message) }
        private fun commitGate(control: ModelOperationControl, operation: ModelOperationControl.Operation, ready: ModelReadiness): ModelVerification.CommitGate = object : ModelVerification.CommitGate {
            override fun reserve(token: ModelReadiness.VerifyToken): Boolean = control.tryReserveProofCommit(operation, ready, token)
            override fun succeed(token: ModelReadiness.VerifyToken): Boolean = control.trySucceed(operation, ready, token)
        }
        private inline fun fixture(body: (Fixture) -> Unit) { Fixture().use(body) }
        private fun remove(path: Path) {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
            Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
        private fun denied(block: () -> Unit) { try { block() } catch (expected: IOException) { return }; throw AssertionError("expected fail closed") }
        private fun missing(f: PersistentFixture) {
            f.install(); Files.write(f.model.resolve("a"), byteArrayOf(9, 9, 9))
            require(!f.access.restoreFast() && !f.ready.isReady, "missing proof must not restore READY")
            denied { f.access.requireReady() }; require(f.hashes.get() == 0, "unknown proof never hashes")
        }
        private fun revoke(f: PersistentFixture) {
            f.install(); f.verify(); f.io.fail = "open"
            denied { f.repo.deleteAll() }
            require(Files.exists(f.model.resolve("a")) && Files.readAllBytes(f.model.resolve("a")).contentEquals(byteArrayOf(1, 2, 3)), "durable revoke must precede mutation")
            require(!f.ready.isReady, "revoke failure clears current READY"); f.io.fail = null
        }
        private fun cancel(f: PersistentFixture) {
            f.install(); f.verify()
            val c = ModelOperationControl(); val op = c.tryBegin(ModelOperationControl.Kind.VERIFY)
            val t = f.proof.begin(f.repo); f.repo.verifyAll(null, ModelRepository.NEVER_CANCEL)
            require(c.requestCancel(op.id), "cancel before reservation wins")
            val accepted = f.proof.commit(f.repo, t, commitGate(c, op, f.ready))
            val fresh = ModelVerification(f.manifest, ModelReadiness(), f.io, MemoryGuard())
            var restored = false; try { restored = fresh.restoreFast(f.repo) } catch (expected: IOException) { }
            require(!restored, "cancel winner must not leave restorable proof")
            require(!accepted, "cancel must defeat proof commit")
            c.complete(op, false)
        }
        private fun request(f: PersistentFixture) {
            f.install(); f.verify(); val hashes = f.hashes.get()
            require(f.access.restoreFast(), "valid proof restores")
            repeat(3) { f.access.requireReady() }
            require(f.hashes.get() == hashes, "request must not hash")
            Files.write(f.model.resolve("a"), byteArrayOf(9, 9, 9)); denied { f.access.requireReady() }
            require(!f.ready.isReady, "stat drift rejects request")
        }
        private fun continuity(f: PersistentFixture) {
            f.install(); f.verify()
            val receipt = f.io.root.resolve("receipt"); val ledger = f.io.root.resolve("ledger"); val anchor = f.io.root.resolve("anchor")
            val r = Files.readAllBytes(receipt); val l = Files.readAllBytes(ledger); val a = Files.readAllBytes(anchor)
            Files.delete(anchor); require(!f.access.restoreFast() && !f.ready.isReady, "ordinary restore cannot self-authorize without noBackup anchor")
            Files.write(anchor, "11111111-1111-1111-1111-111111111111".toByteArray()); denied { f.access.restoreFast() }
            Files.write(anchor, a)
            for (offset in listOf(0, 4, 10, 80)) {
                val corrupt = r.clone(); corrupt[offset] = (corrupt[offset].toInt() xor 1).toByte(); Files.write(receipt, corrupt)
                denied { f.access.restoreFast() }; require(!f.ready.isReady, "schema/policy/generation corruption rejected")
            }
            Files.write(receipt, r + byteArrayOf(0)); denied { f.access.restoreFast() }; Files.write(receipt, r)
            f.proof.beforeMutation(); denied { f.access.restoreFast() }
            Files.write(ledger, l); require(f.access.restoreFast(), "exact original committed proof")
            val outside = Files.createTempFile("proof-external-", ".txt")
            try {
                Files.write(outside, byteArrayOf(8)); Files.delete(receipt); Files.createSymbolicLink(receipt, outside)
                denied { f.access.restoreFast() }; require(Files.readAllBytes(outside).contentEquals(byteArrayOf(8)), "proof symlink external target untouched")
            } finally { Files.deleteIfExists(receipt); Files.deleteIfExists(outside) }
        }
        private fun faults(f: PersistentFixture) {
            for (stage in listOf("open", "write", "flush", "close", "fsync", "rename", "dirsync")) {
                f.install(); f.io.fail = null; f.verify(); f.io.fail = stage
                denied { f.repo.deleteAll() }
                require(Files.exists(f.model.resolve("a")) && !f.ready.isReady, "revoke fault $stage blocks mutation")
            }
            f.io.fail = null; f.verify()
            for (name in listOf("anchor", "receipt", "ledger")) {
                val p = f.io.root.resolve(name); val saved = Files.readAllBytes(p)
                Files.write(p, ByteArray(16385))
                denied { f.access.restoreFast() }; require(!f.ready.isReady, "bounded corrupt $name rejected")
                Files.write(p, saved)
            }
            require(f.access.restoreFast(), "repair exact proof")
            Files.write(f.model.resolve("a.part"), byteArrayOf(1)); denied { f.access.requireReady() }
            require(Files.exists(f.model.resolve("a.part")), "safe orphan retained but never READY")
        }
        private fun reservation(f: PersistentFixture) {
            f.install(); val control = ModelOperationControl(); val op = control.tryBegin(ModelOperationControl.Kind.VERIFY)
            val t = f.proof.begin(f.repo); f.repo.verifyAll(null, ModelRepository.NEVER_CANCEL)
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val error = AtomicReference<Throwable>()
            f.io.beforeWrite = { name -> if (name == "receipt") { entered.countDown(); if (!release.await(3, TimeUnit.SECONDS)) throw IOException("timeout") } }
            val thread = f.worker { try { require(f.proof.commit(f.repo, t, commitGate(control, op, f.ready)), "reserved commit") } catch (failure: Throwable) { error.set(failure) } }
            try {
                require(entered.await(2, TimeUnit.SECONDS), "commit blocked in IO")
                require(!control.requestCancel(op.id) && control.isBusy && !op.isSucceeded, "reservation is not success and control lock remains available")
            } finally { release.countDown(); thread.join(4000); f.io.beforeWrite = null }
            require(!thread.isAlive && error.get() == null && f.ready.isReady, "real IO finalized before owner release")
            control.complete(op, true)
        }
        private fun scopedMutation(f: PersistentFixture) {
            for (kind in listOf("delete", "reclaim", "import", "cleanup")) {
                f.install(); f.verify(); if (kind=="reclaim") Files.write(f.model.resolve("a.part"), byteArrayOf(8))
                val entered = CountDownLatch(1); val release = CountDownLatch(1); val error = AtomicReference<Throwable>()
                val repo = object : ModelRepository(f.manifest, f.model.toFile(), { Long.MAX_VALUE }, f.proof) {
                    override fun deleteManaged(file: File?) {
                        entered.countDown(); if (!release.await(3, TimeUnit.SECONDS)) throw IOException("mutation timeout")
                        super.deleteManaged(file)
                    }
                    override fun openPart(file: File?): OutputStream {
                        if (kind == "import") { entered.countDown(); if (!release.await(3,TimeUnit.SECONDS)) throw IOException("import timeout") }
                        if (kind == "cleanup") return object : FileOutputStream(file) { override fun write(b: ByteArray?, off: Int, len: Int) { throw IOException("copy primary") } }
                        return super.openPart(file)
                    }
                }
                if (kind == "import" || kind == "cleanup") { Files.deleteIfExists(f.model.resolve("a.part")); Files.write(f.model.resolve("a"), byteArrayOf(9,9,9)) }
                val worker = f.worker { try {
                    when (kind) { "delete" -> repo.deleteAll(); "reclaim" -> repo.reclaimOrphanedParts()
                        else -> repo.importFrom(ModelRepositoryCancelTest.Source(byteArrayOf(1,2,3), "a"), null) }
                } catch (failure: Throwable) { error.set(failure) } }
                try {
                    require(entered.await(2, TimeUnit.SECONDS), "mutation entered $kind")
                    var overtook = false
                    try {
                        val c=ModelOperationControl(); val op=c.tryBegin(ModelOperationControl.Kind.VERIFY)
                        val token=f.proof.begin(repo); repo.verifyAll(null,ModelRepository.NEVER_CANCEL)
                        overtook=f.proof.commit(repo,token,commitGate(c,op,f.ready))
                    } catch (expected: IOException) { }
                    require(!overtook, "active mutation must exclude new verification")
                    denied { f.access.restoreFast() }; require(!f.ready.isReady, "active mutation cannot restore READY")
                } finally { release.countDown(); worker.join(4000) }
                require(!worker.isAlive, "mutation actual exit $kind")
                require(error.get()==null || kind=="cleanup", "mutation worker failure $kind: ${error.get()}")
                Files.createDirectories(f.model); Files.deleteIfExists(f.model.resolve("a.part"))
            }
        }
        private fun listeners(f: PersistentFixture) {
            f.install(); f.verify()
            val once=AtomicBoolean(); val callbackFailure=AtomicReference<Throwable>(); val lockBlocked=AtomicBoolean()
            val listener=ModelReadiness.Listener {
                if (once.compareAndSet(false,true)) try {
                    try { f.access.restoreFast() } catch (expected: IOException) { }
                    val finished=CountDownLatch(1)
                    val probe=f.worker { try { f.access.restoreFast() } catch (expected: IOException) { } finally { finished.countDown() } }
                    if (!finished.await(1,TimeUnit.SECONDS)) lockBlocked.set(true)
                    probe.join(1500)
                } catch (failure: Throwable) { callbackFailure.set(failure) }
            }
            f.ready.addListener(listener)
            try { val scope=f.proof.beginMutation(); try { f.proof.beforeMutation() } finally { scope.close() } }
            finally { f.ready.removeListener(listener) }
            require(once.get() && callbackFailure.get()==null && !lockBlocked.get(), "readiness callbacks must run outside journal lock")
            require(!f.ready.isReady, "reentrant observer must not republish old proof")
        }
        private fun workerRetention(f: PersistentFixture) {
            val entered=CountDownLatch(1); val release=CountDownLatch(1); val error=AtomicReference<Throwable>()
            val thread=f.worker { try { entered.countDown(); release.await(3,TimeUnit.SECONDS) } catch (failure: Throwable) { error.set(failure) } }
            try {
                require(entered.await(1,TimeUnit.SECONDS), "fixture worker entered")
                var rejected=false; try { f.close() } catch (expected: IOException) { rejected=true }
                require(rejected && Files.isDirectory(f.base), "live fixture worker must retain directory")
                var interrupted: InterruptedException? = null
                Thread.currentThread().interrupt()
                try { thread.join(1) } catch (failure: InterruptedException) { interrupted=failure }
                finally { Thread.interrupted() }
                require(interrupted!=null && thread.isAlive, "actual interrupted join leaves worker live")
                val primary=interrupted!!
                try { throw primary } catch (failure: Throwable) { try { f.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }; require(failure===primary && failure.suppressed.size==1 && Files.isDirectory(f.base), "fixture preserves interrupted primary and retained directory") }
            } finally { release.countDown(); thread.join(4000) }
            require(!thread.isAlive && error.get()==null, "fixture actual worker exit before cleanup")
        }
        private fun pages() {
            fixture { f ->
                val p = f.page(null); p.foreground(true); val code = p.beginPicker()
                require(!f.owner.isBusy() && !TextExportController.isRequest(code), "picker does not own worker")
                p.foreground(false); require(p.result(code, f.source()) && !p.consume(f.controller::startImport), "background defers picker")
                p.foreground(true); require(p.consume(f.controller::startImport) && p.ownsActive(), "page owns import")
                f.queue.run(); require(f.ready.isReady && !f.owner.isBusy(), "actual controller commits persistent proof"); p.destroy()
            }
            fixture { f ->
                f.persistent.install(); require(f.controller.startVerify(), "full verify admitted"); f.queue.run()
                val hashes = f.persistent.hashes.get(); f.ready.invalidate(); require(f.controller.startRestore(), "explicit restore worker")
                require(f.owner.isBusy() && !f.ready.isReady, "UI admission performs no restore IO")
                f.queue.run(); require(f.ready.isReady && f.persistent.hashes.get() == hashes, "management quick restore never SHA")
                require(f.controller.startVerify(), "forced full verify"); f.queue.run(); require(f.persistent.hashes.get() == hashes + 1, "VERIFY always full SHA")
            }
            fixture { f ->
                require(f.controller.startImport(f.source()), "cancel fixture admitted"); val id = f.control.peekActive().id
                val p = f.page(id); p.stopped(false); require(f.control.peekActive().isCancelRequested && f.owner.isBusy(), "cancel retains owner")
                f.queue.run(); require(f.state.current().phase == ModelManagementState.Phase.CANCELLED && !f.owner.isBusy(), "cancel real finalization"); p.destroy()
            }
            fixture { f ->
                val p=f.page(null);p.foreground(true)
                val code=p.beginPicker(ModelPickerTickets.Mode.ZIP)
                require(!p.result(code,ModelPickerTickets.Mode.DIRECTORY,f.source()),"ZIP stale or mismatched picker cannot import")
                require(p.result(code,ModelPickerTickets.Mode.ZIP,f.source()),"typed ZIP callback")
                p.foreground(false)
                require(!p.consumeTyped {true},"typed background callback deferred")
                p.foreground(true)
                var mode:ModelPickerTickets.Mode?=null
                require(p.consumeTyped {selection -> mode=selection.mode;f.controller.startImport(selection.value)},"typed foreground import")
                require(mode==ModelPickerTickets.Mode.ZIP,"immutable ZIP ticket mode")
                f.queue.run();require(f.ready.isReady,"typed import existing proof protocol")
                p.destroy();require(!p.result(code,ModelPickerTickets.Mode.ZIP,f.source()),"destroyed typed callback rejected")
            }
            val tickets = ModelPickerTickets()
            for (code in ModelPickerTickets.FIRST..ModelPickerTickets.LAST) { val t = tickets.begin(); require(t.requestCode == code && !TextExportController.isRequest(code), "picker unique range"); tickets.release(t) }
            try { tickets.begin(); throw AssertionError("picker exhaustion") } catch (expected: IllegalStateException) { }
            require(ModelUiText.bytes(-1).contains("未知") && !ModelUiText.bytes(0).contains("未知"), "space text")
            require(!ModelUiText.safeError("content://provider/private").contains("content:"), "provider privacy")
        }
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<String>?) {
            if (args != null && args.isNotEmpty()) {
                val mode = args[0]
                if (mode.startsWith("crash-") || mode == "reject") {
                    val f = PersistentFixture(Paths.get(args[1]))
                    if (mode == "reject") {
                        var restored = false; try { restored = f.access.restoreFast() } catch (expected: IOException) { }
                        require(!restored && !f.ready.isReady && f.hashes.get() == 0, "interrupted transaction cannot restore or hash")
                        println("PASS independent JVM rejects interrupted proof"); return
                    }
                    f.install(); f.verify()
                    val control = ModelOperationControl(); val op = control.tryBegin(ModelOperationControl.Kind.VERIFY)
                    val token = f.proof.begin(f.repo)
                    if (mode == "crash-dirty") Runtime.getRuntime().halt(23)
                    if (mode == "crash-part") { Files.write(f.model.resolve("a.part"), byteArrayOf(7)); Runtime.getRuntime().halt(23) }
                    if (mode == "crash-finalhash") { Files.write(f.model.resolve("a"), byteArrayOf(9, 9, 9)); Runtime.getRuntime().halt(23) }
                    f.repo.verifyAll(null, ModelRepository.NEVER_CANCEL)
                    f.io.beforeWrite = { name -> if (name == "ledger") Runtime.getRuntime().halt(23) }
                    f.proof.commit(f.repo, token, commitGate(control, op, f.ready))
                    throw AssertionError("crash boundary not reached")
                }
                if (mode == "write" || mode == "read") {
                    val f = PersistentFixture(Paths.get(args[1]))
                    if (mode == "write") { f.install(); f.verify(); require(f.hashes.get() == 1, "writer real SHA") }
                    else { require(f.access.restoreFast() && f.hashes.get() == 0, "independent JVM restores without hash"); repeat(3) { f.access.requireReady() }; require(f.hashes.get() == 0, "request must not hash") }
                    println("PASS independent JVM $mode"); return
                }
                PersistentFixture().use { f -> when (mode) {
                    "missing" -> missing(f); "revoke" -> revoke(f); "cancel" -> cancel(f); "request" -> request(f)
                    "faults" -> faults(f); "continuity" -> continuity(f); "reservation" -> reservation(f)
                    "scoped" -> scopedMutation(f); "listeners" -> listeners(f); "workers" -> workerRetention(f); else -> throw AssertionError(mode)
                } }
            } else {
                for (mode in listOf("missing", "revoke", "cancel", "request", "faults", "continuity", "reservation", "scoped", "listeners", "workers")) main(arrayOf(mode))
                pages()
            }
            println("PASS persistent model verification production contracts")
        }
    }
}
