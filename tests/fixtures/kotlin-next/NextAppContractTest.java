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
    static final class ImeFixture implements AutoCloseable {
        final TaskCoordinator owner = new TaskCoordinator(Runnable::run);
        int captures, writes; final List<String> events = new ArrayList<>();
        final ImeController controller = new ImeController(owner, new ImeController.Backend() {
            public byte[] capture(RecordingControl c, ImeController.Progress p) {
                captures++; c.start(() -> {}); c.stop(); c.captureReleased(); return new byte[]{1};
            }
            public String transcribe(RequestContext c, byte[] w, String l) { return "synthetic preview"; }
            public void cleanup(RequestContext c) {}
        }, () -> {});
        final AsrImeService service = new AsrImeService();
        final android.widget.LinearLayout root;
        final ImeSession original;
        ImeFixture() throws Exception {
            android.widget.Toast.shows=0; android.widget.Toast.text="";
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
        public void close() {service.onDestroy();android.os.Handler.pending.clear();check(!owner.isBusy(),"IME fixture actual owner exit");}
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
    static void imeFallback() throws Exception {
        for(int mode=0;mode<7;mode++) try(ImeFixture f=new ImeFixture()) {
            f.service.previousResult=false;
            if(mode==1)f.service.previousFailure=new SecurityException("PRIVATE editor sentinel");
            if(mode==2)f.service.previousFailure=new IllegalStateException("PRIVATE editor sentinel");
            if(mode==3)f.service.manager.failure=new SecurityException("PRIVATE editor sentinel");
            if(mode==4)f.service.noManager=true;
            if(mode==5)f.service.serviceFailure=new IllegalStateException("PRIVATE editor sentinel");
            if(mode==6)f.service.hideFailure=new IllegalStateException("PRIVATE hide sentinel");
            f.click("返回上一输入法");
            check(f.service.previousCalls==1&&f.service.manager.calls==(mode==4||mode==5?0:1),
                "previous failure must launch picker once");
            check(f.service.hides==1&&f.events.get(0).equals("hide")&&f.events.get(1).equals("previous"),"hide before previous");
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
            check(invalid.size()==2&&invalid.get(0)&&invalid.get(1),"external switch must invalidate before platform request");
            f.stale();check(f.service.previousCalls==1&&f.service.manager.calls==0,"accepted previous never duplicates picker");
        }
    }
    static void imeHideFailure() throws Exception {
        try(ImeFixture f=new ImeFixture()) {
            f.service.hideFailure=new IllegalStateException("hide sentinel");
            try{f.click("返回上一输入法");}catch(RuntimeException ignored){/* Assert observable attempt outside listener. */}
            check(f.service.previousCalls==1,"hide failure must not suppress external attempt");f.stale();
        }
        try(ImeFixture f=new ImeFixture()) {
            f.controller.invalidate();set(f.service,"controller",null);
            f.service.hideFailure=new SecurityException("hide sentinel");f.service.previousResult=false;
            f.click("返回上一输入法");
            check(f.service.previousCalls==1&&f.service.manager.calls==1,"null controller hide failure still attempts fallback");
            check(!f.button("丢弃 / 重新开始").isEnabled(),"null controller cannot restart");
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
    static void imeReturn(String mode) throws Exception {
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
        if (mode.startsWith("ime-")) imeReturn(mode);
        if (mode.startsWith("compat-")) compat(mode);
        if (mode.equals("all") || mode.equals("fallback")) controller(false);
        if (mode.equals("all") || mode.equals("notification")) controller(true);
        if (mode.equals("all") || mode.equals("graph")) graph();
        if (mode.equals("all") || mode.equals("operation")) operation();
        if (mode.equals("all") || mode.equals("service")) service();
        System.out.println("PASS next real app/controller host contracts " + mode + " " + checks + " checks (NOT Android runtime)");
    }
}
