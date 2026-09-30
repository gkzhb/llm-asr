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

/** Java-caller ABI and subtle Java/Kotlin behavior preservation, no Android runtime. */
public final class KotlinMigrationTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Kotlin migration check " + checks);
    }
    public static void main(String[] args) throws Exception {
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
