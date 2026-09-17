package org.llmasr.minimal.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure Java model repository. Holds the fixed manifest and owns the model
 * directory. The repository:
 *  - inspects existing files without computing SHA (lightweight status read),
 *  - reclaims a stale .part for any fixed manifest entry before checking
 *    free space,
 *  - copies bytes from a ModelSource to a .part, sizes + hashes, then
 *    atomically renames the .part to the final name,
 *  - performs a final full verification pass after every copy,
 *  - cancels at chunk / file / publish boundaries via the supplied
 *    cancel gate, preserving "correct finals": already-published files are
 *    NOT removed, and a partial import can be resumed by re-selecting the
 *    source,
 *  - deletes only the fixed-manifest names and their explicit .parts,
 *    collecting partial-failure list; refuses symlinks, directories at
 *    the model name, or anything outside the model root,
 *  - fails closed on symlink / non-regular / path-escape and on delete
 *    failure.
 *
 * The repository does not hold a ContentResolver, an Activity, or any
 * Android types. Space is queried through a SpaceProvider; cancellation
 * is queried through a CancelGate so the controller can latch a cancel
 * without the worker holding any UI lock across slow IO.
 */
public class ModelRepository {
    public interface SpaceProvider { long usableBytes(); }
    public interface Progress {
        void update(Stage stage, String file, int fileIndex, int fileCount,
                    long fileDone, long fileTotal,
                    long stageDone, long stageTotal,
                    long reusedBytes);
        default void planned(ModelReports.ImportPlan plan) { }
        default void deletion(int succeeded, List<String> failed) { }
        /** Per-file milestone observation. Typed outcome so the model core
         *  never embeds UI strings; the page renders them through ModelUiText. */
        default void fileResult(String file, ModelReports.FileOutcome outcome) { }
    }
    /** Per-chunk cancel check + per-publish reservation. The controller
     *  implements this in terms of ModelOperationControl and Operation. */
    public interface CancelGate {
        /** True if a cancel was latched for this operation. Cheap volatile
         *  read; safe to call from any thread. */
        boolean cancelled();
        /** Settle the cancel/publish race. Called by the worker immediately
         *  before the atomic rename. Returns true if the rename may proceed
         *  (and the caller has reserved publication so a later cancel does
         *  not undo the in-flight rename); false if cancel already won. */
        boolean reservePublish();
        default void beforeMutation() throws IOException { }
        default void invalidExisting(String file) { }
    }
    /** Always-false gate for tests and for callers that don't track cancel. */
    public static final CancelGate NEVER_CANCEL = new CancelGate() {
        @Override public boolean cancelled() { return false; }
        @Override public boolean reservePublish() { return true; }
    };

    public enum Stage {
        INSPECTING,
        ENUMERATING,
        CHECKING_EXISTING,
        COPYING,
        VERIFYING_FILE,
        PUBLISHING,
        FINAL_VERIFY,
        DELETING
    }

    public static final long SPACE_PAD = 64L * 1024 * 1024;

    private final ModelManifest manifest;
    private volatile File modelDir;
    private final File appFilesDir;
    private boolean appRootResolved;
    private final SpaceProvider space;

    public ModelRepository(ModelManifest manifest, File modelDir, SpaceProvider space) throws IOException {
        this(manifest, modelDir, space, true);
    }

    /** Android graph bootstrap must not stat/canonicalize private model files on
     * the UI thread. Every repository operation still validates its root under
     * the shared worker owner; legacy eager construction retains its contract. */
    public static ModelRepository forWorker(ModelManifest manifest, File modelDir, SpaceProvider space) throws IOException {
        return new ModelRepository(manifest, modelDir, space, false);
    }
    private ModelRepository(ModelManifest manifest, File modelDir, SpaceProvider space, boolean inspectRoot) throws IOException {
        this(manifest, modelDir, space, inspectRoot, null);
    }
    private ModelRepository(ModelManifest manifest, File modelDir, SpaceProvider space, boolean inspectRoot, File appFilesDir) throws IOException {
        if (manifest == null) throw new IOException("manifest required");
        if (space == null) throw new IOException("space provider required");
        if (modelDir == null) throw new IOException("modelDir required");
        this.manifest = manifest;
        this.space = space;
        if (inspectRoot) {
            RootStatus root = checkRoot(modelDir);
            if (root != RootStatus.ROOT_OK && root != RootStatus.ROOT_MISSING) throw new IOException("非法 model 根目录");
        }
        this.modelDir = modelDir;
        this.appFilesDir = appFilesDir;
    }

