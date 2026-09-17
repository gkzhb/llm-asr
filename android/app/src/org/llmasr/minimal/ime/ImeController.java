package org.llmasr.minimal.ime;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.task.TaskKind;

/** Private voice-IME transaction. Shares only the task owner with the App.
 * Backend and notifier must not retain a Service/View. UI entry points (begin,
 * invalidate, start, commit) run on Android's main thread. Worker results are
 * serialized here and carry both editor and request identity. */
public final class ImeController {
    public interface Backend {
        byte[] capture(RecordingControl control, Progress progress) throws IOException;
        String transcribe(RequestContext request, byte[] wav, String language) throws Exception;
        void cleanup(RequestContext request) throws IOException;
    }
    public interface Progress { void update(double seconds); }
    public interface Commit { boolean write(String text); }
    private final TaskCoordinator coordinator;
    private final Backend backend;
    private final Runnable changed;
    private ImeSession current;
    private RecordingControl recording;
    private long counter;

    public ImeController(TaskCoordinator coordinator, Backend backend, Runnable changed) {
        this.coordinator = coordinator; this.backend = backend; this.changed = changed;
    }
    public synchronized ImeSession begin(int type, int options, String key) {
        invalidate();
        if (ImeFieldPolicy.isSensitive(type, options)) return null;
        current = new ImeSession(++counter, key);
        return current;
    }
    public synchronized void invalidate() {
        if (current != null) { current.valid = false; current.preview = ""; current.status = ""; }
        current = null;
        if (recording != null) recording.cancel();
        notifyChanged();
    }
    /** Called by the Service on the main thread. The injected hide command
     * must request actual system hiding before opening a picker/settings UI. */
    public void leaveForExternalUi(Runnable hide, Runnable launch) {
        invalidate();
        hide.run();
        try { launch.run(); } catch (RuntimeException ignored) { /* Remain hidden, no stale restoration. */ }
    }
    public boolean busy() { return coordinator.isBusy(); }
    public synchronized boolean ownsTask() { return recording != null; }
    public synchronized boolean capturing() { return recording != null && !recording.committed() && !recording.cancelled(); }
    public synchronized void stop() { if (recording != null) recording.stop(); }
    private boolean live(ImeSession s, long revision) {
        return current == s && s.valid && s.revision == revision;
    }
    private void notifyChanged() { try { changed.run(); } catch (RuntimeException ignored) {} }
    private synchronized void status(ImeSession s, long rev, String text) {
        if (live(s, rev)) { s.status = text; notifyChanged(); }
    }
    public synchronized boolean start(ImeSession s, String language) {
        if (s == null || s != current || !s.valid || coordinator.isBusy()) return false;
        if (!"Chinese".equals(language) && !"English".equals(language) && !"auto".equals(language)) return false;
        final RecordingControl control = new RecordingControl();
        final long rev = s.revision + 1;
        RequestRunner.Lifecycle policy = new RequestRunner.Lifecycle() {
            public void admitted(RequestContext ctx) { status(s, rev, "执行中，请保持应用在前台……"); }
            public void rejected(RequestContext ctx) { status(s, rev, "任务执行器已关闭，无法启动新任务。"); }
            public void cancelled(RequestContext ctx) { status(s, rev, "已取消录音，音频已丢弃。"); }
            public void failed(RequestContext ctx, Throwable failure) { status(s, rev, "失败：" + failure.getMessage()); }
            public void cleanupFailed(RequestContext ctx, Throwable failure) {
                synchronized (ImeController.this) {
                    status(s, rev, s.status + "\n临时文件清理失败：" + failure.getMessage());
                }
            }
        };
        RequestRunner runner = new RequestRunner(coordinator, () -> 0, policy, backend::cleanup);
        return runner.submit(TaskKind.INFERENCE, ctx -> {
            if (control.cancelled()) throw new CancellationException();
            status(s, rev, "正在录音，停止后转写……");
            byte[] wav = backend.capture(control, seconds -> status(s, rev,
                String.format(java.util.Locale.ROOT, "录音 %.1f / 30 秒", seconds)));
            // The real capture adapter must acknowledge actual hardware release.
            if (!control.tryCommitInference()) throw new CancellationException();
            status(s, rev, "校验模型 / 加载 / 转写中，请稍候……");
            String text = backend.transcribe(ctx, wav, language);
            synchronized (ImeController.this) {
                if (live(s, rev)) {
                    s.preview = text == null ? "" : text;
                    s.status = s.preview.isEmpty() ? "未识别到文本，请重新录音。" : "请检查预览，点击确认输入。";
                    notifyChanged();
                }
            }
        }, () -> {
            s.revision = rev; s.preview = ""; s.consumed = false;
            recording = control;
        }, () -> {
            synchronized (ImeController.this) { if (recording == control) recording = null; }
            notifyChanged(); // Coordinator notification also fires AFTER release.
        });
    }
    /** Consumes before calling the connection. A rejected/throwing connection
     * may already have applied text: never retry automatically. */
    public synchronized boolean commit(ImeSession s, String key, int type, int options, Commit connection) {
        if (s == null || current != s || !s.valid || s.consumed || s.preview.isEmpty()
            || coordinator.isBusy() || !s.fieldKey.equals(key) || ImeFieldPolicy.isSensitive(type, options)) return false;
        String text = s.preview;
        s.consumed = true; s.preview = "";
        boolean success = false;
        try { success = connection != null && connection.write(text); }
        catch (RuntimeException ignored) { /* Do not log field text or retry. */ }
        s.status = success ? "已提交。可以录制下一句。" : "目标未确认接收，未自动重试；请检查输入框。";
        notifyChanged();
        return success;
    }
}
