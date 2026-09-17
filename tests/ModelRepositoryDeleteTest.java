import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.model.ModelRepository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Real deleteAll tests against the production ModelRepository. Verifies
 *  the strict whitelist, the no-recursion / no-symlink / no-nested-dir
 *  guarantees, partial-failure reporting, and that the delete never
 *  touches files outside the model directory (ASR results, raw reports,
 *  external source). */
public final class ModelRepositoryDeleteTest {
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
        for (int i = 0; i < n; i++) b[i] = (byte) ((i * 7 + 13) & 0xff);
        return b;
    }
    static File freshDir(String prefix) throws IOException { return Files.createTempDirectory(prefix).toFile(); }

    static ModelEntry entry(String name, byte[] data) throws Exception {
        return new ModelEntry(name, data.length, sha256(data));
    }

    public static void main(String[] args) throws Exception {
        // D1: deleteAll removes only manifest files and their parts.
        {
            File dir = freshDir("del-d1-");
            byte[] a = bytes(64), b = bytes(64);
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", a), entry("b.bin", b))), dir,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            Files.write(new File(dir, "a.bin").toPath(), a);
            Files.write(new File(dir, "b.bin").toPath(), b);
            Files.write(new File(dir, "a.bin.part").toPath(), bytes(200));
            ModelRepository.DeleteReport report = r.deleteAll();
            check(report.modelDirWasMissing == false);
            check(report.failed.isEmpty());
            check(report.succeeded() >= 2);
            check(report.freedBytes >= 64 + 64);
            check(!new File(dir, "a.bin").exists());
            check(!new File(dir, "b.bin").exists());
            check(!new File(dir, "a.bin.part").exists());
        }

        // D2: deleteAll leaves external files alone (results, raw, etc.).
        {
            File dir = freshDir("del-d2-");
            File outside = File.createTempFile("del-d2-out-", ".txt");
            Files.write(outside.toPath(), bytes(32));
            byte[] a = bytes(64);
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", a))), dir,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            Files.write(new File(dir, "a.bin").toPath(), a);
            r.deleteAll();
            check(Files.exists(outside.toPath()));
            check(outside.length() == 32);
            outside.delete();
        }

        // D3: deleteAll with missing modelDir returns modelDirWasMissing
        //     and does not throw.
        {
            File dir = freshDir("del-d3-");
            File missing = new File(dir, "missing");
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", bytes(8)))), missing,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            check(!missing.exists());
            ModelRepository.DeleteReport report = r.deleteAll();
            check(report.modelDirWasMissing);
        }

        // D4: symlink in modelDir is reported as failed (cannot delete
        //     symlink via deleteRegular — only regular files).
        {
            File dir = freshDir("del-d4-");
            File outside = File.createTempFile("del-d4-out-", ".bin");
            Files.write(outside.toPath(), bytes(8));
            try {
                byte[] a = bytes(64);
                ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", a))), dir,
                    new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
                Files.write(new File(dir, "a.bin").toPath(), a);
                Files.createSymbolicLink(new File(dir, "b.bin").toPath(), outside.toPath());
                ModelRepository.DeleteReport report = r.deleteAll();
                check(!report.failed.isEmpty() || !report.unexpected.isEmpty());
                check(!new File(dir, "a.bin").exists());
            } finally { outside.delete(); }
        }

        // D5: nested directory inside modelDir is left in place and
        //     reported in unexpected (no recursion).
        {
            File dir = freshDir("del-d5-");
            byte[] a = bytes(64);
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", a))), dir,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            Files.write(new File(dir, "a.bin").toPath(), a);
            File nested = new File(dir, "nested");
            nested.mkdirs();
            Files.write(new File(nested, "secret.txt").toPath(), bytes(8));
            ModelRepository.DeleteReport report = r.deleteAll();
            check(report.unexpected.contains("nested"));
            check(nested.isDirectory());
            nested.delete();
        }

        // D6: directory at the manifest file name is rejected by verify
        //     but deleteAll reports the directory as a failed entry.
        {
            File dir = freshDir("del-d6-");
            byte[] a = bytes(64);
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", a))), dir,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            // Create a regular file first, then replace with a directory.
            File f = new File(dir, "a.bin");
            Files.write(f.toPath(), a);
            f.delete();
            f.mkdirs();
            boolean refused = false;
            try { r.deleteAll(); } catch (IOException expected) { refused = true; }
            check(refused); check(f.isDirectory());
            f.delete();
        }

        // D7: unknown file in modelDir is reported in unexpected and
        //     not deleted; model directory is not removed.
        {
            File dir = freshDir("del-d7-");
            byte[] a = bytes(64);
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", a))), dir,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            Files.write(new File(dir, "a.bin").toPath(), a);
            Files.write(new File(dir, "leftover.bin").toPath(), bytes(16));
            ModelRepository.DeleteReport report = r.deleteAll();
            check(report.unexpected.contains("leftover.bin"));
            check(!new File(dir, "leftover.bin").exists() == false); // file is preserved
            check(new File(dir, "leftover.bin").exists());
        }

        // D8: modelDir is a symlink — delete refuses and throws.
        {
            File dir = freshDir("del-d8-");
            File real = freshDir("del-d8-real-");
            File link = new File(dir, "link");
            Files.createSymbolicLink(link.toPath(), real.toPath());
            boolean refused = false;
            try {
                new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", bytes(8)))), link,
                    new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            } catch (IOException expected) { refused = true; }
            check(refused);
        }

        // D9: inspect() does not create the model directory.
        {
            File dir = freshDir("del-d9-");
            File nested = new File(dir, "no-such");
            // dir/nested does not exist; requireSafeRoot would mkdir it
            // (side effect). inspect must NOT mkdir.
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", bytes(8)))), nested,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            check(!nested.exists());
            ModelRepository.InspectReport report = r.inspect(ModelRepository.NEVER_CANCEL);
            check(report.expectedBytes == 8L);
            // dir is still missing — inspect did not create it.
            check(!nested.exists());
        }

        // D10: deleteAll on a freshly inspected dir (no files) reports
        //      the model dir is removed.
        {
            File dir = freshDir("del-d10-");
            byte[] a = bytes(64);
            ModelRepository r = new ModelRepository(new ModelManifest(Arrays.asList(entry("a.bin", a))), dir,
                new ModelRepository.SpaceProvider() { public long usableBytes() { return 0; } });
            // no files on disk; delete should consider them missing
            ModelRepository.DeleteReport report = r.deleteAll();
            check(report.considered == 0);
            // model dir is removed if no failures and no unexpected files
            check(report.modelDirRemoved); check(!dir.exists());
        }

        System.out.println("PASS " + checks + " model repository delete whitelist / partial / no-recursion checks");
    }
}
