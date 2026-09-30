package org.llmasr.minimal.task

import java.io.File

/** Per-request runtime state, constructed at submit and dropped at finalization. */
class RequestContext(
    @JvmField val requestId: String?,
    @JvmField val kind: TaskKind?
) {
    /** Preflight cleanup count, filled before the body. */
    @JvmField var cleanedTemporary: Int = 0
    /** Body wrote its own terminal report. */
    @JvmField var inferenceReported: Boolean = false
    /** Request temp WAV, deleted by cleanup when present. */
    @JvmField var inputWav: File? = null
}
