package org.llmasr.minimal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.llmasr.minimal.diagnostics.RuntimeLogClock;
import org.llmasr.minimal.diagnostics.RuntimeLogEvent;
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogPersistence;
import org.llmasr.minimal.diagnostics.RuntimeLogSink;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;
import org.llmasr.minimal.asr.InferenceAdapter;
import org.llmasr.minimal.asr.InferencePhase;
import org.llmasr.minimal.asr.InferencePhaseListener;
import org.llmasr.minimal.asr.JniNativeTranscription;
import org.llmasr.minimal.asr.LoggingInferencePhaseListener;
import org.llmasr.minimal.asr.NativeResponse;
import org.llmasr.minimal.asr.NativeTranscription;
import org.llmasr.minimal.model.ModelAccess;
import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;
import org.llmasr.minimal.transcription.AppRequestPolicy;
import org.llmasr.minimal.diagnostics.RuntimeLogCodec;
import static org.llmasr.minimal.diagnostics.RuntimeLogEventKind.*;

/** Exercises the SAME pure Java orchestration used by actual App and IME.
 * No Android stubs or duplicate test adapter. Native is an injected port;
 * JNI source checks below are explicitly not runtime JNI validation. */
public final class InferenceAdapterTest {
    private static final String PRIVATE = "私密转写 alice@example.test content://private/secret.wav";
    private static final byte[] PAYLOAD = ("99.25 321.5 7\nlanguage Chinese<asr_text>" + PRIVATE)
        .getBytes(StandardCharsets.UTF_8);
    private static int groups, checks;
    private interface Checked { void run() throws Exception; }
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    private static void group(String name, Checked test) throws Exception {
        test.run(); groups++; System.out.println("PASS " + name);
    }
    private static final class Fixture {
        final RuntimeLogClock.Fake clock = new RuntimeLogClock.Fake(1_700_000_000_123L, 123_000L);
        final RuntimeLogStore store = new RuntimeLogStore(1000, clock);
        final List<InferencePhaseListener> callbacks = new ArrayList<>();
        int readiness, libraries, calls;
        InferenceAdapter.Readiness ready = () -> { readiness++; clock.advance(-1000, 2_000_000); };
        InferenceAdapter.Library library = () -> { libraries++; clock.advance(1, 3_000_000); };
        NativeTranscription bridge = (config, wav, language, cache, listener) -> {
            calls++; callbacks.add(listener);
            check(config.equals(PRIVATE) && wav.equals(PRIVATE) && language.equals("Chinese") && cache.equals(PRIVATE),
                "all inference arguments preserved");
            complete(listener); return PAYLOAD;
        };
        void complete(InferencePhaseListener listener) {
            listener.onPhase(1); clock.advance(-2000, 5_000_000);
            listener.onPhase(2); clock.advance(1, 7_000_000);
            listener.onPhase(3); clock.advance(1, 11_000_000);
            listener.onPhase(4);
        }
        NativeResponse run(RuntimeLogSource source, String id) throws IOException {
            return new InferenceAdapter(store, ready, library, bridge)
                .transcribe(source, id, PRIVATE, PRIVATE, "Chinese", PRIVATE);
        }
        NativeResponse run() throws IOException { return run(RuntimeLogSource.APP, UUID.randomUUID().toString()); }
    }
    private static List<RuntimeLogEventKind> kinds(RuntimeLogStore store) {
        List<RuntimeLogEventKind> result = new ArrayList<>();
        for (RuntimeLogEvent e : store.snapshot()) result.add(e.kind);
        return result;
    }
    private static long count(RuntimeLogStore store, RuntimeLogEventKind kind) {
        return store.snapshot().stream().filter(e -> e.kind == kind).count();
    }
    private static void sequence(RuntimeLogStore store, RuntimeLogEventKind... expected) {
        check(kinds(store).equals(Arrays.asList(expected)), "event sequence " + kinds(store));
    }
    private static void payload(NativeResponse response) {
        NativeResponse expected = NativeResponse.parse(PAYLOAD);
        check(response.raw.equals(expected.raw) && response.display.equals(expected.display), "private result preserved");
        check(response.loadSeconds == 99.25 && response.inferenceSeconds == 321.5 && response.generatedTokens == 7,
            "protocol numeric data unchanged");
    }
    private static void noSuccess(RuntimeLogStore store) {
        check(count(store, REQUEST_SUCCESS) == 0, "no request success");
    }
    private static void successAndTime() throws Exception {
        Fixture f = new Fixture();
        String app = UUID.randomUUID().toString(), ime = UUID.randomUUID().toString();
        payload(f.run(RuntimeLogSource.APP, app));
        sequence(f.store, MODEL_ACCESS_STARTED, MODEL_ACCESS_COMPLETED, MODEL_LOAD_STARTED,
            MODEL_LOAD_COMPLETED, INFERENCE_STARTED, INFERENCE_COMPLETED, REQUEST_SUCCESS);
        List<RuntimeLogEvent> first = f.store.snapshot();
        check(first.get(0).wallMillis == 1_700_000_000_123L, "actual initial wall timestamp");
        check(first.get(1).wallMillis < first.get(0).wallMillis, "wall rollback retained");
        check(first.get(1).elapsedNanos == 2_000_000 && first.get(3).elapsedNanos == 5_000_000
            && first.get(5).elapsedNanos == 11_000_000 && first.get(6).elapsedNanos == 28_000_000,
            "numeric monotonic durations not reconstructed from response metrics");
        check(first.get(2).monotonicNanos == 5_123_000 && first.get(3).monotonicNanos == 10_123_000,
            "phase timestamps from same store clock");
        check(first.get(3).formatLocal().contains("elapsed_ms=5.000000"), "numeric rendered duration");
        payload(f.run(RuntimeLogSource.IME, ime));
        check(!app.equals(ime) && f.readiness == 2 && f.libraries == 2 && f.calls == 2, "two independent requests");
        for (int i = 0; i < 14; i++) {
            RuntimeLogEvent e = f.store.snapshot().get(i);
            check(e.sequence == i && e.requestId.equals(i < 7 ? app : ime)
                && e.source == (i < 7 ? RuntimeLogSource.APP : RuntimeLogSource.IME), "source and task ID");
        }
        check(f.callbacks.get(0) != f.callbacks.get(1), "per-request callback, no global listener");
    }
    private static void sharedInstance() throws Exception {
        Fixture f = new Fixture();
        // Construction of the real bridge must not load JNI on this host.
        NativeTranscription unloaded = new JniNativeTranscription();
        check(unloaded instanceof NativeTranscription, "standalone JNI bridge constructs without library loading");
        InferenceAdapter shared = new InferenceAdapter(f.store, f.ready, f.library, f.bridge);
        check(f.readiness == 0 && f.libraries == 0 && f.calls == 0, "graph-style construction is lazy");
        String app = UUID.randomUUID().toString(), ime = UUID.randomUUID().toString();
        payload(shared.transcribe(RuntimeLogSource.APP, app, PRIVATE, PRIVATE, "Chinese", PRIVATE));
        payload(shared.transcribe(RuntimeLogSource.IME, ime, PRIVATE, PRIVATE, "Chinese", PRIVATE));
        check(f.readiness == 2 && f.libraries == 2 && f.calls == 2, "same adapter performs readiness/load each request");
        check(f.callbacks.get(0) != f.callbacks.get(1), "shared adapter retains no request listener");
        for (int i = 0; i < 14; i++) {
            RuntimeLogEvent e = f.store.snapshot().get(i);
            check(e.requestId.equals(i < 7 ? app : ime)
                && e.source == (i < 7 ? RuntimeLogSource.APP : RuntimeLogSource.IME), "shared instance keeps APP/IME request attribution");
        }
    }

