package org.llmasr.minimal.model

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** Android-private noBackup journal. Supplier and all filesystem work are lazy.
 * Trusted Context parent aliases may resolve; untrusted proof/model leaves may not. */
class AndroidModelVerificationIo @JvmOverloads constructor(private val namespace: String? = null, private val noBackupDirectory: () -> File) : ModelVerification.Io {
    class Catalog(private val noBackup: () -> File) : ModelCatalog.Io {
        private val metadata = AndroidModelVerificationIo("catalog", noBackup)
        override fun readCatalog(): ByteArray? = metadata.read("catalog", 65536)
        override fun writeCatalog(bytes: ByteArray) { metadata.write("catalog", bytes) }
        override fun readRecovery(): ByteArray? = metadata.read("recovery", 65537)
        override fun writeRecovery(bytes: ByteArray) { metadata.write("recovery", bytes) }
        override fun clearRecovery() { metadata.removeCatalog("recovery") }
        override fun restoreCatalog(bytes: ByteArray?) {
            if (bytes != null) metadata.write("catalog", bytes) else metadata.removeCatalog("catalog")
        }
        override fun proof(key: String): ModelVerification.Io = if (key == ModelDeployment.LEGACY) AndroidModelVerificationIo(noBackupDirectory = noBackup)
            else AndroidModelVerificationIo(key, noBackup)
    }
    init { if (namespace != null && namespace != "catalog" && !namespace.matches(Regex("[0-9a-f]{64}"))) throw IllegalArgumentException("invalid proof namespace") }
    private fun stat(file: File): StructStat? = try { Os.lstat(file.path) }
        catch (failure: ErrnoException) {
            if (failure.errno == OsConstants.ENOENT) null else throw IOException("无法读取模型文件身份", failure)
        }
    private fun checked(file: File, directory: Boolean): StructStat {
        val value = stat(file) ?: throw IOException("模型证明文件缺失")
        if (if (directory) !OsConstants.S_ISDIR(value.st_mode) else !OsConstants.S_ISREG(value.st_mode))
            throw IOException("模型证明路径类型无效")
        return value
    }
    override fun exists(file: File): Boolean = stat(file) != null
    override fun identity(file: File, directory: Boolean): String {
        val s = checked(file, directory)
        return "${s.st_dev}:${s.st_ino}:${s.st_mode}:${s.st_size}:${s.st_mtim.tv_sec}:${s.st_mtim.tv_nsec}:${s.st_ctim.tv_sec}:${s.st_ctim.tv_nsec}"
    }
    private fun directory(create: Boolean): File? {
        val parent = noBackupDirectory().canonicalFile
        checked(parent, true)
        var base = parent
        if (namespace != null) {
            base = File(parent, "model-variants")
            if (stat(base) == null) {
                if (!create) return null
                if (!base.mkdir()) throw IOException("无法创建模型证明父目录")
                syncDirectory(parent)
            }
            checked(base, true)
        }
        val root = File(base, namespace ?: "model-verification")
        if (stat(root) == null) {
            if (!create) return null
            if (!root.mkdir()) throw IOException("无法创建模型证明目录")
            syncDirectory(base)
        }
        checked(root, true)
        return root
    }
    /** Catalog-only rollback to observed absence; legacy proof protocol is unchanged. */
    private fun removeCatalog(name: String) {
        if (namespace != "catalog") throw IOException("非法目录恢复")
        val root = directory(false) ?: return
        val file = path(root, name)
        if (stat(file) != null) { checked(file, false); Os.remove(file.path) }
        syncDirectory(root)
    }
    private fun path(root: File, name: String): File {
        if (if (namespace == "catalog") name != "catalog" && name != "recovery" else name != "anchor" && name != "ledger" && name != "receipt") throw IOException("未知证明文件")
        return File(root, name)
    }
    @Throws(IOException::class)
    override fun read(name: String, limit: Int): ByteArray? {
        val root = directory(false) ?: return null
        val file = path(root, name)
        if (stat(file) == null) return null
        val before = identity(file, false)
        try {
            val fd = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            val bytes = ownedFd(fd) {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val n = Os.read(fd, buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
                    if (n == 0) break
                    if (out.size() + n > limit) throw IOException("模型证明过大")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            if (identity(file, false) != before) throw IOException("模型证明读取期间变化")
            return bytes
        } catch (failure: ErrnoException) { throw IOException("无法读取模型证明", failure) }
    }
    @Throws(IOException::class)
    override fun write(name: String, bytes: ByteArray) {
        if (bytes.size > (if (namespace == "catalog") 65537 else 16384) || (name == "anchor" && bytes.size > 128)) throw IOException("模型证明过大")
        val root = directory(true)!!
        val dest = path(root, name)
        if (stat(dest) != null) checked(dest, false)
        val temporary = File(root, ".$name-${UUID.randomUUID()}.tmp")
        var primary: Throwable? = null
        try {
            val fd = Os.open(temporary.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
            ownedFd(fd) {
                var offset = 0
                while (offset < bytes.size) {
                    val written = Os.write(fd, bytes, offset, bytes.size - offset)
                    if (written <= 0) throw IOException("模型证明写入未前进")
                    offset += written
                }
                Os.fsync(fd)
            }
            checked(root, true)
            if (stat(dest) != null) checked(dest, false)
            Os.rename(temporary.path, dest.path)
            syncDirectory(root)
        } catch (failure: ErrnoException) {
            val wrapped = IOException("模型证明耐久写入失败", failure); primary = wrapped; throw wrapped
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            // Never promote leftovers. Cleanup cannot replace the primary failure.
            try {
                if (stat(temporary) != null) {
                    checked(temporary, false); Os.remove(temporary.path); syncDirectory(root)
                }
            } catch (secondary: Throwable) {
                if (primary == null) throw secondary
                if (secondary !== primary) primary.addSuppressed(secondary)
            }
        }
    }
    /** Exactly one raw-FD owner; borrowed Java stream wrappers are never used. */
    private fun <T> ownedFd(fd: java.io.FileDescriptor, body: () -> T): T {
        var primary: Throwable? = null
        try { return body() } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            if (primary == null) Os.close(fd)
            else try { Os.close(fd) } catch (secondary: Throwable) { if (secondary !== primary) primary.addSuppressed(secondary) }
        }
    }
    private fun syncDirectory(directory: File) {
        checked(directory, true)
        try {
            val fd = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            ownedFd(fd) {
                if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw IOException("模型证明目录类型已变化")
                Os.fsync(fd)
            }
        } catch (failure: ErrnoException) { throw IOException("模型证明目录同步失败", failure) }
    }
}
