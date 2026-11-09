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
    static void variantSelectedPaths() throws Exception {
        Path root=Files.createTempDirectory("variant-selected-path-");List<Runnable> jobs=new ArrayList<>();List<String> nativePaths=new ArrayList<>();
        try {
            Path files=Files.createDirectory(root.resolve("files")),cache=Files.createDirectory(root.resolve("cache"));
            List<ModelEntry> entries=new ArrayList<>();String hash=ModelPackagePolicy.sha(new byte[]{1,2,3});
            for(int i=0;i<7;i++)entries.add(new ModelEntry("f"+i,3,hash));
            FixedModelPackage fixed=new FixedModelPackage(new ModelManifest(entries),"selected-fixture","mnn",1048576);
            final Map<String,Map<String,byte[]>> journals=new HashMap<>();final byte[][] metadata={null};
            ModelCatalog.Io io=new ModelCatalog.Io(){byte[] recovery;public byte[]readRecovery(){return recovery;}public void writeRecovery(byte[]b){recovery=b.clone();}public void clearRecovery(){recovery=null;}public byte[]readCatalog(){return metadata[0];}public void writeCatalog(byte[]b){metadata[0]=b.clone();}public ModelVerification.Io proof(String key){return new ModelVerification.Io(){public boolean exists(File f){return f.exists();}public String identity(File f,boolean d)throws IOException{return new TreeMap<>(Files.readAttributes(f.toPath(),"unix:dev,ino,mode,size,lastModifiedTime,ctime",LinkOption.NOFOLLOW_LINKS)).toString();}public byte[]read(String n,int l){return journals.getOrDefault(key,Collections.emptyMap()).get(n);}public void write(String n,byte[]b){journals.computeIfAbsent(key,k->new HashMap<>()).put(n,b.clone());}};}};
            ModelResidency resident=new ModelResidency(new ModelResidency.Native(){public double prepare(String c,String t){nativePaths.add("prepare:"+c);return 0;}public void release(){nativePaths.add("release");}},()->true);
            ModelReadiness projected=new ModelReadiness();ModelCatalog catalog=new ModelCatalog(fixed,files.toFile(),cache.toFile(),()->Long.MAX_VALUE,resident,io,projected);catalog.load();
            ModelDeployment model=catalog.selected();Files.createDirectory(files.resolve("model"));for(int i=0;i<7;i++)Files.write(files.resolve("model/f"+i),new byte[]{1,2,3});
            ModelVerification proof=model.repository.verification();ModelVerification.Token token=proof.begin(model.repository);model.repository.verifyAll();check(proof.commit(model.repository,token,new ModelVerification.CommitGate(){public boolean reserve(ModelReadiness.VerifyToken t){return model.readiness.accepts(t);}public boolean succeed(ModelReadiness.VerifyToken t){return model.readiness.commitVerified(t);}}),"selected actual SHA proof");
            ModelAccess access=ModelAccess.forCatalog(catalog,resident);TaskCoordinator owner=new TaskCoordinator(jobs::add);
            PcmWave pcm=new PcmWave();pcm.append(new short[PcmWave.MIN_SAMPLES],PcmWave.MIN_SAMPLES);byte[] wav=pcm.finish();
            OperationContext context=new OperationContext(){public File filesDir(){return files.toFile();}public File cacheDir(){return cache.toFile();}public InputStream openAsset(String n){return new ByteArrayInputStream(wav);}public android.content.ContentResolver contentResolver(){return new android.content.ContentResolver();}};
            InferenceAdapter inference=new InferenceAdapter(null,access::requireResidentReady,()->{},(c,w,l,t,p)->{check(c.equals(model.configPath()),"AppIME native path must match selected snapshot");nativePaths.add("infer:"+c);p.onPhase(3);p.onPhase(4);return "0 1 2\npublic".getBytes(java.nio.charset.StandardCharsets.UTF_8);},new InferenceAdapter.Session(){public Object enter()throws IOException{return access.enterRequest();}public void finish(Object t,boolean ok)throws Exception{access.finishRequest(t,ok);}});
            ModelManifest wrongManifest=new ModelManifest(Arrays.asList(new ModelEntry("a",0,hash)));ModelRepository wrong=ModelRepository.forWorker(wrongManifest,root.resolve("WRONG-LEGACY-CONSTRUCTOR").toFile(),()->Long.MAX_VALUE);
            AsrOperation operation=new AsrOperation(context,owner,wrong,new AppState(),new TextExportController<android.net.Uri>(u->null,Runnable::run),inference,new AppReportWriter(context),access);
            check(operation.startPrepare(),"selected prepare admitted");jobs.remove(0).run();check(operation.startSample("auto"),"selected App request admitted");jobs.remove(0).run();
            ImeBackend backend=new ImeBackend(context,wrong,inference,access);RecordingControl control=new RecordingControl();
            check(owner.submit(()->{backend.prepare(control,s->{});backend.requireModelReady();RequestContext request=new RequestContext("variant-ime",TaskKind.INFERENCE);try{check(backend.transcribe(request,wav,"auto").equals("public"),"IME selected result");}finally{backend.cleanup(request);}}),"selected IME request admitted");jobs.remove(0).run();
            check(nativePaths.stream().filter(s->s.equals("infer:"+model.configPath())).count()==2,"both real App and IME infer selected path");
            check(nativePaths.stream().filter(s->s.startsWith("prepare:")).count()==1,"same selected generation reused single native resident");
        } finally {check(jobs.isEmpty(),"variant request workers exited");delete(root);}
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
    /** First-use transaction through real Service/Controller/Backend/ModelAccess/proof/residency. */
    static final class FirstUse implements AutoCloseable {
        final Path root=Files.createTempDirectory("ime-first-use-");
        final ModelReadiness ready=new ModelReadiness();
        final int[] loads={0},unloads={0},reads={0},writes={0},hashUpdates={0},captures={0};
        final List<String> stages=new ArrayList<>();
        final AtomicReference<Throwable> workerFailure=new AtomicReference<>();
        final List<Runnable> jobs=new ArrayList<>();
        final TaskCoordinator owner=new TaskCoordinator(jobs::add);
        final ModelResidency resident;
        final ModelRepository repo;
        final ModelAccess access;
        final AsrImeService service=new AsrImeService();
        final ImeController controller;
        final android.widget.LinearLayout view;
        final OperationContext context;
        Runnable onLoad=()->{},onRead=()->{},onWrite=()->{},onStage=()->{};
        boolean failWrite;
        Thread worker;
        boolean retained;
        FirstUse()throws Exception {
            Files.createDirectories(root.resolve("models"));Files.write(root.resolve("models/config.json"),new byte[]{1,2,3});
            resident=new ModelResidency(new ModelResidency.Native(){
                public double prepare(String c,String t){loads[0]++;onLoad.run();return 0;}
                public void release(){unloads[0]++;}
            },()->true);
            AndroidModelVerificationIo disk=new AndroidModelVerificationIo(()->root.toFile());
            ModelVerification.Io io=new ModelVerification.Io(){
                public String identity(File f,boolean d)throws IOException{return disk.identity(f,d);}
                public boolean exists(File f)throws IOException{return disk.exists(f);}
                public byte[] read(String n,int limit)throws IOException{reads[0]++;onRead.run();return disk.read(n,limit);}
                public void write(String n,byte[] b)throws IOException{writes[0]++;onWrite.run();if(failWrite&&n.equals("receipt"))throw new IOException("controlled proof disk failure");disk.write(n,b);}
            };
            ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("config.json",3,"039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")));
            ModelVerification proof=new ModelVerification(manifest,ready,io,resident);
            repo=new ModelRepository(manifest,root.resolve("models").toFile(),()->Long.MAX_VALUE,proof);
            access=new ModelAccess(repo,ready,resident);
            context=new OperationContext(){public File filesDir(){return root.toFile();}public File cacheDir(){return root.toFile();}
                public InputStream openAsset(String n){throw new AssertionError("no asset IO");}
                public android.content.ContentResolver contentResolver(){throw new AssertionError("no provider IO");}};
            ImeBackend real=new ImeBackend(context,repo,null,access);
            controller=new ImeController(owner,new ImeController.Backend(){
                public void requireModelReady()throws IOException{real.requireModelReady();}
                public void prepare(RecordingControl c,ImeController.Preparation p)throws Exception {
                    real.prepare(c,s->{stages.add(s);if(s.contains("SHA-256："))hashUpdates[0]++;p.update(s);onStage.run();});
                }
                public byte[] capture(RecordingControl c,ImeController.Progress p)throws IOException{captures[0]++;return real.capture(c,p);}
                public byte[] captureWithStart(RecordingControl c,ImeController.Progress p,RecordingControl.StartGate gate)throws IOException{captures[0]++;return real.captureWithStart(c,p,gate);}
                public String transcribe(RequestContext c,byte[] w,String l)throws Exception{
                    Object token=access.enterRequest();try{return "manual preview";}finally{access.finishRequest(token,true);}
                }
                public void cleanup(RequestContext c)throws IOException{real.cleanup(c);}
            },()->{});
            AppGraph.install(new android.content.Context(root.toFile()));AppGraph graph=AppGraph.get();graph.coordinator().awaitFree();
            set(graph,"modelAccess",access);set(graph,"readiness",ready);set(service,"graph",graph);set(service,"controller",controller);set(service,"alive",true);
            service.onStartInputView(service.info,false);view=(android.widget.LinearLayout)service.onCreateInputView();
        }
        android.widget.Button button(String label){return findButton(view,label);}
        void click()throws Exception{check(button("录音").performClick(),"record click enabled");}
        void run()throws Exception{check(jobs.size()==1,"exactly one owned transaction");jobs.remove(0).run();check(!owner.isBusy(),"owner released after real work");}
        void threaded()throws Exception{
            Runnable task=jobs.remove(0);worker=new Thread(()->{try{task.run();}catch(Throwable t){workerFailure.set(t);}},"first-use-test");worker.start();
        }
        void join()throws Exception{if(worker!=null){worker.join(5000);retained=worker.isAlive();check(!retained,"actual preparation worker joined");}owner.awaitFree();if(workerFailure.get()!=null)throw new AssertionError("worker throwable",workerFailure.get());}
        public void close()throws Exception{
            service.onDestroy();while(!jobs.isEmpty())jobs.remove(0).run();join();
            android.os.Handler.pending.clear();AppGraph graph=AppGraph.get();graph.coordinator().awaitFree();graph.runtimeLogWorker().close();
            java.util.concurrent.ExecutorService executor=(java.util.concurrent.ExecutorService)field(graph.runtimeLogWorker(),"executor");executor.shutdown();
            check(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS),"first-use log worker exited");
            Field singleton=AppGraph.class.getDeclaredField("INSTANCE");singleton.setAccessible(true);singleton.set(null,null);
            check(!retained,"retain live input tree");delete(root);
        }
    }
    static void imeModelReady()throws Exception{
        try(FirstUse f=new FirstUse()) {
            check(f.button("录音").isEnabled(),"first-use record must be enabled without proof or resident");
            check(f.reads[0]==0&&f.loads[0]==0&&f.hashUpdates[0]==0,"render performs no proof/hash/native IO");
            f.click();check(f.controller.phase()==ImeController.Phase.PREPARING&&!f.button("停止").isEnabled(),"preparing is not capturing");
            check(f.owner.isBusy()&&!f.controller.start((ImeSession)field(f.service,"session"),"Chinese"),"double click shares single owner");
            check(!f.owner.submit(()->{throw new AssertionError("foreign app overlap");}),"App and IME mutually exclusive during prepare");
            f.run();check(f.ready.isReady()&&f.hashUpdates[0]>0&&f.writes[0]>=3&&f.loads[0]==1&&f.captures[0]==1,"automatic SHA durable proof then prepare then capture");
            check(((ImeSession)field(f.service,"session")).preview().equals("manual preview"),"preview remains manual");
            int reads=f.reads[0],writes=f.writes[0],hash=f.hashUpdates[0];
            renderIme(f.service);f.click();f.run();
            check(f.loads[0]==1&&f.unloads[0]==0&&f.reads[0]==reads&&f.writes[0]==writes&&f.hashUpdates[0]==hash,"valid current proof reuses resident without restore or SHA");
            f.access.requestRelease();f.access.release();renderIme(f.service);f.click();f.run();
            check(f.loads[0]==2&&f.unloads[0]==1&&f.hashUpdates[0]==hash,"released model re-prepares without SHA");
            // New authority objects restore the on-disk proof; real separate JVM is below.
            ModelReadiness next=new ModelReadiness();ModelVerification proof=new ModelVerification(f.repo.manifest(),next,new AndroidModelVerificationIo(()->f.root.toFile()),f.resident);
            ModelRepository nextRepo=new ModelRepository(f.repo.manifest(),f.repo.modelDir(),()->Long.MAX_VALUE,proof);
            check(new ModelAccess(nextRepo,next,f.resident).restoreFast()&&next.isReady(),"durable proof restores new authority");
            Process child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("java.class.path"),
                "NextAppContractTest","first-use-proof-child",f.root.toString()).inheritIO().start();
            try{check(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)&&child.exitValue()==0,"separate JVM prepare restores without SHA");}
            finally{if(child.isAlive()){child.destroyForcibly();check(child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS),"proof child actual exit");}}
        }
        for(String damage:Arrays.asList("missing","hash"))try(FirstUse f=new FirstUse()) {
            if(damage.equals("missing"))Files.delete(f.root.resolve("models/config.json"));else Files.write(f.root.resolve("models/config.json"),new byte[]{3,2,1});
            f.click();f.run();check(f.captures[0]==0&&f.loads[0]==0&&!f.ready.isReady(),"bad or missing model capture zero");
            check(((ImeSession)field(f.service,"session")).status().contains("导入 / 修复"),"missing model repair guidance");
        }
        for(String stage:Arrays.asList("恢复","SHA-256：","加载 / 复用"))try(FirstUse f=new FirstUse()) {
            f.onStage=()->{if(!f.stages.isEmpty()&&f.stages.get(f.stages.size()-1).contains(stage))f.controller.invalidate();};
            f.click();f.run();check(f.captures[0]==0&&f.loads[0]==0,"cancel at prepare stage never captures or loads");
            check(f.ready.isReady()==stage.equals("加载 / 复用"),"only completed proof survives preparation cancellation");
        }
        // Cancellation can lose durable-proof publication but can never authorize the microphone.
        try(FirstUse f=new FirstUse()) {
            f.onWrite=()->{if(f.writes[0]==3)f.controller.invalidate();};f.click();f.run();
            check(f.captures[0]==0&&f.loads[0]==0&&f.ready.isReady(),"commit winner may retain valid proof but cancelled intent never captures");
        }
        try(FirstUse f=new FirstUse()) {
            f.failWrite=true;f.click();f.run();
            check(f.captures[0]==0&&f.loads[0]==0&&!f.ready.isReady(),"proof IO failure never captures");
            f.failWrite=false;
            boolean restored=false;try{restored=new ModelAccess(f.repo,f.ready,f.resident).restoreFast();}catch(IOException expected){}
            check(!restored,"failed proof publication leaves dirty journal");
            renderIme(f.service);f.click();f.run();check(f.captures[0]==1&&f.ready.isReady(),"proof IO failure retry safely prepares");
        }
        try(FirstUse f=new FirstUse()) {
            f.onLoad=f.controller::stop;f.click();f.run();
            check(f.captures[0]==1&&((ImeSession)field(f.service,"session")).preview().equals("manual preview"),"stop during PREPARING cannot pre-stop future recording");
        }
        imePrepareRestoreCancel();imePrepareNativeCancel();imePrepareIntent();imeReadyActive();
        ImeSessionTest.main(new String[0]);
    }
    static void imePrepareRestoreCancel()throws Exception{
        try(FirstUse f=new FirstUse()){
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            f.onRead=()->{entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("restore IO deadline");}catch(InterruptedException e){throw new AssertionError(e);}};
            f.click();f.threaded();
            try{
                check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"restore actual IO entered");f.service.onWindowHidden();
                check(f.owner.isBusy()&&f.captures[0]==0&&!f.owner.submit(()->{}),"restore cancellation retains owner during IO");
            }finally{release.countDown();f.join();}
            check(f.captures[0]==0&&f.loads[0]==0&&!f.owner.isBusy(),"restore IO actual return before cancelled owner release");
        }
    }
    static void imePrepareNativeCancel()throws Exception{
        for(String cancel:Arrays.asList("cancel","hidden","field"))try(FirstUse f=new FirstUse()){
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            f.onLoad=()->{entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("load deadline");}catch(InterruptedException e){throw new AssertionError(e);}};
            f.click();f.threaded();
            try{
                check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"native prepare entered");renderIme(f.service);
                check(f.controller.phase()==ImeController.Phase.PREPARING&&!f.button("停止").isEnabled(),"native load remains PREPARING");
                f.controller.stop();
                if(cancel.equals("hidden"))f.service.onWindowHidden();else if(cancel.equals("field")){f.service.info.fieldId++;f.service.onStartInput(f.service.info,false);}else f.controller.invalidate();
                check(f.owner.isBusy()&&f.controller.ownsTask()&&f.captures[0]==0&&!f.owner.submit(()->{}),"cancel retains owner until actual native return");
            }finally{release.countDown();f.join();}
            check(f.captures[0]==0&&!f.owner.isBusy()&&!f.controller.ownsTask(),"cancelled native preparation cannot open microphone late");
        }
    }
    static void imePrepareIntent()throws Exception{
        for(String change:Arrays.asList("permission","shown","field","sensitive"))try(FirstUse f=new FirstUse()){
            f.onLoad=()->{if(change.equals("permission"))f.service.permission=1;else if(change.equals("shown"))f.service.shown=false;else if(change.equals("field"))f.service.info.fieldId++;else f.service.info.inputType=129;};
            f.click();f.run();
            check(f.captures[0]==0,change.equals("permission")?"permission change during prepare must prevent capture":"editor visibility or field change prevents capture");
        }
        // Exercise the worker->main Handler handshake (not an Android framework test).
        try(FirstUse f=new FirstUse()){
            f.click();android.os.Handler.posted.drainPermits();f.threaded();
            try{
                check(android.os.Handler.posted.tryAcquire(5,java.util.concurrent.TimeUnit.SECONDS),"final intent posted to main");
                check(f.captures[0]==0&&f.owner.isBusy(),"worker waits for actual main intent gate");
                f.service.permission=1;android.os.Handler.drain();
            }finally{android.os.Handler.drain();f.join();}
            check(f.captures[0]==0,"main thread permission gate refuses late microphone");
        }
    }
    static void imeReadyActive() throws Exception {
        try(FirstUse f=new FirstUse()){
            // External assertion after the callback: production listeners may swallow failures.
            String[] shown={null},expected={null};
            f.onLoad=()->{try{renderIme(f.service);shown[0]=imeStatusText(f.service);expected[0]=((ImeSession)field(f.service,"session")).status();}catch(Exception e){throw new AssertionError(e);}};
            f.click();f.run();
            check(shown[0]!=null&&shown[0].equals(expected[0]),"owned IME task must retain session progress while resident active");
        }
    }
    static String imeStatusText(AsrImeService service)throws Exception{return ((android.widget.TextView)field(service,"status")).getText().toString();}
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
    static void startBoundary(String mode)throws Exception{
        try(FirstUse f=new FirstUse()){
            org.llmasr.minimal.audio.ForegroundRecorder.starts=0;
            org.llmasr.minimal.audio.ForegroundRecorder.beforeStart=()->{
                if(mode.equals("start-intent")){f.service.shown=false;f.service.permission=-1;}
                else f.access.requestRelease();
            };
            try{f.click();f.run();
                check(org.llmasr.minimal.audio.ForegroundRecorder.starts==0,
                    mode.equals("start-intent")?"R1 post-gate invalidation must reject actual microphone start":"R2 pending release must reject actual microphone start");
            }finally{org.llmasr.minimal.audio.ForegroundRecorder.beforeStart=()->{};}
        }
    }
    static void startProtocol()throws Exception{
        try(FirstUse f=new FirstUse()){
            // Obtain the actual Service gate; no copy of production dispatch/arbitration logic.
            Class<?> type=Class.forName("org.llmasr.minimal.AsrImeService$CaptureGate");
            java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
            ImeController.CaptureIntent intent=(ImeController.CaptureIntent)ctor.newInstance(f.service,field(f.service,"session"));
            for(String mode:Arrays.asList("queued-cancel","queued-timeout","throw","running-interrupt")){
                android.os.Handler.pending.clear();android.os.Handler.posted.drainPermits();
                java.util.concurrent.atomic.AtomicInteger starts=new java.util.concurrent.atomic.AtomicInteger();
                AtomicReference<Throwable> failure=new AtomicReference<>();boolean[] result={true},interrupted={false};
                java.util.concurrent.CountDownLatch hardware=new java.util.concurrent.CountDownLatch(1),finish=new java.util.concurrent.CountDownLatch(1);
                RuntimeException sentinel=new RuntimeException("hardware start sentinel");
                Thread worker=new Thread(()->{try{result[0]=intent.start(()->{starts.incrementAndGet();hardware.countDown();if(mode.equals("throw"))throw sentinel;if(mode.equals("running-interrupt"))try{if(!finish.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("hardware deadline");}catch(InterruptedException e){throw new AssertionError(e);}});}catch(Throwable t){failure.set(t);}finally{interrupted[0]=Thread.currentThread().isInterrupted();}});
                Thread dispatch=null;worker.start();
                try{
                    check(android.os.Handler.posted.tryAcquire(5,java.util.concurrent.TimeUnit.SECONDS),"actual start posted");
                    if(mode.equals("queued-cancel")){worker.interrupt();worker.join(1000);android.os.Handler.drain();}
                    else if(mode.equals("queued-timeout")){worker.join(6500);android.os.Handler.drain();}
                    else if(mode.equals("throw")){android.os.Handler.drain();worker.join(1000);}
                    else{
                        dispatch=new Thread(android.os.Handler::drain);dispatch.start();
                        check(hardware.await(5,java.util.concurrent.TimeUnit.SECONDS),"hardware callback actually running");worker.interrupt();
                        worker.join(50);check(worker.isAlive(),"running start interrupt must retain worker until actual return");finish.countDown();dispatch.join(1000);worker.join(1000);
                    }
                    check(!worker.isAlive(),"start protocol worker bounded actual return");
                    if(mode.startsWith("queued"))check(starts.get()==0&&!result[0]&&failure.get()==null,"abandoned queued start must never run late");
                    if(mode.equals("queued-cancel"))check(interrupted[0],"queued interrupt preserved");
                    if(mode.equals("throw"))check(failure.get()==sentinel&&starts.get()==1,"actual start Throwable identity reaches worker");
                    if(mode.equals("running-interrupt"))check(result[0]&&interrupted[0]&&starts.get()==1,"running start finishes once and restores interrupt");
                }finally{finish.countDown();worker.interrupt();worker.join(7000);if(dispatch!=null)dispatch.join(1000);check(!worker.isAlive()&&(dispatch==null||!dispatch.isAlive()),"protocol threads really joined");android.os.Handler.pending.clear();}
            }
            f.click();f.run();String generation=f.access.readyGeneration();
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),done=new java.util.concurrent.CountDownLatch(1),releaseDone=new java.util.concurrent.CountDownLatch(1);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread starter=new Thread(()->{try{f.access.startCapture(generation,()->{entered.countDown();try{if(!done.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("resident hardware deadline");}catch(InterruptedException e){throw new AssertionError(e);}return true;});}catch(Throwable t){failure.set(t);}});
            Thread releaser=new Thread(()->{f.access.requestRelease();releaseDone.countDown();});starter.start();
            try{check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"resident start entered");releaser.start();check(!releaseDone.await(50,java.util.concurrent.TimeUnit.MILLISECONDS),"release cannot overtake running actual start");}
            finally{done.countDown();starter.join(1000);releaser.join(1000);check(!starter.isAlive()&&!releaser.isAlive()&&failure.get()==null,"resident start/release actual threads joined");}
            check(f.access.residentSnapshot().getPendingRelease(),"start winner precedes release pending");
        }
    }
    static void firstUseProofChild(Path root)throws Exception{
        ModelManifest manifest=new ModelManifest(Arrays.asList(new ModelEntry("config.json",3,"039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")));
        ModelReadiness ready=new ModelReadiness();int[] loads={0},hash={0};
        ModelResidency resident=new ModelResidency(new ModelResidency.Native(){public double prepare(String c,String t){loads[0]++;return 0;}public void release(){}},()->true);
        ModelVerification proof=new ModelVerification(manifest,ready,new AndroidModelVerificationIo(()->root.toFile()),resident);
        ModelRepository repo=new ModelRepository(manifest,root.resolve("models").toFile(),()->Long.MAX_VALUE,proof);
        new ModelAccess(repo,ready,resident).prepareForCapture(new ModelAccess.PreparationControl(){
            public boolean cancelled(){return false;}
            public boolean reserve(ModelReadiness r,ModelReadiness.VerifyToken t){throw new AssertionError("no SHA reservation on restored proof");}
            public boolean succeed(ModelReadiness r,ModelReadiness.VerifyToken t){throw new AssertionError("no full proof commit on restore");}
        },"config","cache",s->{if(s.contains("SHA-256"))hash[0]++;return kotlin.Unit.INSTANCE;});
        check(ready.isReady()&&loads[0]==1&&hash[0]==0,"cross-process helper restores valid proof without SHA");
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0&&args[0].equals("first-use-proof-child")){firstUseProofChild(Paths.get(args[1]));return;}
        String mode = args.length == 0 ? "all" : args[0];
        if (mode.equals("start-protocol")) startProtocol();
        else if (mode.startsWith("start-")) startBoundary(mode);
        if (mode.equals("ime-model-ready")) imeModelReady();
        else if (mode.startsWith("ime-")) imeReturn(mode);
        if (mode.startsWith("compat-")) compat(mode);
        if (mode.equals("all") || mode.equals("fallback")) controller(false);
        if (mode.equals("all") || mode.equals("notification")) controller(true);
        if (mode.equals("all") || mode.equals("graph")) graph();
        if (mode.equals("all") || mode.equals("operation")) operation();
        if (mode.equals("all") || mode.equals("preinput")) preInputAdmission();
        if (mode.equals("all") || mode.equals("resident-owner")) residentOwnerRelease();
        if (mode.equals("all") || mode.equals("variant-paths")) variantSelectedPaths();
        if (mode.equals("all") || mode.equals("service")) service();
        System.out.println("PASS next real app/controller host contracts " + mode + " " + checks + " checks (NOT Android runtime)");
    }
}
