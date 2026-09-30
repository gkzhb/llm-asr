import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import org.llmasr.minimal.audio.AsrText;
import org.llmasr.minimal.audio.PcmWave;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.asr.NativeResponse;

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
        System.out.println("PASS Kotlin migration Java ABI/behavior: " + checks + " checks");
    }
}
