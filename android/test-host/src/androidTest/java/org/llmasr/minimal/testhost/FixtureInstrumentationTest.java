package org.llmasr.minimal.testhost;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.test.InstrumentationTestCase;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.llmasr.minimal.ime.ImeController;
import org.llmasr.minimal.ime.ImeSession;
import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.model.ModelProviderBoundary;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelReports;
import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.model.SafModelSource;
import org.llmasr.minimal.task.TaskCoordinator;

/** SDK JUnit3 naming is intentional. All 14 public test methods require future device discovery.
 * Compiling this suite proves no Android behavior, Binder/grants or production Service callbacks.
 */
@SuppressWarnings("deprecation")
public final class FixtureInstrumentationTest extends InstrumentationTestCase {
    public void testEnginePayloadAndOrder() throws Exception { FixtureChecks.enginePayloadAndOrder(); }
    public void testReadinessRejectsBeforeLoad() throws Exception { FixtureChecks.readinessRejectsBeforeLoad(); }
    public void testBadBytesAreSafe() throws Exception { FixtureChecks.badBytesAreSafe(); }
    public void testSingleConfirmationAndPassword() { FixtureChecks.singleConfirmationAndPassword(); }
    public void testLateResultIsInvalidated() { FixtureChecks.lateResultIsInvalidated(); }
    public void testCleanupRetainsOwner() throws Exception { FixtureChecks.cleanupRetainsOwner(); }
    public void testReusedBackendQueuedCancellation() { FixtureChecks.reusedBackendQueuedCancellation(); }

