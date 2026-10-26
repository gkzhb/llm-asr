package org.llmasr.minimal.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

/** Fixed-manifest verification journal. Call only on the shared owner's worker.
 * IO is outside readiness/control monitors. The disk lock serializes direct repository
 * revocation against proof publication; cancellation never takes this lock. */
class ModelVerification(private val manifest: ModelManifest, private val readiness: ModelReadiness,
    private val io: Io) : ModelRepository.MutationGuard {
    interface Io {
        /** Stable no-follow identity including type, dev/inode, mode, size, mtime and ctime. */
        @Throws(IOException::class) fun identity(file: File, directory: Boolean): String
        @Throws(IOException::class) fun exists(file: File): Boolean
        /** Null only for absent files. Enforce limit while reading, not afterwards. */
        @Throws(IOException::class) fun read(name: String, limit: Int): ByteArray?
        /** File sync, close, same-directory atomic rename, directory sync; no best effort. */
        @Throws(IOException::class) fun write(name: String, bytes: ByteArray)
    }
    interface CommitGate {
        fun reserve(token: ModelReadiness.VerifyToken): Boolean
        fun succeed(token: ModelReadiness.VerifyToken): Boolean
    }
    class Token internal constructor(internal val generation: String, internal val anchor: String,
        internal val epoch: ModelReadiness.VerifyToken, internal val before: List<String>)
    private data class Proof(val generation: String, val anchor: String, val identities: List<String>)
    private val disk = ReentrantLock()
    @Volatile private var generation: String? = null
    @Volatile private var current: Proof? = null
    @Volatile private var mutation: Any? = null
    @Volatile private var mutationThread: Thread? = null
    override fun beginMutation(): ModelRepository.MutationScope {
        val identity = Any()
        disk.lock()
        try {
            if (mutation != null) throw IOException("模型写入仍在收尾")
            mutation = identity; mutationThread = Thread.currentThread()
        } finally { disk.unlock() }
        return object : ModelRepository.MutationScope {
            private var closed = false
            override fun close() {
                disk.lock()
                try {
                    if (closed) return
                    if (mutation !== identity) throw IOException("模型写入世代失效")
                    mutation = null; mutationThread = null; closed = true
                } finally { disk.unlock() }
                // Releasing admission is not itself a new proof; do not run
                // observers in the import's handoff to its own final SHA phase.
            }
        }
    }
    private fun noMutation() { if (mutation != null) throw IOException("模型写入仍在收尾") }
    private val digest = hex(MessageDigest.getInstance("SHA-256").digest(manifest.entries().sortedBy { it.file() }
        .joinToString("\n", "mnn-verification-policy-1\n") { "${it.file()}:${it.bytes()}:${it.sha256()}" }.toByteArray(Charsets.UTF_8)))

    @Throws(IOException::class)
    override fun beforeMutation() {
        readiness.invalidateState()
        current = null
        disk.lock()
        try {
            if (mutation != null && mutationThread !== Thread.currentThread()) throw IOException("模型写入由另一事务占用")
            // A direct caller may have waited behind an in-flight restore/commit.
            current = null
            readiness.invalidateState()
            dirtyLocked()
        } finally { disk.unlock(); readiness.notifyChange() }
    }
    private fun dirtyLocked(): Pair<String, String> {
        val anchor = anchor(true)!!
        val next = UUID.randomUUID().toString()
        generation = next
        io.write("ledger", encode(1, next, anchor, emptyList()))
        return Pair(next, anchor)
    }
    /** Explicit full VERIFY revokes even a previously valid proof. */
    @Throws(IOException::class)
    fun begin(repository: ModelRepository): Token {
        disk.lock()
        try {
            noMutation()
            current = null; readiness.invalidateState(); dirtyLocked()
            val g = generation ?: throw IOException("缺少模型世代")
            val a = anchor(false) ?: throw IOException("缺少安装锚点")
            val before = snapshot(repository)
            return Token(g, a, readiness.beginVerify(g), before)
        } finally { disk.unlock(); readiness.notifyChange() }
    }
    /** Bind the full SHA's before/after identities. Import calls begin at the first
     * FINAL_VERIFY callback, before that pass opens any model content. */
    @Throws(IOException::class)
    fun commit(repository: ModelRepository, token: Token, gate: CommitGate): Boolean {
        val after = snapshot(repository)
        if (after != token.before) throw IOException("校验期间模型已变化")
        if (!gate.reserve(token.epoch)) return false
        disk.lock()
        try {
            noMutation()
            if (generation != token.generation || !readiness.accepts(token.epoch) || anchor(false) != token.anchor)
                throw IOException("模型校验世代已过期")
            val proof = Proof(token.generation, token.anchor, after)
            io.write("receipt", encode(2, proof.generation, proof.anchor, proof.identities))
            if (!readiness.accepts(token.epoch) || snapshot(repository) != after)
                throw IOException("模型提交期间已变化")
            io.write("ledger", encode(3, proof.generation, proof.anchor, emptyList()))
            if (!gate.succeed(token.epoch)) throw IOException("模型校验世代已过期")
            current = proof
            return true
        } catch (failure: Throwable) {
            current = null
            readiness.finishUnverified(token.epoch)
            try { dirtyLocked() } catch (secondary: Throwable) { if (secondary !== failure) failure.addSuppressed(secondary) }
            throw failure
        } finally { disk.unlock() }
    }
    /** Independent startup/management worker only. Never hashes or loads native. */
    @Throws(IOException::class)
    fun restoreFast(repository: ModelRepository): Boolean {
        disk.lock()
        try {
            noMutation()
            current = null; readiness.invalidateState()
            val a = anchor(false) ?: return false
            val ledger = io.read("ledger", LIMIT) ?: return false
            val committed = decode(ledger, 3)
            val receipt = io.read("receipt", LIMIT) ?: return false
            val proof = decode(receipt, 2)
            if (proof.generation != committed.generation || proof.anchor != a || committed.anchor != a)
                throw IOException("模型证明不连续")
            val first = snapshot(repository)
            if (proof.identities != first || first != snapshot(repository)) throw IOException("模型证明已失效")
            generation = proof.generation
            val token = readiness.beginVerify(proof.generation)
            if (!readiness.commitVerified(token)) throw IOException("模型世代已变化")
            current = proof
            return true
        } catch (failure: IOException) {
            readiness.markInvalidState()
            throw failure
        } finally { disk.unlock(); readiness.notifyChange() }
    }
    /** Request boundary: consume the in-memory proof and fixed light identities only. */
    @Throws(IOException::class)
    fun requireReady(repository: ModelRepository) {
        val proof = current
        if (mutation != null || proof == null || !readiness.isReady) throw IOException("模型未准备，请到模型管理完整校验或快速恢复已校验证明")
        try {
            if (mutation != null || generation != proof.generation || snapshot(repository) != proof.identities || !readiness.isReady || current !== proof)
                throw IOException("模型文件已变化")
        } catch (failure: Throwable) {
            current = null
            readiness.markInvalid()
            if (failure is Error) throw failure
            throw IOException("模型不可用，请到模型管理完整校验", failure)
        }
    }
    private fun snapshot(repository: ModelRepository): List<String> {
        repository.validateManagedBoundary(ModelRepository.NEVER_CANCEL)
        val root = repository.modelDir()
        val result = ArrayList<String>()
        result.add(io.identity(root, true))
        for (entry in manifest.entries().sortedBy { it.file() }) {
            if (io.exists(File(root, entry.partName()))) throw IOException("存在未完成导入，请修复模型")
            val file = File(root, entry.file())
            if (file.length() != entry.bytes()) throw IOException("模型文件大小不符")
            result.add(io.identity(file, false))
        }
        return result
    }
    private fun anchor(create: Boolean): String? {
        val bytes = io.read("anchor", 128)
        if (bytes != null) {
            val text = String(bytes, Charsets.US_ASCII)
            if (!uuid(text)) throw IOException("安装锚点无效")
            return text
        }
        if (!create) return null
        val value = UUID.randomUUID().toString()
        io.write("anchor", value.toByteArray(Charsets.US_ASCII))
        return value
    }
    private fun encode(kind: Int, g: String, a: String, identities: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x4d565031); out.writeInt(kind); out.writeUTF(digest)
            out.writeUTF(g); out.writeUTF(a); out.writeInt(identities.size)
            for (identity in identities) out.writeUTF(identity)
        }
        return bytes.toByteArray().also { if (it.size > LIMIT) throw IOException("模型证明过大") }
    }
    private fun decode(bytes: ByteArray, kind: Int): Proof {
        if (bytes.size > LIMIT) throw IOException("模型证明过大")
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != 0x4d565031 || input.readInt() != kind || input.readUTF() != digest)
                throw IOException("模型证明策略不符")
            val g = input.readUTF(); val a = input.readUTF(); val count = input.readInt()
            if (!uuid(g) || !uuid(a) || count != if (kind == 2) manifest.entries().size + 1 else 0)
                throw IOException("模型证明结构无效")
            val identities = ArrayList<String>()
            repeat(count) { identities.add(input.readUTF().also { value ->
                if (value.isEmpty() || value.length > 512 || value.any { it.code < 32 || it.code > 126 }) throw IOException("文件身份无效")
            }) }
            if (input.read() != -1) throw IOException("模型证明包含未知字段")
            return Proof(g, a, identities)
        }
    }
    companion object {
        private const val LIMIT = 16384
        private fun uuid(value: String): Boolean = value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
