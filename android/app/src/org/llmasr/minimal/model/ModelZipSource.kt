package org.llmasr.minimal.model

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Supplier

/** A single worker-owned archive, outside model root. No URI/name or untrusted JSON escapes this port. */
open class ModelZipSource(
    private val opener: Opener,
    private val filesDir: Supplier<File>,
    private val policy: Supplier<FixedModelPackage>,
    private val clock: Clock = Clock { System.nanoTime() }
) : ModelSource {
    fun interface Opener { @Throws(IOException::class) fun open(): InputStream }
    fun interface Clock { fun nanos(): Long }
    protected open fun create(file: File): OutputStream = Files.newOutputStream(file.toPath(),
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
    protected open fun remove(file: File) { Files.delete(file.toPath()) }
    protected open fun openStage(stage: File): java.nio.file.DirectoryStream<java.nio.file.Path> = Files.newDirectoryStream(stage.toPath())
    protected open fun archiveIo(): ModelZipArchive.Io = ModelZipArchive.Io()
    private fun unsupported(): Nothing = throw IOException("请通过模型包会话导入")
    override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?>? = unsupported()
    override fun open(ref: ModelSource.UriRef?): InputStream? = unsupported()

    class PreparedPackage internal constructor(@JvmField val policy: ModelPackagePolicy, private val session: ModelSource.Session) : ModelSource, AutoCloseable {
        private var transferred = false
        private var closed = false
        override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?>? = throw IOException("需要模型包会话")
        override fun open(ref: ModelSource.UriRef?): InputStream? = throw IOException("需要模型包会话")
        override fun openSession(manifest: ModelManifest, space: ModelRepository.SpaceProvider, progress: ModelRepository.Progress?, cancel: ModelRepository.CancelGate): ModelSource.Session {
            if (transferred || closed || manifest.entries() != policy.manifest.entries()) throw IOException("模型包会话已转移或身份不符")
            transferred = true
            return session
        }
        override fun close() {
            if (transferred || closed) return
            closed = true // This owner closes once, including when the real close throws.
            session.close()
        }
    }
    @Throws(IOException::class)
    fun preparePackage(space: ModelRepository.SpaceProvider, progress: ModelRepository.Progress?, cancel: ModelRepository.CancelGate): PreparedPackage =
        prepare(null, space, progress, cancel)

    @Throws(IOException::class)
    override fun openSession(manifest: ModelManifest, space: ModelRepository.SpaceProvider,
                             progress: ModelRepository.Progress?, cancel: ModelRepository.CancelGate): ModelSource.Session =
        prepare(manifest, space, progress, cancel).openSession(manifest, space, progress, cancel)

    private fun prepare(expectedManifest: ModelManifest?, space: ModelRepository.SpaceProvider,
                        progress: ModelRepository.Progress?, cancel: ModelRepository.CancelGate): PreparedPackage {
        val start = clock.nanos()
        var sourceCleanupSucceeded = true
        val check = object : ModelZipArchive.Check {
            override fun run() {
                if (cancel.cancelled()) throw ModelRepository.CancelledException()
                if (clock.nanos() - start >= DEADLINE_NANOS) throw IOException("模型包处理超时，请重新选择")
            }
            override fun policyAccepted(policy: ModelPackagePolicy) {
                run()
                if (expectedManifest == null && space.usableBytes() < Math.addExact(policy.totalBytes, ModelRepository.SPACE_PAD)) throw IOException("存储空间不足：已审核模型包预留")
            }
            override fun cleanupFailed() { sourceCleanupSucceeded = false }
        }
        check.run()
        val trusted = policy.get()
        if (expectedManifest != null && trusted.manifest.entries() != expectedManifest.entries()) throw IOException(FixedModelPackage.FORMAT_ERROR)
        val discover = expectedManifest == null
        val reserve = if (discover) ModelRepository.SPACE_PAD else Math.addExact(trusted.totalBytes, ModelRepository.SPACE_PAD)
        val archiveLimit = if (discover) ModelPackagePolicy.MAX_ARCHIVE else trusted.archiveLimit
        check.run()
        val parent = filesDir.get().canonicalFile
        if (!parent.isDirectory) throw IOException("应用内部存储目录不可用")
        val stage = File(parent, "model-zip-staging")
        val archive = File(stage, "archive.part")
        prepareStage(stage, archive, progress, check)
        check.run()
        if (space.usableBytes() < reserve) throw IOException("存储空间不足：模型包预留")
        var zip: ModelZipArchive? = null
        var created = false
        var count = 0L
        fun phase(s: ModelRepository.Stage) { progress?.update(s, null, 0, 8, count, archiveLimit, count, archiveLimit, 0) }
        try {
            check.run(); phase(ModelRepository.Stage.COPYING_ARCHIVE)
            val out = create(archive)
            created = true
            // CREATE_NEW uses app-private parent; narrow POSIX owner mode before opening provider.
            withClosed(out, { check.cleanupFailed() }) { target ->
                Files.setPosixFilePermissions(archive.toPath(), PosixFilePermissions.fromString("rw-------"))
                check.run()
                withClosed(opener.open(), { check.cleanupFailed() }) { input ->
                    check.run()
                    val b = ByteArray(65536)
                    while (true) {
                        check.run()
                        val n = input.read(b)
                        check.run()
                        if (n < 0) break
                        if (n == 0) throw IOException("模型包读取无进展")
                        if (n > archiveLimit - count) throw IOException("模型包超过输入上限")
                        val available = space.usableBytes()
                        if (available < reserve || available - reserve < n) throw IOException("存储空间不足：模型包复制")
                        target.write(b, 0, n)
                        count += n
                        check.run(); phase(ModelRepository.Stage.COPYING_ARCHIVE)
                    }
                }
                check.run()
            }
            check.run(); phase(ModelRepository.Stage.CHECKING_ARCHIVE)
            zip = if (discover) ModelZipArchive.discover(archive, trusted, check, archiveIo()) else ModelZipArchive.prepare(archive, trusted, check, archiveIo())
            check.run()
            val prepared = zip
            val admitted = prepared.policy()
            val manifest = admitted.manifest
            val session = object : ModelSource.Session {
                private var closed = false
                override fun archiveBytes(): Long = count
                override fun checkpoint() { check.run() }
                override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> {
                    check.run()
                    if (closed || maxEntries < 7 || expected?.toSet() != manifest.entries().map { it.file() }.toSet()) throw IOException("模型包文件集合不匹配")
                    return manifest.entries().associateTo(LinkedHashMap()) { it.file() to ModelSource.UriRef(it.file()) }
                }
                override fun open(ref: ModelSource.UriRef?): InputStream {
                    check.run()
                    if (closed || ref?.handle() !is String) throw IOException("模型包条目不可用")
                    return prepared.open(ref.handle() as String)
                }
                override fun close() {
                    if (closed) return
                    // No cancel/deadline check may bypass real resource close or cleanup.
                    var primary: Throwable? = null
                    var cleanupSucceeded = sourceCleanupSucceeded
                    try { prepared.close() } catch (e: Throwable) { primary = e; cleanupSucceeded = false }
                    try { phase(ModelRepository.Stage.CLEANING_ARCHIVE) }
                    catch (e: Throwable) { if (primary == null) primary = e else ModelZipArchive.suppress(primary, e) }
                    try { remove(archive) }
                    catch (e: Throwable) { cleanupSucceeded = false; if (primary == null) primary = e else ModelZipArchive.suppress(primary, e) }
                    try { progress?.sourceCleanup(cleanupSucceeded && sourceCleanupSucceeded) }
                    catch (notify: Throwable) { if (primary == null) primary = notify else ModelZipArchive.suppress(primary, notify) }
                    closed = true
                    if (primary != null) throw primary
                    check.run()
                }
            }
            return PreparedPackage(admitted, session)
        } catch (primary: Throwable) {
            var cleanupSucceeded = sourceCleanupSucceeded
            try { zip?.close() } catch (close: Throwable) { cleanupSucceeded = false; ModelZipArchive.suppress(primary, close) }
            if (created) {
                try { phase(ModelRepository.Stage.CLEANING_ARCHIVE) } catch (notify: Throwable) { ModelZipArchive.suppress(primary, notify) }
                try { remove(archive) } catch (cleanup: Throwable) { cleanupSucceeded = false; ModelZipArchive.suppress(primary, cleanup) }
            }
            try { progress?.sourceCleanup(cleanupSucceeded && sourceCleanupSucceeded) } catch (notify: Throwable) { ModelZipArchive.suppress(primary, notify) }
            throw primary
        }
    }

    /** Like use, but preserves identical primary/close Throwable without self suppression. */
    private inline fun <T : AutoCloseable, R> withClosed(resource: T, closeFailed: () -> Unit = {}, body: (T) -> R): R {
        var primary: Throwable? = null
        try { return body(resource) } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try { resource.close() } catch (close: Throwable) {
                closeFailed()
                if (primary == null) throw close
                ModelZipArchive.suppress(primary, close)
            }
        }
    }

    private fun prepareStage(stage: File, archive: File, progress: ModelRepository.Progress?, check: ModelZipArchive.Check) {
        val path = stage.toPath()
        if (Files.isSymbolicLink(path) || (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)))
            throw IOException("模型包暂存目录不安全")
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(path,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        var stageCleanupSucceeded = true
        try {
            withClosed(openStage(stage), { stageCleanupSucceeded = false; check.cleanupFailed() }) { children ->
                for (child in children) if (child.fileName.toString() != archive.name)
                    throw IOException("模型包暂存目录含未知文件，已保留")
            }
        } catch (primary: Throwable) {
            // This owner can fail before prepare creates an archive or enters its catch.
            try { progress?.sourceCleanup(stageCleanupSucceeded) } catch (notify: Throwable) { ModelZipArchive.suppress(primary, notify) }
            throw primary
        }
        if (Files.exists(archive.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(archive.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("模型包暂存文件不安全")
            try { remove(archive) }
            catch (failure: Throwable) {
                try { progress?.sourceCleanup(false) } catch (notify: Throwable) { ModelZipArchive.suppress(failure, notify) }
                throw failure
            }
        }
    }
    companion object { const val DEADLINE_NANOS = 30L * 60 * 1000 * 1000 * 1000 }
}
