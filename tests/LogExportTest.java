import org.llmasr.minimal.diagnostics.LogExportController;
import org.llmasr.minimal.diagnostics.LogExportPage;
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;
import org.llmasr.minimal.diagnostics.RuntimeLogText;
import org.llmasr.minimal.task.TaskCoordinator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Executes production page tickets/export owner. No Android picker/runtime claim. */
public final class LogExportTest {
    static int checks;
    static void check(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    static final class Queue implements Executor {
        final List<Runnable> work = new ArrayList<>();
        public void execute(Runnable r) { work.add(r); }
        void run() { work.remove(0).run(); }
    }
    static RuntimeLogStore logs() {
        RuntimeLogStore s = new RuntimeLogStore();
        s.append(RuntimeLogEventKind.APP_STARTUP,RuntimeLogSource.APP,null,"ok"); return s;
    }
    public static void main(String[] args) throws Exception {
        RuntimeLogStore s = logs(); Queue q = new Queue(); ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicInteger opens = new AtomicInteger();
        LogExportController<String> c = new LogExportController<>(target -> { opens.incrementAndGet(); return out; },q);
        LogExportPage<String> page = new LogExportPage<>(c);
        check(page.begin(s.snapshot()) == null,"not foreground"); page.foreground(true);
        LogExportController.Ticket first = page.begin(s.snapshot()); check(first != null,"begin");
        s.append(RuntimeLogEventKind.APP_READY,RuntimeLogSource.APP,null,"ok");
        check(first.snapshot.size() == 1,"frozen");
        try { first.snapshot.clear(); throw new AssertionError("mutable"); } catch (UnsupportedOperationException expected) { checks++; }
        page.foreground(false);
        check(page.result(first.requestCode,"target"),"result before resume deferred");
        check(!page.result(first.requestCode,"target"),"duplicate result"); check(opens.get()==0 && q.work.isEmpty(),"no background admission");
        page.foreground(true); check(c.state().phase==LogExportController.Phase.WRITING,"queued");
        page.destroy(); check(c.state().busy(),"destroy does not release IO slot");
        LogExportPage<String> next = new LogExportPage<>(c); next.foreground(true);
        check(next.begin(s.snapshot())==null,"busy no queue");
        q.run(); check(c.state().phase==LogExportController.Phase.SUCCEEDED,"success");
        String text = new String(out.toByteArray(),StandardCharsets.UTF_8);
        check(text.contains("APP_STARTUP") && !text.contains("APP_READY"),"actual selected snapshot not live");
        check(c.state().bytes == out.size() && out.size()>1,"byte count");
        LogExportController.Ticket second=next.begin(s.snapshot());check(second.requestCode!=first.requestCode,"unique request");
        check(!next.result(first.requestCode,"stale"),"old callback cannot consume new ticket");
        check(next.result(second.requestCode,null),"cancel");check(opens.get()==1&&!c.state().busy(),"cancel no IO");
        LogExportController.Ticket rotation=next.begin(s.snapshot());next.foreground(false);next.result(rotation.requestCode,"deferred");next.destroy();
        check(c.state().phase==LogExportController.Phase.EXPIRED,"destroy invalidates deferred");next.foreground(true);check(q.work.isEmpty(),"destroy never revive");
        for (int mode=0;mode<8;mode++) {
            final int fault=mode; Queue tasks=new Queue();
            final AssertionError expectedFatal=new AssertionError("fatal");
            LogExportController<String> failing = new LogExportController<>(target -> {
                if(fault==0)return null;
                if(fault==1)throw new IOException("private-content://secret");
                if(fault==2)throw new SecurityException("private");
                if(fault==3)throw new LinkageError("private");
                return new ByteArrayOutputStream() {
                    public void write(byte[] b,int off,int len) { if(fault==4)throw new IllegalStateException("private");super.write(b,off,len); }
                    public void flush() throws IOException { if(fault==5)throw new IOException("private"); }
                    public void close() throws IOException { if(fault==6)throw new IOException("private"); if(fault==7)throw expectedFatal; }
                };
            },tasks);
            LogExportController.Ticket t=failing.begin(failing.newPage(),s.snapshot());
            check(failing.submit(t,"user-selected-uri"),"failure admitted");
            Throwable caught=null;
            try { tasks.run(); } catch(Throwable failure) { caught=failure; }
            check(caught==(fault==7 ? expectedFatal : null),"exact fatal propagation outside capture");
            check(failing.state().phase==LogExportController.Phase.FAILED&&!failing.state().busy(),"failure releases slot including close");
            check(!RuntimeLogText.export(failing.state()).contains("private"),"safe failure status");
            check(failing.state().bytes==0,"failure no success bytes");
        }
        for (boolean primaryFatal : new boolean[]{false,true}) {
            Queue tasks=new Queue(); Error expectedFatal=new InternalError("fatal-close-sentinel");
            AtomicInteger closes=new AtomicInteger();
            LogExportController<String> compound=new LogExportController<>(target -> new OutputStream() {
                public void write(int b) throws IOException { if(primaryFatal)throw expectedFatal; throw new IOException("ordinary-write"); }
                public void close() throws IOException { closes.incrementAndGet(); if(primaryFatal)throw new IOException("ordinary-close"); throw expectedFatal; }
            },tasks);
            LogExportController.Ticket ticket=compound.begin(compound.newPage(),s.snapshot()); compound.submit(ticket,"target");
            Throwable caught=null; try { tasks.run(); } catch(Throwable failure) { caught=failure; }
            check(caught==expectedFatal,"compound exact fatal propagation");
            check(closes.get()==1 && !compound.state().busy() && compound.state().phase==LogExportController.Phase.FAILED,"compound close/release");
        }
        LogExportController<String> rejected=new LogExportController<>(target -> out,r -> {throw new RejectedExecutionException("private");});
        LogExportController.Ticket t=rejected.begin(rejected.newPage(),s.snapshot());rejected.submit(t,"target");
        check(rejected.state().phase==LogExportController.Phase.FAILED&&!rejected.state().busy(),"executor failure");
        CountDownLatch closing=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Executor background=r -> new Thread(() -> {try{r.run();}catch(Throwable e){failure.set(e);}finally{finished.countDown();}}).start();
        LogExportController<String> blocked=new LogExportController<>(target -> new ByteArrayOutputStream(){
            public void close() throws IOException { closing.countDown(); try {if(!release.await(5,TimeUnit.SECONDS))throw new IOException("timeout");}catch(InterruptedException e){throw new IOException(e);} }
        },background);
        t=blocked.begin(blocked.newPage(),s.snapshot());blocked.submit(t,"target");
        try {
            check(closing.await(5,TimeUnit.SECONDS),"close reached");check(blocked.state().busy(),"slot held through close");
            check(blocked.begin(blocked.newPage(),s.snapshot())==null,"blocked close no second export");
            TaskCoordinator owner=new TaskCoordinator(Runnable::run);check(owner.submit(() -> {}),"export independent ASR owner");
        } finally {release.countDown();}
        check(finished.await(5,TimeUnit.SECONDS)&&failure.get()==null,"bounded background completes");
        check(blocked.state().phase==LogExportController.Phase.SUCCEEDED,"success after close only");
        for(RuntimeLogEventKind kind:RuntimeLogEventKind.values())check(!RuntimeLogText.event(kind).isEmpty(),"all event meanings");
        check(RuntimeLogText.event(RuntimeLogEventKind.LOG_TELEMETRY_FAILED).contains("不代表"),"telemetry not ASR failure");
        System.out.println("PASS LogExportTest: "+checks+" checks (production tickets/owner/streams; not Android runtime)");
    }
}
