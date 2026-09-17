package org.llmasr.minimal.transcription;

/** App-only text ownership. Text/revision and export revocation epoch change
 * under this monitor. No IO or observers run under it. Edits use revision;
 * exports freeze text and survive edits/new inference, but NOT explicit clear.
 * Export workers check the epoch and mark write admission under this same
 * monitor immediately before provider open. After admission clear cannot undo
 * the external side effect. */
public final class ResultState {
    public static final class Snapshot {
        public final long revision;
        public final String text;
        private final long clearEpoch;
        private final ResultState owner;
        private Snapshot(ResultState owner, long revision, String text, long epoch) {
            this.owner=owner; this.revision=revision; this.text=text; clearEpoch=epoch;
        }
        public boolean isEmpty() { return text.isEmpty(); }
    }
    private long revision, clearEpoch;
    private String text="";

    public synchronized Snapshot begin() { return new Snapshot(this, revision, text, clearEpoch); }
    public synchronized void setText(String value) { text=value == null ? "" : value; revision++; }
    public synchronized void clear() { text=""; revision++; clearEpoch++; }
    public synchronized long applyEdit(Snapshot expected, String edited) {
        if (expected == null || expected.owner != this) return -1;
        return applyEdit(expected.revision, edited);
    }
    public synchronized long applyEdit(long expectedRevision, String edited) {
        if (expectedRevision != revision) return -1;
        text=edited == null ? "" : edited;
        return ++revision;
    }
    /** Called while holding this monitor through the controller's state change.
     * Package-private: a snapshot is not a general external-write capability. */
    synchronized boolean exportValid(Snapshot snapshot) {
        return snapshot != null && snapshot.owner == this && snapshot.clearEpoch == clearEpoch;
    }
    public synchronized boolean live(long value) { return revision == value; }
    public synchronized long currentRevision() { return revision; }
    public synchronized String currentText() { return text; }
}
