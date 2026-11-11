import java.io.*;
import java.nio.file.*;
import java.util.*;
import android.content.*;
import android.media.AudioRecord;
import org.llmasr.minimal.asr.*;
import org.llmasr.minimal.audio.*;
import org.llmasr.minimal.ime.*;
import org.llmasr.minimal.model.*;
import org.llmasr.minimal.platform.OperationContext;
import org.llmasr.minimal.task.*;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;

/** Real four runtime adapters on finite calibrated platform fakes; not device acceptance. */
public final class RuntimeContractTest {
    static int checks;
    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    static final class ContextPort extends Context {
        boolean nullApplication;
        File directory;
        ContextPort(File value) { directory = value; }
        public Context getApplicationContext() { order += "application;"; return nullApplication ? null : this; }
        public File getFilesDir() { return directory; }
        public File getCacheDir() { return directory; }
        public ContentResolver getContentResolver() { return new ContentResolver() {}; }
        public android.content.res.AssetManager getAssets() { order += "assets;"; return new android.content.res.AssetManager(); }
    }
    static void asset() throws Exception {
        ContextPort c = new ContextPort(null);
        OperationContext context = new OperationContext.Android(c);
        Context.order = ""; Context.seen = "unset"; Context.assetCalls = 0;
        Throwable actual = null;
        try { context.openAsset(null); } catch (Throwable failure) { actual = failure; }
        check(Context.assetCalls == 1 && Context.seen == null, "Asset null must reach receiver");
        check(actual == Context.SENTINEL && Context.order.equals("assets;open;"), "Asset receiver/sentinel identity");
        c.nullApplication = true; actual = null;
        try { new OperationContext.Android(c); } catch (Throwable failure) { actual = failure; }
        check(actual instanceof IllegalArgumentException && "applicationContext required".equals(actual.getMessage()), "application-only validation");
        actual = null;
        try { new OperationContext.Android(null); } catch (Throwable failure) { actual = failure; }
        check(actual instanceof IllegalArgumentException && "appContext required".equals(actual.getMessage()), "context ctor null validation");
    }
    static SafModelSource source() { return new SafModelSource(new ContentResolver() {}, null); }
    static void safNull() throws Exception {
        SafModelSource source = source();
        ContentResolver.queryCalls = 0; ContentResolver.order = ""; ContentResolver.failQuery = true;
        ContentResolver.seen = new android.net.Uri() {};
        Throwable actual = null;
        try { source.enumerate(new ArrayList<String>(), 10, ModelRepository.NEVER_CANCEL); } catch (Throwable failure) { actual = failure; }
        check(ContentResolver.queryCalls == 1 && ContentResolver.seen == null, "SAF null Uri must reach receiver inside boundary");
        check(actual instanceof IOException && ModelProviderBoundary.DIRECTORY_FAILURE.equals(actual.getMessage()) && actual.getCause() == null && actual.getSuppressed().length == 0, "SAF directory failure stays safe");
        check(ContentResolver.order.equals("tree;build;query;"), "SAF URI/query evaluation order");
        // UriRef is final with a fixed private final handle. These are the ordinary reachable values.
        for (Object handle : new Object[] {null, "wrong type", new android.net.Uri() {}}) {
            ContentResolver.openCalls = 0; actual = null;
            try { source.open(new ModelSource.UriRef(handle)); } catch (Throwable failure) { actual = failure; }
            if (handle instanceof android.net.Uri) {
                check(ContentResolver.openCalls == 1 && ContentResolver.seen == handle, "SAF valid handle identity");
                check(actual instanceof IOException && ModelProviderBoundary.FILE_FAILURE.equals(actual.getMessage()) && actual.getCause() == null, "SAF open private failure safe");
            } else check(ContentResolver.openCalls == 0 && actual instanceof IOException && ModelProviderBoundary.FILE_FAILURE.equals(actual.getMessage()), "SAF null/wrong handle rejects before provider");
        }
        ContentResolver.openCalls = 0; actual = null;
        try { source.open(null); } catch (Throwable failure) { actual = failure; }
        check(actual instanceof NullPointerException && ContentResolver.openCalls == 0, "SAF null ref dereferences before provider");
    }
    static void twr() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            final int scenario = mode;
            AssertionError primary = new AssertionError("own-checkpoint");
            AssertionError closing = mode == 0 ? primary : new AssertionError("close-error");
            int[] checkpoint = {0}, closed = {0}, advanced = {0};
            ContentResolver.failQuery = false;
            ContentResolver.cursor = new android.database.Cursor() {
                public boolean moveToNext() { advanced[0]++; return false; }
                public String getString(int index) { return null; }
                public boolean isNull(int index) { return false; }
                public long getLong(int index) { return 0; }
                public void close() {
                    closed[0]++;
                    if (scenario != 3) throw closing;
                }
            };
            ModelRepository.CancelGate gate = new ModelRepository.CancelGate() {
                public boolean cancelled() {
                    if (++checkpoint[0] == 3 && scenario != 2) throw primary;
                    return false;
                }
                public boolean reservePublish() { return true; }
            };
            Throwable actual = null;
            try { source().enumerate(new ArrayList<String>(), 10, gate); } catch (Throwable failure) { actual = failure; }
            check(closed[0] == 1, "SAF TWR close exactly once");
            if (mode == 0) {
                check(actual instanceof IllegalArgumentException && actual.getCause() == primary, "SAF Java self suppression cause");
                check(primary.getSuppressed().length == 0, "self does not attach itself");
            } else if (mode == 1) check(actual == primary && primary.getSuppressed().length == 1 && primary.getSuppressed()[0] == closing, "SAF distinct primary/suppressed identities");
            else if (mode == 2) check(actual == closing && advanced[0] == 1, "SAF close-only Error identity");
            else check(actual == primary && primary.getSuppressed().length == 0, "SAF primary-only Error identity");
            if (mode != 2) check(advanced[0] == 0, "checkpoint failure precedes advance");
        }
    }
    static void recorder() throws Exception {
        for (String phase : new String[] {"minimum", "constructor", "state", "start", "read", "recordingState", "stop", "release", "cancel", "fatal", "success", "illegalStop"}) {
            RecordingControl control = new RecordingControl(); AudioRecord.reset(phase, control);
            if (phase.equals("cancel")) control.cancel();
            Throwable actual = null; byte[] wav = null;
            try { wav = ForegroundRecorder.capture(control, seconds -> {}); } catch (Throwable failure) { actual = failure; }
            check(control.released(), "captureReleased acknowledgement must survive every exit");
            int made = phase.equals("minimum") || phase.equals("constructor") || phase.equals("cancel") ? 0 : 1;
            check(AudioRecord.made == made && AudioRecord.releases == made, "Recorder constructed/released once");
            if (phase.equals("cancel")) check(actual instanceof java.util.concurrent.CancellationException && AudioRecord.starts == 0, "cancel before start");
            else if (phase.equals("fatal")) check(actual == AudioRecord.fatal, "Recorder Error identity");
            else if (phase.equals("success") || phase.equals("illegalStop")) check(actual == null && wav != null && wav.length == 3244 && control.tryCommitInference(), "Recorder real PCM/gate result");
            else check(actual instanceof IOException && "麦克风权限不可用，请重新授权后重试".equals(actual.getMessage()) && actual.getCause() == null && actual.getSuppressed().length == 0, "Recorder permission privacy");
        }
        recorderStartGate();
    }
    static void recorderStartGate() throws Exception {
        for(String mode:Arrays.asList("deny","throw","start")){
            RecordingControl control=new RecordingControl();AudioRecord.reset("success",control);
            RuntimeException sentinel=new RuntimeException("start dispatcher sentinel");Throwable seen=null;
            try{ForegroundRecorder.capture(control,s->{},hardware->{
                check(AudioRecord.made==1&&AudioRecord.starts==0,"actual recorder init precedes dispatcher");
                if(mode.equals("throw"))throw sentinel;
                return mode.equals("start")&&control.start(hardware);
            });}catch(Throwable t){seen=t;}
            check(AudioRecord.releases==1&&control.released(),"dispatcher paths worker hardware cleanup acknowledged");
            if(mode.equals("deny"))check(seen instanceof java.util.concurrent.CancellationException&&AudioRecord.starts==0&&!control.tryCommitInference(),"denied start never records or commits");
            if(mode.equals("throw"))check(seen==sentinel&&AudioRecord.starts==0,"dispatcher exception propagates with hardware cleanup");
            if(mode.equals("start"))check(seen==null&&AudioRecord.starts==1&&control.tryCommitInference(),"actual recorder gated start once");
        }
    }
    /** Real synthetic disk proof + worker resident; never a fabricated READY/loaded flag. */
    static final class QualifiedBackendFixture implements AutoCloseable {
        static final String DIGEST = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81";
        final Path root, models, journal, inputs;
        final Thread worker = Thread.currentThread();
        final ModelManifest manifest = new ModelManifest(Arrays.asList(new ModelEntry("a", 3, DIGEST)));
        final ModelReadiness readiness = new ModelReadiness();
        final ModelResidency residency;
        final ModelVerification proof;
        final ModelRepository repository;
        final ModelAccess access;
        int prepares, releases, deniedInferenceCalls;
        QualifiedBackendFixture(Path root) throws Exception {
            this.root = root;
            models = Files.createDirectories(root.resolve("model"));
            journal = Files.createDirectories(root.resolve("proof"));
            inputs = Files.createDirectories(root.resolve("inputs"));
            Files.write(models.resolve("a"), new byte[]{1, 2, 3});
            residency = new ModelResidency(new ModelResidency.Native() {
                public double prepare(String config, String cache) { prepares++; return 0; }
                public void release() { releases++; }
            }, () -> Thread.currentThread() == worker);
            proof = new ModelVerification(manifest, readiness, new ModelVerification.Io() {
                public boolean exists(File file) { return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS); }
                public String identity(File file, boolean directory) throws IOException {
                    return new TreeMap<>(Files.readAttributes(file.toPath(), "unix:dev,ino,mode,size,lastModifiedTime,ctime", LinkOption.NOFOLLOW_LINKS)).toString();
                }
                public byte[] read(String name, int limit) throws IOException {
                    Path file = journal.resolve(name);
                    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
                    try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        for (int b; (b = in.read()) != -1;) {
                            if (out.size() == limit) throw new IOException("proof limit");
                            out.write(b);
                        }
                        return out.toByteArray();
                    }
                }
                public void write(String name, byte[] bytes) throws IOException {
                    Path temp = journal.resolve(name + ".tmp");
                    try (FileOutputStream out = new FileOutputStream(temp.toFile())) { out.write(bytes); out.getFD().sync(); }
                    Files.move(temp, journal.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(journal, StandardOpenOption.READ)) { channel.force(true); }
                }
            }, residency);
            repository = new ModelRepository(manifest, models.toFile(), () -> Long.MAX_VALUE, proof);
            access = new ModelAccess(repository, readiness, residency);
        }
        OperationContext context() {
            return new OperationContext() {
                public File filesDir() { return inputs.toFile(); }
                public File cacheDir() { return root.toFile(); }
                public InputStream openAsset(String name) { return null; }
                public ContentResolver contentResolver() { return null; }
            };
        }
        ImeBackend backend(OperationContext context, ModelRepository arguments, InferenceAdapter inference) {
            // The plain proof repository and argument getter port point to the same tree.
            // Readiness snapshot IO must not be mistaken for admitted inference argument evaluation.
            return new ImeBackend(context, arguments, inference, access);
        }
        void qualify(ImeBackend backend) throws Exception { backend.qualify(new RecordingControl(), message -> {}); }
        void prepare(ImeBackend backend) throws Exception {
            qualify(backend);
            String generation = access.readyGeneration();
            backend.prepare(new RecordingControl(), message -> {});
            check(generation.equals(access.readyGeneration()) && residency.snapshot().getState() == ModelResidency.State.LOADED,
                "Runtime fixture actual proof generation and resident admission");
            access.requireResidentReady();
            check(Files.isRegularFile(journal.resolve("receipt"), LinkOption.NOFOLLOW_LINKS) && prepares > 0,
                "Runtime fixture durable receipt and actual fake native preparation");
        }
        void reject(ImeBackend backend, RequestContext request) throws Exception {
            int before = prepares, inferenceBefore = deniedInferenceCalls;
            Throwable actual = null;
            try { backend.transcribe(request, orderWave(), "Chinese"); } catch (Throwable failure) { actual = failure; }
            check(actual instanceof IOException && request.inputWav == null && prepares == before && deniedInferenceCalls == inferenceBefore,
                "Runtime unqualified admission denies before WAV/native");
            try (java.util.stream.Stream<Path> paths = Files.list(inputs)) { check(!paths.findAny().isPresent(), "Runtime denied admission leaves no WAV"); }
        }
        public void close() throws Exception { access.requestRelease(); access.release(); }
    }
    static void backendQualificationNegatives(Path directory) throws Exception {
        try (QualifiedBackendFixture f = new QualifiedBackendFixture(directory.resolve("negative"))) {
            InferenceAdapter deniedInference = new InferenceAdapter(null, () -> {}, () -> {},
                (config, wav, language, cache, listener) -> { f.deniedInferenceCalls++; throw new AssertionError("denied request reached native inference"); });
            ImeBackend backend = f.backend(f.context(), f.repository, deniedInference);
            f.reject(backend, new RequestContext("unqualified", TaskKind.INFERENCE));
            f.qualify(backend);
            check(f.prepares == 0 && f.residency.snapshot().getState() == ModelResidency.State.EMPTY, "qualification alone never loads native");
            f.reject(backend, new RequestContext("unprepared", TaskKind.INFERENCE));
            f.prepare(backend);
            Files.write(f.journal.resolve("receipt"), new byte[]{0});
            Throwable actual = null;
            try { f.access.restoreFast(); } catch (Throwable failure) { actual = failure; }
            check(actual instanceof IOException, "bad persisted proof rejected by actual restore");
            f.reject(backend, new RequestContext("bad-proof", TaskKind.INFERENCE));
            f.prepare(backend);
            String previous = f.access.readyGeneration();
            f.proof.begin(f.repository); // Real verification revocation, not a fabricated generation.
            ImeBackend next = f.backend(f.context(), f.repository, null);
            f.prepare(next);
            check(!previous.equals(f.access.readyGeneration()), "real proof transaction changes generation");
            f.reject(backend, new RequestContext("old-generation", TaskKind.INFERENCE));
            next.cleanup(new RequestContext("fresh-cleanup", TaskKind.INFERENCE));
        }
    }
    static void backend() throws Exception {
        ImeBackend empty = new ImeBackend(null, null, null, new org.llmasr.minimal.model.ModelAccess(null, null));
        RecordingControl control = new RecordingControl(); AudioRecord.reset("success", control);
        Throwable actual = null;
        try { empty.requireModelReady(); throw new AssertionError("unprepared backend admission accepted"); } catch (IOException expected) { }
        check(AudioRecord.made == 0 && AudioRecord.starts == 0, "IME model admission before AudioRecord");
        try { empty.capture(control, null); } catch (Throwable failure) { actual = failure; }
        check(actual instanceof NullPointerException && AudioRecord.made == 0 && AudioRecord.starts == 0 && !control.released(), "IME null progress must fail before capture");
        Path directory = Files.createTempDirectory("runtime-backend-");
        try (QualifiedBackendFixture f = new QualifiedBackendFixture(directory)) {
            OperationContext context = f.context();
            ModelRepository repository = f.repository;
            InferenceAdapter inference = new InferenceAdapter(null, () -> {}, () -> {},
                (config, wav, language, cache, listener) -> "1 2 3\n<asr_text>runtime".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ImeBackend backend = f.backend(context, repository, inference);
            f.prepare(backend);
            PcmWave pcm = new PcmWave(); pcm.append(new short[PcmWave.MIN_SAMPLES], PcmWave.MIN_SAMPLES);
            RequestContext request = new RequestContext("runtime-id", TaskKind.INFERENCE);
            check(backend.transcribe(request, pcm.finish(), "Chinese").equals("runtime") && request.inputWav.isFile(), "IME actual WAV/inference adapter");
            backend.cleanup(request);
            check(!request.inputWav.exists(), "IME cleanup exact request file");
            backend.cleanup(request);
            backendQualificationNegatives(directory);
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
    static byte[] orderWave() throws IOException {
        PcmWave pcm = new PcmWave(); pcm.append(new short[PcmWave.MIN_SAMPLES], PcmWave.MIN_SAMPLES);
        return pcm.finish();
    }
    static void deleteOrderDirectory(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }
    static void imeRequestOrder() throws Exception {
        Path directory = Files.createTempDirectory("runtime-request-order-");
        try {
            byte[] wav = orderWave();
            for (int mode = 0; mode < 3; mode++) {
                final Throwable injected = mode == 0 ? null : mode == 1 ? new IllegalStateException("files-getter") : new AssertionError("files-getter");
                int[] files = {0}, cache = {0}; boolean[] armed = {false};
                try (QualifiedBackendFixture f = new QualifiedBackendFixture(directory.resolve("case-" + mode))) {
                    OperationContext context = new OperationContext() {
                        public File filesDir() { if (armed[0]) { files[0]++; if (injected instanceof RuntimeException) throw (RuntimeException)injected; if (injected instanceof Error) throw (Error)injected; } return f.inputs.toFile(); }
                        public File cacheDir() { if (armed[0]) cache[0]++; return directory.toFile(); }
                        public InputStream openAsset(String name) { return null; }
                        public ContentResolver contentResolver() { return null; }
                    };
                    ImeBackend backend = f.backend(context, f.repository, null);
                    f.prepare(backend);
                    check(files[0] == 0 && cache[0] == 0, "request setup never arms observation counters");
                    armed[0] = true;
                    Throwable actual = null;
                    try { backend.transcribe(null, wav, null); } catch (Throwable failure) { actual = failure; }
                    check(files[0] == 1, "IME null request must evaluate filesDir first");
                    check(injected == null ? actual instanceof NullPointerException : actual == injected, "IME null request getter Throwable identity");
                    check(cache[0] == 0, "IME null request never reaches inference arguments");
                    try (java.util.stream.Stream<Path> paths = Files.list(f.inputs)) { check(!paths.findAny().isPresent(), "IME null request creates no WAV"); }
                    backend.cleanup(new RequestContext("order-cleanup", TaskKind.INFERENCE));
                }
            }
        } finally { deleteOrderDirectory(directory); }
    }
    static void imeInferenceOrder() throws Exception {
        Path directory = Files.createTempDirectory("runtime-inference-order-");
        try {
            byte[] wav = orderWave();
            for (int mode = 0; mode < 5; mode++) {
                try (QualifiedBackendFixture f = new QualifiedBackendFixture(directory.resolve("case-" + mode))) {
                    final int scenario = mode;
                    final Throwable injected = mode == 4 ? null : mode % 2 == 0 ? new IllegalStateException("argument-getter") : new AssertionError("argument-getter");
                    RequestContext request = new RequestContext("order-" + mode, TaskKind.INFERENCE);
                    StringBuilder order = new StringBuilder(); int[] files = {0}, models = {0}, caches = {0}; boolean[] modelWav = {false}, cacheWav = {false};
                    boolean[] armed = {false};
                    ModelRepository repository = new ModelRepository(f.manifest, f.models.toFile(), () -> Long.MAX_VALUE) {
                        public File modelDir() {
                            if (!armed[0]) return f.models.toFile();
                            models[0]++; order.append("model;"); modelWav[0] = request.inputWav != null && request.inputWav.isFile();
                            if (scenario < 2) { if (injected instanceof RuntimeException) throw (RuntimeException)injected; throw (Error)injected; }
                            return f.models.toFile();
                        }
                    };
                    OperationContext context = new OperationContext() {
                        public File filesDir() { if (armed[0]) { files[0]++; order.append("files;"); } return f.inputs.toFile(); }
                        public File cacheDir() {
                            if (!armed[0]) return directory.toFile();
                            caches[0]++; order.append("cache;"); cacheWav[0] = request.inputWav != null && request.inputWav.isFile();
                            if (scenario == 2 || scenario == 3) { if (injected instanceof RuntimeException) throw (RuntimeException)injected; throw (Error)injected; }
                            return directory.toFile();
                        }
                        public InputStream openAsset(String name) { return null; }
                        public ContentResolver contentResolver() { return null; }
                    };
                    ImeBackend backend = f.backend(context, repository, null);
                    f.prepare(backend);
                    check(files[0] == 0 && models[0] == 0 && caches[0] == 0 && order.length() == 0, "inference setup never arms observation counters");
                    armed[0] = true;
                    Throwable actual = null;
                    try { backend.transcribe(request, wav, null); } catch (Throwable failure) { actual = failure; }
                    check(models[0] == 1, "IME null inference must evaluate arguments first");
                    check(files[0] == 1 && caches[0] == (mode < 2 ? 0 : 1), "IME argument getter counts");
                    check(order.toString().equals(mode < 2 ? "files;model;" : "files;model;cache;"), "IME model then cache argument order");
                    check(injected == null ? actual instanceof NullPointerException : actual == injected, "IME null inference argument Throwable identity");
                    check(modelWav[0] && (mode < 2 || cacheWav[0]) && request.inputWav.isFile(), "IME WAV canonicalized before inference arguments");
                    backend.cleanup(request); check(!request.inputWav.exists(), "IME failed inference exact WAV cleanup");
                    if (mode == 4) {
                        armed[0] = false; f.prepare(backend); armed[0] = true;
                        files[0] = models[0] = caches[0] = 0; order.setLength(0); actual = null;
                        try { backend.transcribe(request, null, null); } catch (Throwable failure) { actual = failure; }
                        check(actual instanceof NullPointerException && files[0] == 1 && models[0] == 0 && caches[0] == 0 && !request.inputWav.exists(), "IME null WAV fails after assignment before inference arguments");
                        backend.cleanup(request);
                    }
                }
            }
        } finally { deleteOrderDirectory(directory); }
    }
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "all" : args[0];
        if (mode.equals("all") || mode.equals("asset")) asset();
        if (mode.equals("all") || mode.equals("saf")) safNull();
        if (mode.equals("all") || mode.equals("twr")) twr();
        if (mode.equals("all") || mode.equals("recorder")) recorder();
        if (mode.equals("all") || mode.equals("backend")) backend();
        if (mode.equals("all") || mode.equals("ime-request-order")) imeRequestOrder();
        if (mode.equals("all") || mode.equals("ime-inference-order")) imeInferenceOrder();
        System.out.println("PASS runtime four production contracts " + mode + " " + checks + " checks (not Android)");
    }
}
