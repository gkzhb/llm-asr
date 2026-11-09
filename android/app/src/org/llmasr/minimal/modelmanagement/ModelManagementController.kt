package org.llmasr.minimal.modelmanagement

import java.io.IOException
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind
import org.llmasr.minimal.diagnostics.RuntimeLogSource
import org.llmasr.minimal.diagnostics.RuntimeLogStore
import org.llmasr.minimal.model.ModelReadiness
import org.llmasr.minimal.model.ModelReports
import org.llmasr.minimal.model.ModelRepository
import org.llmasr.minimal.model.ModelSource
import org.llmasr.minimal.model.ModelVerification
import org.llmasr.minimal.model.ModelAccess
import org.llmasr.minimal.model.ModelCatalog
import org.llmasr.minimal.model.ModelDeployment
import org.llmasr.minimal.model.ModelZipSource
import org.llmasr.minimal.task.RequestRunner
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.task.TaskKind

/** Maintenance IO stays under the caller's shared owner; observers only invalidate. */
class ModelManagementController(coordinator: TaskCoordinator?, repository: ModelRepository?,
    readiness: ModelReadiness?, control: ModelOperationControl?, state: ModelManagementState?,
    private val logStore: RuntimeLogStore?) {
    private var catalog: ModelCatalog? = null
    @Volatile private var detailKey: String = ModelDeployment.LEGACY
    @Volatile private var snapshotKey: String = ModelDeployment.LEGACY
    @Volatile private var snapshotInstallation: String? = null
    private var boundSnapshot: ModelManagementState.Snapshot? = null
    @Volatile private var importSummary: ModelManagementState.Snapshot? = null
    @Volatile private var archiveBytes: Long = -1
    @Synchronized fun importArchiveBytes(id: String?): Long = if (importSummary?.activeOperationId == id) archiveBytes else -1
    /** Page-level progress only; never per-model file/proof evidence. Retained across inspections. */
    fun importSnapshot(): ModelManagementState.Snapshot? = importSummary
    @Synchronized fun snapshotFor(key: String?): ModelManagementState.Snapshot? = if (key != null && key == snapshotKey &&
        (catalog == null || try { catalog!!.require(key).installationId == snapshotInstallation } catch (_: IOException) { false })) boundSnapshot else null
    fun snapshotMatches(key: String?): Boolean = snapshotFor(key) != null
    private fun install(snapshot: ModelManagementState.Snapshot) {
        state.install(snapshot)
        boundSnapshot = snapshot
        val active = control.peekActive()
        if (active?.kind == ModelOperationControl.Kind.IMPORT && active.id == snapshot.activeOperationId) {
            if (importSummary?.activeOperationId != active.id) archiveBytes = -1
            if (snapshot.phase == ModelManagementState.Phase.COPYING_ARCHIVE || snapshot.phase == ModelManagementState.Phase.CHECKING_ARCHIVE || snapshot.phase == ModelManagementState.Phase.CLEANING_ARCHIVE)
                archiveBytes = snapshot.fileBytesDone
            importSummary = ModelManagementState.importSummary(snapshot)
        }
    }
    fun hasCatalog(): Boolean = catalog != null
    fun bindCatalog(value: ModelCatalog) { catalog = value }
    fun catalogRows(): List<ModelCatalog.Row> = catalog?.rows() ?: emptyList()
    fun detailKey(): String = detailKey
    fun showDetail(key: String) {
        if (detailKey == key) { refreshInspect(); return }
        detailKey = key
        refreshInspect()
    }
    fun detail(): ModelDeployment? = detail(detailKey)
    fun detail(key: String?): ModelDeployment? = try { if (key == null) null else catalog?.require(key) } catch (_: IOException) { null }
    fun confirmation(): String? = confirmation(detailKey)
    fun confirmation(key: String?): String? = try { if (key == null) null else catalog?.confirmation(key) } catch (_: IOException) { null }
    private fun matches(key: String, installation: String): Boolean = detail(key)?.installationId == installation
    fun startVerify(key: String, installation: String): Boolean = matches(key, installation) && submit(ModelOperationControl.Kind.VERIFY, null, -1, targetKey = key)
    fun startRestore(key: String, installation: String): Boolean = matches(key, installation) && submit(ModelOperationControl.Kind.INSPECT, null, -1, true, key)
    fun startDeleteIdentity(key: String, installation: String, expected: String?, epoch: Long): Boolean =
        matches(key, installation) && expected != null && confirmation(key) == expected && submit(ModelOperationControl.Kind.DELETE, null, epoch, targetKey = key)
    fun startDeleteIdentity(expected: String?, confirmEpoch: Long): Boolean {
        if (catalog != null && (expected == null || confirmation() != expected)) return false
        return startDelete(confirmEpoch, true)
    }
    fun detailCachedFiles(): List<ModelReports.FileDetail> = detailCachedFiles(detailKey)
    fun detailCachedFiles(key: String?): List<ModelReports.FileDetail> {
        val model = detail(key) ?: return emptyList()
        return catalog?.rows()?.firstOrNull { it.deployment === model }?.files ?: emptyList()
    }
    fun startSelect(key: String): Boolean = submit(ModelOperationControl.Kind.SELECT, null, -1, targetKey = key)
    private var readiness: ModelReadiness
    private val control: ModelOperationControl
    private val state: ModelManagementState
    private var repository: ModelRepository
    private val runner: RequestRunner
    constructor(coordinator: TaskCoordinator?, repository: ModelRepository?, readiness: ModelReadiness?,
        control: ModelOperationControl?, state: ModelManagementState?) : this(coordinator, repository, readiness, control, state, null)
    init {
        if (repository == null || readiness == null || control == null || state == null)
            throw IllegalArgumentException("wiring required")
        this.repository = repository; this.readiness = readiness; this.control = control; this.state = state
        runner = RequestRunner(coordinator, { 0 }, object : RequestRunner.Lifecycle {}, { })
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startImport(source: ModelSource?): Boolean = source != null && submit(ModelOperationControl.Kind.IMPORT, source, -1)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startVerify(): Boolean = submit(ModelOperationControl.Kind.VERIFY, null, -1)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startDelete(confirmEpoch: Long, confirmed: Boolean): Boolean = confirmed && submit(ModelOperationControl.Kind.DELETE, null, confirmEpoch)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun refreshInspect() { submit(ModelOperationControl.Kind.INSPECT, null, -1) }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun startRestore(): Boolean = submit(ModelOperationControl.Kind.INSPECT, null, -1, true)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun state(): ModelManagementState = state
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun readiness(): ModelReadiness = readiness
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun opControl(): ModelOperationControl = control
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun requestCancel(id: String?) {
        synchronized(this) {
            if (!control.requestCancel(id)) return
            val b = builder()
            if (!id!!.equals(b.activeOperationId)) return
            b.phase = ModelManagementState.Phase.CANCELLING
            b.errorCode = "正在取消，等待文件读取/清理结束"
            install(b.build())
        }
        state.notifyListeners()
    }
    private fun submit(kind: ModelOperationControl.Kind, source: ModelSource?, confirmEpoch: Long, restore: Boolean = false, targetKey: String = detailKey): Boolean {
        val installation = try { catalog?.require(targetKey)?.installationId } catch (_: IOException) {
            if (kind != ModelOperationControl.Kind.IMPORT) return false
            null
        }
        val confirmation = if (kind == ModelOperationControl.Kind.DELETE) catalog?.confirmation(targetKey) else null
        val ref = AtomicReference<ModelOperationControl.Operation>()
        try {
            return runner.submit(TaskKind.MAINTENANCE, { runTarget(ref.get(), source, confirmEpoch, restore, targetKey, installation, confirmation) }, {
                if (kind == ModelOperationControl.Kind.DELETE && (catalog?.require(targetKey)?.readiness ?: readiness).epoch() != confirmEpoch)
                    throw IllegalStateException("模型已变化，请重新确认")
                val op = control.tryBegin(kind) ?: throw IllegalStateException("模型任务仍在收尾")
                ref.set(op)
                pending(op)
            }, { finish(ref.get()) })
        } catch (rejected: IllegalStateException) { return false }
    }
    private fun runTarget(op: ModelOperationControl.Operation?, source: ModelSource?, confirmEpoch: Long, restore: Boolean, key: String, installation: String?, confirmation: String?) {
        val models = catalog
        if (models == null) { run(op, source, confirmEpoch, restore); return }
        var prepared: ModelZipSource.PreparedPackage? = null
        val cleanup = arrayOfNulls<Boolean>(1)
        try {
            models.load()
            if (op!!.kind == ModelOperationControl.Kind.SELECT) {
                models.select(key)
                control.trySucceed(op, readiness, null)
                bindTarget(models.require(key), op)
                terminal(op, repository.inspect(ModelRepository.NEVER_CANCEL), null, true, null, null, null)
                return
            }
            if (op.kind == ModelOperationControl.Kind.DELETE && models.confirmation(key) != confirmation) throw IOException("删除确认已过期")
            var model = if (op.kind == ModelOperationControl.Kind.IMPORT) models.legacy() else models.require(key)
            if (op.kind != ModelOperationControl.Kind.IMPORT && model.installationId != installation) throw IOException("模型安装已变化，请重新操作")
            if (source is ModelZipSource) {
                prepared = source.preparePackage(model.repository.space(), object : ModelRepository.Progress {
                    override fun update(stage: ModelRepository.Stage?, file: String?, index: Int, count: Int, done: Long, total: Long, sd: Long, st: Long, reused: Long) {
                        progress(op, stage, file, index, count, done, total, sd, st, reused)
                    }
                    override fun sourceCleanup(succeeded: Boolean) { cleanup[0] = cleanup[0] != false && succeeded }
                }, object : ModelRepository.CancelGate {
                    override fun cancelled(): Boolean = control.isCancelled(op)
                    override fun reservePublish(): Boolean = control.tryReservePublish(op)
                })
                if (control.isCancelled(op)) throw ModelRepository.CancelledException()
                model = models.admit(prepared.policy)
            }
            bindTarget(model, op)
            run(op, prepared ?: source, confirmEpoch, restore, { models.deleted(model) }, cleanup)
        } catch (failure: Exception) {
            try { prepared?.close() } catch (close: Exception) {
                cleanup[0] = false
                if (close !== failure) failure.addSuppressed(close)
            }
            terminal(op, null, null, false, failure, cleanup[0], null)
        } finally { models.refresh(); state.notifyListeners() }
    }
    private fun bindTarget(model: ModelDeployment, op: ModelOperationControl.Operation) {
        synchronized(this) {
            val same = snapshotKey == model.key && snapshotInstallation == model.installationId
            val previous = state.current()
            val b = if (same) builder() else ModelManagementState.Builder(ModelManagementState.empty())
            b.inspectBusy = previous.inspectBusy
            if (op.kind != ModelOperationControl.Kind.INSPECT) {
                b.activeOperationId = op.id; b.operationKind = ModelManagementState.OperationKind.valueOf(op.kind.name)
                b.phase = ModelManagementState.Phase.INSPECTING; b.pageOwnerHeld = true
            }
            repository = model.repository; readiness = model.readiness
            snapshotKey = model.key; snapshotInstallation = model.installationId
            applyReadiness(b); install(b.build())
        }
        state.notifyListeners()
    }
    private fun run(op: ModelOperationControl.Operation?, source: ModelSource?, confirmEpoch: Long, restore: Boolean, afterDelete: (() -> Unit)? = null, sourceFacts: Array<Boolean?>? = null) {
        val token = arrayOfNulls<ModelReadiness.VerifyToken>(1)
        var proofToken: ModelVerification.Token? = null
        var sourceCleanup: Boolean? = null
        var modelCleanup: Boolean? = null
        val verification = repository.verification()
        val gate = object : ModelRepository.CancelGate {
            override fun cancelled(): Boolean = control.isCancelled(op)
            override fun reservePublish(): Boolean = control.tryReservePublish(op)
            override fun beforeMutation() { readiness.invalidate() }
            override fun invalidExisting(file: String?) { readiness.markInvalid() }
        }
        val progress = object : ModelRepository.Progress {
            override fun update(stage: ModelRepository.Stage?, file: String?, index: Int, count: Int,
                done: Long, total: Long, sd: Long, st: Long, reused: Long) {
                if (stage == ModelRepository.Stage.FINAL_VERIFY && token[0] == null && proofToken == null) {
                    if (verification == null) token[0] = readiness.beginVerify()
                    else proofToken = verification.begin(repository)
                }
                if (token[0] == null || token[0]!!.epoch() == readiness.epoch())
                    progress(op, stage, file, index, count, done, total, sd, st, reused)
            }
            override fun sourceCleanup(succeeded: Boolean) { sourceCleanup = (sourceCleanup != false) && succeeded }
            override fun modelCleanup(succeeded: Boolean) { modelCleanup = (modelCleanup != false) && succeeded }
            override fun planned(plan: ModelReports.ImportPlan?) { publishPlan(op, plan) }
            override fun fileResult(file: String?, outcome: ModelReports.FileOutcome?) { publishFileResult(op, file, outcome) }
            override fun deletion(succeeded: Int, failed: MutableList<String?>?) { publishDeletion(op, succeeded, failed) }
        }
        var failure: Throwable? = null
        var deletion: ModelRepository.DeleteReport? = null
        var success = false
        try {
            if (op!!.kind == ModelOperationControl.Kind.DELETE) {
                if (readiness.epoch() != confirmEpoch) throw IOException("模型已变化，请重新确认")
                readiness.invalidate()
                deletion = repository.deleteAll(progress)
                if (deletion.failed.isEmpty()) afterDelete?.invoke() // Catalog durability is part of delete success, not later telemetry.
            } else if (op.kind == ModelOperationControl.Kind.IMPORT) repository.importFrom(source, progress, gate)
            else if (op.kind == ModelOperationControl.Kind.VERIFY) {
                if (verification == null) token[0] = readiness.beginVerify()
                else proofToken = verification.begin(repository)
                repository.verifyAll(progress, gate)
            } else if (restore) ModelAccess(repository, readiness).restoreFast()
        } catch (e: Exception) { failure = e }
        catch (e: LinkageError) { failure = e }
        // importFrom can fail before openSession (notably cancellation after bind).
        // Close only the still-untransferred owner here, before inspect/proof/terminal.
        // PreparedPackage.close is a no-op once Repository owns the session.
        if (source is ModelZipSource.PreparedPackage) {
            try { source.close() }
            catch (close: Exception) {
                // Session may throw cancellation at its final checkpoint AFTER reporting
                // actual cleanup success. Preserve that fact; missing/false stays failure.
                if (sourceFacts?.get(0) != true) sourceCleanup = false
                if (failure == null) failure = close else if (failure !== close) failure.addSuppressed(close)
            } catch (close: LinkageError) {
                if (sourceFacts?.get(0) != true) sourceCleanup = false
                if (failure == null) failure = close else if (failure !== close) failure.addSuppressed(close)
            }
        }
        if (failure != null) {
            readiness.finishUnverified(token[0])
            if (failure is ModelRepository.InvalidModelException) readiness.markInvalid()
        }
        var report: ModelRepository.InspectReport? = null
        try { report = repository.inspect(ModelRepository.NEVER_CANCEL); readiness.inspect(report) }
        catch (e: IOException) {
            readiness.markInvalid()
            if (failure == null) failure = e else {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (failure as java.lang.Throwable).addSuppressed(e)
            }
        } catch (e: RuntimeException) {
            readiness.markInvalid()
            if (failure == null) failure = e else {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (failure as java.lang.Throwable).addSuppressed(e)
            }
        }
        if (failure == null && (deletion == null || deletion.failed.isEmpty())) {
            try {
                success = if (proofToken != null) verification!!.commit(repository, proofToken!!, object : ModelVerification.CommitGate {
                    override fun reserve(token: ModelReadiness.VerifyToken): Boolean = control.tryReserveProofCommit(op, readiness, token)
                    override fun succeed(token: ModelReadiness.VerifyToken): Boolean = control.trySucceed(op, readiness, token)
                }) else control.trySucceed(op, readiness, token[0])
                if (!success && !control.isCancelled(op)) failure = IOException("模型校验 epoch 已过期")
            } catch (e: Exception) { failure = e }
            catch (e: LinkageError) { failure = e }
        }
        if (!success) {
            readiness.finishUnverified(token[0])
            if (proofToken != null) readiness.finishUnverified(proofToken!!.epoch)
        }
        terminal(op, report, deletion, success, failure, if (sourceCleanup == false || sourceFacts?.get(0) == false) false else sourceCleanup ?: sourceFacts?.get(0), modelCleanup)
        readiness.notifyChange()
    }
    private fun pending(op: ModelOperationControl.Operation) {
        var startKind: RuntimeLogEventKind? = null
        synchronized(this) {
            if (op.kind == ModelOperationControl.Kind.INSPECT) {
                val b = builder(); b.inspectBusy = true; b.inspectError = null
                install(b.build())
            } else {
                startKind = if (op.kind == ModelOperationControl.Kind.IMPORT) RuntimeLogEventKind.MODEL_IMPORT_STARTED
                    else if (op.kind == ModelOperationControl.Kind.DELETE) RuntimeLogEventKind.MODEL_DELETE_STARTED
                    else if (op.kind == ModelOperationControl.Kind.VERIFY) RuntimeLogEventKind.MODEL_VERIFY_STARTED else null
                if (catalog != null) { snapshotKey = ""; snapshotInstallation = null }
                val b = ModelManagementState.Builder(ModelManagementState.empty())
                val prev = state.current()
                b.expectedBytes = prev.expectedBytes; b.installedBytes = prev.installedBytes
                if (catalog == null) { b.orphanPartBytes = prev.orphanPartBytes; b.availableBytes = prev.availableBytes; b.files = prev.files }
                else { b.expectedBytes = -1; b.installedBytes = -1; b.orphanPartBytes = -1; b.availableBytes = -1 }
                b.activeOperationId = op.id; b.operationKind = ModelManagementState.OperationKind.valueOf(op.kind.name)
                b.phase = ModelManagementState.Phase.INSPECTING; b.pageOwnerHeld = true
                applyReadiness(b); install(b.build())
            }
        }
        if (startKind != null) safeLog(startKind, op.id, "ok")
        state.notifyListeners()
    }
    private fun builder(): ModelManagementState.Builder = ModelManagementState.Builder(state.current())
    private fun applyReadiness(b: ModelManagementState.Builder) {
        val r = readiness.snapshot()
        b.modelEpoch = r.epoch; b.readiness = r.state; b.ready = r.ready()
    }
    private fun progress(op: ModelOperationControl.Operation?, stage: ModelRepository.Stage?, file: String?,
        index: Int, count: Int, done: Long, total: Long, sd: Long, st: Long, reused: Long) {
        synchronized(this) {
            if (control.peekActive() !== op || op!!.isTerminated || op.isSucceeded) return
            val b = builder(); applyReadiness(b)
            b.phase = if (control.isCancelled(op)) ModelManagementState.Phase.CANCELLING else ModelManagementState.Phase.valueOf(stage!!.name)
            b.fileName = file; b.fileIndex = index; b.fileCount = count
            b.fileBytesDone = done; b.fileBytesTotal = total; b.stageBytesDone = sd; b.stageBytesTotal = st
            if (stage != ModelRepository.Stage.FINAL_VERIFY) b.reusedBytes = reused
            install(b.build())
        }
        state.notifyListeners()
    }
    private fun publishPlan(op: ModelOperationControl.Operation?, plan: ModelReports.ImportPlan?) {
        synchronized(this) {
            if (control.peekActive() !== op || op!!.isTerminated) return
            val b = builder(); b.importPlan = plan; b.reusedBytes = plan!!.reusedBytes
            install(b.build())
        }
        state.notifyListeners()
    }
    private fun publishFileResult(op: ModelOperationControl.Operation?, file: String?, outcome: ModelReports.FileOutcome?) {
        synchronized(this) {
            if (control.peekActive() !== op || op!!.isTerminated) return
            val b = builder(); b.fileResults = java.util.LinkedHashMap(b.fileResults)
            b.fileResults.put(file, outcome); install(b.build())
        }
        state.notifyListeners()
    }
    private fun publishDeletion(op: ModelOperationControl.Operation?, succeeded: Int, failed: MutableList<String?>?) {
        synchronized(this) {
            if (control.peekActive() !== op || op!!.isTerminated) return
            val b = builder(); b.deleteSucceeded = succeeded; b.deleteFailed = failed!!.size
            b.failedFiles = failed; install(b.build())
        }
        state.notifyListeners()
    }
    private fun terminal(op: ModelOperationControl.Operation?, report: ModelRepository.InspectReport?,
        deletion: ModelRepository.DeleteReport?, success: Boolean, failure: Throwable?, sourceCleanup: Boolean?, modelCleanup: Boolean?) {
        val isInspect = op!!.kind == ModelOperationControl.Kind.INSPECT
        var terminalKind: RuntimeLogEventKind? = null
        var terminalDetail: String? = null
        synchronized(this) {
            val b = builder(); applyReadiness(b)
            if (report != null) {
                b.expectedBytes = report.expectedBytes; b.installedBytes = report.installedBytes
                b.orphanPartBytes = report.orphanPartBytes; b.availableBytes = report.availableBytes
                b.files = ArrayList()
                for (f in report.files) b.files.add(ModelReports.FileDetail(f.name, f.expectedBytes, f.actualBytes,
                    f.present, f.sizeMatch, f.partExists, b.ready))
                if (!isInspect) b.fileCount = report.files.size
                b.unexpectedFiles = report.unexpected
            } else {
                b.installedBytes = -1; b.orphanPartBytes = -1; b.availableBytes = -1; b.files = Collections.emptyList()
            }
            control.decideTerminal(op)
            if (isInspect) {
                b.inspectError = if (failure == null) null else "内部概况读取失败，请检查模型存储后重试"
                install(b.build())
            } else {
                b.phase = if (success) ModelManagementState.Phase.SUCCEEDED else if (control.isCancelled(op))
                    ModelManagementState.Phase.CANCELLED else ModelManagementState.Phase.FAILED
                b.lastOutcome = ModelManagementState.Outcome.valueOf(b.phase.name)
                b.errorCode = failure?.message
                b.cleanupOutcome = if ((report == null && !(catalog != null && snapshotKey.isEmpty() && sourceCleanup == true)) || (report != null && (report.orphanPartBytes > 0 || report.files.any { it.partExists })) ||
                    sourceCleanup == false || modelCleanup == false || (sourceCleanup == null && failure != null && failure.suppressed.isNotEmpty()))
                    ModelManagementState.Outcome.FAILED else ModelManagementState.Outcome.SUCCEEDED
                if (failure is ModelRepository.ModelFileException) {
                    val file = failure.file
                    b.failedFiles = Collections.singletonList(file)
                    b.fileResults = java.util.LinkedHashMap(b.fileResults)
                    b.fileResults.put(file, ModelReports.FileOutcome(ModelReports.FileOutcome.Status.FAILED, failure.failure))
                }
                if (deletion != null) {
                    b.deleteSucceeded = deletion.succeeded(); b.deleteFailed = deletion.failed.size
                    b.deleteFreedBytes = deletion.freedBytes; b.failedFiles = deletion.failed; b.unexpectedFiles = deletion.unexpected
                    if (deletion.failed.isNotEmpty()) b.errorCode = "部分删除"
                }
                install(b.build())
                if (logStore != null) {
                    if (b.phase == ModelManagementState.Phase.SUCCEEDED) {
                        terminalKind = if (op.kind == ModelOperationControl.Kind.IMPORT) RuntimeLogEventKind.MODEL_IMPORT_COMPLETED
                            else if (op.kind == ModelOperationControl.Kind.DELETE) RuntimeLogEventKind.MODEL_DELETE_COMPLETED
                            else if (op.kind == ModelOperationControl.Kind.VERIFY) RuntimeLogEventKind.MODEL_VERIFY_COMPLETED else null
                    } else if (b.phase == ModelManagementState.Phase.CANCELLED) {
                        terminalKind = if (op.kind == ModelOperationControl.Kind.IMPORT) RuntimeLogEventKind.MODEL_IMPORT_CANCELLED
                            else if (op.kind == ModelOperationControl.Kind.DELETE) RuntimeLogEventKind.MODEL_DELETE_CANCELLED
                            else if (op.kind == ModelOperationControl.Kind.VERIFY) RuntimeLogEventKind.MODEL_VERIFY_CANCELLED else null
                    } else if (b.phase == ModelManagementState.Phase.FAILED) {
                        terminalKind = if (op.kind == ModelOperationControl.Kind.IMPORT) RuntimeLogEventKind.MODEL_IMPORT_FAILED
                            else if (op.kind == ModelOperationControl.Kind.DELETE) RuntimeLogEventKind.MODEL_DELETE_FAILED
                            else if (op.kind == ModelOperationControl.Kind.VERIFY) RuntimeLogEventKind.MODEL_VERIFY_FAILED else null
                    }
                    if (terminalKind != null) terminalDetail = if (b.phase == ModelManagementState.Phase.SUCCEEDED) "ok"
                        else if (b.phase == ModelManagementState.Phase.CANCELLED) "cancelled" else "failed"
                }
            }
        }
        if (terminalKind != null) safeLog(terminalKind, op.id, terminalDetail)
        state.notifyListeners()
    }
    private fun safeLog(kind: RuntimeLogEventKind?, id: String?, detail: String?) {
        if (logStore == null) return
        try { logStore.append(kind, RuntimeLogSource.SHARED, id, detail) }
        catch (ignored: Throwable) { /* Optional telemetry never owns model state/release. */ }
    }
    private fun finish(op: ModelOperationControl.Operation?) {
        if (op == null) return
        val succeeded: Boolean
        var fallbackKind: RuntimeLogEventKind? = null
        synchronized(this) {
            val b = builder()
            if (op.kind == ModelOperationControl.Kind.INSPECT) {
                if (control.peekActive() !== op) return
                if (!op.isTerminated) b.inspectError = "概况检查未启动或异常终止"
                b.inspectBusy = false
            } else {
                if (!op.id.equals(b.activeOperationId)) return
                if (!state.current().isTerminalPhase) {
                    b.phase = ModelManagementState.Phase.FAILED; b.lastOutcome = ModelManagementState.Outcome.FAILED
                    b.errorCode = "任务未启动或异常终止"
                    fallbackKind = if (op.kind == ModelOperationControl.Kind.IMPORT) RuntimeLogEventKind.MODEL_IMPORT_FAILED
                        else if (op.kind == ModelOperationControl.Kind.DELETE) RuntimeLogEventKind.MODEL_DELETE_FAILED
                        else if (op.kind == ModelOperationControl.Kind.VERIFY) RuntimeLogEventKind.MODEL_VERIFY_FAILED else null
                }
                b.pageOwnerHeld = false
            }
            control.decideTerminal(op)
            install(b.build())
            succeeded = op.isSucceeded
        }
        try {
            if (fallbackKind != null) safeLog(fallbackKind, op.id, "failed")
            state.notifyListeners()
        } finally { control.complete(op, succeeded) }
    }
}
