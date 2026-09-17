import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.model.FileSafety;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct host tests of the production ModelRepository. Uses a fake
 * ModelSource so read/close/rename failures can be injected without an
 * Android ContentResolver. Covers bad-hash, short / over-long input, close
 * failure, rename failure, stale .part residue, low-space recovery,
 * safety boundaries, and existing-validated-file reuse. There are no
 * production test backdoors; all checks reach the public API.
 */
public final class ModelRepositoryTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }

    static String sha256(byte[] data) throws Exception {
        MessageDigest h = MessageDigest.getInstance("SHA-256");
        h.update(data);
        StringBuilder s = new StringBuilder();
        for (byte b : h.digest()) s.append(String.format("%02x", b & 255));
        return s.toString();
    }
    static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (i & 0xff);
        return b;
    }
    static File freshDir(String prefix) throws IOException { return Files.createTempDirectory(prefix).toFile(); }

    static class FakeSource implements ModelSource {
        final Map<String, byte[]> store = new HashMap<>();
        final Set<String> failOpen = new HashSet<>();
        final Set<String> failClose = new HashSet<>();
        final Set<String> shortRead = new HashSet<>();
        final Set<String> overLong = new HashSet<>();
        @Override public Map<String, UriRef> enumerate(Collection<String> expected, int max) {
            Map<String, UriRef> out = new LinkedHashMap<>();
            for (String n : expected) if (store.containsKey(n)) out.put(n, new UriRef(n));
            return out;
        }
        @Override public InputStream open(UriRef ref) throws IOException {
            String name = (String) ref.handle();
            if (failOpen.contains(name)) throw new IOException("open failed: " + name);
            byte[] payload = store.get(name);
            if (payload == null) throw new IOException("no payload: " + name);
            final byte[] toDeliver;
            if (overLong.contains(name)) {
                toDeliver = new byte[payload.length + 1024];
                System.arraycopy(payload, 0, toDeliver, 0, payload.length);
            } else if (shortRead.contains(name)) {
                toDeliver = payload.length > 4 ? Arrays.copyOf(payload, payload.length / 2) : payload;
            } else {
                toDeliver = payload;
            }
            return new ByteArrayInputStream(toDeliver) {
                @Override public void close() throws IOException {
                    if (failClose.contains(name)) throw new IOException("close failed: " + name);
                    super.close();
                }
            };
        }
    }

    static final class FakeSpace implements ModelRepository.SpaceProvider {
        final AtomicLong free;
        FakeSpace(long free) { this.free = new AtomicLong(free); }
        @Override public long usableBytes() { return free.get(); }
        void set(long v) { free.set(v); }
    }

    public static void main(String[] args) throws Exception {
        // R1: happy path.
        byte[] a = bytes(4096), b = bytes(8192);
        File dir1 = freshDir("model-r1-");
        ModelManifest m1 = new ModelManifest(Arrays.asList(entry("a.bin", a), entry("b.bin", b)));
        FakeSource s1 = new FakeSource(); s1.store.put("a.bin", a); s1.store.put("b.bin", b);
        ModelRepository r1 = new ModelRepository(m1, dir1, new FakeSpace(256L * 1024 * 1024));
        r1.importFrom(s1, null);
        check(new File(dir1, "a.bin").isFile());
        check(new File(dir1, "b.bin").isFile());
        check(!new File(dir1, "a.bin.part").exists());
        r1.verifyAll();

        // R2: bad-hash source is rejected.
        byte[] tampered = bytes(1024); tampered[0] ^= 1;
        File dir2 = freshDir("model-r2-");
        ModelManifest m2 = new ModelManifest(Arrays.asList(entry("c.bin", bytes(1024))));
        FakeSource s2 = new FakeSource(); s2.store.put("c.bin", tampered);
        ModelRepository r2 = new ModelRepository(m2, dir2, new FakeSpace(256L * 1024 * 1024));
        boolean rejected = false;
        try { r2.importFrom(s2, null); }
        catch (IOException expected) { rejected = expected.getMessage().contains("SHA-256"); }
        check(rejected);
        check(!new File(dir2, "c.bin").exists());
        check(!new File(dir2, "c.bin.part").exists());

        // R3: short read rejected.
        byte[] full = bytes(4096);
        File dir3 = freshDir("model-r3-");
        ModelManifest m3 = new ModelManifest(Arrays.asList(entry("d.bin", full)));
        FakeSource s3 = new FakeSource(); s3.store.put("d.bin", full); s3.shortRead.add("d.bin");
        ModelRepository r3 = new ModelRepository(m3, dir3, new FakeSpace(256L * 1024 * 1024));
        boolean shortRejected = false;
        try { r3.importFrom(s3, null); }
        catch (IOException expected) { shortRejected = true; }
        check(shortRejected);
        check(!new File(dir3, "d.bin").exists());
        check(!new File(dir3, "d.bin.part").exists());

        // R4: over-long input rejected.
        File dir4 = freshDir("model-r4-");
        ModelManifest m4 = new ModelManifest(Arrays.asList(entry("e.bin", bytes(1024))));
        FakeSource s4 = new FakeSource(); s4.store.put("e.bin", bytes(1024)); s4.overLong.add("e.bin");
        ModelRepository r4 = new ModelRepository(m4, dir4, new FakeSpace(256L * 1024 * 1024));
        boolean overRejected = false;
        try { r4.importFrom(s4, null); }
        catch (IOException expected) { overRejected = true; }
        check(overRejected);
        check(!new File(dir4, "e.bin").exists());
        check(!new File(dir4, "e.bin.part").exists());

        // R5: close failure on the source is reported.
        File dir5 = freshDir("model-r5-");
        ModelManifest m5 = new ModelManifest(Arrays.asList(entry("f.bin", bytes(1024))));
        FakeSource s5 = new FakeSource(); s5.store.put("f.bin", bytes(1024)); s5.failClose.add("f.bin");
        ModelRepository r5 = new ModelRepository(m5, dir5, new FakeSpace(256L * 1024 * 1024));
        boolean closeRejected = false;
        try { r5.importFrom(s5, null); }
        catch (IOException expected) { closeRejected = true; }
        check(closeRejected);
        check(!new File(dir5, "f.bin").exists());
        check(!new File(dir5, "f.bin.part").exists());

        // R6: rename failure surfaces; .part is reclaimed.
        //     The lambda now matches the typed Progress signature
        //     (Stage + file/byte counters) introduced in 0.5; the
        //     semantic is the same — inject the target as a directory
        //     at the moment copy has just finished.
        File dir6 = freshDir("model-r6-");
        File dest = new File(dir6, "g.bin");
        try {
            ModelManifest m6 = new ModelManifest(Arrays.asList(entry("g.bin", bytes(1024))));
            FakeSource s6 = new FakeSource(); s6.store.put("g.bin", bytes(1024));
            ModelRepository r6 = new ModelRepository(m6, dir6, new FakeSpace(256L * 1024 * 1024));
            boolean renameRejected = false;
            AtomicBoolean reachedCopy = new AtomicBoolean();
            try { r6.importFrom(s6, (stage, file, fileIndex, fileCount, fileDone, fileTotal, stageDone, stageTotal, reused) -> {
                // Inject publication failure only after copy has actually run.
                if (stage == ModelRepository.Stage.COPYING && fileDone == fileTotal && fileTotal > 0) {
                    reachedCopy.set(true);
                    if (!dest.mkdir()) throw new AssertionError("failed to inject target directory");
                }
            }); }
            catch (IOException expected) { renameRejected = expected.getMessage().contains("原子替换"); }
            check(reachedCopy.get());
            check(renameRejected);
            check(!new File(dir6, "g.bin.part").exists());
        } finally { dest.delete(); }

        // R7: existing validated file is reused.
        byte[] h = bytes(2048);
        File dir7 = freshDir("model-r7-");
        Files.write(new File(dir7, "h.bin").toPath(), h);
        ModelManifest m7 = new ModelManifest(Arrays.asList(entry("h.bin", h)));
        FakeSource s7 = new FakeSource(); s7.store.put("h.bin", h);
        ModelRepository r7 = new ModelRepository(m7, dir7, new FakeSpace(256L * 1024 * 1024));
        long before = new File(dir7, "h.bin").lastModified();
        s7.failOpen.add("h.bin"); // reuse must not open the provider, irrespective of clock resolution
        r7.importFrom(s7, null);
        long after = new File(dir7, "h.bin").lastModified();
        check(before == after);
        check(s7.store.containsKey("h.bin"));

        // R8: symlink at the .part name is rejected (outside file untouched).
        File dir8 = freshDir("model-r8-");
        File outside = File.createTempFile("model-r8-out-", ".bin");
        Files.write(outside.toPath(), bytes(64));
        ModelManifest m8 = new ModelManifest(Arrays.asList(entry("i.bin", bytes(64))));
        Files.createSymbolicLink(new File(dir8, "i.bin.part").toPath(), outside.toPath());
        FakeSource s8 = new FakeSource(); s8.store.put("i.bin", bytes(64));
        ModelRepository r8 = new ModelRepository(m8, dir8, new FakeSpace(256L * 1024 * 1024));
        boolean symlinkRejected = false;
        try { r8.importFrom(s8, null); }
        catch (IOException expected) { symlinkRejected = true; }
        check(symlinkRejected);
        check(Files.exists(outside.toPath()));

        // R9: directory at the .bin name is rejected by verify.
        File dir10 = freshDir("model-r10-");
        new File(dir10, "k.bin").mkdirs();
        ModelManifest m10 = new ModelManifest(Arrays.asList(entry("k.bin", bytes(64))));
        ModelRepository r10 = new ModelRepository(m10, dir10, new FakeSpace(256L * 1024 * 1024));
        boolean dirRejected = false;
        try { r10.verifyEntry(m10.require("k.bin")); }
        catch (IOException expected) { dirRejected = true; }
        check(dirRejected);
        new File(dir10, "k.bin").delete();

        // R10: deletion of a non-regular file is rejected.
        File dir12 = freshDir("model-r12-");
        new File(dir12, "leftover.bin").mkdirs();
        boolean deleteRejected = false;
        try { FileSafety.deleteRegular(new File(dir12, "leftover.bin")); }
        catch (IOException expected) { deleteRejected = true; }
        check(deleteRejected);
        new File(dir12, "leftover.bin").delete();

        // R11: part reclaim removes the file.
        File dir13 = freshDir("model-r13-");
        byte[] data = bytes(1024);
        ModelEntry e13 = entry("m.bin", data);
        Files.write(new File(dir13, e13.partName()).toPath(), bytes(512));
        ModelRepository r13 = new ModelRepository(new ModelManifest(Arrays.asList(e13)), dir13, new FakeSpace(0L));
        check(new File(dir13, e13.partName()).exists());
        r13.reclaimOrphanedParts();
        check(!new File(dir13, e13.partName()).exists());

        // R12: too-low space is rejected (no false positive from a fresh part).
        File dir14 = freshDir("model-r14-");
        byte[] n = bytes(2048);
        ModelEntry e14 = entry("n.bin", n);
        ModelRepository r14 = new ModelRepository(new ModelManifest(Arrays.asList(e14)), dir14, new FakeSpace(0L));
        boolean noSpace = false;
        try {
            FakeSource s14 = new FakeSource();
            s14.store.put("n.bin", n);
            r14.importFrom(s14, null);
        } catch (IOException expected) { noSpace = true; }
        check(noSpace);

        // R13: file-size mismatch is detected.
        File dir15 = freshDir("model-r15-");
        byte[] o = bytes(512);
        Files.write(new File(dir15, "o.bin").toPath(), bytes(256));
        ModelRepository r15 = new ModelRepository(new ModelManifest(Arrays.asList(entry("o.bin", o))), dir15, new FakeSpace(256L * 1024 * 1024));
        boolean sizeMismatch = false;
        try { r15.verifyEntry(r15.manifest().require("o.bin")); }
        catch (IOException expected) { sizeMismatch = true; }
        check(sizeMismatch);

        // R15: repository propagates source enumeration failure (NOT SAF cap execution).
        File dir17 = freshDir("model-r17-");
        ModelManifest m17 = new ModelManifest(Arrays.asList(entry("q.bin", bytes(8))));
        ModelSource overCap = new ModelSource() {
            @Override public Map<String, UriRef> enumerate(Collection<String> expected, int max) throws IOException {
                throw new IOException("目录超过" + max + "个条目");
            }
            @Override public InputStream open(UriRef ref) { return new ByteArrayInputStream(new byte[0]); }
        };
        ModelRepository r17 = new ModelRepository(m17, dir17, new FakeSpace(256L * 1024 * 1024));
        boolean capRejected = false;
        try { r17.importFrom(overCap, null); }
        catch (IOException expected) { capRejected = expected.getMessage().contains("目录超过10000"); }
        check(capRejected);

        // R16: empty manifest construction fails closed.
        boolean emptyRejected = false;
        try { new ModelManifest(Collections.<ModelEntry>emptyList()); }
        catch (IOException expected) { emptyRejected = true; }
        check(emptyRejected);

        // R17: duplicate file names in the manifest fail closed.
        boolean dupNameRejected = false;
        try { new ModelManifest(Arrays.asList(entry("dup.bin", bytes(8)), entry("dup.bin", bytes(16)))); }
        catch (IOException expected) { dupNameRejected = true; }
        check(dupNameRejected);

        // R18: a name whose .part collides with another file fails closed.
        // The .part for "a.bin" is "a.bin.part". If the manifest also
        // contains a file named "a.bin.part", the construction must refuse.
        boolean partCollisionRejected = false;
        try { new ModelManifest(Arrays.asList(entry("a.bin", bytes(8)), entry("a.bin.part", bytes(8)))); }
        catch (IOException expected) { partCollisionRejected = true; }
        check(partCollisionRejected);

        // R19: model directory that is a symlink is rejected.
        File dir19 = freshDir("model-r19-");
        File real = freshDir("model-r19-real-");
        File symlink = new File(dir19, "link");
        Files.createSymbolicLink(symlink.toPath(), real.toPath());
        ModelManifest m19 = new ModelManifest(Arrays.asList(entry("x.bin", bytes(8))));
        boolean symlinkRoot = false;
        try { new ModelRepository(m19, symlink, new FakeSpace(256L * 1024 * 1024)); }
        catch (IOException expected) { symlinkRoot = true; }
        check(symlinkRoot);

        System.out.println("PASS " + checks + " model repository checks (host only; no test backdoors)");
    }

    static ModelEntry entry(String name, byte[] data) throws Exception {
        return new ModelEntry(name, data.length, sha256(data));
    }
}
