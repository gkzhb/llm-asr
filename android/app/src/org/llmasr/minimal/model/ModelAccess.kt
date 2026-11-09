package org.llmasr.minimal.model

import java.io.IOException
import java.util.concurrent.CancellationException

/** Shared worker admission; only explicit prepareForCapture may restore/hash/load. */
class ModelAccess @JvmOverloads constructor(private val repository: ModelRepository?, private val readiness: ModelReadiness?,
    private val residency: ModelResidency? = null, private val domain: String = "") {
    private var catalog: ModelCatalog? = null
    private var pinned: Binding? = null
    private constructor(catalog: ModelCatalog, residency: ModelResidency) : this(null, null, residency) { this.catalog = catalog }
    companion object {
        @JvmStatic fun forCatalog(catalog: ModelCatalog, residency: ModelResidency): ModelAccess = ModelAccess(catalog, residency)
    }
    class Binding internal constructor(@JvmField val deployment: ModelDeployment)
    /** Shared-worker request binding. No selection reread after binding until actual cleanup. */
    @Throws(IOException::class)
    fun pin(): Binding? {
        val models = catalog ?: return null
        if (pinned != null) throw IOException("模型请求已绑定")
        return Binding(models.selected()).also { pinned = it }
    }
    @Throws(IOException::class)
    fun unpin(binding: Binding?) {
        if (binding == null) return
        if (pinned !== binding) throw IOException("模型请求绑定失效")
        pinned = null
        catalog?.publishReadiness()
    }
    private fun target(): ModelAccess? = catalog?.let { (pinned?.deployment ?: it.selected()).access }
    @Throws(IOException::class)
    fun configPath(fallback: String): String = if (catalog == null) fallback else
        (pinned?.deployment ?: throw IOException("模型请求尚未绑定")).configPath()
    @Throws(IOException::class)
    fun cachePath(fallback: String): String = if (catalog == null) fallback else
        (pinned?.deployment ?: throw IOException("模型请求尚未绑定")).cache.absolutePath
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun requireReady() {
        target()?.let { it.requireReady(); return }
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        val proof = repo.verification() ?: throw IOException("模型未准备，请到模型管理完整校验")
        proof.requireReady(repo)
    }
    @Throws(IOException::class)
    fun readyGeneration(): String {
        target()?.let { return it.readyGeneration() }
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        return domain + (repo.verification() ?: throw IOException("缺少持久模型证明")).readyGeneration(repo)
    }
    @Throws(IOException::class)
    fun requireResidentReady() {
        val resident = residency ?: throw IOException("模型内存管理未绑定")
        resident.requireReady(readyGeneration())
    }
    @Throws(Exception::class)
    fun prepare(config: String, cache: String): Double {
        target()?.let {
            val binding = pinned ?: throw IOException("模型请求尚未绑定")
            catalog!!.prepareCache(binding.deployment)
            return it.prepare(configPath(config), cachePath(cache))
        }
        val resident = residency ?: throw IOException("模型内存管理未绑定")
        val g = readyGeneration()
        return resident.prepare(g, config, cache) { if (readyGeneration() != g) throw IOException("模型世代已变化") }
    }
    /** Feature-supplied arbitration; model core never imports audio or presentation packages. */
    interface PreparationControl {
        fun cancelled(): Boolean
        fun reserve(ready: ModelReadiness, token: ModelReadiness.VerifyToken): Boolean
        fun succeed(ready: ModelReadiness, token: ModelReadiness.VerifyToken): Boolean
    }
    /** Explicit user intent, inside the already-held request owner. Never submits another task. */
    @Throws(Exception::class)
    fun prepareForCapture(control: PreparationControl, config: String, cache: String, progress: (String) -> Unit) {
        target()?.let {
            val binding = pinned ?: throw IOException("模型请求尚未绑定")
            catalog!!.prepareCache(binding.deployment)
            it.prepareForCapture(control, configPath(config), cachePath(cache), progress); return
        }
        fun checkpoint() { if (control.cancelled()) throw CancellationException() }
        checkpoint()
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        val proof = repo.verification() ?: throw IOException("缺少模型证明，请到模型管理导入 / 修复")
        val ready = readiness ?: throw IOException("模型状态未绑定")
        val current = try { requireReady(); true } catch (unverified: IOException) { false }
        if (!current) {
            progress("正在恢复模型证明……")
            checkpoint()
            val restored = try { restoreFast() } catch (unverified: IOException) { false }
            checkpoint()
            if (!restored) {
                var token: ModelVerification.Token? = null
                var success = false
                try {
                    checkpoint()
                    progress("正在完整校验模型 SHA-256，可取消……")
                    checkpoint()
                    token = proof.begin(repo)
                    repo.verifyAll(object : ModelRepository.Progress {
                        override fun update(stage: ModelRepository.Stage?, file: String?, index: Int, count: Int,
                            done: Long, total: Long, sd: Long, st: Long, reused: Long) {
                            progress("正在完整校验模型 SHA-256：${index + 1} / $count，$sd / $st 字节")
                        }
                    }, object : ModelRepository.CancelGate {
                        override fun cancelled(): Boolean = control.cancelled()
                        override fun reservePublish(): Boolean = false // Verification never publishes model files.
                    })
                    success = proof.commit(repo, token, object : ModelVerification.CommitGate {
                        override fun reserve(epoch: ModelReadiness.VerifyToken): Boolean = control.reserve(ready, epoch)
                        override fun succeed(epoch: ModelReadiness.VerifyToken): Boolean = control.succeed(ready, epoch)
                    })
                    if (!success) throw CancellationException()
                } catch (cancelled: ModelRepository.CancelledException) {
                    throw CancellationException()
                } catch (failure: ModelRepository.InvalidModelException) {
                    ready.markInvalid()
                    throw failure
                } finally {
                    if (!success && token != null) ready.finishUnverified(token.epoch)
                    ready.notifyChange()
                }
            }
        }
        checkpoint()
        progress("正在加载 / 复用模型到内存；取消将等待加载实际结束……")
        checkpoint()
        prepare(config, cache) // Synchronous native return owns the worker until real completion.
        checkpoint()
        requireResidentReady()
    }
    /** Generation is resolved on worker before dispatch; no filesystem IO at hardware start. */
    @Throws(IOException::class)
    fun startCapture(generation: String, start: () -> Boolean): Boolean =
        (residency ?: throw IOException("模型内存管理未绑定")).startCapture(generation, start)
    fun requestRelease() { residency?.requestRelease() }
    @Throws(Exception::class)
    fun release() { residency?.drainRelease() }
    fun finishSelectionSnapshot() { catalog?.publishReadiness() }
    fun residentSnapshot(): ModelResidency.Snapshot? = residency?.snapshot()
    @Throws(IOException::class)
    fun enterRequest(): Any = (residency ?: throw IOException("模型内存管理未绑定")).enter(readyGeneration())
    @Throws(Exception::class)
    fun finishRequest(token: Any, success: Boolean) { (residency ?: throw IOException("模型内存管理未绑定")).finish(token, success) }
    /** Startup MAINTENANCE / explicit management worker only; never falls back to SHA. */
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun restoreFast(): Boolean {
        catalog?.let { it.load(); return it.selected().access.restoreFast().also { _ -> it.publishReadiness() } }
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        val proof = repo.verification() ?: throw IOException("缺少持久模型证明")
        return proof.restoreFast(repo)
    }
}
