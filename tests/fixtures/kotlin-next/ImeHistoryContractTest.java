import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.llmasr.minimal.*;
import org.llmasr.minimal.audio.*;
import org.llmasr.minimal.ime.*;
import org.llmasr.minimal.task.*;
import org.llmasr.minimal.model.*;

/** Synthetic text/audio only; actual production controller/store/resident/Service, never device acceptance. */
public final class ImeHistoryContractTest {
    static void check(boolean value,String diagnostic){if(!value)throw new AssertionError(diagnostic);}
    static void await(CountDownLatch latch)throws Exception{check(latch.await(5,TimeUnit.SECONDS),"bounded latch");}
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static ImeHistoryStore.Snapshot snapshot(ImeHistoryStore store)throws Exception{
        AtomicReference<ImeHistoryStore.Snapshot> result=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        check(store.snapshot(s->{result.set(s);done.countDown();}),"snapshot admitted");await(done);return result.get();
    }
    static void shutdown(ImeHistoryStore store)throws Exception{
        ThreadPoolExecutor io=(ThreadPoolExecutor)field(store,"io");io.shutdown();check(io.awaitTermination(5,TimeUnit.SECONDS),"history actual worker exit before files removal");
    }
    static void delete(Path root)throws Exception{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);}}
    static void history()throws Exception{
        Path root=Files.createTempDirectory("ime-history-synthetic-");List<ImeHistoryStore> stores=new ArrayList<>();
        try{
            ImeHistoryStore store=new ImeHistoryStore(()->root.toFile());stores.add(store);
            check(snapshot(store).records.isEmpty(),"new private history empty");
            for(int n=0;n<55;n++)check(store.append("synthetic-"+n),"bounded append admission");
            ImeHistoryStore.Snapshot s=snapshot(store);
            check(s.available&&s.records.size()==50&&s.records.get(0).originalText.equals("synthetic-54")&&s.records.get(49).originalText.equals("synthetic-5"),"history newest-first bounded at fifty");
            Set<String> ids=new HashSet<>();for(ImeHistory r:s.records)check(ids.add(r.id)&&r.schemaVersion==1&&r.createdAt>0&&r.correctedText==null,"stable ID schema timestamp correction seam");
            String id=s.records.get(0).id;
            ImeHistoryStore restart=new ImeHistoryStore(()->root.toFile());stores.add(restart);
            check(snapshot(restart).records.get(0).id.equals(id),"restart preserves record identity");shutdown(restart);stores.remove(restart);
            CountDownLatch deleted=new CountDownLatch(1);check(store.delete(id,x->deleted.countDown()),"delete admitted");await(deleted);
            check(snapshot(store).records.size()==49&&!snapshot(store).records.get(0).id.equals(id),"delete exactly one");
            CountDownLatch cleared=new CountDownLatch(1);check(store.clear(x->cleared.countDown()),"clear admitted");await(cleared);
            check(snapshot(store).records.isEmpty(),"explicit clear persisted");
            check(!store.append("")&&!store.append(String.join("",Collections.nCopies(32769,"x"))),"empty oversized text rejected");
            shutdown(store);stores.remove(store);
            for(String damage:Arrays.asList("future","corrupt","oversize")){
                byte[] bytes=damage.equals("future")?new byte[]{0x49,0x4d,0x45,0x48,0,0,0,2}:damage.equals("corrupt")?new byte[]{1,2,3}:new byte[2097153];
                Path file=root.resolve("ime-history-v1.bin");Files.write(file,bytes);
                ImeHistoryStore broken=new ImeHistoryStore(()->root.toFile());stores.add(broken);
                check(!snapshot(broken).available,"damaged future schema read-only unavailable");
                broken.append("must-not-overwrite");broken.clear(x->{});broken.delete(id,x->{});snapshot(broken);
                check(Arrays.equals(bytes,Files.readAllBytes(file)),"unknown history must preserve original bytes");shutdown(broken);stores.remove(broken);
            }
            Path notDirectory=root.resolve("not-directory");Files.write(notDirectory,new byte[]{1});
            ImeHistoryStore failed=new ImeHistoryStore(()->notDirectory.toFile());stores.add(failed);failed.append("synthetic-failure");
            check(!snapshot(failed).available,"write failure unavailable without text exposure");
        }finally{for(ImeHistoryStore store:stores)shutdown(store);delete(root);}
    }
    static void orphanRecovery()throws Exception {
        for(String operation:Arrays.asList("delete","clear","future","corrupt")) {
            Path root=Files.createTempDirectory("ime-orphan-synthetic-");List<ImeHistoryStore> stores=new ArrayList<>();
            try {
                ImeHistoryStore first=new ImeHistoryStore(()->root.toFile());stores.add(first);first.append("synthetic orphan record");
                ImeHistory entry=snapshot(first).records.get(0);shutdown(first);stores.remove(first);
                Path orphan=root.resolve("ime-history-123456789.tmp");Files.write(orphan,new byte[]{1,2,3});
                Path bin=root.resolve("ime-history-v1.bin");
                byte[] unknown=operation.equals("future")?new byte[]{0x49,0x4d,0x45,0x48,0,0,0,2}:new byte[]{1,2,3};
                if(operation.equals("future")||operation.equals("corrupt"))Files.write(bin,unknown);
                ImeHistoryStore restart=new ImeHistoryStore(()->root.toFile());stores.add(restart);snapshot(restart);
                check(!Files.exists(orphan),"restart must remove owned orphan text before history use");
                CountDownLatch done=new CountDownLatch(1);
                if(operation.equals("delete"))restart.delete(entry.id,s->done.countDown());else restart.clear(s->done.countDown());await(done);
                if(operation.equals("future")||operation.equals("corrupt"))check(Arrays.equals(unknown,Files.readAllBytes(bin)),"orphan cleanup must preserve unsupported authoritative binary");
                else check(snapshot(restart).records.isEmpty(),"delete clear after restart leaves no orphan copy");
            }finally{for(ImeHistoryStore s:stores)shutdown(s);delete(root);}
        }
        for(String boundary:Arrays.asList("symlink","hardlink","unknown","directory","oversize","overflow","failure")) {
            Path root=Files.createTempDirectory("ime-orphan-boundary-");ImeHistoryStore store=null;
            try {
                Path outside=root.resolveSibling(root.getFileName()+"-outside");Files.write(outside,new byte[]{7});
                Path suspicious=root.resolve(boundary.equals("unknown")?"unrelated.txt":"ime-history-123.tmp");
                try {
                    if(boundary.equals("symlink"))Files.createSymbolicLink(suspicious,outside);
                    else if(boundary.equals("hardlink"))Files.createLink(suspicious,outside);
                    else if(boundary.equals("directory"))Files.createDirectory(suspicious);
                    else Files.write(suspicious,boundary.equals("oversize")?new byte[2097153]:new byte[]{1});
                    if(boundary.equals("overflow"))for(int n=0;n<65;n++)Files.write(root.resolve("ime-history-"+(1000+n)+".tmp"),new byte[]{1});
                    if(boundary.equals("failure"))Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
                    store=new ImeHistoryStore(()->root.toFile());check(!snapshot(store).available,"unsafe orphan cleanup must fail closed");
                    check(Files.exists(suspicious,LinkOption.NOFOLLOW_LINKS)&&Arrays.equals(new byte[]{7},Files.readAllBytes(outside)),"cleanup must preserve unknown links files and failed deletions");
                    store.append("must not create more copies");snapshot(store);
                    check(!Files.exists(root.resolve("ime-history-v1.bin")),"unsafe orphan state must never save");
                }finally{Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));Files.deleteIfExists(outside);}
            }finally{if(store!=null)shutdown(store);delete(root);}
        }
    }
    static void boundedIo()throws Exception{
        Path root=Files.createTempDirectory("ime-history-queue-");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        ImeHistoryStore store=new ImeHistoryStore(()->{entered.countDown();try{check(release.await(5,TimeUnit.SECONDS),"bounded supplier");}catch(Throwable t){failure.set(t);throw new IllegalStateException();}return root.toFile();});
        try{
            check(store.append("first"),"first append asynchronous");await(entered);
            int accepted=0;for(int n=0;n<80;n++)if(store.append("queued-"+n))accepted++;
            check(accepted==64,"IO queue must be bounded at sixty-four");
            release.countDown();shutdown(store);check(failure.get()==null,"IO worker Throwable carried out");
        }finally{release.countDown();shutdown(store);delete(root);}
    }
    static final class Run implements AutoCloseable {
        final AtomicReference<Throwable> failure=new AtomicReference<>();final AtomicReference<Thread> worker=new AtomicReference<>();
        final TaskCoordinator owner=new TaskCoordinator(r->{Thread t=new Thread(()->{try{r.run();}catch(Throwable f){failure.set(f);}},"asr-task-worker");worker.set(t);t.start();});
        final CountDownLatch captureStarted=new CountDownLatch(1),captureRelease=new CountDownLatch(1),loadStarted=new CountDownLatch(1),loadRelease=new CountDownLatch(1);
        final AtomicInteger nativeCalls=new AtomicInteger(),cleanups=new AtomicInteger(),starts=new AtomicInteger();
        final AtomicBoolean permitted=new AtomicBoolean(true);boolean failLoad,failCapture;final ImeController controller;final ImeSession session;
        final ImeHistoryStore history;
        Run(ImeHistoryStore history){this.history=history;controller=new ImeController(owner,new ImeController.Backend(){
            public void qualify(RecordingControl c,ImeController.Preparation p){}
            public void requireModelReady(){}
            public void prepare(RecordingControl c,ImeController.Preparation p)throws Exception{loadStarted.countDown();await(loadRelease);if(failLoad)throw new IOException("synthetic load failure");}
            public byte[] capture(RecordingControl c,ImeController.Progress p){throw new AssertionError("actual gate path required");}
            public byte[] captureWithStart(RecordingControl c,ImeController.Progress p,RecordingControl.StartGate gate)throws IOException{
                try{check(gate.start(()->starts.incrementAndGet()),"actual capture gate allowed");captureStarted.countDown();await(captureRelease);if(failCapture)throw new IOException("synthetic capture failure");return new byte[]{1};}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}catch(IOException e){throw e;}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new AssertionError(e);}finally{c.captureReleased();}
            }
            public String transcribe(RequestContext r,byte[] w,String l){nativeCalls.incrementAndGet();return "synthetic publication";}
            public void cleanup(RequestContext r){cleanups.incrementAndGet();}
        },()->{},history);session=controller.begin(1,0,"synthetic-field");}
        void start(){check(controller.start(session,"Chinese",()->permitted.get()),"single outer owner admitted");}
        void join()throws Exception{Thread t=worker.get();t.join(5000);check(!t.isAlive(),"actual outer worker joined");if(failure.get()!=null)throw new AssertionError("worker Throwable",failure.get());}
        public void close()throws Exception{captureRelease.countDown();loadRelease.countDown();if(worker.get()!=null)join();check(!owner.isBusy(),"owner actual terminal");}
    }
    static void parallel()throws Exception{
        for(String mode:Arrays.asList("early-stop","cancel","hide-field","permission","load-failure","capture-failure"))try(Run r=new Run(null)){
            r.failLoad=mode.equals("load-failure");r.failCapture=mode.equals("capture-failure");r.start();await(r.captureStarted);await(r.loadStarted);
            check(r.starts.get()==1&&r.owner.isBusy(),"actual capture must start before native load finishes");
            check(!r.owner.submit(()->{})&&!r.controller.start(r.session,"Chinese"),"single owner excludes App and second IME");
            if(mode.equals("cancel"))r.controller.invalidate();
            if(mode.equals("hide-field"))r.controller.begin(1,0,"new-field");
            if(mode.equals("permission"))r.permitted.set(false);
            r.controller.stop();r.captureRelease.countDown();
            check(r.owner.isBusy()&&r.nativeCalls.get()==0&&r.cleanups.get()==0,"early stop retains owner while native prepares");
            r.loadRelease.countDown();r.join();
            check(r.cleanups.get()==1&&r.nativeCalls.get()==(mode.equals("early-stop")?1:0),"cancel failure permission rejects inference and cleans once");
            check(r.session.preview().isEmpty()!=mode.equals("early-stop"),"only live successful transaction publishes preview");
        }
        // Release/maintenance must still arbitrate the final actual hardware callback without a loaded gate.
        ModelResidency resident=new ModelResidency(new ModelResidency.Native(){public double prepare(String c,String t){return 0;}public void release(){}},()->true);
        long epoch=resident.captureEpoch();AtomicInteger starts=new AtomicInteger();
        check(resident.startEarlyCapture(epoch,()->{starts.incrementAndGet();return true;}),"EMPTY resident permits qualified early capture");
        resident.requestRelease();try{resident.startEarlyCapture(epoch,()->{starts.incrementAndGet();return true;});}catch(IOException expected){}
        check(starts.get()==1,"early capture must reject release winner");resident.drainRelease();
        try(ModelVerification.MaintenanceLease lease=resident.acquire()){
            try{resident.startEarlyCapture(resident.captureEpoch(),()->{starts.incrementAndGet();return true;});}catch(IOException expected){}
            check(starts.get()==1,"early capture must reject maintenance");
        }
    }
    static void publication()throws Exception{
        Path root=Files.createTempDirectory("ime-history-publication-");ImeHistoryStore store=new ImeHistoryStore(()->root.toFile());
        try{
            try(Run r=new Run(store)){r.start();await(r.loadStarted);r.captureRelease.countDown();r.loadRelease.countDown();r.join();
                check(snapshot(store).records.size()==1,"successful current preview must append once");
                ImeHistory entry=snapshot(store).records.get(0);long rev=r.session.revision();
                check(r.controller.restoreHistory(r.session,rev,entry),"selected history restores preview only");
                check(!r.controller.restoreHistory(r.session,rev,entry),"stale history revision rejected");
                check(snapshot(store).records.size()==1,"history selection must not append again");
                AtomicInteger writes=new AtomicInteger();check(r.controller.commit(r.session,"synthetic-field",1,0,t->{writes.incrementAndGet();return true;}),"explicit confirm inputs selected preview");
                check(writes.get()==1&&snapshot(store).records.size()==1,"commit selected record does not duplicate history");
                check(r.controller.begin(129,0,"secret")==null&&!r.controller.restoreHistory(r.session,r.session.revision(),entry),"sensitive field invalidates historical preview");
            }
            try(Run r=new Run(store)){r.start();await(r.loadStarted);r.controller.invalidate();r.captureRelease.countDown();r.loadRelease.countDown();r.join();check(snapshot(store).records.size()==1,"invalidated result must not append history");}
        }finally{shutdown(store);delete(root);}
    }
    static void actualBackendParallel()throws Exception {
        try(NextAppContractTest.FirstUse f=new NextAppContractTest.FirstUse()) {
            CountDownLatch captured=new CountDownLatch(1),captureRelease=new CountDownLatch(1),loaded=new CountDownLatch(1),loadRelease=new CountDownLatch(1);
            AtomicReference<Throwable> fixtureFailure=new AtomicReference<>();
            org.llmasr.minimal.audio.ForegroundRecorder.afterStart=()->{captured.countDown();try{await(captureRelease);}catch(Throwable t){fixtureFailure.set(t);throw new AssertionError(t);}};
            f.onLoad=()->{loaded.countDown();try{await(loadRelease);}catch(Throwable t){fixtureFailure.set(t);throw new AssertionError(t);}};
            try {
                f.click();f.threaded();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(loaded.getCount()!=0&&System.nanoTime()<deadline){android.os.Handler.drain();loaded.await(5,TimeUnit.MILLISECONDS);}
                check(captured.getCount()==0&&loaded.getCount()==0&&f.loads[0]==1&&f.captures[0]==1,"real backend actual start overlaps sole native loading");
                f.controller.stop();captureRelease.countDown();
                check(f.owner.isBusy()&&!f.owner.submit(()->{}),"real backend early stop cannot release loading owner");
                loadRelease.countDown();f.pumpJoin();
                check(fixtureFailure.get()==null&&f.workerFailure.get()==null&&((ImeSession)field(f.service,"session")).preview().equals("manual preview"),"real backend early stop waits prepare then transcribes");
            }finally{captureRelease.countDown();loadRelease.countDown();f.pumpJoin();org.llmasr.minimal.audio.ForegroundRecorder.afterStart=()->{};}
        }
    }
    static void captureTerminalFaults()throws Exception {
        for(String mode:Arrays.asList("await-interrupt","finish-interrupt","capture-error","prepare-error","both-errors")) {
            CountDownLatch beforeStart=new CountDownLatch(1),allowStart=new CountDownLatch(1),captureEntered=new CountDownLatch(1),allowCapture=new CountDownLatch(1),prepareEntered=new CountDownLatch(1),allowPrepare=new CountDownLatch(1);
            AtomicReference<Thread> worker=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();AtomicReference<RecordingControl> control=new AtomicReference<>();
            AtomicBoolean interrupted=new AtomicBoolean();AtomicInteger cleanups=new AtomicInteger(),inferences=new AtomicInteger();
            Error captureError=new AssertionError("synthetic capture Error"),prepareError=new AssertionError("synthetic prepare Error");
            TaskCoordinator owner=new TaskCoordinator(r->{Thread t=new Thread(()->{try{r.run();}catch(Throwable x){failure.set(x);}finally{interrupted.set(Thread.currentThread().isInterrupted());}},"asr-task-worker");worker.set(t);t.start();});
            ImeController controller=new ImeController(owner,new ImeController.Backend(){
                public void requireModelReady(){}public void qualify(RecordingControl c,ImeController.Preparation p){}
                public void prepare(RecordingControl c,ImeController.Preparation p)throws Exception{prepareEntered.countDown();await(allowPrepare);if(mode.contains("prepare")||mode.equals("both-errors"))throw prepareError;}
                public byte[] capture(RecordingControl c,ImeController.Progress p){throw new AssertionError("gated path");}
                public byte[] captureWithStart(RecordingControl c,ImeController.Progress p,RecordingControl.StartGate gate)throws IOException {
                    control.set(c);beforeStart.countDown();
                    try {await(allowStart);if(!gate.start(()->{}))throw new CancellationException();captureEntered.countDown();await(allowCapture);if(mode.contains("capture")||mode.equals("both-errors"))throw captureError;return new byte[]{1};}
                    catch(RuntimeException|Error x){throw x;}catch(Exception x){throw new AssertionError(x);}finally{c.captureReleased();}
                }
                public String transcribe(RequestContext r,byte[] w,String l){inferences.incrementAndGet();return "must not publish";}
                public void cleanup(RequestContext r){cleanups.incrementAndGet();}
            },()->{});
            try {
                ImeSession session=controller.begin(1,0,"terminal-fault");check(controller.start(session,"Chinese"),"fault transaction admitted");await(beforeStart);
                if(mode.equals("await-interrupt")) {
                    waitBlocked(worker.get(),"awaitStart");worker.get().interrupt();waitCancelled(control.get());
                    check(owner.isBusy()&&cleanups.get()==0&&!owner.submit(()->{}),"awaitStart interrupt retains actual capture owner");allowStart.countDown();
                } else {
                    allowStart.countDown();await(captureEntered);await(prepareEntered);
                    if(mode.equals("finish-interrupt")){allowPrepare.countDown();waitBlocked(worker.get(),"finish");worker.get().interrupt();waitCancelled(control.get());check(owner.isBusy()&&cleanups.get()==0,"finish interrupt retains actual capture owner");}
                    else if(mode.equals("capture-error")){allowCapture.countDown();waitCancelled(control.get());check(owner.isBusy()&&cleanups.get()==0,"capture Error waits actual prepare terminal");allowPrepare.countDown();}
                    else {allowPrepare.countDown();waitCancelled(control.get());check(owner.isBusy()&&cleanups.get()==0,"prepare Error waits actual capture terminal");}
                }
                allowCapture.countDown();allowPrepare.countDown();worker.get().join(5000);
                check(!worker.get().isAlive()&&!owner.isBusy()&&cleanups.get()==1&&inferences.get()==0&&session.preview().isEmpty(),"all terminal faults cleanup once after actual return");
                if(mode.endsWith("interrupt"))check(interrupted.get()&&failure.get()==null,"capture wait interruption flag preserved");
                else check(failure.get()==(mode.equals("capture-error")?captureError:prepareError),"actual worker Error identity propagated externally");
                if(mode.equals("both-errors"))check(Arrays.asList(prepareError.getSuppressed()).contains(captureError),"secondary capture Error suppressed on prepare primary");
            } finally {
                allowStart.countDown();allowCapture.countDown();allowPrepare.countDown();if(worker.get()!=null){worker.get().join(5000);check(!worker.get().isAlive(),"fault finally actual worker exit");}
            }
        }
    }
    static void waitBlocked(Thread worker,String method)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean observed=false;
        while(!observed&&System.nanoTime()<deadline){for(StackTraceElement frame:worker.getStackTrace())if(frame.getClassName().endsWith(".ImeCapture")&&frame.getMethodName().equals(method))observed=true;Thread.yield();}
        check(observed,"bounded real capture wait reached "+method);
    }
    static void waitCancelled(RecordingControl control)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!control.cancelled()&&System.nanoTime()<deadline)Thread.yield();
        check(control.cancelled(),"interrupt Error cancellation observed");
    }
    static void serviceHistory()throws Exception {
        try(NextAppContractTest.ImeFixture f=new NextAppContractTest.ImeFixture()) {
            ImeHistoryStore store=new ImeHistoryStore(()->f.graphRoot.resolve("history-fixture").toFile());
            set(f.graph,"imeHistory",store);
            try {
                store.append("synthetic selectable history");snapshot(store);
                Method toggle=AsrImeService.class.getDeclaredMethod("toggleHistory");toggle.setAccessible(true);
                check(((android.widget.ScrollView)((android.widget.LinearLayout)field(f.service,"historyPanel")).parent).getVisibility()==android.view.View.GONE,"closed history outer scroll must not reserve blank space");
                toggle.invoke(f.service);snapshot(store);android.os.Handler.drain();
                check(((android.widget.ScrollView)((android.widget.LinearLayout)field(f.service,"historyPanel")).parent).getVisibility()==android.view.View.VISIBLE,"open history outer scroll visible");
                android.widget.LinearLayout panel=(android.widget.LinearLayout)field(f.service,"historyPanel");
                check(panel.children.size()==2,"eligible field history list visible");
                android.widget.LinearLayout row=(android.widget.LinearLayout)panel.children.get(1);
                android.widget.Button select=(android.widget.Button)row.children.get(0);
                check(f.writes==0&&select.performClick()&&f.original.preview().equals("synthetic selectable history"),"history UI selects preview without editor write");
                check(snapshot(store).records.size()==1,"history UI reuse never appends");
                check(((android.widget.ScrollView)((android.widget.LinearLayout)field(f.service,"historyPanel")).parent).getVisibility()==android.view.View.GONE,"selection closes outer history scroll");
                // Snapshot callback queued for an old editor cannot populate the new/sensitive panel.
                toggle.invoke(f.service);snapshot(store);
                f.service.info.inputType=129;f.service.onStartInputView(f.service.info,false);android.os.Handler.drain();
                check(panel.children.isEmpty()&&panel.getVisibility()==android.view.View.GONE,"late history snapshot cannot cross sensitive field");
                select.performClick();check(f.writes==0&&field(f.service,"session")==null,"old history row cannot restore sensitive field");
                f.service.info.inputType=1;f.service.onStartInputView(f.service.info,false);
                toggle.invoke(f.service);snapshot(store);f.service.onWindowHidden();android.os.Handler.drain();
                check(panel.children.isEmpty(),"hide drops late history callback and text views");
            }finally{shutdown(store);}
        }
    }
    public static void main(String[] args)throws Exception{
        switch(args.length==0?"all":args[0]){case "parallel":parallel();break;case "publication":publication();break;case "history":history();boundedIo();break;case "orphans":orphanRecovery();break;case "history-ui":serviceHistory();break;case "terminal-faults":captureTerminalFaults();break;default:history();orphanRecovery();boundedIo();parallel();publication();actualBackendParallel();captureTerminalFaults();serviceHistory();}
        System.out.println("PASS IME history and actual parallel capture production contracts (host only)");
    }
}
