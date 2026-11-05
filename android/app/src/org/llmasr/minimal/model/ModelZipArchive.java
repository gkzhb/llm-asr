package org.llmasr.minimal.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Narrow ZIP32 profile reader. No attacker paths are ever resolved as filesystem paths. */
public final class ModelZipArchive implements AutoCloseable {
    public interface Check {
        void run() throws IOException;
        default void cleanupFailed() { }
    }
    /** Narrow real-resource close seams; defaults close the exact owned JDK resource. */
    public static class Io {
        public RandomAccessFile openRandom(File archive) throws IOException { return new RandomAccessFile(archive, "r"); }
        public ZipFile openZip(File archive) throws IOException { return new ZipFile(archive); }
        public void closeRandom(RandomAccessFile resource) throws IOException { resource.close(); }
        public void closeZip(ZipFile resource) throws IOException { resource.close(); }
        public void closeEntry(InputStream resource) throws IOException { resource.close(); }
        public void endInflater(Inflater resource) { resource.end(); }
    }
    private static final String BAD = "模型 ZIP 格式不支持/校验失败";
    private final FixedModelPackage policy;
    private final Check check;
    private final Map<String, Item> items;
    private final Io io;
    private ZipFile zip;
    private boolean streamOpen;
    private long outputCount;

    private static final class Item {
        String name;
        int flags, method;
        long crc, packed, size, offset, data;
    }

    private ModelZipArchive(FixedModelPackage policy, Check check, Map<String, Item> items, Io io) {
        this.policy = policy; this.check = check; this.items = items; this.io = io;
    }

    public static ModelZipArchive prepare(File archive, FixedModelPackage policy, Check check) throws IOException {
        return prepare(archive, policy, check, new Io());
    }
    public static ModelZipArchive prepare(File archive, FixedModelPackage policy, Check check, Io io) throws IOException {
        Map<String, Item> items;
        RandomAccessFile in = io.openRandom(archive);
        Throwable primary = null;
        try {
            check.run();
            items = structure(in, policy, check);
            ModelZipArchive prepared = new ModelZipArchive(policy, check, items, io);
            prepared.firstPass(in);
        } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { closeOwned(() -> io.closeRandom(in), check, primary); }
        // All first-pass streams and Inflaters are gone before ZipFile is exposed.
        ModelZipArchive result = new ModelZipArchive(policy, check, items, io);
        try {
            result.zip = io.openZip(archive);
            check.run(); result.checkZipView();
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            try { result.close(); } catch (Throwable close) { suppress(failure, close); }
            throw failure;
        }
    }

