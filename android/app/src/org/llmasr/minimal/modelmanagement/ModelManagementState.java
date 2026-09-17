package org.llmasr.minimal.modelmanagement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelReports;

/** Typed snapshot for the Model Management page. Each snapshot is immutable;
 *  the controller publishes a new one on every meaningful transition. The
 *  Activity renders from snapshots and never reads worker-local state.
 *
 *  Fields default to unknown/zero values that are NEVER confused with valid
 *  data. The UI explicitly shows "—" rather than 0 when a field is unknown
 *  (the controller is responsible for that translation; this class only
 *  carries raw counts and booleans).
 */
public final class ModelManagementState {
    public enum Phase {
        IDLE,
        INSPECTING,
        ENUMERATING,
        CHECKING_EXISTING,
        COPYING,
        VERIFYING_FILE,
        PUBLISHING,
        FINAL_VERIFY,
        DELETING,
        CANCELLING,
        SUCCEEDED,
        CANCELLED,
        FAILED
    }
    public enum Outcome { NONE, SUCCEEDED, CANCELLED, FAILED }
    public enum OperationKind { NONE, INSPECT, IMPORT, VERIFY, DELETE }

    /** The page snapshot carries an immutable ModelReports.ImportPlan
     *  re-homed from the legacy nested type. Reflective test access and
     *  the public field name stay the same; the file's public field API
     *  is provided by the new pure model type. */

    public static final class Snapshot {
        public final long modelEpoch;
        public final ModelReadiness.State readiness;
        public final boolean ready;
        public final long expectedBytes;
        public final long installedBytes;
        public final long orphanPartBytes;
        public final long availableBytes;
        /** Current or retained explicit user-operation ID. Actual active ownership/identity
         * lives only in ModelOperationControl; INSPECT never borrows this historical ID. */
        public final String activeOperationId;
        public final OperationKind operationKind;
        public final Phase phase;
        public final String fileName;
        public final int fileIndex;
        public final int fileCount;
        public final long fileBytesDone;
        public final long fileBytesTotal;
        public final long stageBytesDone;
        public final long stageBytesTotal;
        public final long reusedBytes;
        public final Outcome lastOutcome;
        public final String errorCode;
        public final List<String> failedFiles;
        public final Outcome cleanupOutcome;
        public final List<ModelReports.FileDetail> files;
        public final List<String> unexpectedFiles;
        public final int deleteSucceeded;
        public final int deleteFailed;
        public final long deleteFreedBytes;
        public final boolean pageOwnerHeld;
        /** Inventory refresh is transient; operation fields retain the last explicit user record. */
        public final boolean inspectBusy;
        public final String inspectError;
        public final ModelReports.ImportPlan importPlan;
        /** Last explicit operation's per-file observations, not inventory SHA evidence. */
        public final java.util.Map<String,ModelReports.FileOutcome> fileResults;

