import org.llmasr.minimal.asr.NativeResponse
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Direct strict production parser checks; host only. */
class NativeResponseTest {
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check $checks") }
        private fun bytes(s: String): ByteArray = s.toByteArray(StandardCharsets.UTF_8)
        @JvmStatic @Throws(Exception::class) @Suppress("UNUSED_PARAMETER")
        fun main(args: Array<String>?) {
            val good = NativeResponse.parse(bytes("3.14 0.27 42\n<asr_text>Hello world.<|im_end|>"))
            check(good.loadSeconds == 3.14)
            check(good.inferenceSeconds == 0.27)
            check(good.generatedTokens == 42)
            check(good.raw == "<asr_text>Hello world.<|im_end|>")
            check(good.display == "Hello world.")
            val zero = NativeResponse.parse(bytes("0 0 0\n<asr_text>"))
            check(zero.loadSeconds == 0.0 && zero.inferenceSeconds == 0.0 && zero.generatedTokens == 0)
            check(zero.display == "")
            val emptyRaw = NativeResponse.parse(bytes("0.5 0.2 5\n"))
            check(emptyRaw.raw == "")
            check(emptyRaw.display == "")
            var rejectedNaN = false
            try { NativeResponse.parse(bytes("NaN 0.5 1\nx")) } catch (expected: IllegalArgumentException) { rejectedNaN = true }
            check(rejectedNaN)
            var rejectedInf = false
            try { NativeResponse.parse(bytes("1.0 Infinity 1\nx")) } catch (expected: IllegalArgumentException) { rejectedInf = true }
            check(rejectedInf)
            var rejectedNegLoad = false
            try { NativeResponse.parse(bytes("-0.1 0.5 1\nx")) } catch (expected: IllegalArgumentException) { rejectedNegLoad = true }
            check(rejectedNegLoad)
            var rejectedNegTokens = false
            try { NativeResponse.parse(bytes("0.1 0.5 -3\nx")) } catch (expected: IllegalArgumentException) { rejectedNegTokens = true }
            check(rejectedNegTokens)
            var wrongFields = false
            try { NativeResponse.parse(bytes("0.1 0.5\nx")) } catch (expected: IllegalArgumentException) { wrongFields = true }
            check(wrongFields)
            var tooManyFields = false
            try { NativeResponse.parse(bytes("0.1 0.5 1 extra\nx")) } catch (expected: IllegalArgumentException) { tooManyFields = true }
            check(tooManyFields)
            var noNewline = false
            try { NativeResponse.parse(bytes("0.1 0.5 1")) } catch (expected: IllegalArgumentException) { noNewline = true }
            if (!noNewline) {
                try { NativeResponse.parse(bytes("0.1 0.5 1")) } catch (expected: IllegalArgumentException) { noNewline = true }
            }
            check(noNewline)
            var nullPayload = false
            try { NativeResponse.parse(null) } catch (expected: IllegalArgumentException) { nullPayload = true }
            check(nullPayload)
            var emptyPayload = false
            try { NativeResponse.parse(ByteArray(0)) } catch (expected: IllegalArgumentException) { emptyPayload = true }
            check(emptyPayload)
            var nonNumeric = false
            try { NativeResponse.parse(bytes("0.1 hello 1\nx")) } catch (expected: IllegalArgumentException) { nonNumeric = true }
            check(nonNumeric)
            val emoji = NativeResponse.parse(bytes("1.0 1.0 1\n中文😀<|im_end|>"))
            check(emoji.raw == "中文😀<|im_end|>")
            check(emoji.display == "中文😀")
            var above = false
            try { NativeResponse.parse(bytes("0.0 0.0 129\nraw")) } catch (expected: IllegalArgumentException) { above = true }
            check(above)
            val at = NativeResponse.parse(bytes("0.0 0.0 128\nraw"))
            check(at.generatedTokens == 128)
            val bad = byteArrayOf(0xc3.toByte(), 0x28)
            val prefix = bytes("0.0 0.0 0\n")
            val payload = ByteArray(prefix.size + bad.size)
            System.arraycopy(prefix, 0, payload, 0, prefix.size)
            System.arraycopy(bad, 0, payload, prefix.size, bad.size)
            var invalidUtf8 = false
            try { NativeResponse.parse(payload) } catch (expected: IllegalArgumentException) { invalidUtf8 = true }
            check(invalidUtf8)
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            var decoderRejects = false
            try { decoder.decode(ByteBuffer.wrap(payload)).toString() } catch (expected: CharacterCodingException) { decoderRejects = true }
            check(decoderRejects)
            println("PASS $checks native response parser checks (host only; strict UTF-8, 128 token cap, empty raw allowed)")
        }
    }
}
