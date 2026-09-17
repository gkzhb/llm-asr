package org.llmasr.minimal.audio;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Strict bounded PCM input; canonical output keeps native decoding unambiguous. */
public final class WaveInput {
    private static final int MAX_INPUT = 2 * 1024 * 1024;
    static int u16(byte[] b, int p) { return (b[p]&255) | ((b[p+1]&255)<<8); }
    static long u32(byte[] b, int p) { return (long)u16(b,p) | ((long)u16(b,p+2)<<16); }
    static String tag(byte[] b, int p) { return new String(b,p,4,StandardCharsets.US_ASCII); }
    static void le(OutputStream out, long n, int bytes) throws IOException {
        for (int i=0;i<bytes;i++) out.write((int)(n >>> (8*i)) & 255);
    }
    public static double canonicalize(InputStream in, File output) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n=in.read(buffer))!=-1) {
            if (bytes.size()+n > MAX_INPUT) throw new IOException("WAV 文件超过 2 MiB 上限");
            bytes.write(buffer,0,n);
        }
        byte[] b=bytes.toByteArray();
        if (b.length<44 || !tag(b,0).equals("RIFF") || !tag(b,8).equals("WAVE") || u32(b,4)!=b.length-8)
            throw new IOException("无效 RIFF/WAVE 文件");
        boolean fmt=false; int data=-1, size=0;
        int p=12;
        while (p+8<=b.length) {
            String id=tag(b,p); long len=u32(b,p+4); long end=p+8L+len;
            if (end>b.length) throw new IOException("WAV chunk 越界");
            if (id.equals("fmt ")) {
                if (fmt || len<16 || u16(b,p+8)!=1 || u16(b,p+10)!=1 || u32(b,p+12)!=16000
                    || u32(b,p+16)!=32000 || u16(b,p+20)!=2 || u16(b,p+22)!=16)
                    throw new IOException("仅支持 PCM16 / 单声道 / 16000 Hz WAV");
                fmt=true;
            } else if (id.equals("data")) {
                if (data!=-1) throw new IOException("不支持多个 data chunk");
                data=p+8; size=(int)len;
            }
            p=(int)(end+(len&1));
        }
        if (p!=b.length || !fmt || data<0 || size<3200 || size>960000 || (size&1)!=0)
            throw new IOException("音频必须为 0.1–30 秒且 WAV 结构完整");
        try (OutputStream out=new FileOutputStream(output)) {
            out.write("RIFF".getBytes(StandardCharsets.US_ASCII)); le(out,36+size,4);
            out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)); le(out,16,4);
            le(out,1,2); le(out,1,2); le(out,16000,4); le(out,32000,4); le(out,2,2); le(out,16,2);
            out.write("data".getBytes(StandardCharsets.US_ASCII)); le(out,size,4); out.write(b,data,size);
        }
        return size/32000.0;
    }
}
