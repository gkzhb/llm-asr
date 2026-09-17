package org.llmasr.minimal.model;

import java.io.IOException;

/** Shared App/IME lazy SHA path. Caller must already hold the process task owner.
 * A stale commit is a failure, never permission to invoke native inference. */
public final class ModelAccess {
    private final ModelRepository repository;
    private final ModelReadiness readiness;
    public ModelAccess(ModelRepository repository, ModelReadiness readiness) {
        this.repository = repository; this.readiness = readiness;
    }
    public void requireReady() throws IOException {
        ModelReadiness.VerifyToken token = null;
        try {
            // Validate all managed names even with cached SHA evidence; no orphan deletion.
            repository.validateManagedBoundary(ModelRepository.NEVER_CANCEL);
            token = readiness.tryBeginVerify();
            if (token == null) return;
            repository.verifyAll(null, ModelRepository.NEVER_CANCEL);
            if (!readiness.commitVerified(token))
                throw new IOException("模型已变化，请到模型管理重新校验");
        } catch (IOException | RuntimeException failure) {
            readiness.finishUnverified(token);
            if (failure instanceof ModelRepository.InvalidModelException) readiness.markInvalid();
            try { readiness.inspect(repository.inspect(ModelRepository.NEVER_CANCEL)); }
            catch (IOException | RuntimeException ignored) { readiness.markInvalid(); }
            throw new IOException("模型不可用，请打开模型管理检查、校验或修复导入", failure);
        } finally {
            readiness.finishUnverified(token);
            readiness.notifyChange(); // finish/inspect intentionally do not notify themselves.
        }
    }
}