        public Snapshot(long modelEpoch, ModelReadiness.State readiness, boolean ready,
                        long expectedBytes, long installedBytes, long orphanPartBytes, long availableBytes,
                        String activeOperationId, OperationKind operationKind, Phase phase,
                        String fileName, int fileIndex, int fileCount,
                        long fileBytesDone, long fileBytesTotal,
                        long stageBytesDone, long stageBytesTotal,
                        long reusedBytes,
                        Outcome lastOutcome, String errorCode,
                        List<String> failedFiles, Outcome cleanupOutcome,
                        List<ModelReports.FileDetail> files, List<String> unexpectedFiles,
                        int deleteSucceeded, int deleteFailed, long deleteFreedBytes,
                        boolean pageOwnerHeld, boolean inspectBusy, String inspectError, ModelReports.ImportPlan importPlan, java.util.Map<String,ModelReports.FileOutcome> fileResults) {
            this.modelEpoch = modelEpoch;
            this.readiness = readiness;
            this.ready = ready;
            this.expectedBytes = expectedBytes;
            this.installedBytes = installedBytes;
            this.orphanPartBytes = orphanPartBytes;
            this.availableBytes = availableBytes;
            this.activeOperationId = activeOperationId;
            this.operationKind = operationKind;
            this.phase = phase;
            this.fileName = fileName;
            this.fileIndex = fileIndex;
            this.fileCount = fileCount;
            this.fileBytesDone = fileBytesDone;
            this.fileBytesTotal = fileBytesTotal;
            this.stageBytesDone = stageBytesDone;
            this.stageBytesTotal = stageBytesTotal;
            this.reusedBytes = reusedBytes;
            this.lastOutcome = lastOutcome;
            this.errorCode = errorCode;
            this.failedFiles = failedFiles == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(failedFiles));
            this.cleanupOutcome = cleanupOutcome;
            this.files = files == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(files));
            this.unexpectedFiles = unexpectedFiles == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(unexpectedFiles));
            this.deleteSucceeded = deleteSucceeded;
            this.deleteFailed = deleteFailed;
            this.deleteFreedBytes = deleteFreedBytes;
            this.pageOwnerHeld = pageOwnerHeld;
            this.inspectBusy=inspectBusy; this.inspectError=inspectError;
            this.importPlan=importPlan;
            this.fileResults=Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fileResults));
        }

        public boolean isTerminalPhase() {
            return phase == Phase.SUCCEEDED || phase == Phase.CANCELLED || phase == Phase.FAILED;
        }
    }

    /** Worker-local snapshot builder. No mutable lists escape build(). */
    public static final class Builder {
        public long modelEpoch, expectedBytes, installedBytes, orphanPartBytes, availableBytes;
        public ModelReadiness.State readiness;
        public boolean ready, pageOwnerHeld, inspectBusy;
        public String inspectError;
        public ModelReports.ImportPlan importPlan;
        public java.util.Map<String,ModelReports.FileOutcome> fileResults;
        public String activeOperationId, fileName, errorCode;
        public OperationKind operationKind;
        public Phase phase;
        public int fileIndex, fileCount, deleteSucceeded, deleteFailed;
        public long fileBytesDone, fileBytesTotal, stageBytesDone, stageBytesTotal, reusedBytes, deleteFreedBytes;
        public Outcome lastOutcome, cleanupOutcome;
        public List<String> failedFiles, unexpectedFiles;
        public List<ModelReports.FileDetail> files;
        public Builder(Snapshot s) {
            modelEpoch=s.modelEpoch; readiness=s.readiness; ready=s.ready;
            expectedBytes=s.expectedBytes; installedBytes=s.installedBytes; orphanPartBytes=s.orphanPartBytes;
            availableBytes=s.availableBytes; activeOperationId=s.activeOperationId; operationKind=s.operationKind;
            phase=s.phase; fileName=s.fileName; fileIndex=s.fileIndex; fileCount=s.fileCount;
            fileBytesDone=s.fileBytesDone; fileBytesTotal=s.fileBytesTotal;
            stageBytesDone=s.stageBytesDone; stageBytesTotal=s.stageBytesTotal; reusedBytes=s.reusedBytes;
            lastOutcome=s.lastOutcome; errorCode=s.errorCode; failedFiles=s.failedFiles; cleanupOutcome=s.cleanupOutcome;
            files=s.files; unexpectedFiles=s.unexpectedFiles; deleteSucceeded=s.deleteSucceeded;
            deleteFailed=s.deleteFailed; deleteFreedBytes=s.deleteFreedBytes; pageOwnerHeld=s.pageOwnerHeld;
            inspectBusy=s.inspectBusy; inspectError=s.inspectError; importPlan=s.importPlan; fileResults=s.fileResults;
        }
        public Snapshot build() {
            return new Snapshot(modelEpoch, readiness, ready, expectedBytes, installedBytes, orphanPartBytes,
                availableBytes, activeOperationId, operationKind, phase, fileName, fileIndex, fileCount,
                fileBytesDone, fileBytesTotal, stageBytesDone, stageBytesTotal, reusedBytes, lastOutcome,
                errorCode, failedFiles, cleanupOutcome, files, unexpectedFiles, deleteSucceeded, deleteFailed,
                deleteFreedBytes, pageOwnerHeld, inspectBusy, inspectError, importPlan, fileResults);
        }
    }

    public interface Listener { void onChange(); }
    private final java.util.concurrent.CopyOnWriteArrayList<java.lang.ref.WeakReference<Listener>> listeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Keep a strong reference while subscribed. Callbacks are invalidations on
     * the publishing thread: post rendering to the UI, never block for IO/UI. */
    public void addListener(Listener listener) {
        if (listener == null) return;
        removeListener(listener);
        listeners.add(new java.lang.ref.WeakReference<>(listener));
    }
    public void removeListener(Listener listener) {
        listeners.removeIf(ref -> ref.get() == null || ref.get() == listener);
    }
    private final AtomicReference<Snapshot> current = new AtomicReference<>(empty());

    public Snapshot current() { return current.get(); }
    /** Atomically install a new snapshot as the observable current. No
     *  listeners fire here; callers that want a follow-up invalidation
     *  must call {@link #notifyListeners()} after the lock that
     *  guards the install is released. Splitting install and notify
     *  keeps the controller monitor from being held while a listener
     *  re-enters the model management code path. */
    void install(Snapshot s) {
        if (s == null) return;
        current.set(s);
    }
    /** Fire listener callbacks OUTSIDE the controller monitor. Observer
     *  exceptions are swallowed so one listener cannot poison siblings.
     *  An invalidation carries no stale payload; observers read current(). */
    void notifyListeners() {
        for (java.lang.ref.WeakReference<Listener> ref : listeners) {
            Listener listener = ref.get();
            if (listener == null) listeners.remove(ref);
            else try { listener.onChange(); } catch (RuntimeException ignored) { }
        }
    }
    public void publish(Snapshot s) {
        if (s == null) return;
        install(s);
        notifyListeners();
    }

    public static Snapshot empty() {
        return new Snapshot(0L, ModelReadiness.State.UNKNOWN, false,
            -1L, -1L, -1L, -1L,
            null, OperationKind.NONE, Phase.IDLE,
            null, 0, 0,
            0L, 0L,
            0L, 0L,
            0L,
            Outcome.NONE, null,
            Collections.emptyList(), Outcome.NONE,
            Collections.emptyList(), Collections.emptyList(),
            0, 0, 0L,
            false, false, null, null, Collections.emptyMap());
    }
}
