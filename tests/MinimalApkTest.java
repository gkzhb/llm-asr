import org.llmasr.minimal.audio.WaveInput;
import org.llmasr.minimal.audio.AsrText;
import org.llmasr.minimal.audio.PcmWave;
import org.llmasr.minimal.audio.RecordingControl;
import java.io.*;
import java.nio.file.*;
public final class MinimalApkTest {
    static int checks=0;
    static void require(boolean ok) { checks++; if(!ok)throw new AssertionError("check "+checks); }
    static void reject(byte[] data,File out) throws Exception {
        boolean failed=false;
        try { WaveInput.canonicalize(new ByteArrayInputStream(data),out); } catch(IOException e) { failed=true; }
        require(failed);
    }
    static byte[] wave(int samples) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write("RIFF".getBytes("US-ASCII")); le(out,36+samples*2,4); out.write("WAVEfmt ".getBytes("US-ASCII"));
        le(out,16,4); le(out,1,2); le(out,1,2); le(out,16000,4); le(out,32000,4); le(out,2,2); le(out,16,2);
        out.write("data".getBytes("US-ASCII")); le(out,samples*2,4); out.write(new byte[samples*2]); return out.toByteArray();
    }
    static void le(OutputStream out,int value,int count) throws Exception { for(int i=0;i<count;i++)out.write((value >>> (8*i))&255); }
    public static void main(String[] args) throws Exception {
        byte[] good=Files.readAllBytes(Paths.get(args[0])); File out=File.createTempFile("wav-test-",".wav");
        try {
            require(Math.abs(WaveInput.canonicalize(new ByteArrayInputStream(good),out)-4.2039375)<1e-9);
            byte[] canonical=Files.readAllBytes(out.toPath()); require(canonical.length==44+67263*2);
            require(WaveInput.canonicalize(new ByteArrayInputStream(canonical),out)==4.2039375);
            require(WaveInput.canonicalize(new ByteArrayInputStream(wave(1600)),out)==0.1);
            require(WaveInput.canonicalize(new ByteArrayInputStream(wave(480000)),out)==30.0);
            reject(wave(1599),out); reject(wave(480001),out);
            // Unknown odd chunk plus pad is accepted, malformed missing pad is not.
            ByteArrayOutputStream extra=new ByteArrayOutputStream();
            extra.write(canonical,0,12); extra.write("JUNK".getBytes("US-ASCII")); le(extra,1,4); extra.write(7); extra.write(0);
            extra.write(canonical,12,canonical.length-12); byte[] padded=extra.toByteArray();
            int riff=padded.length-8; for(int i=0;i<4;i++)padded[4+i]=(byte)(riff >>> (8*i));
            require(WaveInput.canonicalize(new ByteArrayInputStream(padded),out)==4.2039375);
            reject(java.util.Arrays.copyOf(padded,padded.length-1),out);
            reject(new byte[12],out);
            byte[] b=canonical.clone(); b[0]='X'; reject(b,out);
            b=canonical.clone(); b[4]=0; reject(b,out);
            b=canonical.clone(); b[20]=3; reject(b,out); // float
            b=canonical.clone(); b[22]=2; reject(b,out); // stereo
            b=canonical.clone(); b[24]=0; reject(b,out); // sample rate
            b=canonical.clone(); b[34]=8; reject(b,out); // bit depth
            b=canonical.clone(); b[40]=(byte)255; reject(b,out); // chunk overflow
            reject(new byte[2*1024*1024+1],out);
            require(AsrText.display("language English<asr_text>Hello.<|im_end|>\n").equals("Hello."));
            require(AsrText.display("甚至出现交易几乎停滞的情况。").equals("甚至出现交易几乎停滞的情况。"));
            require(AsrText.display("<asr_text>").equals(""));
            PcmWave capture=new PcmWave(); short[] block=new short[1600]; block[0]=(short)-32768; block[1]=32767;
            require(capture.append(block,block.length)==1600);
            byte[] mic=capture.finish(); require(mic[44]==0 && mic[45]==(byte)128 && mic[46]==(byte)255 && mic[47]==127);
            require(WaveInput.canonicalize(new ByteArrayInputStream(mic),out)==0.1);
            for(int i=0;i<300;i++)capture.append(block,block.length);
            require(capture.samples()==480000); require(capture.append(block,block.length)==0);
            require(WaveInput.canonicalize(new ByteArrayInputStream(capture.finish()),out)==30.0);
            boolean tooShort=false; try { new PcmWave().finish(); } catch(IOException e) { tooShort=true; } require(tooShort);
            boolean badLength=false; try { capture.append(block,1601); } catch(IllegalArgumentException e) { badLength=true; } require(badLength);
            RecordingControl control=new RecordingControl(); require(!control.stopped() && !control.cancelled());
            control.stop(); require(control.stopped() && !control.cancelled());
            control.cancel(); control.stop(); require(control.stopped() && control.cancelled());
            System.out.println("PASS "+checks+" minimal APK Java checks");
        } finally { out.delete(); }
    }
}
