package org.llmasr.minimal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure model result types: no page/UI/controller references.
 *  This is the shared vocabulary between the model core
 *  (ModelRepository, ModelReadiness), the controller, and the page
 *  snapshot. Anything that depends on this class is independent of
 *  ModelManagementState. Page state (Phase/Outcome/OperationKind/Snapshot)
 *  must not appear here. */
public final class ModelReports {
    private ModelReports() {}

    /** Completed import budget; availableAtCheck=-1 until safe reclaim finishes.
     * Zero copy needs zero additional space (no pointless padding-only refusal). */
    public static final class ImportPlan {
        public final long copyBytes, reusedBytes, requiredBytes, availableAtCheck;
        public final List<String> reusableFiles;
        public ImportPlan(long copyBytes, long reusedBytes, long availableAtCheck, List<String> reusableFiles) {
            this.copyBytes=copyBytes; this.reusedBytes=reusedBytes;
            this.requiredBytes=copyBytes == 0 ? 0 : Math.addExact(copyBytes, ModelRepository.SPACE_PAD);
            this.availableAtCheck=availableAtCheck;
            this.reusableFiles=Collections.unmodifiableList(new ArrayList<>(reusableFiles));
        }
    }

    public static final class FileDetail {
        public final String name;
        public final long expectedBytes;
        public final long actualBytes;
        public final boolean present;
        public final boolean sizeMatch;
        public final boolean partExists;
        /** True only after a full verifyAll for this epoch has passed. */
        public final boolean verified;
        public FileDetail(String name, long expectedBytes, long actualBytes,
                          boolean present, boolean sizeMatch, boolean partExists, boolean verified) {
            this.name = name;
            this.expectedBytes = expectedBytes;
            this.actualBytes = actualBytes;
            this.present = present;
            this.sizeMatch = sizeMatch;
            this.partExists = partExists;
            this.verified = verified;
        }
    }

    /** Typed per-file observation. The repository hands a value at every
     *  visible per-file milestone; presentation text lives in ModelUiText
     *  and the snapshot only carries the structured form so model code
     *  never embeds presentation strings. Failure reasons are closed model codes. */
    public static final class FileOutcome {
        public enum Status {
            /** File is queued for copy in the current run. */
            PENDING,
            /** Existing file reused because it already matches the manifest SHA. */
            REUSED,
            /** This file's .part was published; full-manifest verify still pending. */
            PUBLISHED,
            /** Full-manifest verifyAll accepted this file in the current epoch. */
            FINAL_VERIFIED,
            /** Per-file IO / SHA failure. */
            FAILED
        }
        public enum Failure { SHA, SIZE, SOURCE_MISSING, TOO_LARGE, WRITE_SIZE, IO }
        public final Status status;
        public final Failure failure;
        public FileOutcome(Status status) { this(status, Failure.IO); }
        public FileOutcome(Status status, Failure failure) {
            if (status == null || failure == null) throw new IllegalArgumentException("outcome required");
            this.status = status;
            this.failure = failure;
        }
        public boolean isFailure() { return status == Status.FAILED; }
    }
}
