package org.llmasr.minimal.model

import java.io.IOException

/** Shared lazy verification path; caller already owns the process task. */
class ModelAccess(private val repository: ModelRepository?, private val readiness: ModelReadiness?) {
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun requireReady() {
        var token: ModelReadiness.VerifyToken? = null
        try {
            repository!!.validateManagedBoundary(ModelRepository.NEVER_CANCEL)
            token = readiness!!.tryBeginVerify()
            if (token == null) return
            repository.verifyAll(null, ModelRepository.NEVER_CANCEL)
            if (!readiness.commitVerified(token)) throw IOException("模型已变化，请到模型管理重新校验")
        } catch (failure: IOException) {
            handleFailure(token, failure)
        } catch (failure: RuntimeException) {
            handleFailure(token, failure)
        } finally {
            readiness!!.finishUnverified(token)
            readiness.notifyChange()
        }
    }
    private fun handleFailure(token: ModelReadiness.VerifyToken?, failure: Exception): Nothing {
        readiness!!.finishUnverified(token)
        if (failure is ModelRepository.InvalidModelException) readiness.markInvalid()
        try { readiness.inspect(repository!!.inspect(ModelRepository.NEVER_CANCEL)) }
        catch (ignored: IOException) { readiness.markInvalid() }
        catch (ignored: RuntimeException) { readiness.markInvalid() }
        throw IOException("模型不可用，请打开模型管理检查、校验或修复导入", failure)
    }
}