    /** Only for Context.getFilesDir(), not user/provider-selected directories.
     * Resolve Android's trusted parent aliases on first worker IO. Do NOT
     * canonicalize the model leaf: it and all managed files remain untrusted. */
    public static ModelRepository forAppFiles(ModelManifest manifest, File filesDir, SpaceProvider space) throws IOException {
        if (filesDir == null) throw new IOException("filesDir required");
        return new ModelRepository(manifest, new File(filesDir, "model"), space, false, filesDir);
    }

    private synchronized RootStatus checkedRoot() throws IOException {
        if (appFilesDir != null && !appRootResolved) {
            File parent = appFilesDir.getCanonicalFile();
            if (!parent.isDirectory()) throw new IOException("应用内部存储目录不可用");
            modelDir = new File(parent, "model");
            appRootResolved = true;
        }
        return checkRoot(modelDir);
    }

    public ModelManifest manifest() { return manifest; }
    public File modelDir() { return modelDir; }
    public SpaceProvider space() { return space; }

    /** Validate the model root without creating it. Returns a short status:
     *  - ROOT_MISSING: model directory does not exist (no mkdir side effect).
     *  - ROOT_NOT_DIRECTORY: path exists but is not a directory.
     *  - ROOT_SYMLINK: path is a symbolic link.
     *  - ROOT_OK: directory exists and is safe.
     *
     *  inspect() and deleteAll() must use this non-mutating check so the
     *  page's first paint does not silently create the model directory.
     */
    public static RootStatus checkRoot(File modelDir) throws IOException {
        if (modelDir == null) throw new IOException("modelDir required");
        if (!modelDir.getAbsoluteFile().toPath().normalize().toString().equals(modelDir.getCanonicalPath()))
            throw new IOException("非法 model 根路径/符号链接");
        if (Files.isSymbolicLink(modelDir.toPath())) return RootStatus.ROOT_SYMLINK;
        if (!modelDir.exists()) return RootStatus.ROOT_MISSING;
        if (!modelDir.isDirectory()) return RootStatus.ROOT_NOT_DIRECTORY;
        return RootStatus.ROOT_OK;
    }

    public enum RootStatus { ROOT_OK, ROOT_MISSING, ROOT_NOT_DIRECTORY, ROOT_SYMLINK }

    /** Reclaims any .part files belonging to fixed manifest entries. Called
     * before the space check so a crashed previous import cannot block the
     * retry. Only names in the fixed manifest are touched; the model
     * directory is never recursively scanned, and the action is fail-closed.
     */
    public void reclaimOrphanedParts() throws IOException {
        for (ModelEntry e : manifest.entries()) {
            File part = safe(e.partName());
            if (part.exists()) deleteManaged(part);
        }
    }

