package org.llmasr.minimal;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.platform.ActivityWindow;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.modelmanagement.ModelUiText;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.transcription.AppState;
import org.llmasr.minimal.transcription.ResultState;
import org.llmasr.minimal.transcription.TextExportController;
import org.llmasr.minimal.transcription.TextExportPage;

/** Three bounded logical pages; asynchronous owners remain in the shared graph. */
public final class MainActivity extends Activity {
    private static final int MIC_PERMISSION=12, WAVE_FIRST=20, WAVE_LAST=99;
    private static int waveHighWater=WAVE_FIRST-1;
    private enum Page { HOME, TRANSCRIBE, IME_SETTINGS }
    private Page page=Page.HOME;
    private LinearLayout home, transcriptionPage, imeSettings;
    private TextExportPage<Uri> exportPage;
    private volatile boolean foreground;
    private boolean pendingHome;
    private long captureDialogGeneration;
    private AlertDialog captureDialog;
    private int wavePending;
    private Uri waveDeferred;
    private boolean waveInvalid;
    private Button stopRecording, cancelRecording, exportTXT, microphonePermission;
    private final ArrayList<Button> buttons=new ArrayList<>();
    private TextView status, result, modelSummary, exportStatus, globalTask, globalExportStatus;
    private Spinner language;
    private AppGraph graph;
    private final ActivityWindow.BackRegistration windowBack = new ActivityWindow.BackRegistration(this, this::onBackPressed);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { AppGraph.install(getApplicationContext()); graph=AppGraph.get(); }
        catch (IOException ioe) {
            TextView failure=new TextView(this); failure.setText("初始化失败，请重新安装完整应用。");
            setContentView(failure); ActivityWindow.configure(this, failure); return;
        }
        restoreUi(state);
        exportPage=new TextExportPage<>(graph.textExport());
        LinearLayout root=column(); int pad=dp(16); root.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        ActivityWindow.configure(this, scroll);
        globalTask=text(root,"",16);
        globalExportStatus=text(root,"",16);
        home=column(); transcriptionPage=column(); imeSettings=column();
        root.addView(home); root.addView(transcriptionPage); root.addView(imeSettings);
        text(home,"离线语音转写",24);
        modelSummary=text(home,"",16);
        navigation(home,"转写",() -> requestPage(Page.TRANSCRIBE));
        navigation(home,"模型",this::openModelManagement);
        navigation(home,"输入法",() -> requestPage(Page.IME_SETTINGS));
        navigation(home,"日志",this::openLogs);
        disclosure(home,"使用说明","本应用不联网；模型与推理共享内存实例。CPU 推理需要较多内存，存储空间不是 RAM。无 VAD，静音可能产生幻觉。系统可能在离开后结束进程，不保证后台持续运行。");
        buildTranscribe(); buildImeSettings();
        if(state!=null) {
            String saved=state.getString("language");
            language.setSelection("English".equals(saved)?1:"auto".equals(saved)?2:0);
        }
        displayPage();
    }
    private void restoreUi(Bundle state) {
        if(state==null)return;
        try { page=Page.valueOf(state.getString("page","HOME")); }
        catch(IllegalArgumentException | NullPointerException e) { page=Page.HOME; }
        int high=state.getInt("waveHighWater",WAVE_FIRST-1);
        synchronized(MainActivity.class) {
            if(high<WAVE_FIRST-1 || high>WAVE_LAST) waveInvalid=true;
            else waveHighWater=Math.max(waveHighWater,high);
        }
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); return c; }
    private TextView text(LinearLayout root,String value,int size) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); root.addView(t); return t;
    }
    private Button navigation(LinearLayout root,String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setContentDescription(label); b.setOnClickListener(v -> action.run()); root.addView(b); return b;
    }
    private void disclosure(LinearLayout root,String label,String value) {
        TextView note=text(root,value,15); note.setVisibility(View.GONE);
        navigation(root,label,() -> note.setVisibility(note.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
    }
    private void addButton(LinearLayout root,String label,Runnable action) { buttons.add(navigation(root,label,action)); }
    private void buildTranscribe() {
        navigation(transcriptionPage,"返回首页",this::onBackPressed); text(transcriptionPage,"转写 · 最长30秒",22);
        language=new Spinner(this); language.setMinimumHeight(dp(48)); language.setContentDescription("转写语言");
        language.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"中文","英语","自动"}));
        transcriptionPage.addView(language);
        addButton(transcriptionPage,"录音",this::requestRecording);
        stopRecording=navigation(transcriptionPage,"停止并转写",() -> { RecordingControl r=graph.asrOperation().recording(); if(r!=null)r.stop(); });
        cancelRecording=navigation(transcriptionPage,"丢弃录音",() -> {
            RecordingControl r=graph.asrOperation().recording(); if(r!=null) reportCancellation(r.cancel()); refresh();
        });
        addButton(transcriptionPage,"选文件",() -> guardCapture(this::chooseWave));
        addButton(transcriptionPage,"示例",() -> { if(canTranscribe())graph.asrOperation().startSample(selectedLanguage()); });
        status=text(transcriptionPage,"",16);
        result=text(transcriptionPage,"",20); result.setTextIsSelectable(true);
        addButton(transcriptionPage,"复制",() -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ASR",result.getText()));
            toast("已复制");
        });
        addButton(transcriptionPage,"编辑",this::editResult);
        addButton(transcriptionPage,"分享",() -> guardCapture(this::shareText));
        Button exportBtn=navigation(transcriptionPage,"导出 TXT",() -> guardCapture(this::beginTextExport));
        exportTXT=exportBtn; // Independent of the ASR busy button collection.
        exportStatus=text(transcriptionPage,"",16);
        text(transcriptionPage,"已开始的外部写入不能撤回；失败可能留下不完整文件。",15);
        addButton(transcriptionPage,"清除结果…",() -> new AlertDialog.Builder(this).setTitle("清除应用内结果？")
            .setMessage("清除结果、编辑文本和遗留临时录音，撤销尚未开始的 TXT 写入。已开始的外部写入不能撤回；不删除模型、外部文件或剪贴板。删除不是安全擦除。")
            .setNegativeButton("保留",null).setPositiveButton("清除",(d,w) -> graph.asrOperation().clearResults()).show());
        disclosure(transcriptionPage,"格式与隐私","WAV：16kHz、单声道、PCM16，0.1–30秒。分享接收方或云存储提供方可能联网同步。TXT 保存当次快照；编辑但未导出的内容不持久化。离开转写页会撤销未提交的文件选择和 TXT 写入。");
    }
    private void buildImeSettings() {
        navigation(imeSettings,"返回首页",this::onBackPressed); text(imeSettings,"输入法",22);
        text(imeSettings,"授权不开始录音；启用和选择由您在系统中决定。",16);
        microphonePermission=navigation(imeSettings,"授权麦克风",() -> guardCapture(() -> {
            if(!permitted())requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);
            else toast("麦克风已授权");
        }));
        navigation(imeSettings,"启用输入法",() -> guardCapture(() -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)); }
            catch(ActivityNotFoundException e) { toast("请在系统设置中手动启用语音输入法。"); }
        }));
        navigation(imeSettings,"切换键盘",() -> guardCapture(() -> {
            android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showInputMethodPicker();
        }));
        disclosure(imeSettings,"首次使用与隐私","1. 授权麦克风；2. 在系统设置启用；3. 切换到本输入法后手动录音。敏感输入框不可录音；临时预览需确认才输入。缺少模型时，返回首页→模型。");
    }
    private boolean permitted() { return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED; }
    private boolean canTranscribe() { return foreground && page==Page.TRANSCRIBE && !graph.coordinator().isBusy(); }
    private String selectedLanguage() { int n=language.getSelectedItemPosition(); return n==1?"English":n==2?"auto":"Chinese"; }
    private void requestPage(Page target) { if(page!=target)guardCapture(() -> changePage(target)); }
    private void changePage(Page target) {
        if(target==Page.HOME)revokeCaptureDialog();
        if(page==target)return;
        if(page==Page.TRANSCRIBE) {
            revokeWave(); exportPage.destroy(); exportPage=new TextExportPage<>(graph.textExport());
        }
        page=target; exportPage.foreground(foreground && page==Page.TRANSCRIBE); displayPage(); refresh();
    }
    private void displayPage() {
        home.setVisibility(page==Page.HOME?View.VISIBLE:View.GONE);
        transcriptionPage.setVisibility(page==Page.TRANSCRIBE?View.VISIBLE:View.GONE);
        imeSettings.setVisibility(page==Page.IME_SETTINGS?View.VISIBLE:View.GONE);
    }
    /** Voluntary navigation guards before leaving; platform pause always cancels capture. */
    private void guardCapture(Runnable action) {
        RecordingControl r=graph.asrOperation().recording();
        if(r==null || r.committed()) { action.run(); return; }
        if(r.cancelled()) { graph.appState().setLastStatus("录音已取消，等待收尾。"); action.run(); return; }
        revokeCaptureDialog();
        if(captureDialogGeneration==Long.MAX_VALUE)return; // Never reuse an exhausted dialog capability.
        final long generation=captureDialogGeneration;
        captureDialog=new AlertDialog.Builder(this).setTitle("结束录音？")
            .setMessage("停止并转写会留在转写页；丢弃后继续此动作。取消需等待麦克风真实释放。")
            .setNegativeButton("继续录音",null)
            .setNeutralButton("停止并转写",(d,w) -> {
                if(!consumeCaptureDialog(generation,r))return;
                if(r.committed())graph.appState().setLastStatus("已开始转写，将继续。"); else r.stop();
                changePage(Page.TRANSCRIBE); refresh();
            })
            .setPositiveButton("丢弃并继续",(d,w) -> {
                if(!consumeCaptureDialog(generation,r))return;
                reportCancellation(r.cancel()); action.run(); refresh();
            }).create();
        captureDialog.setOnDismissListener(dialog -> { if(generation==captureDialogGeneration)revokeCaptureDialog(); });
        try { captureDialog.show(); }
        catch(RuntimeException unavailable) { revokeCaptureDialog(); }
    }
    private boolean consumeCaptureDialog(long generation,RecordingControl expected) {
        if(generation!=captureDialogGeneration || captureDialog==null || !captureDialog.isShowing() || !foreground || isDestroyed() || isFinishing()
            || graph.asrOperation().recording()!=expected || expected.cancelled())return false;
        revokeCaptureDialog(); // Consume before stop/cancel/navigation or any external callback.
        return true;
    }
    private void revokeCaptureDialog() {
        if(captureDialogGeneration!=Long.MAX_VALUE)captureDialogGeneration++;
        AlertDialog old=captureDialog; captureDialog=null;
        if(old!=null)try { old.dismiss(); } catch(RuntimeException ignored) { /* Token is already invalid. */ }
    }
    private void reportCancellation(boolean accepted) {
        graph.appState().setLastStatus(accepted?"正在取消录音，等待麦克风释放……":"已开始转写，将继续；本次操作未取消推理。");
    }
    private void openModelManagement() { if(page==Page.HOME)guardCapture(() -> startActivity(new Intent(this,ModelManagementActivity.class))); }
    private void openLogs() { if(page==Page.HOME)guardCapture(() -> startActivity(new Intent(this,LogsActivity.class))); }
    private void shareText() {
        if(page!=Page.TRANSCRIBE || !foreground)return;
        if(graph.appState().lastText().isEmpty()) { graph.appState().setLastStatus("暂无文本可分享。"); return; }
        Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,graph.appState().lastText());
        try { startActivity(Intent.createChooser(share,"分享当前文本")); }
        catch(ActivityNotFoundException e) { graph.appState().setLastStatus("没有可接收文本的应用。"); }
    }
    private void beginTextExport() {
        if(page!=Page.TRANSCRIBE || !foreground)return;
        TextExportController.Ticket ticket=exportPage.begin(graph.appState().resultState());
        if(ticket==null) { toast("导出尚未结束、文本为空或超过上限，请重试。"); return; }
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE);
        save.putExtra(Intent.EXTRA_TITLE,"qwen-asr-"+System.currentTimeMillis()+".txt");
        try { startActivityForResult(save,ticket.requestCode); }
        catch(RuntimeException e) { exportPage.onResult(ticket.requestCode,null); toast("系统没有可用的文件选择器。"); }
    }
    private void chooseWave() {
        if(!canTranscribe() || wavePending!=0 || waveDeferred!=null)return;
        synchronized(MainActivity.class) {
            if(waveInvalid || waveHighWater>=WAVE_LAST) { toast("文件选择次数已达上限，请重启应用。"); return; }
            wavePending=++waveHighWater;
        }
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent,wavePending); }
        catch(RuntimeException e) { revokeWave(); toast("无法打开文件选择器。"); }
    }
    private void revokeWave() { wavePending=0; waveDeferred=null; }
    private void consumeWave() {
        if(!foreground || page!=Page.TRANSCRIBE || waveDeferred==null)return;
        Uri uri=waveDeferred; revokeWave();
        if(!graph.coordinator().isBusy())graph.asrOperation().startWave(uri,selectedLanguage());
        else toast("任务处理中；未转写，请稍后重新选择。");
    }
    private void refresh() {
        AppState s=graph.appState();
        boolean busy=graph.coordinator().isBusy(); RecordingControl r=graph.asrOperation().recording();
        ModelManagementState.Snapshot importing=graph.modelManagement().importSnapshot();
        org.llmasr.minimal.modelmanagement.ModelOperationControl.Operation modelOp=graph.modelManagement().opControl().peekActive();
        String active=r!=null && !r.committed() ? r.cancelled()?"正在取消录音，等待麦克风释放":"录音中 · 最长30秒"
            : modelOp!=null?"模型处理中" : busy?"任务处理中，任务继续":"";
        ModelManagementState.Snapshot modelState=graph.modelManagement().state().current();
        String modelNotice=modelState.phase==ModelManagementState.Phase.FAILED || modelState.cleanupOutcome==ModelManagementState.Outcome.FAILED
            ? "\n模型最近状态："+ModelUiText.task(modelState) : "";
        globalTask.setText(active+(importing==null?"":"\n模型导入："+ModelUiText.task(importing))+modelNotice+"\n转写最近状态："+s.lastStatus());
        String modelName="未选用";
        for(org.llmasr.minimal.model.ModelCatalog.Row row:graph.modelManagement().catalogRows())if(row.selected)modelName=row.deployment.policy.displayName;
        modelSummary.setText("模型："+modelName+" · "+ModelUiText.readiness(graph.readiness().state()));
        microphonePermission.setText(permitted()?"已授权":"授权麦克风");
        status.setText(s.lastStatus()); result.setText(s.lastText()); setBusy(busy);
        TextExportController.State exportState=graph.textExport().state();
        globalExportStatus.setText(exportState.phase==TextExportController.Phase.IDLE ? "" : "TXT："+TextExportController.statusText(exportState)
            +"\n已开始的外部写入不能撤回；失败可能留下不完整文件。");
        if(exportTXT!=null) {
            exportTXT.setEnabled(foreground && page==Page.TRANSCRIBE && !s.lastText().isEmpty() && !exportState.busy());
            exportStatus.setText(TextExportController.statusText(exportState));
            appStateListener.whileExportBusy(foreground && exportState.busy());
        }
    }
    private void setBusy(boolean value) {
        for(Button b:buttons)b.setEnabled(!value); language.setEnabled(!value);
        RecordingControl r=graph.asrOperation().recording();
        stopRecording.setEnabled(value && r!=null && !r.stopped() && !r.cancelled() && !r.committed());
        cancelRecording.setEnabled(value && r!=null && !r.cancelled() && !r.committed());
        if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void requestRecording() {
        if(!canTranscribe())return;
        if(!permitted()) { guardCapture(() -> requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},MIC_PERMISSION)); return; }
        if(!graph.readiness().snapshot().ready()) {
            graph.appState().setLastStatus("模型未准备，请返回首页→模型，校验或恢复证明。"); return;
        }
        graph.asrOperation().startRecording(selectedLanguage());
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==MIC_PERMISSION && graph!=null)graph.appState().setLastStatus(grants.length>0 && grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED
            ?"已授权麦克风；请手动点击录音。":"麦克风未授权；仍可选 WAV，可在系统应用权限中开启。");
    }
    private void editResult() {
        final ResultState.Snapshot original=graph.appState().resultState().begin();
        if(original.isEmpty()) { graph.appState().setLastStatus("暂无文本可编辑。"); return; }
        EditText editor=new EditText(this); editor.setText(original.text); editor.setMinLines(4);
        editor.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100000)});
        new AlertDialog.Builder(this).setTitle("编辑结果（原始推理报告保留）").setView(editor)
            .setNegativeButton("取消",null).setPositiveButton("应用",(d,w) -> {
                final String edited=editor.getText().toString(); graph.asrOperation().editResult(original.revision,edited);
            }).show();
    }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        if(page==Page.TRANSCRIBE && exportPage!=null) {
            Uri target=(code==RESULT_OK && data!=null)?data.getData():null;
            if(exportPage.onResult(request,target))return;
        }
        if(request!=wavePending || wavePending==0 || page!=Page.TRANSCRIBE)return;
        wavePending=0; // Consume before any callback/foreground transition; never reuse this code.
        Uri uri=code==RESULT_OK && data!=null && data.getClipData()==null?data.getData():null;
        if(uri!=null && "content".equals(uri.getScheme()))waveDeferred=uri;
        consumeWave();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); revokeCaptureDialog(); pendingHome=true; }
    private void consumeHomeEntry() {
        if(!pendingHome)return;
        pendingHome=false;
        RecordingControl r=graph.asrOperation().recording();
        if(r!=null && r.cancelled())graph.appState().setLastStatus("录音已取消，等待收尾。");
        else if(r!=null && r.committed())graph.appState().setLastStatus("已开始转写，将继续。");
        else if(graph.coordinator().isBusy())graph.appState().setLastStatus("任务继续，等待实际收尾。");
        changePage(Page.HOME);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("page",page.name()); if(language!=null)out.putString("language",selectedLanguage());
        synchronized(MainActivity.class) { out.putInt("waveHighWater",waveInvalid?-1:waveHighWater); }
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if(graph==null) { super.onBackPressed(); return; }
        guardCapture(() -> {
            if(page!=Page.HOME) { if(graph.coordinator().isBusy())toast("任务继续，结果稍后可查看。"); changePage(Page.HOME); }
            else if(graph.coordinator().isBusy() || graph.textExport().state().busy())new AlertDialog.Builder(this).setTitle("离开应用？")
                .setMessage("任务将继续；离开后系统可能结束进程。")
                .setNegativeButton("留在首页",null).setPositiveButton("离开",(d,w) -> MainActivity.super.onBackPressed()).show();
            else super.onBackPressed();
        });
    }
    @Override protected void onResume() {
        super.onResume(); windowBack.resumed(); foreground=true;
        if(graph==null)return;
        graph.appState().addListener(appStateListener); graph.coordinator().addListener(appStateListener);
        graph.readiness().addListener(appStateListener); graph.textExport().addListener(appStateListener);
        graph.modelManagement().state().addListener(appStateListener);
        consumeHomeEntry(); // Before picker/queued admission: a new App entry cannot revive the old page.
        exportPage.foreground(page==Page.TRANSCRIBE); consumeWave(); refresh();
    }
    @Override protected void onPause() {
        windowBack.paused(); foreground=false; revokeCaptureDialog();
        if(graph!=null) {
            graph.appState().removeListener(appStateListener); graph.coordinator().removeListener(appStateListener);
            graph.readiness().removeListener(appStateListener); graph.textExport().removeListener(appStateListener);
            graph.modelManagement().state().removeListener(appStateListener);
            exportPage.foreground(false); appStateListener.detach(); graph.asrOperation().cancelRecording();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onPause();
    }
    @Override protected void onDestroy() {
        revokeCaptureDialog(); revokeWave(); if(exportPage!=null)exportPage.destroy(); appStateListener.detach(); super.onDestroy();
    }
    private final UiRefresh appStateListener=new UiRefresh(this);
    private static final class UiRefresh implements AppState.Listener, ModelReadiness.Listener, TaskCoordinator.Listener, TextExportController.Listener, ModelManagementState.Listener, Runnable {
        private final java.lang.ref.WeakReference<MainActivity> target;
        private final Handler main=new Handler(Looper.getMainLooper());
        UiRefresh(MainActivity a) { target=new java.lang.ref.WeakReference<>(a); }
        @Override public synchronized void onChange() { main.removeCallbacks(this); main.post(this); }
        synchronized void whileExportBusy(boolean busy) { main.removeCallbacks(this); if(busy)main.postDelayed(this,250L); }
        synchronized void detach() { main.removeCallbacks(this); }
        @Override public void run() {
            MainActivity a=target.get(); if(a!=null && a.foreground && !a.isDestroyed() && !a.isFinishing())a.refresh();
        }
    }
}
