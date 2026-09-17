package org.llmasr.minimal.transcription;

import java.io.IOException;
import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.RequestRunner;
import org.llmasr.minimal.task.TaskKind;

/** App-only text/report lifecycle. Host tests use this production policy. */
public final class AppRequestPolicy implements RequestRunner.Lifecycle {
    public interface Reports {
        void writePending(RequestContext ctx) throws IOException;
        void writeTerminal(RequestContext ctx) throws IOException;
        void writeFailure(RequestContext ctx, Throwable cause) throws IOException;
        void writeCancel(RequestContext ctx) throws IOException;
    }
    public interface State {
        String lastText();
        void setLastText(String value);
        String lastStatus();
        void setLastStatus(String value);
    }
    private final Reports reports;
    private final State state;
    public AppRequestPolicy(Reports reports, State state) {
        if (reports == null || state == null) throw new IllegalArgumentException("App ports required");
        this.reports = reports; this.state = state;
    }
    private boolean reporting(RequestContext ctx) { return ctx.kind != TaskKind.MAINTENANCE; }
    @Override public void admitted(RequestContext ctx) {
        if (reporting(ctx)) state.setLastText("");
        state.setLastStatus("执行中，请保持应用在前台……");
    }
    @Override public void busy(RequestContext ctx) { state.setLastStatus("已有任务执行中，请等待。"); }
    @Override public void rejected(RequestContext ctx) { state.setLastStatus("任务执行器已关闭，无法启动新任务。"); }
    @Override public void beforeBody(RequestContext ctx) throws IOException {
        if (reporting(ctx)) reports.writePending(ctx);
    }
    @Override public void succeeded(RequestContext ctx) throws IOException {
        if (reporting(ctx) && !ctx.inferenceReported) reports.writeTerminal(ctx);
    }
    @Override public void cancelled(RequestContext ctx) {
        state.setLastStatus("已取消录音，音频已丢弃。");
        try { if (reporting(ctx)) reports.writeCancel(ctx); }
        catch (Exception | LinkageError persistence) {
            state.setLastStatus("取消完成，但结果无法持久化：" + persistence.getMessage());
        }
    }
    @Override public void failed(RequestContext ctx, Throwable failure) {
        state.setLastStatus("失败：" + failure.getMessage());
        try { if (reporting(ctx)) reports.writeFailure(ctx, failure); }
        catch (Exception | LinkageError persistence) {
            state.setLastStatus("失败且结果无法持久化：" + persistence.getMessage());
        }
    }
    @Override public void cleanupFailed(RequestContext ctx, Throwable failure) {
        state.setLastStatus(state.lastStatus() + "\n临时文件清理失败：" + failure.getMessage());
    }
}
