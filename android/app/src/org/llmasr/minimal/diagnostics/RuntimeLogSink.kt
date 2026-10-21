package org.llmasr.minimal.diagnostics

/** Read-only, best-effort snapshot listener. Publications are serialized and
 * coalesced; intermediate snapshots may be skipped. droppedCount is the total
 * evicted during this process (saturated at Integer.MAX_VALUE), not a delta.
 * Callbacks run outside the store lock, must be quick/nonblocking, and must not
 * perform IO. RuntimeExceptions are isolated. Removal does not cancel a callback
 * already in progress, so UI owners must also gate lifecycle delivery. */
fun interface RuntimeLogSink {
    fun onSnapshot(snapshot: List<RuntimeLogEvent?>?, droppedCount: Int)

    /** No-op sink used when no UI / persistence is wired. */
    class None : RuntimeLogSink {
        // Keep the original Java non-final method flag; None itself remains final.
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun onSnapshot(snapshot: List<RuntimeLogEvent?>?, droppedCount: Int) {}
    }
}
