package org.llmasr.minimal

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import org.json.JSONArray
import org.json.JSONObject
import org.llmasr.minimal.asr.InferenceAdapter
import org.llmasr.minimal.asr.JniNativeTranscription
import org.llmasr.minimal.asr.LoggingInferencePhaseListener
import org.llmasr.minimal.diagnostics.LogExportController
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind
import org.llmasr.minimal.diagnostics.RuntimeLogPersistence
import org.llmasr.minimal.diagnostics.RuntimeLogSource
import org.llmasr.minimal.diagnostics.RuntimeLogStore
import org.llmasr.minimal.diagnostics.RuntimeLogWorker
import org.llmasr.minimal.ime.ImeBackend
import org.llmasr.minimal.ime.ImeController
import org.llmasr.minimal.model.FixedModelPackage
import org.llmasr.minimal.model.SafModelZipSource
import org.llmasr.minimal.model.ModelSource
import org.llmasr.minimal.model.ModelAccess
import org.llmasr.minimal.model.ModelCatalog
import org.llmasr.minimal.model.ModelResidency
import org.llmasr.minimal.model.ModelVerification
import org.llmasr.minimal.model.AndroidModelVerificationIo
import org.llmasr.minimal.model.ModelEntry
import org.llmasr.minimal.model.ModelManifest
import org.llmasr.minimal.model.ModelReadiness
import org.llmasr.minimal.model.ModelRepository
import org.llmasr.minimal.modelmanagement.ModelManagementController
import org.llmasr.minimal.modelmanagement.ModelManagementState
import org.llmasr.minimal.modelmanagement.ModelOperationControl
import org.llmasr.minimal.modelmanagement.ModelPickerTickets
import org.llmasr.minimal.platform.OperationContext
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.transcription.AppReportWriter
import org.llmasr.minimal.transcription.AppState
import org.llmasr.minimal.transcription.AsrOperation
import org.llmasr.minimal.transcription.TextExportController

