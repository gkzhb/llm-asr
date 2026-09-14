package org.llmasr.minimal;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.CancellationException;

/** No Service and no retained recorder: the worker owns init/read/stop/release. */
public final class ForegroundRecorder {
    public interface Progress { void update(double seconds); }
    public static byte[] capture(RecordingControl control,Progress progress) throws IOException {
        AudioRecord recorder=null;
        try {
            if(control.cancelled())throw new CancellationException("录音已取消");
            int minimum=AudioRecord.getMinBufferSize(PcmWave.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0 || minimum>1024*1024)throw new IOException("设备不支持16kHz单声道PCM录音，请使用WAV文件");
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,PcmWave.RATE,AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum,6400));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED || recorder.getSampleRate()!=PcmWave.RATE)
                throw new IOException("麦克风初始化失败或采样率不支持，请关闭其他录音应用");
            final AudioRecord backend=recorder;
            if(!control.start(() -> backend.startRecording()))throw new CancellationException("录音已取消");
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("无法启动麦克风");
            PcmWave pcm=new PcmWave(); short[] buffer=new short[1600];
            long start=SystemClock.elapsedRealtime(), lastData=start, lastUi=start;
            while(!control.stopped() && pcm.samples()<PcmWave.MAX_SAMPLES) {
                long now=SystemClock.elapsedRealtime();
                if(now-start>35000)throw new IOException("录音时间异常，已停止并丢弃");
                int n=recorder.read(buffer,0,buffer.length,AudioRecord.READ_NON_BLOCKING);
                if(n<0)throw new IOException("麦克风读取失败："+n+"；请检查权限或其他录音应用");
                if(n>0) { pcm.append(buffer,n); lastData=now; }
                else {
                    if(now-lastData>3000)throw new IOException("麦克风未返回音频，请检查系统麦克风开关");
                    SystemClock.sleep(10); // non-blocking read prevents stop/release deadlock
                }
                if(now-lastUi>=100) { progress.update(pcm.samples()/(double)PcmWave.RATE); lastUi=now; }
            }
            if(control.cancelled())throw new CancellationException("录音已取消，音频已丢弃");
            return pcm.finish();
        } finally {
            try {
                if(recorder!=null) {
                    try { if(recorder.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)recorder.stop(); }
                    catch(IllegalStateException ignored) { /* always release even after an audio server error */ }
                    finally { recorder.release(); }
                }
            } finally { control.captureReleased(); }
        }
    }
}
