package org.llmasr.minimal.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.UUID

/** Worker-owned durable installation catalog. UI only consumes cached immutable rows. */
class ModelCatalog(private val fixed: FixedModelPackage, private val files: File, private val cache: File,
    private val space: ModelRepository.SpaceProvider, private val resident: ModelResidency,
    private val io: Io, private val projection: ModelReadiness) {
    interface Io {
        fun readCatalog(): ByteArray?
        fun writeCatalog(bytes: ByteArray)
        fun readRecovery(): ByteArray?
        fun writeRecovery(bytes: ByteArray)
        fun clearRecovery()
        /** Restore the exact previously observed document (absence included), durably. */
        fun restoreCatalog(bytes: ByteArray?) {
            if (bytes == null) throw IOException("无法确认首次目录发布回滚")
            writeCatalog(bytes)
        }
        fun proof(key: String): ModelVerification.Io
    }
    class Row(@JvmField val deployment: ModelDeployment, @JvmField val selected: Boolean,
        @JvmField val files: List<ModelReports.FileDetail>, @JvmField val installedBytes: Long,
        @JvmField val error: Boolean, @JvmField val verifiedCount: Int, @JvmField val epoch: Long)
    private val models = java.util.concurrent.ConcurrentHashMap<String, ModelDeployment>()
    @Volatile private var rows: List<Row> = emptyList()
    @Volatile private var selectedKey: String? = ModelDeployment.LEGACY
    @Volatile private var loaded = false
    @Volatile private var uncertain = false
    private var rollbackBytes: ByteArray? = null
    @Volatile private var revision = 0L
    private val observers = HashMap<ModelReadiness, ModelReadiness.Listener>()
    init {
        val legacy = create(ModelPackagePolicy.legacy(fixed), ModelDeployment.LEGACY)
        models[ModelDeployment.LEGACY] = legacy; observe(legacy)
    }
    fun rows(): List<Row> = rows
    fun revision(): Long = revision
    fun legacy(): ModelDeployment = models[ModelDeployment.LEGACY]!!
    fun selected(): ModelDeployment {
        if (!loaded || uncertain) throw IOException("模型目录尚未完成安全恢复或持久发布待恢复")
        return require(selectedKey ?: throw IOException("请选择模型"))
    }
    fun require(key: String): ModelDeployment = models[key] ?: throw IOException("模型安装身份已失效")
    private fun create(policy: ModelPackagePolicy, installation: String): ModelDeployment {
        val key = if (policy.trustedLegacy) ModelDeployment.LEGACY else policy.contentSha256
        val ready = ModelReadiness()
        val domain = if (policy.trustedLegacy) "" else key + ":" + installation + ":" + ModelPackagePolicy.sha(policy.json())
        val verification = ModelVerification(policy.manifest, ready, io.proof(key), resident, domain)
        val repo = if (policy.trustedLegacy) ModelRepository.forAppFiles(policy.manifest, files, space, verification)
            else ModelRepository.forVariant(policy.manifest, files, key, space, verification)
        val access = ModelAccess(repo, ready, resident, if (policy.trustedLegacy) "" else "$key:$installation:")
        return ModelDeployment(policy, installation, repo, ready, access, if (policy.trustedLegacy) cache else File(File(cache, "model-variants"), key))
    }
    private fun observe(model: ModelDeployment) {
        val listener = ModelReadiness.Listener { if (selectedKey == model.key) publishReadiness() }
        observers[model.readiness] = listener; model.readiness.addListener(listener)
    }
    /** Startup worker only; never hashes assets or prepares native. */
    private fun reconcile() {
        if (!uncertain) return
        try {
            rollbackBytes?.let { parseDocument(it) } // Validate before any restore/clear side effect.
            io.restoreCatalog(rollbackBytes)
            val actual = io.readCatalog()
            if (!(actual?.contentEquals(rollbackBytes ?: byteArrayOf()) ?: (rollbackBytes == null))) throw IOException("目录回滚读取不一致")
            io.clearRecovery()
            uncertain = false
            publishReadiness()
        } catch (failure: Exception) { throw IOException("模型目录持久发布未确认，请重试恢复", failure) }
    }
    fun load() {
        if (!uncertain) try {
            io.readRecovery()?.let { recovery ->
                if (recovery.isEmpty() || recovery.size > 65537 || recovery[0].toInt() !in 0..1 || (recovery[0].toInt() == 0 && recovery.size != 1))
                    throw IOException("目录恢复记录无效")
                val previous = if (recovery[0].toInt() == 0) null else recovery.copyOfRange(1, recovery.size)
                previous?.let { parseDocument(it) }
                rollbackBytes = previous
                uncertain = true
            }
        } catch (failure: IOException) {
            loaded = false; resident.invalidate(); publishReadiness()
            throw IOException("目录恢复记录无效，请保留数据并重试", failure)
        }
        reconcile()
        if (loaded) return
        val bytes = try { io.readCatalog() } catch (failure: IOException) {
            selectedKey = null; publishReadiness(); throw failure
        }
        if (bytes == null) { loaded = true; refresh(); return }
        // No authority until the same side-effect-free parser used for recovery succeeds.
        selectedKey = null; publishReadiness()
        val document = parseDocument(bytes)
        val rebuilt = document.entries.map { create(it.first, it.second) }
        for (deployment in rebuilt) { models[deployment.key] = deployment; observe(deployment) }
        revision = document.revision
        selectedKey = document.selection
        loaded = true; refresh()
    }
    private class Document(val revision: Long, val selection: String?, val entries: List<Pair<ModelPackagePolicy, String>>)
    /** Finite metadata validation only: never creates repositories/proofs or publishes state. */
    private fun parseDocument(bytes: ByteArray): Document {
        if (bytes.size > 65536) throw IOException("模型目录元信息过大")
        val parsed = ArrayList<Pair<ModelPackagePolicy, String>>()
        val next: String?
        val generation: Long
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != 0x4d434132) throw IOException("模型目录格式无效")
            generation = input.readLong()
            next = input.readUTF().takeIf { it.isNotEmpty() }
            val count = input.readInt()
            if (count !in 0..16 || generation < 0) throw IOException("模型目录数量无效")
            repeat(count) {
                val id = input.readUTF()
                if (!id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) throw IOException("安装世代无效")
                val size = input.readInt()
                if (size !in 1..16384) throw IOException("模型描述过大")
                val descriptor = ByteArray(size); input.readFully(descriptor)
                val policy = ModelPackagePolicy.parse(descriptor, fixed)
                if (policy.trustedLegacy || parsed.any { it.first.contentSha256 == policy.contentSha256 || it.first.modelId == policy.modelId }) throw IOException("模型目录身份冲突")
                parsed.add(policy to id)
            }
            if (input.read() != -1) throw IOException("模型目录含未知内容")
        }
        if (next != null && next != ModelDeployment.LEGACY && parsed.none { it.first.contentSha256 == next })
            throw IOException("模型目录选择身份无效")
        return Document(generation, next, parsed)
    }
    private fun persist(nextSelection: String?, values: Collection<ModelDeployment>) {
        if (revision == Long.MAX_VALUE) throw IOException("模型目录世代耗尽")
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(0x4d434132); output.writeLong(revision + 1); output.writeUTF(nextSelection ?: "")
            val custom = values.filter { !it.policy.trustedLegacy }.sortedBy { it.key }; output.writeInt(custom.size)
            for (model in custom) {
                val descriptor = model.policy.json()
                output.writeUTF(model.installationId); output.writeInt(descriptor.size); output.write(descriptor)
            }
        }
        if (bytes.size() > 65536) throw IOException("模型目录元信息过大")
        val previous = io.readCatalog()?.clone()
        // Durable rollback intent precedes replacement, including first-install absence.
        // A restarted catalog reconciles this marker before admitting any selection.
        try { io.writeRecovery(byteArrayOf(if (previous == null) 0 else 1) + (previous ?: byteArrayOf())) }
        catch (failure: Exception) {
            rollbackBytes = previous; uncertain = true; resident.invalidate(); publishReadiness()
            try { reconcile() } catch (rollback: Exception) { if (rollback !== failure) failure.addSuppressed(rollback) }
            throw IOException("模型目录恢复记录写入失败", failure)
        }
        try { io.writeCatalog(bytes.toByteArray()); io.clearRecovery() }
        catch (failure: Exception) {
            rollbackBytes = previous; uncertain = true
            resident.invalidate(); publishReadiness()
            // clearRecovery may itself remove then fail directory sync; re-establish
            // recovery intent before attempting a rollback that can also fail.
            try { io.writeRecovery(byteArrayOf(if (previous == null) 0 else 1) + (previous ?: byteArrayOf())) }
            catch (marker: Exception) { if (marker !== failure) failure.addSuppressed(marker) }
            try { reconcile() } catch (rollback: Exception) { if (rollback !== failure) failure.addSuppressed(rollback) }
            throw IOException("模型目录持久写入失败（已尝试恢复此前状态）", failure)
        }
        revision++
    }
    /** Descriptor was already fully CRC/SHA/config audited by the archive owner. */
    fun admit(policy: ModelPackagePolicy): ModelDeployment {
        load()
        if (policy.trustedLegacy) return legacy()
        val existing = models[policy.contentSha256]
        if (existing != null) {
            if (!existing.policy.json().contentEquals(policy.json())) throw IOException("相同内容已有不同身份/精度/名称")
            return existing
        }
        if (models.values.any { it.policy.modelId == policy.modelId }) throw IOException("相同模型ID对应不同内容，请使用新ID")
        if (models.size >= 17) throw IOException("已达到16个自定义模型上限")
        val created = create(policy, UUID.randomUUID().toString())
        // Resolve the trusted Context parent lazily before comparing the model leaf.
        created.repository.inspect(ModelRepository.NEVER_CANCEL)
        val status = ModelRepository.checkRoot(created.repository.modelDir())
        if (status != ModelRepository.RootStatus.ROOT_MISSING) throw IOException("模型内容目录已有未登记数据，已保留；禁止覆盖")
        persist(selectedKey, models.values + created) // Descriptor is durable BEFORE any model asset mutation.
        models[created.key] = created; observe(created); refresh(); return created
    }
    private fun ensureCache(model: ModelDeployment) {
        val parent = cache.canonicalFile
        if (!parent.isDirectory) throw IOException("模型cache父目录不可用")
        if (model.policy.trustedLegacy) return
        val variants = File(parent, "model-variants")
        ModelManifest.requireSafeRoot(variants)
        if (ModelRepository.checkRoot(variants) != ModelRepository.RootStatus.ROOT_OK) throw IOException("模型cache父目录不安全")
        val leaf = File(variants, model.key)
        ModelManifest.requireSafeRoot(leaf)
        if (ModelRepository.checkRoot(leaf) != ModelRepository.RootStatus.ROOT_OK) throw IOException("模型cache目录不安全")
    }
    fun prepareCache(model: ModelDeployment) { ensureCache(model) }
    fun select(key: String) {
        load(); val next = require(key)
        if (selectedKey == key) return
        resident.acquire().use {
            if (next.repository.modelDir().exists()) next.repository.validateManagedBoundary(ModelRepository.NEVER_CANCEL)
            next.repository.inspect(ModelRepository.NEVER_CANCEL)
            persist(key, models.values)
            selectedKey = key
        }
        try { next.access.restoreFast() } catch (_: IOException) { /* Chosen but unverified is honest. */ }
        refresh()
    }
    fun confirmation(key: String): String {
        val model = require(key)
        return "$revision:${model.key}:${model.installationId}:${model.readiness.epoch()}"
    }
    fun deleted(model: ModelDeployment) {
        reconcile()
        if (require(model.key) !== model) throw IOException("安装已变化")
        if (!model.policy.trustedLegacy) model.repository.removeEmptyVariantRoot()
        val selection = if (selectedKey == model.key) null else selectedKey
        val remaining = if (model.policy.trustedLegacy) models.values.toList() else models.values.filter { it !== model }
        persist(selection, remaining)
        if (!model.policy.trustedLegacy) {
            models.remove(model.key)
            observers.remove(model.readiness)?.let { model.readiness.removeListener(it) }
        }
        selectedKey = selection; refresh()
    }
    fun publishReadiness() {
        // Light immutable row update after proof restore/IME prepare; never filesystem IO.
        rows = Collections.unmodifiableList(rows.map { row ->
            val ready = row.deployment.readiness.snapshot()
            Row(row.deployment, selectedKey == row.deployment.key, row.files, row.installedBytes,
                row.error, if (ready.ready()) 7 else 0, ready.epoch)
        })
        val snapshot = if (!loaded || uncertain) null else selectedKey?.let { models[it]?.readiness?.snapshot() }
        projection.installProjection(snapshot, selectedKey?.let { models[it] })
    }
    fun refresh() {
        val result = models.values.sortedBy { if (it.policy.trustedLegacy) "" else it.key }.map { model ->
            try {
                val report = model.repository.inspect(ModelRepository.NEVER_CANCEL)
                model.readiness.inspect(report)
                Row(model, selectedKey == model.key, report.files, report.installedBytes, false,
                    if (model.readiness.isReady) 7 else 0, model.readiness.epoch())
            } catch (_: IOException) {
                model.readiness.markInvalidState()
                if (selectedKey == model.key) resident.invalidate()
                Row(model, selectedKey == model.key, emptyList(), -1, true, 0, model.readiness.epoch())
            }
        }
        rows = Collections.unmodifiableList(result); publishReadiness()
    }
}
