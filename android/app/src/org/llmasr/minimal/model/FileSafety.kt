package org.llmasr.minimal.model

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Fixed model leaves: canonical containment and no symlinks or non-regular files. */
class FileSafety private constructor() {
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun requireRegularInside(root: File?, name: String?): File {
            ModelManifest.requireSafeRoot(root)
            if (name == null || name.isEmpty() || name == "." || name == "..") throw IOException("Empty file name")
            if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw IOException("拒绝穿越路径：" + name)
            val target = File(root, name)
            val rootPath = root!!.canonicalPath
            val targetPath = target.canonicalPath
            if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator))
                throw IOException("拒绝越界路径：" + name)
            if (Files.isSymbolicLink(target.toPath())) throw IOException("拒绝符号链接：" + name)
            if (target.exists() && !target.isFile) throw IOException("拒绝非普通文件：" + name)
            return target
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic @Throws(IOException::class)
        open fun deleteRegular(file: File?) {
            if (file == null) return
            if (Files.isSymbolicLink(file.toPath())) throw IOException("拒绝删除符号链接：" + file.name)
            if (file.exists() && !file.isFile) throw IOException("拒绝删除非普通文件：" + file.name)
            if (file.exists() && !file.delete()) throw IOException("无法删除：" + file.name)
        }
    }
}
