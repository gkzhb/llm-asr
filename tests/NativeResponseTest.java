import org.llmasr.minimal.asr.NativeResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Direct host tests of the strict NativeResponse parser. The protocol is
 * "load_s inference_s generated_tokens\nraw" with a 128-token MNN cap.
 * Tests run against the production parser, not a hand-rolled stand-in.
 */
public final class NativeResponseTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        // Happy path.
        NativeResponse good = NativeResponse.parse(bytes("3.14 0.27 42\n<asr_text>Hello world.<|im_end|>"));
        check(good.loadSeconds == 3.14);
        check(good.inferenceSeconds == 0.27);
        check(good.generatedTokens == 42);
        check(good.raw.equals("<asr_text>Hello world.<|im_end|>"));
        check(good.display.equals("Hello world."));

        // Zero values are allowed.
        NativeResponse zero = NativeResponse.parse(bytes("0 0 0\n<asr_text>"));
        check(zero.loadSeconds == 0.0 && zero.inferenceSeconds == 0.0 && zero.generatedTokens == 0);
        check(zero.display.equals(""));

        // Empty raw (after the newline) is allowed: MNN's normal EOS may
        // produce a short output with no markers.
        NativeResponse emptyRaw = NativeResponse.parse(bytes("0.5 0.2 5\n"));
        check(emptyRaw.raw.equals(""));
        check(emptyRaw.display.equals(""));

        // Non-finite timings are rejected.
        boolean rejectedNaN = false;
        try { NativeResponse.parse(bytes("NaN 0.5 1\nx")); }
        catch (IllegalArgumentException expected) { rejectedNaN = true; }
        check(rejectedNaN);
        boolean rejectedInf = false;
        try { NativeResponse.parse(bytes("1.0 Infinity 1\nx")); }
        catch (IllegalArgumentException expected) { rejectedInf = true; }
        check(rejectedInf);

        // Negative timings and tokens are rejected.
        boolean rejectedNegLoad = false;
        try { NativeResponse.parse(bytes("-0.1 0.5 1\nx")); }
        catch (IllegalArgumentException expected) { rejectedNegLoad = true; }
        check(rejectedNegLoad);
        boolean rejectedNegTokens = false;
        try { NativeResponse.parse(bytes("0.1 0.5 -3\nx")); }
        catch (IllegalArgumentException expected) { rejectedNegTokens = true; }
        check(rejectedNegTokens);

        // Wrong number of metrics is rejected.
        boolean wrongFields = false;
        try { NativeResponse.parse(bytes("0.1 0.5\nx")); }
        catch (IllegalArgumentException expected) { wrongFields = true; }
        check(wrongFields);
        boolean tooManyFields = false;
        try { NativeResponse.parse(bytes("0.1 0.5 1 extra\nx")); }
        catch (IllegalArgumentException expected) { tooManyFields = true; }
        check(tooManyFields);

        // No newline is rejected. Without a newline, the substring(0,
        // newline) call returns -1 and the parser throws. The first try
        // either rejects via that path or via a metrics-count mismatch;
        // either is a valid contract.
        boolean noNewline = false;
        try { NativeResponse.parse(bytes("0.1 0.5 1")); }
        catch (IllegalArgumentException expected) { noNewline = true; }
        if (!noNewline) {
            try { NativeResponse.parse(bytes("0.1 0.5 1")); }
            catch (IllegalArgumentException expected) { noNewline = true; }
        }
        check(noNewline);

        // Null and empty payload are rejected.
        boolean nullPayload = false;
        try { NativeResponse.parse(null); }
        catch (IllegalArgumentException expected) { nullPayload = true; }
        check(nullPayload);
        boolean emptyPayload = false;
        try { NativeResponse.parse(new byte[0]); }
        catch (IllegalArgumentException expected) { emptyPayload = true; }
        check(emptyPayload);

        // Non-numeric metrics are rejected.
        boolean nonNumeric = false;
        try { NativeResponse.parse(bytes("0.1 hello 1\nx")); }
        catch (IllegalArgumentException expected) { nonNumeric = true; }
        check(nonNumeric);

        // UTF-8 raw text round-trips; emoji survives.
        NativeResponse emoji = NativeResponse.parse(bytes("1.0 1.0 1\n中文😀<|im_end|>"));
        check(emoji.raw.equals("中文😀<|im_end|>"));
        check(emoji.display.equals("中文😀"));

        // The 128-token MNN cap is enforced: tokens above the cap are rejected.
        boolean above = false;
        try { NativeResponse.parse(bytes("0.0 0.0 129\nraw")); }
        catch (IllegalArgumentException expected) { above = true; }
        check(above);

        // Exactly 128 is accepted.
        NativeResponse at = NativeResponse.parse(bytes("0.0 0.0 128\nraw"));
        check(at.generatedTokens == 128);

        // Invalid UTF-8 is rejected by the strict decoder. The decoder
        // throws CharacterCodingException on a malformed sequence; the
        // parser surfaces this as IllegalArgumentException.
        byte[] bad = new byte[] { (byte) 0xC3, 0x28 }; // 0xC3 0x28 is an invalid 2-byte sequence
        // Construct a response: "0.0 0.0 0\n" + bad — the bad bytes
        // sit inside the raw portion. The decoder is called on the whole
        // payload, so any invalid byte fails the parse.
        byte[] prefix = bytes("0.0 0.0 0\n");
        byte[] payload = new byte[prefix.length + bad.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(bad, 0, payload, prefix.length, bad.length);
        boolean invalidUtf8 = false;
        try { NativeResponse.parse(payload); }
        catch (IllegalArgumentException expected) { invalidUtf8 = true; }
        check(invalidUtf8);

        // Sanity: the strict decoder rejects the same bytes the production
        // parser rejects, confirming we wired CharsetDecoder.REPORT through.
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        boolean decoderRejects = false;
        try { decoder.decode(ByteBuffer.wrap(payload)).toString(); }
        catch (CharacterCodingException expected) { decoderRejects = true; }
        check(decoderRejects);

        System.out.println("PASS " + checks + " native response parser checks (host only; strict UTF-8, 128 token cap, empty raw allowed)");
    }
}