    /** Lightweight inspect: per-file existence + size match, plus orphan
     *  .part discovery. Does NOT compute SHA. Use this for the page's
     *  first paint. The result is consumed by the management page's UI. */
    public InspectReport inspect(CancelGate cancel) throws IOException {
        checkpoint(cancel);
        RootStatus root = checkedRoot();
        List<ModelReports.FileDetail> details = new ArrayList<>();
        long installed = 0L, orphan = 0L, expected = 0L;
        if (root == RootStatus.ROOT_SYMLINK || root == RootStatus.ROOT_NOT_DIRECTORY)
            throw new IOException("非法 model 根路径");
        List<String> unexpected = new ArrayList<>();
        Set<String> managed = new HashSet<>();
        for (ModelEntry e : manifest.entries()) {
            checkpoint(cancel);
            expected += e.bytes();
            File f = root == RootStatus.ROOT_MISSING ? new File(modelDir, e.file()) : safe(e.file());
            File part = root == RootStatus.ROOT_MISSING ? new File(modelDir, e.partName()) : safe(e.partName());
            boolean present = f.isFile(), partExists = part.isFile();
            long actual = present ? f.length() : -1;
            if (present) installed += actual;
            if (partExists) orphan += part.length();
            details.add(new ModelReports.FileDetail(e.file(), e.bytes(), actual,
                present, present && actual == e.bytes(), partExists, false));
            managed.add(e.file()); managed.add(e.partName());
        }
        if (root == RootStatus.ROOT_OK) {
            File[] listed = modelDir.listFiles();
            if (listed == null) throw new IOException("无法枚举内部模型目录");
            for (File f : listed) if (!managed.contains(f.getName())) unexpected.add(f.getName());
        }
        return new InspectReport(details, installed, orphan, expected, space.usableBytes(), unexpected);
    }

    /** Shared fail-closed final/part path validation. Does not hash, create or delete. */
    public void validateManagedBoundary(CancelGate cancel) throws IOException {
        for (ModelEntry e : manifest.entries()) {
            checkpoint(cancel); safe(e.file()); safe(e.partName());
        }
    }

    private File safe(String name) throws IOException {
        RootStatus root = checkedRoot();
        if (root != RootStatus.ROOT_OK) throw new IOException("非法或缺少 model 根目录");
        return FileSafety.requireRegularInside(modelDir, name);
    }
    public static final class CancelledException extends IOException {
        public CancelledException() { super("已取消"); }
    }
    public static class ModelFileException extends IOException {
        public final String file;
        public final ModelReports.FileOutcome.Failure failure;
        ModelFileException(String file, String reason) {
            super(reason + "：" + file); this.file=file;
            switch (reason) {
                case "SHA-256 不符": failure=ModelReports.FileOutcome.Failure.SHA; break;
                case "缺少或大小不符": case "大小不符": failure=ModelReports.FileOutcome.Failure.SIZE; break;
                case "源目录缺少": failure=ModelReports.FileOutcome.Failure.SOURCE_MISSING; break;
                case "文件超出清单大小": failure=ModelReports.FileOutcome.Failure.TOO_LARGE; break;
                case "写入字节数不匹配清单": failure=ModelReports.FileOutcome.Failure.WRITE_SIZE; break;
                default: failure=ModelReports.FileOutcome.Failure.IO;
            }
        }
    }
    public static class InvalidModelException extends ModelFileException {
        InvalidModelException(String file) { super(file, "SHA-256 不符"); }
    }
    private static void checkpoint(CancelGate cancel) throws IOException {
        if (cancel != null && cancel.cancelled()) throw new CancelledException();
    }
    // Narrow real-IO fault seams; defaults preserve the original filesystem behavior.
    protected OutputStream openPart(File part) throws IOException { return new FileOutputStream(part); }
    protected void publishPart(File part, File dest) throws IOException {
        if (!part.renameTo(dest)) throw new IOException("无法原子替换：" + dest.getName());
    }
    protected void deleteManaged(File file) throws IOException { FileSafety.deleteRegular(file); }

    /** Verifies a single file already on disk against its manifest entry. */
    public void verifyEntry(ModelEntry entry) throws IOException {
        File file = safe(entry.file());
        if (!file.isFile() || file.length() != entry.bytes()) throw new ModelFileException(entry.file(), "缺少或大小不符");
        if (!hex(sha256(file)).equals(entry.sha256())) throw new InvalidModelException(entry.file());
    }

    /** Final full verification: every manifest entry must be present and correct. */
    public void verifyAll() throws IOException {
        if (manifest.entries().isEmpty()) throw new IOException("清单为空");
        validateManagedBoundary(NEVER_CANCEL);
        for (ModelEntry e : manifest.entries()) verifyEntry(e);
    }

