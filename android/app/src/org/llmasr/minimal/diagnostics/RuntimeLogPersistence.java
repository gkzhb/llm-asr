package org.llmasr.minimal.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Single-owner fixed-file persistence. The historical filename is retained,
 * but unreleased permissive JSON is replaced by strict ASCII/UTF-8 V1 records
 * with count and end marker. All input is revalidated as typed events.
 *
 * The supplied filesDir is a TRUSTED platform directory, not user input.
 * Its system aliases are resolved/pinned on first worker IO (not construction).
 * Managed final and .part leaves are never canonicalized to excuse a symlink.
 * NOFOLLOW/CREATE_NEW protects opens; atomic rename has no non-atomic fallback.
 * Same-UID hostile concurrent directory replacement is outside this private-dir
 * trust boundary; callers must use one persistence worker per directory. */
public final class RuntimeLogPersistence {
    public static final String FILE_NAME = "runtime-logs.json";
    public static final long MAX_FILE_BYTES = 512L * 1024;
    public enum Status { OK, MISSING, UNSAFE_PATH, CORRUPT, OVERSIZED, IO_ERROR }
    public interface FailureSink {
        void onFailure(String reason);
        final class None implements FailureSink { @Override public void onFailure(String reason) {} }
    }

    private final File filesDir;
    private Path pinnedParent;
    private volatile Status status = Status.MISSING;

    public RuntimeLogPersistence(File filesDir) {
        if (filesDir == null) throw new IllegalArgumentException("filesDir required");
        this.filesDir = filesDir;
    }
    public File file() { return new File(filesDir, FILE_NAME); }
    public Status status() { return status; }

