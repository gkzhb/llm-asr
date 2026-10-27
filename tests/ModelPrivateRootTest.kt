import org.llmasr.minimal.modelmanagement.ModelManagementController
import org.llmasr.minimal.modelmanagement.ModelManagementState
import org.llmasr.minimal.modelmanagement.ModelOperationControl
import org.llmasr.minimal.model.*
import org.llmasr.minimal.task.TaskCoordinator
import java.io.*
import java.nio.file.*
import java.util.Collections
import java.util.Comparator

/** Real host filesystem aliases and production controller, not Android acceptance. */
class ModelPrivateRootTest {
    companion object {
        private fun check(ok: Boolean, why: String) { if (!ok) throw AssertionError(why) }
        private fun interface Action { fun run() }
        private fun expectRejected(action: Action) {
            try { action.run() } catch (expected: IOException) { return }
            throw AssertionError("unsafe root/file accepted")
        }
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private fun closeJava(resource: AutoCloseable?, primary: Throwable?) {
            if (resource == null) return
            if (primary == null) resource.close()
            else try { resource.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) }
        }
        @JvmStatic @Throws(Exception::class) @Suppress("UNUSED_PARAMETER")
        fun main(args: Array<String>?) {
            val base = Files.createTempDirectory("model-private-root-")
            try {
                val real = Files.createDirectories(base.resolve("user/0/app/files"))
                val alias = base.resolve("data"); Files.createSymbolicLink(alias, base.resolve("user/0"))
                val files = alias.resolve("app/files").toFile()
                val manifest = ModelManifest(Collections.singletonList(ModelRepositoryTest.entry("a.bin", byteArrayOf(1, 2, 3))))
                val ready = ModelReadiness()
                val syntheticIo = ModelAndroidHelpersTest.TestIo(base.resolve("synthetic-noBackup"))
                val proof = ModelVerification(manifest, ready, syntheticIo, ModelAndroidHelpersTest.MemoryGuard())
                val repo = ModelRepository.forAppFiles(manifest, files, { Long.MAX_VALUE }, proof)
                check(!Files.exists(real.resolve("model")), "bootstrap does not mkdir")
                check(repo.inspect(ModelRepository.NEVER_CANCEL).installedBytes == 0L, "alias inspect succeeds")
                check(!Files.exists(real.resolve("model")), "inspect does not mkdir")
                val source = ModelRepositoryTest.FakeSource(); source.store["a.bin"] = byteArrayOf(1, 2, 3)
                val state = ModelManagementState()
                val owner = TaskCoordinator { it.run() }
                val controller = ModelManagementController(owner, repo, ready, ModelOperationControl(), state)
                check(controller.startImport(source), "import admitted")
                check(state.current().phase == ModelManagementState.Phase.SUCCEEDED, "alias import succeeds: " + state.current().errorCode)
                check(state.current().cleanupOutcome == ModelManagementState.Outcome.SUCCEEDED && ready.snapshot().ready(), "cleanup and READY")
                check(repo.modelDir() == real.resolve("model").toFile().canonicalFile, "native path uses pinned parent")
                val noCopy = object : ModelSource {
                    override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?>? = Collections.emptyMap()
                    override fun open(ref: ModelSource.UriRef?): InputStream? { throw AssertionError("must reuse") }
                }
                check(controller.startImport(noCopy), "retry admitted")
                check(state.current().phase == ModelManagementState.Phase.SUCCEEDED && state.current().reusedBytes == 3L, "SHA reuse succeeds")
                controller.refreshInspect(); check(state.current().inspectError == null, "overview refresh succeeds")
                check(controller.startVerify() && ready.snapshot().ready(), "verify alias")
                check(controller.startDelete(ready.epoch(), true), "delete admitted")
                check(!Files.exists(real.resolve("model/a.bin")), "managed file deleted")
                val outside = Files.createDirectory(base.resolve("outside")); Files.write(outside.resolve("a.bin"), byteArrayOf(9))
                for (leaf in arrayOf("model", "a.bin", "a.bin.part")) {
                    val model = real.resolve("model")
                    if (leaf != "model") Files.createDirectories(model)
                    val link = if (leaf == "model") model else model.resolve(leaf)
                    Files.createSymbolicLink(link, if (leaf == "model") outside else outside.resolve("a.bin"))
                    val unsafe = ModelRepository.forAppFiles(manifest, files, { Long.MAX_VALUE }, proof)
                    expectRejected { unsafe.inspect(ModelRepository.NEVER_CANCEL) }
                    expectRejected { unsafe.importFrom(source, null) }
                    expectRejected { unsafe.verifyAll() }
                    expectRejected { unsafe.deleteAll() }
                    check(java.util.Arrays.equals(Files.readAllBytes(outside.resolve("a.bin")), byteArrayOf(9)), "outside unchanged")
                    Files.delete(link); if (leaf != "model") Files.delete(model)
                }
                Files.createSymbolicLink(real.resolve("model"), base.resolve("absent"))
                expectRejected { ModelRepository.forAppFiles(manifest, files, { 0 }, proof).inspect(ModelRepository.NEVER_CANCEL) }
                Files.delete(real.resolve("model")); Files.write(real.resolve("model"), byteArrayOf(7))
                expectRejected { ModelRepository.forAppFiles(manifest, files, { 0 }, proof).importFrom(source, null) }
                Files.delete(real.resolve("model"))
                expectRejected { ModelRepository(manifest, File(files, "model")) { 0 } }
                val noIo = object : File(files.path) { override fun getCanonicalFile(): File { throw AssertionError("bootstrap IO") } }
                val lazyIo = object : ModelVerification.Io {
                    override fun identity(file: File, directory: Boolean): String = throw AssertionError("guard bootstrap IO")
                    override fun exists(file: File): Boolean = throw AssertionError("guard bootstrap IO")
                    override fun read(name: String, limit: Int): ByteArray? = throw AssertionError("guard bootstrap IO")
                    override fun write(name: String, bytes: ByteArray) { throw AssertionError("guard bootstrap IO") }
                }
                ModelRepository.forAppFiles(manifest, noIo, { 0 }, ModelVerification(manifest, ModelReadiness(), lazyIo, ModelAndroidHelpersTest.MemoryGuard()))
                println("PASS private-root alias import/inspect/verify/reuse/delete and symlink safety (host filesystem only)")
            } finally {
                val paths = Files.walk(base); var primary: Throwable? = null
                try { for (p in Iterable { paths.sorted(Comparator.reverseOrder()).iterator() }) Files.deleteIfExists(p) }
                catch (failure: Throwable) { primary = failure; throw failure }
                finally { closeJava(paths, primary) }
            }
        }
    }
}
