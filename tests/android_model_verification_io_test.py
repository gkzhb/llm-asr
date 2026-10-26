#!/usr/bin/env python3
"""Actual AndroidModelVerificationIo method contracts on explicit raw-FD host fake.
One finite compile <=270s, all JVM <=60s, outer420, Xmx256m. Not Android probe.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


lifecycle = load('_android_proof_owned', ROOT / 'tests/mixed_compiler_lifecycle.py')
FAKES = {
    'ErrnoException.java': 'package android.system; public class ErrnoException extends Exception {public final int errno;public ErrnoException(String n,int e){super(n);errno=e;}}',
    'StructTimespec.java': 'package android.system; public class StructTimespec {public long tv_sec,tv_nsec;public StructTimespec(long s,long n){tv_sec=s;tv_nsec=n;}}',
    'StructStat.java': 'package android.system; public class StructStat {public long st_dev,st_ino,st_size;public int st_mode;public StructTimespec st_mtim,st_ctim;}',
    'OsConstants.java': 'package android.system; public class OsConstants {public static final int ENOENT=2,O_RDONLY=0,O_WRONLY=1,O_CREAT=64,O_EXCL=128,O_NOFOLLOW=131072;public static boolean S_ISDIR(int m){return(m&0170000)==0040000;}public static boolean S_ISREG(int m){return(m&0170000)==0100000;}}',
    'Os.java': r'''package android.system;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Fake raw FD capability: no Java stream wrapper owns these descriptors. */
public final class Os {
 static final class Handle {String path;RandomAccessFile file;java.nio.channels.FileChannel dir;}
 static final Map<FileDescriptor,Handle> handles=new IdentityHashMap<>();
 public static int opened,closed,renames;public static String fail="";public static RuntimeException primary,closeFailure;
 public static int live(){return handles.size();}
 public static void fault(String at){if(fail.equals(at))throw primary;}
 public static StructStat lstat(String p)throws ErrnoException{
  try{Map<String,Object>a=Files.readAttributes(Paths.get(p),"unix:dev,ino,mode,size,lastModifiedTime,ctime",LinkOption.NOFOLLOW_LINKS);StructStat s=new StructStat();s.st_dev=((Number)a.get("dev")).longValue();s.st_ino=((Number)a.get("ino")).longValue();s.st_size=((Number)a.get("size")).longValue();s.st_mode=((Number)a.get("mode")).intValue();s.st_mtim=time((java.nio.file.attribute.FileTime)a.get("lastModifiedTime"));s.st_ctim=time((java.nio.file.attribute.FileTime)a.get("ctime"));return s;}catch(NoSuchFileException e){throw new ErrnoException("stat",2);}catch(IOException e){throw new ErrnoException("stat",5);}}
 static StructTimespec time(java.nio.file.attribute.FileTime f){java.time.Instant i=f.toInstant();return new StructTimespec(i.getEpochSecond(),i.getNano());}
 public static StructStat fstat(FileDescriptor fd)throws ErrnoException{return lstat(handles.get(fd).path);}
 public static FileDescriptor open(String p,int flags,int mode)throws ErrnoException{
  fault("open");try{if(Files.isSymbolicLink(Paths.get(p)))throw new IOException("nofollow");Handle h=new Handle();h.path=p;
   if(Files.isDirectory(Paths.get(p),LinkOption.NOFOLLOW_LINKS))h.dir=java.nio.channels.FileChannel.open(Paths.get(p),StandardOpenOption.READ);
   else{if((flags&OsConstants.O_EXCL)!=0)Files.createFile(Paths.get(p));h.file=new RandomAccessFile(p,(flags&OsConstants.O_WRONLY)!=0?"rw":"r");}
   FileDescriptor fd=h.file==null?new FileDescriptor():h.file.getFD();handles.put(fd,h);opened++;return fd;
  }catch(IOException e){throw new ErrnoException("open",5);}}
 public static int read(FileDescriptor fd,byte[] b,int o,int n)throws ErrnoException{fault("read");try{int got=handles.get(fd).file.read(b,o,n);return got<0?0:got;}catch(IOException e){throw new ErrnoException("read",5);}}
 public static int write(FileDescriptor fd,byte[] b,int o,int n)throws ErrnoException{fault("write");try{int count=Math.min(n,2);handles.get(fd).file.write(b,o,count);return count;}catch(IOException e){throw new ErrnoException("write",5);}}
 public static void fsync(FileDescriptor fd)throws ErrnoException{fault("fsync");try{Handle h=handles.get(fd);if(h.file!=null)h.file.getFD().sync();else h.dir.force(true);}catch(IOException e){throw new ErrnoException("sync",5);}}
 public static void close(FileDescriptor fd)throws ErrnoException{Handle h=handles.remove(fd);if(h==null)throw new AssertionError("raw descriptor double close");closed++;try{if(h.file!=null)h.file.close();else h.dir.close();}catch(IOException e){throw new ErrnoException("close",5);}if(closeFailure!=null)throw closeFailure;fault("close");}
 public static void rename(String a,String b)throws ErrnoException{renames++;try{Files.move(Paths.get(a),Paths.get(b),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){throw new ErrnoException("rename",5);}}
 public static void remove(String p)throws ErrnoException{try{Files.delete(Paths.get(p));}catch(IOException e){throw new ErrnoException("remove",5);}}
}
''',
}
CALLER = r'''import android.system.Os;import java.io.*;import java.nio.file.*;import org.llmasr.minimal.model.*;
public final class AndroidProofIoContract {
 static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 static void reset(){Os.fail="";Os.closeFailure=null;Os.primary=new IllegalStateException("primary");}
 static Throwable thrown(Runnable r){try{r.run();}catch(Throwable t){return t;}throw new AssertionError("missing failure");}
 public static void main(String[]args)throws Exception{
  Path base=Files.createTempDirectory("actual-android-proof-io-");
  AndroidModelVerificationIo io=new AndroidModelVerificationIo(()->base.toFile());byte[] bytes={1,2,3,4,5};
  try{
   require(Os.opened==0,"construct must not open FD");io.write("receipt",bytes);
   require(java.util.Arrays.equals(io.read("receipt",16),bytes),"actual adapter read/write bytes");
   require(Os.live()==0&&Os.opened==Os.closed,"every raw open must actually close");
   for(int i=0;i<40;i++){io.write("ledger",bytes);io.read("ledger",16);}
   require(Os.live()==0&&Os.opened==Os.closed,"repeated actual adapter no live FD growth");
   Throwable limit=thrown(()->{try{io.read("receipt",2);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
   require(Os.live()==0,"bounded-read failure closes raw FD");
   for(String stage:new String[]{"read","write","fsync","close"}){
    reset();Os.fail=stage;int rename=Os.renames;Throwable seen=thrown(()->{try{if(stage.equals("read"))io.read("receipt",16);else io.write("receipt",bytes);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
    require(seen==Os.primary,"actual adapter preserves primary "+stage);
    require(Os.live()==0&&Os.opened==Os.closed,"failure raw close "+stage);
    if(!stage.equals("read"))require(Os.renames==rename,"actual close/write/fsync failure before rename");
   }
   reset();Os.fail="read";Os.closeFailure=new IllegalArgumentException("distinct-close");
   Throwable seen=thrown(()->{try{io.read("receipt",16);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
   require(seen==Os.primary&&seen.getSuppressed().length==1&&seen.getSuppressed()[0]==Os.closeFailure,"read primary plus distinct actual-close suppressed");
   reset();require(Os.live()==0&&Os.opened==Os.closed,"raw FD final balance");
   System.out.println("PASS actual AndroidModelVerificationIo raw-FD single owner/partial write/failure/close-before-rename contracts (host fake, not Android)");
  }finally{reset();require(Os.live()==0,"retain inputs if descriptor still active");try(java.util.stream.Stream<Path> ps=Files.walk(base)){for(Path p:(Iterable<Path>)ps.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(p);}}
 }
}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--contracts', required=True, type=Path)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    args = parser.parse_args()
    directory = args.evidence_dir.resolve()
    directory.mkdir(parents=True, exist_ok=False)
    sources = []
    for name, text in FAKES.items():
        p = directory / 'source/android/system' / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text + '\n')
        sources.append(p)
    caller = directory / 'source/AndroidProofIoContract.java'
    caller.write_text(CALLER)
    sources.append(caller)
    actual = ROOT / 'android/app/src/org/llmasr/minimal/model/AndroidModelVerificationIo.kt'
    copy = directory / 'source' / actual.name
    copy.write_bytes(actual.read_bytes())
    sources.append(copy)
    (directory / 'inputs.json').write_text(json.dumps([{'path': str(p), 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources], indent=2) + '\n')
    primary = None
    try:
        result = lifecycle.run(['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(directory / 'classes'), str(args.contracts.resolve()), *map(str, sources)], directory, 'compile', 270, dependency=args.contracts)
        if result.returncode:
            raise RuntimeError('actual adapter compile failure ' + result.stderr.decode())
        result = lifecycle.run(['java', '-Xmx256m', '-cp', str(directory / 'classes') + ':' + str(args.contracts.resolve()) + ':' + os.environ['KOTLIN_HOME'] + '/lib/kotlin-stdlib.jar', 'AndroidProofIoContract'], directory, 'actual-adapter', 60, dependency=args.contracts)
        print(result.stdout.decode(), flush=True)
        if result.returncode:
            raise AssertionError(result.stderr.decode())
    except BaseException as failure:
        primary = failure
        raise
    finally:
        try:
            lifecycle.owned.cleanup_workspace(directory, remove=False)
        except BaseException as failure:
            if primary is None:
                raise
            primary.add_note('unknown exit inputs retained: ' + repr(failure))


if __name__ == '__main__':
    main()
