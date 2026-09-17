package org.llmasr.minimal.transcription;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import org.llmasr.minimal.asr.InferenceAdapter;
import org.llmasr.minimal.asr.NativeResponse;
import org.llmasr.minimal.audio.ForegroundRecorder;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.audio.WaveInput;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.platform.OperationContext;
import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;

/** Application-level operation layer. The Activity does not hold any
 * long-running task closures: every entry point is a named method whose
 * lambda is created INSIDE this class and captures only AsrOperation (and
 * final locals). The Activity calls the named methods.
 *
 * Transaction lifecycle is delegated to RequestRunner, which owns the
 * preflight / pending / body / terminal / cancel / failure / cleanup
 * pipeline. AsrOperation provides the real Android-side ports: file paths,
 * content resolver, native bridge, report writer.
 *
 * The same TaskCoordinator serializes inference/models/clear local cleanup.
 * The TXT export IO is delegated to a separate {@link TextExportController}
 * (process-singleton, bounded worker, 1 slot) so that a slow provider open /
 * write / close does NOT occupy the ASR owner.
 */
public final class AsrOperation {
    private final OperationContext context;
    private final TaskCoordinator coordinator;
    private final ModelRepository modelRepository;
    private final AppState appState;
    private final TextExportController<Uri> textExport;
    private final RequestRunner runner;
    private final InferenceAdapter inference;
    private final AppReportWriter reports;
    private volatile RecordingControl recording;

    /** Dependencies are assembled once by AppGraph, never by a UI entry point. */
    public AsrOperation(OperationContext context, TaskCoordinator coordinator,
                         ModelRepository modelRepository, AppState appState,
                         TextExportController<Uri> textExport,
                         InferenceAdapter inference, AppReportWriter reports) {
        this.context = context;
        this.coordinator = coordinator;
        this.modelRepository = modelRepository;
        this.appState = appState;
        this.textExport = textExport;
        this.inference = inference;
        this.reports = reports;
        this.runner = new RequestRunner(
            coordinator,
            () -> ResultFiles.cleanTemporary(context.filesDir()),
            new AppRequestPolicy(reports, new StateImpl()),
            ctx -> { if (ctx.inputWav != null && ctx.inputWav.exists() && !ctx.inputWav.delete())
                throw new IOException("无法删除请求临时WAV"); });
    }
    public RecordingControl recording() { return recording; }
    public TextExportController<Uri> textExport() { return textExport; }

    /** Activity lifecycle hook: cancel any in-flight recording. */
    public void cancelRecording() {
        RecordingControl r = recording;
        if (r != null) r.cancel();
    }

    // ===== Public entry points. Activity only calls these. =====

    public boolean startSample(String language) {
        return runner.submit(TaskKind.INFERENCE, ctx -> {
            try (InputStream in = context.openAsset("sample.wav")) {
                if (in == null) throw new IOException("无法读取内置音频");
                transcribeFromStream(ctx, in, language, "public-zh-example");
            }
        });
    }

    public boolean startWave(Uri uri, String language) {
        return runner.submit(TaskKind.INFERENCE, ctx -> {
            ContentResolver cr = context.contentResolver();
            try (InputStream in = cr.openInputStream(uri)) {
                if (in == null) throw new IOException("无法读取音频");
                transcribeFromStream(ctx, in, language, "user-selected-wav");
            }
        });
    }

    public boolean startRecording(String language) {
        final RecordingControl control = new RecordingControl();
        return runner.submit(TaskKind.INFERENCE, ctx -> {
            byte[] wav;
            try {
                wav = ForegroundRecorder.capture(control,
                    seconds -> appState.setLastStatus(String.format(Locale.ROOT,
                        "正在录音 %.1f / 30.0 秒 · 停止后转写，离开界面会取消", seconds)));
                if (!control.tryCommitInference()) throw new CancellationException("已取消录音，音频已丢弃");
            } finally { clearRecording(control); }
            transcribeFromStream(ctx, new ByteArrayInputStream(wav), language, "user-microphone");
        }, () -> { recording = control; appState.notifyChange(); }, () -> clearRecording(control));
    }

    private void clearRecording(RecordingControl control) {
        if (recording == control) { recording = null; appState.notifyChange(); }
    }

    public boolean clearResults() {
        return runner.submit(TaskKind.MAINTENANCE, ctx -> {
            // Snapshot is taken under the SAME lock as text clear and export
            // admission, so no in-flight export can revive pre-clear text.
            appState.clearText();
            try {
                int cleared = ResultFiles.clearResults(context.filesDir());
                appState.setLastStatus("已清除应用内结果（" + (ctx.cleanedTemporary + cleared) + "个文件），模型未删除。");
            } catch (IOException ioe) {
                throw new IOException("清除结果失败：" + ioe.getMessage());
            }
        });
    }

    /** Submit edit under the App owner; compare captured revision in the worker. */
    public boolean editResult(long expectedRevision, String edited) {
        return runner.submit(TaskKind.MAINTENANCE, ctx -> {
            if (appState.applyEdit(expectedRevision, edited) < 0)
                throw new IOException("结果已变化，请重新打开编辑。");
            appState.setLastStatus("已更新当前文本；编辑仅保留在内存，请导出保存。原始推理报告未改写。");
        });
    }

    public boolean startupOnce() {
        return runner.submit(TaskKind.MAINTENANCE, ctx -> {
            appState.setLastStatus(ResultFiles.startupStatus(ctx.cleanedTemporary));
        });
    }

    // ===== internal helpers =====

    private void transcribeFromStream(RequestContext ctx, InputStream in, String language, String source) throws IOException {
        File wav = new File(context.filesDir(), "input-" + ctx.requestId + ".wav");
        ctx.inputWav = wav;
        double seconds = WaveInput.canonicalize(in, wav);
        runInference(ctx, wav, seconds, language, source);
    }

    private void runInference(RequestContext ctx, File wav, double seconds, String language, String source) throws IOException {
        appState.setLastStatus("模型加载 / 转写中（CPU，首次可能需要数十秒）……");
        NativeResponse r = inference.transcribe(RuntimeLogSource.APP, ctx.requestId,
            new File(modelRepository.modelDir(), "config.json").getAbsolutePath(),
            wav.getAbsolutePath(), language, context.cacheDir().getAbsolutePath());
        reports.writeSuccess(ctx, r, wav, seconds, language, source);
        ctx.inferenceReported = true;
        appState.setLastText(r.display);
        appState.setLastStatus(String.format(Locale.ROOT,
            "完成 · 加载 %.2fs / 推理 %.2fs / RTF %.3f\n每请求释放模型；非 warm 测试。",
            r.loadSeconds, r.inferenceSeconds, r.inferenceSeconds / seconds));
    }

    /** Adapter from AppState to the runner's State port. */
    private final class StateImpl implements AppRequestPolicy.State {
        @Override public String lastText() { return appState.lastText(); }
        @Override public void setLastText(String value) { appState.setLastText(value); }
        @Override public String lastStatus() { return appState.lastStatus(); }
        @Override public void setLastStatus(String value) { appState.setLastStatus(value); }
    }
}
