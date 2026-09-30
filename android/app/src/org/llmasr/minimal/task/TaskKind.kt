package org.llmasr.minimal.task

/** Per-request classification; business policy lives outside the shared runner. */
enum class TaskKind {
    INFERENCE,
    MODEL_OPERATION,
    MAINTENANCE
}
