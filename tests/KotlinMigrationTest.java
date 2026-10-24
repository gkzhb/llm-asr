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
import java.io.IOException;
import java.util.Collections;
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
    private static void portMethod(Class<?> owner, String name, Class<?> result, boolean abstractMethod,
                                   boolean defaultMethod, Class<?>... arguments) throws Exception {
        java.lang.reflect.Method method = owner.getDeclaredMethod(name, arguments);
        int flags = method.getModifiers();
        require(Modifier.isPublic(flags) && !Modifier.isStatic(flags) && !Modifier.isFinal(flags)
            && Modifier.isAbstract(flags) == abstractMethod && method.isDefault() == defaultMethod
            && method.getReturnType() == result && method.getExceptionTypes().length == 0,
            owner.getSimpleName() + "." + name + " Java method ABI");
    }
    private static void portClass(Class<?> type, Class<?> enclosing, Class<?> port, int methods,
                                  Class<?>[]... constructors) throws Exception {
        int flags = type.getModifiers();
        require(Modifier.isPublic(flags) && Modifier.isStatic(flags) && Modifier.isFinal(flags)
            && !Modifier.isAbstract(flags) && type.getEnclosingClass() == enclosing
            && type.getSuperclass() == Object.class && Arrays.equals(type.getInterfaces(), new Class<?>[]{port})
            && type.getDeclaredMethods().length == methods, type.getName() + " nested class ABI");
        require(type.getDeclaredConstructors().length == constructors.length, type.getName() + " constructor inventory");
        for (Class<?>[] parameters : constructors)
            require(Modifier.isPublic(type.getDeclaredConstructor(parameters).getModifiers()), type.getName() + " public constructor");
    }
    private static void clockAdvanceRejected(RuntimeLogClock clock) {
        Throwable observed = null;
        try { clock.advance(Long.MIN_VALUE, Long.MAX_VALUE); } catch (Throwable failure) { observed = failure; }
        require(observed != null && observed.getClass() == UnsupportedOperationException.class
            && "clock does not support advance".equals(observed.getMessage()),
            "RuntimeLogClock default advance exception/message");
    }
    private static void noneIgnores(RuntimeLogSink sink, List<RuntimeLogEvent> snapshot, int dropped) {
        Throwable observed = null;
        try { sink.onSnapshot(snapshot, dropped); } catch (Throwable failure) { observed = failure; }
        require(observed == null, snapshot == null ? "RuntimeLogSink.None must ignore null snapshot"
            : "RuntimeLogSink.None must ignore snapshot contents");
    }
    private static void runtimeLogPorts() throws Exception {
        require(RuntimeLogClock.class.isInterface() && Modifier.isPublic(RuntimeLogClock.class.getModifiers())
            && RuntimeLogClock.class.getDeclaredMethods().length == 3, "RuntimeLogClock interface ABI");
        portMethod(RuntimeLogClock.class, "wallTimeMillis", long.class, true, false);
        portMethod(RuntimeLogClock.class, "monotonicNanos", long.class, true, false);
        portMethod(RuntimeLogClock.class, "advance", void.class, false, true, long.class, long.class);
        RuntimeLogClock anonymous = new RuntimeLogClock() {
            public long wallTimeMillis() { return -31; }
            public long monotonicNanos() { return 47; }
        };
        require(anonymous.wallTimeMillis() == -31 && anonymous.monotonicNanos() == 47,
            "RuntimeLogClock Java anonymous implementation");
        clockAdvanceRejected(anonymous);
        portClass(RuntimeLogClock.System.class, RuntimeLogClock.class, RuntimeLogClock.class, 2, new Class<?>[0]);
        portMethod(RuntimeLogClock.System.class, "wallTimeMillis", long.class, false, false);
        portMethod(RuntimeLogClock.System.class, "monotonicNanos", long.class, false, false);
        require(RuntimeLogClock.System.class.getDeclaredFields().length == 0, "System clock has no cached fields");
        RuntimeLogClock system = new RuntimeLogClock.System();
        // Read both real clocks, but impose no positivity, wall-clock or scheduling bounds.
        system.wallTimeMillis(); system.monotonicNanos(); clockAdvanceRejected(system);
        require(RuntimeLogClock.System.class.getMethod("advance", long.class, long.class).getDeclaringClass()
            == RuntimeLogClock.class, "System inherits original interface default");
        portClass(RuntimeLogClock.Fake.class, RuntimeLogClock.class, RuntimeLogClock.class, 5,
            new Class<?>[0], new Class<?>[]{long.class, long.class});
        portMethod(RuntimeLogClock.Fake.class, "wallTimeMillis", long.class, false, false);
        portMethod(RuntimeLogClock.Fake.class, "monotonicNanos", long.class, false, false);
        portMethod(RuntimeLogClock.Fake.class, "advance", void.class, false, false, long.class, long.class);
        portMethod(RuntimeLogClock.Fake.class, "setWall", void.class, false, false, long.class);
        portMethod(RuntimeLogClock.Fake.class, "setMonotonic", void.class, false, false, long.class);
        require(RuntimeLogClock.Fake.class.getDeclaredFields().length == 2, "Fake private state inventory");
        for (String name : new String[]{"wall", "mono"}) {
            java.lang.reflect.Field field = RuntimeLogClock.Fake.class.getDeclaredField(name);
            require(field.getType() == long.class && Modifier.isPrivate(field.getModifiers())
                && !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()),
                "Fake private mutable long " + name);
        }
        RuntimeLogClock.Fake zero = new RuntimeLogClock.Fake();
        require(zero.wallTimeMillis() == 0 && zero.monotonicNanos() == 0, "Fake zero constructor");
        RuntimeLogClock.Fake fake = new RuntimeLogClock.Fake(-7, Long.MIN_VALUE);
        require(fake.wallTimeMillis() == -7 && fake.monotonicNanos() == Long.MIN_VALUE, "Fake exact constructor values");
        fake.setWall(Long.MAX_VALUE);
        require(fake.wallTimeMillis() == Long.MAX_VALUE && fake.monotonicNanos() == Long.MIN_VALUE, "Fake independent wall setter");
        fake.setMonotonic(11);
        require(fake.wallTimeMillis() == Long.MAX_VALUE && fake.monotonicNanos() == 11, "Fake independent monotonic setter");
        RuntimeLogClock dispatch = fake;
        dispatch.advance(0, 0);
        require(fake.wallTimeMillis() == Long.MAX_VALUE && fake.monotonicNanos() == 11, "Fake zero advance");
        dispatch.advance(1, -17);
        require(fake.wallTimeMillis() == Long.MIN_VALUE && fake.monotonicNanos() == -6,
            "RuntimeLogClock.Fake independent advance");
        fake.setWall(23); fake.setMonotonic(Long.MIN_VALUE); dispatch.advance(-9, -1);
        require(fake.wallTimeMillis() == 14 && fake.monotonicNanos() == Long.MAX_VALUE, "Fake monotonic negative wrap");
        fake.setWall(Long.MIN_VALUE); fake.setMonotonic(Long.MAX_VALUE); dispatch.advance(-1, 1);
        require(fake.wallTimeMillis() == Long.MAX_VALUE && fake.monotonicNanos() == Long.MIN_VALUE, "Fake both clocks long wrap");
        require(RuntimeLogSink.class.isInterface() && Modifier.isPublic(RuntimeLogSink.class.getModifiers())
            && RuntimeLogSink.class.getDeclaredMethods().length == 1, "RuntimeLogSink Java SAM ABI");
        portMethod(RuntimeLogSink.class, "onSnapshot", void.class, true, false, List.class, int.class);
        java.lang.reflect.Type listType = RuntimeLogSink.class.getDeclaredMethod("onSnapshot", List.class, int.class)
            .getGenericParameterTypes()[0];
        require(listType.getTypeName().equals("java.util.List<org.llmasr.minimal.diagnostics.RuntimeLogEvent>"),
            "RuntimeLogSink exact Java generic signature");
        portClass(RuntimeLogSink.None.class, RuntimeLogSink.class, RuntimeLogSink.class, 1, new Class<?>[0]);
        portMethod(RuntimeLogSink.None.class, "onSnapshot", void.class, false, false, List.class, int.class);
        require(RuntimeLogSink.None.class.getDeclaredFields().length == 0, "None has no singleton fields");
        require(RuntimeLogSink.None.class.getDeclaredMethod("onSnapshot", List.class, int.class)
            .getGenericParameterTypes()[0].equals(listType), "None exact Java generic signature");
        RuntimeLogSink none = new RuntimeLogSink.None();
        noneIgnores(none, null, Integer.MIN_VALUE); noneIgnores(none, null, Integer.MAX_VALUE);
        noneIgnores(none, java.util.Collections.emptyList(), -1);
        noneIgnores(none, Arrays.asList((RuntimeLogEvent) null), 0);
        noneIgnores(none, new java.util.AbstractList<RuntimeLogEvent>() {
            public RuntimeLogEvent get(int index) { throw new AssertionError("None read element"); }
            public int size() { throw new AssertionError("None read size"); }
        }, Integer.MAX_VALUE);
        final List<?>[] observedList = new List<?>[1];
        final int[] observedDrop = new int[1];
        RuntimeLogSink lambda = (snapshot, dropped) -> { observedList[0] = snapshot; observedDrop[0] = dropped; };
        RuntimeLogSink javaSink = new RuntimeLogSink() {
            public void onSnapshot(List<RuntimeLogEvent> snapshot, int dropped) {
                observedList[0] = snapshot; observedDrop[0] = dropped;
            }
        };
        List<RuntimeLogEvent> mutable = new ArrayList<>(); mutable.add(null);
        for (RuntimeLogSink sink : new RuntimeLogSink[]{lambda, javaSink}) {
            for (List<RuntimeLogEvent> list : Arrays.asList(mutable, java.util.Collections.unmodifiableList(mutable), null)) {
                for (int count : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
                    sink.onSnapshot(list, count);
                    require(observedList[0] == list && observedDrop[0] == count, "Java sink preserves nullable list identity/drop");
                }
            }
        }
        RuntimeLogClock.Fake injected = new RuntimeLogClock.Fake(-100, 900);
        RuntimeLogStore store = new RuntimeLogStore(1, injected);
        require(store.clock() == injected, "Store retains identical injected clock");
        final int[] calls = new int[1];
        RuntimeLogSink observer = (snapshot, dropped) -> {
            observedList[0] = snapshot; observedDrop[0] = dropped; calls[0]++;
        };
        store.addSink(null); store.addSink(none); store.addSink(observer);
        require(store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, null, "ok"), "ports Store smoke append");
        List<RuntimeLogEvent> first = store.snapshot();
        require(first.get(0).wallMillis == -100 && first.get(0).monotonicNanos == 900
            && calls[0] == 1 && observedDrop[0] == 0 && observedList[0].equals(first), "ports Store clock/sink smoke");
        injected.advance(-3, 5);
        require(store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, null, "ok"), "ports Store second append");
        require(calls[0] == 2 && observedDrop[0] == 1 && store.snapshot().get(0).wallMillis == -103
            && store.snapshot().get(0).monotonicNanos == 905, "ports Store cumulative drop and clocks");
        store.removeSink(observer); store.removeSink(none);
        require(store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, null, "ok") && calls[0] == 2,
            "ports Store removes identical observer");
        System.out.println("PASS runtime log ports Java ABI/default/nullable/SAM/clock/store");
    }
    private static void inferencePhaseEnum() throws Exception {
        org.llmasr.minimal.asr.InferencePhase[] values = org.llmasr.minimal.asr.InferencePhase.values();
        String[] names = {"MODEL_LOAD_STARTED", "MODEL_LOAD_COMPLETED", "INFERENCE_STARTED", "INFERENCE_COMPLETED"};
        enumAbi(org.llmasr.minimal.asr.InferencePhase.class, values, names);
        java.lang.reflect.Field code = org.llmasr.minimal.asr.InferencePhase.class.getField("code");
        require(code.getType() == int.class && Modifier.isPublic(code.getModifiers())
            && Modifier.isFinal(code.getModifiers()) && !Modifier.isStatic(code.getModifiers()), "InferencePhase code field ABI");
        require(org.llmasr.minimal.asr.InferencePhase.class.getGenericSuperclass().getTypeName().equals(
            "java.lang.Enum<org.llmasr.minimal.asr.InferencePhase>"), "InferencePhase Enum generic signature");
        int[] codes = {1, 2, 3, 4};
        for (int i = 0; i < values.length; i++) {
            require(values[i].code == codes[i] && code.getInt(values[i]) == codes[i],
                "InferencePhase explicit JNI codes must remain 1/2/3/4");
            require(phaseSwitch(values[i]) == codes[i], "InferencePhase Java switch");
        }
        java.lang.reflect.Constructor<?> constructor = org.llmasr.minimal.asr.InferencePhase.class
            .getDeclaredConstructor(String.class, int.class, int.class);
        require(Modifier.isPrivate(constructor.getModifiers()), "InferencePhase private enum constructor");
        values[0] = null;
        require(org.llmasr.minimal.asr.InferencePhase.values()[0] == org.llmasr.minimal.asr.InferencePhase.MODEL_LOAD_STARTED,
            "InferencePhase defensive values array");
        for (String bad : new String[]{"", "model_load_started", "MODEL_LOAD_STARTED ", "UNKNOWN"})
            rejectsExactly(() -> org.llmasr.minimal.asr.InferencePhase.valueOf(bad), IllegalArgumentException.class,
                "InferencePhase invalid valueOf");
        rejectsExactly(() -> org.llmasr.minimal.asr.InferencePhase.valueOf(null), NullPointerException.class,
            "InferencePhase null valueOf");
        rejectsExactly(() -> phaseSwitch(null), NullPointerException.class, "InferencePhase null switch");
    }
    private static int phaseSwitch(org.llmasr.minimal.asr.InferencePhase phase) {
        switch (phase) {
            case MODEL_LOAD_STARTED: return 1;
            case MODEL_LOAD_COMPLETED: return 2;
            case INFERENCE_STARTED: return 3;
            case INFERENCE_COMPLETED: return 4;
            default: throw new AssertionError("unexpected inference phase");
        }
    }
    private static java.lang.reflect.Method inferenceSam(Class<?> type, String name, Class<?> result,
            Class<?>[] exceptions, Class<?>... arguments) throws Exception {
        require(type.isInterface() && Modifier.isPublic(type.getModifiers()) && Modifier.isAbstract(type.getModifiers())
            && type.getTypeParameters().length == 0, type.getSimpleName() + " interface ABI");
        long abstractMethods = Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())
            && Modifier.isAbstract(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())).count();
        java.lang.reflect.Method method = type.getDeclaredMethod(name, arguments);
        int flags = method.getModifiers();
        require(abstractMethods == 1 && Modifier.isPublic(flags) && Modifier.isAbstract(flags)
            && !Modifier.isStatic(flags) && !Modifier.isFinal(flags) && !method.isDefault()
            && method.getReturnType() == result && method.getTypeParameters().length == 0
            && Arrays.equals(method.getExceptionTypes(), exceptions), type.getSimpleName() + " SAM descriptor/throws ABI");
        require(Arrays.equals(method.getGenericParameterTypes(), method.getParameterTypes())
            && method.getGenericReturnType() == result, type.getSimpleName() + " non-generic method ABI");
        return method;
    }
    private static void inferenceListener() throws Exception {
        inferenceSam(org.llmasr.minimal.asr.InferencePhaseListener.class, "onPhase", void.class,
            new Class<?>[0], int.class);
        java.lang.reflect.Field field = org.llmasr.minimal.asr.InferencePhaseListener.class.getField("NONE");
        require(field.getDeclaringClass() == org.llmasr.minimal.asr.InferencePhaseListener.class
            && field.getType() == org.llmasr.minimal.asr.InferencePhaseListener.class
            && Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
            && Modifier.isFinal(field.getModifiers()), "InferencePhaseListener.NONE interface static final field ABI");
        org.llmasr.minimal.asr.InferencePhaseListener none = org.llmasr.minimal.asr.InferencePhaseListener.NONE;
        require(none != null && field.get(null) == none && org.llmasr.minimal.asr.InferencePhaseListener.NONE == none,
            "InferencePhaseListener.NONE stable identity");
        final int[] observed = new int[1];
        org.llmasr.minimal.asr.InferencePhaseListener lambda = code -> { observed[0] = code; };
        org.llmasr.minimal.asr.InferencePhaseListener anonymous = new org.llmasr.minimal.asr.InferencePhaseListener() {
            public void onPhase(int code) { observed[0] = code; }
        };
        for (int code : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1, 2, 3, 4}) {
            Throwable failure = null;
            try { none.onPhase(code); } catch (Throwable caught) { failure = caught; }
            require(failure == null, "InferencePhaseListener.NONE must ignore every int code");
            lambda.onPhase(code);
            require(observed[0] == code, "InferencePhaseListener Java lambda primitive int");
            anonymous.onPhase(code);
            require(observed[0] == code, "InferencePhaseListener Java anonymous primitive int");
        }
    }
    private static void nativeTranscriptionAbi() throws Exception {
        inferenceSam(org.llmasr.minimal.asr.NativeTranscription.class, "invokeWithListener", byte[].class,
            new Class<?>[]{java.io.IOException.class}, String.class, String.class, String.class, String.class,
            org.llmasr.minimal.asr.InferencePhaseListener.class);
        require(org.llmasr.minimal.asr.NativeTranscription.class.getDeclaredMethods().length == 1,
            "NativeTranscription abstract-only declaration inventory");
        final Object[][] captured = new Object[1][];
        final byte[][] output = new byte[1][];
        org.llmasr.minimal.asr.NativeTranscription lambda = (config, wav, language, cache, listener) -> {
            captured[0] = new Object[]{config, wav, language, cache, listener}; return output[0];
        };
        org.llmasr.minimal.asr.NativeTranscription anonymous = new org.llmasr.minimal.asr.NativeTranscription() {
            public byte[] invokeWithListener(String config, String wav, String language, String cache,
                    org.llmasr.minimal.asr.InferencePhaseListener listener) throws java.io.IOException {
                captured[0] = new Object[]{config, wav, language, cache, listener}; return output[0];
            }
        };
        for (org.llmasr.minimal.asr.NativeTranscription port : new org.llmasr.minimal.asr.NativeTranscription[]{lambda, anonymous})
            nativeNullableArguments(port, captured, output);
        java.io.IOException sentinel = new java.io.IOException("synthetic-native-port");
        org.llmasr.minimal.asr.NativeTranscription failing = (c, w, l, t, listener) -> { throw sentinel; };
        org.llmasr.minimal.asr.NativeTranscription failingAnonymous = new org.llmasr.minimal.asr.NativeTranscription() {
            public byte[] invokeWithListener(String config, String wav, String language, String cache,
                    org.llmasr.minimal.asr.InferencePhaseListener listener) throws java.io.IOException { throw sentinel; }
        };
        for (org.llmasr.minimal.asr.NativeTranscription port :
                new org.llmasr.minimal.asr.NativeTranscription[]{failing, failingAnonymous}) {
            java.io.IOException observed = null;
            try { port.invokeWithListener(null, null, null, null, null); }
            catch (java.io.IOException caught) { observed = caught; }
            require(observed == sentinel, "NativeTranscription Java checked IOException identity");
        }
    }
    private static void nativeNullableArguments(org.llmasr.minimal.asr.NativeTranscription port,
            Object[][] captured, byte[][] output) throws Exception {
        Object[] full = {new String("config"), new String("wav"), new String("language"), new String("cache"),
            org.llmasr.minimal.asr.InferencePhaseListener.NONE};
        for (int nullAt = -1; nullAt <= full.length; nullAt++) {
            Object[] arguments = full.clone();
            if (nullAt == full.length) Arrays.fill(arguments, null);
            else if (nullAt >= 0) arguments[nullAt] = null;
            for (byte[] bytes : new byte[][]{null, new byte[0], new byte[]{0, -1, 127, -128}}) {
                output[0] = bytes;
                byte[] actual = port.invokeWithListener((String) arguments[0], (String) arguments[1],
                    (String) arguments[2], (String) arguments[3],
                    (org.llmasr.minimal.asr.InferencePhaseListener) arguments[4]);
                require(actual == bytes, "NativeTranscription nullable byte array identity");
                for (int i = 0; i < arguments.length; i++)
                    require(captured[0][i] == arguments[i], "NativeTranscription nullable argument identity/order");
            }
        }
    }
    private static void inferencePhasePorts() throws Exception {
        inferencePhaseEnum();
        inferenceListener();
        nativeTranscriptionAbi();
        System.out.println("PASS inference phase ports Java ABI/codes/NONE/nullable/throws/SAM");
    }
    private interface BatchCall { void run() throws Exception; }
    private static Throwable batchFailure(BatchCall call) {
        try { call.run(); return null; } catch (Throwable error) { return error; }
    }
    private static void batchModels() throws Exception {
        org.llmasr.minimal.model.ModelEntry a = new org.llmasr.minimal.model.ModelEntry("a", 7, repeat('A', 64));
        org.llmasr.minimal.model.ModelEntry b = new org.llmasr.minimal.model.ModelEntry("a", 8, repeat('a', 64));
        require(a.sha256().equals(repeat('a', 64)) && a.partName().equals("a.part"), "ModelEntry canonical SHA/name");
        require(a.hashCode() == "a".hashCode()*31 + Long.hashCode(7) && a.hashCode()!=b.hashCode(), "ModelEntry hash must retain byte count");
        require(a.equals(new org.llmasr.minimal.model.ModelEntry("a",7,repeat('a',64))) && !a.equals(b) && !a.equals(null), "ModelEntry content equality");
        Throwable error=batchFailure(() -> new org.llmasr.minimal.model.ModelEntry(null,0,"a"));
        require(error instanceof NullPointerException && "file".equals(error.getMessage()), "ModelEntry null file exact rejection");
        error=batchFailure(() -> new org.llmasr.minimal.model.ModelManifest(Arrays.asList(a,b)));
        require(error instanceof IOException && error.getMessage().contains("重复文件名"), "ModelManifest duplicate names must fail closed");
        for (java.util.List<org.llmasr.minimal.model.ModelEntry> entries : Arrays.asList(null, Collections.<org.llmasr.minimal.model.ModelEntry>emptyList(), Arrays.asList((org.llmasr.minimal.model.ModelEntry)null))) {
            final java.util.List<org.llmasr.minimal.model.ModelEntry> input=entries;
            require(batchFailure(() -> new org.llmasr.minimal.model.ModelManifest(input)) instanceof IOException, "ModelManifest invalid list is IOException");
        }
        org.llmasr.minimal.model.ModelEntry part=new org.llmasr.minimal.model.ModelEntry("a.part",1,repeat('a',64));
        for (java.util.List<org.llmasr.minimal.model.ModelEntry> entries : Arrays.asList(Arrays.asList(a,part),Arrays.asList(part,a)))
            require(batchFailure(() -> new org.llmasr.minimal.model.ModelManifest(entries)) instanceof IOException,"ModelManifest bidirectional part collision");
        ArrayList<org.llmasr.minimal.model.ModelEntry> input=new ArrayList<>(); input.add(a);
        org.llmasr.minimal.model.ModelManifest manifest=new org.llmasr.minimal.model.ModelManifest(input); input.clear();
        require(manifest.entries().size()==1 && manifest.require("a")==a,"ModelManifest defensive snapshot identity");
        require(batchFailure(() -> manifest.entries().clear()) instanceof UnsupportedOperationException,"ModelManifest unmodifiable entries");
        require(batchFailure(() -> manifest.require(null)) instanceof IOException,"ModelManifest null lookup");
        require(batchFailure(() -> org.llmasr.minimal.model.ModelManifest.requireSafeRoot(null)) instanceof IOException,"ModelManifest null root");
        ArrayList<String> names=new ArrayList<>();names.add("a");
        org.llmasr.minimal.model.ModelReports.ImportPlan zero=new org.llmasr.minimal.model.ModelReports.ImportPlan(0,9,-1,names);
        names.clear();require(zero.requiredBytes==0,"ImportPlan zero-copy requires zero extra bytes");
        require(zero.reusableFiles.equals(Arrays.asList("a")) && batchFailure(() -> zero.reusableFiles.clear()) instanceof UnsupportedOperationException,"ImportPlan defensive unmodifiable list");
        require(batchFailure(() -> new org.llmasr.minimal.model.ModelReports.ImportPlan(Long.MAX_VALUE,0,0,Collections.emptyList())) instanceof ArithmeticException,"ImportPlan overflow");
        require(batchFailure(() -> new org.llmasr.minimal.model.ModelReports.FileOutcome(null)) instanceof IllegalArgumentException,"FileOutcome null status");
        require(new org.llmasr.minimal.model.ModelReports.FileOutcome(org.llmasr.minimal.model.ModelReports.FileOutcome.Status.FAILED).isFailure(),"FileOutcome failure");
        final boolean[] cancelled={false}; final Object handle=new Object();
        org.llmasr.minimal.model.ModelSource.UriRef ref=new org.llmasr.minimal.model.ModelSource.UriRef(handle);
        require(ref.handle()==handle && new org.llmasr.minimal.model.ModelSource.UriRef(null).toString().equals("null"),"ModelSource opaque nullable handle");
        org.llmasr.minimal.model.ModelSource src=new org.llmasr.minimal.model.ModelSource() {
            public java.util.Map<String,UriRef> enumerate(java.util.Collection<String> expected,int max) { cancelled[0]=true;return null; }
            public java.io.InputStream open(UriRef value) { return null; }
        };
        org.llmasr.minimal.model.ModelRepository.CancelGate gate=new org.llmasr.minimal.model.ModelRepository.CancelGate() {
            public boolean cancelled(){return cancelled[0];}public boolean reservePublish(){return true;}
        };
        error=batchFailure(() -> src.enumerate(null,3,gate));
        require(error instanceof org.llmasr.minimal.model.ModelRepository.CancelledException,"ModelSource default must checkpoint after enumerate");
        require(org.llmasr.minimal.model.ModelSource.class.getMethod("enumerate",java.util.Collection.class,int.class,org.llmasr.minimal.model.ModelRepository.CancelGate.class).isDefault(),"ModelSource real JVM default");
        error=batchFailure(() -> org.llmasr.minimal.model.ModelProviderBoundary.call("safe",() -> {throw new java.util.concurrent.CancellationException("private");}));
        require(error instanceof IOException && "safe".equals(error.getMessage()) && error.getCause()==null && error.getSuppressed().length==0,"Provider cancellation must be sanitized");
        Error fatal=new InternalError("fatal");
        require(batchFailure(() -> org.llmasr.minimal.model.ModelProviderBoundary.call("safe",() -> {throw fatal;}))==fatal,"Provider fatal identity");
        org.llmasr.minimal.model.FileSafety.deleteRegular(null);
    }
    private static String repeat(char c,int n) { char[] values=new char[n];Arrays.fill(values,c);return new String(values); }
    private static void batchPagesAndExports() throws Exception {
        org.llmasr.minimal.modelmanagement.ModelPickerTickets first=new org.llmasr.minimal.modelmanagement.ModelPickerTickets();
        org.llmasr.minimal.modelmanagement.ModelPickerTickets other=new org.llmasr.minimal.modelmanagement.ModelPickerTickets();
        org.llmasr.minimal.modelmanagement.ModelPickerTickets.Ticket mine=first.begin(), foreign=other.begin();
        require(!first.valid(foreign),"ModelPickerTickets foreign ticket must be rejected");
        first.release(foreign);require(first.valid(mine),"ModelPickerTickets foreign release no-op");first.release(mine);
        require(Modifier.isSynchronized(first.getClass().getMethod("begin").getModifiers()),"ModelPickerTickets monitor flag");
        final class Output extends ByteArrayOutputStream { int flushes,closes;public void flush(){flushes++;}public void close(){closes++;} }
        Output out=new Output();
        RuntimeLogEvent event=new RuntimeLogEvent(0,1,2,RuntimeLogEventKind.APP_STARTUP,RuntimeLogSource.APP,null,"ok");
        long bytes=RuntimeLogCodec.writeSnapshot(Arrays.asList(event),out);
        require(out.flushes==1 && out.closes==0,"RuntimeLogCodec must flush once without closing");
        require(bytes==out.size() && new String(out.toByteArray(),StandardCharsets.UTF_8).endsWith("\n"),"RuntimeLogCodec byte count/UTF8");
        Output untouched=new Output();require(batchFailure(() -> RuntimeLogCodec.writeSnapshot(Arrays.asList(event,null),untouched)) instanceof IOException && untouched.size()==0,"RuntimeLogCodec preflight before writes");
        org.llmasr.minimal.transcription.ResultFiles.writeText(out,"中文😀");require(out.flushes==1 && out.closes==0,"ResultFiles does not flush or close");
        require(new org.llmasr.minimal.transcription.ResultFiles()!=null,"ResultFiles public default constructor");
        final int[] opens={0};ArrayList<Runnable> jobs=new ArrayList<>();
        org.llmasr.minimal.transcription.ResultState result=new org.llmasr.minimal.transcription.ResultState();result.setText("private");
        org.llmasr.minimal.transcription.TextExportController<String> text=new org.llmasr.minimal.transcription.TextExportController<>(target -> {opens[0]++;return new Output();},jobs::add);
        org.llmasr.minimal.transcription.TextExportPage<String> page=new org.llmasr.minimal.transcription.TextExportPage<>(text);page.foreground(true);
        org.llmasr.minimal.transcription.TextExportController.Ticket ticket=page.begin(result);
        require(page.onResult(ticket.requestCode,"target") && jobs.size()==1,"TXT queued actual page");page.destroy();jobs.remove(0).run();
        require(opens[0]==0,"Revoked queued TXT must cause zero provider opens");
        require(!text.state().busy(),"TXT revoked slot releases on runnable exit");
        org.llmasr.minimal.diagnostics.LogExportController<String> log=new org.llmasr.minimal.diagnostics.LogExportController<>(target -> new Output(),Runnable::run);
        org.llmasr.minimal.diagnostics.LogExportPage<String> lp=new org.llmasr.minimal.diagnostics.LogExportPage<>(log);lp.foreground(true);
        org.llmasr.minimal.diagnostics.LogExportController.Ticket lt=lp.begin(Arrays.asList(event));require(lt!=null && lp.result(lt.requestCode,"x") && log.state().phase==org.llmasr.minimal.diagnostics.LogExportController.Phase.SUCCEEDED,"Log actual page writes frozen snapshot");lp.destroy();
        require(org.llmasr.minimal.diagnostics.RuntimeLogText.event(RuntimeLogEventKind.APP_STARTUP).equals("应用启动"),"RuntimeLogText fixed text");
        require(org.llmasr.minimal.modelmanagement.ModelUiText.bytes(-1).equals("未知（尚未取得快照）"),"ModelUiText unknown bytes");
        require(org.llmasr.minimal.modelmanagement.ModelUiText.safeError("content://private").equals("存储或读取失败，请检查可用空间、重新选择并授权本地目录后重试"),"ModelUiText safe provider text");
    }
    private static void batchAdapterAndAbi() throws Exception {
        org.llmasr.minimal.asr.InferenceAdapter adapter=new org.llmasr.minimal.asr.InferenceAdapter(null,() -> {},() -> {},(c,w,l,t,listener) -> null);
        Throwable error=batchFailure(() -> adapter.transcribe(RuntimeLogSource.APP,null,null,null,null,null));
        require(error instanceof IOException && "Native returned null".equals(error.getMessage()),"InferenceAdapter null result must use fixed failure");
        for (Class<?> type : new Class<?>[]{org.llmasr.minimal.model.ModelEntry.class,org.llmasr.minimal.model.ModelManifest.class,org.llmasr.minimal.model.ModelReports.class,org.llmasr.minimal.model.ModelAccess.class,org.llmasr.minimal.model.FileSafety.class,org.llmasr.minimal.model.ModelProviderBoundary.class,org.llmasr.minimal.modelmanagement.ModelPageSession.class,org.llmasr.minimal.modelmanagement.ModelPickerTickets.class,org.llmasr.minimal.modelmanagement.ModelUiText.class,RuntimeLogCodec.class,org.llmasr.minimal.diagnostics.RuntimeLogText.class,org.llmasr.minimal.diagnostics.RuntimeLogWorker.class,org.llmasr.minimal.diagnostics.LogExportController.class,org.llmasr.minimal.diagnostics.LogExportPage.class,org.llmasr.minimal.transcription.TextExportController.class,org.llmasr.minimal.transcription.TextExportPage.class,org.llmasr.minimal.transcription.ResultFiles.class,org.llmasr.minimal.asr.InferenceAdapter.class,org.llmasr.minimal.ime.ImeController.class})
            require(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()),"batch final class ABI "+type.getName());
        batchPrivateConstructor(org.llmasr.minimal.modelmanagement.ModelPickerTickets.Ticket.class, int.class);
        batchPrivateConstructor(org.llmasr.minimal.diagnostics.LogExportController.Ticket.class, long.class, int.class, List.class);
        batchPrivateConstructor(org.llmasr.minimal.diagnostics.LogExportController.State.class,
            org.llmasr.minimal.diagnostics.LogExportController.Ticket.class, org.llmasr.minimal.diagnostics.LogExportController.Phase.class, long.class);
        batchPrivateConstructor(org.llmasr.minimal.transcription.TextExportController.Ticket.class,
            long.class, int.class, org.llmasr.minimal.transcription.ResultState.class, org.llmasr.minimal.transcription.ResultState.Snapshot.class);
        batchPrivateConstructor(org.llmasr.minimal.transcription.TextExportController.State.class,
            org.llmasr.minimal.transcription.TextExportController.Ticket.class, org.llmasr.minimal.transcription.TextExportController.Phase.class, long.class);
        require(Modifier.isFinal(org.llmasr.minimal.model.ModelReports.ImportPlan.class.getField("requiredBytes").getModifiers()),"ImportPlan real final field");
        require(org.llmasr.minimal.asr.InferenceAdapter.class.getMethod("transcribe",RuntimeLogSource.class,String.class,String.class,String.class,String.class,String.class).getExceptionTypes()[0]==IOException.class,"InferenceAdapter IOException ABI");
    }
    private static void batchPrivateConstructor(Class<?> type, Class<?>... parameters) throws Exception {
        require(Modifier.isPrivate(type.getDeclaredConstructor(parameters).getModifiers()), "batch private original constructor " + type.getName());
        int bridges = 0;
        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!Modifier.isPublic(constructor.getModifiers())) continue;
            Class<?>[] actual = constructor.getParameterTypes();
            require(constructor.getModifiers() == (Modifier.PUBLIC | 0x1000)
                && actual.length == parameters.length + 1
                && Arrays.equals(Arrays.copyOf(actual, parameters.length), parameters)
                && actual[parameters.length].getName().equals("kotlin.jvm.internal.DefaultConstructorMarker"),
                "batch exact whitelisted synthetic constructor " + type.getName());
            bridges++;
        }
        require(bridges <= 1, "batch at most one exact constructor bridge " + type.getName());
    }
    private static void batchContracts() throws Exception {
        batchModels();batchPagesAndExports();batchAdapterAndAbi();
        System.out.println("PASS batch public boundaries Java ABI/nullable/ownership/IO");
    }
    private static void nextControllerAbi() throws Exception {
        Class<?> type = org.llmasr.minimal.modelmanagement.ModelManagementController.class;
        require(Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()), "next Controller final class ABI");
        for (String name : new String[]{"startVerify", "refreshInspect", "state", "readiness", "opControl"}) {
            java.lang.reflect.Method method = type.getDeclaredMethod(name);
            require(Modifier.isPublic(method.getModifiers()) && !Modifier.isFinal(method.getModifiers())
                && !Modifier.isSynchronized(method.getModifiers()), "next Controller public nonfinal lock-free method " + name);
        }
        Throwable failure = batchFailure(() -> new org.llmasr.minimal.modelmanagement.ModelManagementController(null,null,null,null,null));
        require(failure instanceof IllegalArgumentException && "wiring required".equals(failure.getMessage()), "next Controller wiring rejection before runner");
        org.llmasr.minimal.modelmanagement.ModelManagementController controller =
            new org.llmasr.minimal.modelmanagement.ModelManagementController(new TaskCoordinator(Runnable::run),
                org.llmasr.minimal.model.ModelRepository.forWorker(new org.llmasr.minimal.model.ModelManifest(
                    Arrays.asList(new org.llmasr.minimal.model.ModelEntry("a",0,repeat('0',64)))),
                    new java.io.File("unused-next-controller-no-IO"), () -> Long.MAX_VALUE),
                new org.llmasr.minimal.model.ModelReadiness(), new org.llmasr.minimal.modelmanagement.ModelOperationControl(),
                new org.llmasr.minimal.modelmanagement.ModelManagementState());
        require(!controller.startImport(null) && !controller.startDelete(-1,false), "next Controller nullable/confirmation short circuit");
        controller.requestCancel(null);
        System.out.println("PASS next Controller Java ABI/nullable wiring");
    }
    private static void k2jRecordingShape() throws Exception {
        Class<?> type = RecordingControl.class;
        require(type.getModifiers() == (Modifier.PUBLIC | Modifier.FINAL)
            && type.getSuperclass() == Object.class && type.getInterfaces().length == 0,
            "K2J RecordingControl final class ABI");
        require(type.getDeclaredConstructors().length == 1
            && type.getDeclaredConstructor().getModifiers() == Modifier.PUBLIC,
            "K2J RecordingControl constructor ABI");
        String[] names = {"start", "stop", "cancel", "captureReleased", "tryCommitInference",
            "stopped", "cancelled", "released", "committed"};
        require(type.getDeclaredMethods().length == names.length, "K2J RecordingControl method inventory");
        for (String name : names) {
            java.lang.reflect.Method method = type.getDeclaredMethod(name,
                name.equals("start") ? new Class<?>[]{Runnable.class} : new Class<?>[0]);
            require(Modifier.isSynchronized(method.getModifiers()), "K2J RecordingControl synchronized ABI: " + name);
            require(method.getModifiers() == (Modifier.PUBLIC | Modifier.FINAL | Modifier.SYNCHRONIZED)
                && method.getReturnType() == (name.equals("stop") || name.equals("captureReleased") ? void.class : boolean.class)
                && method.getExceptionTypes().length == 0, "K2J RecordingControl method ABI: " + name);
        }
        require(type.getDeclaredFields().length == 5, "K2J RecordingControl state inventory");
        for (String name : new String[]{"stopped", "cancelled", "started", "released", "committed"}) {
            java.lang.reflect.Field field = type.getDeclaredField(name);
            require(field.getModifiers() == Modifier.PRIVATE && field.getType() == boolean.class,
                "K2J RecordingControl ordinary private state: " + name);
        }
    }
    private static void k2jBackendFailure() {
        for (Throwable sentinel : new Throwable[]{new AssertionError("synthetic-backend"),
                new IllegalStateException("synthetic-backend")}) {
            sentinel.addSuppressed(new IllegalArgumentException("synthetic-suppressed"));
            RecordingControl control = new RecordingControl();
            Throwable observed = batchFailure(() -> control.start(() -> {
                require(Thread.holdsLock(control), "K2J backend must hold receiver monitor");
                if (sentinel instanceof Error) throw (Error) sentinel;
                throw (RuntimeException) sentinel;
            }));
            require(observed == sentinel && observed.getSuppressed().length == 1,
                "K2J backend Throwable identity");
            require(control.start(() -> {}), "K2J backend failure must permit retry");
        }
        RecordingControl eligible = new RecordingControl();
        Throwable npe = batchFailure(() -> eligible.start(null));
        require(npe != null && npe.getClass() == NullPointerException.class && npe.getCause() == null
            && npe.getSuppressed().length == 0, "K2J eligible null exact Throwable shape");
        System.out.println("K2J_NULL message=" + (npe.getMessage() == null ? "<null>" :
            java.util.Base64.getEncoder().encodeToString(npe.getMessage().getBytes(StandardCharsets.UTF_8)))
            + " cause=null suppressed=0");
        require(eligible.start(() -> {}), "K2J null failure must permit retry");
    }
    private static void k2jRecordingStates() {
        RecordingControl cancelled = new RecordingControl();
        require(cancelled.start(() -> {}), "K2J cancelled fixture start");
        require(cancelled.cancel() && cancelled.cancel(), "K2J repeated cancellation accepted");
        cancelled.captureReleased();
        require(!cancelled.tryCommitInference(), "K2J cancelled capture must never commit");
        require(cancelled.stopped() && cancelled.cancelled() && cancelled.released() && !cancelled.committed()
            && !cancelled.start(null), "K2J cancellation state/nullable short circuit");
        RecordingControl control = new RecordingControl();
        require(!control.stopped() && !control.cancelled() && !control.released() && !control.committed()
            && !control.tryCommitInference(), "K2J initial recording state");
        final boolean[] monitor = {false};
        require(control.start(() -> { monitor[0] = Thread.holdsLock(control); }), "K2J first start");
        require(monitor[0] && !control.start(null) && !control.tryCommitInference(), "K2J started before release");
        control.stop();
        require(control.stopped() && !control.released() && !control.cancelled(), "K2J stop is not release/cancel");
        control.captureReleased(); control.captureReleased();
        require(control.tryCommitInference() && !control.tryCommitInference() && !control.cancel(),
            "K2J commit exactly once and rejects late cancel");
        control.stop();
        require(control.committed() && control.stopped() && control.released() && !control.cancelled(),
            "K2J committed terminal state");
        for (int mode = 0; mode < 3; mode++) {
            RecordingControl rejected = new RecordingControl();
            if (mode == 0) rejected.stop(); else if (mode == 1) rejected.cancel(); else rejected.captureReleased();
            require(!rejected.start(null) && !rejected.tryCommitInference(), "K2J rejected null never dereferenced");
        }
        RecordingControl reentrant = new RecordingControl();
        require(reentrant.start(() -> { reentrant.cancel(); reentrant.captureReleased(); }),
            "K2J reentrant start retains callback result order");
        require(reentrant.cancelled() && reentrant.stopped() && reentrant.released()
            && !reentrant.tryCommitInference(), "K2J reentrant cancellation wins commit");
        RecordingControl nested = new RecordingControl();
        final boolean[] nestedAccepted = {false};
        require(nested.start(() -> { nestedAccepted[0] = nested.start(() -> {}); }), "K2J nested outer start");
        require(nestedAccepted[0] && !nested.start(null), "K2J started assignment follows reentrant backend");
    }
    private static void k2jRequestContext() throws Exception {
        Class<?> type = RequestContext.class;
        require(type.getModifiers() == (Modifier.PUBLIC | Modifier.FINAL) && type.getSuperclass() == Object.class
            && type.getInterfaces().length == 0 && type.getDeclaredMethods().length == 0
            && type.getDeclaredConstructors().length == 1
            && type.getDeclaredConstructor(String.class, TaskKind.class).getModifiers() == Modifier.PUBLIC,
            "K2J RequestContext constructor/no-accessor ABI");
        String[] names = {"requestId", "kind", "cleanedTemporary", "inferenceReported", "inputWav"};
        Class<?>[] kinds = {String.class, TaskKind.class, int.class, boolean.class, java.io.File.class};
        require(type.getDeclaredFields().length == 5, "K2J RequestContext field inventory");
        for (int i = 0; i < names.length; i++) {
            java.lang.reflect.Field field = type.getDeclaredField(names[i]);
            require(field.getType() == kinds[i] && field.getModifiers() == (Modifier.PUBLIC | (i < 2 ? Modifier.FINAL : 0)),
                "K2J RequestContext direct field ABI: " + names[i]);
        }
        String id = new String("synthetic-id");
        for (RequestContext context : new RequestContext[]{new RequestContext(null, null), new RequestContext(id, TaskKind.INFERENCE)}) {
            require(context.cleanedTemporary == 0 && !context.inferenceReported && context.inputWav == null,
                "K2J RequestContext zero defaults");
            require(context.requestId == (context.kind == null ? null : id), "K2J RequestContext identity/nullable constructor");
            java.io.File wav = new java.io.File("synthetic-not-opened.wav");
            context.cleanedTemporary = -7; context.inferenceReported = true; context.inputWav = wav;
            require(context.cleanedTemporary == -7 && context.inferenceReported && context.inputWav == wav,
                "K2J RequestContext direct mutable field identity");
            context.inputWav = null;
            require(context.inputWav == null, "K2J RequestContext nullable reassignment");
        }
    }
    private static void k2jOwners() throws Exception {
        String expected = System.getProperty("k2j.owner.dir");
        require(expected != null, "K2J loaded owner directory required");
        Path root = java.nio.file.Paths.get(expected).toRealPath();
        for (Class<?> type : new Class<?>[]{RecordingControl.class, RequestContext.class, KotlinMigrationTest.class}) {
            Path origin = java.nio.file.Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(origin.equals(root), "K2J loaded owner must shadow dependency: " + type.getName());
            String relative = type.getName().replace('.', '/') + ".class";
            byte[] disk = Files.readAllBytes(root.resolve(relative));
            byte[] loaded;
            try (java.io.InputStream input = type.getResourceAsStream("/" + relative)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                loaded = output.toByteArray();
            }
            require(Arrays.equals(disk, loaded), "K2J loaded owner bytes: " + type.getName());
            System.out.println("K2J_OWNER " + type.getName() + " " + origin + " "
                + java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(loaded)));
        }
    }
    private static void k2jContracts() throws Exception {
        if (System.getProperty("k2j.owner.dir") != null) k2jOwners();
        k2jRecordingShape(); k2jBackendFailure(); k2jRecordingStates(); k2jRequestContext();
        System.out.println("PASS K2J selected production ABI/null/monitor/state/identity");
    }
    public static void main(String[] args) throws Exception {
        k2jContracts();
        nextControllerAbi();
        runtimeLogEnums();
        runtimeLogPorts();
        inferencePhasePorts();
        batchContracts();
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
