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

    /** Preparation owns all source IO, but never model mutation or proof publication. */
    @Throws(IOException::class)
    fun openSession(manifest: ModelManifest, space: ModelRepository.SpaceProvider,
                    progress: ModelRepository.Progress?, cancel: ModelRepository.CancelGate): Session {
        val source = this
        return object : Session {
            override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int) = source.enumerate(expected, maxEntries)
            override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int, cancel: ModelRepository.CancelGate?) =
                source.enumerate(expected, maxEntries, cancel)
            override fun open(ref: UriRef?) = source.open(ref)
            override fun close() { }
        }
    }
    interface Session : ModelSource, AutoCloseable {
        fun archiveBytes(): Long = 0L
        /** Pure budget/cancel check remains valid after close; never reopens IO. */
        @Throws(IOException::class) fun checkpoint() { }
        @Throws(IOException::class) override fun close()
    }

    class UriRef(private val handle: Any?) {
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun handle(): Any? = handle
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun toString(): String = java.lang.String.valueOf(handle)
    }
}