    private SafModelSource source(FakeDocumentsProvider provider) {
        ProviderInfo info = new ProviderInfo();
        info.authority = FakeDocumentsProvider.AUTHORITY;
        info.exported = false;
        provider.attachInfo(getInstrumentation().getTargetContext(), info);
        Uri tree = DocumentsContract.buildTreeDocumentUri(FakeDocumentsProvider.AUTHORITY, "synthetic-root");
        return new SafModelSource(ContentResolver.wrap(provider), tree);
    }
    private static void safe(IOException failure) {
        FixtureChecks.require(ModelProviderBoundary.DIRECTORY_FAILURE.equals(failure.getMessage())
            && failure.getCause() == null && failure.getSuppressed().length == 0,
            "provider error has only fixed safe identity");
    }
    public void testProviderEnumerationClosesCursor() throws Exception {
        FakeDocumentsProvider p = new FakeDocumentsProvider();
        Map<String, ModelSource.UriRef> files = source(p).enumerate(Collections.singleton("model.bin"), 2);
        assertEquals(Collections.singleton("model.bin"), files.keySet());
        assertTrue(files.get("model.bin").handle() instanceof Uri);
        assertEquals(1, p.queries.get()); assertEquals(1, p.closes.get());
    }
    public void testProviderQueryErrorIsPrivate() throws Exception {
        FakeDocumentsProvider p = new FakeDocumentsProvider(); p.failQuery = true;
        try {
            source(p).enumerate(Collections.singleton("model.bin"), 2);
            fail("query error accepted");
        } catch (IOException failure) { safe(failure); }
        assertEquals(1, p.queries.get()); assertEquals(0, p.closes.get());
    }
    public void testProviderCloseErrorIsPrivate() throws Exception {
        FakeDocumentsProvider p = new FakeDocumentsProvider(); p.failClose = true;
        try {
            source(p).enumerate(Collections.singleton("model.bin"), 2);
            fail("close error accepted");
        } catch (IOException failure) { safe(failure); }
        assertEquals(1, p.closes.get());
    }
    public void testProviderCancellationWaitsForQuery() throws Exception {
        FakeDocumentsProvider p = new FakeDocumentsProvider(); p.blockQuery = true;
        SafModelSource source = source(p);
        AtomicBoolean cancelled = new AtomicBoolean(), returned = new AtomicBoolean();
        AtomicReference<Throwable> result = new AtomicReference<>();
        try (FakePorts.Worker worker = new FakePorts.Worker()) {
            TaskCoordinator owner = new TaskCoordinator(worker);
            try {
                assertTrue(owner.submit(() -> {
                    try {
                        source.enumerate(Collections.singleton("model.bin"), 2, new ModelRepository.CancelGate() {
                            @Override public boolean cancelled() { return cancelled.get(); }
                            @Override public boolean reservePublish() {
                                throw new AssertionError("enumeration must not reserve publication");
                            }
                        });
                    }
                    catch (Throwable failure) { result.set(failure); }
                    finally { returned.set(true); }
                }));
                FakePorts.await(p.entered, "query entered"); cancelled.set(true);
                assertFalse(returned.get()); assertTrue(owner.isBusy());
                assertFalse(owner.submit(() -> {}));
                assertEquals(0, p.closes.get());
            } finally { p.release.countDown(); }
            owner.awaitFree();
            assertTrue(returned.get()); assertFalse(owner.isBusy());
            assertTrue(result.get() instanceof ModelRepository.CancelledException);
            assertEquals(1, p.closes.get());
        }
    }
    private File newSafRoot() throws IOException {
        File root = new File(getInstrumentation().getTargetContext().getCacheDir().getCanonicalFile(),
            "m5saf-" + UUID.randomUUID());
        if (!root.mkdir()) throw new IOException("synthetic root creation failed");
        return root;
    }
    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }
    private static void deleteSafRoot(File root) throws IOException {
        // walk does not follow links; only the UUID subtree created by this test is removed.
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
            java.nio.file.Path[] ordered = paths.sorted(java.util.Comparator.reverseOrder())
                .toArray(java.nio.file.Path[]::new);
            for (java.nio.file.Path path : ordered) Files.delete(path);
        }
    }
    private final class SafImportFixture {
        final byte[] bytes = {1, 2, 3};
        final FakeDocumentsProvider provider = new FakeDocumentsProvider();
        final ModelReadiness readiness = new ModelReadiness();
        final ModelOperationControl control = new ModelOperationControl();
        final ModelManagementState state = new ModelManagementState();
        final TaskCoordinator owner;
        final ModelManagementController controller;
        final SafModelSource saf;
        final File installed, part;
        final String expectedSha;
        SafImportFixture(File root, Executor executor) throws Exception {
            File sourceDir = new File(root, "source");
            if (!sourceDir.mkdir()) throw new IOException("synthetic source creation failed");
            File sourceFile = new File(sourceDir, "model.bin");
            Files.write(sourceFile.toPath(), bytes);
            saf = source(provider); // real ContentResolver.wrap(ContentProvider), not Binder/grants
            provider.setSyntheticFile(sourceFile);
            File repositoryDir = new File(root, "repository");
            expectedSha = sha256(bytes);
            ModelManifest manifest = new ModelManifest(Collections.singletonList(
                new ModelEntry("model.bin", bytes.length, expectedSha)));
            ModelRepository repository = new ModelRepository(manifest, repositoryDir,
                () -> ModelRepository.SPACE_PAD + bytes.length + 1024);
            installed = new File(repositoryDir, "model.bin");
            part = new File(repositoryDir, "model.bin.part");
            owner = new TaskCoordinator(executor);
            controller = new ModelManagementController(owner, repository, readiness, control, state);
        }
        void counts(int expected) {
            assertEquals(expected, provider.queries.get());
            assertEquals(expected, provider.closes.get()); // cursor only, NOT InputStream-close evidence
            assertEquals(expected, provider.opens.get());
        }
        void released() {
            assertFalse(owner.isBusy()); assertFalse(control.isBusy());
            assertNull(control.peekActive()); assertFalse(state.current().pageOwnerHeld);
        }
        void unpublished() {
            assertFalse(readiness.isReady()); assertFalse(state.current().ready);
            assertFalse(installed.exists()); assertFalse(part.exists());
        }
    }
    public void testSafImportOpenFailureThenRetryPreservesHistory() throws Exception {
        File root = newSafRoot();
        try {
            FakePorts.Queue queue = new FakePorts.Queue();
            SafImportFixture f = new SafImportFixture(root, queue);
            f.provider.failOpen = true;
            assertTrue(f.controller.startImport(f.saf));
            String failedId = f.state.current().activeOperationId;
            assertNotNull(failedId);
            queue.drain();
            ModelManagementState.Snapshot failed = f.state.current();
            assertEquals(ModelManagementState.Phase.FAILED, failed.phase);
            assertEquals(ModelManagementState.Outcome.FAILED, failed.lastOutcome);
            assertEquals(ModelProviderBoundary.FILE_FAILURE, failed.errorCode);
            assertFalse(failed.errorCode.contains(FakeDocumentsProvider.CANARY));
            // Plain open failure has no ModelFileException entry, but retains the pre-open plan history.
            assertTrue(failed.failedFiles.isEmpty());
            assertEquals(Collections.singleton("model.bin"), failed.fileResults.keySet());
            assertEquals(ModelReports.FileOutcome.Status.PENDING, failed.fileResults.get("model.bin").status);
            assertEquals(ModelManagementState.Outcome.SUCCEEDED, failed.cleanupOutcome);
            f.counts(1); f.unpublished(); f.released();
            f.controller.refreshInspect();
            assertTrue(f.state.current().inspectBusy); queue.drain();
            ModelManagementState.Snapshot inspected = f.state.current();
            assertEquals(failedId, inspected.activeOperationId);
            assertEquals(failed.phase, inspected.phase); assertEquals(failed.lastOutcome, inspected.lastOutcome);
            assertEquals(failed.errorCode, inspected.errorCode);
            assertEquals(failed.failedFiles, inspected.failedFiles);
            assertEquals(failed.fileResults, inspected.fileResults);
            assertEquals(failed.cleanupOutcome, inspected.cleanupOutcome);
            assertFalse(inspected.inspectBusy); assertNull(inspected.inspectError);
            assertEquals(3L, inspected.expectedBytes); assertEquals(0L, inspected.installedBytes);
            assertEquals(0L, inspected.orphanPartBytes); assertEquals(1, inspected.files.size());
            assertFalse(inspected.files.get(0).present);
            f.counts(1); f.unpublished(); f.released();
            f.provider.failOpen = false;
            assertTrue(f.controller.startImport(f.saf));
            String retryId = f.state.current().activeOperationId;
            assertNotNull(retryId); assertFalse(failedId.equals(retryId));
            queue.drain();
            ModelManagementState.Snapshot succeeded = f.state.current();
            // External terminal assertions fail even if RequestRunner swallowed a body exception.
            assertEquals(ModelManagementState.Phase.SUCCEEDED, succeeded.phase);
            assertEquals(ModelManagementState.Outcome.SUCCEEDED, succeeded.lastOutcome);
            assertEquals(retryId, succeeded.activeOperationId); assertNull(succeeded.errorCode);
            assertTrue(succeeded.failedFiles.isEmpty()); assertTrue(succeeded.ready);
            assertTrue(f.readiness.isReady()); assertEquals(ModelReadiness.State.READY, succeeded.readiness);
            byte[] actual = Files.readAllBytes(f.installed.toPath());
            assertTrue(Arrays.equals(f.bytes, actual)); assertEquals(f.expectedSha, sha256(actual));
            assertFalse(f.part.exists()); f.counts(2); f.released();
        } finally { deleteSafRoot(root); }
    }
    /** Captures even fatal worker errors outside RequestRunner; join precedes fixture deletion. */
    private static final class SafWorker implements Executor, AutoCloseable {
        private Thread thread;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        @Override public void execute(Runnable work) {
            if (thread != null) throw new AssertionError("SAF worker reused");
            thread = new Thread(() -> {
                try { work.run(); } catch (Throwable problem) { failure.set(problem); }
            }, "m5saf-open-worker");
            thread.start();
        }
        boolean stopped() { return thread == null || !thread.isAlive(); }
        @Override public void close() throws InterruptedException {
            if (thread != null) {
                thread.join(6000);
                if (thread.isAlive()) { thread.interrupt(); thread.join(1000); }
            }
            FixtureChecks.require(stopped(), "SAF worker failed bounded join");
            Throwable problem = failure.get();
            if (problem != null) throw new AssertionError("SAF worker failed", problem);
        }
    }
    public void testSafImportCancellationWaitsForOpen() throws Exception {
        File root = newSafRoot();
        SafWorker worker = new SafWorker();
        try {
            SafImportFixture f = new SafImportFixture(root, worker);
            f.provider.blockOpen = true;
            String operationId;
            try (SafWorker joining = worker) {
                try {
                    assertTrue(f.controller.startImport(f.saf));
                    FakePorts.await(f.provider.openEntered, "import open entered");
                    operationId = f.state.current().activeOperationId;
                    assertNotNull(operationId);
                    f.controller.requestCancel(operationId);
                    assertEquals(ModelManagementState.Phase.CANCELLING, f.state.current().phase);
                    assertTrue(f.state.current().pageOwnerHeld); assertTrue(f.owner.isBusy());
                    assertTrue(f.control.isBusy()); assertNotNull(f.control.peekActive());
                    assertEquals(operationId, f.control.peekActive().id);
                    assertFalse(f.control.peekActive().isTerminated());
                    assertFalse(f.controller.startVerify()); assertFalse(f.owner.submit(() -> {}));
                    assertEquals(operationId, f.state.current().activeOperationId);
                    f.counts(1); f.unpublished();
                } finally { f.provider.openRelease.countDown(); }
            }
            // Neither a cancel flag nor owner release is accepted as successful terminal cleanup.
            ModelManagementState.Snapshot cancelled = f.state.current();
            assertEquals(operationId, cancelled.activeOperationId);
            assertEquals(ModelManagementState.Phase.CANCELLED, cancelled.phase);
            assertEquals(ModelManagementState.Outcome.CANCELLED, cancelled.lastOutcome);
            assertEquals(ModelManagementState.Outcome.SUCCEEDED, cancelled.cleanupOutcome);
            f.counts(1); f.unpublished(); f.released();
        } finally {
            // Constructor failures start no worker; an unjoinable worker fails without deleting live IO.
            if (worker.stopped()) deleteSafRoot(root);
        }
    }
    private void onMain(Runnable action) throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Throwable t) { failure.set(t); }
        });
        if (failure.get() != null) throw failure.get();
    }
    public void testInputConnectionAndManualInvalidation() throws Throwable {
        Intent intent = new Intent(getInstrumentation().getTargetContext(), ImeHostActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ImeHostActivity activity = (ImeHostActivity) getInstrumentation().startActivitySync(intent);
        try {
            onMain(() -> {
                FakePorts.Queue queue = new FakePorts.Queue();
                FakePorts.Backend backend = new FakePorts.Backend();
                ImeController c = new ImeController(new TaskCoordinator(queue), backend, () -> {});
                EditText first = activity.field(0), second = activity.field(1), password = activity.field(2);
                first.requestFocus(); EditorInfo firstInfo = new EditorInfo();
                InputConnection firstConnection = first.onCreateInputConnection(firstInfo);
                FixtureChecks.require(firstConnection != null, "first InputConnection");
                ImeSession initial = c.begin(firstInfo.inputType, firstInfo.imeOptions, "first");
                FixtureChecks.require(c.start(initial, "English"), "input request admitted"); queue.drain();
                FixtureChecks.require(c.commit(initial, "first", firstInfo.inputType, firstInfo.imeOptions,
                    text -> firstConnection.commitText(text, 1)), "real InputConnection commit");
                FixtureChecks.require(!c.commit(initial, "first", firstInfo.inputType, firstInfo.imeOptions,
                    text -> firstConnection.commitText(text, 1)), "no second input commit");
                FixtureChecks.require(FakePorts.TEXT.contentEquals(first.getText()), "first field exact text");
                FixtureChecks.require(c.start(initial, "English"), "late input admitted");
                // Explicit manual invalidation; NOT an AsrImeService system callback test.
                c.invalidate(); second.requestFocus(); EditorInfo nextInfo = new EditorInfo();
                InputConnection nextConnection = second.onCreateInputConnection(nextInfo);
                FixtureChecks.require(nextConnection != null, "second InputConnection");
                ImeSession next = c.begin(nextInfo.inputType, nextInfo.imeOptions, "second");
                queue.drain();
                FixtureChecks.require(!c.commit(initial, "second", nextInfo.inputType, nextInfo.imeOptions,
                    text -> nextConnection.commitText(text, 1)) && next.preview().isEmpty(), "old result not routed");
                password.requestFocus(); EditorInfo passwordInfo = new EditorInfo();
                InputConnection passwordConnection = password.onCreateInputConnection(passwordInfo);
                FixtureChecks.require(passwordConnection != null, "password InputConnection");
                FixtureChecks.require(c.begin(passwordInfo.inputType, passwordInfo.imeOptions, "password") == null,
                    "real password metadata refused");
                FixtureChecks.require(second.getText().length() == 0 && password.getText().length() == 0,
                    "other fields untouched");
                c.invalidate(); // Intentional manual boundary before finish, not framework evidence.
            });
        } finally { onMain(activity::finish); }
    }
}
