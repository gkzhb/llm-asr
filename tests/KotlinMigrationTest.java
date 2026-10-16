import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import org.llmasr.minimal.audio.AsrText;
import org.llmasr.minimal.audio.PcmWave;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.asr.NativeResponse;
import org.llmasr.minimal.task.*;
import org.llmasr.minimal.transcription.AppState;
import org.llmasr.minimal.ime.ImeFieldPolicy;
import java.util.concurrent.Executor;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.llmasr.minimal.diagnostics.*;

/** Java-caller ABI and subtle Java/Kotlin behavior preservation, no Android runtime. */
public final class KotlinMigrationTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Kotlin migration check " + checks);
    }
    private static void require(boolean condition, String diagnostic) {
        checks++;
        if (!condition) throw new AssertionError(diagnostic);
    }
    private interface Action { void run() throws Exception; }
    private static void rejectsExactly(Action action, Class<? extends Throwable> type, String diagnostic) {
        Throwable observed = null;
        try { action.run(); } catch (Throwable failure) { observed = failure; }
        require(observed != null && observed.getClass() == type, diagnostic + ": " + observed);
    }
    private static final String[] SOURCE_NAMES = {"APP", "IME", "SHARED"};
    private static final String[] KIND_NAMES = {
        "APP_STARTUP", "APP_READY", "MODEL_VERIFY_STARTED", "MODEL_VERIFY_COMPLETED",
        "MODEL_VERIFY_FAILED", "MODEL_VERIFY_CANCELLED", "MODEL_ACCESS_STARTED",
        "MODEL_ACCESS_COMPLETED", "MODEL_ACCESS_FAILED", "LOG_TELEMETRY_FAILED",
        "MODEL_LOAD_STARTED", "MODEL_LOAD_COMPLETED", "MODEL_LOAD_FAILED", "INFERENCE_STARTED",
        "INFERENCE_COMPLETED", "INFERENCE_FAILED", "REQUEST_SUCCESS", "REQUEST_FAILURE",
        "MODEL_IMPORT_STARTED", "MODEL_IMPORT_COMPLETED", "MODEL_IMPORT_FAILED", "MODEL_IMPORT_CANCELLED",
        "MODEL_DELETE_STARTED", "MODEL_DELETE_COMPLETED", "MODEL_DELETE_FAILED", "MODEL_DELETE_CANCELLED",
        "LOG_PERSISTENCE_FAILED", "LOG_EXPORT_COMPLETED", "LOG_EXPORT_FAILED"
    };
    private static int sourceSwitch(RuntimeLogSource source) {
        switch (source) {
            case APP: return 0;
            case IME: return 1;
            case SHARED: return 2;
            default: throw new AssertionError("unexpected source switch value");
        }
    }
    private static int kindSwitch(RuntimeLogEventKind kind) {
        switch (kind) {
            case APP_STARTUP: return 0;
            case APP_READY: return 1;
            case MODEL_VERIFY_STARTED: return 2;
            case MODEL_VERIFY_COMPLETED: return 3;
            case MODEL_VERIFY_FAILED: return 4;
            case MODEL_VERIFY_CANCELLED: return 5;
            case MODEL_ACCESS_STARTED: return 6;
            case MODEL_ACCESS_COMPLETED: return 7;
            case MODEL_ACCESS_FAILED: return 8;
            case LOG_TELEMETRY_FAILED: return 9;
            case MODEL_LOAD_STARTED: return 10;
            case MODEL_LOAD_COMPLETED: return 11;
            case MODEL_LOAD_FAILED: return 12;
            case INFERENCE_STARTED: return 13;
            case INFERENCE_COMPLETED: return 14;
            case INFERENCE_FAILED: return 15;
            case REQUEST_SUCCESS: return 16;
            case REQUEST_FAILURE: return 17;
            case MODEL_IMPORT_STARTED: return 18;
            case MODEL_IMPORT_COMPLETED: return 19;
            case MODEL_IMPORT_FAILED: return 20;
            case MODEL_IMPORT_CANCELLED: return 21;
            case MODEL_DELETE_STARTED: return 22;
            case MODEL_DELETE_COMPLETED: return 23;
            case MODEL_DELETE_FAILED: return 24;
            case MODEL_DELETE_CANCELLED: return 25;
            case LOG_PERSISTENCE_FAILED: return 26;
            case LOG_EXPORT_COMPLETED: return 27;
            case LOG_EXPORT_FAILED: return 28;
            default: throw new AssertionError("unexpected kind switch value");
        }
    }
    private static <E extends Enum<E>> void enumAbi(Class<E> type, E[] values, String[] names) throws Exception {
        String label = type.getSimpleName();
        require(type.isEnum() && type.getSuperclass() == Enum.class
            && Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()), label + " enum ABI");
        require(Arrays.equals(Arrays.stream(values).map(Enum::name).toArray(String[]::new), names),
            label + " inventory/order mismatch");
        require(Arrays.equals(values, type.getEnumConstants()), label + " reflected values mismatch");
        require(Modifier.isStatic(type.getMethod("values").getModifiers())
            && type.getMethod("values").getReturnType() == values.getClass(), label + " values ABI");
        require(Modifier.isStatic(type.getMethod("valueOf", String.class).getModifiers())
            && type.getMethod("valueOf", String.class).getReturnType() == type, label + " valueOf ABI");
        for (int i = 0; i < names.length; i++) {
            E value = values[i];
            int flags = type.getField(names[i]).getModifiers();
            require(Modifier.isPublic(flags) && Modifier.isStatic(flags) && Modifier.isFinal(flags)
                && type.getField(names[i]).get(null) == value, label + " constant field ABI " + names[i]);
            require(value.ordinal() == i && value.toString().equals(names[i]) && value.getDeclaringClass() == type,
                label + " ordinal/toString " + names[i]);
            require(Enum.valueOf(type, names[i]) == value, label + " Enum.valueOf " + names[i]);
            if (i > 0) require(values[i - 1].compareTo(value) < 0, label + " compareTo order");
        }
    }
    private static void runtimeLogEnums() throws Exception {
        enumAbi(RuntimeLogSource.class, RuntimeLogSource.values(), SOURCE_NAMES);
        enumAbi(RuntimeLogEventKind.class, RuntimeLogEventKind.values(), KIND_NAMES);
        for (int i = 0; i < SOURCE_NAMES.length; i++) {
            RuntimeLogSource value = RuntimeLogSource.valueOf(SOURCE_NAMES[i]);
            require(sourceSwitch(value) == i, "RuntimeLogSource Java switch " + SOURCE_NAMES[i]);
        }
        for (int i = 0; i < KIND_NAMES.length; i++) {
            RuntimeLogEventKind value = RuntimeLogEventKind.valueOf(KIND_NAMES[i]);
            require(kindSwitch(value) == i, "RuntimeLogEventKind Java switch " + KIND_NAMES[i]);
        }
        RuntimeLogSource[] sources = RuntimeLogSource.values(); sources[0] = null;
        RuntimeLogEventKind[] kinds = RuntimeLogEventKind.values(); kinds[0] = null;
        require(RuntimeLogSource.values()[0] == RuntimeLogSource.APP, "source values must be a defensive array");
        require(RuntimeLogEventKind.values()[0] == RuntimeLogEventKind.APP_STARTUP, "kind values must be a defensive array");
        for (String bad : new String[]{"", "app", "APP ", "UNKNOWN"})
            rejectsExactly(() -> RuntimeLogSource.valueOf(bad), IllegalArgumentException.class, "source invalid name");
        for (String bad : new String[]{"", "app_startup", "APP_STARTUP ", "UNKNOWN"})
            rejectsExactly(() -> RuntimeLogEventKind.valueOf(bad), IllegalArgumentException.class, "kind invalid name");
        rejectsExactly(() -> RuntimeLogSource.valueOf(null), NullPointerException.class, "source null valueOf");
        rejectsExactly(() -> RuntimeLogEventKind.valueOf(null), NullPointerException.class, "kind null valueOf");
        rejectsExactly(() -> sourceSwitch(null), NullPointerException.class, "source null switch");
        rejectsExactly(() -> kindSwitch(null), NullPointerException.class, "kind null switch");
        runtimeLogPersistence();
        System.out.println("PASS runtime log enums Java ABI/order/switch/errors and production persistence/codec");
    }
    private static void runtimeLogPersistence() throws Exception {
        Path root = Files.createTempDirectory("enum-log-compat-");
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            RuntimeLogPersistence persistence = new RuntimeLogPersistence(root.toFile());
            // Frozen pre-migration V1 fixture, consumed by the real parser and writer.
            String legacy = "RUNTIME_LOG_V1\t2\n"
                + "0\t0\t10\t-1\tAPP_STARTUP\tAPP\t-\t-\n"
                + "1\t0\t11\t1000001\tAPP_READY\tIME\t-\tok\nEND\n";
            Files.write(persistence.file().toPath(), legacy.getBytes(StandardCharsets.UTF_8));
            List<RuntimeLogEvent> loaded = persistence.load();
            require(persistence.status() == RuntimeLogPersistence.Status.OK && loaded.size() == 2,
                "legacy enum V1 fixture must parse");
            require(loaded.get(0).kind == RuntimeLogEventKind.APP_STARTUP && loaded.get(0).source == RuntimeLogSource.APP
                && loaded.get(1).kind == RuntimeLogEventKind.APP_READY && loaded.get(1).source == RuntimeLogSource.IME,
                "legacy enum V1 names must retain identity");
            require(persistence.save(loaded, null) == 2
                && Arrays.equals(Files.readAllBytes(persistence.file().toPath()), legacy.getBytes(StandardCharsets.UTF_8)),
                "legacy enum V1 bytes must roundtrip unchanged");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String display = "1970-01-01 00:00:00.000 Z [APP] APP_STARTUP\n"
                + "1970-01-01 00:00:00.000 Z [IME] APP_READY detail=ok elapsed_ms=1.000001\n";
            require(RuntimeLogCodec.writeSnapshot(loaded, out) == display.getBytes(StandardCharsets.UTF_8).length
                && Arrays.equals(out.toByteArray(), display.getBytes(StandardCharsets.UTF_8)),
                "legacy enum production codec exact UTF8 display");
            List<RuntimeLogEvent> all = new ArrayList<>();
            for (String source : SOURCE_NAMES) for (String kind : KIND_NAMES) {
                RuntimeLogEvent event = new RuntimeLogEvent(all.size(), 0, 0,
                    RuntimeLogEventKind.valueOf(kind), RuntimeLogSource.valueOf(source), null, null);
                all.add(event);
                require(RuntimeLogCodec.formatLine(event).equals("1970-01-01 00:00:00.000 Z [" + source + "] " + kind),
                    "production display vocabulary " + source + "/" + kind);
            }
            require(persistence.save(all, null) == all.size(), "all enum names production save");
            RuntimeLogPersistence reopened = new RuntimeLogPersistence(root.toFile());
            require(reopened.load().equals(all) && reopened.status() == RuntimeLogPersistence.Status.OK,
                "all enum names production reopen");
            String saved = new String(Files.readAllBytes(persistence.file().toPath()), StandardCharsets.UTF_8);
            require(saved.contains("\tLOG_EXPORT_FAILED\tSHARED\t"), "production name encoding not ordinal");
            for (String invalid : new String[]{legacy.replace("APP_STARTUP", "app_startup"),
                    legacy.replace("\tIME\t", "\time\t"), legacy.replace("APP_READY", "UNKNOWN")}) {
                Files.write(persistence.file().toPath(), invalid.getBytes(StandardCharsets.UTF_8));
                require(persistence.load().isEmpty() && persistence.status() == RuntimeLogPersistence.Status.CORRUPT,
                    "production parser rejects noncanonical enum name");
            }
        } finally {
            TimeZone.setDefault(original);
            Files.deleteIfExists(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part"));
            Files.deleteIfExists(root.resolve(RuntimeLogPersistence.FILE_NAME));
            Files.deleteIfExists(root);
        }
    }
    public static void main(String[] args) throws Exception {
        runtimeLogEnums();
        check(AsrText.display("\u0000\t<asr_text> hello <|im_end|>\r\n").equals("hello"));
        check(AsrText.display("\u2003text\u2003").equals("\u2003text\u2003"));
        check(Modifier.isStatic(AsrText.class.getMethod("display", String.class).getModifiers()));
        check(Modifier.isStatic(NativeResponse.class.getMethod("parse", byte[].class).getModifiers()));
        check(Modifier.isFinal(NativeResponse.class.getField("raw").getModifiers()));
        NativeResponse response = NativeResponse.parse("0 1 2   \n<asr_text>ok".getBytes(StandardCharsets.UTF_8));
        check(response.generatedTokens == 2 && response.loadSeconds == 0 && response.display.equals("ok"));
        try { NativeResponse.parse(null); throw new AssertionError("null accepted"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().equals("Native returned empty")); }
        PcmWave pcm = new PcmWave();
        short[] values = new short[PcmWave.MIN_SAMPLES];
        values[0] = -1; values[1] = Short.MIN_VALUE;
        check(pcm.append(values, values.length) == values.length);
        byte[] wav = pcm.finish();
        check(wav[44] == -1 && wav[45] == -1 && wav[46] == 0 && wav[47] == -128);
        check(PcmWave.RATE == 16000 && PcmWave.MAX_SAMPLES == 480000);
        try { pcm.append(null, -1); throw new AssertionError("negative length accepted"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().equals("Invalid PCM length")); }
        String[] methods = {"start", "stop", "cancel", "captureReleased", "tryCommitInference", "stopped", "cancelled", "released", "committed"};
        for (String name : methods) check(Modifier.isSynchronized(RecordingControl.class.getMethod(name,
            name.equals("start") ? new Class<?>[]{Runnable.class} : new Class<?>[0]).getModifiers()));
        RecordingControl cancelled = new RecordingControl();
        cancelled.cancel();
        check(!cancelled.start(null)); // Original Java checks eligibility before touching backend.
        RecordingControl failed = new RecordingControl();
        Error fatal = new AssertionError("backend");
        Throwable observed = null;
        try { failed.start(() -> { throw fatal; }); } catch (Throwable t) { observed = t; }
        check(observed == fatal);
        check(failed.start(() -> {})); // Failed backend must not mark started.
        RequestContext nullable = new RequestContext(null, null);
        check(nullable.requestId == null && nullable.kind == null && nullable.inputWav == null);
        nullable.cleanedTemporary = 9; nullable.inferenceReported = true;
        check(nullable.cleanedTemporary == 9 && nullable.inferenceReported);
        check(TaskKind.valueOf("INFERENCE") == TaskKind.INFERENCE && TaskKind.values().length == 3);
        String[] hooks = {"admitted", "busy", "rejected", "beforeBody", "succeeded", "cancelled"};
        for (String hook : hooks) check(RequestRunner.Lifecycle.class.getMethod(hook, RequestContext.class).isDefault());
        for (String hook : new String[]{"failed", "cleanupFailed"})
            check(RequestRunner.Lifecycle.class.getMethod(hook, RequestContext.class, Throwable.class).isDefault());
        RequestRunner.Lifecycle defaults = new RequestRunner.Lifecycle() {};
        TaskCoordinator direct = new TaskCoordinator(Runnable::run);
        RequestRunner runner = new RequestRunner(direct, () -> 7, defaults, ctx -> check(ctx.cleanedTemporary == 7));
        check(runner.submit(TaskKind.MAINTENANCE, ctx -> check(direct.isBusy())));
        check(!direct.isBusy());
        Throwable badPort = null;
        try { new TaskCoordinator((Executor) null); } catch (Throwable e) { badPort = e; }
        check(badPort instanceof IllegalArgumentException && badPort.getMessage().equals("executor required"));
        badPort = null;
        try { direct.submit(null); } catch (Throwable e) { badPort = e; }
        check(badPort instanceof IllegalArgumentException && badPort.getMessage().equals("task/admission/abandon required"));
        AppState state = new AppState();
        state.setLastText(null); state.setLastStatus(null);
        check(state.lastText().isEmpty() && state.lastStatus().isEmpty());
        final class EqualListener implements AppState.Listener {
            int calls;
            public void onChange() { calls++; }
            public boolean equals(Object ignored) { return true; }
            public int hashCode() { return 1; }
        }
        EqualListener first = new EqualListener(), second = new EqualListener();
        state.addListener(first); state.addListener(second); state.removeListener(first); state.notifyChange();
        check(first.calls == 0 && second.calls == 1); // Must compare identity, not Kotlin == equality.
        check(ImeFieldPolicy.isSensitive(0x81, 0) && !ImeFieldPolicy.isSensitive(1, 0));
        System.out.println("PASS Kotlin migration Java ABI/behavior: " + checks + " checks");
    }
}
