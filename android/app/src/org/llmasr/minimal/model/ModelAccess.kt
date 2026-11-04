package org.llmasr.minimal.model

import java.io.IOException
import java.util.concurrent.CancellationException

/** Shared worker admission; only explicit prepareForCapture may restore/hash/load. */
class ModelAccess @JvmOverloads constructor(private val repository: ModelRepository?, private val readiness: ModelReadiness?,
    private val residency: ModelResidency? = null) {
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun requireReady() {
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        val proof = repo.verification() ?: throw IOException("模型未准备，请到模型管理完整校验")
        proof.requireReady(repo)
    }
    @Throws(IOException::class)
    fun readyGeneration(): String {
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        return (repo.verification() ?: throw IOException("缺少持久模型证明")).readyGeneration(repo)
    }
    @Throws(IOException::class)
    fun requireResidentReady() {
        val resident = residency ?: throw IOException("模型内存管理未绑定")
        resident.requireReady(readyGeneration())
    }
    @Throws(Exception::class)
    fun prepare(config: String, cache: String): Double {
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
    fun residentSnapshot(): ModelResidency.Snapshot? = residency?.snapshot()
    @Throws(IOException::class)
    fun enterRequest(): Any = (residency ?: throw IOException("模型内存管理未绑定")).enter(readyGeneration())
    @Throws(Exception::class)
    fun finishRequest(token: Any, success: Boolean) { (residency ?: throw IOException("模型内存管理未绑定")).finish(token, success) }
    /** Startup MAINTENANCE / explicit management worker only; never falls back to SHA. */
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun restoreFast(): Boolean {
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        val proof = repo.verification() ?: throw IOException("缺少持久模型证明")
        return proof.restoreFast(repo)
    }
}
