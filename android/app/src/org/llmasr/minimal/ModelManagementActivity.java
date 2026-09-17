package org.llmasr.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelReports;
import org.llmasr.minimal.model.SafModelSource;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.modelmanagement.ModelPageSession;
import org.llmasr.minimal.modelmanagement.ModelPickerTickets;
import org.llmasr.minimal.modelmanagement.ModelUiText;
import org.llmasr.minimal.task.TaskCoordinator;

/** Same-process foreground maintenance page. All file work belongs to the shared controller. */
public final class ModelManagementActivity extends Activity {
    private AppGraph graph;
    private ModelPageSession<Uri> page;
    private TextView readiness, storage, task, files, busy;
    private Button choose, verify, delete, cancel;
    private volatile boolean visible;
    private boolean inspectNeeded, receiverRegistered;
    private final UiRefresh invalidation = new UiRefresh(this);
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (page != null) { page.foreground(false); page.cancelOwn(); }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try { AppGraph.install(getApplicationContext()); graph = AppGraph.get(); }
        catch (IOException e) {
            TextView failure = new TextView(this); failure.setText("初始化失败，请重新安装完整应用。"); setContentView(failure); return;
        }
        page = new ModelPageSession<>(graph.pickerTickets(), graph.modelManagement(), saved == null ? null : saved.getString("modelOperation"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        int pad = dp(16); root.setPadding(pad,pad,pad,pad);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(248,250,252)); scroll.addView(root);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left=bars.left; top=bars.top; right=bars.right; bottom=bars.bottom;
            } else {
                left=insets.getSystemWindowInsetLeft(); top=insets.getSystemWindowInsetTop();
                right=insets.getSystemWindowInsetRight(); bottom=insets.getSystemWindowInsetBottom();
            }
            v.setPadding(left,top,right,bottom); return insets;
        });
        scroll.requestApplyInsets();
        button(root, "‹ 返回 · 模型管理", this::onBackPressed);
        text(root,"Qwen3-ASR-0.6B",24);
        text(root,"MNN · FP16 权重 · CPU · 当前固定部署版本\n仅支持内置固定清单，不支持原始 safetensors、压缩包或在线下载。",16);
        button(root, "运行日志（独立页面）", () -> {
            if (page.ownsActive()) {
                toast("请等待本页模型操作结束，或先取消并等待收尾，再查看日志。"); return;
            }
            startActivity(new Intent(this, LogsActivity.class));
        });
        readiness = text(root,"",18);
        TextView metadata = text(root, manifestDetails(),14); metadata.setTextIsSelectable(true); metadata.setVisibility(View.GONE);
        button(root,"展开 / 收起清单版本与 SHA-256", () -> metadata.setVisibility(metadata.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        text(root,"内部存储",20); storage = text(root,"",16);
        text(root,"逻辑文件字节不是物理磁盘分配；可用存储不是 RAM。删除释放量仅为估计。\n云文件提供方可能联网获取模型，优先选择专用本地目录；本应用无网络权限。",15);
        busy = text(root,"",16);
        choose = button(root,"选择模型目录 / 修复导入（重试需重新选择）",this::chooseDirectory);
        verify = button(root,"完整 SHA 校验 / 重新校验", () -> {
            if (graph.modelManagement().startVerify()) page.adoptStarted(); else rejected();
            invalidation.onChange();
        });
        task = text(root,"",16); // No live region / per-block accessibility announcements.
        cancel = button(root,"取消本页操作", () -> { page.cancelOwn(); invalidation.onChange(); });
        text(root,"逐文件详情",20); files = text(root,"",15); files.setTextIsSelectable(true);
        delete = button(root,"删除内部模型… / 重试删除…",this::confirmDelete);
        text(root,"只删除内部固定清单文件及其 .part；未知文件保留，不递归。不会删除外部源目录、转写文本或导出文件。删除不是安全擦除。\n导入/校验离开前台会请求取消；旋转保留任务。取消需等待 provider 读取与清理结束，不强制释放任务锁。",15);
        if (saved != null) toast("页面已重建；旧目录选择结果失效，如有需要请重新选择。");
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout root, String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(17,24,39));
        v.setPadding(0,dp(8),0,dp(8)); root.addView(v); return v;
    }
    private Button button(LinearLayout root, String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setTextSize(16); b.setAllCaps(false);
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setSingleLine(false);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{Color.rgb(90,97,108),Color.rgb(17,24,39)}));
        b.setOnClickListener(v -> action.run()); root.addView(b, new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    private String manifestDetails() {
        StringBuilder canonical = new StringBuilder(), details = new StringBuilder("固定清单（名称 / bytes / SHA-256）\n");
        for (ModelEntry e : graph.modelRepository().manifest().entries()) {
            canonical.append(e.file()).append(':').append(e.bytes()).append(':').append(e.sha256()).append('\n');
            details.append(e.file()).append("\n").append(ModelUiText.bytes(e.bytes())).append("\nSHA-256: ").append(e.sha256()).append("\n\n");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte b : hash) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            details.insert(0,"清单内容指纹（名称:bytes:SHA 换行 UTF-8）\n"+hex+"\n模型 epoch：仅本进程有效，不持久化 READY\n\n");
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        return details.toString();
    }
    private boolean unlocked() {
        KeyguardManager key = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        return key != null && power != null && !key.isKeyguardLocked() && power.isInteractive();
    }
    private void chooseDirectory() {
        if (!visible || !unlocked()) return;
        try {
            int code = page.beginPicker();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent,code);
        } catch (RuntimeException e) { page.abandonPicker(); toast("无法打开目录选择器，或已有选择未结束；请返回后重试。"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (page == null || !ModelPickerTickets.isRequest(request)) return;
        Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
        if (!page.result(request,uri)) { toast("目录选择会话失效，请重新选择；未导入。"); return; }
        if (uri == null) toast("已取消选择，模型与转写结果未改变。");
        consumePicker();
    }
    private void consumePicker() {
        page.foreground(visible && unlocked());
        if (!visible || !unlocked() || !page.hasDeferred()) return;
        inspectNeeded = false;
        if (!page.consume(uri -> graph.modelManagement().startImport(new SafModelSource(getApplicationContext().getContentResolver(),uri)))) rejected();
        invalidation.onChange();
    }
    private void confirmDelete() {
        final long epoch = graph.readiness().epoch();
        ModelManagementState.Snapshot s = graph.modelManagement().state().current();
        long estimate = s.installedBytes < 0 || s.orphanPartBytes < 0 ? -1 : s.installedBytes+s.orphanPartBytes;
        new AlertDialog.Builder(this).setTitle("删除内部模型？")
            .setMessage("删除本应用内部模型副本及未完成导入文件？预计释放约 "+ModelUiText.bytes(estimate)+"。\n删除后 App 和语音输入法需重新导入模型。不会删除外部源目录、转写文本或导出文件；未知文件保留，删除不是安全擦除。")
            .setNegativeButton("保留",null).setPositiveButton("确认删除",(d,w) -> {
                if (!visible || !unlocked()) return;
                if (graph.readiness().epoch() != epoch) { toast("模型已变化，请刷新后重新确认。"); inspectNeeded=true; }
                else if (graph.modelManagement().startDelete(epoch,true)) page.adoptStarted(); else rejected();
                invalidation.onChange();
            }).show();
    }
    private void rejected() { toast("任务忙、模型已变化或未能启动；请等待后重试（不排队）。"); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void render() {
        if (!visible || graph == null || isDestroyed() || isFinishing()) return;
        boolean ownerBusy = graph.coordinator().isBusy();
        if (inspectNeeded && !ownerBusy && !page.hasDeferred() && unlocked()) {
            inspectNeeded=false; graph.modelManagement().refreshInspect(); ownerBusy=graph.coordinator().isBusy();
        }
        ModelManagementState.Snapshot s = graph.modelManagement().state().current();
        ModelReadiness.Snapshot r = graph.readiness().snapshot();
        readiness.setText("模型："+ModelUiText.readiness(r.state)+"\nepoch "+r.epoch);
        long expected = 0; for (ModelEntry e : graph.modelRepository().manifest().entries()) expected += e.bytes();
        storage.setText("预期模型："+ModelUiText.bytes(expected)+"\n内部正式文件："+ModelUiText.bytes(s.installedBytes)
            +"\n临时残片："+ModelUiText.bytes(s.orphanPartBytes)+"\n可用存储（最近检查）："+ModelUiText.bytes(s.availableBytes)
            +"\n"+ModelUiText.spacePlan(s));
        busy.setText(ownerBusy ? page.ownsActive() ? "本页任务占用共享任务锁；取消后仍需等待收尾。显示最近快照。"
            : "应用 / 输入法 / 另一管理页正在处理，完成后可管理模型。当前仅显示缓存概况。" : "空闲 · 所有写操作仍会重新检查共享任务锁。");
        task.setText(ModelUiText.task(s)+(s.inspectBusy ? "\n正在刷新内部概况（最近操作保留）" : "")
            +(s.inspectError == null ? "" : "\n概况刷新："+s.inspectError));
        StringBuilder detail = new StringBuilder();
        for (ModelReports.FileDetail f : s.files) detail.append(f.name).append("\n")
            .append(!f.present ? "缺失" : !f.sizeMatch ? "大小不符" : r.ready() && r.epoch == s.modelEpoch ? "SHA 已通过" : "大小匹配 / 尚无当前 SHA 证明")
            .append("\n").append(ModelUiText.fileResult(s,f.name))
            .append(" · ").append(ModelUiText.bytes(f.actualBytes)).append("\n预期：").append(ModelUiText.bytes(f.expectedBytes))
            .append(f.partExists ? "\n有受管 .part 残片" : "").append("\n\n");
        if (s.files.isEmpty()) detail.append("尚未取得文件快照。\n");
        if (!s.unexpectedFiles.isEmpty()) detail.append("内部目录有 ").append(s.unexpectedFiles.size()).append(" 项未知文件/目录：保留、不递归删除。");
        files.setText(detail);
        boolean any = false, completeFile = false;
        for (ModelReports.FileDetail f : s.files) { any |= f.present || f.partExists; completeFile |= f.present && f.sizeMatch; }
        choose.setEnabled(!ownerBusy); verify.setEnabled(!ownerBusy && completeFile); delete.setEnabled(!ownerBusy && any);
        cancel.setVisibility(page.ownsActive() && s.operationKind != ModelManagementState.OperationKind.DELETE ? View.VISIBLE : View.GONE);
        cancel.setEnabled(page.cancellable());
        if (page.ownsActive() && ownerBusy && unlocked()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onResume() {
        super.onResume(); visible=true;
        if (page == null) return;
        graph.modelManagement().state().addListener(invalidation); graph.readiness().addListener(invalidation); graph.coordinator().addListener(invalidation);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screenOff,filter);
        receiverRegistered=true; page.foreground(unlocked());
        inspectNeeded=!page.ownsActive(); consumePicker(); invalidation.onChange();
    }
    @Override protected void onPause() {
        visible=false;
        if (page != null) {
            page.foreground(false);
            graph.modelManagement().state().removeListener(invalidation); graph.readiness().removeListener(invalidation); graph.coordinator().removeListener(invalidation);
            invalidation.detach();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }
    @Override protected void onStop() {
        if (page != null) page.stopped(isChangingConfigurations()); // onPause is too early to decide rotation.
        if (receiverRegistered) { unregisterReceiver(screenOff); receiverRegistered=false; }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (page != null) out.putString("modelOperation",page.ownedId());
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if (page != null && page.cancellable()) {
            new AlertDialog.Builder(this).setTitle("取消操作并离开？")
                .setMessage("已发布的完整文件保留；取消等待文件读取与清理结束，任务锁不会提前释放。")
                .setNegativeButton("留在页面",null).setPositiveButton("取消并离开",(d,w) -> { page.cancelOwn(); finish(); }).show();
        } else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (page != null) page.destroy(); invalidation.detach(); super.onDestroy();
    }
    /** Weak target, one coalesced main invalidation; no captured snapshot. Terminal/owner release immediate. */
    private static final class UiRefresh implements ModelManagementState.Listener, ModelReadiness.Listener, TaskCoordinator.Listener, Runnable {
        private final WeakReference<ModelManagementActivity> target;
        private final Handler main = new Handler(Looper.getMainLooper());
        private long lastRender;
        UiRefresh(ModelManagementActivity activity) { target=new WeakReference<>(activity); }
        @Override public synchronized void onChange() {
            ModelManagementActivity a = target.get();
            if (a == null || !a.visible || a.graph == null) return;
            ModelManagementState.Snapshot s = a.graph.modelManagement().state().current();
            boolean immediate = s.isTerminalPhase() || !a.graph.coordinator().isBusy();
            main.removeCallbacks(this);
            main.postDelayed(this,immediate ? 0 : Math.max(0,200-(SystemClock.uptimeMillis()-lastRender)));
        }
        synchronized void detach() { main.removeCallbacks(this); }
        @Override public void run() {
            ModelManagementActivity a=target.get();
            synchronized (this) { lastRender=SystemClock.uptimeMillis(); }
            // Never hold the dispatch monitor while entering controller admission:
            // worker publishes under its short controller lock and calls onChange.
            if (a != null && a.visible) a.render();
        }
    }
}
