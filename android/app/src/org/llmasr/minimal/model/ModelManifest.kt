package org.llmasr.minimal.model

import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/** Typed manifest. Validation order, fixed names and defensive copies are retained. */
class ModelManifest @Throws(IOException::class) constructor(entries: List<ModelEntry?>?) {
    private val entries: List<ModelEntry>
    init {
        if (entries == null || entries.isEmpty()) throw IOException("清单为空")
        val normalised = ArrayList<ModelEntry>(entries.size)
        val fileNames = HashSet<String>()
        val partNames = HashSet<String>()
        for (e in entries) {
            if (e == null) throw IOException("清单包含空条目")
            if (!e.file().matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]*")) || e.file() == "." || e.file() == "..")
                throw IOException("非法模型文件名：" + e.file())
            if (e.bytes() < 0 || e.bytes() > Long.MAX_VALUE - ModelRepository.SPACE_PAD)
                throw IOException("非法模型文件大小：" + e.file())
            if (!e.sha256().matches(Regex("[0-9a-f]{64}"))) throw IOException("非法SHA-256：" + e.file())
            if (partNames.contains(e.file())) throw IOException("清单文件与 .part 名冲突：" + e.file())
            if (!fileNames.add(e.file())) throw IOException("清单包含重复文件名：" + e.file())
            if (fileNames.contains(e.partName())) throw IOException("清单文件与 .part 名冲突：" + e.file())
            if (!partNames.add(e.partName())) throw IOException("清单包含 .part 名冲突：" + e.file())
            normalised.add(e)
        }
        this.entries = Collections.unmodifiableList(normalised)
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun entries(): List<ModelEntry> = entries
    @Throws(IOException::class)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun require(file: String?): ModelEntry {
        for (e in entries) if (e.file() == file) return e
        throw IOException("清单缺少条目：" + file)
    }
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        @Throws(IOException::class)
        open fun requireSafeRoot(modelDir: File?) {
            if (modelDir == null) throw IOException("modelDir required")
            if (java.nio.file.Files.isSymbolicLink(modelDir.toPath())) throw IOException("拒绝符号链接 model 目录")
            if (modelDir.exists() && !modelDir.isDirectory) throw IOException("model 路径不是目录")
            if (!modelDir.exists() && !modelDir.mkdirs()) throw IOException("无法创建 model 目录")
        }
    }
}
