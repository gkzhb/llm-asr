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
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.llmasr.minimal.ime.ImeController;
import org.llmasr.minimal.ime.ImeSession;
import org.llmasr.minimal.model.ModelProviderBoundary;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.model.SafModelSource;
import org.llmasr.minimal.task.TaskCoordinator;

/** SDK JUnit3 naming is intentional. All 12 public test methods require future device discovery.
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
