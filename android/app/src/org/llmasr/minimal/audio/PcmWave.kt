package org.llmasr.minimal.audio

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Bounded capture buffer, sample-count limit; never relabels a different sample rate. */
class PcmWave {
    private val pcm = ByteArrayOutputStream()

    fun samples(): Int = pcm.size() / 2
    fun append(input: ShortArray?, length: Int): Int {
        // Match Java's short-circuit ordering: a negative length wins over null input.
        if (length < 0 || length > input!!.size) throw IllegalArgumentException("Invalid PCM length")
        val count = Math.min(length, MAX_SAMPLES - samples())
        for (i in 0 until count) {
            pcm.write(input[i].toInt() and 255)
            pcm.write((input[i].toInt() ushr 8) and 255)
        }
        return count
    }

    @Throws(IOException::class)
    fun finish(): ByteArray {
        if (samples() < MIN_SAMPLES) throw IOException("录音不足0.1秒，请重新录制")
        val out = ByteArrayOutputStream(pcm.size() + 44)
        out.write("RIFF".toByteArray(StandardCharsets.US_ASCII)); WaveInput.le(out, 36L + pcm.size(), 4)
        out.write("WAVEfmt ".toByteArray(StandardCharsets.US_ASCII)); WaveInput.le(out, 16, 4)
        WaveInput.le(out, 1, 2); WaveInput.le(out, 1, 2); WaveInput.le(out, RATE.toLong(), 4); WaveInput.le(out, RATE * 2L, 4)
        WaveInput.le(out, 2, 2); WaveInput.le(out, 16, 2); out.write("data".toByteArray(StandardCharsets.US_ASCII))
        WaveInput.le(out, pcm.size().toLong(), 4); pcm.writeTo(out)
        return out.toByteArray()
    }

    companion object {
        const val RATE = 16000
        const val MAX_SAMPLES = RATE * 30
        const val MIN_SAMPLES = RATE / 10
    }
}
