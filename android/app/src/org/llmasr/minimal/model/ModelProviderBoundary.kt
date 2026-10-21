package org.llmasr.minimal.model

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Only provider calls are sanitized here; the caller's cancellation gate stays outside. */
class ModelProviderBoundary private constructor() {
    fun interface Call<T> { @Throws(IOException::class) fun run(): T }
    companion object {
        const val DIRECTORY_FAILURE: String = "无法读取所选目录，请重新授权或改用本地目录"
        const val FILE_FAILURE: String = "无法读取所选模型文件，请重新选择并授权或改用本地目录"
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun <T> call(safeMessage: String?, provider: Call<T>?): T {
            try { return provider!!.run() }
            catch (e: IOException) { throw IOException(safeMessage) }
            catch (e: RuntimeException) { throw IOException(safeMessage) }
            catch (e: LinkageError) { throw IOException(safeMessage) }
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun checkpoint(gate: ModelRepository.CancelGate?) {
            if (gate!!.cancelled()) throw ModelRepository.CancelledException()
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun open(provider: Call<InputStream?>?): InputStream {
            val stream = call(FILE_FAILURE, provider) ?: throw IOException(FILE_FAILURE)
            return object : FilterInputStream(stream) {
                @Throws(IOException::class)
                override fun read(): Int = call(FILE_FAILURE) { `in`.read() }
                @Throws(IOException::class)
                override fun read(b: ByteArray?, off: Int, len: Int): Int = call(FILE_FAILURE) { `in`.read(b, off, len) }
                @Throws(IOException::class)
                override fun close() { call<Any?>(FILE_FAILURE) { `in`.close(); null } }
            }
        }
    }
}
