package org.llmasr.minimal.model

import java.io.File

/** Immutable installation identity; mutable authorities are local to this installation only. */
class ModelDeployment(
    @JvmField val policy: ModelPackagePolicy,
    @JvmField val installationId: String,
    @JvmField val repository: ModelRepository,
    @JvmField val readiness: ModelReadiness,
    @JvmField val access: ModelAccess,
    @JvmField val cache: File
) {
    @JvmField val key: String = if (policy.trustedLegacy) LEGACY else policy.contentSha256
    fun configPath(): String = File(repository.modelDir(), "config.json").absolutePath
    fun residentGeneration(): String = key + ":" + installationId + ":" + access.readyGeneration()
    companion object { const val LEGACY = "legacy-v1" }
}