    /** Final full verification with cancel checkpoints. */
    public void verifyAll(Progress progress, CancelGate cancel) throws IOException {
        if (manifest.entries().isEmpty()) throw new IOException("清单为空");
        validateManagedBoundary(cancel);
        List<ModelEntry> entries = manifest.entries();
        long total = 0L; for (ModelEntry e : entries) total += e.bytes();
        long done = 0L;
        for (int i = 0; i < entries.size(); i++) {
            checkpoint(cancel);
            final ModelEntry e = entries.get(i);
            final int idx = i;
            final long baseDone = done;
            final long entryBytes = e.bytes();
            final int totalCount = entries.size();
            final long finalTotal = total;
            File file = safe(e.file());
            if (!file.isFile() || file.length() != e.bytes()) throw new ModelFileException(e.file(), "缺少或大小不符");
            if (progress != null) progress.update(Stage.FINAL_VERIFY, e.file(), idx, totalCount,
                0L, entryBytes, baseDone, finalTotal, 0L);
            byte[] digest = sha256WithProgress(file, cancel, e.bytes(), fileDone -> {
                if (progress != null) progress.update(Stage.FINAL_VERIFY, e.file(), idx, totalCount,
                    fileDone, entryBytes, baseDone + fileDone, finalTotal, 0L);
            });
            if (!hex(digest).equals(e.sha256())) throw new InvalidModelException(e.file());
            if (progress != null) progress.fileResult(e.file(),
                new ModelReports.FileOutcome(ModelReports.FileOutcome.Status.FINAL_VERIFIED));
            done += entryBytes;
        }
        checkpoint(cancel);
    }

