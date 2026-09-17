package org.llmasr.minimal.ime;

/** Transient editor state. Mutations are serialized by ImeController; volatile
 * snapshots are read by the main-thread view. No text survives invalidation. */
public final class ImeSession {
    final long token;
    public final String fieldKey;
    volatile boolean valid = true;
    volatile String preview = "";
    volatile String status = "点击录音，最长30秒；转写后确认输入。";
    long revision;
    boolean consumed;

    ImeSession(long token, String fieldKey) { this.token = token; this.fieldKey = fieldKey; }
    public String preview() { return preview; }
    public String status() { return status; }
    public boolean valid() { return valid; }
}
