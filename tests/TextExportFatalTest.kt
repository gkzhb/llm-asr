import org.llmasr.minimal.transcription.ResultState
import org.llmasr.minimal.transcription.TextExportController
import java.io.*
import java.util.concurrent.atomic.AtomicInteger

/** Compound error regression against production export worker; not Android IO. */
class TextExportFatalTest {
    companion object {
        @JvmStatic @Suppress("UNUSED_PARAMETER")
        fun main(args: Array<String>?) {
            for (primaryFatal in booleanArrayOf(false, true)) {
                val fatal: Error = InternalError("sentinel-fatal")
                val ordinary = IOException("sentinel-provider")
                val closes = AtomicInteger()
                val jobs = ArrayList<Runnable>()
                val c = TextExportController<String>({
                    object : OutputStream() {
                        override fun write(b: Int) { if (primaryFatal) throw fatal; throw ordinary }
                        override fun close() { closes.incrementAndGet(); if (primaryFatal) throw ordinary; throw fatal }
                    }
                }, { jobs.add(it) })
                val result = ResultState(); result.setText("secret")
                val ticket = c.begin(c.newPage(), result)
                c.admit(ticket, "target")
                var caught: Throwable? = null
                try { jobs.removeAt(0).run() } catch (e: Throwable) { caught = e }
                if (caught !== fatal) throw AssertionError("exact fatal must propagate even when close is suppressed under ordinary write failure")
                if (closes.get() != 1 || c.state().busy() || c.state().phase != TextExportController.Phase.FAILED)
                    throw AssertionError("fatal must attempt close exactly once and release with FAILED")
            }
            println("PASS compound write/close exact fatal propagation and cleanup")
        }
    }
}