/** Process-singleton application-context wiring. Publish only after startup admission. */
class AppGraph private constructor(private val context: OperationContext?, private val coordinator: TaskCoordinator?,
    private val modelRepository: ModelRepository?, private val appState: AppState?,
    private val textExport: TextExportController<android.net.Uri>?, private val asrOperation: AsrOperation?,
    private val readiness: ModelReadiness?, private val modelAccess: ModelAccess?, private val catalog: ModelCatalog?,
    private val runtimeLogStore: RuntimeLogStore?, private val runtimeLogWorker: RuntimeLogWorker?,
    inference: InferenceAdapter?) {
    private val modelState = ModelManagementState()
    private val modelControl = ModelOperationControl()
    private val modelManagement: ModelManagementController
    private val pickerTickets = ModelPickerTickets()
    private val imeBackend: ImeBackend
    private val logExports: LogExportController<android.net.Uri>
    init {
        imeBackend = ImeBackend(context, modelRepository, inference, modelAccess!!)
        logExports = LogExportController { target -> context!!.contentResolver()!!.openOutputStream(unchanged<android.net.Uri>(target), "wt") }
        modelManagement = ModelManagementController(coordinator, modelRepository, readiness, modelControl, modelState, runtimeLogStore)
        if (catalog != null) modelManagement.bindCatalog(catalog)
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun readiness(): ModelReadiness? = readiness
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun modelAccess(): ModelAccess? = modelAccess
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun modelManagement(): ModelManagementController = modelManagement
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun pickerTickets(): ModelPickerTickets = pickerTickets
    // Suppliers run only after worker admission, preserving synthetic no-ZIP Graph fixtures and UI no-IO.
    fun modelZipSource(uri: android.net.Uri): ModelSource = SafModelZipSource(context!!.contentResolver(), uri,
        java.util.function.Supplier { context.filesDir()!! },
        java.util.function.Supplier { FixedModelPackage.mnn(modelRepository!!.manifest()) })
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun context(): OperationContext? = context
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun coordinator(): TaskCoordinator? = coordinator
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun modelRepository(): ModelRepository? = modelRepository
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun appState(): AppState? = appState
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun textExport(): TextExportController<android.net.Uri>? = textExport
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun asrOperation(): AsrOperation? = asrOperation
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun imeBackend(): ImeController.Backend = imeBackend
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun logExports(): LogExportController<android.net.Uri> = logExports
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun runtimeLogStore(): RuntimeLogStore? = runtimeLogStore
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open fun runtimeLogWorker(): RuntimeLogWorker? = runtimeLogWorker
    companion object {
        // Preserve Java null forwarding across SDK annotations; scoped bytecode/receiver regression evidence.
        @Suppress("UNCHECKED_CAST") private fun <T> unchanged(value: Any?): T = value as T
        @Volatile private var INSTANCE: AppGraph? = null
        @JvmStatic @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        open fun get(): AppGraph {
            val g = INSTANCE
            if (g == null) throw IllegalStateException("AppGraph not installed")
            return g
        }
        @JvmStatic @Throws(IOException::class) @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        open fun install(appContext: Context?) {
            if (appContext == null) throw IllegalArgumentException("appContext required")
            if (INSTANCE != null) return
            synchronized(AppGraph::class.java) {
                if (INSTANCE != null) return
                val opCtx: OperationContext = OperationContext.Android(appContext)
                val manifest = parseManifest(opCtx)
                val readiness = ModelReadiness()
                val state = AppState()
                val coordinator = TaskCoordinator()
                val textExport = TextExportController<android.net.Uri> { target -> opCtx.contentResolver()!!.openOutputStream(unchanged<android.net.Uri>(target), "wt") }
                val space = ModelRepository.SpaceProvider { opCtx.filesDir()!!.usableSpace }
                val app = appContext.applicationContext
                val logStore = RuntimeLogStore()
                val bridge = JniNativeTranscription()
                var nativeLibraryLoaded = false // accessed only by the shared worker
                val resident = ModelResidency(object : ModelResidency.Native {
                    override fun prepare(config: String, cache: String): Double {
                        System.loadLibrary("qwen_asr_jni")
                        nativeLibraryLoaded = true
                        val phases = LoggingInferencePhaseListener.prepareListener(logStore)
                        try { return bridge.prepare(config, cache, phases).also { phases.finished(true) } }
                        catch (failure: Throwable) { phases.finished(false); throw failure }
                    }
                    override fun release() { if (nativeLibraryLoaded) bridge.release() }
                }, { Thread.currentThread().name == "asr-task-worker" })
                val catalog = ModelCatalog(FixedModelPackage.mnn(manifest), opCtx.filesDir()!!, opCtx.cacheDir()!!, space, resident,
                    AndroidModelVerificationIo.Catalog { app.noBackupFilesDir }, readiness)
                val repository = catalog.legacy().repository
                val access = ModelAccess.forCatalog(catalog, resident)
                val logPersistence = RuntimeLogPersistence(opCtx.filesDir())
                val logFailure = RuntimeLogPersistence.FailureSink { reason -> logStore.append(RuntimeLogEventKind.LOG_PERSISTENCE_FAILED, RuntimeLogSource.SHARED, null, reason) }
                val logWorker = RuntimeLogWorker(logStore, logPersistence, logFailure)
                val inference = InferenceAdapter(logStore, access::requireResidentReady, null, bridge, object : InferenceAdapter.Session {
                    override fun enter(): Any = access.enterRequest()
                    override fun finish(token: Any, success: Boolean) { access.finishRequest(token, success) }
                })
                val ops = AsrOperation(opCtx, coordinator, repository, state, textExport, inference, AppReportWriter(opCtx), access)
                val built = AppGraph(opCtx, coordinator, repository, state, textExport, ops, readiness, access, catalog, logStore, logWorker, inference)
                app.registerComponentCallbacks(object : ComponentCallbacks2 {
                    override fun onConfigurationChanged(configuration: Configuration) {}
                    override fun onLowMemory() { ops.startRelease() }
                    override fun onTrimMemory(level: Int) {
                        // UI_HIDDEN(20) is not memory pressure and must not unload.
                        if (level == 5 || level == 10 || level == 15 || level == 40 || level == 60 || level == 80) ops.startRelease()
                    }
                })
                built.asrOperation!!.startupOnce()
                logStore.append(RuntimeLogEventKind.APP_STARTUP, RuntimeLogSource.SHARED, null, "ok")
                logStore.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok")
                INSTANCE = built
            }
        }
        @Throws(IOException::class)
        private fun parseManifest(opCtx: OperationContext): ModelManifest {
            val entries = ArrayList<ModelEntry>()
            val buf = ByteArray(8192)
            try {
                val input = opCtx.openAsset("model-manifest.json")
                var primary: Throwable? = null
                try {
                    val out = java.io.ByteArrayOutputStream()
                    while (true) {
                        val n = input!!.read(buf)
                        if (n == -1) break
                        if (out.size() + n > 1024 * 1024) throw IOException("模型清单过大")
                        out.write(buf, 0, n)
                    }
                    val root = JSONObject(String(out.toByteArray(), StandardCharsets.UTF_8))
                    val arr: JSONArray = root.getJSONArray("files")
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        val name = e.getString("file")
                        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw IOException("清单条目包含路径分隔符：" + name)
                        entries.add(ModelEntry(name, e.getLong("bytes"), e.getString("sha256")))
                    }
                } catch (failure: Throwable) { primary = failure; throw failure }
                finally {
                    if (input != null) {
                        if (primary == null) input.close()
                        else try { input.close() } catch (secondary: Throwable) {
                            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                            (primary as java.lang.Throwable).addSuppressed(secondary)
                        }
                    }
                }
            } catch (je: org.json.JSONException) { throw IOException("无法解析模型清单：" + je.message) }
            return ModelManifest(entries)
        }
    }
}
