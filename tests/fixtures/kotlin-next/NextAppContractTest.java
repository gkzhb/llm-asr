import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.llmasr.minimal.AppGraph;
import org.llmasr.minimal.AsrImeService;
import org.llmasr.minimal.asr.InferenceAdapter;
import org.llmasr.minimal.audio.*;
import org.llmasr.minimal.ime.*;
import org.llmasr.minimal.model.*;
import org.llmasr.minimal.platform.OperationContext;
import org.llmasr.minimal.task.*;
import org.llmasr.minimal.transcription.*;

/** Calls real app/controller implementations on explicit host platform fakes, never Android acceptance. */
public final class NextAppContractTest {
    private static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static void graph() throws Exception {
        Path root = Files.createTempDirectory("next-graph-");
        AppGraph graph = null;
        try {
            android.content.Context context = new android.content.Context(root.toFile());
            AppGraph.install(context); graph = AppGraph.get(); graph.coordinator().awaitFree();
            check(graph.context() != null && graph.modelRepository() != null, "real graph assembled");
            check(graph == AppGraph.get() && !graph.coordinator().isBusy(), "graph singleton/startup finalized");
            Throwable seen = null;
            try { AppGraph.install(null); } catch (Throwable failure) { seen = failure; }
            check(seen instanceof IllegalArgumentException && "appContext required".equals(seen.getMessage()),
                "installed Graph must still reject null context before fast return");
            AppGraph.install(context); check(graph == AppGraph.get(), "graph repeat install identity");
        } finally {
            if (graph != null) {
                graph.coordinator().awaitFree(); graph.runtimeLogWorker().close();
                // Worker IO is real; never delete its directory while a drain may still use it.
                Object executor = field(graph.runtimeLogWorker(), "executor");
                if (executor instanceof java.util.concurrent.ExecutorService) {
                    ((java.util.concurrent.ExecutorService)executor).shutdown();
                    check(((java.util.concurrent.ExecutorService)executor).awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS), "graph log worker actual exit");
                }
            }
            Field singleton = AppGraph.class.getDeclaredField("INSTANCE"); singleton.setAccessible(true); singleton.set(null, null);
            delete(root);
        }
    }
    static ModelAccess preparedAccess(Path root) throws Exception {
        return preparedAccess(root,new ModelResidency.Native(){public double prepare(String c,String cache){return 0;}public void release(){}});
    }
    static ModelAccess preparedAccess(Path root,ModelResidency.Native nativeControl) throws Exception {
        return preparedAccess(root,nativeControl,new int[1]);
    }
    static ModelAccess preparedAccess(Path root,ModelResidency.Native nativeControl,int[] ioCalls) throws Exception {
        Path models=Files.createDirectories(root.resolve("prepared-model"));Files.write(models.resolve("a"),new byte[]{1,2,3});
        Path journal=Files.createDirectories(root.resolve("prepared-proof"));
        ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("a",3,"039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")));
        ModelReadiness ready=new ModelReadiness();
        ModelVerification.Io io=new ModelVerification.Io(){
            public boolean exists(File f){ioCalls[0]++;return Files.exists(f.toPath(),LinkOption.NOFOLLOW_LINKS);}
            public String identity(File f,boolean directory)throws IOException{ioCalls[0]++;return new java.util.TreeMap<>(Files.readAttributes(f.toPath(),"unix:dev,ino,mode,size,lastModifiedTime,ctime",LinkOption.NOFOLLOW_LINKS)).toString();}
            public byte[] read(String name,int limit)throws IOException{
                ioCalls[0]++;Path p=journal.resolve(name);if(!Files.exists(p))return null;
                try(InputStream in=Files.newInputStream(p)){ByteArrayOutputStream out=new ByteArrayOutputStream();for(int b;(b=in.read())!=-1;){if(out.size()==limit)throw new IOException("limit");out.write(b);}return out.toByteArray();}
            }
            public void write(String name,byte[] bytes)throws IOException{
                ioCalls[0]++;Path temp=journal.resolve(name+".tmp");try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(bytes);out.getFD().sync();}
                Files.move(temp,journal.resolve(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                try(java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(journal,StandardOpenOption.READ)){channel.force(true);}
            }
        };
        ModelVerification verification=new ModelVerification(manifest,ready,io,new ModelVerification.MaintenanceGuard(){public ModelVerification.MaintenanceLease acquire(){return ()->{};}public void invalidate(){}});ModelRepository repo=new ModelRepository(manifest,models.toFile(),()->Long.MAX_VALUE,verification);
        org.llmasr.minimal.modelmanagement.ModelOperationControl control=new org.llmasr.minimal.modelmanagement.ModelOperationControl();
        org.llmasr.minimal.modelmanagement.ModelOperationControl.Operation op=control.tryBegin(org.llmasr.minimal.modelmanagement.ModelOperationControl.Kind.VERIFY);
        ModelVerification.Token token=verification.begin(repo);repo.verifyAll();check(verification.commit(repo,token,new ModelVerification.CommitGate(){public boolean reserve(ModelReadiness.VerifyToken t){return control.tryReserveProofCommit(op,ready,t);}public boolean succeed(ModelReadiness.VerifyToken t){return control.trySucceed(op,ready,t);}}),"synthetic actual proof");control.complete(op,true);
        ModelResidency resident=new ModelResidency(nativeControl,()->true);
        ModelAccess access=new ModelAccess(repo,ready,resident);access.prepare("synthetic-config","synthetic-cache");return access;
    }
    static void preInputAdmission() throws Exception {
        Path root=Files.createTempDirectory("preinput-model-");
        final int[] inputs={0};final int[] bridges={0};List<Runnable> jobs=new ArrayList<>();
        try {
            OperationContext context=new OperationContext(){public File filesDir(){return root.toFile();}public File cacheDir(){return root.toFile();}
                public InputStream openAsset(String name){inputs[0]++;return null;}
                public android.content.ContentResolver contentResolver(){inputs[0]++;return new android.content.ContentResolver();}};
            TaskCoordinator owner=new TaskCoordinator(jobs::add);AppState state=new AppState();
            ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("a",3,"039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")));
            ModelRepository repo=ModelRepository.forWorker(manifest,root.resolve("model").toFile(),()->Long.MAX_VALUE);
            ModelAccess unprepared=new ModelAccess(repo,new ModelReadiness());
            InferenceAdapter inference=new InferenceAdapter(null,unprepared::requireReady,()->{bridges[0]++;},(c,w,l,t,p)->{bridges[0]++;return null;});
            AppReportWriter reports=new AppReportWriter(context);
            AsrOperation ops=new AsrOperation(context,owner,repo,state,new TextExportController<android.net.Uri>(u->null,Runnable::run),inference,reports,unprepared);
            android.content.ContentResolver.calls=0;org.llmasr.minimal.audio.ForegroundRecorder.calls=0;
            check(ops.startSample("Chinese")&&owner.isBusy()&&inputs[0]==0,"sample admitted no UI IO");jobs.remove(0).run();
            check(ops.startWave(new android.net.Uri(),"Chinese"),"wave admitted");jobs.remove(0).run();
            check(ops.startRecording("Chinese"),"record admitted");jobs.remove(0).run();
            check(inputs[0]==0&&android.content.ContentResolver.calls==0&&org.llmasr.minimal.audio.ForegroundRecorder.calls==0&&bridges[0]==0,"unprepared app must reject before asset/provider/capture/native");
            org.llmasr.minimal.ime.ImeBackend backend=new org.llmasr.minimal.ime.ImeBackend(context,repo,inference,unprepared);
            ImeController ime=new ImeController(owner,backend,()->{});ImeSession session=ime.begin(1,0,"editor");
            check(ime.start(session,"Chinese"),"IME admitted");jobs.remove(0).run();
            check(org.llmasr.minimal.audio.ForegroundRecorder.calls==0&&bridges[0]==0&&!owner.isBusy(),"unprepared real IME backend capture0 and owner finalized");
        } finally {check(jobs.isEmpty(),"preinput worker final exit");delete(root);}
    }
    static void operation() throws Exception {
        Path root = Files.createTempDirectory("next-operation-");
        try {
            PcmWave pcm = new PcmWave(); pcm.append(new short[PcmWave.MIN_SAMPLES], PcmWave.MIN_SAMPLES);
            byte[] sample = pcm.finish();
            OperationContext context = new OperationContext() {
                public File filesDir() { return root.toFile(); }
                public File cacheDir() { return root.toFile(); }
                public InputStream openAsset(String name) { return new ByteArrayInputStream(sample); }
                public android.content.ContentResolver contentResolver() { return new android.content.ContentResolver(); }
            };
            ModelManifest manifest = new ModelManifest(Arrays.asList(new ModelEntry("a", 0, String.join("", Collections.nCopies(64,"0")))));
            ModelRepository repository = ModelRepository.forWorker(manifest, root.resolve("model").toFile(), () -> Long.MAX_VALUE);
            TaskCoordinator coordinator = new TaskCoordinator(Runnable::run);
            AppState state = new AppState();
            InferenceAdapter inference = new InferenceAdapter(null, () -> {}, () -> {},
                (c,w,l,t,listener) -> "1 2 3\n<asr_text>synthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            AppReportWriter reports = new AppReportWriter(context);
            AsrOperation operation = new AsrOperation(context, coordinator, repository, state,
                new TextExportController<android.net.Uri>(target -> new ByteArrayOutputStream(), Runnable::run), inference, reports, preparedAccess(root));
            reports.failSuccess = true;
            check(operation.startSample("Chinese"), "real operation admitted");
            check(!reports.reportedAtSuccess, "success report must precede inferenceReported");
            check(reports.successes == 1 && reports.failures == 1 && state.lastText().isEmpty(), "report failure preserves lifecycle failure");
            check(!coordinator.isBusy() && reports.lastContext.inputWav != null && !reports.lastContext.inputWav.exists(), "operation cleanup owns actual request WAV");
            reports.failSuccess = false;
            check(operation.startSample("Chinese"), "operation retry admitted");
            check(reports.lastContext.inferenceReported && state.lastText().equals("synthetic"), "real success after report");
            state.setLastText("old"); ResultState.Snapshot old = state.resultState().begin();
            check(operation.clearResults() && state.lastText().isEmpty(), "clear uses actual result facade");
            check(state.resultState().currentRevision() != old.revision && !coordinator.isBusy(), "clear revision/owner");
        } finally { delete(root); }
    }
    static void residentOwnerRelease() throws Exception {
        Path root=Files.createTempDirectory("resident-owner-");List<Runnable> jobs=new ArrayList<>();int[] unload={0};
        try {
            ModelAccess access=preparedAccess(root,new ModelResidency.Native(){public double prepare(String c,String t){return 0;}public void release(){unload[0]++;}});
            PcmWave pcm=new PcmWave();pcm.append(new short[PcmWave.MIN_SAMPLES],PcmWave.MIN_SAMPLES);byte[] sample=pcm.finish();
            OperationContext context=new OperationContext(){public File filesDir(){return root.toFile();}public File cacheDir(){return root.toFile();}public InputStream openAsset(String n){return new ByteArrayInputStream(sample);}public android.content.ContentResolver contentResolver(){return new android.content.ContentResolver();}};
            ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("a",0,String.join("",Collections.nCopies(64,"0")))));ModelRepository repo=ModelRepository.forWorker(manifest,root.resolve("unused").toFile(),()->Long.MAX_VALUE);
            TaskCoordinator owner=new TaskCoordinator(jobs::add);AppState state=new AppState();AsrOperation[] ops={null};
            InferenceAdapter adapter=new InferenceAdapter(null,access::requireResidentReady,()->{throw new AssertionError("warm request library");},(c,w,l,t,p)->{
                check(owner.isBusy(),"native owner busy");ops[0].startRelease();ops[0].startRelease();
                check(unload[0]==0&&jobs.isEmpty(),"busy release cannot unload or queue before actual owner finish");
                try{access.requireResidentReady();throw new AssertionError("pending request admission");}catch(IOException expected){}
                p.onPhase(3);p.onPhase(4);return "0 1 2\npublic".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            },new InferenceAdapter.Session(){public Object enter()throws IOException{return access.enterRequest();}public void finish(Object token,boolean ok)throws Exception{access.finishRequest(token,ok);}});
            AppReportWriter reports=new AppReportWriter(context);
            ops[0]=new AsrOperation(context,owner,repo,state,new TextExportController<android.net.Uri>(u->null,Runnable::run),adapter,reports,access);
            check(ops[0].startSample("auto")&&jobs.size()==1,"owner inference admitted");jobs.remove(0).run();
            check(unload[0]==0&&jobs.size()==1&&owner.isBusy(),"owner true exit queues exactly one maintenance release");
            check(reports.lastContext.inputWav!=null&&!reports.lastContext.inputWav.exists(),"request cleanup before native unload");jobs.remove(0).run();
            check(unload[0]==1&&!owner.isBusy()&&!access.residentSnapshot().getPendingRelease(),"pending native unload after runner cleanup once");
            TaskCoordinator rejected=new TaskCoordinator(r->{throw new java.util.concurrent.RejectedExecutionException("controlled");});
            AsrOperation failOps=new AsrOperation(context,rejected,repo,state,new TextExportController<android.net.Uri>(u->null,Runnable::run),adapter,reports,access);
            failOps.startRelease();check(!rejected.isBusy()&&access.residentSnapshot().getPendingRelease(),"rejected maintenance leaves admission closed no spin");
            failOps.startRelease();check(!rejected.isBusy()&&access.residentSnapshot().getPendingRelease(),"explicit rejected retry bounded");
        } finally {check(jobs.isEmpty(),"resident owner worker exited before remove");delete(root);}
    }
    static void service() throws Exception {
        TaskCoordinator owner = new TaskCoordinator(Runnable::run);
        ImeController.Backend backend = new ImeController.Backend() {
            public void requireModelReady() { /* Explicit synthetic prepared policy. */ }
            public byte[] capture(RecordingControl control, ImeController.Progress progress) {
                control.start(() -> {}); control.stop(); control.captureReleased(); return new byte[]{1};
            }
            public String transcribe(RequestContext request, byte[] wav, String language) { return "synthetic preview"; }
            public void cleanup(RequestContext request) {}
        };
        ImeController controller = new ImeController(owner, backend, () -> {});
        AsrImeService service = new AsrImeService();
        set(service, "controller", controller); set(service, "alive", true);
        service.onStartInputView(new android.view.inputmethod.EditorInfo(), false);
        ImeSession original = (ImeSession)field(service, "session");
        check(original != null && controller.start(original, "Chinese"), "real Service session and controller preview");
        check(original.preview().equals("synthetic preview"), "preview produced by actual controller");
        service.onFinishInputView(true);
        check(!original.valid() && original.preview().isEmpty(), "Service finishView must invalidate old session");
        check(!controller.commit(original, "fixture:1:field:1:0", 1, 0, text -> { throw new AssertionError("stale editor write"); }), "old Service preview never commits");
        service.onStartInputView(new android.view.inputmethod.EditorInfo(), false);
        ImeSession next = (ImeSession)field(service, "session");
        check(next != original && next.preview().isEmpty(), "same editor restart fresh empty session");
        service.onDestroy(); check(!next.valid() && !owner.isBusy(), "service destroy invalidates without owner leak");
    }
    static final class ImeFixture implements AutoCloseable {
        final TaskCoordinator owner = new TaskCoordinator(Runnable::run);
        int captures, writes; final List<String> events = new ArrayList<>();
        final ImeController controller = new ImeController(owner, new ImeController.Backend() {
            public void requireModelReady() { /* Explicit synthetic prepared policy. */ }
            public byte[] capture(RecordingControl c, ImeController.Progress p) {
                captures++; c.start(() -> {}); c.stop(); c.captureReleased(); return new byte[]{1};
            }
            public String transcribe(RequestContext c, byte[] w, String l) { return "synthetic preview"; }
            public void cleanup(RequestContext c) {}
        }, () -> {});
        final AsrImeService service = new AsrImeService();
        final android.widget.LinearLayout root;
        final ImeSession original;
        final Path graphRoot = Files.createTempDirectory("ime-ui-readiness-fixture-");
        final AppGraph graph;
        final ModelAccess access;
        final int[] proofIo = new int[1];
        boolean retainRoot; // Unconfirmed test worker exit must never permit deletion of its input tree.
        ImeFixture() throws Exception {
            this(new ModelResidency.Native(){public double prepare(String c,String t){return 0;}public void release(){}});
        }
        ImeFixture(ModelResidency.Native nativeControl) throws Exception {
            AppGraph.install(new android.content.Context(graphRoot.toFile())); graph=AppGraph.get(); graph.coordinator().awaitFree();
            // Real three-byte proof and real resident preparation, not a counterfeit READY flag.
            access=preparedAccess(graphRoot,nativeControl,proofIo);
            set(graph,"modelAccess",access);set(graph,"readiness",field(access,"readiness"));set(service,"graph",graph);
            android.widget.Toast.shows=0; android.widget.Toast.text="";android.widget.Toast.failure=null;android.widget.Toast.fatal=null;
            set(service,"controller",controller);set(service,"alive",true);
            service.connection=(text,n)->{writes++;return true;};
            service.onStartInputView(service.info,false);
            original=(ImeSession)field(service,"session");
            check(controller.start(original,"Chinese"),"fixture production preview admitted");
            root=(android.widget.LinearLayout)service.onCreateInputView();
            service.event=()->events.add(service.previousCalls==0?"hide":"previous");
            service.manager.event=()->events.add("picker");
        }
        android.widget.Button button(String label) {return findButton(root,label);}
        void click(String label) {check(button(label).performClick(),"actual enabled button: "+label);}
        void stale() throws Exception {
            check(!original.valid()&&original.preview().isEmpty()&&field(service,"session")==null,
                "external switch must invalidate before platform request");
            check(!controller.commit(original,"fixture:1:field:1:0",1,0,text->{writes++;return true;})&&writes==0,
                "external switch never commits old preview");
        }
        public void close() throws Exception {
            service.onDestroy();android.os.Handler.pending.clear();android.widget.Toast.failure=null;android.widget.Toast.fatal=null;check(!owner.isBusy(),"IME fixture actual owner exit");
            graph.coordinator().awaitFree(); graph.runtimeLogWorker().close();
            java.util.concurrent.ExecutorService executor=(java.util.concurrent.ExecutorService)field(graph.runtimeLogWorker(),"executor");
            executor.shutdown();check(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS),"IME graph IO finalized");
            Field singleton=AppGraph.class.getDeclaredField("INSTANCE");singleton.setAccessible(true);singleton.set(null,null);check(!retainRoot,"unconfirmed IME test worker exit; retain "+graphRoot);delete(graphRoot);
        }
    }
    static android.widget.Button findButton(android.widget.LinearLayout root,String label) {
        for(android.view.View child:root.children) {
            if(child instanceof android.widget.Button&&((android.widget.Button)child).getText().toString().equals(label)) return (android.widget.Button)child;
            if(child instanceof android.widget.LinearLayout) {
                android.widget.Button b=findButton((android.widget.LinearLayout)child,label);if(b!=null)return b;
            }
        }
        return null;
    }
    static void imeVisibility() throws Exception {
        android.inputmethodservice.InputMethodService direct=new android.inputmethodservice.InputMethodService();direct.visibilityModel=true;
        check(direct.switchToPreviousInputMethod()&&direct.previousVisible,"model direct previous keeps show intent");
        android.inputmethodservice.InputMethodService hidden=new android.inputmethodservice.InputMethodService();hidden.visibilityModel=true;
        hidden.requestHideSelf(0);check(hidden.switchToPreviousInputMethod()&&!hidden.previousVisible,"model accepted is not visible after hide");
        try(ImeFixture f=new ImeFixture()) {
            f.service.visibilityModel=true;final List<Boolean> invalid=new ArrayList<>();
            f.service.event=()->invalid.add(!f.original.valid()&&f.original.preview().isEmpty());
            f.click("返回上一输入法");
            check(f.service.previousCalls==1&&f.service.acceptedRequests==1&&f.service.manager.calls==0,"accepted previous never duplicates picker");
            check(f.service.previousVisible,"accepted previous request must leave previous keyboard visible in host visibility model");
            check(f.service.hides==0&&f.service.showRequested&&f.service.selectedIme.equals("previous"),"return preserves platform show intent");
            check(invalid.size()==1&&invalid.get(0),"external switch must invalidate before platform request");
            f.stale();check(f.captures==1&&f.writes==0,"visible previous never records or commits");
        }
    }
    static void imeFallback() throws Exception {
        for(int mode=0;mode<7;mode++) try(ImeFixture f=new ImeFixture()) {
            f.service.visibilityModel=true;f.service.previousResult=false;
            if(mode==1)f.service.previousFailure=new SecurityException("PRIVATE editor sentinel");
            if(mode==2)f.service.previousFailure=new IllegalStateException("PRIVATE editor sentinel");
            if(mode==3)f.service.manager.failure=new SecurityException("PRIVATE editor sentinel");
            if(mode==4)f.service.noManager=true;
            if(mode==5)f.service.serviceFailure=new IllegalStateException("PRIVATE editor sentinel");
            if(mode==6)f.service.hideFailure=new IllegalStateException("PRIVATE hide sentinel");
            f.click("返回上一输入法");
            check(f.service.previousCalls==1&&f.service.manager.calls==(mode==4||mode==5?0:1),
                "previous failure must launch picker once");
            check(f.service.hides==0&&f.service.showRequested&&f.events.get(0).equals("previous"),"return fallback must not clear show intent");
            f.stale();check(f.captures==1&&f.writes==0,"switch does not record or commit");
            check(android.widget.Toast.shows==(mode>=3&&mode<=5?1:0),"only failed picker emits one friendly hint");
            if(android.widget.Toast.shows>0)check(android.widget.Toast.text.equals("暂时无法切换，请使用系统输入法选择入口。"),"picker hint sanitized");
        }
    }
    static void imeInvalidation() throws Exception {
        try(ImeFixture f=new ImeFixture()) {
            final List<Boolean> invalid=new ArrayList<>();
            f.service.event=()->invalid.add(!f.original.valid()&&f.original.preview().isEmpty());
            f.click("返回上一输入法");
            check(invalid.size()==1&&invalid.get(0),"external switch must invalidate before platform request");
            f.stale();check(f.service.previousCalls==1&&f.service.manager.calls==0,"accepted previous never duplicates picker");
        }
    }
    static void imeHideFailure() throws Exception {
        for(String label:new String[]{"切换键盘","设置 / 授权","模型管理"}) for(boolean failHide:new boolean[]{false,true}) try(ImeFixture f=new ImeFixture()) {
            f.service.hideChangesShown=true;
            if(failHide)f.service.hideFailure=new IllegalStateException("hide sentinel");
            try{f.click(label);}catch(RuntimeException ignored){/* Assert observable attempt outside listener. */}
            check(f.service.hides==1&&f.service.previousCalls==0,"generic actions retain hide only");
            check(f.service.manager.calls==(label.equals("切换键盘")?1:0)&&f.service.activityCalls==(label.equals("切换键盘")?0:1),"hide failure must not suppress external attempt");
            check(f.events.get(0).equals("hide"),"generic hide precedes external action");f.stale();
        }
        try(ImeFixture f=new ImeFixture()) {
            f.controller.invalidate();set(f.service,"controller",null);
            f.service.hideFailure=new SecurityException("hide sentinel");f.service.previousResult=false;
            f.click("返回上一输入法");
            check(f.service.previousCalls==1&&f.service.manager.calls==1&&f.service.hides==0,"null controller return still attempts fallback without hide");
            check(!f.button("丢弃 / 重新开始").isEnabled(),"null controller cannot restart");
        }
        try(ImeFixture f=new ImeFixture()) {
            f.controller.invalidate();set(f.service,"controller",null);f.service.hideFailure=new SecurityException("hide sentinel");
            f.click("切换键盘");check(f.service.hides==1&&f.service.manager.calls==1,"null controller generic hide failure still attempts picker");
        }
        for(boolean fatalToast:new boolean[]{false,true}) try(ImeFixture f=new ImeFixture()) {
            f.service.previousResult=false;f.service.noManager=true;
            Error fatal=new AssertionError("fatal toast sentinel");
            if(fatalToast)android.widget.Toast.fatal=fatal;else android.widget.Toast.failure=new IllegalStateException("PRIVATE toast sentinel");
            Throwable seen=null;try{f.click("返回上一输入法");}catch(Throwable failure){seen=failure;}
            check(fatalToast?seen==fatal:seen==null,"return contains Toast RuntimeException but propagates Error");
            check(f.service.previousCalls==1&&f.service.hides==0,"Toast failure never retries or hides");f.stale();
        }
        try(ImeFixture f=new ImeFixture()) {
            Error fatal=new AssertionError("fatal previous sentinel");f.service.previousFatal=fatal;Throwable seen=null;
            try{f.click("返回上一输入法");}catch(Error failure){seen=failure;}
            check(seen==fatal&&f.service.manager.calls==0,"fatal previous error propagates without fallback");f.stale();
        }
        // Fatal errors are not swallowed by the RuntimeException policy.
        ImeController c=new ImeController(new TaskCoordinator(Runnable::run),null,()->{});
        Error fatal=new AssertionError("fatal sentinel");Throwable seen=null;int[] launches={0};
        try{c.leaveForExternalUi(()->{throw fatal;},()->launches[0]++);}catch(Error e){seen=e;}
        check(seen==fatal&&launches[0]==0,"fatal hide errors propagate");
    }
    static void imeRestart() throws Exception {
        for(int mode=0;mode<4;mode++) try(ImeFixture f=new ImeFixture()) {
            f.service.previousResult=false;
            if(mode==1)f.service.manager.failure=new IllegalStateException("picker sentinel");
            if(mode==2)f.service.hideFailure=new IllegalStateException("hide sentinel");
            if(mode==3)f.service.previousResult=true;
            f.click("返回上一输入法");f.stale();
            check(f.service.shown&&!f.button("确认输入").isEnabled(),"no callback leaves visible but invalid UI");
            android.widget.Button restart=f.button("丢弃 / 重新开始");
            check(restart.isEnabled(),"visible failed switch must allow manual fresh restart");
            restart.performClick();ImeSession fresh=(ImeSession)field(f.service,"session");
            check(fresh!=null&&fresh!=f.original&&fresh.valid()&&fresh.preview().isEmpty(),"visible failed switch must allow manual fresh restart");
            check(f.captures==1&&f.writes==0,"manual fresh never auto records/commits");
            f.click("录音");check(f.captures==2,"manual record after callback-free recovery");
            f.click("确认输入");check(f.writes==1,"explicit confirm once");
            f.click("返回上一输入法");check(f.writes==1,"return after confirm never resends");
        }
        try(ImeFixture f=new ImeFixture()) {
            f.click("切换键盘");f.stale();check(f.service.previousCalls==0&&f.service.manager.calls==1,"direct picker shares leave");
            f.service.shown=false;f.click("丢弃 / 重新开始");check(field(f.service,"session")==null,"hidden restart denied");
            f.service.shown=true;f.service.info=null;f.click("丢弃 / 重新开始");check(field(f.service,"session")==null,"missing field restart denied");
            f.service.info=new android.view.inputmethod.EditorInfo();f.service.info.inputType=0x81;
            f.click("丢弃 / 重新开始");check(field(f.service,"session")==null&&!f.button("录音").isEnabled(),"sensitive restart denied");
            f.service.info.inputType=1;f.service.permission=-1;f.click("丢弃 / 重新开始");
            check(!f.button("录音").isEnabled()&&f.captures==1,"permission remains required after restart");
            f.service.permission=0;f.click("丢弃 / 重新开始");
            f.service.info.fieldId=9;f.click("录音");check(f.captures==1,"changed current field cannot record old ticket");
            android.widget.Button oldRestart=f.button("丢弃 / 重新开始");int captures=f.captures,writes=f.writes;
            f.service.onDestroy();oldRestart.performClick();
            check(field(f.service,"session")==null&&f.captures==captures&&f.writes==writes,"destroyed old listener cannot revive session");
            android.widget.LinearLayout destroyedRoot=(android.widget.LinearLayout)f.service.onCreateInputView();
            check(!findButton(destroyedRoot,"丢弃 / 重新开始").isEnabled(),"destroyed service cannot restart");
        }
        for(String label:new String[]{"设置 / 授权","模型管理"})try(ImeFixture f=new ImeFixture()) {f.click(label);f.stale();check(f.service.hides==1,"old external actions retain hide");}
        try(ImeFixture f=new ImeFixture()) {
            f.service.hideChangesShown=true;f.click("返回上一输入法");f.service.shown=true;
            f.service.onStartInputView(f.service.info,false);
            ImeSession fresh=(ImeSession)field(f.service,"session");check(fresh!=f.original&&fresh.preview().isEmpty(),"normal lifecycle fresh return");
        }
    }
    static void imeActiveReturn() throws Exception {
        for(String stage:new String[]{"capture","native","cleanup"}) {
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Thread> worker=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<RecordingControl> control=new java.util.concurrent.atomic.AtomicReference<>();
            TaskCoordinator owner=new TaskCoordinator(task->{Thread t=new Thread(()->{try{task.run();}catch(Throwable x){failure.set(x);}});worker.set(t);t.start();});
            ImeController controller=new ImeController(owner,new ImeController.Backend(){
                void block(){entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("return worker deadline");}catch(InterruptedException x){throw new AssertionError(x);}}
                public void requireModelReady(){}
                public byte[] capture(RecordingControl c,ImeController.Progress p){control.set(c);c.start(()->{});try{if(stage.equals("capture"))block();}finally{c.captureReleased();}return new byte[]{1};}
                public String transcribe(RequestContext c,byte[] w,String l){if(stage.equals("native"))block();return "late private preview";}
                public void cleanup(RequestContext c){if(stage.equals("cleanup"))block();}
            },()->{});
            AsrImeService service=new AsrImeService();set(service,"controller",controller);set(service,"alive",true);
            service.onStartInputView(service.info,false);ImeSession old=(ImeSession)field(service,"session");
            android.widget.LinearLayout root=(android.widget.LinearLayout)service.onCreateInputView();
            int[] writes={0};service.connection=(text,n)->{writes[0]++;return true;};
            List<Boolean> invalid=new ArrayList<>(),cancelled=new ArrayList<>();
            service.event=()->{invalid.add(!old.valid()&&old.preview().isEmpty());cancelled.add(control.get().cancelled());};
            try {
                check(controller.start(old,"Chinese")&&entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"actual return worker entered "+stage);
                check(findButton(root,"返回上一输入法").performClick(),"active actual return button");
                check(invalid.size()==1&&invalid.get(0),"active return invalidates before previous "+stage);
                check(cancelled.get(0)==stage.equals("capture"),"active return capture cancel/native commitment "+stage);
                check(service.hides==0&&service.previousCalls==1&&writes[0]==0,"active return no hide/auto commit");
                ImeSession fresh=controller.begin(1,0,"fresh");String status=fresh.status();
                check(owner.isBusy()&&controller.ownsTask()&&!controller.start(fresh,"Chinese")&&!owner.submit(()->{}),"actual return retains owner "+stage);
                release.countDown();worker.get().join(5000);
                check(!worker.get().isAlive()&&failure.get()==null&&!owner.isBusy()&&!controller.ownsTask(),"actual return real worker terminal "+stage);
                check(!old.valid()&&old.preview().isEmpty()&&fresh.preview().isEmpty()&&fresh.status().equals(status)&&writes[0]==0,"active return rejects late preview/commit "+stage);
            } finally {
                release.countDown();if(worker.get()!=null)worker.get().join(5000);
                check(worker.get()==null||!worker.get().isAlive(),"actual return finally joined "+stage);service.onDestroy();android.os.Handler.pending.clear();
            }
        }
    }
    static void renderIme(AsrImeService service) throws Exception {
        Method render=AsrImeService.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(service);
    }
    static String imeStatus(ImeFixture f) throws Exception {return ((android.widget.TextView)field(f.service,"status")).getText().toString();}
    static void imeModelReady() throws Exception {
        int[] nativeCalls={0};boolean[] failRelease={false};
        try(ImeFixture f=new ImeFixture(new ModelResidency.Native(){
            public double prepare(String c,String t){nativeCalls[0]++;return 0;}
            public void release()throws IOException{nativeCalls[0]++;if(failRelease[0])throw new IOException("controlled release");}
        })) {
            f.access.requestRelease();f.access.release();
            check(f.graph.readiness().snapshot().ready(),"release retains actual file proof");
            int io=f.proofIo[0],nativeBefore=nativeCalls[0],captures=f.captures,provider=android.content.ContentResolver.calls;
            renderIme(f.service);
            check(!f.button("录音").isEnabled(),"verified proof without resident must disable IME record");
            check(imeStatus(f).contains("显式准备")&&imeStatus(f).contains("文件已校验"),"EMPTY guides explicit memory prepare not SHA");
            check(f.button("确认输入").isEnabled()&&f.original.preview().equals("synthetic preview"),"release preserves manually confirmable preview");
            f.click("确认输入");check(f.writes==1&&!f.button("确认输入").isEnabled(),"released preview manual single commit");
            check(!f.button("确认输入").performClick()&&f.writes==1,"no automatic or repeat commit");
            check(f.proofIo[0]==io&&nativeCalls[0]==nativeBefore&&f.captures==captures&&android.content.ContentResolver.calls==provider,"UI render/confirm snapshots perform no proof/native/capture/provider IO");
            f.access.prepare("synthetic-config","synthetic-cache");
            f.service.onWindowShown();check(f.button("录音").isEnabled(),"explicit prepare enables through existing lifecycle refresh");
            f.access.requestRelease();renderIme(f.service);
            check(!f.button("录音").isEnabled()&&imeStatus(f).contains("释放"),"pending LOADED blocks and explains release");
            failRelease[0]=true;try{f.access.release();throw new AssertionError("release must fail");}catch(IOException expected){}
            check(f.access.residentSnapshot().getState()==ModelResidency.State.EVICTING,"controlled native failure produces EVICTING");
            renderIme(f.service);check(!f.button("录音").isEnabled()&&imeStatus(f).contains("释放"),"EVICTING fail closed");
            failRelease[0]=false;f.access.release();f.access.prepare("synthetic-config","synthetic-cache");
            set(f.graph,"modelAccess",null);renderIme(f.service);check(!f.button("录音").isEnabled(),"missing access with READY proof fails closed");
            set(f.graph,"modelAccess",f.access);
            f.graph.readiness().invalidate();renderIme(f.service);
            check(!f.button("录音").isEnabled()&&imeStatus(f).contains("校验"),"unverified proof blocks even LOADED");
            f.service.permission=1;renderIme(f.service);check(imeStatus(f).contains("权限"),"permission denial precedes proof/resident hint");
            f.service.permission=0;set(f.graph,"modelAccess",null);renderIme(f.service);check(!f.button("录音").isEnabled(),"missing access fails closed");
            set(f.service,"graph",null);renderIme(f.service);check(!f.button("录音").isEnabled(),"missing graph fails closed");
        }
        imeReadyStaleClick();imeReadyActive();imeReadyLoading();
    }
    static void imeReadyStaleClick() throws Exception {
        try(ImeFixture f=new ImeFixture()) {
            List<Runnable> jobs=new ArrayList<>();int[] captures={0};
            TaskCoordinator owner=new TaskCoordinator(jobs::add);
            ImeBackend realBackend=new ImeBackend(f.graph.context(),f.graph.modelRepository(),null,f.access);
            ImeController controller=new ImeController(owner,new ImeController.Backend(){
                public void requireModelReady()throws IOException{realBackend.requireModelReady();}
                public byte[] capture(RecordingControl c,ImeController.Progress p){captures[0]++;c.start(()->{});c.stop();c.captureReleased();return new byte[]{1};}
                public String transcribe(RequestContext c,byte[] w,String l){return "queued preview";}
                public void cleanup(RequestContext c){}
            },()->{});
            set(f.service,"controller",controller);f.service.onStartInputView(f.service.info,false);
            check(f.button("录音").isEnabled(),"rendered LOADED action initially enabled");
            f.access.requestRelease();int io=f.proofIo[0];
            try {
                f.click("录音");
                check(jobs.isEmpty()&&!owner.isBusy()&&captures[0]==0,"stale IME record action must not admit pending resident");
                check(f.proofIo[0]==io,"stale click snapshot performs no proof IO");
            } finally {while(!jobs.isEmpty())jobs.remove(0).run();owner.awaitFree();}
            f.access.release();f.access.prepare("synthetic-config","synthetic-cache");renderIme(f.service);
            try {
                f.click("录音");check(jobs.size()==1&&owner.isBusy(),"ready click submits exactly one owned worker");
                // Release after the last UI guard: the unchanged real backend remains authoritative.
                f.access.requestRelease();jobs.remove(0).run();owner.awaitFree();
                check(captures[0]==0&&!owner.isBusy()&&!controller.ownsTask(),"worker final resident gate rejects UI snapshot race before capture");
            } finally {while(!jobs.isEmpty())jobs.remove(0).run();owner.awaitFree();}
            f.access.release();f.access.prepare("synthetic-config","synthetic-cache");
            try {
                check(owner.submit(()->{}),"foreign worker admitted");renderIme(f.service);
                check(imeStatus(f).contains("等待应用或上一会话")&&!f.button("录音").isEnabled(),"foreign owner retains priority");
                f.service.info.inputType=129;renderIme(f.service);
                check(imeStatus(f).contains("敏感")&&!f.button("录音").isEnabled(),"sensitive field priority unchanged");
            } finally {while(!jobs.isEmpty())jobs.remove(0).run();owner.awaitFree();}
        }
    }
    static void imeReadyActive() throws Exception {
        try(ImeFixture f=new ImeFixture()) {
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            AtomicReference<Thread> worker=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
            TaskCoordinator owner=new TaskCoordinator(task->{Thread t=new Thread(()->{try{task.run();}catch(Throwable x){failure.set(x);}});worker.set(t);t.start();});
            ImeController controller=new ImeController(owner,new ImeController.Backend(){
                public void requireModelReady()throws IOException{f.access.requireResidentReady();}
                public byte[] capture(RecordingControl c,ImeController.Progress p){c.start(()->{});c.stop();c.captureReleased();return new byte[]{1};}
                public String transcribe(RequestContext c,byte[] w,String l)throws Exception{
                    Object token=f.access.enterRequest();boolean success=false;
                    try{entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("active deadline");success=true;return "active preview";}
                    finally{f.access.finishRequest(token,success);}
                }
                public void cleanup(RequestContext c){}
            },()->{});
            set(f.service,"controller",controller);f.service.onStartInputView(f.service.info,false);
            f.retainRoot=true;
            try {
                f.click("录音");check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"real resident ACTIVE entered");
                ImeSession session=(ImeSession)field(f.service,"session");renderIme(f.service);
                check(f.access.residentSnapshot().getState()==ModelResidency.State.ACTIVE&&imeStatus(f).equals(session.status()),"owned IME task must retain session progress while resident active");
                check(!f.button("录音").isEnabled()&&f.button("丢弃 / 重新开始").isEnabled(),"ACTIVE does not enable a second recording and keeps cancel");
            } finally {
                release.countDown();if(worker.get()!=null)worker.get().join(5000);
                f.retainRoot=worker.get()!=null&&worker.get().isAlive();
                check(!f.retainRoot,"active actual worker joined");owner.awaitFree();
                if(failure.get()!=null)throw new AssertionError("active worker throwable",failure.get());
            }
            renderIme(f.service);check(f.button("确认输入").isEnabled()&&!owner.isBusy(),"ACTIVE worker true terminal exposes preview");
        }
    }
    static void imeReadyLoading() throws Exception {
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        boolean[] block={false};AtomicReference<Throwable> failure=new AtomicReference<>();
        try(ImeFixture f=new ImeFixture(new ModelResidency.Native(){
            public double prepare(String c,String t)throws Exception{if(block[0]){entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("loading deadline");}return 0;}
            public void release(){}
        })) {
            f.access.requestRelease();f.access.release();block[0]=true;
            Thread worker=new Thread(()->{try{f.access.prepare("synthetic-config","synthetic-cache");}catch(Throwable x){failure.set(x);}});
            f.retainRoot=true;worker.start();
            try {
                check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"controlled prepare actually LOADING");renderIme(f.service);
                check(f.access.residentSnapshot().getState()==ModelResidency.State.LOADING&&!f.button("录音").isEnabled()&&imeStatus(f).contains("准备中"),"LOADING snapshot denies new recording");
            } finally {
                release.countDown();worker.join(5000);f.retainRoot=worker.isAlive();check(!f.retainRoot,"loading worker actual joined");
                if(failure.get()!=null)throw new AssertionError("loading worker throwable",failure.get());
            }
            f.service.onWindowShown();check(f.button("录音").isEnabled(),"loaded terminal refresh enables without auto capture");
        }
    }
    static void imeReturn(String mode) throws Exception {
        if(mode.equals("ime-return")||mode.equals("ime-visibility"))imeVisibility();
        if(mode.equals("ime-return"))imeActiveReturn();
        if(mode.equals("ime-return")||mode.equals("ime-fallback"))imeFallback();
        if(mode.equals("ime-return")||mode.equals("ime-invalidation"))imeInvalidation();
        if(mode.equals("ime-return")||mode.equals("ime-hide"))imeHideFailure();
        if(mode.equals("ime-return")||mode.equals("ime-restart"))imeRestart();
        if(mode.equals("ime-return"))ImeSessionTest.main(new String[0]);
    }
    static void controller(boolean notification) throws Exception {
        if (notification) ModelNotificationTest.main(new String[0]);
        else ModelManagementControllerTest.main(new String[]{"logging-rejection-import"});
    }
    static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            for (Path path : (Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }
    static final class FaultStream extends InputStream {
        final IOException primary, closing; final InputStream data; int closes;
        FaultStream(IOException p, IOException c, byte[] bytes) { primary=p;closing=c;data=new ByteArrayInputStream(bytes); }
        public int read() throws IOException { if(primary!=null)throw primary;return data.read(); }
        public int read(byte[] b,int o,int n)throws IOException {if(primary!=null)throw primary;return data.read(b,o,n);}
        public void close()throws IOException {closes++;if(closing!=null)throw closing;}
    }
    static void failureContract(Throwable actual, IOException primary, IOException closing, String label) {
        if(primary!=null && primary==closing) check(actual instanceof IllegalArgumentException && actual.getCause()==primary, label+" Java self suppression cause");
        else if(primary!=null) {check(actual==primary,label+" primary identity");check(primary.getSuppressed().length==(closing==null?0:1),label+" suppression count");if(closing!=null)check(primary.getSuppressed()[0]==closing,label+" distinct suppressed identity");}
        else check(actual==closing,label+" close-only identity");
    }
    static byte[] waveBytes()throws IOException {PcmWave pcm=new PcmWave();pcm.append(new short[PcmWave.MIN_SAMPLES],PcmWave.MIN_SAMPLES);return pcm.finish();}
    static void controllerSuppression()throws Exception {
        for(boolean runtime:new boolean[]{false,true}) for(boolean self:new boolean[]{true,false}) {
            Path dir=Files.createTempDirectory("compat-control-");List<Runnable> jobs=new ArrayList<>();TaskCoordinator owner=new TaskCoordinator(jobs::add);
            Throwable primary=runtime?new IllegalStateException("work-sentinel"):new IOException("work-sentinel");
            Throwable secondary=self?primary:(runtime?new IllegalStateException("inspect-sentinel"):new IOException("inspect-sentinel"));
            ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("a",0,String.join("",Collections.nCopies(64,"0")))));
            ModelRepository repo=new ModelRepository(manifest,dir.toFile(),()->Long.MAX_VALUE){
                public void verifyAll(Progress p,CancelGate g)throws IOException {if(primary instanceof IOException)throw(IOException)primary;throw(RuntimeException)primary;}
                public InspectReport inspect(CancelGate g)throws IOException {if(secondary instanceof IOException)throw(IOException)secondary;throw(RuntimeException)secondary;}
            };
            org.llmasr.minimal.modelmanagement.ModelOperationControl control=new org.llmasr.minimal.modelmanagement.ModelOperationControl();
            org.llmasr.minimal.modelmanagement.ModelManagementState state=new org.llmasr.minimal.modelmanagement.ModelManagementState();
            ModelReadiness ready=new ModelReadiness();int[] notifications={0};ModelReadiness.Listener listener=()->notifications[0]++;ready.addListener(listener);
            org.llmasr.minimal.modelmanagement.ModelManagementController c=new org.llmasr.minimal.modelmanagement.ModelManagementController(owner,repo,ready,control,state);
            AtomicReference<Throwable> seen=new AtomicReference<>();set(field(c,"runner"),"lifecycle",new RequestRunner.Lifecycle(){public void failed(RequestContext ctx,Throwable t){seen.set(t);}});
            try {
                check(c.startVerify()&&owner.isBusy()&&jobs.size()==1,"controller queued owned work");jobs.remove(0).run();
                if(self) check(seen.get() instanceof IllegalArgumentException && seen.get().getCause()==primary,"Controller Java self suppression cause");
                else check(seen.get()==null && primary.getSuppressed().length==1 && primary.getSuppressed()[0]==secondary,"Controller distinct suppression identity");
                check(notifications[0]==(self?1:2),"Controller self skips normal terminal readiness notification");
                check(!owner.isBusy()&&!control.isBusy()&&!state.current().pageOwnerHeld&&jobs.isEmpty(),"Controller finally releases actual owners");
            } finally {ready.removeListener(listener);check(!owner.isBusy()&&jobs.isEmpty(),"controller actual worker exit before deletion");delete(dir);}
        }
    }
    static void graphSuppression()throws Exception {
        for(int mode=0;mode<3;mode++) {
            IOException primary=mode==2?null:new IOException("read-sentinel"),closing=mode==0?primary:new IOException("close-sentinel");
            FaultStream input=new FaultStream(primary,closing,new byte[0]);Path dir=Files.createTempDirectory("compat-parser-");
            android.content.res.AssetManager.stream=input;Throwable seen=null;
            try {try{AppGraph.install(new android.content.Context(dir.toFile()));}catch(Throwable t){seen=t;}
                failureContract(seen,primary,closing,"Graph");check(input.closes==1,"Graph stream close once");
                Throwable absent=null;try{AppGraph.get();}catch(Throwable t){absent=t;}check(absent instanceof IllegalStateException,"failed parser never publishes Graph");
            } finally {android.content.res.AssetManager.stream=null;delete(dir);}
        }
    }
    static void operationSuppression(boolean wave)throws Exception {
        for(int mode=0;mode<4;mode++) {
            IOException primary=mode<2?new IOException("read-sentinel"):null,closing=mode==0?primary:mode==3?null:new IOException("close-sentinel");
            FaultStream input=new FaultStream(primary,closing,waveBytes());Path dir=Files.createTempDirectory("compat-stream-");List<Runnable> jobs=new ArrayList<>();TaskCoordinator owner=new TaskCoordinator(jobs::add);
            OperationContext ctx=new OperationContext(){public File filesDir(){return dir.toFile();}public File cacheDir(){return dir.toFile();}public InputStream openAsset(String n){return input;}public android.content.ContentResolver contentResolver(){return new android.content.ContentResolver();}};
            ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("a",0,String.join("",Collections.nCopies(64,"0")))));ModelRepository repo=ModelRepository.forWorker(manifest,dir.resolve("model").toFile(),()->Long.MAX_VALUE);
            AppReportWriter reports=new AppReportWriter(ctx);InferenceAdapter infer=new InferenceAdapter(null,()->{},()->{},(c,w,l,t,listener)->"1 2 3\n<asr_text>compat".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            AsrOperation op=new AsrOperation(ctx,owner,repo,new AppState(),new TextExportController<android.net.Uri>(u->new ByteArrayOutputStream(),Runnable::run),infer,reports,preparedAccess(dir));
            android.content.ContentResolver.stream=input;
            try {check(wave?op.startWave(new android.net.Uri(),null):op.startSample(null),"stream request admitted");check(input.closes==0&&owner.isBusy(),"stream IO deferred to worker");jobs.remove(0).run();
                failureContract(reports.cause,primary,closing,wave?"Wave":"Sample");check(input.closes==1,"Operation stream close once");
                check(!owner.isBusy()&&jobs.isEmpty(),"Operation finalizer releases owner");check(reports.lastContext!=null&&reports.lastContext.inputWav!=null&&!reports.lastContext.inputWav.exists(),"Operation actual WAV cleanup");
                check(reports.failures==(mode==3?0:1),"Operation report failure count");
            } finally {android.content.ContentResolver.stream=null;check(!owner.isBusy()&&jobs.isEmpty(),"operation exit before deletion");delete(dir);}
        }
    }
    static void receiverReset(){android.content.ContentResolver.fail=true;android.content.ContentResolver.calls=0;android.content.ContentResolver.seen=new android.net.Uri();android.content.ContentResolver.order="";}
    static void graphNullable(boolean text)throws Exception {
        Path dir=Files.createTempDirectory("compat-backend-");AppGraph actual=null;
        try {AppGraph.install(new android.content.Context(dir.toFile()));actual=AppGraph.get();actual.coordinator().awaitFree();
            Object backend=field(text?actual.textExport():actual.logExports(),"backend");receiverReset();Throwable seen=null;
            try{if(text)((TextExportController.Backend<android.net.Uri>)backend).open(null);else((org.llmasr.minimal.diagnostics.LogExportController.Backend<android.net.Uri>)backend).open(null);}catch(Throwable t){seen=t;}
            check(android.content.ContentResolver.calls==1&&android.content.ContentResolver.seen==null,text?"Graph text null must reach receiver":"Graph log null must reach receiver");
            check(seen==android.content.ContentResolver.SENTINEL&&android.content.ContentResolver.order.equals("receiver;open;"),"Graph platform sentinel/receiver order");
            android.net.Uri target=new android.net.Uri();receiverReset();seen=null;
            try{if(text)((TextExportController.Backend<android.net.Uri>)backend).open(target);else((org.llmasr.minimal.diagnostics.LogExportController.Backend<android.net.Uri>)backend).open(target);}catch(Throwable t){seen=t;}
            check(android.content.ContentResolver.calls==1&&android.content.ContentResolver.seen==target&&seen==android.content.ContentResolver.SENTINEL&&android.content.ContentResolver.order.equals("receiver;open;"),"Graph nonnull identity/order");
        } finally {android.content.ContentResolver.fail=false;if(actual!=null){actual.coordinator().awaitFree();actual.runtimeLogWorker().close();java.util.concurrent.ExecutorService ex=(java.util.concurrent.ExecutorService)field(actual.runtimeLogWorker(),"executor");ex.shutdown();check(ex.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS),"compat graph worker actual exit");}Field singleton=AppGraph.class.getDeclaredField("INSTANCE");singleton.setAccessible(true);singleton.set(null,null);delete(dir);}
    }
    static void waveNullable()throws Exception {
        Path dir=Files.createTempDirectory("compat-null-wave-");List<Runnable> jobs=new ArrayList<>();TaskCoordinator owner=new TaskCoordinator(jobs::add);
        OperationContext ctx=new OperationContext(){public File filesDir(){return dir.toFile();}public File cacheDir(){return dir.toFile();}public InputStream openAsset(String n){return null;}public android.content.ContentResolver contentResolver(){android.content.ContentResolver.order+="receiver;";return new android.content.ContentResolver();}};
        AppReportWriter reports=new AppReportWriter(ctx);AsrOperation op=new AsrOperation(ctx,owner,null,new AppState(),new TextExportController<android.net.Uri>(u->null,Runnable::run),null,reports,preparedAccess(dir));
        try {receiverReset();check(op.startWave(null,null)&&owner.isBusy()&&android.content.ContentResolver.calls==0,"nullable wave no IO before queued worker");jobs.remove(0).run();
            check(android.content.ContentResolver.calls==1&&android.content.ContentResolver.seen==null,"Wave null must reach receiver");check(reports.cause==android.content.ContentResolver.SENTINEL&&android.content.ContentResolver.order.equals("receiver;open;"),"Wave platform sentinel/receiver order");check(!owner.isBusy()&&jobs.isEmpty(),"nullable wave actual owner finalized");
        } finally {android.content.ContentResolver.fail=false;check(!owner.isBusy()&&jobs.isEmpty(),"nullable worker exit before deletion");delete(dir);}
    }
    static void windowNullable()throws Exception {
        Method unregister=Class.forName("org.llmasr.minimal.platform.ActivityWindow$Api33").getDeclaredMethod("unregister",android.app.Activity.class,Object.class);unregister.setAccessible(true);
        int[] receivers={0};android.app.Activity activity=new android.app.Activity(){public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher(){receivers[0]++;return new android.window.OnBackInvokedDispatcher();}};
        android.window.OnBackInvokedDispatcher.calls=0;Throwable seen=null;
        try{unregister.invoke(null,activity,null);}catch(InvocationTargetException t){seen=t.getCause();}
        check(android.window.OnBackInvokedDispatcher.calls==1&&android.window.OnBackInvokedDispatcher.seen==null,"Window null must reach receiver");check(seen==android.window.OnBackInvokedDispatcher.SENTINEL&&receivers[0]==1,"Window sentinel/receiver before callback");
        android.window.OnBackInvokedDispatcher.calls=0;seen=null;try{unregister.invoke(null,activity,new Object());}catch(InvocationTargetException t){seen=t.getCause();}
        check(seen instanceof ClassCastException&&android.window.OnBackInvokedDispatcher.calls==0&&receivers[0]==2,"Window wrongtype casts after receiver before unregister");
        android.window.OnBackInvokedCallback callback=()->{};seen=null;
        try{unregister.invoke(null,activity,callback);}catch(InvocationTargetException t){seen=t.getCause();}
        check(android.window.OnBackInvokedDispatcher.calls==1&&android.window.OnBackInvokedDispatcher.seen==callback&&receivers[0]==3&&seen==android.window.OnBackInvokedDispatcher.SENTINEL,"Window nonnull identity/order");
    }
    static void compat(String mode)throws Exception {
        if(mode.equals("compat-all")||mode.equals("compat-controller"))controllerSuppression();
        if(mode.equals("compat-all")||mode.equals("compat-parser"))graphSuppression();
        if(mode.equals("compat-all")||mode.equals("compat-sample"))operationSuppression(false);
        if(mode.equals("compat-all")||mode.equals("compat-wave"))operationSuppression(true);
        if(mode.equals("compat-all")||mode.equals("compat-graph-log"))graphNullable(false);
        if(mode.equals("compat-all")||mode.equals("compat-graph-text"))graphNullable(true);
        if(mode.equals("compat-all")||mode.equals("compat-wave-null"))waveNullable();
        if(mode.equals("compat-all")||mode.equals("compat-window-null"))windowNullable();
    }
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "all" : args[0];
        if (mode.equals("ime-model-ready")) imeModelReady();
        else if (mode.startsWith("ime-")) imeReturn(mode);
        if (mode.startsWith("compat-")) compat(mode);
        if (mode.equals("all") || mode.equals("fallback")) controller(false);
        if (mode.equals("all") || mode.equals("notification")) controller(true);
        if (mode.equals("all") || mode.equals("graph")) graph();
        if (mode.equals("all") || mode.equals("operation")) operation();
        if (mode.equals("all") || mode.equals("preinput")) preInputAdmission();
        if (mode.equals("all") || mode.equals("resident-owner")) residentOwnerRelease();
        if (mode.equals("all") || mode.equals("service")) service();
        System.out.println("PASS next real app/controller host contracts " + mode + " " + checks + " checks (NOT Android runtime)");
    }
}
