import org.llmasr.minimal.transcription.AppRequestPolicy;
import org.llmasr.minimal.transcription.AppState;
import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;
import org.llmasr.minimal.diagnostics.RuntimeLogCodec;
import org.llmasr.minimal.diagnostics.RuntimeLogEvent;
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogSink;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic tests of actual maintenance admission/worker/runner/IO ports. */
public final class ModelManagementControllerTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    static final byte[] DATA={1,2,3};
    static final class Queue implements Executor {
        Runnable pending; boolean reject;
        public void execute(Runnable r){if(reject)throw new RejectedExecutionException();if(pending!=null)throw new AssertionError("queued twice");pending=r;}
        void run(){Runnable r=pending;pending=null;r.run();}
    }
    static class Fixture {
        final Queue queue=new Queue();
        final TaskCoordinator owner=new TaskCoordinator(queue);
        final ModelReadiness readiness=new ModelReadiness();
        final ModelOperationControl control=new ModelOperationControl();
        final ModelManagementState state=new ModelManagementState();
        final File dir=ModelRepositoryCancelTest.dir();
        final ModelManifest manifest=ModelRepositoryCancelTest.manifest(DATA,"a","b");
        ModelRepository repo=new ModelRepository(manifest,dir,()->123456789);
        final AppState asr=new AppState();
        final RequestRunnerTest.FakeReports reports=new RequestRunnerTest.FakeReports();
        final AtomicInteger statusWrites=new AtomicInteger();
        final AppState.Listener appObserver=statusWrites::incrementAndGet;
        Fixture()throws Exception {asr.setLastText("ASR preserved");asr.setLastStatus("ASR status");asr.addListener(appObserver);
            // Seed actual App report policy on the shared owner, then require model work to leave it unchanged.
            RequestRunner appRunner=new RequestRunner(owner,()->0,new AppRequestPolicy(reports,new AppRequestPolicy.State(){
                public String lastText(){return asr.lastText();} public void setLastText(String s){asr.setLastText(s);}
                public String lastStatus(){return asr.lastStatus();} public void setLastStatus(String s){asr.setLastStatus(s);}
            }),ctx->{});
            appRunner.submit(TaskKind.INFERENCE,ctx->{});queue.run();
            asr.setLastText("ASR preserved");asr.setLastStatus("ASR status");statusWrites.set(0);}
        ModelManagementController controller(){return new ModelManagementController(owner,repo,readiness,control,state);}
        ModelRepositoryCancelTest.Source source(){return new ModelRepositoryCancelTest.Source(DATA,"a","b");}
        void install()throws Exception{for(String n:new String[]{"a","b"})Files.write(new File(dir,n).toPath(),DATA);}
        void ready(){readiness.markVerified(readiness.tryBeginVerify());}
        void isolated(){check(reports.log.equals(Arrays.asList("pending:INFERENCE","terminal:INFERENCE")));check(asr.lastText().equals("ASR preserved"));check(asr.lastStatus().equals("ASR status"));check(statusWrites.get()==0);}
        void terminal(ModelManagementState.Phase phase){check(state.current().phase==phase);check(!control.isBusy());check(!owner.isBusy());check(!state.current().pageOwnerHeld);isolated();}
    }
    private static final String LOG_SECRET = "content://private-sentinel/model-path transcript-sentinel";
    private static void logCheck(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    /** Synchronous queued tasks only: no live IO or background worker at deletion. */
    private static final class LoggingFixture extends Fixture implements AutoCloseable {
        final RuntimeLogStore logs = new RuntimeLogStore();
        LoggingFixture() throws Exception { super(); }
        @Override ModelManagementController controller() {
            return new ModelManagementController(owner,repo,readiness,control,state,logs);
        }
        @Override public void close() throws Exception {
            logCheck(queue.pending == null && !owner.isBusy() && !control.isBusy(),
                "logging fixture must be finalized before directory deletion");
            // Successful deleteAll may already have removed the empty model root.
            if (!Files.exists(dir.toPath(),LinkOption.NOFOLLOW_LINKS)) return;
            try (java.util.stream.Stream<Path> paths = Files.walk(dir.toPath())) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    Files.delete(path);
            }
        }
    }
    private static boolean startLogged(ModelManagementController c, LoggingFixture f,
            ModelOperationControl.Kind kind, ModelSource source) {
        switch (kind) {
            case IMPORT: return c.startImport(source);
            case VERIFY: return c.startVerify();
            case DELETE: return c.startDelete(f.readiness.epoch(),true);
            default: throw new AssertionError("explicit operation required");
        }
    }
    private static void logPair(LoggingFixture f, String id, ModelOperationControl.Kind kind,
            String outcome, String diagnostic) throws Exception {
        List<RuntimeLogEvent> pair = new ArrayList<>();
        for (RuntimeLogEvent event : f.logs.snapshot())
            if (Objects.equals(id,event.requestId)) pair.add(event);
        logCheck(id != null && pair.size() == 2, diagnostic);
        logCheck(pair.get(0).kind == RuntimeLogEventKind.valueOf("MODEL_"+kind+"_STARTED")
            && pair.get(1).kind == RuntimeLogEventKind.valueOf("MODEL_"+kind+"_"+outcome),
            "maintenance exact terminal kind: "+kind+"/"+outcome);
        logCheck(pair.get(0).source == RuntimeLogSource.SHARED && pair.get(1).source == RuntimeLogSource.SHARED,
            "maintenance fixed source");
        String detail = "COMPLETED".equals(outcome) ? "ok" : "CANCELLED".equals(outcome) ? "cancelled" : "failed";
        logCheck("ok".equals(pair.get(0).detail) && detail.equals(pair.get(1).detail), "maintenance fixed detail");
        logCheck(pair.get(0).sequence < pair.get(1).sequence, "maintenance ordered log pair");
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        RuntimeLogCodec.writeSnapshot(f.logs.snapshot(),encoded);
        String text = new String(encoded.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        logCheck(!text.contains("private-sentinel") && !text.contains("transcript-sentinel")
            && !text.contains("InternalError") && !text.contains("IOException") && !text.contains("\tat "),
            "maintenance exported logs exclude exception/path/text/stack");
    }
    private static void loggingRejection(ModelOperationControl.Kind kind) throws Exception {
        try (LoggingFixture f = new LoggingFixture()) {
            ModelManagementController c = f.controller();
            f.queue.reject = true;
            logCheck(!startLogged(c,f,kind,f.source()), "maintenance rejected submission returns false");
            String id = f.state.current().activeOperationId;
            f.terminal(ModelManagementState.Phase.FAILED);
            logPair(f,id,kind,"FAILED","maintenance rejection terminal missing: "+kind);
        }
    }
    private static void loggingAbnormal() throws Exception {
        try (LoggingFixture f = new LoggingFixture()) {
            final InternalError fatal = new InternalError(LOG_SECRET);
            f.repo = new ModelRepository(f.manifest,f.dir,()->123456789) {
                public void verifyAll(Progress p,CancelGate gate) { throw fatal; }
            };
            ModelManagementController c = f.controller();
            logCheck(c.startVerify(), "maintenance abnormal task admitted");
            String id = f.control.peekActive().id;
            Throwable seen = null;
            try { f.queue.run(); } catch (Throwable failure) { seen = failure; }
            logCheck(seen == fatal, "maintenance abnormal exact Error identity");
            f.terminal(ModelManagementState.Phase.FAILED);
            logPair(f,id,ModelOperationControl.Kind.VERIFY,"FAILED","maintenance abnormal terminal missing: VERIFY");
        }
    }
    private static void loggingObserver(boolean admission) throws Exception {
        try (LoggingFixture f = new LoggingFixture()) {
            f.install();
            ModelManagementController c = f.controller();
            final InternalError fatal = new InternalError(LOG_SECRET);
            AtomicBoolean fired = new AtomicBoolean(), lockHeld = new AtomicBoolean();
            ModelManagementState.Listener observer = () -> {
                if (Thread.holdsLock(c)) lockHeld.set(true);
                ModelManagementState.Snapshot s = f.state.current();
                if (s.pageOwnerHeld && s.isTerminalPhase() != admission && fired.compareAndSet(false,true))
                    throw fatal;
            };
            f.state.addListener(observer);
            Throwable seen = null;
            try {
                try {
                    if (!admission) logCheck(c.startVerify(), "maintenance observer task admitted");
                    if (admission) c.startVerify(); else f.queue.run();
                } catch (Throwable failure) { seen = failure; }
            } finally { f.state.removeListener(observer); }
            logCheck(fired.get() && !lockHeld.get(), "maintenance observer reached outside monitor");
            logCheck(seen == fatal, "maintenance observer exact Error identity");
            f.terminal(admission ? ModelManagementState.Phase.FAILED : ModelManagementState.Phase.SUCCEEDED);
            logPair(f,f.state.current().activeOperationId,ModelOperationControl.Kind.VERIFY,
                admission ? "FAILED" : "COMPLETED", admission
                    ? "maintenance admission log pair missing after observer error"
                    : "maintenance decided terminal missing after observer error");
        }
    }
    private static void loggingOrdinary(ModelOperationControl.Kind kind, String outcome) throws Exception {
        try (LoggingFixture f = new LoggingFixture()) {
            f.install();
            ModelSource source = f.source();
            if ("FAILED".equals(outcome)) {
                if (kind == ModelOperationControl.Kind.IMPORT) source = new ModelSource() {
                    public Map<String,UriRef> enumerate(Collection<String> names,int max) throws IOException {
                        throw new IOException(LOG_SECRET);
                    }
                    public InputStream open(UriRef ref) { throw new AssertionError("must not open"); }
                };
                else f.repo = new ModelRepository(f.manifest,f.dir,()->123456789) {
                    public void verifyAll(Progress p,CancelGate gate) throws IOException { throw new IOException(LOG_SECRET); }
                    protected void deleteManaged(File path) throws IOException { throw new IOException(LOG_SECRET); }
                };
            }
            ModelManagementController c = f.controller();
            logCheck(startLogged(c,f,kind,source), "maintenance normal task admitted");
            String id = f.control.peekActive().id;
            if ("CANCELLED".equals(outcome)) c.requestCancel(id);
            f.queue.run();
            ModelManagementState.Phase phase = "COMPLETED".equals(outcome)
                ? ModelManagementState.Phase.SUCCEEDED : ModelManagementState.Phase.valueOf(outcome);
            f.terminal(phase);
            c.requestCancel(id);
            logCheck(f.state.current().phase == phase, "maintenance late cancel preserves terminal");
            logPair(f,id,kind,outcome,"maintenance normal terminal must occur exactly once");
        }
    }
    private static void loggingNoAdmissionAndRetry() throws Exception {
        try (LoggingFixture f = new LoggingFixture()) {
            ModelManagementController c = f.controller();
            logCheck(f.owner.submit(()->{}), "logging owner busy setup");
            logCheck(!c.startVerify() && !c.startImport(f.source()), "logging busy requests rejected");
            logCheck(f.logs.size() == 0 && !f.control.isBusy(), "busy requests have no maintenance events");
            f.queue.run();
            logCheck(!c.startDelete(f.readiness.epoch()+1,true), "logging stale confirmation rejected");
            c.refreshInspect(); f.queue.run();
            logCheck(f.logs.size() == 0 && !f.owner.isBusy() && !f.control.isBusy(), "inspect/stale zero maintenance events");
            f.queue.reject = true; logCheck(!c.startVerify(), "logging retry rejection setup");
            String oldId = f.state.current().activeOperationId;
            f.queue.reject = false; logCheck(c.startImport(f.source()), "logging retry accepted");
            String newId = f.control.peekActive().id;
            f.queue.run(); f.terminal(ModelManagementState.Phase.SUCCEEDED);
            logCheck(!oldId.equals(newId) && f.logs.size() == 4, "logging retry uses new UUID without duplicate terminal");
            logPair(f,oldId,ModelOperationControl.Kind.VERIFY,"FAILED","logging old rejection pair");
            logPair(f,newId,ModelOperationControl.Kind.IMPORT,"COMPLETED","logging new retry pair");
        }
    }
    private static void loggingTelemetry(boolean sinkError, boolean workerError) throws Exception {
        try (LoggingFixture f = new LoggingFixture()) {
            f.install();
            final InternalError fatal = new InternalError(LOG_SECRET);
            if (workerError) f.repo = new ModelRepository(f.manifest,f.dir,()->123456789) {
                public void verifyAll(Progress p,CancelGate gate) { throw fatal; }
            };
            RuntimeLogSink sink = (events,dropped) -> {
                if (sinkError) throw new InternalError(LOG_SECRET);
                throw new IllegalStateException(LOG_SECRET);
            };
            f.logs.addSink(sink);
            ModelManagementController c = f.controller();
            Throwable seen = null;
            try {
                logCheck(c.startVerify(), "logging optional telemetry admission");
                try { f.queue.run(); } catch (Throwable failure) { seen = failure; }
            } finally { f.logs.removeSink(sink); }
            logCheck(seen == (workerError ? fatal : null), "optional telemetry preserves original worker Error identity");
            f.terminal(workerError ? ModelManagementState.Phase.FAILED : ModelManagementState.Phase.SUCCEEDED);
            logPair(f,f.state.current().activeOperationId,ModelOperationControl.Kind.VERIFY,
                workerError ? "FAILED" : "COMPLETED", "optional telemetry must not duplicate or skip emit attempt");
        }
    }
    private static void loggingProtection() throws Exception {
        for (ModelOperationControl.Kind kind : new ModelOperationControl.Kind[]{
                ModelOperationControl.Kind.IMPORT,ModelOperationControl.Kind.VERIFY,ModelOperationControl.Kind.DELETE}) {
            loggingOrdinary(kind,"COMPLETED"); loggingOrdinary(kind,"FAILED");
            if (kind != ModelOperationControl.Kind.DELETE) loggingOrdinary(kind,"CANCELLED");
        }
        loggingNoAdmissionAndRetry();
        for (boolean sinkError : new boolean[]{false,true})
            for (boolean workerError : new boolean[]{false,true}) loggingTelemetry(sinkError,workerError);
    }
    private static void logging(String name) throws Exception {
        switch (name) {
            case "logging-rejection-import": loggingRejection(ModelOperationControl.Kind.IMPORT); break;
            case "logging-rejection-verify": loggingRejection(ModelOperationControl.Kind.VERIFY); break;
            case "logging-rejection-delete": loggingRejection(ModelOperationControl.Kind.DELETE); break;
            case "logging-abnormal": loggingAbnormal(); break;
            case "logging-terminal-observer": loggingObserver(false); break;
            case "logging-admission-observer": loggingObserver(true); break;
            case "logging-protection": loggingProtection(); break;
            default: throw new IllegalArgumentException("unknown logging case");
        }
    }
    private static void loggingAll() throws Exception {
        for (String name : new String[]{"logging-rejection-import","logging-rejection-verify","logging-rejection-delete",
                "logging-abnormal","logging-terminal-observer","logging-admission-observer","logging-protection"}) logging(name);
    }
    public static void main(String[] args)throws Exception {
        if (args.length > 0) {
            logging(args[0]);
            System.out.println("PASS maintenance logging case "+args[0]+": "+checks+" checks");
            return;
        }
        loggingAll();
        // New page subscription gets live progress/terminal invalidations; old page unsubscribed.
        {
            Fixture f=new Fixture(); ModelManagementController c=f.controller();
            AtomicInteger old=new AtomicInteger(), fresh=new AtomicInteger();
            ModelManagementState.Listener oldListener=()->old.incrementAndGet();
            ModelManagementState.Listener newListener=()->fresh.incrementAndGet();
            f.state.addListener(oldListener); check(c.startImport(f.source())); check(old.get()>0);
            f.state.removeListener(oldListener); int oldCount=old.get(); f.state.addListener(newListener);
            f.queue.run();check(old.get()==oldCount);check(fresh.get()>2);f.terminal(ModelManagementState.Phase.SUCCEEDED);
            f.state.removeListener(newListener);
        }
        // Owner first. Busy never creates op or overwrites cached model state. Rejection rolls back.
        {
            Fixture f=new Fixture(); ModelManagementController c=f.controller();
            check(f.owner.submit(()->{})); ModelManagementState.Snapshot before=f.state.current();
            check(!c.startVerify());check(!c.startImport(f.source()));check(!c.startDelete(0,true));c.refreshInspect();
            check(f.control.peekActive()==null);check(f.state.current()==before);f.queue.run();
            f.queue.reject=true;check(!c.startVerify());check(!f.control.isBusy());check(!f.owner.isBusy());
            f.queue.reject=false;check(c.startImport(f.source()));f.queue.run();f.terminal(ModelManagementState.Phase.SUCCEEDED);
        }
        // Cancel identity exists before worker starts; same request stops, stale IDs don't.
        {
            Fixture f=new Fixture();ModelManagementController c=f.controller();ModelRepositoryCancelTest.Source src=f.source();
            check(c.startImport(src));String id=f.control.peekActive().id;
            check(f.owner.isBusy());check(!c.startVerify());c.requestCancel("stale");check(!f.control.peekActive().isCancelRequested());
            c.requestCancel(id);check(f.state.current().phase==ModelManagementState.Phase.CANCELLING);
            f.queue.run();f.terminal(ModelManagementState.Phase.CANCELLED);check(src.opens==0);
            check(f.readiness.state()==ModelReadiness.State.NOT_INSTALLED);
            check(c.startImport(src));c.requestCancel(id);f.queue.run();f.terminal(ModelManagementState.Phase.SUCCEEDED);
            check(f.readiness.isReady());check(f.state.current().installedBytes==6);check(f.state.current().files.get(0).verified);
            c.requestCancel(f.state.current().activeOperationId);check(f.state.current().phase==ModelManagementState.Phase.SUCCEEDED);
        }
        // Inspection runs in the actual queued worker and never creates the directory/hashes files.
        {
            Fixture f=new Fixture();check(f.dir.delete());ModelManagementController c=f.controller();
            c.refreshInspect();check(f.state.current().files.isEmpty());check(!f.dir.exists());
            f.queue.run();check(f.state.current().files.size()==2);check(f.state.current().expectedBytes==6);
            check(f.state.current().availableBytes==123456789);check(!f.dir.exists());check(f.readiness.state()==ModelReadiness.State.NOT_INSTALLED);
            f.dir.mkdir();Files.write(new File(f.dir,"a").toPath(),new byte[]{9});
            c.refreshInspect();f.queue.run();check(f.state.current().installedBytes==1);check(f.readiness.state()==ModelReadiness.State.INCOMPLETE);
            f.install();c.refreshInspect();f.queue.run();check(f.readiness.state()==ModelReadiness.State.UNVERIFIED);
        }
        // Verify starts its token BEFORE hashing; stale epoch after real hashing cannot commit.
        {
            Fixture f=new Fixture();f.install();f.ready();
            f.repo=new ModelRepository(f.manifest,f.dir,()->123456789){
                public void verifyAll(Progress p,CancelGate g)throws IOException{
                    check(!f.readiness.isReady());check(f.readiness.state()==ModelReadiness.State.VERIFYING);
                    super.verifyAll(p,g);f.readiness.invalidate();
                }
            };
            ModelManagementController c=f.controller();check(c.startVerify());f.queue.run();f.terminal(ModelManagementState.Phase.FAILED);check(!f.readiness.isReady());
        }
        // Cancel after last real hash but before success/READY arbitration; exact cancelled outcome.
        {
            Fixture f=new Fixture();f.install();f.ready();
            f.repo=new ModelRepository(f.manifest,f.dir,()->123456789){
                public void verifyAll(Progress p,CancelGate g)throws IOException{super.verifyAll(p,g);f.control.requestCancel(f.control.peekActive().id);}
            };
            ModelManagementController c=f.controller();check(c.startVerify());f.queue.run();f.terminal(ModelManagementState.Phase.CANCELLED);
            check(!f.readiness.isReady());check(f.readiness.state()==ModelReadiness.State.UNVERIFIED);
        }
        // Preflight failure before mutations preserves a valid same-epoch proof.
        {
            Fixture f=new Fixture();f.install();f.ready();long epoch=f.readiness.epoch();
            ModelSource bad=new ModelSource(){
                public Map<String,UriRef> enumerate(Collection<String> n,int max)throws IOException{throw new IOException("provider permission");}
                public InputStream open(UriRef ref){throw new AssertionError("must not open");}
            };
            ModelManagementController c=f.controller();check(c.startImport(bad));f.queue.run();f.terminal(ModelManagementState.Phase.FAILED);
            check(f.readiness.isReady());check(f.readiness.epoch()==epoch);
            check(c.startVerify());c.requestCancel(f.control.peekActive().id);f.queue.run();f.terminal(ModelManagementState.Phase.CANCELLED);
            check(f.readiness.state()==ModelReadiness.State.UNVERIFIED);
        }
        // A preflight that discovers an invalid local SHA must revoke even a cached READY.
        {
            Fixture f=new Fixture();f.install();f.ready();
            Files.write(new File(f.dir,"b").toPath(),new byte[]{8,8,8});
            ModelManagementController c=f.controller();
            check(c.startImport(new ModelRepositoryCancelTest.Source(DATA)));f.queue.run();
            f.terminal(ModelManagementState.Phase.FAILED);
            check(!f.readiness.isReady());check(f.readiness.state()==ModelReadiness.State.INVALID);
        }
        // Bad final SHA is INVALID, not a false READY; exact filename retained.
        {
            Fixture f=new Fixture();f.install();f.ready();Files.write(new File(f.dir,"b").toPath(),new byte[]{9,9,9});
            ModelManagementController c=f.controller();check(c.startVerify());f.queue.run();f.terminal(ModelManagementState.Phase.FAILED);
            check(f.readiness.state()==ModelReadiness.State.INVALID);check(f.state.current().failedFiles.equals(Arrays.asList("b")));
        }
        // Delete revokes before IO; ordinary partial fault preserves accurate bytes, retry deletes only managed files.
        {
            Fixture f=new Fixture();f.install();f.ready();long epoch=f.readiness.epoch();AtomicBoolean fail=new AtomicBoolean(true);
            Files.write(new File(f.dir,"unknown").toPath(),DATA);
            f.repo=new ModelRepository(f.manifest,f.dir,()->987654321){
                protected void deleteManaged(File file)throws IOException{
                    check(!f.readiness.isReady());check(f.readiness.epoch()>epoch);
                    if(file.getName().equals("b")&&fail.get())throw new IOException("denied");super.deleteManaged(file);
                }
            };
            ModelManagementController c=f.controller();check(!c.startDelete(epoch,false));check(!c.startDelete(epoch+1,true));check(!f.control.isBusy());
            check(c.startDelete(epoch,true));c.requestCancel(f.control.peekActive().id);check(!f.control.peekActive().isCancelRequested());
            f.queue.run();f.terminal(ModelManagementState.Phase.FAILED);check(f.state.current().deleteSucceeded==1);check(f.state.current().deleteFailed==1);
            check(f.state.current().installedBytes==3);check(f.state.current().availableBytes==987654321);check(f.readiness.state()==ModelReadiness.State.INCOMPLETE);
            fail.set(false);check(!c.startDelete(epoch,true));check(c.startDelete(f.readiness.epoch(),true));f.queue.run();f.terminal(ModelManagementState.Phase.SUCCEEDED);
            check(f.readiness.state()==ModelReadiness.State.NOT_INSTALLED);check(f.state.current().unexpectedFiles.equals(Arrays.asList("unknown")));check(new File(f.dir,"unknown").exists());
        }
        // Blocked close and failed cleanup: cancellation cannot release shared owner or hide residue.
        {
            Fixture f=new Fixture();ModelRepositoryCancelTest.Gate gate=new ModelRepositoryCancelTest.Gate();
            f.repo=new ModelRepository(f.manifest,f.dir,()->123456789){
                protected void deleteManaged(File file)throws IOException {if(file.exists())throw new IOException("cleanup denied");super.deleteManaged(file);}
            };
            ModelManagementController c=f.controller();ModelRepositoryCancelTest.Source src=f.source();src.closeGate=gate;
            check(c.startImport(src));
            try(ModelRepositoryCancelTest.Worker w=new ModelRepositoryCancelTest.Worker(()->f.queue.run())) {
                try {gate.await();c.requestCancel(f.control.peekActive().id);check(f.owner.isBusy());check(!c.startVerify());check(!f.owner.submit(()->{}));}
                finally{gate.close();}
                w.join();check(w.error.get()==null);
            }
            f.terminal(ModelManagementState.Phase.CANCELLED);check(f.state.current().cleanupOutcome==ModelManagementState.Outcome.FAILED);
            check(f.state.current().orphanPartBytes==3);check(f.readiness.state()==ModelReadiness.State.INCOMPLETE);
        }
        // Blocked cleanup still owns the shared lane; cancellation never frees it early.
        {
            Fixture f=new Fixture();ModelRepositoryCancelTest.Gate gate=new ModelRepositoryCancelTest.Gate();
            f.repo=new ModelRepository(f.manifest,f.dir,()->123456789){
                protected void deleteManaged(File file)throws IOException{if(file.exists())gate.block();super.deleteManaged(file);}
            };
            ModelManagementController c=f.controller();ModelRepositoryCancelTest.Source src=f.source();src.failRead=true;
            check(c.startImport(src));
            try(ModelRepositoryCancelTest.Worker w=new ModelRepositoryCancelTest.Worker(()->f.queue.run())) {
                try{gate.await();c.requestCancel(f.control.peekActive().id);check(f.owner.isBusy());check(!c.startVerify());}
                finally{gate.close();}
                w.join();check(w.error.get()==null);
            }
            f.terminal(ModelManagementState.Phase.CANCELLED);check(f.state.current().orphanPartBytes==0);
            check(f.state.current().cleanupOutcome==ModelManagementState.Outcome.SUCCEEDED);
        }
        // Confirmation is rechecked under owner in worker, and illegal managed path is hard refusal.
        {
            Fixture f=new Fixture();f.install();f.ready();ModelManagementController c=f.controller();
            check(c.startDelete(f.readiness.epoch(),true));f.readiness.invalidate();f.queue.run();f.terminal(ModelManagementState.Phase.FAILED);check(new File(f.dir,"a").exists());
            Files.delete(new File(f.dir,"a").toPath());new File(f.dir,"a").mkdir();
            check(c.startDelete(f.readiness.epoch(),true));f.queue.run();f.terminal(ModelManagementState.Phase.FAILED);check(new File(f.dir,"b").exists());check(f.readiness.state()==ModelReadiness.State.INVALID);
        }
        System.out.println("PASS "+checks+" deterministic model controller/connected runner isolation checks");
    }
}
