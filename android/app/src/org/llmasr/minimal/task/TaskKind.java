package org.llmasr.minimal.task;

/** Per-request task type. Replaces the prior boolean "report" parameter on launch().
 * INFERENCE clears lastText and writes a pending then terminal inference report.
 * MODEL_OPERATION clears lastText and writes a pending then model-operation report.
 * MAINTENANCE never clears lastText and never writes a pending report.
 */
public enum TaskKind {
    INFERENCE,
    MODEL_OPERATION,
    MAINTENANCE
}
