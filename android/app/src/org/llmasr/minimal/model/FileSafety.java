package org.llmasr.minimal.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Tiny helper that enforces the model directory's safety rules: regular files only,
 * no symbolic links, and the resolved path must stay inside the supplied root.
 * Any violation fails closed. ResultFiles already enforces the same rules for the
 * app-private results directory; this helper is the matching boundary for the
 * model directory and is reused by the model repository.
 */
public final class FileSafety {
    private FileSafety() {}

    public static File requireRegularInside(File root, String name) throws IOException {
        ModelManifest.requireSafeRoot(root);
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")) throw new IOException("Empty file name");
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IOException("拒绝穿越路径：" + name);
        File target = new File(root, name);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        // Canonical path is the only path that resolves symlinks; the equality check rejects ../ escapes.
        if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
            throw new IOException("拒绝越界路径：" + name);
        }
        if (Files.isSymbolicLink(target.toPath())) throw new IOException("拒绝符号链接：" + name);
        if (target.exists() && !target.isFile()) throw new IOException("拒绝非普通文件：" + name);
        return target;
    }

    public static void deleteRegular(File file) throws IOException {
        if (file == null) return;
        if (Files.isSymbolicLink(file.toPath())) throw new IOException("拒绝删除符号链接：" + file.getName());
        if (file.exists() && !file.isFile()) throw new IOException("拒绝删除非普通文件：" + file.getName());
        if (file.exists() && !file.delete()) throw new IOException("无法删除：" + file.getName());
    }
}
