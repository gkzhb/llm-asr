package org.llmasr.minimal.ime;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Process-singleton, private text store. Bounded async commands; no transcript diagnostics or main-thread IO. */
public final class ImeHistoryStore {
    public static final int LIMIT = 50;
    public static final int MAX_TEXT_BYTES = 32768;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x494d4548;
    public interface Callback { void complete(Snapshot snapshot); }
    public static final class Snapshot {
        public final List<ImeHistory> records;
        public final boolean available;
        Snapshot(List<ImeHistory> records, boolean available) {
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
            this.available = available;
        }
    }
    private final Supplier<File> directory;
    private final ThreadPoolExecutor io;
    private final List<ImeHistory> records = new ArrayList<>();
    private boolean loaded;
    private boolean available = true;
    private Path file;

    public ImeHistoryStore(Supplier<File> directory) {
        this.directory = Objects.requireNonNull(directory);
        io = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, "ime-history-io"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    /** False means bounded admission rejected; caller never retries or blocks UI. */
    public boolean append(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) return false;
        ImeHistory entry = new ImeHistory(UUID.randomUUID().toString(), System.currentTimeMillis(), text);
        return submit(() -> {
            load(); if (!available) return;
            records.add(0, entry);
            while (records.size() > LIMIT) records.remove(records.size() - 1);
            save();
        });
    }
    public boolean snapshot(Callback callback) {
        Objects.requireNonNull(callback);
        return submit(() -> { load(); complete(callback); });
    }
    public boolean delete(String id, Callback callback) {
        return mutate(() -> records.removeIf(r -> r.id.equals(id)), callback);
    }
    public boolean clear(Callback callback) { return mutate(records::clear, callback); }
    private boolean mutate(Runnable action, Callback callback) {
        Objects.requireNonNull(callback);
        return submit(() -> {
            load();
            if (available) { action.run(); save(); }
            complete(callback);
        });
    }
    private void complete(Callback callback) {
        try { callback.complete(new Snapshot(records, available)); }
        catch (RuntimeException ignored) { /* Presentation cannot terminate or poison the IO worker. */ }
    }
    private boolean submit(Runnable action) {
        try { io.execute(action); return true; }
        catch (RejectedExecutionException rejected) { return false; }
    }
    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            File root = directory.get();
            if (root == null) throw new IOException();
            file = root.toPath().resolve("ime-history-v1.bin");
            recoverTemporaryFiles(root.toPath());
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES) throw new IOException();
            List<ImeHistory> decoded = new ArrayList<>();
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                if (in.readInt() != MAGIC || in.readInt() != ImeHistory.SCHEMA_VERSION) throw new IOException();
                int count = in.readInt();
                if (count < 0 || count > LIMIT) throw new IOException();
                Set<String> ids = new HashSet<>();
                for (int n = 0; n < count; n++) {
                    if (in.readInt() != ImeHistory.SCHEMA_VERSION) throw new IOException();
                    String id = readText(in, 64);
                    long createdAt = in.readLong();
                    String text = readText(in, MAX_TEXT_BYTES);
                    if (!UUID.fromString(id).toString().equals(id) || !ids.add(id) || createdAt < 0 || text.isEmpty()) throw new IOException();
                    decoded.add(new ImeHistory(id, createdAt, text));
                }
                if (in.read() != -1) throw new IOException();
            }
            records.addAll(decoded);
        } catch (IOException | RuntimeException failure) { records.clear(); available = false; }
    }
    /** Sole IO owner; inspect a bounded flat private directory before deleting any known orphan.
     * Never follow links, recurse, promote temp content, or remove the authoritative binary. */
    private void recoverTemporaryFiles(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException();
        Map<Path, BasicFileAttributes> orphans = new LinkedHashMap<>();
        int entries = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (++entries > 128) throw new IOException();
                String name = entry.getFileName().toString();
                if (name.equals("ime-history-v1.bin")) continue;
                if (!name.matches("ime-history-[0-9]{1,20}\\.tmp")) throw new IOException();
                BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                Object links = Files.getAttribute(entry, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
                if (!attrs.isRegularFile() || attrs.fileKey() == null || !(links instanceof Number) ||
                    ((Number) links).longValue() != 1 || attrs.size() > MAX_FILE_BYTES || orphans.size() == 64) throw new IOException();
                orphans.put(entry, attrs);
            }
        }
        for (Map.Entry<Path, BasicFileAttributes> orphan : orphans.entrySet()) {
            BasicFileAttributes now = Files.readAttributes(orphan.getKey(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes before = orphan.getValue();
            if (!now.isRegularFile() || !Objects.equals(before.fileKey(), now.fileKey()) ||
                !Long.valueOf(1).equals(((Number) Files.getAttribute(orphan.getKey(), "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue()) ||
                before.size() != now.size() || !before.lastModifiedTime().equals(now.lastModifiedTime())) throw new IOException();
            Files.delete(orphan.getKey());
        }
    }
    private static String readText(DataInputStream in, int limit) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > limit) throw new IOException();
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static void writeText(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private void save() {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "ime-history-", ".tmp");
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(stream))) {
                out.writeInt(MAGIC); out.writeInt(ImeHistory.SCHEMA_VERSION); out.writeInt(records.size());
                for (ImeHistory r : records) {
                    out.writeInt(r.schemaVersion); writeText(out, r.id); out.writeLong(r.createdAt); writeText(out, r.originalText);
                }
                out.flush(); stream.getFD().sync();
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            // Unknown prior bytes remain untouched. Stop future mutation rather than blindly overwrite.
            available = false; records.clear();
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Private orphan contains text only. */ }
        }
    }
}
