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
        try {
            OperationContext context = new OperationContext() {
                public File filesDir() { return directory.toFile(); }
                public File cacheDir() { return directory.toFile(); }
                public InputStream openAsset(String name) { return null; }
                public ContentResolver contentResolver() { return null; }
            };
            ModelManifest manifest = new ModelManifest(Arrays.asList(new ModelEntry("a", 0, String.join("", Collections.nCopies(64, "0")))));
            ModelRepository repository = ModelRepository.forWorker(manifest, directory.resolve("model").toFile(), () -> Long.MAX_VALUE);
            InferenceAdapter inference = new InferenceAdapter(null, () -> {}, () -> {},
                (config, wav, language, cache, listener) -> "1 2 3\n<asr_text>runtime".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ImeBackend backend = new ImeBackend(context, repository, inference, new org.llmasr.minimal.model.ModelAccess(repository, new org.llmasr.minimal.model.ModelReadiness()));
            PcmWave pcm = new PcmWave(); pcm.append(new short[PcmWave.MIN_SAMPLES], PcmWave.MIN_SAMPLES);
            RequestContext request = new RequestContext("runtime-id", TaskKind.INFERENCE);
            check(backend.transcribe(request, pcm.finish(), "Chinese").equals("runtime") && request.inputWav.isFile(), "IME actual WAV/inference adapter");
            backend.cleanup(request);
            check(!request.inputWav.exists(), "IME cleanup exact request file");
            backend.cleanup(request);
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
                int[] files = {0}, cache = {0};
                OperationContext context = new OperationContext() {
                    public File filesDir() { files[0]++; if (injected instanceof RuntimeException) throw (RuntimeException)injected; if (injected instanceof Error) throw (Error)injected; return directory.toFile(); }
                    public File cacheDir() { cache[0]++; return directory.toFile(); }
                    public InputStream openAsset(String name) { return null; }
                    public ContentResolver contentResolver() { return null; }
                };
                Throwable actual = null;
                try { new ImeBackend(context, null, null, new org.llmasr.minimal.model.ModelAccess(null, null)).transcribe(null, wav, null); } catch (Throwable failure) { actual = failure; }
                check(files[0] == 1, "IME null request must evaluate filesDir first");
                check(injected == null ? actual instanceof NullPointerException : actual == injected, "IME null request getter Throwable identity");
                check(cache[0] == 0, "IME null request never reaches inference arguments");
                try (java.util.stream.Stream<Path> paths = Files.list(directory)) { check(!paths.findAny().isPresent(), "IME null request creates no WAV"); }
            }
        } finally { deleteOrderDirectory(directory); }
    }
    static void imeInferenceOrder() throws Exception {
        Path directory = Files.createTempDirectory("runtime-inference-order-");
        try {
            byte[] wav = orderWave();
            ModelManifest manifest = new ModelManifest(Arrays.asList(new ModelEntry("a", 0, String.join("", Collections.nCopies(64, "0")))));
            for (int mode = 0; mode < 5; mode++) {
                final int scenario = mode;
                final Throwable injected = mode == 4 ? null : mode % 2 == 0 ? new IllegalStateException("argument-getter") : new AssertionError("argument-getter");
                RequestContext request = new RequestContext("order-" + mode, TaskKind.INFERENCE);
                StringBuilder order = new StringBuilder(); int[] files = {0}, models = {0}, caches = {0}; boolean[] modelWav = {false}, cacheWav = {false};
                ModelRepository repository = new ModelRepository(manifest, directory.resolve("model").toFile(), () -> Long.MAX_VALUE) {
                    public File modelDir() {
                        models[0]++; order.append("model;"); modelWav[0] = request.inputWav != null && request.inputWav.isFile();
                        if (scenario < 2) { if (injected instanceof RuntimeException) throw (RuntimeException)injected; throw (Error)injected; }
                        return directory.resolve("model").toFile();
                    }
                };
                OperationContext context = new OperationContext() {
                    public File filesDir() { files[0]++; order.append("files;"); return directory.toFile(); }
                    public File cacheDir() {
                        caches[0]++; order.append("cache;"); cacheWav[0] = request.inputWav != null && request.inputWav.isFile();
                        if (scenario == 2 || scenario == 3) { if (injected instanceof RuntimeException) throw (RuntimeException)injected; throw (Error)injected; }
                        return directory.toFile();
                    }
                    public InputStream openAsset(String name) { return null; }
                    public ContentResolver contentResolver() { return null; }
                };
                ImeBackend backend = new ImeBackend(context, repository, null, new org.llmasr.minimal.model.ModelAccess(repository, new org.llmasr.minimal.model.ModelReadiness())); Throwable actual = null;
                try { backend.transcribe(request, wav, null); } catch (Throwable failure) { actual = failure; }
                check(models[0] == 1, "IME null inference must evaluate arguments first");
                check(files[0] == 1 && caches[0] == (mode < 2 ? 0 : 1), "IME argument getter counts");
                check(order.toString().equals(mode < 2 ? "files;model;" : "files;model;cache;"), "IME model then cache argument order");
                check(injected == null ? actual instanceof NullPointerException : actual == injected, "IME null inference argument Throwable identity");
                check(modelWav[0] && (mode < 2 || cacheWav[0]) && request.inputWav.isFile(), "IME WAV canonicalized before inference arguments");
                backend.cleanup(request); check(!request.inputWav.exists(), "IME failed inference exact WAV cleanup");
                if (mode == 4) {
                    files[0] = models[0] = caches[0] = 0; order.setLength(0); actual = null;
                    try { backend.transcribe(request, null, null); } catch (Throwable failure) { actual = failure; }
                    check(actual instanceof NullPointerException && files[0] == 1 && models[0] == 0 && caches[0] == 0 && !request.inputWav.exists(), "IME null WAV fails after assignment before inference arguments");
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