    private static byte[] sha256WithProgress(File file, CancelGate cancel, long limit, ProgressSink sink) throws IOException {
        try {
            MessageDigest h = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] b = new byte[1024 * 1024];
                long done = 0L; int n;
                while (true) {
                    checkpoint(cancel);
                    n = in.read(b);
                    checkpoint(cancel);
                    if (n == -1) break;
                    if (done + n > limit) throw new IOException("文件超出清单大小：" + file.getName());
                    h.update(b, 0, n);
                    done += n;
                    if (sink != null) sink.consume(done);
                }
            }
            checkpoint(cancel);
            return h.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable");
        }
    }
    private interface ProgressSink { void consume(long fileDone); }

    /** Imports the supplied entries from the source with cancel checkpoints.
     *  The cancel gate is consulted:
     *  - before each file's copy,
     *  - between every chunk (1 MiB) of copy,
     *  - before the atomic rename (reservePublish),
     *  - before verifyAll commits.
     *
     *  The space check happens AFTER the per-entry part reclaim, so an import
     *  that the previous code would have refused because of residual .part
     *  bytes can still proceed.
     *
     *  Files already present and matching the manifest are reused (verified
     *  in place). Missing or changed entries are copied from the source.
     */
    public void importFrom(ModelSource source, Progress progress) throws IOException {
        importFrom(source, progress, NEVER_CANCEL);
    }

    public void importFrom(ModelSource source, Progress progress, CancelGate cancel) throws IOException {
        if (source == null) throw new IOException("source required");
        final CancelGate gate = cancel == null ? NEVER_CANCEL : cancel;
        checkpoint(gate);
        RootStatus root = checkedRoot();
        if (root != RootStatus.ROOT_OK && root != RootStatus.ROOT_MISSING) throw new IOException("非法 model 根目录");
        Set<String> expected = new HashSet<>();
        for (ModelEntry e : manifest.entries()) expected.add(e.file());
        emit(progress, Stage.ENUMERATING, null, 0, 0, 0, 0, 0, 0);
        Map<String, ModelSource.UriRef> refs = source.enumerate(expected, 10_000, gate);
        checkpoint(gate);
        if (refs == null || refs.size() > 10_000) throw new IOException("非法源枚举");
        Map<ModelEntry, Boolean> reuse = new HashMap<>();
        long totalCopy = 0, reused = 0, checked = 0;
        List<String> reusableFiles = new ArrayList<>();
        long total = 0;
        if (root == RootStatus.ROOT_OK) for (ModelEntry e : manifest.entries()) {
            checkpoint(gate);
            File f = safe(e.file()); safe(e.partName());
            if (f.isFile() && f.length() == e.bytes()) total = Math.addExact(total, e.bytes());
        }
        int index = 0;
        for (ModelEntry e : manifest.entries()) {
            checkpoint(gate);
            boolean valid = false;
            if (root == RootStatus.ROOT_OK) {
                File f = safe(e.file()); safe(e.partName()); // path violations are NOT repairable content failures
                if (f.isFile() && f.length() == e.bytes()) {
                    final long base = checked, stageTotal = total;
                    final int idx = index;
                    emit(progress, Stage.CHECKING_EXISTING, e.file(), idx, 0, e.bytes(), base, stageTotal, 0);
                    valid = hex(sha256WithProgress(f, gate, e.bytes(), n ->
                        emit(progress, Stage.CHECKING_EXISTING, e.file(), idx, n, e.bytes(), base + n, stageTotal, 0))).equals(e.sha256());
                    if (!valid) gate.invalidExisting(e.file());
                    checked += e.bytes();
                }
            }
            reuse.put(e, valid);
            if (valid) { reused += e.bytes(); reusableFiles.add(e.file()); }
            else totalCopy = Math.addExact(totalCopy, e.bytes());
            if (progress != null) progress.fileResult(e.file(), valid
                ? new ModelReports.FileOutcome(ModelReports.FileOutcome.Status.REUSED)
                : new ModelReports.FileOutcome(ModelReports.FileOutcome.Status.PENDING));
            index++;
        }
        if (progress != null) progress.planned(new ModelReports.ImportPlan(totalCopy, reused, -1, reusableFiles));
        for (ModelEntry e : manifest.entries()) {
            checkpoint(gate);
            if (!reuse.get(e) && refs.get(e.file()) == null) throw new ModelFileException(e.file(), "源目录缺少");
        }
        // First mutation revokes READY; a failure above preserves a same-epoch proof.
        boolean parts = false;
        if (root == RootStatus.ROOT_OK) for (ModelEntry e : manifest.entries()) parts |= safe(e.partName()).exists();
        if (totalCopy > 0 || parts) {
            checkpoint(gate);
            gate.beforeMutation();
            ModelManifest.requireSafeRoot(modelDir);
            for (ModelEntry e : manifest.entries()) { checkpoint(gate); reclaimEntryPart(e); }
        }
        // Capture the actual budget check once, AFTER reclaim; preserve it on rejection.
        long availableAtCheck = space.usableBytes();
        ModelReports.ImportPlan plan = new ModelReports.ImportPlan(totalCopy, reused, availableAtCheck, reusableFiles);
        if (progress != null) progress.planned(plan);
        if (totalCopy > 0 && availableAtCheck < plan.requiredBytes)
            throw new IOException("存储空间不足：预检");
        long stageDone = 0; index = 0;
        for (ModelEntry entry : manifest.entries()) {
            checkpoint(gate);
            if (reuse.get(entry)) { index++; continue; }
            File dest = safe(entry.file());
            reclaimEntryPart(entry);

            if (space.usableBytes() < entry.bytes() + SPACE_PAD) {
                throw new IOException("存储空间不足：" + entry.file());
            }
            emit(progress, Stage.COPYING, entry.file(), index, 0, entry.bytes(), stageDone, totalCopy, reused);
            copyAndVerify(source, refs.get(entry.file()), entry, dest, progress, gate, index, stageDone, totalCopy, reused);
            stageDone += entry.bytes(); index++;
        }
        checkpoint(gate);
        verifyAll(progress, gate);
        checkpoint(gate);
    }

    private void emit(Progress p, Stage stage, String name, int index, long done, long total,
                      long stageDone, long stageTotal, long reused) {
        if (p != null) p.update(stage, name, index, manifest.entries().size(), done, total, stageDone, stageTotal, reused);
    }

    private void reclaimEntryPart(ModelEntry entry) throws IOException {
        File part = safe(entry.partName());
        if (part.exists()) deleteManaged(part);
    }

    private void copyAndVerify(ModelSource source, ModelSource.UriRef ref, ModelEntry entry, File dest,
                                Progress progress, CancelGate cancel,
                                int fileIndex, long stageBase, long stageTotal, long reusedBytes) throws IOException {
        File part = safe(entry.partName());
        checkpoint(cancel);
        InputStream stream = source.open(ref);
        if (stream == null) throw new IOException("无法读取：" + entry.file());
        try {
            long count = 0;
            try (InputStream in = stream; OutputStream out = openPart(part)) {
                byte[] b = new byte[1024 * 1024];
                int n;
                while (true) {
                    checkpoint(cancel);
                    n = in.read(b);
                    checkpoint(cancel);
                    if (n == -1) break;
                    count += n;
                    if (count > entry.bytes()) throw new ModelFileException(entry.file(), "文件超出清单大小");
                    out.write(b, 0, n);
                    if (progress != null) progress.update(Stage.COPYING, entry.file(), fileIndex, manifest.entries().size(),
                        count, entry.bytes(), stageBase + count, stageTotal, reusedBytes);
                }
                if (count != entry.bytes()) throw new ModelFileException(entry.file(), "写入字节数不匹配清单");
            }
            // Stream closed cleanly. Now verify the part.
            if (progress != null) progress.update(Stage.VERIFYING_FILE, entry.file(), fileIndex, manifest.entries().size(),
                0L, entry.bytes(), 0L, entry.bytes(), reusedBytes);
            checkpoint(cancel);
            if (!part.isFile() || part.length() != entry.bytes()) throw new ModelFileException(entry.file(), "大小不符");
            byte[] digest = sha256WithProgress(part, cancel, entry.bytes(), n ->
                emit(progress, Stage.VERIFYING_FILE, entry.file(), fileIndex, n, entry.bytes(), n, entry.bytes(), reusedBytes));
            if (!hex(digest).equals(entry.sha256())) throw new InvalidModelException(entry.file());

            // Publication / cancel arbitration. The cancel lock is held
            // only across this short critical section; the rename itself
            // is allowed to run even if a cancel arrives during it because
            // the reservation is already in.
            if (progress != null) progress.update(Stage.PUBLISHING, entry.file(), fileIndex, manifest.entries().size(),
                0L, entry.bytes(), 0L, entry.bytes(), reusedBytes);
            if (!cancel.reservePublish()) throw new CancelledException();

            // Rename only after both streams have closed cleanly. A close
            // failure surfaces from the inner try-with-resources and the
            // import fails before the rename happens.
            publishPart(part, dest);
            // Historical publication evidence, not global READY. Record it even
            // when cancellation arrived during this already-reserved rename.
            if (progress != null) progress.fileResult(entry.file(),
                new ModelReports.FileOutcome(ModelReports.FileOutcome.Status.PUBLISHED));
            checkpoint(cancel);
        } catch (IOException | RuntimeException failure) {
            if (part.exists()) {
                try { deleteManaged(part); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    /** Deletes internal model files. Strict whitelist:
     *  - only the fixed manifest file names and their explicit .part names,
     *  - symlinks rejected (no traversal),
     *  - directories at a manifest name rejected,
     *  - unexpected files in modelDir are left in place and reported in the
     *    DeleteReport so the user can see them.
     *
     *  The model directory itself is removed only if it is empty after the
     *  whitelist deletions. Non-empty directories are left in place and the
     *  unexpected files are reported.
     */
    public DeleteReport deleteAll() throws IOException { return deleteAll(null); }

    public DeleteReport deleteAll(Progress progress) throws IOException {
        RootStatus root = checkedRoot();
        if (root == RootStatus.ROOT_MISSING) {
            return new DeleteReport(0, Collections.emptyList(), 0L, Collections.emptyList(), true, true);
        }
        if (root == RootStatus.ROOT_SYMLINK) throw new IOException("model 目录是符号链接，拒绝删除");
        if (root == RootStatus.ROOT_NOT_DIRECTORY) throw new IOException("model 路径不是目录，拒绝删除");

        List<String> failed = new ArrayList<>(), unexpected = new ArrayList<>();
        List<File> plan = new ArrayList<>();
        Set<String> whitelist = new HashSet<>();
        // Validate the entire boundary before deleting anything. Never continue past an unsafe path.
        for (ModelEntry e : manifest.entries()) {
            for (String name : new String[] {e.file(), e.partName()}) {
                whitelist.add(name);
                File f = safe(name);
                if (f.exists()) plan.add(f);
            }
        }
        long freed = 0;
        int processed = 0;
        if (progress != null) progress.update(Stage.DELETING, null, 0, plan.size(), 0, 0, 0, plan.size(), 0);
        for (File f : plan) {
            safe(f.getName());
            try { long size = f.length(); deleteManaged(f); freed += size; }
            catch (IOException ex) { failed.add(f.getName() + ":" + ex.getMessage()); }
            processed++;
            if (progress != null) progress.deletion(processed - failed.size(), Collections.unmodifiableList(new ArrayList<>(failed)));
            if (progress != null) progress.update(Stage.DELETING, f.getName(), processed, plan.size(),
                0, 0, processed, plan.size(), 0);
        }
        File[] listed = modelDir.listFiles();
        if (listed == null) throw new IOException("无法枚举内部模型目录");
        for (File f : listed) if (!whitelist.contains(f.getName())) unexpected.add(f.getName());
        boolean removed = failed.isEmpty() && unexpected.isEmpty() && modelDir.delete();
        return new DeleteReport(plan.size(), failed, freed, unexpected, removed, false);
    }

    private static String hex(byte[] data) {
        StringBuilder s = new StringBuilder();
        for (byte b : data) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }

    private static byte[] sha256(File file) throws IOException {
        try {
            MessageDigest h = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] b = new byte[1024 * 1024];
                int n;
                while ((n = in.read(b)) != -1) h.update(b, 0, n);
            }
            return h.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable");
        }
    }

    /** Inspect report from a non-mutating inspect. */
    public static final class InspectReport {
        public final List<ModelReports.FileDetail> files;
        public final long installedBytes;
        public final long orphanPartBytes;
        public final long expectedBytes;
        public final long availableBytes;
        public final List<String> unexpected;
        public InspectReport(List<ModelReports.FileDetail> files, long installedBytes, long orphanPartBytes, long expectedBytes, long availableBytes) {
            this(files, installedBytes, orphanPartBytes, expectedBytes, availableBytes, Collections.emptyList());
        }
        public InspectReport(List<ModelReports.FileDetail> files, long installedBytes, long orphanPartBytes, long expectedBytes, long availableBytes, List<String> unexpected) {
            this.unexpected = Collections.unmodifiableList(new ArrayList<>(unexpected));
            this.files = Collections.unmodifiableList(new ArrayList<>(files));
            this.installedBytes = installedBytes;
            this.orphanPartBytes = orphanPartBytes;
            this.expectedBytes = expectedBytes;
            this.availableBytes = availableBytes;
        }
    }

    /** Delete report from a strict-whitelist delete. */
    public static final class DeleteReport {
        public final int considered;
        public final List<String> failed;
        public final long freedBytes;
        public final List<String> unexpected;
        public final boolean modelDirRemoved;
        public final boolean modelDirWasMissing;
        public DeleteReport(int considered, List<String> failed, long freedBytes, List<String> unexpected, boolean modelDirRemoved, boolean modelDirWasMissing) {
            this.considered = considered;
            this.failed = Collections.unmodifiableList(new ArrayList<>(failed));
            this.freedBytes = freedBytes;
            this.unexpected = Collections.unmodifiableList(new ArrayList<>(unexpected));
            this.modelDirRemoved = modelDirRemoved;
            this.modelDirWasMissing = modelDirWasMissing;
        }
        public int succeeded() { return considered - failed.size(); }
    }
}
