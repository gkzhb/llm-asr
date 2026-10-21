package org.llmasr.minimal.model

import java.util.Locale
import java.util.Objects

/** Immutable manifest entry; equality is by content, hash retains the legacy byte budget. */
class ModelEntry(file: String?, private val bytes: Long, sha256: String?) {
    private val file: String = Objects.requireNonNull(file, "file")!!
    private val sha256: String = Objects.requireNonNull(sha256, "sha256")!!.lowercase(Locale.ROOT)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun file(): String = file
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun bytes(): Long = bytes
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun sha256(): String = sha256
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun partName(): String = file + ".part"
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelEntry) return false
        return bytes == other.bytes && file == other.file && sha256 == other.sha256
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun hashCode(): Int = file.hashCode() * 31 + java.lang.Long.hashCode(bytes)
}
