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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.lang.ref.WeakReference;
import org.llmasr.minimal.model.ModelEntry;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelReports;
import org.llmasr.minimal.model.SafModelSource;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.modelmanagement.ModelPageSession;
import org.llmasr.minimal.modelmanagement.ModelPickerTickets;
import org.llmasr.minimal.modelmanagement.ModelUiText;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.platform.ActivityWindow;

/** Same-process foreground maintenance page. All file work belongs to the shared controller. */
public final class ModelManagementActivity extends Activity {
    private AppGraph graph;
    private final ActivityWindow.BackRegistration windowBack = new ActivityWindow.BackRegistration(this, this::onBackPressed);
    private ModelPageSession<Uri> page;
    private TextView readiness, storage, task, files, busy;
    private LinearLayout cards, details;
    private String detailModel;
    private Button select;
    private Button choose, chooseZip, verify, restore, delete, cancel, prepare, release;
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
            TextView failure = new TextView(this); failure.setText("初始化失败，请重新安装完整应用。"); setContentView(failure);
            ActivityWindow.configure(this, failure); return;
        }
        page = new ModelPageSession<>(graph.pickerTickets(), graph.modelManagement(), saved == null ? null : saved.getString("modelOperation"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        int pad = dp(16); root.setPadding(pad,pad,pad,pad);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(248,250,252)); scroll.addView(root);
        setContentView(scroll);
        ActivityWindow.configure(this, scroll);
        button(root, "‹ 返回 · 模型管理", this::onBackPressed);
        text(root,"模型管理",24);
        text(root,"兼容 Qwen3-ASR-0.6B · MNN · CPU。自定义模型精度为导入者声明，不代表数学验证或可信来源认证。",16);
        cards = new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL); root.addView(cards);
        detailModel = saved == null ? null : saved.getString("detailModel");
        button(root, "运行日志（独立页面）", () -> {
            if (page.ownsActive()) {
                toast("请等待本页模型操作结束，或先取消并等待收尾，再查看日志。"); return;
            }
            startActivity(new Intent(this, LogsActivity.class));
        });
        details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); root.addView(details);
        button(details,"‹ 返回模型列表",() -> { detailModel = null; invalidation.onChange(); });
        select = button(details,"选用此模型",() -> {
            if (detailModel != null && !graph.modelManagement().startSelect(detailModel)) rejected();
            invalidation.onChange();
        });
        readiness = text(details,"",18);
        TextView metadata = text(details, "逐文件预期 SHA-256 与检查结果见下方",14); metadata.setTextIsSelectable(true); metadata.setVisibility(View.GONE);
        button(details,"展开 / 收起清单说明", () -> metadata.setVisibility(metadata.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        text(details,"内部存储",20); storage = text(details,"",16);
        text(root,"逻辑文件字节不是物理磁盘分配；可用存储不是 RAM。删除释放量仅为估计。\n云文件提供方可能联网获取模型，优先选择专用本地目录；本应用无网络权限。",15);
        busy = text(root,"",16);
        chooseZip = button(root,"选择模型 ZIP 包 / 修复导入",() -> chooseModel(ModelPickerTickets.Mode.ZIP));
        choose = button(root,"选择模型目录 / 修复导入（兼容入口）",this::chooseDirectory);
        text(root,"ZIP 支持旧固定包或受控 v2 七资产变体；导入不自动切换当前模型。目录入口仅修复旧固定部署。先审核后逐文件发布，不是整包回滚；私有 ZIP 另占存储，低空间不删除旧模型。",15);
        restore = button(details,"快速恢复已校验证明（不做 SHA）", () -> {
            org.llmasr.minimal.model.ModelDeployment target = visibleModel();
            if (target == null || !graph.modelManagement().startRestore(target.key,target.installationId)) rejected();
            invalidation.onChange();
        });
        text(root,"正常启动仅恢复文件证明，不加载内存模型。完整校验或恢复后，请显式准备；App 与输入法共用同一内存实例。释放忙时等待真实任务收尾。",15);
        prepare = button(details,"显式准备模型（加载到内存）", () -> {
            if (!graph.asrOperation().startPrepare()) rejected();
            invalidation.onChange();
        });
        release = button(details,"释放内存模型（忙时延后）", () -> {
            graph.asrOperation().startRelease(); invalidation.onChange();
        });
        verify = button(details,"完整 SHA 校验 / 重新校验", () -> {
            org.llmasr.minimal.model.ModelDeployment target = visibleModel();
            if (target != null && graph.modelManagement().startVerify(target.key,target.installationId)) page.adoptStarted(); else rejected();
            invalidation.onChange();
        });
        task = text(details,"",16); // No live region / per-block accessibility announcements.
        cancel = button(details,"取消本页操作", () -> { page.cancelOwn(); invalidation.onChange(); });
        text(details,"逐文件详情",20); files = text(details,"",15); files.setTextIsSelectable(true);
        delete = button(details,"删除内部模型… / 重试删除…",this::confirmDelete);
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
    private boolean unlocked() {
        KeyguardManager key = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        return key != null && power != null && !key.isKeyguardLocked() && power.isInteractive();
    }
    private void chooseDirectory() { chooseModel(ModelPickerTickets.Mode.DIRECTORY); }
    private void chooseModel(ModelPickerTickets.Mode mode) {
        if (!visible || !unlocked() || graph.coordinator().isBusy()) return;
        try {
            int code = page.beginPicker(mode);
            Intent intent;
            if (mode == ModelPickerTickets.Mode.ZIP) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false)
                    .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/octet-stream"});
            } else intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent,code);
        } catch (RuntimeException e) { page.abandonPicker(); toast("无法打开目录选择器，或已有选择未结束；请返回后重试。"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (page == null || !ModelPickerTickets.isRequest(request)) return;
        ModelPickerTickets.Mode mode = page.mode(request);
        if (mode == null) { toast("选择会话失效，请重新选择；未导入。"); return; }
        Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
        if (uri != null && mode == ModelPickerTickets.Mode.ZIP &&
            (!"content".equals(uri.getScheme()) || (data.getClipData() != null && data.getClipData().getItemCount() != 0))) {
            page.result(request,mode,null); toast("请选择单个可读取的本地模型 ZIP 文件；未导入。"); return;
        }
        if (!page.result(request,mode,uri)) { toast("选择会话失效，请重新选择；未导入。"); return; }
        if (uri == null) toast("已取消选择，模型与转写结果未改变。");
        consumePicker();
    }
    private void consumePicker() {
        page.foreground(visible && unlocked());
        if (!visible || !unlocked() || !page.hasDeferred()) return;
        inspectNeeded = false;
        if (!page.consumeTyped(selection -> graph.modelManagement().startImport(selection.mode == ModelPickerTickets.Mode.ZIP
            ? graph.modelZipSource(selection.value) : new SafModelSource(getApplicationContext().getContentResolver(),selection.value)))) rejected();
        invalidation.onChange();
    }
    private void confirmDelete() {
        final org.llmasr.minimal.model.ModelDeployment target = visibleModel();
        if (target == null) { rejected(); return; }
        final long epoch = target.readiness.epoch();
        final String identity = target.installationId;
        final String confirmation = graph.modelManagement().confirmation(target.key);
        ModelManagementState.Snapshot s = graph.modelManagement().snapshotFor(detailModel);
        if (s == null) { rejected(); return; }
        long estimate = s.installedBytes < 0 || s.orphanPartBytes < 0 ? -1 : s.installedBytes+s.orphanPartBytes;
        new AlertDialog.Builder(this).setTitle("删除内部模型？")
            .setMessage("删除本应用内部模型副本及未完成导入文件？预计释放约 "+ModelUiText.bytes(estimate)+"。\n若删除当前选用模型，App 和语音输入法需明确选用其他已安装模型或重新导入。不会删除外部源目录、转写文本或导出文件；未知文件保留，删除不是安全擦除。")
            .setNegativeButton("保留",null).setPositiveButton("确认删除",(d,w) -> {
                if (!visible || !unlocked()) return;
                org.llmasr.minimal.model.ModelDeployment current = visibleModel();
                if (current == null || !target.key.equals(detailModel) || current.readiness.epoch() != epoch || !identity.equals(current.installationId)) { toast("模型已变化，请刷新后重新确认。"); inspectNeeded=true; }
                else if (graph.modelManagement().startDeleteIdentity(target.key,identity,confirmation,epoch)) page.adoptStarted(); else rejected();
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
        ModelManagementState.Snapshot boundDetail = graph.modelManagement().snapshotFor(detailModel);
        ModelManagementState.Snapshot s = boundDetail == null ? ModelManagementState.empty() : boundDetail;
        cards.removeAllViews();
        java.util.List<org.llmasr.minimal.model.ModelCatalog.Row> rows = graph.modelManagement().catalogRows();
        for (org.llmasr.minimal.model.ModelCatalog.Row row : rows) {
            org.llmasr.minimal.model.ModelDeployment model = row.deployment;
            int verified = model.readiness.isReady() && model.readiness.epoch() == row.epoch ? row.verifiedCount : 0;
            String summary = model.policy.displayName + " · " + model.policy.precision + (model.policy.trustedLegacy ? "" : "（声明）")
                + "\n" + ModelUiText.bytes(model.policy.totalBytes) + (row.selected ? " · 当前选用" : "")
                + "\n" + ModelUiText.readiness(model.readiness.state()) + " · 已校验 " + verified + "/7";
            Button card = button(cards,summary,() -> { detailModel = model.key; graph.modelManagement().showDetail(model.key); invalidation.onChange(); });
            card.setContentDescription(summary);
        }
        if (rows.isEmpty()) text(cards,"正在读取模型目录……",16);
        details.setVisibility(detailModel == null ? View.GONE : View.VISIBLE);
        cards.setVisibility(detailModel == null ? View.VISIBLE : View.GONE);
        org.llmasr.minimal.model.ModelDeployment model = visibleModel();
        ModelReadiness.Snapshot r = model == null ? new ModelReadiness.Snapshot(-1,ModelReadiness.State.UNKNOWN,-2) : model.readiness.snapshot();
        org.llmasr.minimal.model.ModelResidency.Snapshot loaded = graph.modelAccess().residentSnapshot();
        readiness.setText((model == null ? "" : model.policy.displayName + "\n" + (model.policy.trustedLegacy ? "内置固定可信清单" : "完整性按导入者提供清单；非可信来源认证 · 精度仅声明") + "\n") + "文件："+ModelUiText.readiness(r.state)+"\nepoch "+r.epoch
            +"\n内存："+(loaded == null ? "未绑定" : loaded.getState().name())
            +(loaded != null && loaded.getPendingRelease() ? " · 等待释放" : ""));
        long expected = model == null ? -1 : model.policy.totalBytes;
        boolean storageSnapshot = boundDetail != null;
        storage.setText("预期模型："+ModelUiText.bytes(expected)+"\n内部正式文件："+ModelUiText.bytes(storageSnapshot ? s.installedBytes : -1)
            +"\n临时残片："+ModelUiText.bytes(storageSnapshot ? s.orphanPartBytes : -1)+"\n可用存储（最近检查）："+ModelUiText.bytes(storageSnapshot ? s.availableBytes : -1)
            +"\n"+(storageSnapshot ? ModelUiText.spacePlan(s) : "等待此模型概况检查"));
        busy.setText(ownerBusy ? page.ownsActive() ? "本页任务占用共享任务锁；取消后仍需等待收尾。显示最近快照。"
            : "应用 / 输入法 / 另一管理页正在处理，完成后可管理模型。当前仅显示缓存概况。" : "空闲 · 所有写操作仍会重新检查共享任务锁。");
        boolean targetSnapshot = boundDetail != null;
        task.setText((targetSnapshot ? ModelUiText.task(s) : "此模型尚无当前操作记录")+(s.inspectBusy ? "\n正在刷新内部概况（最近操作保留）" : "")
            +(s.inspectError == null ? "" : "\n概况刷新："+s.inspectError));
        StringBuilder detail = new StringBuilder();
        boolean snapshotMatches = boundDetail != null;
        java.util.List<ModelReports.FileDetail> detailFiles = snapshotMatches ? s.files : graph.modelManagement().detailCachedFiles(detailModel);
        for (ModelReports.FileDetail f : detailFiles) detail.append(f.name).append("\n")
            .append(!f.present ? "缺失" : !f.sizeMatch ? "大小不符" : r.ready() && (!snapshotMatches || r.epoch == s.modelEpoch) ? "SHA 已通过" : "大小匹配 / 尚无当前 SHA 证明")
            .append("\n").append(snapshotMatches ? ModelUiText.fileResult(s,f.name) : "最近目录快照；未运行新校验")
            .append(" · ").append(ModelUiText.bytes(f.actualBytes)).append("\n预期：").append(ModelUiText.bytes(f.expectedBytes))
            .append(f.partExists ? "\n有受管 .part 残片" : "").append("\nSHA-256: ").append(expectedHash(model, f.name)).append("\n\n");
        if (detailFiles.isEmpty()) detail.append("尚未取得文件快照。\n");
        if (snapshotMatches && !s.unexpectedFiles.isEmpty()) detail.append("内部目录有 ").append(s.unexpectedFiles.size()).append(" 项未知文件/目录：保留、不递归删除。");
        files.setText(detail);
        boolean any = false, completeFile = false;
        for (ModelReports.FileDetail f : detailFiles) { any |= f.present || f.partExists; completeFile |= f.present && f.sizeMatch; }
        choose.setEnabled(!ownerBusy); chooseZip.setEnabled(!ownerBusy); verify.setEnabled(!ownerBusy && model != null && completeFile); restore.setEnabled(!ownerBusy && model != null); delete.setEnabled(!ownerBusy && model != null && any);
        boolean currentModel = false; for (org.llmasr.minimal.model.ModelCatalog.Row row : rows) if (model == row.deployment && row.selected) currentModel = true;
        select.setEnabled(!ownerBusy && model != null && !currentModel);
        select.setText(currentModel ? "当前选用模型" : "选用此模型");
        prepare.setEnabled(!ownerBusy && r.ready() && currentModel);
        release.setEnabled(true);
        cancel.setVisibility(page.ownsActive() && s.operationKind != ModelManagementState.OperationKind.DELETE ? View.VISIBLE : View.GONE);
        cancel.setEnabled(page.cancellable());
        if (page.ownsActive() && ownerBusy && unlocked()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private org.llmasr.minimal.model.ModelDeployment visibleModel() {
        return graph == null ? null : graph.modelManagement().detail(detailModel);
    }
    private String expectedHash(org.llmasr.minimal.model.ModelDeployment model, String file) {
        if (model == null) return "未知";
        try { return model.repository.manifest().require(file).sha256(); } catch (IOException unavailable) { return "未知"; }
    }
    @Override protected void onResume() {
        super.onResume(); windowBack.resumed(); visible=true;
        if (page == null) return;
        graph.modelManagement().state().addListener(invalidation); graph.readiness().addListener(invalidation); graph.coordinator().addListener(invalidation);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screenOff,filter);
        receiverRegistered=true; page.foreground(unlocked());
        if (detailModel != null) graph.modelManagement().showDetail(detailModel);
        inspectNeeded=!page.ownsActive(); consumePicker(); invalidation.onChange();
    }
    @Override protected void onPause() {
        windowBack.paused();
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
        out.putString("detailModel",detailModel);
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if (detailModel != null) { detailModel = null; invalidation.onChange(); return; }
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
