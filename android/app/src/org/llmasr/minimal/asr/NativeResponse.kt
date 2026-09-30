package org.llmasr.minimal.asr

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import org.llmasr.minimal.audio.AsrText

/** Strict UTF-8 parser for "load_s inference_s generated_tokens\nraw".
 * Rejects empty payloads, invalid timings/token counts and malformed UTF-8.
 * Empty raw remains a valid EOS path; protocol and 128-token cap are unchanged.
 */
class NativeResponse private constructor(
    @JvmField val loadSeconds: Double,
    @JvmField val inferenceSeconds: Double,
    @JvmField val generatedTokens: Int,
    @JvmField val raw: String,
    @JvmField val display: String
) {
    companion object {
        const val MAX_TOKENS = 128

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun parse(output: ByteArray?): NativeResponse {
            if (output == null || output.isEmpty()) throw IllegalArgumentException("Native returned empty")
            val response = decodeUtf8(output)
            val newline = response.indexOf('\n')
            if (newline <= 0) throw IllegalArgumentException("Native response missing metrics line")
            // Pattern.split preserves the original Java String.split(" ") limit=0 semantics.
            val metrics = Pattern.compile(" ").split(response.substring(0, newline))
            if (metrics.size != 3) throw IllegalArgumentException("Native response expects three metrics")
            val load = parseFinite(metrics[0], "load_s")
            val infer = parseFinite(metrics[1], "inference_s")
            val tokens = parseTokens(metrics[2])
            val raw = response.substring(newline + 1)
            return NativeResponse(load, infer, tokens, raw, AsrText.display(raw))
        }

        private fun decodeUtf8(output: ByteArray): String {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                return decoder.decode(ByteBuffer.wrap(output)).toString()
            } catch (e: CharacterCodingException) {
                throw IllegalArgumentException("Native output is not valid UTF-8")
            }
        }

        private fun parseFinite(value: String, field: String): Double {
            val v = try { java.lang.Double.parseDouble(value) }
                catch (e: NumberFormatException) { throw IllegalArgumentException("$field not a number: $value") }
            if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v))
                throw IllegalArgumentException("$field not finite: $value")
            if (v < 0.0) throw IllegalArgumentException("$field negative: $value")
            return v
        }

        private fun parseTokens(value: String): Int {
            val parsed = try { java.lang.Long.parseLong(value) }
                catch (e: NumberFormatException) { throw IllegalArgumentException("tokens not an integer: $value") }
            if (parsed < 0 || parsed > MAX_TOKENS)
                throw IllegalArgumentException("tokens out of range [0..$MAX_TOKENS]: $value")
            return parsed.toInt()
        }
    }
}
