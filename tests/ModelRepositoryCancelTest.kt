import org.llmasr.minimal.model.ModelEntry
import org.llmasr.minimal.model.ModelManifest
import org.llmasr.minimal.modelmanagement.ModelOperationControl
import org.llmasr.minimal.model.ModelRepository
import org.llmasr.minimal.model.ModelSource
import java.io.*
import java.nio.file.*
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Deterministic production IO boundaries. All gates/threads retain bounded teardown. */
class ModelRepositoryCancelTest {
    internal class Source(b: ByteArray, vararg names: String) : ModelSource {
        internal val data: MutableMap<String?, ByteArray> = HashMap()
        internal var opens = 0; internal var closed = false
        internal var failRead = false; internal var failClose = false
        internal var readGate: Gate? = null; internal var closeGate: Gate? = null
        init { for (n in names) data[n] = b }
        override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> {
            val refs: MutableMap<String?, ModelSource.UriRef?> = HashMap()
            for (n in expected!!) if (data.containsKey(n)) refs[n] = ModelSource.UriRef(n)
            return refs
        }
        override fun open(ref: ModelSource.UriRef?): InputStream {
            opens++
            return object : FilterInputStream(ByteArrayInputStream(data[ref!!.handle()])) {
                override fun read(b: ByteArray?, off: Int, len: Int): Int {
                    if (readGate != null) readGate!!.block()
                    if (failRead) throw IOException("injected read")
                    return super.read(b, off, len)
                }
                override fun close() {
                    try { if (closeGate != null) closeGate!!.block(); super.close() } finally { closed = true }
                    if (failClose) throw IOException("injected close")
                }
            }
        }
    }
    internal class Gate : AutoCloseable {
        private val entered = CountDownLatch(1); private val release = CountDownLatch(1)
        internal fun block() {
            entered.countDown()
            try { if (!release.await(4, TimeUnit.SECONDS)) throw IOException("gate timeout") }
            catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException(e) }
        }
        internal fun await() { check(entered.await(3, TimeUnit.SECONDS)) }
        override fun close() { release.countDown() }
    }
    internal fun interface IO { @Throws(Exception::class) fun run() }
    internal class Worker(io: IO) : AutoCloseable {
        internal val error = AtomicReference<Throwable>()
        internal val thread = Thread { try { io.run() } catch (e: Throwable) { error.set(e) } }
        init { thread.isDaemon = true; thread.start() }
        internal fun join() { thread.join(5000); check(!thread.isAlive) }
        override fun close() {
            thread.join(5000)
            if (thread.isAlive) { thread.interrupt(); thread.join(1000); throw AssertionError("worker leaked") }
        }
    }
    private class Control : ModelRepository.CancelGate {
        val control = ModelOperationControl(); val op = control.tryBegin(ModelOperationControl.Kind.IMPORT)
        override fun cancelled(): Boolean = control.isCancelled(op)
        override fun reservePublish(): Boolean = control.tryReservePublish(op)
        fun cancel() { control.requestCancel(op.id) }
    }
    companion object {
        private var checks = 0
        private fun check(b: Boolean) { checks++; if (!b) throw AssertionError("check " + checks) }
        private fun bytes(n: Int): ByteArray = ByteArray(n) { it.toByte() }
        private fun entry(name: String, b: ByteArray): ModelEntry {
            val s = StringBuilder(); for (v in MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", v.toInt() and 255))
            return ModelEntry(name, b.size.toLong(), s.toString())
        }
        internal fun manifest(b: ByteArray, vararg names: String): ModelManifest {
            val es = ArrayList<ModelEntry>(); for (n in names) es.add(entry(n, b)); return ModelManifest(es)
        }
        internal fun dir(): File = Files.createTempDirectory("model-core-").toFile()
        private fun fails(io: IO): IOException {
            try { io.run(); throw AssertionError("expected IO failure") } catch (e: IOException) { checks++; return e }
        }
        // Java TWR suppression must call the Java member (including self-suppression IAE).
        private inline fun workerScope(w: Worker, body: (Worker) -> Unit) {
            var primary: Throwable? = null
            try { body(w) } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                if (primary == null) w.close()
                else try { w.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) }
            }
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            ModelAndroidHelpersTest.PersistentFixture().use { f ->
                f.install(); f.verify(); Files.write(f.model.resolve("a.part"), byteArrayOf(8))
                f.io.fail = "open"
                try { f.repo.reclaimOrphanedParts(); throw AssertionError("unguarded direct reclaim") } catch (expected: IOException) { }
                check(Files.readAllBytes(f.model.resolve("a.part")).contentEquals(byteArrayOf(8)))
                f.io.fail = null; f.repo.reclaimOrphanedParts(); check(!Files.exists(f.model.resolve("a.part")))
                f.verify(); f.io.fail = "open"; Files.write(f.model.resolve("a"), byteArrayOf(9, 9, 9))
                try { f.repo.importFrom(Source(byteArrayOf(1, 2, 3), "a"), null); throw AssertionError("unguarded direct import") } catch (expected: IOException) { }
                check(Files.readAllBytes(f.model.resolve("a")).contentEquals(byteArrayOf(9, 9, 9)))
                check(!Files.exists(f.model.resolve("a.part")))
                f.io.fail = null; f.install(); f.verify(); Files.write(f.model.resolve("a"), byteArrayOf(9))
                try { f.repo.importFrom(Source(byteArrayOf(1, 2, 3)), null); throw AssertionError("invalid size and missing source accepted") } catch (expected: IOException) { }
                check(!f.ready.isReady)
            }
            val b = bytes(64)
            run {
                val d = dir(); val r = ModelRepository(manifest(b, "a"), d, { Long.MAX_VALUE }); val c = Control(); c.cancel(); val src = Source(b, "a")
                check(fails { r.importFrom(src, null, c) } is ModelRepository.CancelledException); check(src.opens == 0); check(!File(d, "a").exists())
            }
            run {
                val d = dir(); val r = ModelRepository(manifest(b, "a"), d, { Long.MAX_VALUE }); val c = Control(); val src = Source(b, "a"); val g = Gate(); src.readGate = g
                workerScope(Worker { r.importFrom(src, null, c) }) { w ->
                    try { g.await(); c.cancel(); check(w.thread.isAlive) } finally { g.close() }
                    w.join(); check(w.error.get() is ModelRepository.CancelledException)
                }
                check(src.closed); check(!File(d, "a").exists()); check(!File(d, "a.part").exists())
            }
            run {
                val d = dir(); val r = ModelRepository(manifest(b, "a", "b"), d, { Long.MAX_VALUE }); val src = Source(b, "a", "b"); val c = Control()
                fails { r.importFrom(src, { stage, file, _, _, _, _, _, _, _ -> if (stage == ModelRepository.Stage.PUBLISHING && "b".equals(file)) c.cancel() }, c) }
                check(File(d, "a").isFile); check(!File(d, "b").exists()); check(!File(d, "b.part").exists())
                val retry = Source(b, "b"); r.importFrom(retry, null); check(retry.opens == 1); r.verifyAll()
                val empty = Source(b); r.importFrom(empty, null); check(empty.opens == 0)
            }
            run {
                val d = dir(); val g = Gate(); val c = Control()
                val r = object : ModelRepository(manifest(b, "a", "b"), d, { Long.MAX_VALUE }) {
                    override fun publishPart(part: File?, dest: File?) { g.block(); super.publishPart(part, dest) }
                }
                workerScope(Worker { r.importFrom(Source(b, "a", "b"), null, c) }) { w ->
                    try { g.await(); c.cancel() } finally { g.close() }; w.join(); check(w.error.get() is ModelRepository.CancelledException)
                }
                check(File(d, "a").isFile); check(!File(d, "b").exists())
            }
            for (target in arrayOf(ModelRepository.Stage.CHECKING_EXISTING, ModelRepository.Stage.VERIFYING_FILE, ModelRepository.Stage.FINAL_VERIFY)) {
                val d = dir(); val big = bytes(1024 * 1024 + 3); val r = ModelRepository(manifest(big, "a"), d, { Long.MAX_VALUE })
                if (target == ModelRepository.Stage.CHECKING_EXISTING) Files.write(File(d, "a").toPath(), big)
                val c = Control()
                val e = fails { r.importFrom(Source(big, "a"), { stage, _, _, _, fd, _, _, _, _ -> if (stage == target && fd > 0) c.cancel() }, c) }
                check(e is ModelRepository.CancelledException); check(!File(d, "a.part").exists())
                check(File(d, "a").exists() == (target != ModelRepository.Stage.VERIFYING_FILE))
            }
            for (fault in Arrays.asList("read", "write", "out-close", "in-close", "rename")) {
                val d = dir(); val src = Source(b, "a"); src.failRead = fault.equals("read"); src.failClose = fault.equals("in-close")
                val r = object : ModelRepository(manifest(b, "a"), d, { Long.MAX_VALUE }) {
                    override fun openPart(f: File?): OutputStream = object : FilterOutputStream(super.openPart(f)) {
                        override fun write(b: ByteArray?, o: Int, n: Int) { if (fault.equals("write")) throw IOException("write"); out.write(b, o, n) }
                        override fun close() { super.close(); if (fault.equals("out-close")) throw IOException("close") }
                    }
                    override fun publishPart(p: File?, f: File?) { if (fault.equals("rename")) throw IOException("rename"); super.publishPart(p, f) }
                }
                fails { r.importFrom(src, null) }; check(src.closed); check(!File(d, "a").exists()); check(!File(d, "a.part").exists())
            }
            run {
                val d = dir(); val r = ModelRepository(manifest(b, "a", "b"), d, { Long.MAX_VALUE }); val src = Source(b, "a")
                fails { r.importFrom(src, null) }; check(src.opens == 0); check(!File(d, "a").exists()); Files.write(File(d, "a.part").toPath(), b)
                val low = ModelRepository(manifest(b, "a", "b"), d, { if (File(d, "a.part").exists()) 0 else ModelRepository.SPACE_PAD + 64 })
                val all = Source(b, "a", "b"); fails { low.importFrom(all, null) }; check(all.opens == 0); check(!File(d, "a.part").exists())
            }
            for (size in intArrayOf(0, 3, 1024 * 1024 + 7)) {
                val data = bytes(size); val d = dir(); val r = ModelRepository(manifest(data, "a"), d, { Long.MAX_VALUE }); val sawCopy = AtomicBoolean(); val sawHash = AtomicBoolean()
                r.importFrom(Source(data, "a"), { stage, _, i, _, fd, ft, sd, st, _ ->
                    check(fd >= 0 && fd <= ft && sd >= 0 && sd <= st); check(i >= 0)
                    if (stage == ModelRepository.Stage.COPYING) { sawCopy.set(true); check(!File(d, "a").exists()) }
                    if (stage == ModelRepository.Stage.VERIFYING_FILE) sawHash.set(true)
                })
                check(sawCopy.get() && sawHash.get()); r.verifyAll()
            }
            println("PASS " + checks + " deterministic repository cancel/IO/progress checks")
        }
    }
}