    private static final class UnsafePath extends IOException {
        private static final long serialVersionUID = 1L;
    }
    private static final class Oversized extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private Path parent() throws IOException {
        Path resolved = filesDir.getCanonicalFile().toPath();
        if (pinnedParent != null && !resolved.equals(pinnedParent)) throw new UnsafePath();
        if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) throw new UnsafePath();
        if (pinnedParent == null) pinnedParent = resolved;
        checkLeaf(resolved.resolve(FILE_NAME));
        checkLeaf(resolved.resolve(FILE_NAME + ".part"));
        return resolved;
    }
    private static void checkLeaf(Path path) throws IOException {
        if (Files.isSymbolicLink(path)
            || (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))) throw new UnsafePath();
    }

    /** All-or-nothing bounded recovery; status is observable without recursively logging IO. */
    public synchronized List<RuntimeLogEvent> load() {
        try {
            Path path = parent().resolve(FILE_NAME);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                status = Status.MISSING;
                return new ArrayList<>();
            }
            if (Files.size(path) > MAX_FILE_BYTES) throw new Oversized();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_FILE_BYTES) throw new Oversized();
                    bytes.write(buffer, 0, count);
                }
            }
            byte[] data = bytes.toByteArray();
            // V1 contains ASCII only; reject malformed UTF-8 rather than decoding replacements.
            for (byte b : data) if (b < 0) throw new IllegalArgumentException("non-ascii");
            List<RuntimeLogEvent> result = decode(new String(data, StandardCharsets.UTF_8));
            status = Status.OK;
            return result;
        } catch (UnsafePath unsafe) { status = Status.UNSAFE_PATH;
        } catch (Oversized oversized) { status = Status.OVERSIZED;
        } catch (IOException failure) { status = Status.IO_ERROR;
        } catch (RuntimeException invalid) { status = Status.CORRUPT; }
        return new ArrayList<>();
    }

    /** Writes the latest suffix fitting BOTH count and byte caps. Returns kept
     * count, or -1. A failure callback is isolated; it must not perform disk IO. */
    public synchronized int save(List<RuntimeLogEvent> events, FailureSink sink) {
        try {
            if (events == null) throw new IllegalArgumentException("events required");
            int from = Math.max(0, events.size() - RuntimeLogStore.DEFAULT_CAPACITY);
            List<RuntimeLogEvent> tail = new ArrayList<>(events.subList(from, events.size()));
            byte[] bytes = encode(tail);
            while (bytes.length > MAX_FILE_BYTES && !tail.isEmpty()) {
                tail.remove(0);
                bytes = encode(tail);
            }
            if (bytes.length > MAX_FILE_BYTES) throw new Oversized();
            Path root = parent();
            Path target = root.resolve(FILE_NAME);
            Path part = root.resolve(FILE_NAME + ".part");
            // Only remove our fixed, regular stale staging file; never unknown files.
            Files.deleteIfExists(part);
            try (FileChannel out = FileChannel.open(part, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            parent(); // Recheck trusted root and both leaves before atomic replacement.
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            status = Status.OK;
            return tail.size();
        } catch (UnsafePath unsafe) { status = Status.UNSAFE_PATH;
        } catch (Oversized oversized) { status = Status.OVERSIZED;
        } catch (IOException failure) { status = Status.IO_ERROR;
        } catch (RuntimeException invalid) { status = Status.CORRUPT; }
        if (sink != null) {
            try { sink.onFailure(reason(status)); }
            catch (RuntimeException ignored) { /* never propagate logging faults */ }
        }
        // A regular failed .part is ignored on load and replaced on the next save.
        return -1;
    }

    static String reason(Status value) {
        switch (value) {
            case UNSAFE_PATH: return "unsafe-path";
            case CORRUPT: return "corrupt";
            case OVERSIZED: return "oversized";
            default: return "io";
        }
    }
    private static String nullable(String value) { return value == null ? "-" : value; }
    private static String unnull(String value) { return value.equals("-") ? null : value; }
    private static byte[] encode(List<RuntimeLogEvent> events) {
        StringBuilder text = new StringBuilder("RUNTIME_LOG_V1\t").append(events.size()).append('\n');
        long previous = -1;
        for (RuntimeLogEvent e : events) {
            if (e == null || e.sequence <= previous) throw new IllegalArgumentException("invalid sequence");
            previous = e.sequence;
            text.append(e.sequence).append('\t').append(e.wallMillis).append('\t')
                .append(e.monotonicNanos).append('\t').append(e.elapsedNanos).append('\t')
                .append(e.kind.name()).append('\t').append(e.source.name()).append('\t')
                .append(nullable(e.requestId)).append('\t').append(nullable(e.detail)).append('\n');
        }
        return text.append("END\n").toString().getBytes(StandardCharsets.UTF_8);
    }
    private static long number(String text) {
        long result = Long.parseLong(text);
        if (!Long.toString(result).equals(text)) throw new IllegalArgumentException("noncanonical number");
        return result;
    }
    private static List<RuntimeLogEvent> decode(String text) {
        String[] lines = text.split("\n", -1);
        if (lines.length < 3 || !lines[0].startsWith("RUNTIME_LOG_V1\t"))
            throw new IllegalArgumentException("header");
        long count = number(lines[0].substring("RUNTIME_LOG_V1\t".length()));
        if (count < 0 || count > RuntimeLogStore.DEFAULT_CAPACITY || lines.length != count + 3
            || !lines[lines.length - 2].equals("END") || !lines[lines.length - 1].isEmpty())
            throw new IllegalArgumentException("incomplete file");
        List<RuntimeLogEvent> result = new ArrayList<>();
        long previous = -1;
        for (int i = 1; i <= count; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length != 8 || f[6].isEmpty() || f[7].isEmpty()) throw new IllegalArgumentException("fields");
            RuntimeLogEvent e = new RuntimeLogEvent(number(f[0]), number(f[1]), number(f[2]),
                RuntimeLogEventKind.valueOf(f[4]), RuntimeLogSource.valueOf(f[5]),
                unnull(f[6]), unnull(f[7]), number(f[3]));
            if (e.sequence <= previous) throw new IllegalArgumentException("sequence");
            previous = e.sequence;
            result.add(e);
        }
        return result;
    }
}
