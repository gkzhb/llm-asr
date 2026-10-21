package org.llmasr.minimal.model

import java.io.IOException
import java.io.InputStream

/** Bounded source port. The repository owns validation, cancellation and stream lifetime. */
interface ModelSource {
    @Throws(IOException::class)
    fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, UriRef?>?

    @Throws(IOException::class)
    fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int, cancel: ModelRepository.CancelGate?): MutableMap<String?, UriRef?>? {
        if (cancel!!.cancelled()) throw ModelRepository.CancelledException()
        val refs = enumerate(expected, maxEntries)
        if (cancel.cancelled()) throw ModelRepository.CancelledException()
        return refs
    }

    @Throws(IOException::class)
    fun open(ref: UriRef?): InputStream?

    class UriRef(private val handle: Any?) {
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun handle(): Any? = handle
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun toString(): String = java.lang.String.valueOf(handle)
    }
}
