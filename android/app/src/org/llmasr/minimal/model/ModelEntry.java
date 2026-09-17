package org.llmasr.minimal.model;

import java.util.Objects;

/** Immutable description of a single manifest entry. The fixed manifest owned by
 * ModelRepository is an ordered list of these. Equality is by content so a model
 * repository can compare entries for part-name recovery without aliasing.
 */
public final class ModelEntry {
    private final String file;
    private final long bytes;
    private final String sha256;

    public ModelEntry(String file, long bytes, String sha256) {
        this.file = Objects.requireNonNull(file, "file");
        this.bytes = bytes;
        this.sha256 = Objects.requireNonNull(sha256, "sha256").toLowerCase(java.util.Locale.ROOT);
    }

    public String file() { return file; }
    public long bytes() { return bytes; }
    public String sha256() { return sha256; }

    public String partName() { return file + ".part"; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelEntry)) return false;
        ModelEntry other = (ModelEntry) o;
        return bytes == other.bytes && file.equals(other.file) && sha256.equals(other.sha256);
    }

    @Override public int hashCode() { return file.hashCode() * 31 + Long.hashCode(bytes); }
}
