package org.llmasr.minimal.asr;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.llmasr.minimal.audio.AsrText;

/** Strict parser for the native transcribe response. The protocol is
 * "load_s inference_s generated_tokens\nraw" with a 128-token MNN cap.
 *
 * The parser rejects:
 *  - non-finite or negative timings,
 *  - non-integer or out-of-range token counts (MNN caps at 128),
 *  - payloads that are not valid UTF-8 (decoded strictly, no replacement),
 *  - empty / null payloads, missing newline, wrong metric count.
 *
 * The protocol is unchanged; this only narrows the Java surface so future
 * refactors can rely on the contract.
 */
public final class NativeResponse {
    /** The native decoder caps at 128 tokens. We surface tokens beyond that
     * as an error so a host test or future refactor that mints a higher
     * cap will notice the divergence. The existing 0.3 binary enforces 128.
     */
    public static final int MAX_TOKENS = 128;

    public final double loadSeconds;
    public final double inferenceSeconds;
    public final int generatedTokens;
    public final String raw;
    public final String display;

    private NativeResponse(double loadSeconds, double inferenceSeconds, int generatedTokens, String raw, String display) {
        this.loadSeconds = loadSeconds;
        this.inferenceSeconds = inferenceSeconds;
        this.generatedTokens = generatedTokens;
        this.raw = raw;
        this.display = display;
    }

    public static NativeResponse parse(byte[] output) throws IllegalArgumentException {
        if (output == null || output.length == 0) throw new IllegalArgumentException("Native returned empty");
        String response = decodeUtf8(output);
        int newline = response.indexOf('\n');
        if (newline <= 0) throw new IllegalArgumentException("Native response missing metrics line");
        String[] metrics = response.substring(0, newline).split(" ");
        if (metrics.length != 3) throw new IllegalArgumentException("Native response expects three metrics");
        double load = parseFinite(metrics[0], "load_s");
        double infer = parseFinite(metrics[1], "inference_s");
        int tokens = parseTokens(metrics[2]);
        // Empty raw is allowed when MNN produces no markers and a short
        // output; we keep that as a valid EOS path.
        String raw = response.substring(newline + 1);
        String display = AsrText.display(raw);
        return new NativeResponse(load, infer, tokens, raw, display);
    }

    /** Strict UTF-8 decode. Surfaces invalid sequences as an error so
     * corrupted native output is not silently masquerading as a string.
     */
    private static String decodeUtf8(byte[] output) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(output)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Native output is not valid UTF-8");
        }
    }

    private static double parseFinite(String value, String field) {
        double v;
        try { v = Double.parseDouble(value); }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException(field + " not a number: " + value); }
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException(field + " not finite: " + value);
        if (v < 0.0) throw new IllegalArgumentException(field + " negative: " + value);
        return v;
    }

    private static int parseTokens(String value) {
        long parsed;
        try { parsed = Long.parseLong(value); }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("tokens not an integer: " + value); }
        if (parsed < 0 || parsed > MAX_TOKENS) throw new IllegalArgumentException("tokens out of range [0.." + MAX_TOKENS + "]: " + value);
        return (int) parsed;
    }
}
