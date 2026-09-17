import org.llmasr.minimal.model.ModelAccess;
import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelReports;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.modelmanagement.ModelUiText;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Review regressions use the real queued maintenance lane, not Android stubs. */
public final class ModelReviewFixTest {
    static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static void history() throws Exception {
        for (int mode=0;mode<4;mode++) {
            ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();
            if(mode==3) f.repo=new ModelRepository(f.manifest,f.dir,()->123456789) {
                protected void deleteManaged(File p)throws IOException { if(p.exists()) throw new IOException("cleanup denied"); }
            };
            ModelManagementController c=f.controller();
            if(mode==0) { c.startImport(f.source()); c.requestCancel(f.control.peekActive().id); }
            if(mode==1) { f.install(); Files.write(new File(f.dir,"b").toPath(),new byte[]{9,9,9}); c.startVerify(); }
            if(mode==2) {
                f.install(); f.repo=new ModelRepository(f.manifest,f.dir,()->123456789) {
                    protected void deleteManaged(File p)throws IOException { if(p.getName().equals("b"))throw new IOException("denied");super.deleteManaged(p); }
                }; c=f.controller(); c.startDelete(f.readiness.epoch(),true);
            }
            if(mode==3) { ModelRepositoryCancelTest.Source src=f.source();src.failRead=true;c.startImport(src); }
            f.queue.run(); ModelManagementState.Snapshot old=f.state.current();
            check(old.isTerminalPhase(),"user terminal");
            if(mode==1)check(ModelUiText.fileResult(old,"b").contains("SHA-256 不符"),"per-file exact SHA failure presentation");
            if(mode==3)check(old.cleanupOutcome==ModelManagementState.Outcome.FAILED&&old.orphanPartBytes>=0,"real failed cleanup retained");
            Files.write(new File(f.dir,"a").toPath(),new byte[]{7});
            c.refreshInspect();
            check(old.activeOperationId.equals(f.state.current().activeOperationId),"inspect admission retains user identity");
            check(f.control.peekActive().kind==ModelOperationControl.Kind.INSPECT,"real separate inspect owner");
            f.queue.run(); ModelManagementState.Snapshot now=f.state.current();
            check(now.activeOperationId.equals(old.activeOperationId)&&now.phase==old.phase&&now.operationKind==old.operationKind,"history identity/outcome");
            check(Objects.equals(now.errorCode,old.errorCode)&&now.failedFiles.equals(old.failedFiles)&&now.cleanupOutcome==old.cleanupOutcome,"history failure/cleanup");
            check(now.deleteSucceeded==old.deleteSucceeded&&now.deleteFailed==old.deleteFailed,"history deletion stats");
            check(now.files.get(0).actualBytes==1&&!f.owner.isBusy()&&!f.control.isBusy(),"fresh inventory and release");
            // An unsafe inspection cannot relabel the retained user failure.
            Files.delete(new File(f.dir,"a").toPath());new File(f.dir,"a").mkdir();
            c.refreshInspect(); f.queue.run(); now=f.state.current();
            check(now.phase==old.phase&&Objects.equals(now.errorCode,old.errorCode),"inspect error independent of history");
            check(now.readiness==ModelReadiness.State.INVALID&&!f.owner.isBusy(),"inspect failure invalidates inventory");
        }
    }
    static void terminal() throws Exception {
        for(int mode=0;mode<4;mode++) {
            ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();
            ModelManagementController c=f.controller(); if(mode==2)f.install();
            if(mode!=3) {c.startVerify(); if(mode==1)c.requestCancel(f.control.peekActive().id);}
            AtomicReference<ModelManagementState.Snapshot> before=new AtomicReference<>();
            AtomicBoolean rejected=new AtomicBoolean(),held=new AtomicBoolean();
            ModelManagementState.Listener listener=()->{
                ModelManagementState.Snapshot s=f.state.current();
                if(s.isTerminalPhase()&&before.compareAndSet(null,s)) {
                    held.set(f.owner.isBusy()&&f.control.isBusy());
                    c.requestCancel(s.activeOperationId);
                    rejected.set(f.state.current().phase==s.phase&&Objects.equals(f.state.current().errorCode,s.errorCode));
                }
            };
            f.state.addListener(listener);
            if(mode==3) {f.queue.reject=true;check(!c.startVerify(),"executor rejection");} else f.queue.run();
            f.state.removeListener(listener);
            check(before.get()!=null&&held.get(),"terminal notification before real finalizer");
            check(rejected.get(),"late reentrant cancel must not overwrite terminal");
            check(f.state.current().phase==before.get().phase&&Objects.equals(f.state.current().errorCode,before.get().errorCode),"finalizer preserves terminal");
            check(!f.owner.isBusy()&&!f.control.isBusy(),"finalizer release");
        }
    }
    static void terminalWindow() throws Exception {
        for(int mode=0;mode<3;mode++) {
            ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();
            if(mode==2)f.install();ModelManagementController c=f.controller();c.startVerify();
            if(mode==1)c.requestCancel(f.control.peekActive().id);
            ModelRepositoryCancelTest.Gate pause=new ModelRepositoryCancelTest.Gate();
            AtomicReference<Throwable> callbackError=new AtomicReference<>();
            ModelReadiness.Listener listener=()->{
                if(f.state.current().isTerminalPhase()&&f.control.isBusy()) {
                    try{pause.block();}catch(Throwable e){callbackError.set(e);}
                }
            };
            f.readiness.addListener(listener);
            try(ModelRepositoryCancelTest.Worker worker=new ModelRepositoryCancelTest.Worker(()->f.queue.run())) {
                try {
                    pause.await();ModelManagementState.Snapshot before=f.state.current();
                    check(f.owner.isBusy()&&f.control.isBusy(),"post-terminal window retains real owner");
                    c.requestCancel(before.activeOperationId);
                    check(!f.control.requestCancel(before.activeOperationId),"all committed terminals reject direct late cancel");
                    check(f.state.current().phase==before.phase&&Objects.equals(f.state.current().errorCode,before.errorCode),"post-terminal cross-thread cancel preserves detailed result");
                    check(!c.startVerify()&&!f.owner.submit(()->{}),"no competing admission before finalizer");
                } finally {pause.close();}
                worker.join();check(worker.error.get()==null&&callbackError.get()==null,"bounded worker/listener failures observed outside guards");
            } finally {pause.close();f.readiness.removeListener(listener);}
            check(!f.owner.isBusy()&&!f.control.isBusy(),"post-terminal worker finalized");
        }
    }
    static void liveDeletion()throws Exception {
        ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();f.install();
        f.repo=new ModelRepository(f.manifest,f.dir,()->123456789) {
            protected void deleteManaged(File p)throws IOException {if(p.getName().equals("a"))throw new IOException("denied");super.deleteManaged(p);}
        };
        List<ModelManagementState.Snapshot> observed=new ArrayList<>();
        ModelManagementState.Listener listener=()->{ModelManagementState.Snapshot s=f.state.current();if(s.phase==ModelManagementState.Phase.DELETING)observed.add(s);};
        f.state.addListener(listener);ModelManagementController c=f.controller();c.startDelete(f.readiness.epoch(),true);f.queue.run();f.state.removeListener(listener);
        boolean liveFailure=false;
        for(ModelManagementState.Snapshot s:observed)if(s.fileIndex==1) {
            liveFailure|=s.deleteFailed==1&&s.deleteSucceeded==0&&ModelUiText.task(s).contains("；失败 1");
        }
        check(liveFailure,"ordinary delete failure count displayed before next item/terminal");
        check(f.state.current().deleteFailed==1&&f.state.current().deleteSucceeded==1,"partial delete totals remain exact");
    }
    static void boundary() throws Exception {
        for(int mode=0;mode<3;mode++)for(boolean cached:new boolean[]{false,true}) {
            ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();f.install();if(cached)f.ready();
            File part=new File(f.dir,"a.part"),external=File.createTempFile("model-review-target", ".bin");Files.write(external.toPath(),new byte[]{8});
            if(mode==0) Files.createSymbolicLink(part.toPath(),external.toPath());
            else if(mode==1)part.mkdir(); else Files.write(part.toPath(),new byte[]{6});
            AtomicBoolean nativeContinuation=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();
            check(f.owner.submit(()->{try {new ModelAccess(f.repo,f.readiness).requireReady();nativeContinuation.set(true);}catch(Throwable e){failure.set(e);}}),"lazy owner admitted");f.queue.run();
            if(mode<2) {
                check(!nativeContinuation.get()&&failure.get()!=null,"unsafe part prevents lazy/native continuation including cached READY");
                check(f.readiness.state()==ModelReadiness.State.INVALID,"unsafe part INVALID");
            } else check(nativeContinuation.get()&&f.readiness.isReady()&&part.isFile(),"safe orphan retained without blocking readiness");
            check(Files.readAllBytes(external.toPath())[0]==8,"external target untouched");
        }
    }
    static void deletionText() {
        for(int n:new int[]{1,7,14}) {
            ModelManagementState.Builder b=new ModelManagementState.Builder(ModelManagementState.empty());
            b.operationKind=ModelManagementState.OperationKind.DELETE;b.phase=ModelManagementState.Phase.FAILED;
            b.fileName="a";b.fileIndex=n;b.fileCount=7;b.deleteSucceeded=n-1;b.deleteFailed=1;
            check(!ModelUiText.task(b.build()).contains("文件："),"terminal must omit live file denominator "+n);
        }
    }
    static void planning() throws Exception {
        for(boolean parts:new boolean[]{false,true}) {
            ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();f.install();
            if(parts)Files.write(new File(f.dir,"a.part").toPath(),new byte[]{9});
            f.repo=new ModelRepository(f.manifest,f.dir,()->0);
            AtomicInteger copyEvents=new AtomicInteger();
            ModelManagementState.Listener noCopy=()->{if(f.state.current().phase==ModelManagementState.Phase.COPYING)copyEvents.incrementAndGet();};
            f.state.addListener(noCopy);
            ModelRepositoryCancelTest.Source src=new ModelRepositoryCancelTest.Source(ModelManagementControllerTest.DATA);
            ModelManagementController c=f.controller();c.startImport(src);f.queue.run();
            f.state.removeListener(noCopy);
            check(copyEvents.get()==0&&!new File(f.dir,"a.part").exists(),"zero space reuse performs no copy and reclaims safe part");
            check(f.state.current().reusedBytes==6,"all-local reuse exact bytes");
            check(ModelUiText.fileResult(f.state.current(),"a").contains("已复用"),"per-file reuse displayed separately from current inventory");
            check(src.opens==0&&f.state.current().phase==ModelManagementState.Phase.SUCCEEDED,"reuse without source opens");
            // Reflection lets the pre-fix production compile; missing structured fields fail behaviorally.
            Object plan=f.state.current().getClass().getField("importPlan").get(f.state.current());
            planValue(plan,"copyBytes",0);planValue(plan,"reusedBytes",6);planValue(plan,"requiredBytes",0);planValue(plan,"availableAtCheck",0);
            c.refreshInspect();f.queue.run();check(f.state.current().getClass().getField("importPlan").get(f.state.current())==plan,"inspect retains immutable plan");
        }
        ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();
        Files.write(new File(f.dir,"a").toPath(),ModelManagementControllerTest.DATA);
        f.repo=new ModelRepository(f.manifest,f.dir,()->17);
        ModelManagementController c=f.controller();ModelRepositoryCancelTest.Source src=f.source();c.startImport(src);f.queue.run();
        check(f.state.current().phase==ModelManagementState.Phase.FAILED&&src.opens==0,"aggregate reject before first copy");
        Object plan=f.state.current().getClass().getField("importPlan").get(f.state.current());
        planValue(plan,"copyBytes",3);planValue(plan,"reusedBytes",3);planValue(plan,"requiredBytes",3+ModelRepository.SPACE_PAD);planValue(plan,"availableAtCheck",17);
        check(ModelUiText.spacePlan(f.state.current()).contains("67108867 bytes")&&ModelUiText.spacePlan(f.state.current()).contains("17 bytes"),"preflight UI retains exact need/available");
        try {f.state.current().importPlan.reusableFiles.add("bad");throw new AssertionError("mutable reuse plan");}catch(UnsupportedOperationException expected){}
        try {f.state.current().fileResults.put("bad",new ModelReports.FileOutcome(ModelReports.FileOutcome.Status.FAILED));throw new AssertionError("mutable file observations");}catch(UnsupportedOperationException expected){}
        // Three entries, one reused + two copied. Observe counters outside listener guard.
        ModelManagementControllerTest.Fixture multi=new ModelManagementControllerTest.Fixture();
        ModelManifest manifest=ModelRepositoryCancelTest.manifest(ModelManagementControllerTest.DATA,"a","b","c");
        multi.repo=new ModelRepository(manifest,multi.dir,()->123456789);
        Files.write(new File(multi.dir,"a").toPath(),ModelManagementControllerTest.DATA);
        List<ModelManagementState.Snapshot> copies=new ArrayList<>();
        ModelManagementState.Listener listener=()->{ModelManagementState.Snapshot x=multi.state.current();if(x.phase==ModelManagementState.Phase.COPYING)copies.add(x);};
        multi.state.addListener(listener);ModelManagementController mc=multi.controller();mc.startImport(new ModelRepositoryCancelTest.Source(ModelManagementControllerTest.DATA,"b","c"));multi.queue.run();multi.state.removeListener(listener);
        long last=-1;for(ModelManagementState.Snapshot x:copies){check(x.stageBytesDone>=last&&x.stageBytesDone<=6&&x.stageBytesTotal==6&&x.reusedBytes==3,"multi-file monotonic exact cumulative copy/reuse");last=x.stageBytesDone;}
        check(last==6,"all new bytes counted across two copied files");
        plan=multi.state.current().getClass().getField("importPlan").get(multi.state.current());
        planValue(plan,"copyBytes",6);planValue(plan,"reusedBytes",3);planValue(plan,"requiredBytes",6+ModelRepository.SPACE_PAD);
    }
    static void publicationHistory() throws Exception {
        for (boolean cancelAfterPublish : new boolean[]{false,true}) {
            ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();
            f.repo=new ModelRepository(f.manifest,f.dir,()->123456789) {
                protected void publishPart(File part,File dest)throws IOException {
                    super.publishPart(part,dest);
                    if(cancelAfterPublish && dest.getName().equals("a")) f.control.requestCancel(f.control.peekActive().id);
                }
            };
            ModelRepositoryCancelTest.Source src=f.source();
            if(!cancelAfterPublish)src.data.put("b",new byte[]{9,9,9});
            ModelManagementController c=f.controller();
            check(c.startImport(src),"publication fixture admitted");f.queue.run();
            ModelManagementState.Snapshot s=f.state.current();
            check(Arrays.equals(Files.readAllBytes(new File(f.dir,"a").toPath()),ModelManagementControllerTest.DATA),"published official A retained");
            check(!new File(f.dir,"b").exists()&&!f.readiness.isReady(),"incomplete import cannot become READY");
            check(s.phase==(cancelAfterPublish?ModelManagementState.Phase.CANCELLED:ModelManagementState.Phase.FAILED),"actual terminal cause");
            if(!cancelAfterPublish)check(s.failedFiles.contains("b"),"B SHA failure retained");
            String observation=ModelUiText.fileResult(s,"a");
            check(observation.contains("已复制并发布")&&observation.contains("仍需全清单核验"),"published A observation must precede post-publish cancel and later B failure");
            c.refreshInspect();f.queue.run();
            check(ModelUiText.fileResult(f.state.current(),"a").equals(observation),"inspection preserves publication history");
            check(!f.owner.isBusy()&&!f.control.isBusy(),"publication fixture releases owner");
        }
    }
    static void planValue(Object plan,String field,long value)throws Exception {
        check(plan!=null&&plan.getClass().getField(field).getLong(plan)==value,"immutable plan exact "+field);
    }
    public static void main(String[] args)throws Exception {
        switch(args[0]) {case "publication":publicationHistory();break;case "window":terminalWindow();break;case "liveDeletion":liveDeletion();break;case "planning":planning();break;case "history":history();break;case "terminal":terminal();break;case "boundary":boundary();break;case "deletion":deletionText();break;default:throw new AssertionError(args[0]);}
        System.out.println("PASS review regression "+args[0]);
    }
}