    private static IOException bad() { return new IOException(BAD); }
    private static int u16(byte[] b, int p) { return (b[p] & 255) | ((b[p + 1] & 255) << 8); }
    private static long u32(byte[] b, int p) {
        return (long) u16(b, p) | ((long) u16(b, p + 2) << 16);
    }
    private static long size32(byte[] b, int p) throws IOException {
        long n = u32(b, p); if (n == 0xffffffffL) throw bad(); return n;
    }
    private static long add(long a, long b) throws IOException {
        if (a < 0 || b < 0 || b > Long.MAX_VALUE - a) throw bad(); return a + b;
    }
    private static byte[] read(RandomAccessFile in, long at, int n, long end) throws IOException {
        if (n < 0 || at < 0 || add(at, n) > end) throw bad();
        byte[] b = new byte[n]; in.seek(at); in.readFully(b); return b;
    }
    private static void signature(byte[] b, long expected) throws IOException {
        if (u32(b, 0) != expected) throw bad();
    }
    private static String name(byte[] bytes, FixedModelPackage policy) throws IOException {
        if (bytes.length < 1 || bytes.length > 128) throw bad();
        for (byte b : bytes) if (b < 33 || b > 126) throw bad();
        String n = new String(bytes, StandardCharsets.US_ASCII);
        if (n.equals(FixedModelPackage.JSON_NAME)) return n;
        for (ModelEntry e : policy.manifest.entries()) if (e.file().equals(n)) return n;
        throw bad();
    }
    private static void attributes(byte[] h) throws IOException {
        int host = (h[5] & 255);
        long attrs = u32(h, 38);
        if (host != 0 && host != 3) throw bad();
        if ((attrs & 0x18) != 0) throw bad(); // DOS volume/directory
        int type = (int) ((attrs >>> 16) & 0170000);
        if (host == 3 && type != 0 && type != 0100000) throw bad();
    }
    private static Map<String, Item> structure(RandomAccessFile in, FixedModelPackage policy, Check check) throws IOException {
        long length = in.length();
        if (length < 22 || length > policy.archiveLimit) throw bad();
        byte[] end = read(in, length - 22, 22, length);
        signature(end, 0x06054b50L);
        if (u16(end, 4) != 0 || u16(end, 6) != 0 || u16(end, 8) != 8 || u16(end, 10) != 8 || u16(end, 20) != 0) throw bad();
        long cenSize = size32(end, 12), cen = size32(end, 16);
        if (cenSize > 4096 || add(cen, cenSize) != length - 22) throw bad();
        Map<String, Item> result = new LinkedHashMap<>();
        long pos = cen, names = 0;
        for (int i = 0; i < 8; i++) {
            check.run();
            byte[] h = read(in, pos, 46, length - 22);
            signature(h, 0x02014b50L);
            int n = u16(h, 28);
            if (n < 1 || n > 128 || u16(h, 30) != 0 || u16(h, 32) != 0 || u16(h, 34) != 0 || u16(h, 6) > 20) throw bad();
            names += n; if (names > 1024) throw bad();
            attributes(h);
            Item item = new Item();
            item.name = name(read(in, add(pos, 46), n, length - 22), policy);
            item.flags = u16(h, 8); item.method = u16(h, 10);
            if ((item.flags != 0 && item.flags != 0x800) || (item.method != 0 && item.method != 8)) throw bad();
            item.crc = u32(h, 16); item.packed = size32(h, 20); item.size = size32(h, 24); item.offset = size32(h, 42);
            if (item.method == 0 && item.packed != item.size) throw bad();
            long allowed = item.name.equals(FixedModelPackage.JSON_NAME) ? FixedModelPackage.JSON_LIMIT : policy.manifest.require(item.name).bytes();
            if (item.size > allowed || (!item.name.equals(FixedModelPackage.JSON_NAME) && item.size != allowed)) throw bad();
            if (result.put(item.name, item) != null) throw bad();
            pos = add(pos, 46 + n);
        }
        if (pos != length - 22 || result.size() != 8) throw bad();
        List<Item> physical = new ArrayList<>(result.values());
        Collections.sort(physical, Comparator.comparingLong(x -> x.offset));
        long cursor = 0;
        for (Item item : physical) {
            check.run();
            if (item.offset != cursor) throw bad();
            byte[] h = read(in, item.offset, 30, cen);
            signature(h, 0x04034b50L);
            int n = u16(h, 26);
            if (n < 1 || n > 128 || u16(h, 28) != 0 || u16(h, 4) > 20) throw bad();
            String localName = name(read(in, add(item.offset, 30), n, cen), policy);
            if (!localName.equals(item.name) || u16(h, 6) != item.flags || u16(h, 8) != item.method ||
                u32(h, 14) != item.crc || size32(h, 18) != item.packed || size32(h, 22) != item.size) throw bad();
            item.data = add(item.offset, 30 + n);
            cursor = add(item.data, item.packed);
            if (cursor > cen) throw bad();
        }
        if (cursor != cen) throw bad();
        return Collections.unmodifiableMap(result);
    }

