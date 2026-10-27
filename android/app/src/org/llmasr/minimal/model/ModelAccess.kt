package org.llmasr.minimal.model

import java.io.IOException

/** Shared worker admission. Requests never restore, hash or load the model. */
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
