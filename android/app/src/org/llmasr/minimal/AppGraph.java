package org.llmasr.minimal;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.llmasr.minimal.asr.InferenceAdapter;
import org.llmasr.minimal.asr.JniNativeTranscription;
import org.llmasr.minimal.diagnostics.LogExportController;
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogPersistence;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;
import org.llmasr.minimal.diagnostics.RuntimeLogWorker;
import org.llmasr.minimal.ime.ImeBackend;
import org.llmasr.minimal.ime.ImeController;
import org.llmasr.minimal.model.ModelAccess;
import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import org.llmasr.minimal.modelmanagement.ModelPickerTickets;
import org.llmasr.minimal.platform.OperationContext;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.transcription.AppReportWriter;
import org.llmasr.minimal.transcription.AppState;
import org.llmasr.minimal.transcription.AsrOperation;
import org.llmasr.minimal.transcription.TextExportController;

/** Process-wide wiring. Built once when the launcher Activity is created.
 * The manifest parser is the only Android adapter for JSON; ModelManifest
 * itself never touches org.json. A bad asset fails closed: install throws
 * and the Activity sees a fresh start with a usable error message instead
 * of a silent empty-manifest success.
 */
public final class AppGraph {
    private static volatile AppGraph INSTANCE;

    public static AppGraph get() {
        AppGraph g = INSTANCE;
        if (g == null) throw new IllegalStateException("AppGraph not installed");
        return g;
    }

    /** Install with the supplied application context. Parses the bundled
     * manifest, builds the model repository, and runs the process-once
     * startup status. Throws if the manifest is missing or malformed.
     */
    public static void install(Context appContext) throws IOException {
        if (appContext == null) throw new IllegalArgumentException("appContext required");
        if (INSTANCE != null) return;
        synchronized (AppGraph.class) {
            if (INSTANCE != null) return;
            OperationContext opCtx = new OperationContext.Android(appContext);
            ModelManifest manifest = parseManifest(opCtx);
            ModelReadiness readiness = new ModelReadiness();
            AppState state = new AppState();
            TaskCoordinator coordinator = new TaskCoordinator();
            TextExportController<android.net.Uri> textExport = new TextExportController<>(target ->
                opCtx.contentResolver().openOutputStream(target, "wt"));
            ModelRepository.SpaceProvider space = () -> opCtx.filesDir().getUsableSpace();
            ModelRepository repository = ModelRepository.forAppFiles(manifest, opCtx.filesDir(), space);
            ModelAccess access = new ModelAccess(repository, readiness);
            RuntimeLogStore logStore = new RuntimeLogStore();
            RuntimeLogPersistence logPersistence = new RuntimeLogPersistence(opCtx.filesDir());
            RuntimeLogPersistence.FailureSink logFailure = reason -> logStore.append(
                RuntimeLogEventKind.LOG_PERSISTENCE_FAILED, RuntimeLogSource.SHARED, null, reason);
            RuntimeLogWorker logWorker = new RuntimeLogWorker(logStore, logPersistence, logFailure);
            InferenceAdapter inference = new InferenceAdapter(logStore, access::requireReady,
                () -> System.loadLibrary("qwen_asr_jni"), new JniNativeTranscription());
            AsrOperation ops = new AsrOperation(opCtx, coordinator, repository, state, textExport,
                inference, new AppReportWriter(opCtx));
            AppGraph built = new AppGraph(opCtx, coordinator, repository, state, textExport, ops, readiness, access,
                logStore, logWorker, inference);
            // Process-once startup. Same owner as every other request, runs
            // exactly one cleanup and surfaces the count to the user.
            built.asrOperation.startupOnce();
            logStore.append(RuntimeLogEventKind.APP_STARTUP, RuntimeLogSource.SHARED, null, "ok");
            logStore.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok");
            INSTANCE = built;
        }
    }

    /** Parse the bundled manifest asset into a typed ModelManifest.
     * Android-side adapter; this is the only place org.json is used for
     * manifest parsing.
     */
    private static ModelManifest parseManifest(OperationContext opCtx) throws IOException {
        List<ModelEntry> entries = new ArrayList<>();
        byte[] buf = new byte[8192];
        try (InputStream in = opCtx.openAsset("model-manifest.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > 1024 * 1024) throw new IOException("模型清单过大");
                out.write(buf, 0, n);
            }
            JSONObject root = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            JSONArray arr = root.getJSONArray("files");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                String name = e.getString("file");
                if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IOException("清单条目包含路径分隔符：" + name);
                entries.add(new ModelEntry(name, e.getLong("bytes"), e.getString("sha256")));
            }
        } catch (org.json.JSONException je) {
            throw new IOException("无法解析模型清单：" + je.getMessage());
        }
        return new ModelManifest(entries);
    }

    private final OperationContext context;
    private final TaskCoordinator coordinator;
    private final ModelRepository modelRepository;
    private final AppState appState;
    private final TextExportController<android.net.Uri> textExport;
    private final AsrOperation asrOperation;
    private final ImeBackend imeBackend;
    private final ModelReadiness readiness;
    private final ModelAccess modelAccess;
    private final ModelManagementState modelState = new ModelManagementState();
    private final ModelOperationControl modelControl = new ModelOperationControl();
    private final ModelManagementController modelManagement;
    private final ModelPickerTickets pickerTickets = new ModelPickerTickets();
    private final RuntimeLogStore runtimeLogStore;
    private final RuntimeLogWorker runtimeLogWorker;
    private final LogExportController<android.net.Uri> logExports;

    private AppGraph(OperationContext context, TaskCoordinator coordinator,
                     ModelRepository modelRepository, AppState appState,
                     TextExportController<android.net.Uri> textExport, AsrOperation asrOperation,
                     ModelReadiness readiness, ModelAccess modelAccess,
                     RuntimeLogStore runtimeLogStore, RuntimeLogWorker runtimeLogWorker,
                     InferenceAdapter inference) {
        this.context = context;
        this.coordinator = coordinator;
        this.modelRepository = modelRepository;
        this.appState = appState;
        this.textExport = textExport;
        this.asrOperation = asrOperation;
        this.imeBackend = new ImeBackend(context, modelRepository, inference);
        this.readiness = readiness; this.modelAccess = modelAccess;
        this.runtimeLogStore = runtimeLogStore;
        this.runtimeLogWorker = runtimeLogWorker;
        // Only application context is reachable from a provider worker, never an Activity.
        this.logExports = new LogExportController<>(target ->
            context.contentResolver().openOutputStream(target, "wt"));
        this.modelManagement = new ModelManagementController(coordinator, modelRepository, readiness, modelControl, modelState, runtimeLogStore);
    }

    public ModelReadiness readiness() { return readiness; }
    public ModelAccess modelAccess() { return modelAccess; }
    public ModelManagementController modelManagement() { return modelManagement; }
    public ModelPickerTickets pickerTickets() { return pickerTickets; }
    public OperationContext context() { return context; }
    public TaskCoordinator coordinator() { return coordinator; }
    public ModelRepository modelRepository() { return modelRepository; }
    public AppState appState() { return appState; }
    public TextExportController<android.net.Uri> textExport() { return textExport; }
    public AsrOperation asrOperation() { return asrOperation; }
    public ImeController.Backend imeBackend() { return imeBackend; }
    public LogExportController<android.net.Uri> logExports() { return logExports; }
    public RuntimeLogStore runtimeLogStore() { return runtimeLogStore; }
    public RuntimeLogWorker runtimeLogWorker() { return runtimeLogWorker; }
}
