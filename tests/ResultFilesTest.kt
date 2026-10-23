import org.llmasr.minimal.transcription.ResultFiles
import org.llmasr.minimal.transcription.ResultState
import org.llmasr.minimal.transcription.TextExportController
import java.io.*
import java.nio.file.*
import java.nio.charset.StandardCharsets

open class ResultFilesTest {
    companion object {
        private var checks = 0
        private fun check(c: Boolean) { if (!c) throw AssertionError("check $checks"); checks++ }
        // Java TWR invokes Throwable's member even for self suppression.
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private fun closeJava(resource: AutoCloseable?, primary: Throwable?) {
            if (resource == null) return
            if (primary == null) resource.close()
            else try { resource.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) }
        }
        @JvmStatic @Throws(Exception::class) @Suppress("UNUSED_PARAMETER")
        fun main(args: Array<String>?) {
            val dir = Files.createTempDirectory("asr-results-")
            val id = "12345678-1234-1234-1234-123456789abc"
            try {
                val temp = dir.resolve("input-$id.wav"); val result = dir.resolve("last-result.json")
                val model = dir.resolve("llm.mnn.weight"); val personal = dir.resolve("input-personal.wav")
                for (p in arrayOf(temp, result, model, personal)) Files.write(p, byteArrayOf(1))
                check(ResultFiles.cleanTemporary(dir.toFile()) == 1); check(!Files.exists(temp)); check(Files.exists(result))
                check(Files.exists(model) && Files.exists(personal))
                check(ResultFiles.clearResults(dir.toFile()) == 1); check(!Files.exists(result))
                check(Files.exists(model) && Files.exists(personal)); check(ResultFiles.clearResults(dir.toFile()) == 0)
                Files.createSymbolicLink(temp, personal); var rejected = false
                try { ResultFiles.cleanTemporary(dir.toFile()) } catch (e: IOException) { rejected = true }
                check(rejected); check(Files.exists(personal)); Files.delete(temp)
                Files.createDirectory(temp); rejected = false
                try { ResultFiles.cleanTemporary(dir.toFile()) } catch (e: IOException) { rejected = true }
                check(rejected); Files.delete(temp)
                val out = ByteArrayOutputStream(); ResultFiles.writeText(out, "中文😀\nsecond line")
                check(String(out.toByteArray(), StandardCharsets.UTF_8) == "中文😀\nsecond line")
                rejected = false; try { ResultFiles.writeText(out, String(CharArray(100001))) } catch (e: IOException) { rejected = true }; check(rejected)
                check(ResultFiles.cleanTemporary(dir.toFile()) == 0)
                val nested = Files.createDirectory(dir.resolve("model"))
                Files.write(nested.resolve("llm.mnn.weight.part"), byteArrayOf(2))
                Files.write(dir.resolve("last-result-$id.part"), byteArrayOf(1)); Files.write(temp, byteArrayOf(1))
                Files.write(result, byteArrayOf(1)); Files.write(dir.resolve("edited-result.txt"), byteArrayOf(1))
                val count = ResultFiles.cleanTemporary(dir.toFile()); check(count == 2)
                check(ResultFiles.startupStatus(count).contains("2个")); check(ResultFiles.startupStatus(0).contains("准备就绪"))
                check(!ResultFiles.startupStatus(0).contains("执行中"))
                check(Files.exists(result) && Files.exists(dir.resolve("edited-result.txt")))
                check(ResultFiles.clearResults(dir.toFile()) == 2); check(Files.exists(nested.resolve("llm.mnn.weight.part")))
                Files.delete(nested.resolve("llm.mnn.weight.part")); Files.delete(nested)
                var writeFailed = false
                try { ResultFiles.writeText(object : OutputStream() { override fun write(b: Int) { throw IOException("injected") } }, "secret") }
                catch (expected: IOException) { writeFailed = true }; check(writeFailed)
                var closeFailed = false
                try {
                    val failing: OutputStream = object : ByteArrayOutputStream() { override fun close() { throw IOException("close") } }
                    var primary: Throwable? = null
                    try { ResultFiles.writeText(failing, "text") } catch (failure: Throwable) { primary = failure; throw failure }
                    finally { closeJava(failing, primary) }
                } catch (expected: IOException) { closeFailed = true }; check(closeFailed)
                val rs = ResultState(); rs.setText("before clear")
                val session = TextExportController<String>({ ByteArrayOutputStream() }, { it.run() })
                val first = session.begin(session.newPage(), rs)
                check(first != null && first.snapshot.text == "before clear")
                check(session.begin(session.newPage(), rs) == null)
                rs.clear()
                check(!session.admit(first, "uri-old"))
                check(session.state().phase == TextExportController.Phase.EXPIRED)
                check(session.begin(session.newPage(), rs) == null)
                check(rs.applyEdit(first!!.snapshot, "stale") == -1L)
                val live = rs.begin(); check(rs.applyEdit(live, "after clear") > 0)
                val second = session.begin(session.newPage(), rs)
                check(second != null && second.requestCode != first.requestCode)
                check(session.admit(second, "uri-new"))
                check(session.state().phase == TextExportController.Phase.SUCCEEDED)
                check(TextExportController.isRequest(5000) && !TextExportController.isRequest(13))
                println("PASS $checks result cleanup/export checks (host only)")
            } finally {
                val files = Files.list(dir); var primary: Throwable? = null
                try { for (p in Iterable { files.iterator() }) Files.delete(p) }
                catch (failure: Throwable) { primary = failure; throw failure }
                finally { closeJava(files, primary) }
                Files.delete(dir)
            }
        }
    }
}
