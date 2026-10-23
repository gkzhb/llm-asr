import org.llmasr.minimal.transcription.ResultState
import org.llmasr.minimal.transcription.TextExportController
import java.io.ByteArrayOutputStream

/** Clear must revoke the selected ticket before provider side effects. */
class TextExportSafetyTest {
    companion object {
        @JvmStatic @Suppress("UNUSED_PARAMETER")
        fun main(args: Array<String>?) {
            val result = ResultState(); result.setText("private-before-clear")
            val queued = ArrayList<Runnable>()
            val opens = intArrayOf(0)
            val exports = TextExportController<String>({
                opens[0]++; ByteArrayOutputStream()
            }, { queued.add(it) })
            val ticket = exports.begin(exports.newPage(), result)
            result.clear()
            exports.admit(ticket, "chosen-target")
            for (work in queued) work.run()
            if (opens[0] != 0) throw AssertionError("clear before write admission must cause ZERO provider opens")
            println("PASS clear-before-admission production regression")
        }
    }
}
