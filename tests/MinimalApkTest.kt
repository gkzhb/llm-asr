import org.llmasr.minimal.audio.WaveInput
import org.llmasr.minimal.audio.AsrText
import org.llmasr.minimal.audio.PcmWave
import org.llmasr.minimal.audio.RecordingControl
import java.io.*
import java.nio.file.*

class MinimalApkTest {
    companion object {
        private var checks = 0
        private fun require(ok: Boolean) { checks++; if (!ok) throw AssertionError("check $checks") }
        private fun reject(data: ByteArray, out: File) {
            var failed = false
            try { WaveInput.canonicalize(ByteArrayInputStream(data), out) } catch (e: IOException) { failed = true }
            require(failed)
        }
        private fun wave(samples: Int): ByteArray {
            val out = ByteArrayOutputStream()
            out.write("RIFF".toByteArray(charset("US-ASCII"))); le(out, 36 + samples * 2, 4)
            out.write("WAVEfmt ".toByteArray(charset("US-ASCII")))
            le(out, 16, 4); le(out, 1, 2); le(out, 1, 2); le(out, 16000, 4); le(out, 32000, 4); le(out, 2, 2); le(out, 16, 2)
            out.write("data".toByteArray(charset("US-ASCII"))); le(out, samples * 2, 4)
            out.write(ByteArray(samples * 2)); return out.toByteArray()
        }
        private fun le(out: OutputStream, value: Int, count: Int) { for (i in 0 until count) out.write((value ushr (8 * i)) and 255) }
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<String>?) {
            val good = Files.readAllBytes(Paths.get(args!![0]))
            val out = File.createTempFile("wav-test-", ".wav")
            try {
                require(Math.abs(WaveInput.canonicalize(ByteArrayInputStream(good), out) - 4.2039375) < 1e-9)
                val canonical = Files.readAllBytes(out.toPath()); require(canonical.size == 44 + 67263 * 2)
                require(WaveInput.canonicalize(ByteArrayInputStream(canonical), out) == 4.2039375)
                require(WaveInput.canonicalize(ByteArrayInputStream(wave(1600)), out) == 0.1)
                require(WaveInput.canonicalize(ByteArrayInputStream(wave(480000)), out) == 30.0)
                reject(wave(1599), out); reject(wave(480001), out)
                val extra = ByteArrayOutputStream()
                extra.write(canonical, 0, 12); extra.write("JUNK".toByteArray(charset("US-ASCII"))); le(extra, 1, 4); extra.write(7); extra.write(0)
                extra.write(canonical, 12, canonical.size - 12); val padded = extra.toByteArray()
                val riff = padded.size - 8; for (i in 0 until 4) padded[4 + i] = (riff ushr (8 * i)).toByte()
                require(WaveInput.canonicalize(ByteArrayInputStream(padded), out) == 4.2039375)
                reject(java.util.Arrays.copyOf(padded, padded.size - 1), out)
                reject(ByteArray(12), out)
                var b = canonical.clone(); b[0] = 'X'.code.toByte(); reject(b, out)
                b = canonical.clone(); b[4] = 0; reject(b, out)
                b = canonical.clone(); b[20] = 3; reject(b, out)
                b = canonical.clone(); b[22] = 2; reject(b, out)
                b = canonical.clone(); b[24] = 0; reject(b, out)
                b = canonical.clone(); b[34] = 8; reject(b, out)
                b = canonical.clone(); b[40] = 255.toByte(); reject(b, out)
                reject(ByteArray(2 * 1024 * 1024 + 1), out)
                require(AsrText.display("language English<asr_text>Hello.<|im_end|>\n") == "Hello.")
                require(AsrText.display("甚至出现交易几乎停滞的情况。") == "甚至出现交易几乎停滞的情况。")
                require(AsrText.display("<asr_text>") == "")
                val capture = PcmWave(); val block = ShortArray(1600); block[0] = (-32768).toShort(); block[1] = 32767
                require(capture.append(block, block.size) == 1600)
                val mic = capture.finish(); require(mic[44] == 0.toByte() && mic[45] == 128.toByte() && mic[46] == 255.toByte() && mic[47] == 127.toByte())
                require(WaveInput.canonicalize(ByteArrayInputStream(mic), out) == 0.1)
                for (i in 0 until 300) capture.append(block, block.size)
                require(capture.samples() == 480000); require(capture.append(block, block.size) == 0)
                require(WaveInput.canonicalize(ByteArrayInputStream(capture.finish()), out) == 30.0)
                var tooShort = false; try { PcmWave().finish() } catch (e: IOException) { tooShort = true }; require(tooShort)
                var badLength = false; try { capture.append(block, 1601) } catch (e: IllegalArgumentException) { badLength = true }; require(badLength)
                val control = RecordingControl(); require(!control.stopped() && !control.cancelled())
                control.stop(); require(control.stopped() && !control.cancelled())
                control.cancel(); control.stop(); require(control.stopped() && control.cancelled())
                println("PASS $checks minimal APK Java checks")
            } finally { out.delete() }
        }
    }
}
