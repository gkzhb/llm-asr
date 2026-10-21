package org.llmasr.minimal.model

import java.util.ArrayList
import java.util.Collections

/** Immutable model vocabulary, independent of the management page. */
class ModelReports private constructor() {
    class ImportPlan(copyBytes: Long, reusedBytes: Long, availableAtCheck: Long, reusableFiles: List<String?>?) {
        @JvmField val copyBytes: Long = copyBytes
        @JvmField val reusedBytes: Long = reusedBytes
        @JvmField val requiredBytes: Long = if (copyBytes == 0L) 0L else Math.addExact(copyBytes, ModelRepository.SPACE_PAD)
        @JvmField val availableAtCheck: Long = availableAtCheck
        @JvmField val reusableFiles: List<String?> = Collections.unmodifiableList(ArrayList(reusableFiles!!))
    }
    class FileDetail(
        @JvmField val name: String?, @JvmField val expectedBytes: Long, @JvmField val actualBytes: Long,
        @JvmField val present: Boolean, @JvmField val sizeMatch: Boolean, @JvmField val partExists: Boolean,
        @JvmField val verified: Boolean
    )
    class FileOutcome {
        enum class Status { PENDING, REUSED, PUBLISHED, FINAL_VERIFIED, FAILED }
        enum class Failure { SHA, SIZE, SOURCE_MISSING, TOO_LARGE, WRITE_SIZE, IO }
        @JvmField val status: Status
        @JvmField val failure: Failure
        constructor(status: Status?) : this(status, Failure.IO)
        constructor(status: Status?, failure: Failure?) {
            if (status == null || failure == null) throw IllegalArgumentException("outcome required")
            this.status = status
            this.failure = failure
        }
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun isFailure(): Boolean = status == Status.FAILED
    }
}
