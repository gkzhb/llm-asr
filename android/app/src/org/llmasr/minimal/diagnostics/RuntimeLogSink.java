package org.llmasr.minimal.diagnostics;

import java.util.List;

/** Read-only, best-effort snapshot listener. Publications are serialized and
 * coalesced; intermediate snapshots may be skipped. droppedCount is the total
 * evicted during this process (saturated at Integer.MAX_VALUE), not a delta.
 * Callbacks run outside the store lock, must be quick/nonblocking, and must not
 * perform IO. RuntimeExceptions are isolated. Removal does not cancel a callback
 * already in progress, so UI owners must also gate lifecycle delivery. */
public interface RuntimeLogSink {
    void onSnapshot(List<RuntimeLogEvent> snapshot, int droppedCount);

    /** No-op sink used when no UI / persistence is wired. */
    final class None implements RuntimeLogSink {
        @Override public void onSnapshot(List<RuntimeLogEvent> snapshot, int droppedCount) { }
    }
}
