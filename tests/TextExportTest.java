import org.llmasr.minimal.transcription.AppRequestPolicy;
import org.llmasr.minimal.transcription.AppState;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.transcription.ResultState;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;
import org.llmasr.minimal.transcription.TextExportController;
import org.llmasr.minimal.transcription.TextExportPage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real production controller/page/result tests, not Android SAF execution. */
public final class TextExportTest {
    static int checks;
    static void check(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    static final class Queue implements Executor {
        final List<Runnable> jobs=new ArrayList<>();
        public void execute(Runnable r) { jobs.add(r); }
        void run() { jobs.remove(0).run(); }
    }
    static ResultState result() { ResultState r=new ResultState(); r.setText("私密 héllo"); return r; }
    static void await(CountDownLatch l) throws Exception { check(l.await(4,TimeUnit.SECONDS),"bounded latch"); }
    static void block(CountDownLatch l) throws IOException {
        try { if(!l.await(4,TimeUnit.SECONDS)) throw new IOException("test timeout"); }
        catch(InterruptedException e) { throw new IOException(e); }
    }
    public static void main(String[] args) throws Exception {
        ResultState r=result(); ResultState.Snapshot old=r.begin();
        r.setText("B"); r.setText(old.text);
        check(r.applyEdit(old,"stale")<0,"ABA rejected");
        check(r.applyEdit(result().begin(),"foreign")<0,"foreign snapshot rejected");
        check(r.applyEdit(r.begin(),"new")>0,"current edit accepted");
        r.clear(); check(r.currentText().isEmpty(),"clear empty");
        Queue q=new Queue(); AtomicInteger opens=new AtomicInteger(); ByteArrayOutputStream out=new ByteArrayOutputStream();
        TextExportController<String> c=new TextExportController<>(target -> { opens.incrementAndGet(); return out; },q);
        long page=c.newPage(); check(c.begin(page,r)==null,"empty rejected");
        r.setText("私密 héllo"); TextExportController.Ticket t=c.begin(page,r);
        check(c.begin(page,r)==null,"one picker");
        r.setText("changed after click"); check(c.admit(t,"target"),"queued");
        check(c.state().phase==TextExportController.Phase.QUEUED && opens.get()==0,"enqueue is not write admission");
        check(!c.admit(t,"replay"),"one shot"); q.run();
        check(out.toString("UTF-8").equals(t.snapshot.text),"immutable UTF8 snapshot");
        check(c.state().bytes==t.snapshot.text.getBytes(StandardCharsets.UTF_8).length,"byte count");
        check(!c.admit(t,"replay"),"finished replay rejected");
        // Selected and queued clear both forbid ANY provider open.
        for(boolean queueFirst:new boolean[]{false,true}) {
            r.setText("before clear"); t=c.begin(page,r); int before=opens.get();
            if(queueFirst) check(c.admit(t,"target"),"queue before clear");
            r.clear(); r.setText("new result after clear");
            if(queueFirst) {
                check(c.state().busy(),"revoked queue holds slot until unwind");
                check(c.begin(page,r)==null,"no early reuse"); q.run();
            } else check(!c.admit(t,"target"),"selected clear rejected");
            check(opens.get()==before,"clear forbids provider open");
            check(c.state().phase==TextExportController.Phase.EXPIRED,"expired status");
        }
        // Page lifecycle drives deferred callbacks and queued revocation on SAME controller.
        TextExportPage<String> p=new TextExportPage<>(c);
        check(p.begin(r)==null,"not foreground"); p.foreground(true); t=p.begin(r);
        check(t!=null,"foreground begin");
        check(!p.onResult(t.requestCode+1,"foreign"),"foreign ignored");
        p.foreground(false); check(p.onResult(t.requestCode,"target"),"defer while picker paused");
        check(q.jobs.isEmpty(),"no work while background"); p.foreground(true);
        check(q.jobs.size()==1,"resume queues once"); p.destroy();
        int before=opens.get(); check(c.state().busy(),"destroy does not free queued slot");
        q.run(); check(opens.get()==before,"destroy queued forbids open");
        check(c.state().phase==TextExportController.Phase.CANCELLED,"destroy terminal");
        TextExportPage<String> fresh=new TextExportPage<>(c); fresh.foreground(true);
        TextExportController.Ticket freshTicket=fresh.begin(r);
        check(freshTicket!=null,"new page can use same controller");
        check(!fresh.onResult(t.requestCode,"old"),"old callback cannot consume new ticket");
        fresh.destroy(); check(!c.state().busy(),"picker destroyed frees slot");
        TextExportPage<String> deferred=new TextExportPage<>(c); deferred.foreground(true);
        t=deferred.begin(r); deferred.foreground(false);
        check(deferred.onResult(t.requestCode,"target"),"deferred accepts once");
        check(!deferred.onResult(t.requestCode,"duplicate"),"duplicate deferred refused");
        before=opens.get(); r.clear(); r.setText("after deferred clear");
        deferred.foreground(true); deferred.foreground(true);
        check(q.jobs.isEmpty() && opens.get()==before,"deferred clear forbids resume write");
        TextExportController.Ticket next=deferred.begin(r);
        check(next!=null && !deferred.onResult(t.requestCode,"stale"),"same page stale callback refused");
        deferred.foreground(false); deferred.onResult(next.requestCode,"target"); deferred.destroy();
        deferred.foreground(true); check(q.jobs.isEmpty(),"destroy deferred no write");
        // Invalid/foreign ticket from another controller cannot affect this controller.
        TextExportController<String> other=new TextExportController<>(target -> out,q);
        t=other.begin(other.newPage(),r);
        check(!c.admit(t,"foreign") && !c.admit(null,"null"),"foreign ticket refused"); other.revoke(t);
        // Actual open/write/close block: clear/destroy cannot retract admitted write,
        // and shared ASR/model owner is usable during every IO boundary.
        for(int stage=0;stage<3;stage++) blocked(stage);
        // All ordinary failures are safe fixed status; close failure never success.
        for(int mode=0;mode<6;mode++) {
            final int m=mode; Queue fq=new Queue(); AtomicInteger closed=new AtomicInteger();
            TextExportController<String> f=new TextExportController<>(target -> {
                if(m==0) throw new IOException("secret-uri");
                if(m==1) throw new SecurityException("secret-path");
                if(m==2) return null;
                return new OutputStream() {
                    public void write(int b) throws IOException { if(m==3) throw new IOException("secret-body"); if(m==5) throw new AssertionError("fatal"); }
                    public void close() throws IOException { closed.incrementAndGet(); if(m==4) throw new IOException("secret-close"); }
                };
            },fq);
            t=f.begin(f.newPage(),r); f.admit(t,"private-target");
            Throwable failure=null; try { fq.run(); } catch(Error e) { failure=e; }
            check((failure!=null)==(m==5),"fatal propagation");
            check(f.state().phase==TextExportController.Phase.FAILED && !f.state().busy(),"failure releases after cleanup");
            check(!TextExportController.statusText(f.state()).contains("secret"),"safe status");
            if(m>=3) check(closed.get()==1,"close even write failure/fatal");
        }
        TextExportController<String> reject=new TextExportController<>(target -> out,job -> { throw new RejectedExecutionException(); });
        t=reject.begin(reject.newPage(),r); check(!reject.admit(t,"target"),"executor rejects");
        check(!reject.state().busy() && reject.state().phase==TextExportController.Phase.FAILED,"rejection rollback");
        // Slot cannot be overwritten by an old revoked callback.
        t=c.begin(page,r); c.revoke(t); TextExportController.Ticket newer=c.begin(page,r);
        c.revoke(t); check(c.state().requestCode==newer.requestCode && c.state().busy(),"stale revoke isolated"); c.revoke(newer);
        // Full request code exhaustion (not just 50 tickets), no recycled codes.
        TextExportController<String> codes=new TextExportController<>(target -> out,q); long id=codes.newPage();
        for(int code=TextExportController.FIRST_CODE;code<=TextExportController.LAST_CODE;code++) {
            t=codes.begin(id,r); if(t==null || t.requestCode!=code) throw new AssertionError("code reuse"); codes.revoke(t);
        }
        check(codes.begin(id,r)==null,"exhaustion fails closed");
        r.setText(new String(new char[50000]).replace("\0","😀"));
        t=c.begin(page,r); check(t!=null,"100000 UTF16 boundary accepted");
        c.admit(t,"target"); q.run(); check(c.state().bytes==200000,"supplementary UTF8 byte boundary");
        r.setText(new String(new char[100001]).replace('\0','x')); check(c.begin(page,r)==null,"100000 char cap");
        // Notifications are invalidations; assertion is OUTSIDE isolated observer.
        r.setText("again"); AtomicInteger notices=new AtomicInteger(); AtomicBoolean reentrant=new AtomicBoolean();
        TextExportController.Listener listener=() -> { notices.incrementAndGet(); reentrant.set(c.state()!=null); };
        TextExportController.Listener throwing=() -> { throw new IllegalStateException("observer sentinel"); };
        c.addListener(throwing); c.addListener(listener); t=c.begin(page,r); c.admit(t,"target"); q.run();
        check(notices.get()>=3 && reentrant.get(),"completion notification/reentry");
        int count=notices.get(); c.removeListener(listener); t=c.begin(page,r); c.revoke(t); check(notices.get()==count,"listener detached");
        System.out.println("PASS TextExportTest: "+checks+" checks; full code exhaustion and open/write/close interleavings");
    }
    static void blocked(int stage) throws Exception {
        CountDownLatch reached=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>(); ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        AppState asr=new AppState(); asr.setLastText("私密 héllo"); asr.setLastStatus("asr-status");
        ResultState r=asr.resultState();
        TaskCoordinator owner=new TaskCoordinator(Runnable::run);
        RequestRunnerTest.FakeReports reports=new RequestRunnerTest.FakeReports();
        RequestRunner runner=new RequestRunner(owner,()->0,new AppRequestPolicy(reports,new AppRequestPolicy.State(){
            public String lastText(){return asr.lastText();} public void setLastText(String s){asr.setLastText(s);}
            public String lastStatus(){return asr.lastStatus();} public void setLastStatus(String s){asr.setLastStatus(s);}
        }),ctx->{});
        TextExportController<String> c=new TextExportController<>(target -> {
            if(stage==0) { reached.countDown(); block(release); }
            return new OutputStream() {
                public void write(int b) throws IOException { if(stage==1) { reached.countDown(); block(release); } bytes.write(b); }
                public void close() throws IOException { if(stage==2) { reached.countDown(); block(release); } }
            };
        },job -> { Thread th=new Thread(() -> { try { job.run(); } catch(Throwable e) { failure.set(e); } finally { done.countDown(); } }); th.setDaemon(true); th.start(); });
        TextExportPage<String> page=new TextExportPage<>(c); page.foreground(true); TextExportController.Ticket t=page.begin(r);
        page.onResult(t.requestCode,"chosen");
        try {
            await(reached); check(c.state().phase==TextExportController.Phase.WRITING,"real write admission");
            check(runner.submit(TaskKind.MAINTENANCE,ctx -> { asr.clearText(); asr.setLastStatus("clear-finished"); }),"shared runner clear admitted");
            page.destroy(); check(c.state().busy(),"clear/destroy keep actual IO slot");
            check(runner.submit(TaskKind.INFERENCE,ctx -> { asr.setLastText("next result"); asr.setLastStatus("new-inference-finished"); }),"real policy/runner usable during IO stage "+stage);
            check(!owner.isBusy() && reports.log.size()==2,"inference reports and release completed");
            check(c.begin(c.newPage(),r)==null,"slot cannot reuse while IO blocks");
        } finally { release.countDown(); await(done); }
        check(failure.get()==null,"worker no failure"); check(c.state().phase==TextExportController.Phase.SUCCEEDED,"success after close");
        check(bytes.toString("UTF-8").equals(t.snapshot.text),"admitted snapshot finishes");
        check(r.currentText().equals("next result") && asr.lastStatus().equals("new-inference-finished"),"export cannot overwrite result or ASR status");
    }
}
