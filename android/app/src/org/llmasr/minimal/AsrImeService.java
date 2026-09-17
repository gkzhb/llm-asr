package org.llmasr.minimal;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.lang.ref.WeakReference;
import org.llmasr.minimal.ime.ImeController;
import org.llmasr.minimal.ime.ImeFieldPolicy;
import org.llmasr.minimal.ime.ImeSession;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.task.TaskCoordinator;

/** Explicit-action voice keyboard. Android lifecycle/commit run on main;
 * worker retains only application context and a weak invalidation listener. */
public final class AsrImeService extends InputMethodService {
    private AppGraph graph;
    private ImeController controller;
    private ImeSession session;
    private TextView status, preview;
    private Button record, stop, cancel, commit;
    private Spinner language;
    private boolean visible, alive;
    private String setupError = "初始化失败，请打开主App检查模型。";
    private final UiRefresh refresh = new UiRefresh(this);

    @Override public void onCreate() {
        super.onCreate(); alive = true;
        try {
            AppGraph.install(getApplicationContext()); graph = AppGraph.get();
            controller = new ImeController(graph.coordinator(), graph.imeBackend(), refresh::onChange);
            graph.coordinator().addListener(refresh); // Also notified after shared owner release.
            graph.readiness().addListener(refresh);
        } catch (Exception | LinkageError e) { setupError = "初始化失败，请打开主App检查安装。"; }
    }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public View onCreateInputView() {
        // An IME window may be transparent. Paint the whole input panel and
        // use one explicit light widget theme, independent of host/night mode.
        Context ui = new ContextThemeWrapper(this, android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout root = new LinearLayout(ui); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));
        int pad = (int)(8 * getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        status = new TextView(ui); status.setTextColor(Color.rgb(55, 65, 81)); root.addView(status);
        preview = new TextView(ui); preview.setTextSize(18); preview.setTextColor(Color.rgb(17, 24, 39));
        preview.setPadding(pad,pad,pad,pad);
        // Non-editable/non-selectable preview: never opens another IME or copies text.
        ScrollView scroll = new ScrollView(ui); scroll.setBackgroundColor(Color.WHITE); scroll.addView(preview);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, (int)(72*getResources().getDisplayMetrics().density)));
        language = new Spinner(ui);
        language.setAdapter(new ArrayAdapter<String>(ui, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Chinese", "English", "auto"})); root.addView(language);
        LinearLayout row = new LinearLayout(ui); root.addView(row);
        record = button(row, "录音", () -> startRecording());
        stop = button(row, "停止", () -> { if (controller != null) controller.stop(); render(); });
        cancel = button(row, "丢弃", () -> { renewSession(); render(); });
        commit = button(row, "确认输入", () -> commitPreview());
        LinearLayout tools = new LinearLayout(ui); root.addView(tools);
        button(tools, "设置 / 授权", () -> leaveForExternalUi(() ->
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        button(tools, "模型管理", () -> leaveForExternalUi(() ->
            startActivity(new Intent(this, ModelManagementActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        button(tools, "切换键盘", () -> leaveForExternalUi(() -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));
        render(); return root;
    }
    /** Hide the actual IME, not just our eligibility flag. Dismissing the
     * picker leaves it hidden; tapping the editor shows a fresh input view.
     * Neither picker dismissal nor a failed external launch revives old text. */
    private void leaveForExternalUi(Runnable launch) {
        visible = false; session = null;
        if (controller != null) controller.leaveForExternalUi(() -> requestHideSelf(0), launch);
        else { requestHideSelf(0); try { launch.run(); } catch (RuntimeException ignored) {} }
        render();
    }
    private Button button(LinearLayout row, String label, Runnable action) {
        Button b = new Button(row.getContext()); b.setText(label); b.setAllCaps(false); b.setTextSize(12);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setOnClickListener(v -> action.run());
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1)); return b;
    }
    private static String key(EditorInfo info) {
        return info == null ? "" : String.valueOf(info.packageName) + ":" + info.fieldId + ":"
            + String.valueOf(info.fieldName) + ":" + info.inputType + ":" + info.imeOptions;
    }
    private void invalidate() { if (controller != null) controller.invalidate(); session = null; }
    private void renewSession() {
        invalidate();
        EditorInfo info = getCurrentInputEditorInfo();
        if (alive && visible && controller != null && info != null)
            session = controller.begin(info.inputType, info.imeOptions, key(info));
    }
    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        // Always invalidate even when package/fieldId is unchanged.
        renewSession(); render();
    }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        visible = true; renewSession(); render();
    }
    @Override public void onWindowShown() { super.onWindowShown(); render(); }
    @Override public void onFinishInputView(boolean finishingInput) {
        visible = false; invalidate(); render(); super.onFinishInputView(finishingInput);
    }
    @Override public void onFinishInput() {
        visible = false; invalidate(); render(); super.onFinishInput();
    }
    @Override public void onWindowHidden() {
        visible = false; invalidate(); render(); super.onWindowHidden();
    }
    @Override public void onUnbindInput() {
        visible = false; invalidate(); render(); super.onUnbindInput();
    }
    @Override public void onDestroy() {
        alive = false; visible = false; invalidate();
        if (graph != null) { graph.coordinator().removeListener(refresh); graph.readiness().removeListener(refresh); }
        refresh.detach();
        super.onDestroy();
    }
    private boolean permitted() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    private boolean eligible() {
        EditorInfo info = getCurrentInputEditorInfo();
        return alive && visible && session != null && session.valid() && info != null
            && session.fieldKey.equals(key(info)) && !ImeFieldPolicy.isSensitive(info.inputType, info.imeOptions);
    }
    private void startRecording() {
        if (!eligible() || !isInputViewShown() || !permitted()) { render(); return; }
        controller.start(session, language.getSelectedItem().toString()); render();
    }
    private void commitPreview() {
        if (!eligible() || !isInputViewShown()) { invalidate(); render(); return; }
        EditorInfo info = getCurrentInputEditorInfo();
        InputConnection connection = getCurrentInputConnection();
        controller.commit(session, key(info), info.inputType, info.imeOptions,
            connection == null ? null : text -> connection.commitText(text, 1));
        render();
    }
    private void render() {
        if (status == null) return;
        boolean allowed = eligible(), busy = controller != null && controller.busy();
        status.setText(controller == null ? setupError : !allowed ? "当前会话不可用或为敏感输入框。"
            : !permitted() ? "请点击设置 / 授权，在主App授予麦克风权限。"
            : busy && !controller.ownsTask() ? "正在等待应用或上一会话处理完成……" : session.status());
        preview.setText(allowed ? session.preview() : "");
        record.setEnabled(allowed && permitted() && !busy);
        stop.setEnabled(allowed && controller.capturing());
        cancel.setEnabled(allowed);
        commit.setEnabled(allowed && !busy && !session.preview().isEmpty());
        language.setEnabled(!busy);
    }
    private static final class UiRefresh implements TaskCoordinator.Listener, ModelReadiness.Listener, Runnable {
        private final WeakReference<AsrImeService> target;
        private final Handler main = new Handler(Looper.getMainLooper());
        UiRefresh(AsrImeService service) { target = new WeakReference<>(service); }
        public void onChange() { main.post(this); }
        void detach() { main.removeCallbacks(this); }
        public void run() {
            AsrImeService service = target.get();
            if (service != null && service.alive) service.render();
        }
    }
}
