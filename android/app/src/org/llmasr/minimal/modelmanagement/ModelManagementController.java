package org.llmasr.minimal.modelmanagement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelReports;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;

/** Pure-Java maintenance lane. Uses the caller's shared runner owner/executor,
 * its own lifecycle policy. All IO, including
 * terminal inspection, stays inside that owner. Observe coordinator changes
 * and read state(); notification is an invalidation, not a stale UI payload.
 */
public final class ModelManagementController {
    private final ModelReadiness readiness;
    private final ModelOperationControl control;
    private final ModelManagementState state;
    private final ModelRepository repository;
    private final RequestRunner runner;
    private final RuntimeLogStore logStore;

    public ModelManagementController(TaskCoordinator coordinator, ModelRepository repository,
            ModelReadiness readiness, ModelOperationControl control, ModelManagementState state) {
        this(coordinator, repository, readiness, control, state, null);
    }
    public ModelManagementController(TaskCoordinator coordinator, ModelRepository repository,
            ModelReadiness readiness, ModelOperationControl control, ModelManagementState state,
            RuntimeLogStore logStore) {
        if (repository == null || readiness == null || control == null || state == null)
            throw new IllegalArgumentException("wiring required");
        this.repository=repository; this.readiness=readiness; this.control=control; this.state=state;
        this.logStore=logStore;
        // Model body publishes its own terminal state; finish handles abnormal unwind/rejection.
        runner = new RequestRunner(coordinator, () -> 0, new RequestRunner.Lifecycle() {}, ctx -> {});
    }
    public boolean startImport(ModelSource source) {
        return source != null && submit(ModelOperationControl.Kind.IMPORT, source, -1);
    }
    public boolean startVerify() { return submit(ModelOperationControl.Kind.VERIFY, null, -1); }
    public boolean startDelete(long confirmEpoch, boolean confirmed) {
        return confirmed && submit(ModelOperationControl.Kind.DELETE, null, confirmEpoch);
    }
    public void refreshInspect() { submit(ModelOperationControl.Kind.INSPECT, null, -1); }
    public ModelManagementState state() { return state; }
    public ModelReadiness readiness() { return readiness; }
    public ModelOperationControl opControl() { return control; }

    public void requestCancel(String id) {
        ModelManagementState.Snapshot installed;
        synchronized (this) {
            if (!control.requestCancel(id)) return;
            ModelManagementState.Builder b = builder();
            if (!id.equals(b.activeOperationId)) return;
            b.phase = ModelManagementState.Phase.CANCELLING;
            b.errorCode = "正在取消，等待文件读取/清理结束";
            installed = b.build();
            state.install(installed);
        }
        state.notifyListeners();
    }

    private boolean submit(ModelOperationControl.Kind kind, ModelSource source, long confirmEpoch) {
        AtomicReference<ModelOperationControl.Operation> ref = new AtomicReference<>();
        try {
            return runner.submit(TaskKind.MAINTENANCE, ctx -> run(ref.get(), source, confirmEpoch), () -> {
                // Shared owner was acquired before creating the cancel identity.
                if (kind == ModelOperationControl.Kind.DELETE && readiness.epoch() != confirmEpoch)
                    throw new IllegalStateException("模型已变化，请重新确认");
                ModelOperationControl.Operation op = control.tryBegin(kind);
                if (op == null) throw new IllegalStateException("模型任务仍在收尾");
                ref.set(op);
                pending(op);
            }, () -> finish(ref.get()));
        } catch (IllegalStateException rejected) { return false; }
    }