    private void firstPass(RandomAccessFile in) throws IOException {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        inspect(in, items.get(FixedModelPackage.JSON_NAME), json);
        policy.verifyJson(json.toByteArray());
        for (ModelEntry entry : policy.manifest.entries()) inspect(in, items.get(entry.file()), null);
    }
    private void account(int n, long count, Item item) throws IOException {
        if (n < 0 || n > item.size - count || n > policy.totalBytes + FixedModelPackage.JSON_LIMIT - outputCount) throw bad();
        outputCount += n;
    }
    private void inspect(RandomAccessFile in, Item item, ByteArrayOutputStream json) throws IOException {
        if (item == null) throw bad();
        check.run(); in.seek(item.data);
        CRC32 crc = new CRC32();
        MessageDigest sha;
        try { sha = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable"); }
        byte[] input = new byte[65536], output = new byte[65536];
        long remaining = item.packed, count = 0;
        Inflater inflater = item.method == 8 ? new Inflater(true) : null;
        Throwable primary = null;
        try {
            while (true) {
                check.run();
                int n;
                if (inflater == null) {
                    if (remaining == 0) break;
                    n = (int) Math.min(input.length, remaining);
                    in.readFully(output, 0, n); remaining -= n;
                } else {
                    if (inflater.needsInput() && remaining > 0) {
                        int take = (int) Math.min(input.length, remaining);
                        in.readFully(input, 0, take); remaining -= take;
                        inflater.setInput(input, 0, take);
                    }
                    try { n = inflater.inflate(output); }
                    catch (DataFormatException e) { throw bad(); }
                    if (n == 0) {
                        if (inflater.finished()) break;
                        if (inflater.needsDictionary() || !inflater.needsInput() || remaining == 0) throw bad();
                        continue;
                    }
                }
                check.run(); account(n, count, item); count += n;
                crc.update(output, 0, n); sha.update(output, 0, n);
                if (json != null) json.write(output, 0, n);
            }
            check.run();
            if (count != item.size || crc.getValue() != item.crc) throw bad();
            if (inflater != null && (!inflater.finished() || remaining != 0 || inflater.getBytesRead() != item.packed)) throw bad();
            if (json == null && !hex(sha.digest()).equals(policy.manifest.require(item.name).sha256())) throw bad();
        } catch (IOException | RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { if (inflater != null) closeOwned(() -> io.endInflater(inflater), check, primary); }
        check.run();
    }
    private static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte x : bytes) { b.append(Character.forDigit((x >>> 4) & 15, 16)); b.append(Character.forDigit(x & 15, 16)); }
        return b.toString();
    }
    private void checkZipView() throws IOException {
        int count = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            check.run(); ZipEntry e = entries.nextElement(); count++;
            Item i = items.get(e.getName());
            if (i == null || e.isDirectory() || e.getMethod() != i.method || e.getSize() != i.size ||
                e.getCompressedSize() != i.packed || e.getCrc() != i.crc || count > 8) throw bad();
        }
        if (count != 8) throw bad();
    }
    public InputStream open(String name) throws IOException {
        check.run();
        if (zip == null || streamOpen || name.equals(FixedModelPackage.JSON_NAME)) throw bad();
        Item i = items.get(name); if (i == null) throw bad();
        InputStream raw = zip.getInputStream(zip.getEntry(name));
        streamOpen = true;
        return new InputStream() {
            private long count;
            private final CRC32 crc = new CRC32();
            private boolean eof, closed;
            @Override public int read() throws IOException {
                byte[] one = new byte[1]; int n = read(one, 0, 1); return n < 0 ? -1 : one[0] & 255;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                if (closed) throw bad();
                if (b == null) throw new NullPointerException();
                if (off < 0 || len < 0 || off > b.length - len) throw new IndexOutOfBoundsException();
                if (len == 0) return 0;
                check.run(); int n = raw.read(b, off, Math.min(len, 65536)); check.run();
                if (n < 0) {
                    if (count != i.size || crc.getValue() != i.crc) throw bad();
                    eof = true; return -1;
                }
                if (n == 0 && len != 0) throw bad();
                account(n, count, i); count += n; crc.update(b, off, n); return n;
            }
            @Override public void close() throws IOException {
                if (closed) return;
                Throwable primary = null;
                try { closeOwned(() -> io.closeEntry(raw), check, null); } catch (IOException | RuntimeException | Error e) { primary = e; throw e; }
                finally {
                    closed = true; streamOpen = false;
                    if (primary == null) { check.run(); if (!eof) throw bad(); }
                }
            }
        };
    }
    @Override public void close() throws IOException {
        if (streamOpen) throw new IOException("模型 ZIP 流尚未关闭");
        if (zip != null) { ZipFile z = zip; zip = null; closeOwned(() -> io.closeZip(z), check, null); }
    }
    private interface Closing { void run() throws IOException; }
    private static void closeOwned(Closing closing, Check check, Throwable primary) throws IOException {
        try { closing.run(); }
        catch (IOException | RuntimeException | Error failure) {
            try { check.cleanupFailed(); } catch (Throwable notify) { suppress(failure, notify); }
            if (primary == null) throw failure;
            suppress(primary, failure);
        }
    }
    public static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) primary.addSuppressed(secondary);
    }
}
