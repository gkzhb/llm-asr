package org.llmasr.minimal.diagnostics

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections

/** Frozen bounded UTF-8 encoding. Caller owns stream open/close. */
class RuntimeLogCodec private constructor() {
    companion object {
        const val MAX_EXPORT_BYTES: Long = 1024L * 1024
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun formatLine(event: RuntimeLogEvent?): String = RuntimeLogStore.formatEvent(event)
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun writeSnapshot(snapshot: List<RuntimeLogEvent?>?, out: OutputStream?): Long {
            if (snapshot == null || out == null) throw IllegalArgumentException("export dependencies required")
            if (snapshot.size > RuntimeLogStore.DEFAULT_CAPACITY) throw IOException("export-cap")
            val frozen = Collections.unmodifiableList(ArrayList(snapshot))
            val bytes = ByteArrayOutputStream()
            for (event in frozen) {
                if (event == null) throw IOException("export-invalid")
                val line = (formatLine(event) + "\n").toByteArray(StandardCharsets.UTF_8)
                if (bytes.size() + line.size > MAX_EXPORT_BYTES) throw IOException("export-cap")
                bytes.write(line, 0, line.size)
            }
            bytes.writeTo(out)
            out.flush()
            return bytes.size().toLong()
        }
    }
}
