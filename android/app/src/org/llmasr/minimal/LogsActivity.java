package org.llmasr.minimal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.List;
import org.llmasr.minimal.diagnostics.LogExportController;
import org.llmasr.minimal.diagnostics.LogExportPage;
import org.llmasr.minimal.diagnostics.RuntimeLogEvent;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;
import org.llmasr.minimal.diagnostics.RuntimeLogText;
import org.llmasr.minimal.diagnostics.RuntimeLogWorker;

/** Cache-only foreground log viewer. All provider IO belongs to graph's bounded
 * process export lane; the page never supplies worker callbacks capturing UI. */
public final class LogsActivity extends Activity {
    private AppGraph graph;
    private LogExportPage<Uri> page;
    private TextView body, summary, status;
    private Button export;
    private ScrollView scroll;
    private boolean visible;
    private List<RuntimeLogEvent> rendered;
    private final Refresh refresh = new Refresh(this);

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try { AppGraph.install(getApplicationContext()); graph = AppGraph.get(); }
        catch (java.io.IOException failure) {
            TextView text = new TextView(this); text.setText("初始化失败，请重新安装完整应用。"); setContentView(text); return;
        }
        page = new LogExportPage<>(graph.logExports());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(248,250,252));
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248,250,252)); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(b.left,b.top,b.right,b.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.requestApplyInsets();
        button(root,"‹ 返回",this::finish);
        text(root,"运行日志",24);
        text(root,"App / 语音输入法共用日志。日期、毫秒、时区及阶段耗时可见。\n仅保存固定事件，不包含转写正文、录音、外部路径或原始异常。保留最近 1000 条；突然结束进程可能丢失未刷盘日志。",14);
        export = button(root,"导出日志文件（UTF-8 TXT）",this::beginExport);
        status = text(root,saved == null ? "" : "页面重建，旧选择已失效，请重新导出。",15);
        text(root,"导出到您选择的位置；云文件提供方可能联网同步。导出失败可能留下不完整外部文件，不会自动重试或删除该文件。",14);
        summary = text(root,"",14);
        button(root,"查看最新日志（底部）",() -> scroll.post(() -> scroll.fullScroll(android.view.View.FOCUS_DOWN)));
        body = text(root,"",13); body.setTextIsSelectable(true);
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout root, String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size);
        v.setTextColor(Color.rgb(17,24,39)); v.setPadding(0,dp(8),0,dp(8)); root.addView(v); return v;
    }
    private Button button(LinearLayout root,String title,Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setTextColor(Color.rgb(17,24,39)); b.setOnClickListener(v -> action.run()); root.addView(b); return b;
    }
    private void beginExport() {
        if (!visible || page == null) return;
        LogExportController.Ticket ticket = page.begin(graph.runtimeLogStore().snapshot());
        if (ticket == null) { status.setText("暂无日志或另一次导出尚未结束，请等待后重试。"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"qwen-asr-logs-"+System.currentTimeMillis()+".txt");
        try { startActivityForResult(intent,ticket.requestCode); }
        catch (RuntimeException unavailable) { page.abandon(true); status.setText("无法打开保存选择器，请返回后重试。"); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (page != null) page.result(request,result == RESULT_OK && data != null ? data.getData() : null);
    }
    @Override protected void onResume() {
        super.onResume(); visible=true;
        if (page == null) return;
        page.foreground(true); refresh.start();
    }
    @Override protected void onPause() {
        visible=false; if (page != null) page.foreground(false);
        refresh.stop(); super.onPause();
    }
    @Override protected void onDestroy() {
        if (page != null) page.destroy(); refresh.stop(); super.onDestroy();
    }
    private void render() {
        if (!visible || graph == null || isFinishing() || isDestroyed()) return;
        List<RuntimeLogEvent> snapshot = graph.runtimeLogStore().snapshot();
        if (!snapshot.equals(rendered)) {
            StringBuilder b = new StringBuilder();
            for (RuntimeLogEvent e : snapshot) b.append(RuntimeLogText.event(e.kind)).append('\n')
                .append(RuntimeLogStore.formatEvent(e)).append("\n\n");
            rendered=snapshot; body.setText(b);
        }
        RuntimeLogWorker worker = graph.runtimeLogWorker();
        summary.setText("最近 "+snapshot.size()+" / 1000 条；本进程淘汰 "+graph.runtimeLogStore().droppedCount()
            +" 条。\n初始历史读取："+worker.recoveryStatus()
            +(worker.lastFailure() == null ? "\n本地保存：无当前错误（异步刷盘）" : "\n本地保存异常："+worker.lastFailure()+"；新事件将触发重试。"));
        LogExportController.State state = graph.logExports().state();
        status.setText(RuntimeLogText.export(state));
        export.setEnabled(!snapshot.isEmpty() && !state.busy());
    }
    /** One stable foreground tick observes persistence/export status too; no per-event
     * callbacks or queued snapshots. Pausing removes it and drops UI ownership. */
    private static final class Refresh implements Runnable {
        private final WeakReference<LogsActivity> target;
        private final Handler main = new Handler(Looper.getMainLooper());
        Refresh(LogsActivity a) { target=new WeakReference<>(a); }
        void start() { stop(); main.post(this); }
        void stop() { main.removeCallbacks(this); }
        @Override public void run() {
            LogsActivity a=target.get();
            if (a == null || !a.visible) return;
            a.render(); main.postDelayed(this,1000);
        }
    }
}
