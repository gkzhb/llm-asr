package org.llmasr.minimal.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure Java typed model manifest. Construction validates the entry list:
 *  - non-empty (a bad asset cannot be coerced into a silent success),
 *  - no duplicate file names,
 *  - no name collides with another entry's .part,
 *  - no name resolves outside the model root or is a symlink.
 *
 * The class has no JSON or Android dependency. JSON parsing is the Android
 * adapter's responsibility (see AppGraph.parseManifest).
 */
public final class ModelManifest {
    private final List<ModelEntry> entries;

    public ModelManifest(List<ModelEntry> entries) throws IOException {
        if (entries == null || entries.isEmpty()) throw new IOException("清单为空");
        List<ModelEntry> normalised = new ArrayList<>(entries.size());
        Set<String> fileNames = new HashSet<>();
        Set<String> partNames = new HashSet<>();
        for (ModelEntry e : entries) {
            if (e == null) throw new IOException("清单包含空条目");
            if (!e.file().matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*") || e.file().equals(".") || e.file().equals(".."))
                throw new IOException("非法模型文件名：" + e.file());
            if (e.bytes() < 0 || e.bytes() > Long.MAX_VALUE - ModelRepository.SPACE_PAD)
                throw new IOException("非法模型文件大小：" + e.file());
            if (!e.sha256().matches("[0-9a-f]{64}")) throw new IOException("非法SHA-256：" + e.file());
            // Earlier .part matches this file name → collision.
            if (partNames.contains(e.file())) throw new IOException("清单文件与 .part 名冲突：" + e.file());
            if (!fileNames.add(e.file())) throw new IOException("清单包含重复文件名：" + e.file());
            // This entry's .part collides with a previously seen file name.
            if (fileNames.contains(e.partName())) throw new IOException("清单文件与 .part 名冲突：" + e.file());
            if (!partNames.add(e.partName())) throw new IOException("清单包含 .part 名冲突：" + e.file());
            normalised.add(e);
        }
        this.entries = Collections.unmodifiableList(normalised);
    }

    public List<ModelEntry> entries() { return entries; }
    public ModelEntry require(String file) throws IOException {
        for (ModelEntry e : entries) if (e.file().equals(file)) return e;
        throw new IOException("清单缺少条目：" + file);
    }

    /** Assert that the model directory is a usable root: exists, is a
     * directory, and is not a symlink. The repository is the only consumer.
     */
    public static void requireSafeRoot(File modelDir) throws IOException {
        if (modelDir == null) throw new IOException("modelDir required");
        if (java.nio.file.Files.isSymbolicLink(modelDir.toPath())) throw new IOException("拒绝符号链接 model 目录");
        if (modelDir.exists() && !modelDir.isDirectory()) throw new IOException("model 路径不是目录");
        if (!modelDir.exists() && !modelDir.mkdirs()) throw new IOException("无法创建 model 目录");
    }
}
