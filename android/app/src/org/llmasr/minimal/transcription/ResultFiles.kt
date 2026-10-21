package org.llmasr.minimal.transcription

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

/** App-private non-recursive cleanup; never follows symlinks or deletes models. */
class ResultFiles {
    companion object {
        private const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val TEMP = Pattern.compile("(?:input-" + UUID + "\\.wav|last-result-" + UUID + "\\.part)")
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun startupStatus(count: Int): String = "准备就绪，已清理" + count + "个遗留临时文件；请校验已部署模型或导入模型。"
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun cleanTemporary(directory: File?): Int = clean(directory, false)
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun clearResults(directory: File?): Int = clean(directory, true)
        private fun clean(directory: File?, results: Boolean): Int {
            val files = directory!!.listFiles() ?: throw IOException("无法读取应用私有目录")
            var count = 0
            for (file in files) {
                val name = file.name
                if (!TEMP.matcher(name).matches() && !(results && (name == "last-result.json" || name == "edited-result.txt"))) continue
                if (Files.isSymbolicLink(file.toPath()) || !file.isFile) throw IOException("拒绝清理非普通结果文件：" + name)
                if (!file.delete()) throw IOException("无法清理：" + name)
                count++
            }
            return count
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun writeText(out: OutputStream?, text: String?) {
            if (text!!.length > 100000) throw IOException("结果文本超过100000字符上限")
            out!!.write(text.toByteArray(StandardCharsets.UTF_8))
        }
    }
}
