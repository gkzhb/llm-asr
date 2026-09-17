import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic production IO boundaries. All gates/threads have bounded teardown. */
public final class ModelRepositoryCancelTest {
    static int checks;
    static void check(boolean b) { checks++; if (!b) throw new AssertionError("check " + checks); }
    static byte[] bytes(int n) { byte[] b=new byte[n]; for(int i=0;i<n;i++) b[i]=(byte)i; return b; }
    static ModelEntry entry(String name, byte[] b) throws Exception {
        StringBuilder s=new StringBuilder();
        for(byte v:MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x",v&255));
        return new ModelEntry(name,b.length,s.toString());
    }
    static ModelManifest manifest(byte[] b, String... names) throws Exception {
        List<ModelEntry> es=new ArrayList<>(); for(String n:names) es.add(entry(n,b)); return new ModelManifest(es);
    }
    static File dir() throws IOException { return Files.createTempDirectory("model-core-").toFile(); }
    static final class Source implements ModelSource {
        final Map<String,byte[]> data=new HashMap<>();
        int opens; boolean closed; boolean failRead, failClose;
        Gate readGate, closeGate;
        Source(byte[] b, String... names) { for(String n:names) data.put(n,b); }
        public Map<String,UriRef> enumerate(Collection<String> names,int max) {
            Map<String,UriRef> refs=new HashMap<>(); for(String n:names) if(data.containsKey(n)) refs.put(n,new UriRef(n)); return refs;
        }
        public InputStream open(UriRef r) {
            opens++;
            return new FilterInputStream(new ByteArrayInputStream(data.get(r.handle()))) {
                public int read(byte[] b,int off,int len) throws IOException {
                    if(readGate!=null) readGate.block();
                    if(failRead) throw new IOException("injected read");
                    return super.read(b,off,len); // includes real EOF, never returns zero forever
                }
                public void close() throws IOException {
                    try { if(closeGate!=null) closeGate.block(); super.close(); }
                    finally { closed=true; }
                    if(failClose) throw new IOException("injected close");
                }
            };
        }
    }
    static final class Gate implements AutoCloseable {
        final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        void block() throws IOException {
            entered.countDown();
            try { if(!release.await(4,TimeUnit.SECONDS)) throw new IOException("gate timeout"); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        }
        void await() throws Exception { check(entered.await(3,TimeUnit.SECONDS)); }
        public void close() { release.countDown(); }
    }
    interface IO { void run() throws Exception; }
    static final class Worker implements AutoCloseable {
        final AtomicReference<Throwable> error=new AtomicReference<>();
        final Thread thread;
        Worker(IO io) { thread=new Thread(()->{try{io.run();}catch(Throwable e){error.set(e);}}); thread.setDaemon(true); thread.start(); }
        void join() throws Exception { thread.join(5000); check(!thread.isAlive()); }
        public void close() throws Exception { thread.join(5000); if(thread.isAlive()){thread.interrupt();thread.join(1000);throw new AssertionError("worker leaked");} }
    }
    static final class Control implements ModelRepository.CancelGate {
        final ModelOperationControl control=new ModelOperationControl();
        final ModelOperationControl.Operation op=control.tryBegin(ModelOperationControl.Kind.IMPORT);
        public boolean cancelled(){return control.isCancelled(op);}
        public boolean reservePublish(){return control.tryReservePublish(op);}
        void cancel(){control.requestCancel(op.id);}
    }
    static IOException fails(IO io) throws Exception {
        try {io.run();throw new AssertionError("expected IO failure");}catch(IOException e){checks++;return e;}
    }
    public static void main(String[] args) throws Exception {
        byte[] b=bytes(64);
        // Same request cancellation before IO and at a blocked provider read.
        {
            File d=dir(); ModelRepository r=new ModelRepository(manifest(b,"a"),d,()->Long.MAX_VALUE);
            Control c=new Control(); c.cancel(); Source src=new Source(b,"a");
            check(fails(()->r.importFrom(src,null,c)) instanceof ModelRepository.CancelledException);
            check(src.opens==0); check(!new File(d,"a").exists());
        }
        {
            File d=dir(); ModelRepository r=new ModelRepository(manifest(b,"a"),d,()->Long.MAX_VALUE);
            Control c=new Control(); Source src=new Source(b,"a"); Gate g=new Gate(); src.readGate=g;
            try(Worker w=new Worker(()->r.importFrom(src,null,c))) {
                try { g.await(); c.cancel(); check(w.thread.isAlive()); } finally {g.close();}
                w.join(); check(w.error.get() instanceof ModelRepository.CancelledException);
            }
            check(src.closed); check(!new File(d,"a").exists()); check(!new File(d,"a.part").exists());
        }
        // Cancel wins immediately before the second file's reservation (not a whole-op reservation).
        {
            File d=dir(); ModelRepository r=new ModelRepository(manifest(b,"a","b"),d,()->Long.MAX_VALUE);
            Source src=new Source(b,"a","b"); Control c=new Control();
            fails(()->r.importFrom(src,(stage,file,i,n,fd,ft,sd,st,re)->{
                if(stage==ModelRepository.Stage.PUBLISHING && "b".equals(file)) c.cancel();
            },c));
            check(new File(d,"a").isFile()); check(!new File(d,"b").exists()); check(!new File(d,"b.part").exists());
            Source retry=new Source(b,"b"); r.importFrom(retry,null); check(retry.opens==1); r.verifyAll();
            Source empty=new Source(b); r.importFrom(empty,null); check(empty.opens==0);
        }
        // Publish wins, then cancel during actual rename seam: keep this file, stop the next.
        {
            File d=dir(); Gate g=new Gate(); Control c=new Control();
            ModelRepository r=new ModelRepository(manifest(b,"a","b"),d,()->Long.MAX_VALUE){
                protected void publishPart(File part,File dest)throws IOException {g.block();super.publishPart(part,dest);}
            };
            try(Worker w=new Worker(()->r.importFrom(new Source(b,"a","b"),null,c))) {
                try {g.await(); c.cancel();} finally {g.close();}
                w.join(); check(w.error.get() instanceof ModelRepository.CancelledException);
            }
            check(new File(d,"a").isFile()); check(!new File(d,"b").exists());
        }
        // Cancellation inside each hashing stage, including final file's final block.
        for(ModelRepository.Stage target:new ModelRepository.Stage[]{ModelRepository.Stage.CHECKING_EXISTING,ModelRepository.Stage.VERIFYING_FILE,ModelRepository.Stage.FINAL_VERIFY}) {
            File d=dir(); byte[] big=bytes(1024*1024+3);
            ModelRepository r=new ModelRepository(manifest(big,"a"),d,()->Long.MAX_VALUE);
            if(target==ModelRepository.Stage.CHECKING_EXISTING) Files.write(new File(d,"a").toPath(),big);
            Control c=new Control();
            IOException e=fails(()->r.importFrom(new Source(big,"a"),(stage,file,i,n,fd,ft,sd,st,re)->{
                if(stage==target && fd>0) c.cancel();
            },c));
            check(e instanceof ModelRepository.CancelledException);
            check(!new File(d,"a.part").exists());
            check(new File(d,"a").exists() == (target!=ModelRepository.Stage.VERIFYING_FILE));
        }
        // Actual read/write/output-close/input-close/rename faults never publish; streams close.
        for(String fault:Arrays.asList("read","write","out-close","in-close","rename")) {
            File d=dir(); Source src=new Source(b,"a"); src.failRead=fault.equals("read"); src.failClose=fault.equals("in-close");
            ModelRepository r=new ModelRepository(manifest(b,"a"),d,()->Long.MAX_VALUE){
                protected OutputStream openPart(File f)throws IOException {
                    return new FilterOutputStream(super.openPart(f)) {
                        public void write(byte[] b,int o,int n)throws IOException {if(fault.equals("write"))throw new IOException("write");out.write(b,o,n);}
                        public void close()throws IOException {super.close();if(fault.equals("out-close"))throw new IOException("close");}
                    };
                }
                protected void publishPart(File p,File f)throws IOException {if(fault.equals("rename"))throw new IOException("rename");super.publishPart(p,f);}
            };
            fails(()->r.importFrom(src,null)); check(src.closed); check(!new File(d,"a").exists());check(!new File(d,"a.part").exists());
        }
        // Preflight is across all missing sources; safe parts before total space budget.
        {
            File d=dir(); ModelRepository r=new ModelRepository(manifest(b,"a","b"),d,()->Long.MAX_VALUE);
            Source src=new Source(b,"a"); fails(()->r.importFrom(src,null));check(src.opens==0);check(!new File(d,"a").exists());
            Files.write(new File(d,"a.part").toPath(),b);
            ModelRepository low=new ModelRepository(manifest(b,"a","b"),d,()->new File(d,"a.part").exists()?0:ModelRepository.SPACE_PAD+64);
            Source all=new Source(b,"a","b");fails(()->low.importFrom(all,null));check(all.opens==0);check(!new File(d,"a.part").exists());
        }
        // Stage counters bounded; copy 100% is not publish; real EOF including empty payload.
        for(int size:new int[]{0,3,1024*1024+7}) {
            byte[] data=bytes(size); File d=dir();
            ModelRepository r=new ModelRepository(manifest(data,"a"),d,()->Long.MAX_VALUE);
            AtomicBoolean sawCopy=new AtomicBoolean(), sawHash=new AtomicBoolean();
            r.importFrom(new Source(data,"a"),(stage,file,i,n,fd,ft,sd,st,re)->{
                check(fd>=0 && fd<=ft && sd>=0 && sd<=st); check(i>=0);
                if(stage==ModelRepository.Stage.COPYING){sawCopy.set(true);check(!new File(d,"a").exists());}
                if(stage==ModelRepository.Stage.VERIFYING_FILE)sawHash.set(true);
            });
            check(sawCopy.get()&&sawHash.get());r.verifyAll();
        }
        System.out.println("PASS "+checks+" deterministic repository cancel/IO/progress checks");
    }
}
