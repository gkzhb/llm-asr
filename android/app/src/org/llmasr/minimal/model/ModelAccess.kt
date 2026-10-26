package org.llmasr.minimal.model

import java.io.IOException

/** Shared worker admission. Requests never restore, hash or load the model. */
class ModelAccess(private val repository: ModelRepository?, private val readiness: ModelReadiness?) {
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun requireReady() {
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        val proof = repo.verification() ?: throw IOException("模型未准备，请到模型管理完整校验")
        proof.requireReady(repo)
    }
    /** Startup MAINTENANCE / explicit management worker only; never falls back to SHA. */
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun restoreFast(): Boolean {
        val repo = repository ?: throw IOException("模型管理尚未初始化")
        val proof = repo.verification() ?: throw IOException("缺少持久模型证明")
        return proof.restoreFast(repo)
    }
}
