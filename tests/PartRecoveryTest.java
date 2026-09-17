import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real red/green evidence for the part-residue space bug.
 *
 * The fixture puts a fixed manifest entry of 100 bytes on disk and a stale
 * .part residue of 200 bytes. The test sets the free-space lambda to
 * return F (insufficient) while the .part exists, F + residue after it is
 * reclaimed. The test then calls the real ModelRepository.importFrom
 * and asserts it succeeds. The space lambda does NOT manually reclaim —
 * the repository must do it on its own.
 *
 * For the RED evidence, the production ModelRepository is copied verbatim
 * to .work/red/ with an aggregate space refusal injected before reclaim
 * (the old ordering bug). The same fixture is compiled and run against the
 * buggy copy. The compiled driver exits zero only after observing the expected space
 * refusal; compilation errors and unexpected success are not accepted as red.
 */
public final class PartRecoveryTest {
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
        for (int i = 0; i < n; i++) b[i] = (byte) ((i * 31 + 7) & 0xff);
        return b;
    }
    static File freshDir(String prefix) throws IOException { return Files.createTempDirectory(prefix).toFile(); }

    /** Fake source returning the supplied bytes for a single entry. */
    static final class StaticSource implements ModelSource {
        final Map<String, byte[]> store;
        StaticSource(Map<String, byte[]> store) { this.store = store; }
        @Override public Map<String, UriRef> enumerate(Collection<String> expected, int max) {
            Map<String, UriRef> out = new LinkedHashMap<>();
            for (String n : expected) if (store.containsKey(n)) out.put(n, new UriRef(n));
            return out;
        }
        @Override public InputStream open(UriRef ref) { return new ByteArrayInputStream(store.get(ref.handle())); }
    }

    public static void main(String[] args) throws Exception {
        // ===== GREEN: real importFrom on production code =====
        runGreenAgainstProduction();

        // ===== RED: copy production ModelRepository to .work/red, inject early refusal,
        //     compile and run the same test logic against the buggy copy. =====
        runRedEvidenceAgainstBuggyCopy();

        System.out.println("PASS " + checks + " part-recovery red/green checks (host only)");
    }

    static void runGreenAgainstProduction() throws Exception {
        // File: 100 bytes, residue: 200 bytes, padding: SPACE_PAD.
        // threshold = 100 + SPACE_PAD. The space lambda reports the OS-style
        // free space: F while the residue file is on disk, F + P after the
        // import reclaims it. The import reclaims BEFORE the space check, so
        // the check sees the post-reclaim free space.
        int S = 100;
        long F = (long) S + ModelRepository.SPACE_PAD - 164; // 164 bytes below threshold
        long P = 200;                                        // residue bytes
        check(F < (long) S + ModelRepository.SPACE_PAD);
        check(F + P > (long) S + ModelRepository.SPACE_PAD);

        byte[] payload = bytes(S);
        File dir = freshDir("part-green-");
        ModelEntry entry = new ModelEntry("p.bin", S, sha256(payload));
        ModelManifest manifest = new ModelManifest(Arrays.asList(entry));
        File partFile = new File(dir, entry.partName());
        Files.write(partFile.toPath(), bytes((int) P));
        check(partFile.exists());

        // OS-style space: while the residue is on disk, free is F; once the
        // import reclaims it, free grows by P. The import owns the reclaim.
        ModelRepository.SpaceProvider space = () -> partFile.exists() ? F : F + P;
        ModelRepository repo = new ModelRepository(manifest, dir, space);
        StaticSource source = new StaticSource(Collections.singletonMap("p.bin", payload));

        repo.importFrom(source, null);

        // The .part must be gone and the final file must exist.
        check(!partFile.exists());
        check(new File(dir, "p.bin").isFile());
        // Final verify passes.
        repo.verifyAll();
    }

    static void runRedEvidenceAgainstBuggyCopy() throws Exception {
        // Build a buggy ModelRepository in .work/red by copying the production
        // file and injecting a premature refusal inside importFrom. The compiled buggy
        // class is exercised by BuggyDriver with the same fixture.
        File work = new File(".work/red");
        if (work.exists()) deleteRecursively(work);
        work.mkdirs();

        Path source = Paths.get("android/app/src/org/llmasr/minimal/model/ModelRepository.java");
        Path target = Paths.get(work.getPath(), "ModelRepository.java");
        // Read the production source and inject the bug.
        String src = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        // The production order: reclaim first, then space check.
        // The buggy order: space check first, then reclaim.
        String reclaim = "            for (ModelEntry e : manifest.entries()) { checkpoint(gate); reclaimEntryPart(e); }\n";
        String budget = "            if (totalCopy > 0 && space.usableBytes() < Math.addExact(totalCopy, SPACE_PAD))\n                throw new IOException(\"存储空间不足：预检\");\n";
        // Add the premature aggregate refusal before reclaim, leaving the real post-reclaim
        // captured plan/check intact. Same semantic defect with the new structured plan.
        String buggy = src.replace(reclaim, budget + reclaim);
        check(!buggy.equals(src));
        Files.write(target, buggy.getBytes(StandardCharsets.UTF_8));

        // Compile the buggy ModelRepository plus everything it needs.
        File classes = new File(work, "classes");
        classes.mkdirs();
        ProcessBuilder pb = new ProcessBuilder("javac", "-encoding", "UTF-8", "-d", classes.getPath(),
            target.toString(),
            "android/app/src/org/llmasr/minimal/model/ModelEntry.java",
            "android/app/src/org/llmasr/minimal/model/ModelManifest.java",
            "android/app/src/org/llmasr/minimal/model/ModelSource.java",
            "android/app/src/org/llmasr/minimal/model/FileSafety.java",
            "android/app/src/org/llmasr/minimal/model/ModelReports.java",
            "android/app/src/org/llmasr/minimal/modelmanagement/ModelManagementState.java",
            "android/app/src/org/llmasr/minimal/model/ModelReadiness.java");
        pb.inheritIO();
        Process p = pb.start();
        int rc = waitBounded(p);
        check(rc == 0); if (rc != 0) throw new IOException("buggy compile failed rc=" + rc);

        // BuggyDriver runs the same fixture against the compiled buggy class.
        Path driverPath = Paths.get(work.getPath(), "BuggyDriver.java");
        Files.write(driverPath, buildBuggyDriver().getBytes(StandardCharsets.UTF_8));
        pb = new ProcessBuilder("javac", "-encoding", "UTF-8", "-d", classes.getPath(),
            "-classpath", classes.getPath(),
            driverPath.toString());
        pb.inheritIO();
        p = pb.start();
        rc = waitBounded(p);
        check(rc == 0); if (rc != 0) throw new IOException("buggy driver compile failed rc=" + rc);

        pb = new ProcessBuilder("java", "-cp", classes.getPath(), "BuggyDriver");
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(work, "buggy-run.log"));
        p = pb.start();
        rc = waitBounded(p);
        String output = new String(Files.readAllBytes(new File(work, "buggy-run.log").toPath()), StandardCharsets.UTF_8);
        // Record the actual buggy-run output for the report.
        Files.write(Paths.get(work.getPath(), "buggy-run.log"), output.getBytes(StandardCharsets.UTF_8));
        // BuggyDriver exits 0 when it caught the expected IOException. So
        // rc==0 means the buggy order refused the import (RED), rc!=0 means
        // the buggy order unexpectedly accepted the fixture.
        check(rc == 0); if (rc != 0) throw new IOException("buggy ModelRepository unexpectedly accepted the same fixture");
        check(output.contains("存储空间不足")); if (!output.contains("存储空间不足")) throw new IOException("expected 存储空间不足 in buggy output, got: " + output);

        // Confirm the production code still passes by running the green
        // check once more, this time against a freshly built production
        // class on the classpath (compile .work/red/classes includes the
        // buggy ModelRepository; we use the source's own classpath here).
        // The earlier runGreenAgainstProduction is the production check;
        // a second green call verifies determinism.
        runGreenAgainstProduction();
    }

    static int waitBounded(Process p) throws Exception {
        if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            throw new AssertionError("child process timeout");
        }
        return p.exitValue();
    }

    static void deleteRecursively(File f) {
        if (f.isDirectory()) for (File c : f.listFiles()) deleteRecursively(c);
        f.delete();
    }

    /** A small driver class compiled with the buggy ModelRepository. The
     * driver replicates the same fixture and space lambda. The buggy order
     * must refuse the import.
     */
    static String buildBuggyDriver() {
        return "import java.io.*;\n" +
            "import java.nio.file.*;\n" +
            "import java.security.MessageDigest;\n" +
            "import java.util.*;\n" +
            "import org.llmasr.minimal.model.*;\n" +
            "public final class BuggyDriver {\n" +
            "    public static void main(String[] a) throws Exception {\n" +
            "        int S = 100;\n" +
            "        long F = (long) S + ModelRepository.SPACE_PAD - 164;\n" +
            "        long P = 200;\n" +
            "        byte[] payload = new byte[S];\n" +
            "        for (int i = 0; i < S; i++) payload[i] = (byte) ((i * 31 + 7) & 0xff);\n" +
            "        MessageDigest h = MessageDigest.getInstance(\"SHA-256\");\n" +
            "        h.update(payload);\n" +
            "        StringBuilder s = new StringBuilder();\n" +
            "        for (byte b : h.digest()) s.append(String.format(\"%02x\", b & 255));\n" +
            "        File dir = Files.createTempDirectory(\"part-red-\").toFile();\n" +
            "        ModelEntry entry = new ModelEntry(\"p.bin\", S, s.toString());\n" +
            "        ModelManifest manifest = new ModelManifest(Arrays.asList(entry));\n" +
            "        File partFile = new File(dir, entry.partName());\n" +
            "        byte[] residue = new byte[(int) P];\n" +
            "        Files.write(partFile.toPath(), residue);\n" +
            "        // OS-style: free is F while residue is on disk.\n" +
            "        ModelRepository.SpaceProvider space = () -> partFile.exists() ? F : F + P;\n" +
            "        ModelRepository repo = new ModelRepository(manifest, dir, space);\n" +
            "        Map<String, byte[]> store = new HashMap<>();\n" +
            "        store.put(\"p.bin\", payload);\n" +
            "        ModelSource source = new ModelSource() {\n" +
            "            public Map<String, UriRef> enumerate(Collection<String> e, int max) {\n" +
            "                Map<String, UriRef> out = new LinkedHashMap<>();\n" +
            "                for (String n : e) if (store.containsKey(n)) out.put(n, new UriRef(n));\n" +
            "                return out;\n" +
            "            }\n" +
            "            public InputStream open(UriRef r) { return new ByteArrayInputStream(store.get(r.handle())); }\n" +
            "        };\n" +
            "        try {\n" +
            "            repo.importFrom(source, null);\n" +
            "            System.out.println(\"UNEXPECTED_PASS buggy importFrom returned without error\");\n" +
            "            System.exit(2);\n" +
            "        } catch (IOException ex) {\n" +
            "            System.out.println(\"EXPECTED_FAIL \" + ex.getMessage());\n" +
            "            System.exit(0);\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
    }
}