    private void run(ModelOperationControl.Operation op, ModelSource source, long confirmEpoch) {
        final ModelReadiness.VerifyToken[] token = {null};
        ModelRepository.CancelGate gate = new ModelRepository.CancelGate() {
            public boolean cancelled() { return control.isCancelled(op); }
            public boolean reservePublish() { return control.tryReservePublish(op); }
            public void beforeMutation() { readiness.invalidate(); }
            public void invalidExisting(String file) { readiness.markInvalid(); }
        };
        ModelRepository.Progress progress = new ModelRepository.Progress() {
          public void update(ModelRepository.Stage stage, String file, int index, int count,
                  long done, long total, long sd, long st, long reused) {
            // Acquire the proof BEFORE the first final hash, never after IO.
            if (stage == ModelRepository.Stage.FINAL_VERIFY && token[0] == null)
                token[0] = readiness.beginVerify();
            if (token[0] == null || token[0].epoch() == readiness.epoch())
                progress(op, stage, file, index, count, done, total, sd, st, reused);
          }
          public void planned(ModelReports.ImportPlan plan) { publishPlan(op, plan); }
          public void fileResult(String file, ModelReports.FileOutcome outcome) { publishFileResult(op, file, outcome); }
          public void deletion(int succeeded, java.util.List<String> failed) { publishDeletion(op, succeeded, failed); }
        };
        Throwable failure = null;
        ModelRepository.DeleteReport deletion = null;
        boolean success = false;
        try {
            if (op.kind == ModelOperationControl.Kind.DELETE) {
                if (readiness.epoch() != confirmEpoch) throw new IOException("模型已变化，请重新确认");
                readiness.invalidate(); // BEFORE any deletion
                deletion = repository.deleteAll(progress);
            } else if (op.kind == ModelOperationControl.Kind.IMPORT) {
                repository.importFrom(source, progress, gate);
            } else if (op.kind == ModelOperationControl.Kind.VERIFY) {
                token[0] = readiness.beginVerify();
                repository.verifyAll(progress, gate);
            }
        } catch (Exception | LinkageError e) { failure = e; }
        if (failure != null) {
            readiness.finishUnverified(token[0]);
            if (failure instanceof ModelRepository.InvalidModelException) readiness.markInvalid();
        }
        // Inspect actual leftovers/space even on cancellation, with cancellation disabled.
        ModelRepository.InspectReport report = null;
        try { report = repository.inspect(ModelRepository.NEVER_CANCEL); readiness.inspect(report); }
        catch (IOException | RuntimeException e) {
            readiness.markInvalid();
            if (failure == null) failure = e; else failure.addSuppressed(e);
        }
        if (failure == null && (deletion == null || deletion.failed.isEmpty())) {
            success = control.trySucceed(op, readiness, token[0]);
            if (!success && !control.isCancelled(op)) failure = new IOException("模型校验 epoch 已过期");
        }
        if (!success) readiness.finishUnverified(token[0]);
        terminal(op, report, deletion, success, failure);
        readiness.notifyChange();
    }

