package org.llmasr.minimal.testhost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.llmasr.minimal.asr.InferenceAdapter;
import org.llmasr.minimal.asr.NativeResponse;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.ime.ImeController;
import org.llmasr.minimal.ime.ImeSession;
import org.llmasr.minimal.task.TaskCoordinator;

/** Shared assertions call production objects, not a copied state machine or parser. */
public final class FixtureChecks {
    private FixtureChecks() {}
    public static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static NativeResponse run(FakePorts.Engine f) throws IOException {
        return new InferenceAdapter(null, f.readiness, f.library, f.nativePort)
            .transcribe(RuntimeLogSource.IME, "fixture-request", "synthetic-config",
                "synthetic-input", "English", "synthetic-cache");
    }
    public static void enginePayloadAndOrder() throws Exception {
        FakePorts.Engine f = new FakePorts.Engine();
        NativeResponse result = run(f);
        require(FakePorts.TEXT.equals(result.display), "engine payload display");
        require(result.loadSeconds == 1.25 && result.inferenceSeconds == 2.5
            && result.generatedTokens == 3, "engine metrics");
        require(f.order.equals(Arrays.asList("ready", "load", "native"))
            && f.loads == 1 && f.nativeCalls == 1, "engine call order");
    }
    public static void readinessRejectsBeforeLoad() throws Exception {
        FakePorts.Engine f = new FakePorts.Engine(); f.failReadiness = true;
        try { run(f); throw new AssertionError("readiness accepted"); }
        catch (IOException expected) {
            require("synthetic readiness rejection".equals(expected.getMessage()), "readiness identity");
        }
        require(f.loads == 0 && f.nativeCalls == 0 && f.order.equals(Arrays.asList("ready")),
            "readiness zero load/native");
    }
    public static void badBytesAreSafe() throws Exception {
        for (byte[] bad : new byte[][] {"synthetic-private-canary".getBytes(StandardCharsets.UTF_8),
                new byte[] {'1', ' ', '1', ' ', '1', '\n', (byte) 255}}) {
            FakePorts.Engine f = new FakePorts.Engine(); f.payload = bad;
            try { run(f); throw new AssertionError("bad bytes accepted"); }
            catch (IOException expected) {
                require("Invalid native response protocol".equals(expected.getMessage())
                    && expected.getCause() == null && expected.getSuppressed().length == 0,
                    "bad bytes fixed safe exception");
            }
        }
    }
    public static void singleConfirmationAndPassword() {
        FakePorts.Queue queue = new FakePorts.Queue(); FakePorts.Backend b = new FakePorts.Backend();
        TaskCoordinator owner = new TaskCoordinator(queue);
        ImeController c = new ImeController(owner, b, () -> {});
        ImeSession s = c.begin(1, 0, "first");
        require(c.start(s, "English"), "IME admitted"); queue.drain();
        AtomicInteger commits = new AtomicInteger();
        require(c.commit(s, "first", 1, 0, text -> {
            require(FakePorts.TEXT.equals(text), "commit payload"); commits.incrementAndGet(); return true;
        }), "first confirmation");
        require(!c.commit(s, "first", 1, 0, text -> { commits.incrementAndGet(); return true; })
            && commits.get() == 1, "single confirmation only");
        require(c.begin(0x81, 0, "password") == null, "password begin refused");
        require(b.captures == 1 && b.inferences == 1 && b.cleanups == 1
            && !owner.isBusy() && b.control.released(), "memory-only backend complete");
    }
    public static void lateResultIsInvalidated() {
        FakePorts.Queue queue = new FakePorts.Queue(); FakePorts.Backend b = new FakePorts.Backend();
        TaskCoordinator owner = new TaskCoordinator(queue);
        ImeController c = new ImeController(owner, b, () -> {});
        ImeSession old = c.begin(1, 0, "first"); ImeSession[] fresh = new ImeSession[1];
        b.duringInference = () -> {
            c.invalidate(); fresh[0] = c.begin(1, 0, "second");
            require(owner.isBusy() && !c.start(fresh[0], "English"), "late request retains owner");
        };
        require(c.start(old, "English"), "late request admitted"); queue.drain();
        require(!old.valid() && old.preview().isEmpty() && fresh[0].preview().isEmpty(),
            "late result discarded");
        require(!c.commit(old, "first", 1, 0, text -> true), "stale commit refused");
    }
    public static void reusedBackendQueuedCancellation() {
        FakePorts.Queue queue = new FakePorts.Queue(); FakePorts.Backend b = new FakePorts.Backend();
        TaskCoordinator owner = new TaskCoordinator(queue);
        ImeController c = new ImeController(owner, b, () -> {});
        ImeSession first = c.begin(1, 0, "first");
        require(c.start(first, "English"), "reuse first admitted"); queue.drain();
        require(b.captures == 1 && b.inferences == 1 && b.cleanups == 1
            && !owner.isBusy() && FakePorts.TEXT.equals(first.preview()), "reuse first completed");
        require(c.commit(first, "first", 1, 0, text -> FakePorts.TEXT.equals(text)), "reuse first committed");
        require(c.start(first, "English"), "reuse second queued");
        c.invalidate(); ImeSession next = c.begin(1, 0, "second");
        require(owner.isBusy() && !c.start(next, "English"), "queued cancel retains owner");
        queue.drain();
        require(b.captures == 1 && b.inferences == 1 && b.cleanups == 2
            && !owner.isBusy() && !c.ownsTask(), "queued cancel cleans without capture or inference");
        require(!first.valid() && first.preview().isEmpty() && next.preview().isEmpty(),
            "queued cancel leaves no late preview");
        require(!c.commit(first, "first", 1, 0, text -> true), "queued cancel cannot commit old session");
        require(c.start(next, "English"), "reuse subsequent request admitted"); queue.drain();
        require(b.captures == 2 && b.inferences == 2 && b.cleanups == 3
            && !owner.isBusy() && FakePorts.TEXT.equals(next.preview()), "reuse subsequent request completes");
    }
    public static void cleanupRetainsOwner() throws Exception {
        FakePorts.Backend b = new FakePorts.Backend(); b.blockCleanup = true;
        try (FakePorts.Worker worker = new FakePorts.Worker()) {
            TaskCoordinator owner = new TaskCoordinator(worker);
            ImeController c = new ImeController(owner, b, () -> {});
            ImeSession s = c.begin(1, 0, "cleanup");
            try {
                require(c.start(s, "English"), "cleanup request admitted");
                FakePorts.await(b.cleanupEntered, "cleanup entered");
                require(!b.cleanupCompleted, "cleanup must wait for release");
                require(owner.isBusy() && !owner.submit(() -> {}), "cleanup retains exclusive owner");
                c.invalidate();
                require(owner.isBusy(), "invalidate cannot release cleanup owner");
            } finally { b.cleanupRelease.countDown(); }
            owner.awaitFree();
            require(b.cleanupCompleted && b.cleanups == 1 && !owner.isBusy(), "cleanup complete then free");
        }
    }
}
