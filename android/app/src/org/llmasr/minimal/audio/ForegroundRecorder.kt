package org.llmasr.minimal.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.IOException
import java.util.concurrent.CancellationException

/** No Service and no retained recorder: the worker owns init/read/stop/release. */
class ForegroundRecorder {
    fun interface Progress { fun update(seconds: Double) }
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        open fun capture(control: RecordingControl?, progress: Progress?): ByteArray {
            var recorder: AudioRecord? = null
            try {
                try {
                    try {
                        if (control!!.cancelled()) throw CancellationException("录音已取消")
                        val minimum = AudioRecord.getMinBufferSize(PcmWave.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        if (minimum <= 0 || minimum > 1024 * 1024) throw IOException("设备不支持16kHz单声道PCM录音，请使用WAV文件")
                        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, PcmWave.RATE, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 6400))
                        if (recorder.state != AudioRecord.STATE_INITIALIZED || recorder.sampleRate != PcmWave.RATE)
                            throw IOException("麦克风初始化失败或采样率不支持，请关闭其他录音应用")
                        val backend = recorder
                        if (!control.start { backend.startRecording() }) throw CancellationException("录音已取消")
                        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IOException("无法启动麦克风")
                        val pcm = PcmWave()
                        val buffer = ShortArray(1600)
                        val start = SystemClock.elapsedRealtime()
                        var lastData = start
                        var lastUi = start
                        while (!control.stopped() && pcm.samples() < PcmWave.MAX_SAMPLES) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - start > 35000) throw IOException("录音时间异常，已停止并丢弃")
                            val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                            if (count < 0) throw IOException("麦克风读取失败：" + count + "；请检查权限或其他录音应用")
                            if (count > 0) { pcm.append(buffer, count); lastData = now }
                            else {
                                if (now - lastData > 3000) throw IOException("麦克风未返回音频，请检查系统麦克风开关")
                                SystemClock.sleep(10)
                            }
                            if (now - lastUi >= 100) { progress!!.update(pcm.samples() / PcmWave.RATE.toDouble()); lastUi = now }
                        }
                        if (control.cancelled()) throw CancellationException("录音已取消，音频已丢弃")
                        return pcm.finish()
                    } finally {
                        try {
                            if (recorder != null) {
                                try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                                catch (ignored: IllegalStateException) { /* Always release after an audio server error. */ }
                                finally { recorder.release() }
                            }
                        } finally { control!!.captureReleased() }
                    }
                } catch (denied: SecurityException) {
                    throw IOException("麦克风权限不可用，请重新授权后重试")
                }
            } finally { /* Preserve the original nested ownership boundary. */ }
        }
    }
}