    private void pending(ModelOperationControl.Operation op) {
        RuntimeLogEventKind startKind = null;
        synchronized (this) {
            if (op.kind == ModelOperationControl.Kind.INSPECT) {
                ModelManagementState.Builder b=builder(); b.inspectBusy=true; b.inspectError=null;
                state.install(b.build());
            } else {
                startKind = op.kind == ModelOperationControl.Kind.IMPORT
                    ? RuntimeLogEventKind.MODEL_IMPORT_STARTED
                    : op.kind == ModelOperationControl.Kind.DELETE
                        ? RuntimeLogEventKind.MODEL_DELETE_STARTED : RuntimeLogEventKind.MODEL_VERIFY_STARTED;
                ModelManagementState.Builder b = new ModelManagementState.Builder(ModelManagementState.empty());
                ModelManagementState.Snapshot prev = state.current();
                b.expectedBytes=prev.expectedBytes; b.installedBytes=prev.installedBytes;
                b.orphanPartBytes=prev.orphanPartBytes; b.availableBytes=prev.availableBytes; b.files=prev.files;
                b.activeOperationId=op.id; b.operationKind=ModelManagementState.OperationKind.valueOf(op.kind.name());
                b.phase=ModelManagementState.Phase.INSPECTING; b.pageOwnerHeld=true;
                applyReadiness(b); state.install(b.build());
            }
        }
        state.notifyListeners();
        if (startKind != null) safeLog(startKind, op.id, "ok");
    }
    private ModelManagementState.Builder builder() { return new ModelManagementState.Builder(state.current()); }
    private void applyReadiness(ModelManagementState.Builder b) {
        ModelReadiness.Snapshot r = readiness.snapshot();
        b.modelEpoch=r.epoch; b.readiness=r.state; b.ready=r.ready();
    }
    private void progress(ModelOperationControl.Operation op, ModelRepository.Stage stage,
            String file, int index, int count, long done, long total, long sd, long st, long reused) {
        ModelManagementState.Snapshot installed = null;
        synchronized (this) {
            if (control.peekActive() != op || op.isTerminated() || op.isSucceeded()) return;
            ModelManagementState.Builder b=builder(); applyReadiness(b);
            b.phase=control.isCancelled(op) ? ModelManagementState.Phase.CANCELLING
                : ModelManagementState.Phase.valueOf(stage.name());
            b.fileName=file; b.fileIndex=index; b.fileCount=count;
            b.fileBytesDone=done; b.fileBytesTotal=total; b.stageBytesDone=sd; b.stageBytesTotal=st;
            if (stage != ModelRepository.Stage.FINAL_VERIFY) b.reusedBytes=reused;
            installed = b.build();
            state.install(installed);
        }
        if (installed != null) state.notifyListeners();
    }
    private void publishPlan(ModelOperationControl.Operation op, ModelReports.ImportPlan plan) {
        ModelManagementState.Snapshot installed = null;
        synchronized (this) {
            if (control.peekActive() != op || op.isTerminated()) return;
            ModelManagementState.Builder b=builder(); b.importPlan=plan; b.reusedBytes=plan.reusedBytes;
            installed = b.build();
            state.install(installed);
        }
        if (installed != null) state.notifyListeners();
    }
    private void publishFileResult(ModelOperationControl.Operation op, String file, ModelReports.FileOutcome outcome) {
        ModelManagementState.Snapshot installed = null;
        synchronized (this) {
            if (control.peekActive() != op || op.isTerminated()) return;
            ModelManagementState.Builder b=builder(); b.fileResults=new java.util.LinkedHashMap<>(b.fileResults);
            b.fileResults.put(file, outcome); installed = b.build();
            state.install(installed);
        }
        if (installed != null) state.notifyListeners();
    }
    private void publishDeletion(ModelOperationControl.Operation op, int succeeded, java.util.List<String> failed) {
        ModelManagementState.Snapshot installed = null;
        synchronized (this) {
            if (control.peekActive() != op || op.isTerminated()) return;
            ModelManagementState.Builder b=builder(); b.deleteSucceeded=succeeded; b.deleteFailed=failed.size();
            b.failedFiles=failed; installed = b.build();
            state.install(installed);
        }
        if (installed != null) state.notifyListeners();
    }
    private void terminal(ModelOperationControl.Operation op, ModelRepository.InspectReport report,
            ModelRepository.DeleteReport deletion, boolean success, Throwable failure) {
        ModelManagementState.Snapshot installed = null;
        boolean isInspect = op.kind == ModelOperationControl.Kind.INSPECT;
        RuntimeLogEventKind terminalKind = null;
        String terminalDetail = null;
        synchronized (this) {
            ModelManagementState.Builder b=builder(); applyReadiness(b);
            if (report != null) {
                b.expectedBytes=report.expectedBytes; b.installedBytes=report.installedBytes;
                b.orphanPartBytes=report.orphanPartBytes; b.availableBytes=report.availableBytes;
                b.files=new ArrayList<>();
                for (ModelReports.FileDetail f : report.files) b.files.add(new ModelReports.FileDetail(
                    f.name, f.expectedBytes, f.actualBytes, f.present, f.sizeMatch, f.partExists, b.ready));
                if (!isInspect) b.fileCount=report.files.size();
                b.unexpectedFiles=report.unexpected;
            } else {
                b.installedBytes=-1; b.orphanPartBytes=-1; b.availableBytes=-1; b.files=Collections.emptyList();
            }
            // Decide every terminal result before callbacks; retain the slot until finish().
            control.decideTerminal(op);
            if (isInspect) {
                b.inspectError=failure == null ? null : "内部概况读取失败，请检查模型存储后重试";
                installed = b.build();
                state.install(installed);
            } else {
            b.phase=success ? ModelManagementState.Phase.SUCCEEDED : control.isCancelled(op)
                ? ModelManagementState.Phase.CANCELLED : ModelManagementState.Phase.FAILED;
            b.lastOutcome=ModelManagementState.Outcome.valueOf(b.phase.name());
            b.errorCode=failure == null ? null : failure.getMessage();
            b.cleanupOutcome=report == null || report.orphanPartBytes > 0 || (failure != null && failure.getSuppressed().length > 0)
                ? ModelManagementState.Outcome.FAILED : ModelManagementState.Outcome.SUCCEEDED;
            if (failure instanceof ModelRepository.ModelFileException) {
                String file=((ModelRepository.ModelFileException)failure).file;
                b.failedFiles=Collections.singletonList(file);
                b.fileResults=new java.util.LinkedHashMap<>(b.fileResults);
                b.fileResults.put(file, new ModelReports.FileOutcome(
                    ModelReports.FileOutcome.Status.FAILED, ((ModelRepository.ModelFileException)failure).failure));
            }
            if (deletion != null) {
                b.deleteSucceeded=deletion.succeeded(); b.deleteFailed=deletion.failed.size();
                b.deleteFreedBytes=deletion.freedBytes; b.failedFiles=deletion.failed;
                b.unexpectedFiles=deletion.unexpected;
                if (!deletion.failed.isEmpty()) b.errorCode="部分删除";
            }
            // Still held until runner finalization; listeners consult the shared coordinator.
            installed = b.build();
            state.install(installed);
            if (logStore != null) {
                if (b.phase == ModelManagementState.Phase.SUCCEEDED) {
                    terminalKind = op.kind == ModelOperationControl.Kind.IMPORT ? RuntimeLogEventKind.MODEL_IMPORT_COMPLETED
                        : op.kind == ModelOperationControl.Kind.DELETE ? RuntimeLogEventKind.MODEL_DELETE_COMPLETED
                        : op.kind == ModelOperationControl.Kind.VERIFY ? RuntimeLogEventKind.MODEL_VERIFY_COMPLETED : null;
                } else if (b.phase == ModelManagementState.Phase.CANCELLED) {
                    terminalKind = op.kind == ModelOperationControl.Kind.IMPORT ? RuntimeLogEventKind.MODEL_IMPORT_CANCELLED
                        : op.kind == ModelOperationControl.Kind.DELETE ? RuntimeLogEventKind.MODEL_DELETE_CANCELLED
                        : op.kind == ModelOperationControl.Kind.VERIFY ? RuntimeLogEventKind.MODEL_VERIFY_CANCELLED : null;
                } else if (b.phase == ModelManagementState.Phase.FAILED) {
                    terminalKind = op.kind == ModelOperationControl.Kind.IMPORT ? RuntimeLogEventKind.MODEL_IMPORT_FAILED
                        : op.kind == ModelOperationControl.Kind.DELETE ? RuntimeLogEventKind.MODEL_DELETE_FAILED
                        : op.kind == ModelOperationControl.Kind.VERIFY ? RuntimeLogEventKind.MODEL_VERIFY_FAILED : null;
                }
                if (terminalKind != null) {
                    // Bounded reason label: success / cancelled / failed (no raw exception text).
                    terminalDetail = b.phase == ModelManagementState.Phase.SUCCEEDED ? "ok"
                        : b.phase == ModelManagementState.Phase.CANCELLED ? "cancelled" : "failed";
                }
            }
            }
        }
        state.notifyListeners();
        if (terminalKind != null) safeLog(terminalKind, op.id, terminalDetail);
    }
    private void safeLog(RuntimeLogEventKind kind, String id, String detail) {
        if (logStore == null) return;
        try { logStore.append(kind, RuntimeLogSource.SHARED, id, detail); }
        catch (Throwable ignored) { /* optional telemetry must not alter model ownership/state */ }
    }
    private void finish(ModelOperationControl.Operation op) {
        if (op == null) return;
        boolean succeeded;
        synchronized (this) {
            ModelManagementState.Builder b=builder();
            if (op.kind == ModelOperationControl.Kind.INSPECT) {
                if (control.peekActive() != op) return;
                if (!op.isTerminated()) b.inspectError="概况检查未启动或异常终止";
                b.inspectBusy=false;
            } else {
                if (!op.id.equals(b.activeOperationId)) return;
                if (!state.current().isTerminalPhase()) {
                    b.phase=ModelManagementState.Phase.FAILED; b.lastOutcome=ModelManagementState.Outcome.FAILED;
                    b.errorCode="任务未启动或异常终止";
                }
                b.pageOwnerHeld=false;
            }
            control.decideTerminal(op); // Includes admitted-but-never-run executor rejection.
            state.install(b.build());
            succeeded=op.isSucceeded();
        }
        try { state.notifyListeners(); }
        finally { control.complete(op, succeeded); }
    }
}
