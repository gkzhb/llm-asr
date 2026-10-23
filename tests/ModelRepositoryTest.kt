import org.llmasr.minimal.model.*
import java.io.*
import java.nio.file.*
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

/** Real repository IO/safety/reuse checks, no production test backdoors. */
class ModelRepositoryTest {
    internal open class FakeSource : ModelSource {
        internal val store: MutableMap<String?, ByteArray?> = HashMap()
        internal val failOpen: MutableSet<String?> = HashSet(); internal val failClose: MutableSet<String?> = HashSet()
        internal val shortRead: MutableSet<String?> = HashSet(); internal val overLong: MutableSet<String?> = HashSet()
        override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> {
            val out: MutableMap<String?, ModelSource.UriRef?> = LinkedHashMap()
            for (n in expected!!) if (store.containsKey(n)) out[n] = ModelSource.UriRef(n)
            return out
        }
        override fun open(ref: ModelSource.UriRef?): InputStream {
            val name = ref!!.handle() as String?
            if (failOpen.contains(name)) throw IOException("open failed: " + name)
            val payload = store[name] ?: throw IOException("no payload: " + name)
            val toDeliver: ByteArray
            if (overLong.contains(name)) {
                toDeliver = ByteArray(payload.size + 1024); System.arraycopy(payload, 0, toDeliver, 0, payload.size)
            } else if (shortRead.contains(name)) toDeliver = if (payload.size > 4) Arrays.copyOf(payload, payload.size / 2) else payload
            else toDeliver = payload
            return object : ByteArrayInputStream(toDeliver) {
                override fun close() { if (failClose.contains(name)) throw IOException("close failed: " + name); super.close() }
            }
        }
    }
    private class FakeSpace(free: Long) : ModelRepository.SpaceProvider {
        val free = AtomicLong(free)
        override fun usableBytes(): Long = free.get()
        fun set(v: Long) { free.set(v) }
    }
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check " + checks) }
        private fun sha256(data: ByteArray): String {
            val h = MessageDigest.getInstance("SHA-256"); h.update(data); val s = StringBuilder()
            for (b in h.digest()) s.append(String.format("%02x", b.toInt() and 255)); return s.toString()
        }
        private fun bytes(n: Int): ByteArray = ByteArray(n) { (it and 0xff).toByte() }
        private fun freshDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()
        internal fun entry(name: String, data: ByteArray): ModelEntry = ModelEntry(name, data.size.toLong(), sha256(data))
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            val a = bytes(4096); val b = bytes(8192); val dir1 = freshDir("model-r1-")
            val m1 = ModelManifest(Arrays.asList(entry("a.bin", a), entry("b.bin", b)))
            val s1 = FakeSource(); s1.store["a.bin"] = a; s1.store["b.bin"] = b
            val r1 = ModelRepository(m1, dir1, FakeSpace(256L * 1024 * 1024)); r1.importFrom(s1, null)
            check(File(dir1, "a.bin").isFile); check(File(dir1, "b.bin").isFile); check(!File(dir1, "a.bin.part").exists()); r1.verifyAll()
            val tampered = bytes(1024); tampered[0] = (tampered[0].toInt() xor 1).toByte(); val dir2 = freshDir("model-r2-")
            val m2 = ModelManifest(Arrays.asList(entry("c.bin", bytes(1024)))); val s2 = FakeSource(); s2.store["c.bin"] = tampered
            val r2 = ModelRepository(m2, dir2, FakeSpace(256L * 1024 * 1024)); var rejected = false
            try { r2.importFrom(s2, null) } catch (expected: IOException) { rejected = expected.message!!.contains("SHA-256") }
            check(rejected); check(!File(dir2, "c.bin").exists()); check(!File(dir2, "c.bin.part").exists())
            val full = bytes(4096); val dir3 = freshDir("model-r3-"); val m3 = ModelManifest(Arrays.asList(entry("d.bin", full)))
            val s3 = FakeSource(); s3.store["d.bin"] = full; s3.shortRead.add("d.bin")
            val r3 = ModelRepository(m3, dir3, FakeSpace(256L * 1024 * 1024)); var shortRejected = false
            try { r3.importFrom(s3, null) } catch (expected: IOException) { shortRejected = true }
            check(shortRejected); check(!File(dir3, "d.bin").exists()); check(!File(dir3, "d.bin.part").exists())
            val dir4 = freshDir("model-r4-"); val m4 = ModelManifest(Arrays.asList(entry("e.bin", bytes(1024))))
            val s4 = FakeSource(); s4.store["e.bin"] = bytes(1024); s4.overLong.add("e.bin")
            val r4 = ModelRepository(m4, dir4, FakeSpace(256L * 1024 * 1024)); var overRejected = false
            try { r4.importFrom(s4, null) } catch (expected: IOException) { overRejected = true }
            check(overRejected); check(!File(dir4, "e.bin").exists()); check(!File(dir4, "e.bin.part").exists())
            val dir5 = freshDir("model-r5-"); val m5 = ModelManifest(Arrays.asList(entry("f.bin", bytes(1024))))
            val s5 = FakeSource(); s5.store["f.bin"] = bytes(1024); s5.failClose.add("f.bin")
            val r5 = ModelRepository(m5, dir5, FakeSpace(256L * 1024 * 1024)); var closeRejected = false
            try { r5.importFrom(s5, null) } catch (expected: IOException) { closeRejected = true }
            check(closeRejected); check(!File(dir5, "f.bin").exists()); check(!File(dir5, "f.bin.part").exists())
            val dir6 = freshDir("model-r6-"); val dest = File(dir6, "g.bin")
            try {
                val m6 = ModelManifest(Arrays.asList(entry("g.bin", bytes(1024)))); val s6 = FakeSource(); s6.store["g.bin"] = bytes(1024)
                val r6 = ModelRepository(m6, dir6, FakeSpace(256L * 1024 * 1024)); var renameRejected = false; val reachedCopy = AtomicBoolean()
                try {
                    r6.importFrom(s6, { stage, _, _, _, fileDone, fileTotal, _, _, _ ->
                        if (stage == ModelRepository.Stage.COPYING && fileDone == fileTotal && fileTotal > 0) {
                            reachedCopy.set(true); if (!dest.mkdir()) throw AssertionError("failed to inject target directory")
                        }
                    })
                } catch (expected: IOException) { renameRejected = expected.message!!.contains("原子替换") }
                check(reachedCopy.get()); check(renameRejected); check(!File(dir6, "g.bin.part").exists())
            } finally { dest.delete() }
            val h = bytes(2048); val dir7 = freshDir("model-r7-"); Files.write(File(dir7, "h.bin").toPath(), h)
            val m7 = ModelManifest(Arrays.asList(entry("h.bin", h))); val s7 = FakeSource(); s7.store["h.bin"] = h
            val r7 = ModelRepository(m7, dir7, FakeSpace(256L * 1024 * 1024)); val before = File(dir7, "h.bin").lastModified()
            s7.failOpen.add("h.bin"); r7.importFrom(s7, null); val after = File(dir7, "h.bin").lastModified()
            check(before == after); check(s7.store.containsKey("h.bin"))
            val dir8 = freshDir("model-r8-"); val outside = File.createTempFile("model-r8-out-", ".bin"); Files.write(outside.toPath(), bytes(64))
            val m8 = ModelManifest(Arrays.asList(entry("i.bin", bytes(64)))); Files.createSymbolicLink(File(dir8, "i.bin.part").toPath(), outside.toPath())
            val s8 = FakeSource(); s8.store["i.bin"] = bytes(64); val r8 = ModelRepository(m8, dir8, FakeSpace(256L * 1024 * 1024)); var symlinkRejected = false
            try { r8.importFrom(s8, null) } catch (expected: IOException) { symlinkRejected = true }; check(symlinkRejected); check(Files.exists(outside.toPath()))
            val dir10 = freshDir("model-r10-"); File(dir10, "k.bin").mkdirs(); val m10 = ModelManifest(Arrays.asList(entry("k.bin", bytes(64))))
            val r10 = ModelRepository(m10, dir10, FakeSpace(256L * 1024 * 1024)); var dirRejected = false
            try { r10.verifyEntry(m10.require("k.bin")) } catch (expected: IOException) { dirRejected = true }; check(dirRejected); File(dir10, "k.bin").delete()
            val dir12 = freshDir("model-r12-"); File(dir12, "leftover.bin").mkdirs(); var deleteRejected = false
            try { FileSafety.deleteRegular(File(dir12, "leftover.bin")) } catch (expected: IOException) { deleteRejected = true }; check(deleteRejected); File(dir12, "leftover.bin").delete()
            val dir13 = freshDir("model-r13-"); val data = bytes(1024); val e13 = entry("m.bin", data)
            Files.write(File(dir13, e13.partName()).toPath(), bytes(512)); val r13 = ModelRepository(ModelManifest(Arrays.asList(e13)), dir13, FakeSpace(0L))
            check(File(dir13, e13.partName()).exists()); r13.reclaimOrphanedParts(); check(!File(dir13, e13.partName()).exists())
            val dir14 = freshDir("model-r14-"); val n = bytes(2048); val e14 = entry("n.bin", n)
            val r14 = ModelRepository(ModelManifest(Arrays.asList(e14)), dir14, FakeSpace(0L)); var noSpace = false
            try { val s14 = FakeSource(); s14.store["n.bin"] = n; r14.importFrom(s14, null) } catch (expected: IOException) { noSpace = true }; check(noSpace)
            val dir15 = freshDir("model-r15-"); val o = bytes(512); Files.write(File(dir15, "o.bin").toPath(), bytes(256))
            val r15 = ModelRepository(ModelManifest(Arrays.asList(entry("o.bin", o))), dir15, FakeSpace(256L * 1024 * 1024)); var sizeMismatch = false
            try { r15.verifyEntry(r15.manifest().require("o.bin")) } catch (expected: IOException) { sizeMismatch = true }; check(sizeMismatch)
            val dir17 = freshDir("model-r17-"); val m17 = ModelManifest(Arrays.asList(entry("q.bin", bytes(8))))
            val overCap = object : ModelSource {
                override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> { throw IOException("目录超过" + maxEntries + "个条目") }
                override fun open(ref: ModelSource.UriRef?): InputStream = ByteArrayInputStream(ByteArray(0))
            }
            val r17 = ModelRepository(m17, dir17, FakeSpace(256L * 1024 * 1024)); var capRejected = false
            try { r17.importFrom(overCap, null) } catch (expected: IOException) { capRejected = expected.message!!.contains("目录超过10000") }; check(capRejected)
            var emptyRejected = false
            try { ModelManifest(Collections.emptyList()) } catch (expected: IOException) { emptyRejected = true }; check(emptyRejected)
            var dupNameRejected = false
            try { ModelManifest(Arrays.asList(entry("dup.bin", bytes(8)), entry("dup.bin", bytes(16)))) } catch (expected: IOException) { dupNameRejected = true }; check(dupNameRejected)
            var partCollisionRejected = false
            try { ModelManifest(Arrays.asList(entry("a.bin", bytes(8)), entry("a.bin.part", bytes(8)))) } catch (expected: IOException) { partCollisionRejected = true }; check(partCollisionRejected)
            val dir19 = freshDir("model-r19-"); val real = freshDir("model-r19-real-"); val symlink = File(dir19, "link")
            Files.createSymbolicLink(symlink.toPath(), real.toPath()); val m19 = ModelManifest(Arrays.asList(entry("x.bin", bytes(8)))); var symlinkRoot = false
            try { ModelRepository(m19, symlink, FakeSpace(256L * 1024 * 1024)) } catch (expected: IOException) { symlinkRoot = true }; check(symlinkRoot)
            println("PASS " + checks + " model repository checks (host only; no test backdoors)")
        }
    }
}
