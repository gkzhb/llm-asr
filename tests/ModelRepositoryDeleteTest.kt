import org.llmasr.minimal.model.ModelEntry
import org.llmasr.minimal.model.ModelManifest
import org.llmasr.minimal.model.ModelRepository
import java.io.*
import java.nio.file.*
import java.security.MessageDigest
import java.util.Arrays

/** Production delete whitelist/partial/no-recursion/no-symlink and inspect checks. */
class ModelRepositoryDeleteTest {
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check $checks") }
        private fun sha256(data: ByteArray): String {
            val h = MessageDigest.getInstance("SHA-256"); h.update(data)
            val s = StringBuilder(); for (b in h.digest()) s.append(String.format("%02x", b.toInt() and 255))
            return s.toString()
        }
        private fun bytes(n: Int): ByteArray { val b = ByteArray(n); for (i in 0 until n) b[i] = ((i * 7 + 13) and 0xff).toByte(); return b }
        private fun freshDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()
        private fun entry(name: String, data: ByteArray): ModelEntry = ModelEntry(name, data.size.toLong(), sha256(data))
        @JvmStatic @Throws(Exception::class) @Suppress("UNUSED_PARAMETER")
        fun main(args: Array<String>?) {
            ModelAndroidHelpersTest.PersistentFixture().use { f ->
                f.install(); f.verify(); f.io.fail = "open"
                try { f.repo.deleteAll(); throw AssertionError("revoke failure accepted") } catch (expected: IOException) { }
                check(Files.readAllBytes(f.model.resolve("a")).contentEquals(byteArrayOf(1, 2, 3)))
                f.io.fail = null; f.verify(); f.repo.deleteAll(); check(!f.ready.isReady && !Files.exists(f.model.resolve("a")))
                Files.createDirectories(f.model); f.install(); f.verify(); f.io.fail = "open"
                try { f.repo.deleteAll(); throw AssertionError("second transaction reused revoke") } catch (expected: IOException) { }
                check(Files.exists(f.model.resolve("a")))
            }
            run {
                val dir = freshDir("del-d1-"); val a = bytes(64); val b = bytes(64)
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", a), entry("b.bin", b))), dir) { 0 }
                Files.write(File(dir, "a.bin").toPath(), a); Files.write(File(dir, "b.bin").toPath(), b)
                Files.write(File(dir, "a.bin.part").toPath(), bytes(200))
                val report = r.deleteAll()
                check(!report.modelDirWasMissing); check(report.failed.isEmpty()); check(report.succeeded() >= 2)
                check(report.freedBytes >= 64 + 64); check(!File(dir, "a.bin").exists()); check(!File(dir, "b.bin").exists()); check(!File(dir, "a.bin.part").exists())
            }
            run {
                val dir = freshDir("del-d2-"); val outside = File.createTempFile("del-d2-out-", ".txt")
                Files.write(outside.toPath(), bytes(32)); val a = bytes(64)
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", a))), dir) { 0 }
                Files.write(File(dir, "a.bin").toPath(), a); r.deleteAll()
                check(Files.exists(outside.toPath())); check(outside.length() == 32L); outside.delete()
            }
            run {
                val dir = freshDir("del-d3-"); val missing = File(dir, "missing")
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", bytes(8)))), missing) { 0 }
                check(!missing.exists()); val report = r.deleteAll(); check(report.modelDirWasMissing)
            }
            run {
                val dir = freshDir("del-d4-"); val outside = File.createTempFile("del-d4-out-", ".bin")
                Files.write(outside.toPath(), bytes(8))
                try {
                    val a = bytes(64); val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", a))), dir) { 0 }
                    Files.write(File(dir, "a.bin").toPath(), a); Files.createSymbolicLink(File(dir, "b.bin").toPath(), outside.toPath())
                    val report = r.deleteAll(); check(report.failed.isNotEmpty() || report.unexpected.isNotEmpty()); check(!File(dir, "a.bin").exists())
                } finally { outside.delete() }
            }
            run {
                val dir = freshDir("del-d5-"); val a = bytes(64)
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", a))), dir) { 0 }
                Files.write(File(dir, "a.bin").toPath(), a); val nested = File(dir, "nested"); nested.mkdirs()
                Files.write(File(nested, "secret.txt").toPath(), bytes(8))
                val report = r.deleteAll(); check(report.unexpected.contains("nested")); check(nested.isDirectory); nested.delete()
            }
            run {
                val dir = freshDir("del-d6-"); val a = bytes(64)
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", a))), dir) { 0 }
                val f = File(dir, "a.bin"); Files.write(f.toPath(), a); f.delete(); f.mkdirs()
                var refused = false; try { r.deleteAll() } catch (expected: IOException) { refused = true }
                check(refused); check(f.isDirectory); f.delete()
            }
            run {
                val dir = freshDir("del-d7-"); val a = bytes(64)
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", a))), dir) { 0 }
                Files.write(File(dir, "a.bin").toPath(), a); Files.write(File(dir, "leftover.bin").toPath(), bytes(16))
                val report = r.deleteAll(); check(report.unexpected.contains("leftover.bin"))
                check(!File(dir, "leftover.bin").exists() == false); check(File(dir, "leftover.bin").exists())
            }
            run {
                val dir = freshDir("del-d8-"); val real = freshDir("del-d8-real-"); val link = File(dir, "link")
                Files.createSymbolicLink(link.toPath(), real.toPath())
                var refused = false
                try { ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", bytes(8)))), link) { 0 } }
                catch (expected: IOException) { refused = true }; check(refused)
            }
            run {
                val dir = freshDir("del-d9-"); val nested = File(dir, "no-such")
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", bytes(8)))), nested) { 0 }
                check(!nested.exists()); val report = r.inspect(ModelRepository.NEVER_CANCEL)
                check(report.expectedBytes == 8L); check(!nested.exists())
            }
            run {
                val dir = freshDir("del-d10-"); val a = bytes(64)
                val r = ModelRepository(ModelManifest(Arrays.asList(entry("a.bin", a))), dir) { 0 }
                val report = r.deleteAll(); check(report.considered == 0); check(report.modelDirRemoved); check(!dir.exists())
            }
            println("PASS $checks model repository delete whitelist / partial / no-recursion checks")
        }
    }
}
