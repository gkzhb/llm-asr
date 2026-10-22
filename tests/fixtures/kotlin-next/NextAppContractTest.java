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
                new TextExportController<android.net.Uri>(target -> new ByteArrayOutputStream(), Runnable::run), inference, reports);
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
    static void service() throws Exception {
        TaskCoordinator owner = new TaskCoordinator(Runnable::run);
        ImeController.Backend backend = new ImeController.Backend() {
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
            AsrOperation op=new AsrOperation(ctx,owner,repo,new AppState(),new TextExportController<android.net.Uri>(u->new ByteArrayOutputStream(),Runnable::run),infer,reports);
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
        AppReportWriter reports=new AppReportWriter(ctx);AsrOperation op=new AsrOperation(ctx,owner,null,new AppState(),new TextExportController<android.net.Uri>(u->null,Runnable::run),null,reports);
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
        if (mode.startsWith("compat-")) compat(mode);
        if (mode.equals("all") || mode.equals("fallback")) controller(false);
        if (mode.equals("all") || mode.equals("notification")) controller(true);
        if (mode.equals("all") || mode.equals("graph")) graph();
        if (mode.equals("all") || mode.equals("operation")) operation();
        if (mode.equals("all") || mode.equals("service")) service();
        System.out.println("PASS next real app/controller host contracts " + mode + " " + checks + " checks (NOT Android runtime)");
    }
}
