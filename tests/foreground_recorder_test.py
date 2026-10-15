#!/usr/bin/env python3
"""Run real capture + real Kotlin gate/PCM with fake Android media; NOT microphone acceptance."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'android/app/src/org/llmasr/minimal/audio'
STUBS = {
    'android/media/AudioFormat.java': '''package android.media;
public class AudioFormat { public static final int CHANNEL_IN_MONO=1,ENCODING_PCM_16BIT=2; }
''',
    'android/media/MediaRecorder.java': '''package android.media;
public class MediaRecorder { public static class AudioSource { public static final int MIC=1; } }
''',
    'android/os/SystemClock.java': '''package android.os;
public class SystemClock { public static long elapsedRealtime(){return 1;} public static void sleep(long n){} }
''',
    'android/media/AudioRecord.java': '''package android.media;
import org.llmasr.minimal.audio.RecordingControl;
public class AudioRecord {
public static final int STATE_INITIALIZED=1,RECORDSTATE_RECORDING=3,READ_NON_BLOCKING=1;
public static String fail=""; public static int made,starts,reads,stops,releases;
public static RecordingControl control; public static final AssertionError fatal=new AssertionError("fixture fatal");
static void hit(String phase){if(fail.equals(phase))throw new SecurityException("content://PRIVATE/secret");}
public static void reset(String phase,RecordingControl c){fail=phase;control=c;made=starts=reads=stops=releases=0;}
public static int getMinBufferSize(int a,int b,int c){hit("minimum");return 6400;}
public AudioRecord(int a,int b,int c,int d,int e){hit("constructor");made++;}
public int getState(){hit("state");return STATE_INITIALIZED;} public int getSampleRate(){return 16000;}
public void startRecording(){starts++;hit("start");}
public int getRecordingState(){hit("recordingState");return starts>0?RECORDSTATE_RECORDING:0;}
public int read(short[] b,int o,int n,int mode){reads++;if(fail.equals("fatal"))throw fatal;
hit("read");control.stop();return n;}
public void stop(){stops++;if(fail.equals("illegalStop"))throw new IllegalStateException();hit("stop");}
public void release(){releases++;hit("release");}
}
''',
    'RecorderContractTest.java': '''import org.llmasr.minimal.audio.*;
import android.media.AudioRecord; import java.io.IOException; import java.util.concurrent.CancellationException;
public class RecorderContractTest {
static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
public static void main(String[] args)throws Exception{
String phase=args[0]; RecordingControl c=new RecordingControl();AudioRecord.reset(phase,c);
if(phase.equals("cancel"))c.cancel();
Throwable observed=null;byte[] result=null;
try{result=ForegroundRecorder.capture(c,s->{});}catch(Throwable t){observed=t;}
check(c.released(),"captureReleased acknowledgement must survive every exit");
int expectedMade=(phase.equals("minimum")||phase.equals("constructor")||phase.equals("cancel"))?0:1;
check(AudioRecord.made==expectedMade,"constructor identity");
check(AudioRecord.releases==expectedMade,"created recorder released exactly once even after stop/state failure");
if(phase.equals("cancel")){
check(observed instanceof CancellationException,"cancel stays cancellation");
check(AudioRecord.starts==0 && !c.tryCommitInference(),"cancel wins before create/start/commit");
}else if(phase.equals("fatal")){
check(observed==AudioRecord.fatal,"Error must propagate, not normalize");
}else if(phase.equals("success")||phase.equals("illegalStop")){
check(observed==null && result!=null && result.length==3244,"success retains real PCM/WAV");
check(AudioRecord.starts==1 && AudioRecord.reads==1 && AudioRecord.stops==1,"start/read/stop once");
check(c.tryCommitInference(),"normal gate commit after release");
}else{
check(observed instanceof IOException,"permission "+phase+" must be normalized IOException, got "+
(observed==null?"none":observed.getClass().getName()));
check("麦克风权限不可用，请重新授权后重试".equals(observed.getMessage()),"fixed safe permission message");
check(observed.getCause()==null && observed.getSuppressed().length==0,"no raw permission cause/URI retained");
if(phase.equals("minimum")||phase.equals("constructor")||phase.equals("state")||phase.equals("start"))
check(!c.tryCommitInference(),"failed start cannot commit");
}
System.out.println("PASS production capture fake Android phase="+phase+" (NOT device)");
}}
''',
}


def compile_capture(root, source):
    paths = []
    for name, text in STUBS.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        paths.append(path)
    production = root / 'ForegroundRecorder.java'
    production.write_text(source)
    paths += [production, BASE / 'RecordingControl.kt', BASE / 'PcmWave.kt', BASE / 'WaveInput.java']
    classes = root / 'classes'
    result = subprocess.run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '',
                             *map(str, paths)], capture_output=True, text=True, timeout=150)
    if result.returncode:
        raise AssertionError('Compilation failure is NOT target red:\n' + result.stdout + result.stderr)
    return str(classes) + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar'


class RecorderTests(unittest.TestCase):
    def test_permission_revocation_and_cleanup(self):
        with tempfile.TemporaryDirectory(prefix='recorder-contract-') as temporary:
            cp = compile_capture(Path(temporary), (BASE / 'ForegroundRecorder.java').read_text())
            for phase in ('minimum', 'constructor', 'state', 'start', 'read', 'recordingState',
                          'stop', 'release', 'cancel', 'fatal', 'success', 'illegalStop'):
                with self.subTest(phase=phase):
                    result = subprocess.run(['java', '-cp', cp, 'RecorderContractTest', phase],
                                            capture_output=True, text=True, timeout=10)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    print(result.stdout.strip())


if __name__ == '__main__':
    unittest.main()
