package org.llmasr.minimal.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Diagnostic log encoding. Pure function from a frozen snapshot to a UTF-8
 *  byte stream with the same cap/format/flush/caller-close semantics the
 *  persistence worker used to own. No sinks, no providers, no identity —
 *  the caller owns open/close. This is the encoding boundary for runtime
 *  log exports; UI/log store source of truth stays where it was. */
public final class RuntimeLogCodec {
    private RuntimeLogCodec() {}

    /** Bounded UTF-8 line cap; mirrors the prior persistence-worker export cap. */
    public static final long MAX_EXPORT_BYTES = 1024L * 1024;

    /** Encode a single line for a frozen event. Mirrors the prior
     *  RuntimeLogStore.formatEvent contract; duplicated here so encoding
     *  can run without touching the live store monitor. */
    public static String formatLine(RuntimeLogEvent event) {
        return RuntimeLogStore.formatEvent(event);
    }

    /** Encode the frozen snapshot as bounded UTF-8 lines. Caller owns the
     *  output stream; this method writes and flushes but does not close.
     *  Throws IOException for any violation, including null entries, cap
     *  overflow, write/flush failures, and null/empty arguments. */
    public static long writeSnapshot(List<RuntimeLogEvent> snapshot, OutputStream out) throws IOException {
        if (snapshot == null || out == null) throw new IllegalArgumentException("export dependencies required");
        if (snapshot.size() > RuntimeLogStore.DEFAULT_CAPACITY) throw new IOException("export-cap");
        List<RuntimeLogEvent> frozen = Collections.unmodifiableList(new ArrayList<>(snapshot));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (RuntimeLogEvent event : frozen) {
            if (event == null) throw new IOException("export-invalid");
            byte[] line = (formatLine(event) + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes.size() + line.length > MAX_EXPORT_BYTES) throw new IOException("export-cap");
            bytes.write(line, 0, line.length);
        }
        bytes.writeTo(out);
        out.flush();
        return bytes.size();
    }
}