    private static void realReadiness() throws Exception {
        Path root = Files.createTempDirectory("inference-model-");
        try {
            byte[] data = {1, 2, 3};
            StringBuilder hash = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) hash.append(String.format("%02x", b & 255));
            ModelManifest manifest = new ModelManifest(Collections.singletonList(new ModelEntry("model.bin", 3, hash.toString())));
            AtomicInteger shaRuns = new AtomicInteger(), boundaries = new AtomicInteger();
            ModelRepository repo = new ModelRepository(manifest, root.toFile(), () -> Long.MAX_VALUE) {
                @Override public void verifyAll(Progress progress, CancelGate gate) throws IOException {
                    shaRuns.incrementAndGet(); super.verifyAll(progress, gate);
                }
                @Override public void validateManagedBoundary(CancelGate gate) throws IOException {
                    boundaries.incrementAndGet(); super.validateManagedBoundary(gate);
                }
            };
            Files.write(root.resolve("model.bin"), data);
            ModelReadiness readiness = new ModelReadiness();
            ModelAccess access = new ModelAccess(repo, readiness);
            Fixture f = new Fixture(); f.ready = access::requireReady;
            payload(f.run()); payload(f.run(RuntimeLogSource.IME, UUID.randomUUID().toString()));
            check(readiness.isReady() && shaRuns.get() == 1 && boundaries.get() >= 2, "real cold SHA then cached access");
            check(count(f.store, MODEL_ACCESS_COMPLETED) == 2 && count(f.store, MODEL_LOAD_COMPLETED) == 2,
                "cached readiness still has real per-request memory load");
            check(count(f.store, MODEL_VERIFY_COMPLETED) == 0, "cached access never claims a SHA-only completion");
            readiness.invalidate(); Files.write(root.resolve("model.bin"), new byte[] {9, 9, 9});
            try { f.run(); throw new AssertionError("bad SHA accepted"); }
            catch (IOException expected) { }
            check(shaRuns.get() == 2 && f.calls == 2 && !readiness.isReady(), "verify failure never reaches native");
            check(count(f.store, MODEL_ACCESS_FAILED) == 1 && count(f.store, REQUEST_FAILURE) == 1, "verify failures logged");
        } finally { deleteTree(root); }
    }
    private static void failureIdentity() throws Exception {
        for (int stage = 0; stage < 3; stage++) {
            for (int type = 0; type < 4; type++) {
                Fixture f = new Fixture();
                Throwable original = type == 0 ? new IOException(PRIVATE) : type == 1 ? new IllegalStateException(PRIVATE)
                    : type == 2 ? new UnsatisfiedLinkError(PRIVATE) : new OutOfMemoryError(PRIVATE);
                if (stage == 0) f.ready = () -> { raise(original); };
                else if (stage == 1) f.library = () -> { InferenceAdapterTest.<RuntimeException>raise(original); };
                else f.bridge = (c, w, l, t, listener) -> { raise(original); return null; };
                try { f.run(); throw new AssertionError("failure accepted"); }
                catch (Throwable actual) { check(actual == original, "original failure identity incl checked JNI throwables/VM errors"); }
                noSuccess(f.store);
                check(count(f.store, REQUEST_FAILURE) == 1 && count(f.store, MODEL_LOAD_STARTED) == 0, "one real failure, no load");
                if (stage == 0) check(f.libraries == 0 && f.calls == 0, "readiness gates library/native");
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void raise(Throwable value) throws T { throw (T) value; }

    private static void resultFailures() throws Exception {
        byte[][] bad = {null, new byte[0], "missing".getBytes(StandardCharsets.UTF_8),
            (PRIVATE + " 1 1\ntext").getBytes(StandardCharsets.UTF_8),
            "NaN 1 1\ntext".getBytes(StandardCharsets.UTF_8), "1 1 129\ntext".getBytes(StandardCharsets.UTF_8),
            new byte[] {'1', ' ', '1', ' ', '1', '\n', (byte) 0xff}};
        for (byte[] bytes : bad) {
            Fixture f = new Fixture();
            f.bridge = (c, w, l, t, listener) -> { f.complete(listener); return bytes; };
            try { f.run(); throw new AssertionError("invalid bytes accepted"); }
            catch (IOException expected) {
                check(!expected.toString().contains(PRIVATE) && expected.getCause() == null, "fixed protocol exception");
            }
            sequence(f.store, MODEL_ACCESS_STARTED, MODEL_ACCESS_COMPLETED, MODEL_LOAD_STARTED,
                MODEL_LOAD_COMPLETED, INFERENCE_STARTED, INFERENCE_COMPLETED, REQUEST_FAILURE);
            check(f.store.snapshot().get(6).detail.equals(bytes == null ? "native-null" : "parse-failed"), "correct failure label");
        }
    }
    private static void phaseFailures() throws Exception {
        for (int boundary = 0; boundary <= 4; boundary++) {
            Fixture f = new Fixture(); final int emitted = boundary;
            RuntimeException original = new RuntimeException(PRIVATE);
            f.bridge = (c, w, l, t, listener) -> {
                for (int code = 1; code <= emitted; code++) { listener.onPhase(code); f.clock.advance(1, 123); }
                throw original;
            };
            try { f.run(); throw new AssertionError("native failure accepted"); }
            catch (RuntimeException actual) { check(actual == original, "native identity"); }
            noSuccess(f.store);
            check(count(f.store, REQUEST_FAILURE) == 1, "one terminal failure");
            check(count(f.store, MODEL_LOAD_FAILED) == (boundary == 1 ? 1 : 0), "load failure only during real load");
            check(count(f.store, INFERENCE_FAILED) == (boundary == 3 ? 1 : 0), "inference failure only during response");
            check(count(f.store, MODEL_LOAD_COMPLETED) == (boundary >= 2 ? 1 : 0), "no fabricated load completion");
            check(count(f.store, INFERENCE_COMPLETED) == (boundary == 4 ? 1 : 0), "no fabricated inference completion");
        }
    }
    private static void invalidCallbacks() throws Exception {
        int[][] invalid = {{}, {2, 1, 2, 3, 4}, {1, 1, 2, 3, 4}, {1, 2, 2, 3, 4},
            {1, 3, 2, 3, 4}, {1, 2, 3, 3, 4}, {1, 2, 3, 4, 4}, {1, 2, 3}, {99, 1, 2, 3, 4}, {0}, {-1}};
        for (int[] codes : invalid) {
            Fixture f = new Fixture(); AtomicReference<InferencePhaseListener> saved = new AtomicReference<>();
            f.bridge = (c, w, l, t, listener) -> { saved.set(listener); for (int code : codes) listener.onPhase(code); return PAYLOAD; };
            payload(f.run());
            noSuccess(f.store);
            check(count(f.store, REQUEST_FAILURE) == 0 && count(f.store, LOG_TELEMETRY_FAILED) == 1,
                "malformed telemetry is NOT inference failure; one telemetry failure");
            int before = f.store.size();
            for (int code = 1; code <= 4; code++) saved.get().onPhase(code);
            check(f.store.size() == before, "late success cannot revive invalid request");
        }
        Fixture f = new Fixture(); payload(f.run());
        InferencePhaseListener stale = f.callbacks.get(0);
        int before = f.store.size(); stale.onPhase(99); stale.onPhase(1);
        check(f.store.size() == before, "success terminal closes callback");
        f.bridge = (c, w, l, t, listener) -> { stale.onPhase(1); stale.onPhase(4); f.complete(listener); return PAYLOAD; };
        payload(f.run()); check(count(f.store, REQUEST_SUCCESS) == 2, "old request cannot affect new one");
        Fixture crossThread = new Fixture();
        crossThread.bridge = (c, w, l, t, listener) -> {
            Thread thread = new Thread(() -> listener.onPhase(1)); thread.start();
            try { thread.join(1000); } catch (InterruptedException e) { throw new IOException("test interrupted"); }
            check(!thread.isAlive(), "bounded callback thread"); crossThread.complete(listener); return PAYLOAD;
        };
        payload(crossThread.run()); noSuccess(crossThread.store);
        check(count(crossThread.store, LOG_TELEMETRY_FAILED) == 1, "wrong-thread callback rejected");
        Fixture failed = new Fixture(); AtomicReference<InferencePhaseListener> failedCallback = new AtomicReference<>();
        Fixture malformedFailure = new Fixture(); IOException actualFailure = new IOException(PRIVATE);
        malformedFailure.bridge = (c, w, l, t, listener) -> {
            listener.onPhase(2); listener.onPhase(1); throw actualFailure;
        };
        try { malformedFailure.run(); throw new AssertionError("expected real failure"); }
        catch (IOException actual) { check(actual == actualFailure, "telemetry failure cannot mask real failure"); }
        noSuccess(malformedFailure.store);
        check(count(malformedFailure.store, LOG_TELEMETRY_FAILED) == 1
            && count(malformedFailure.store, REQUEST_FAILURE) == 1, "real failure still logged after invalid callbacks");
        failed.bridge = (c, w, l, t, listener) -> { failedCallback.set(listener); listener.onPhase(1); throw new IOException(PRIVATE); };
        try { failed.run(); throw new AssertionError("expected failure"); } catch (IOException expected) { }
        before = failed.store.size(); for (int code = 1; code <= 4; code++) failedCallback.get().onPhase(code);
        check(failed.store.size() == before, "late completion after failed callback cannot mint success");
    }
    private static void loggingFaultsAndPrivacy() throws Exception {
        // Port simulation of JNI's new-Throwable isolation, NOT JNI execution.
        // Actual callback throw/pending-exception runtime testing remains next-layer.
        for (int at = 1; at <= 4; at++) {
            for (Throwable callbackError : new Throwable[] {
                    new IOException(PRIVATE), new IllegalStateException(PRIVATE), new AssertionError(PRIVATE)}) {
                Fixture callbackFault = new Fixture(); final int failureAt = at;
                callbackFault.bridge = (c, w, l, t, listener) -> {
                    for (int code = 1; code <= 4; code++) {
                        try {
                            if (code == failureAt) InferenceAdapterTest.<RuntimeException>raise(callbackError);
                            listener.onPhase(code);
                        } catch (Throwable loggingOnly) { break; }
                    }
                    return PAYLOAD;
                };
                payload(callbackFault.run()); noSuccess(callbackFault.store);
                check(count(callbackFault.store, LOG_TELEMETRY_FAILED) == 1
                    && count(callbackFault.store, REQUEST_FAILURE) == 0, "isolated callback throw leaves successful ASR but incomplete telemetry");
            }
        }
        Fixture runtimeSink = new Fixture();
        runtimeSink.store.addSink((events, dropped) -> { throw new IllegalStateException(PRIVATE); });
        payload(runtimeSink.run()); check(count(runtimeSink.store, REQUEST_SUCCESS) == 1, "core-isolated sink fault preserves complete sequence");
        Fixture errorSink = new Fixture();
        errorSink.store.addSink((events, dropped) -> { throw new AssertionError(PRIVATE); });
        payload(errorSink.run()); noSuccess(errorSink.store);
        check(count(errorSink.store, LOG_TELEMETRY_FAILED) == 1 && count(errorSink.store, REQUEST_FAILURE) == 0,
            "non-RuntimeException instrumentation faults isolated and marked");
        RuntimeLogClock broken = new RuntimeLogClock() {
            public long wallTimeMillis() { throw new AssertionError(PRIVATE); }
            public long monotonicNanos() { throw new IllegalStateException(PRIVATE); }
        };
        RuntimeLogStore unavailable = new RuntimeLogStore(100, broken);
        NativeTranscription good = (c, w, l, t, listener) -> { for (int code = 1; code <= 4; code++) listener.onPhase(code); return PAYLOAD; };
        payload(new InferenceAdapter(unavailable, () -> {}, () -> {}, good)
            .transcribe(RuntimeLogSource.APP, UUID.randomUUID().toString(), PRIVATE, PRIVATE, "Chinese", PRIVATE));
        check(unavailable.size() == 0, "store entirely unavailable does not change inference result");
        IOException nativeError = new IOException(PRIVATE);
        try {
            new InferenceAdapter(unavailable, () -> {}, () -> {}, (c, w, l, t, listener) -> { throw nativeError; })
                .transcribe(RuntimeLogSource.IME, UUID.randomUUID().toString(), PRIVATE, PRIVATE, "Chinese", PRIVATE);
            throw new AssertionError("expected error");
        } catch (IOException e) { check(e == nativeError, "logging errors cannot mask original native failure"); }
        payload(new InferenceAdapter(null, () -> {}, () -> {}, good)
            .transcribe(RuntimeLogSource.APP, UUID.randomUUID().toString(), PRIVATE, PRIVATE, "Chinese", PRIVATE));
        Fixture privacy = new Fixture(); payload(privacy.run());
        privacy.ready = () -> { throw new IOException(PRIVATE); };
        try { privacy.run(); throw new AssertionError("expected privacy failure"); } catch (IOException expected) { }
        Path root = Files.createTempDirectory("inference-logs-");
        try {
            RuntimeLogPersistence persistence = new RuntimeLogPersistence(root.toFile());
            check(persistence.save(privacy.store.snapshot(), new RuntimeLogPersistence.FailureSink.None()) == privacy.store.size(), "persist actual adapter events");
            List<RuntimeLogEvent> reopened = new RuntimeLogPersistence(root.toFile()).load();
            check(reopened.equals(privacy.store.snapshot()), "new vocabulary survives real persistence reopen");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            RuntimeLogCodec.writeSnapshot(reopened, out);
            String disk = new String(Files.readAllBytes(persistence.file().toPath()), StandardCharsets.UTF_8);
            String exported = new String(out.toByteArray(), StandardCharsets.UTF_8);
            for (String secret : new String[] {PRIVATE, "alice@", "content://", "secret.wav", "99.25", "321.5", "私密"}) {
                check(!disk.contains(secret) && !exported.contains(secret), "no payload/path/exception/metric text in persisted/exported logs");
            }
        } finally { deleteTree(root); }
    }
    private static void ownerBoundaries() throws Exception {
        TaskCoordinator owner = new TaskCoordinator(Runnable::run);
        AtomicInteger cleaned = new AtomicInteger(), reportFailures = new AtomicInteger();
        List<String> ids = new ArrayList<>(); Fixture f = new Fixture();
        AppRequestPolicy.State state = new AppRequestPolicy.State() {
            String text = "", status = "";
            public String lastText() { return text; } public void setLastText(String value) { text = value; }
            public String lastStatus() { return status; } public void setLastStatus(String value) { status = value; }
        };
        AppRequestPolicy.Reports reports = new AppRequestPolicy.Reports() {
            public void writePending(RequestContext c) { check(owner.isBusy(), "pending owns task"); }
            public void writeTerminal(RequestContext c) throws IOException { throw new IOException(PRIVATE); }
            public void writeFailure(RequestContext c, Throwable e) { reportFailures.incrementAndGet(); }
            public void writeCancel(RequestContext c) { throw new AssertionError("unexpected cancel"); }
        };
        RequestRunner runner = new RequestRunner(owner, () -> 0, new AppRequestPolicy(reports, state),
            c -> { check(owner.isBusy(), "cleanup retains ownership"); cleaned.incrementAndGet(); });
        for (RuntimeLogSource source : new RuntimeLogSource[] {RuntimeLogSource.APP, RuntimeLogSource.IME}) {
            check(runner.submit(TaskKind.INFERENCE, ctx -> {
                check(owner.isBusy() && !owner.submit(() -> {}), "adapter caller holds exclusive owner");
                ids.add(ctx.requestId); payload(f.run(source, ctx.requestId));
                check(count(f.store, REQUEST_SUCCESS) == ids.size(), "adapter success before external report/IME commit");
            }), "accepted request");
            check(!owner.isBusy(), "external report failure releases owner");
        }
        check(!ids.get(0).equals(ids.get(1)) && cleaned.get() == 2 && reportFailures.get() == 2, "real runner UUID and cleanup boundaries");
        check(count(f.store, REQUEST_SUCCESS) == 2 && count(f.store, REQUEST_FAILURE) == 0,
            "external failure is NOT rewritten as inference adapter failure");
    }
    private static void sourceContracts() throws Exception {
        java.util.Map<String, String> callers = new java.util.LinkedHashMap<>();
        callers.put("AsrOperation", "transcription");
        callers.put("ImeBackend", "ime");
        for (java.util.Map.Entry<String, String> e : callers.entrySet()) {
            String caller = e.getKey();
            String sub = e.getValue();
            String s = read(java.nio.file.Paths.get("android/app/src/org/llmasr/minimal/" + sub + "/" + caller + ".java"));
            check(!s.contains("new InferenceAdapter(") && s.contains("inference.transcribe(RuntimeLogSource."), "actual " + caller + " uses shared production adapter");
            check(s.contains("this.inference = inference;") && !s.contains("MainActivity"), "graph-injected inference port " + caller);
            check(!s.contains("NativeResponse.parse") && !s.contains("onTerminal") && !s.contains("new LoggingInferencePhaseListener"), "no duplicate caller orchestration " + caller);
        }
        String cpp = read(java.nio.file.Paths.get("native/apk/asr_jni.cpp"));
        String notifier = cpp.substring(cpp.indexOf("struct PhaseNotifier"), cpp.indexOf("void throwFailure"));
        check(notifier.contains("env->ExceptionCheck() || listener == nullptr") && notifier.contains("env->ExceptionCheck() || !onPhase"), "original pending exceptions bypass all JNI logging");
        check(notifier.contains("GetMethodID(cls, \"onPhase\", \"(I)V\")") && notifier.contains("DeleteLocalRef(cls)"), "runtime method signature and class ref cleanup");
        check(!cpp.contains("VALUES") && !cpp.contains("onTerminal") && !cpp.contains("notifier.terminal"), "no reflection/early terminal");
        check(notifier.contains("onPhase = nullptr; // no later callbacks"), "throwing callback disables future success sequence");
        ordered(cpp, "notifier.phase(PHASE_MODEL_LOAD_STARTED)", "Llm::createLLM(c)", "llm->set_config(settings)",
            "llm->load()", "notifier.phase(PHASE_MODEL_LOAD_COMPLETED)", "notifier.phase(PHASE_INFERENCE_STARTED)",
            "llm->response(", "ctx->status!=LlmStatus::NORMAL_FINISHED", "notifier.phase(PHASE_INFERENCE_COMPLETED)", "env->NewByteArray(");
        check(cpp.contains("if (!out || env->ExceptionCheck()) return nullptr;")
            && cpp.contains("if (env->ExceptionCheck()) { env->DeleteLocalRef(out); return nullptr; }"), "allocation/copy pending exception retained");
        check(cpp.contains("std::lock_guard<std::mutex> lock(engine_mutex)") && cpp.contains("std::unique_ptr<Llm> llm"), "mutex and per-request RAII preserved");
        String settings = cpp.substring(cpp.indexOf("const std::string settings="), cpp.indexOf("if (!llm->set_config"));
        check(settings.contains("\\\"async\\\":false,\\\"sampler_type\\\":\\\"greedy\\\",\\\"max_new_tokens\\\":128,")
            && settings.contains("\\\"backend_type\\\":\\\"cpu\\\",\\\"thread_num\\\":2,\\\"tmp_path\\\":\\\"")
            && settings.contains("\\\"asr_language\\\":\\\"") && settings.contains("(lang==\"auto\"?\"\":lang)"),
            "native settings/max tokens/CPU preserved");
        check(cpp.contains("llm->response(\"<audio>\"+a+\"</audio>\",&raw)")
            && cpp.contains("ctx->gen_seq_len << \"\\n\" << raw.str()"), "input and result protocol unchanged");
        check(cpp.indexOf("Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener(JNIEnv*,")
            < cpp.indexOf("Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribe(JNIEnv*"), "forward declaration before wrapper");
        for (InferencePhase phase : InferencePhase.values()) {
            check(cpp.contains("PHASE_" + phase.name() + " = " + phase.code + ";"), "explicit ABI matches " + phase);
        }
    }
    private static void ordered(String source, String... parts) {
        int at = -1;
        for (String part : parts) { int next = source.indexOf(part, at + 1); check(next > at, "native boundary order " + part); at = next; }
    }
    private static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static void deleteTree(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }
    public static void main(String[] args) throws Exception {
        group("App/IME validated result, task IDs, phase timestamps and monotonic durations", InferenceAdapterTest::successAndTime);
        group("one shared adapter, lazy JNI construction and per-request APP/IME listeners", InferenceAdapterTest::sharedInstance);
        group("real ModelAccess cold SHA/cached readiness and verify failure", InferenceAdapterTest::realReadiness);
        group("readiness/library/native IOException RuntimeException LinkageError VM error identity", InferenceAdapterTest::failureIdentity);
        group("null and malformed native protocol never request success", InferenceAdapterTest::resultFailures);
        group("load/inference failure boundaries", InferenceAdapterTest::phaseFailures);
        group("missing duplicate out-of-order stale wrong-thread callbacks fail telemetry closed", InferenceAdapterTest::invalidCallbacks);
        group("listener/store Throwable isolation and persisted/exported privacy", InferenceAdapterTest::loggingFaultsAndPrivacy);
        group("production RequestRunner owner/cleanup and external commit boundary", InferenceAdapterTest::ownerBoundaries);
        group("actual App/IME wiring and real JNI source contracts (not JNI runtime)", InferenceAdapterTest::sourceContracts);
        System.out.println("PASS InferenceAdapterTest: " + groups + " groups, " + checks + " checks");
    }
}
