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

    // Test-only deterministic observation seam; never used by the production app.
    static volatile Runnable cleanupObservation;
    private static final Set<Path> ACTIVE_RECOVERY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void runRedEvidenceAgainstBuggyCopy() throws Exception {
        Path workPath = Paths.get(System.getProperty("partRecovery.work", ".work/red")).toAbsolutePath().normalize();
        // Reject before opening any second fd: closing it can release this process's POSIX locks.
        if (!ACTIVE_RECOVERY.add(workPath)) throw new IOException("recovery work still owned by another caller");
        try {
            Path lockPath = Paths.get(workPath + ".cleanup.lock");
            Files.createDirectories(lockPath.getParent());
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                java.nio.channels.FileLock lock;
                try { lock = channel.tryLock(); }
                catch (java.nio.channels.OverlappingFileLockException busy) {
                    throw new IOException("recovery work still owned by another caller", busy);
                }
                if (lock == null) throw new IOException("recovery work still owned by another caller");
                try { runOwnedRecovery(workPath.toFile()); }
                finally { lock.release(); }
            }
        } finally {
            // try-with-resources closed the channel (including failure paths) before JVM admission reopens.
            ACTIVE_RECOVERY.remove(workPath);
        }
    }

    static void runOwnedRecovery(File work) throws Exception {
        // Build a buggy ModelRepository in .work/red by copying the production
        // file and injecting a premature refusal inside importFrom. The compiled buggy
        // class is exercised by BuggyDriver with the same fixture.
        cleanRecoveryDirectory(work.toPath());
        checkCleanupInterrupt();
        Files.createDirectories(work.toPath());

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
        // Fresh mixed compilation includes the real dependency sources, not old classes.
        Path driverPath = Paths.get(work.getPath(), "BuggyDriver.java");
        Files.write(driverPath, buildBuggyDriver().getBytes(StandardCharsets.UTF_8));
        List<String> command = new ArrayList<>(Arrays.asList("bash", "scripts/compile-jvm-sources.sh",
            classes.getPath(), "", target.toString(), driverPath.toString()));
        for (String name : new String[]{"model/ModelEntry", "model/ModelManifest", "model/ModelSource",
                "model/FileSafety", "model/ModelReports", "modelmanagement/ModelManagementState", "model/ModelReadiness", "model/ModelVerification"}) {
            Path base = Paths.get("android/app/src/org/llmasr/minimal/" + name);
            Path java = Paths.get(base + ".java"), kotlin = Paths.get(base + ".kt");
            if (Files.isRegularFile(java) == Files.isRegularFile(kotlin))
                throw new IOException("missing/duplicate recovery dependency: " + name);
            command.add((Files.isRegularFile(java) ? java : kotlin).toString());
        }
        int rc = supervised(command, work, "compile");
        check(rc == 0); if (rc != 0) throw new IOException("buggy mixed compile failed rc=" + rc);

        String stdlib = System.getenv("KOTLIN_HOME") + "/lib/kotlin-stdlib.jar";
        if (!Files.isRegularFile(Paths.get(stdlib))) throw new IOException("fixed Kotlin runtime missing");
        rc = supervised(Arrays.asList("java", "-cp", classes.getPath() + File.pathSeparator + stdlib, "BuggyDriver"), work, "run");
        String output = new String(Files.readAllBytes(new File(work, "run.stdout").toPath()), StandardCharsets.UTF_8);
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

    static void checkCleanupInterrupt() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("recovery cleanup interrupted; no input rebuild");
    }

    static void cleanRecoveryDirectory(Path work) throws Exception {
        checkCleanupInterrupt();
        if (!Files.exists(work, LinkOption.NOFOLLOW_LINKS)) return;
        // Reject marker admission before deleting any leaf. Never follow a root symlink.
        if (Files.isSymbolicLink(work)) throw new IOException("refuse symbolic recovery directory");
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(work)) {
            for (Path entry : entries) {
                checkCleanupInterrupt();
                String name = entry.getFileName().toString();
                if (name.startsWith(".mixed-lease-") && name.endsWith(".json") || name.equals("MIXED_EXIT_UNCONFIRMED.json"))
                    throw new IOException("mixed exit unconfirmed; refuse recovery directory reuse");
            }
        }
        Runnable observation = cleanupObservation;
        if (observation != null) observation.run();
        checkCleanupInterrupt();
        Files.walkFileTree(work, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("recovery cleanup interrupted");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("recovery cleanup interrupted");
                Files.delete(file); // walkFileTree defaults to NOFOLLOW; a symlink is only a leaf.
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("recovery cleanup interrupted");
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
        checkCleanupInterrupt();
    }

    static int supervised(List<String> command, File work, String label) throws Exception {
        Path cancel = new File(work, label + ".cancel").toPath();
        Path reservation = new File(work, ".mixed-lease-java-" + UUID.randomUUID() + ".json").toPath();
        Files.write(reservation, "{\"state\":\"reserved before supervisor launch\"}\n".getBytes(StandardCharsets.UTF_8));
        String deadline = System.getProperty("partRecovery.childSeconds", "20");
        double seconds = Double.parseDouble(deadline);
        if (!(seconds > 0 && seconds <= 20)) throw new IllegalArgumentException("invalid recovery child deadline");
        List<String> argv = new ArrayList<>(Arrays.asList("python3", "-B", "tests/mixed_compiler_lifecycle.py",
            "run", "--work", work.getPath(), "--label", label, "--timeout", deadline,
            "--cancel", cancel.toString(), "--watch-stdin", "--reservation", reservation.toString()));
        // This class directory is a live compiler dependency until owned teardown finishes.
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (new File(entry).isDirectory()) {
                Path dependencyReservation = new File(entry, reservation.getFileName().toString()).toPath();
                Files.copy(reservation, dependencyReservation);
                argv.add("--dependency"); argv.add(entry);
                argv.add("--dependency-reservation"); argv.add(dependencyReservation.toString());
                break;
            }
        }
        argv.add("--"); argv.addAll(command);
        Process process = new ProcessBuilder(argv).inheritIO().redirectInput(ProcessBuilder.Redirect.PIPE).start();
        boolean interrupted = false;
        try {
            try {
                if (!process.waitFor(25, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IOException("mixed supervisor deadline");
            } catch (InterruptedException | IOException failure) {
                interrupted = failure instanceof InterruptedException;
                Files.write(cancel, new byte[]{1});
                // Never kill just the supervisor: it still owns ordinary compiler descendants.
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    Files.write(new File(work, "MIXED_EXIT_UNCONFIRMED.json").toPath(),
                        "{\"reason\":\"supervisor stop unconfirmed\"}\n".getBytes(StandardCharsets.UTF_8));
                }
                throw failure;
            }
            return process.exitValue();
        } finally {
            process.getOutputStream().close(); // Parent exit/EOF also requests owned teardown.
            if (interrupted) Thread.currentThread().interrupt();
        }
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
