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
import org.llmasr.minimal.task.RequestRunner
import org.llmasr.minimal.task.TaskCoordinator
import org.llmasr.minimal.task.TaskKind

/** Maintenance IO stays under the caller's shared owner; observers only invalidate. */
class ModelManagementController(coordinator: TaskCoordinator?, repository: ModelRepository?,
    readiness: ModelReadiness?, control: ModelOperationControl?, state: ModelManagementState?,
    private val logStore: RuntimeLogStore?) {
    private val readiness: ModelReadiness
    private val control: ModelOperationControl
    private val state: ModelManagementState
    private val repository: ModelRepository
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
            state.install(b.build())
        }
        state.notifyListeners()
    }
    private fun submit(kind: ModelOperationControl.Kind, source: ModelSource?, confirmEpoch: Long): Boolean {
        val ref = AtomicReference<ModelOperationControl.Operation>()
        try {
            return runner.submit(TaskKind.MAINTENANCE, { run(ref.get(), source, confirmEpoch) }, {
                if (kind == ModelOperationControl.Kind.DELETE && readiness.epoch() != confirmEpoch)
                    throw IllegalStateException("模型已变化，请重新确认")
                val op = control.tryBegin(kind) ?: throw IllegalStateException("模型任务仍在收尾")
                ref.set(op)
                pending(op)
            }, { finish(ref.get()) })
        } catch (rejected: IllegalStateException) { return false }
    }
    private fun run(op: ModelOperationControl.Operation?, source: ModelSource?, confirmEpoch: Long) {
        val token = arrayOfNulls<ModelReadiness.VerifyToken>(1)
        val gate = object : ModelRepository.CancelGate {
            override fun cancelled(): Boolean = control.isCancelled(op)
            override fun reservePublish(): Boolean = control.tryReservePublish(op)
            override fun beforeMutation() { readiness.invalidate() }
            override fun invalidExisting(file: String?) { readiness.markInvalid() }
        }
        val progress = object : ModelRepository.Progress {
            override fun update(stage: ModelRepository.Stage?, file: String?, index: Int, count: Int,
                done: Long, total: Long, sd: Long, st: Long, reused: Long) {
                if (stage == ModelRepository.Stage.FINAL_VERIFY && token[0] == null) token[0] = readiness.beginVerify()
                if (token[0] == null || token[0]!!.epoch() == readiness.epoch())
                    progress(op, stage, file, index, count, done, total, sd, st, reused)
            }
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
            } else if (op.kind == ModelOperationControl.Kind.IMPORT) repository.importFrom(source, progress, gate)
            else if (op.kind == ModelOperationControl.Kind.VERIFY) {
                token[0] = readiness.beginVerify()
                repository.verifyAll(progress, gate)
            }
        } catch (e: Exception) { failure = e }
        catch (e: LinkageError) { failure = e }
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
            success = control.trySucceed(op, readiness, token[0])
            if (!success && !control.isCancelled(op)) failure = IOException("模型校验 epoch 已过期")
        }
        if (!success) readiness.finishUnverified(token[0])
        terminal(op, report, deletion, success, failure)
        readiness.notifyChange()
    }
    private fun pending(op: ModelOperationControl.Operation) {
        var startKind: RuntimeLogEventKind? = null
        synchronized(this) {
            if (op.kind == ModelOperationControl.Kind.INSPECT) {
                val b = builder(); b.inspectBusy = true; b.inspectError = null
                state.install(b.build())
            } else {
                startKind = if (op.kind == ModelOperationControl.Kind.IMPORT) RuntimeLogEventKind.MODEL_IMPORT_STARTED
                    else if (op.kind == ModelOperationControl.Kind.DELETE) RuntimeLogEventKind.MODEL_DELETE_STARTED
                    else RuntimeLogEventKind.MODEL_VERIFY_STARTED
                val b = ModelManagementState.Builder(ModelManagementState.empty())
                val prev = state.current()
                b.expectedBytes = prev.expectedBytes; b.installedBytes = prev.installedBytes
                b.orphanPartBytes = prev.orphanPartBytes; b.availableBytes = prev.availableBytes; b.files = prev.files
                b.activeOperationId = op.id; b.operationKind = ModelManagementState.OperationKind.valueOf(op.kind.name)
                b.phase = ModelManagementState.Phase.INSPECTING; b.pageOwnerHeld = true
                applyReadiness(b); state.install(b.build())
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
            state.install(b.build())
        }
        state.notifyListeners()
    }
    private fun publishPlan(op: ModelOperationControl.Operation?, plan: ModelReports.ImportPlan?) {
        synchronized(this) {
            if (control.peekActive() !== op || op!!.isTerminated) return
            val b = builder(); b.importPlan = plan; b.reusedBytes = plan!!.reusedBytes
            state.install(b.build())
        }
        state.notifyListeners()
    }
    private fun publishFileResult(op: ModelOperationControl.Operation?, file: String?, outcome: ModelReports.FileOutcome?) {
        synchronized(this) {
            if (control.peekActive() !== op || op!!.isTerminated) return
            val b = builder(); b.fileResults = java.util.LinkedHashMap(b.fileResults)
            b.fileResults.put(file, outcome); state.install(b.build())
        }
        state.notifyListeners()
    }
    private fun publishDeletion(op: ModelOperationControl.Operation?, succeeded: Int, failed: MutableList<String?>?) {
        synchronized(this) {
            if (control.peekActive() !== op || op!!.isTerminated) return
            val b = builder(); b.deleteSucceeded = succeeded; b.deleteFailed = failed!!.size
            b.failedFiles = failed; state.install(b.build())
        }
        state.notifyListeners()
    }
    private fun terminal(op: ModelOperationControl.Operation?, report: ModelRepository.InspectReport?,
        deletion: ModelRepository.DeleteReport?, success: Boolean, failure: Throwable?) {
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
                state.install(b.build())
            } else {
                b.phase = if (success) ModelManagementState.Phase.SUCCEEDED else if (control.isCancelled(op))
                    ModelManagementState.Phase.CANCELLED else ModelManagementState.Phase.FAILED
                b.lastOutcome = ModelManagementState.Outcome.valueOf(b.phase.name)
                b.errorCode = failure?.message
                b.cleanupOutcome = if (report == null || report.orphanPartBytes > 0 || (failure != null && failure.suppressed.isNotEmpty()))
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
                state.install(b.build())
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
                        else RuntimeLogEventKind.MODEL_VERIFY_FAILED
                }
                b.pageOwnerHeld = false
            }
            control.decideTerminal(op)
            state.install(b.build())
            succeeded = op.isSucceeded
        }
        try {
            if (fallbackKind != null) safeLog(fallbackKind, op.id, "failed")
            state.notifyListeners()
        } finally { control.complete(op, succeeded) }
    }
}
