package org.llmasr.minimal.audio;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded capture buffer, sample-count limit; never relabels a different sample rate. */
public final class PcmWave {
    public static final int RATE=16000, MAX_SAMPLES=RATE*30, MIN_SAMPLES=RATE/10;
    private final ByteArrayOutputStream pcm=new ByteArrayOutputStream();
    public int samples() { return pcm.size()/2; }
    public int append(short[] input,int length) {
        if(length<0 || length>input.length)throw new IllegalArgumentException("Invalid PCM length");
        int count=Math.min(length,MAX_SAMPLES-samples());
        for(int i=0;i<count;i++) { pcm.write(input[i]&255); pcm.write((input[i]>>>8)&255); }
        return count;
    }
    public byte[] finish() throws IOException {
        if(samples()<MIN_SAMPLES)throw new IOException("录音不足0.1秒，请重新录制");
        ByteArrayOutputStream out=new ByteArrayOutputStream(pcm.size()+44);
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII)); WaveInput.le(out,36+pcm.size(),4);
        out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)); WaveInput.le(out,16,4);
        WaveInput.le(out,1,2); WaveInput.le(out,1,2); WaveInput.le(out,RATE,4); WaveInput.le(out,RATE*2,4);
        WaveInput.le(out,2,2); WaveInput.le(out,16,2); out.write("data".getBytes(StandardCharsets.US_ASCII));
        WaveInput.le(out,pcm.size(),4); pcm.writeTo(out); return out.toByteArray();
    }
}
