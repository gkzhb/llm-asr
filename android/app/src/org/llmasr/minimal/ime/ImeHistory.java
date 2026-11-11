package org.llmasr.minimal.ime;

/** Text-only, immutable history schema. Corrections are a future seam, never applied by this release. */
public final class ImeHistory {
    public static final int SCHEMA_VERSION = 1;
    public final int schemaVersion;
    public final String id;
    public final long createdAt;
    public final String originalText;
    public final String correctedText;

    public ImeHistory(String id, long createdAt, String originalText) {
        this.schemaVersion = SCHEMA_VERSION;
        this.id = id;
        this.createdAt = createdAt;
        this.originalText = originalText;
        this.correctedText = null;
    }
}
