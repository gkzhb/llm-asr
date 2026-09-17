package org.llmasr.minimal;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.modelmanagement.ModelUiText;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.transcription.AppState;
import org.llmasr.minimal.transcription.ResultState;
import org.llmasr.minimal.transcription.TextExportController;
import org.llmasr.minimal.transcription.TextExportPage;

public final class MainActivity extends Activity {
    private static final int AUDIO_FILE=11, MIC_PERMISSION=12;
    private TextExportPage<Uri> exportPage;
    private volatile boolean foreground;
    private Button stopRecording, cancelRecording;
    private final ArrayList<Button> buttons=new ArrayList<>();
    private TextView status, result, modelSummary, exportStatus;
    private Button exportTXT;
    private boolean navigateAfterRecording;
    private Spinner language;
    private AppGraph graph;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (graph == null) {
            try { AppGraph.install(getApplicationContext()); }
            catch (IOException ioe) {
                TextView failure = new TextView(this);
                failure.setText("初始化失败：" + ioe.getMessage() + "\n请重新安装完整APK后重试。");
                setContentView(failure);
                return; // No graph: do not enable inference or dereference the failed install.
            }
            graph = AppGraph.get();
        }
        exportPage = new TextExportPage<>(graph.textExport());
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        TextView title=new TextView(this); title.setText("Qwen3-ASR · 最小离线原型"); title.setTextSize(22); root.addView(title);
        TextView note=new TextView(this);
        note.setText("arm64 CPU / FP16 权重 · 推理约 3.1 GiB 内存。\n本App不联网；分享接收方或导出到云存储的提供方可能联网同步。录音仅在授权后由您点击开始，离开界面取消；最长30秒。文件仅支持 16kHz 单声道 PCM16 WAV（0.1–30 秒）。模型管理在独立页面；每次转写重新加载模型。静音可能幻觉输出，本版无 VAD。模型导入后占用应用内部存储。"); root.addView(note);
        addButton(root,"输入法：授予麦克风权限（不开始录音）", () -> {
            if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);
            else Toast.makeText(this,"麦克风已授权，请手动切换输入法后点击录音。",Toast.LENGTH_LONG).show();
        });
        addButton(root,"输入法：打开系统启用设置", () -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)); }
            catch(ActivityNotFoundException e) { Toast.makeText(this,"请在系统设置中手动启用Qwen ASR语音输入法。",Toast.LENGTH_LONG).show(); }
        });
        addButton(root,"输入法：选择 / 切换键盘", () -> {
            android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showInputMethodPicker();
        });
        modelSummary=new TextView(this); modelSummary.setTextSize(16); root.addView(modelSummary);
        Button models=new Button(this); models.setText("模型管理"); models.setMinHeight((int)(48*getResources().getDisplayMetrics().density));
        models.setOnClickListener(v -> openModelManagement()); root.addView(models); // Always available, even while owner is busy.
        Button logs=new Button(this); logs.setText("运行日志"); logs.setMinHeight((int)(48*getResources().getDisplayMetrics().density));
        logs.setOnClickListener(v -> openLogs()); root.addView(logs);
        language=new Spinner(this);
        language.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Chinese","English","auto"}));
        root.addView(language);
        addButton(root,"转写内置中文示例", () -> graph.asrOperation().startSample(language.getSelectedItem().toString()));
        addButton(root,"选择 WAV 文件转写", () -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(intent,AUDIO_FILE);
        });
        addButton(root,"开始录音（最多30秒）", () -> requestRecording());
        stopRecording=new Button(this); stopRecording.setText("停止录音并转写");
        stopRecording.setOnClickListener(v -> { RecordingControl r=graph.asrOperation().recording(); if(r!=null)r.stop(); }); root.addView(stopRecording);
        cancelRecording=new Button(this); cancelRecording.setText("取消录音并丢弃");
        cancelRecording.setOnClickListener(v -> {
            RecordingControl r=graph.asrOperation().recording();
            if(r!=null) graph.appState().setLastStatus(r.cancel() ? "正在取消录音，等待麦克风释放……" : "已进入转写，无法取消native推理。");
        }); root.addView(cancelRecording);
        addButton(root,"复制转写文本", () -> {
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ASR",result.getText()));
            Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();
        });
        addButton(root,"编辑转写文本", () -> editResult());
        addButton(root,"分享文本（由您选择接收应用）", () -> {
            if(graph.appState().lastText().isEmpty()) { graph.appState().setLastStatus("暂无文本可分享。"); return; }
            Intent share=new Intent(Intent.ACTION_SEND); share.setType("text/plain"); share.putExtra(Intent.EXTRA_TEXT,graph.appState().lastText());
            try { startActivity(Intent.createChooser(share,"分享当前文本")); }
            catch(ActivityNotFoundException e) { graph.appState().setLastStatus("没有可接收文本的应用。"); }
        });
        Button exportBtn = new Button(this); exportBtn.setText("导出 TXT"); exportBtn.setAllCaps(false);
        exportBtn.setMinHeight((int)(48*getResources().getDisplayMetrics().density));
        exportBtn.setOnClickListener(v -> {
            TextExportController.Ticket ticket = exportPage.begin(graph.appState().resultState());
            if (ticket == null) { Toast.makeText(this,"另一次导出尚未结束、文本为空或超过上限，请重试。",Toast.LENGTH_LONG).show(); return; }
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT); save.setType("text/plain"); save.addCategory(Intent.CATEGORY_OPENABLE);
            save.putExtra(Intent.EXTRA_TITLE,"qwen-asr-"+System.currentTimeMillis()+".txt");
            try { startActivityForResult(save,ticket.requestCode); }
            catch(ActivityNotFoundException e) { exportPage.onResult(ticket.requestCode, null); Toast.makeText(this,"系统没有可用的文件选择器。",Toast.LENGTH_LONG).show(); }
        });
        root.addView(exportBtn);
        exportTXT = exportBtn;
        exportStatus=new TextView(this); root.addView(exportStatus);
        TextView exportNote=new TextView(this);
        exportNote.setText("TXT保存快照：清除结果可撤销尚未开始的写入；已经开始的外部写入不能撤回。云存储提供方可能联网同步。"); root.addView(exportNote);
        addButton(root,"清除结果 / 临时录音", () -> new AlertDialog.Builder(this).setTitle("清除应用内结果？")
            .setMessage("删除本应用最后一次结果、编辑文本和遗留临时录音，并撤销尚未开始的TXT写入。已经开始的外部写入不能撤回；不会删除模型、外部导出文件或系统剪贴板；删除不是安全擦除。")
            .setNegativeButton("取消",null).setPositiveButton("清除",(dialog,which) -> graph.asrOperation().clearResults()).show());
        status=new TextView(this); root.addView(status);
        result=new TextView(this); result.setTextSize(20); result.setTextIsSelectable(true); root.addView(result);
    }

    private void addButton(LinearLayout root,String text,Runnable action) {
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); root.addView(b); buttons.add(b);
    }

    private void refresh() {
        AppState s = graph.appState();
        modelSummary.setText(graph.coordinator().isBusy() ? "模型：处理中（共享任务锁占用，可进入模型管理查看）"
            : "模型：" + ModelUiText.readiness(graph.readiness().state()));
        status.setText(s.lastStatus());
        result.setText(s.lastText());
        setBusy(graph.coordinator().isBusy());
        // TXT button follows export/result availability, NOT ASR busy.
        if (exportTXT != null) {
            TextExportController.State exportState = graph.textExport().state();
            exportTXT.setEnabled(foreground && !s.lastText().isEmpty() && !exportState.busy());
            exportStatus.setText(TextExportController.statusText(exportState));
            // Final write admission intentionally invokes no arbitrary observer
            // before provider open. Foreground-only bounded refresh observes it.
            appStateListener.whileExportBusy(foreground && exportState.busy());
        }
        if (navigateAfterRecording && graph.asrOperation().recording() == null) {
            navigateAfterRecording=false;
            startActivity(new Intent(this,ModelManagementActivity.class));
        }
    }

    private final UiRefresh appStateListener = new UiRefresh(this);
    private static final class UiRefresh implements AppState.Listener, ModelReadiness.Listener, TaskCoordinator.Listener, TextExportController.Listener, Runnable {
        private final java.lang.ref.WeakReference<MainActivity> target;
        private final Handler main = new Handler(Looper.getMainLooper());
        UiRefresh(MainActivity activity) { target = new java.lang.ref.WeakReference<>(activity); }
        @Override public synchronized void onChange() { main.removeCallbacks(this); main.post(this); }
        synchronized void whileExportBusy(boolean busy) {
            main.removeCallbacks(this);
            if (busy) main.postDelayed(this, 250L); // At most one foreground refresh.
        }
        synchronized void detach() { main.removeCallbacks(this); }
        @Override public void run() {
            MainActivity activity = target.get();
            if (activity != null && activity.foreground && !activity.isDestroyed() && !activity.isFinishing())
                activity.refresh();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground=true;
        if (graph == null) return;
        graph.appState().addListener(appStateListener);
        graph.coordinator().addListener(appStateListener);
        graph.readiness().addListener(appStateListener);
        graph.textExport().addListener(appStateListener);
        exportPage.foreground(true);
        refresh();
    }
    @Override protected void onPause() {
        foreground=false;
        if (graph != null) {
            navigateAfterRecording=false;
            graph.appState().removeListener(appStateListener);
            graph.coordinator().removeListener(appStateListener);
            graph.readiness().removeListener(appStateListener);
            graph.textExport().removeListener(appStateListener);
            exportPage.foreground(false);
            appStateListener.detach();
            graph.asrOperation().cancelRecording();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    private void openModelManagement() {
        RecordingControl recording=graph.asrOperation().recording();
        if(recording == null || recording.committed()) {
            startActivity(new Intent(this,ModelManagementActivity.class)); return;
        }
        new AlertDialog.Builder(this).setTitle("先结束录音？")
            .setMessage("停止后转写：留在此页等采集交接完成，再打开模型管理查看；也可明确丢弃录音并离开。不会静默取消。")
            .setNegativeButton("留在此页",null)
            .setNeutralButton("停止并等待",(d,w) -> { recording.stop(); navigateAfterRecording=true; refresh(); })
            .setPositiveButton("取消并离开",(d,w) -> { recording.cancel(); startActivity(new Intent(this,ModelManagementActivity.class)); }).show();
    }

    /** Inference may continue while viewing logs; active capture needs explicit consent. */
    private void openLogs() {
        RecordingControl recording = graph.asrOperation().recording();
        if (recording == null || recording.committed()) {
            startActivity(new Intent(this, LogsActivity.class)); return;
        }
        new AlertDialog.Builder(this).setTitle("录音尚未结束")
            .setMessage("打开日志会离开录音页面。请先停止并转写，或明确丢弃本次录音后查看日志。")
            .setNegativeButton("继续录音",null)
            .setNeutralButton("停止并留在此页",(d,w) -> recording.stop())
            .setPositiveButton("丢弃并查看日志",(d,w) -> {
                recording.cancel(); startActivity(new Intent(this, LogsActivity.class));
            }).show();
    }

    private void requestRecording() {
        if(graph.coordinator().isBusy() || !foreground)return;
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},MIC_PERMISSION); return;
        }
        ModelReadiness.State ready = graph.readiness().state();
        if(ready == ModelReadiness.State.NOT_INSTALLED || ready == ModelReadiness.State.INCOMPLETE || ready == ModelReadiness.State.INVALID) {
            graph.appState().setLastStatus("模型不可用，请打开模型管理检查或修复导入。"); return;
        } // UNKNOWN/UNVERIFIED complete models are lazily SHA-verified under the inference owner.
        graph.asrOperation().startRecording(language.getSelectedItem().toString());
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==MIC_PERMISSION) {
            boolean allowed=grants.length>0 && grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
            graph.appState().setLastStatus(allowed ? "已授权麦克风；请点击开始录音。" : "麦克风权限未授予。仍可选择WAV；若已禁止再次询问，可在系统应用权限中手动开启。");
        }
    }

    private void setBusy(boolean value) {
        for(Button b:buttons)b.setEnabled(!value); language.setEnabled(!value);
        RecordingControl r=graph.asrOperation().recording();
        if(stopRecording != null) stopRecording.setEnabled(value && r!=null && !r.stopped());
        if(cancelRecording != null) cancelRecording.setEnabled(value && r!=null && !r.cancelled() && !r.committed());
        if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void editResult() {
        final ResultState.Snapshot original = graph.appState().resultState().begin();
        if(original.isEmpty()) { graph.appState().setLastStatus("暂无文本可编辑。"); return; }
        EditText editor=new EditText(this); editor.setText(original.text); editor.setMinLines(4);
        editor.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100000)});
        new AlertDialog.Builder(this).setTitle("编辑结果（原始推理报告保留）").setView(editor)
            .setNegativeButton("取消",null).setPositiveButton("应用",(d,w) -> {
                final String edited=editor.getText().toString();
                graph.asrOperation().editResult(original.revision, edited);
            }).show();
    }

    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        // Foreign-callback rejection: the page only accepts a request code
        // that matches its active ticket. Replay of old request codes is
        // ignored.
        if (exportPage != null) {
            Uri target = (code == RESULT_OK && data != null) ? data.getData() : null;
            if (exportPage.onResult(request, target)) return;
        }
        if(code != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if(request == AUDIO_FILE) {
            graph.asrOperation().startWave(uri, language.getSelectedItem().toString());
        }
    }

    @Override public void onBackPressed() {
        if (graph == null) { super.onBackPressed(); return; }
        RecordingControl r=graph.asrOperation().recording();
        if(r!=null && r.cancel()) { graph.appState().setLastStatus("正在取消录音，等待麦克风释放……"); return; }
        if(graph.coordinator().isBusy()) { Toast.makeText(this,"任务执行中；本版不支持安全中断，请等待完成。",Toast.LENGTH_LONG).show(); return; }
        super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (graph != null && exportPage != null) exportPage.destroy();
        appStateListener.detach();
        super.onDestroy();
    }
}
