package org.llmasr.minimal;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int MODEL_TREE=10, AUDIO_FILE=11;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile String lastStatus="尚未校验模型。首次请选择含 7 个清单文件的模型目录，或使用开发部署脚本。", lastText="";
    private static java.lang.ref.WeakReference<MainActivity> current=new java.lang.ref.WeakReference<>(null);
    private String requestId;
    private boolean inferenceReported;
    private final ArrayList<Button> buttons=new ArrayList<>();
    private TextView status, result;
    private Spinner language;
    private static volatile boolean verified=false;
    private File modelDir;
    private static native byte[] transcribe(String config, String wav, String language, String cache);
    private interface Job { void run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        modelDir=new File(getFilesDir(),"model"); modelDir.mkdirs();
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        TextView title=new TextView(this); title.setText("Qwen3-ASR · 最小离线原型"); title.setTextSize(22); root.addView(title);
        TextView note=new TextView(this);
        note.setText("arm64 CPU / FP16 权重 · 约 1.6 GB 模型，推理约 3.1 GiB 内存。\n不联网、不录音。仅支持 16kHz 单声道 PCM16 WAV（0.1–30 秒）。导入后请保持界面前台；每次转写重新加载模型。静音可能幻觉输出，本版无 VAD。模型导入后占用应用内部存储。"); root.addView(note);
        addButton(root,"1. 导入模型目录", () -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent,MODEL_TREE);
        });
        addButton(root,"2. 校验已部署模型", () -> launch(() -> { verifyModel(); showStatus("模型 SHA-256 全部通过，可转写。"); }));
        language=new Spinner(this);
        language.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Chinese","English","auto"}));
        root.addView(language);
        addButton(root,"3. 转写内置中文示例", () -> {
            final String lang=language.getSelectedItem().toString();
            launch(() -> { try(InputStream in=getAssets().open("sample.wav")) { runAudio(in,lang,"public-zh-example"); } });
        });
        addButton(root,"选择 WAV 文件转写", () -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(intent,AUDIO_FILE);
        });
        addButton(root,"复制转写文本", () -> {
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ASR",result.getText()));
            Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();
        });
        status=new TextView(this); root.addView(status);
        result=new TextView(this); result.setTextSize(20); result.setTextIsSelectable(true); root.addView(result);
    }
    private void addButton(LinearLayout root,String text,Runnable action) {
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); root.addView(b); buttons.add(b);
    }
    private static void refresh() {
        new Handler(Looper.getMainLooper()).post(() -> {
            MainActivity a=current.get();
            if(a!=null && !a.isDestroyed()) {
                a.status.setText(lastStatus); a.result.setText(lastText); a.setBusy(RUNNING.get());
            }
        });
    }
    @Override protected void onResume() { super.onResume(); current=new java.lang.ref.WeakReference<>(this); refresh(); }
    private void showStatus(String s) { lastStatus=s; refresh(); }
    private void setBusy(boolean value) {
        for(Button b:buttons)b.setEnabled(!value); language.setEnabled(!value);
        if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void launch(Job job) {
        // Process-wide ownership spans ALL Java file IO, model import, JNI and reporting.
        if(!RUNNING.compareAndSet(false,true)) { showStatus("已有任务执行中，请等待。"); return; }
        requestId=UUID.randomUUID().toString(); inferenceReported=false;
        lastText=""; showStatus("执行中，请保持应用在前台……");
        worker.execute(() -> {
            try {
                JSONObject pending=new JSONObject(); pending.put("state","pending"); pending.put("success",false); saveReport(pending);
                job.run();
                // Import/verification actions also replace pending with a terminal non-inference record.
                if(!inferenceReported) {
                    JSONObject r=new JSONObject(); r.put("state","complete"); r.put("success",true); r.put("kind","model-operation"); saveReport(r);
                }
            } catch(Exception | LinkageError e) {
                showStatus("失败："+e.getMessage());
                try { JSONObject r=new JSONObject(); r.put("state","failed"); r.put("success",false); r.put("error",String.valueOf(e)); saveReport(r); }
                catch(Exception persistence) { showStatus("失败且结果无法持久化："+persistence.getMessage()); }
            } finally {
                new File(getFilesDir(),"input-"+requestId+".wav").delete();
                RUNNING.set(false); refresh();
            }
        });
    }
    private JSONArray manifest() throws Exception {
        try(InputStream in=getAssets().open("model-manifest.json")) {
            return new JSONObject(new String(readSmall(in),StandardCharsets.UTF_8)).getJSONArray("files");
        }
    }
    private static byte[] readSmall(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n;
        while((n=in.read(b))!=-1) { if(out.size()+n>1048576)throw new IOException("Metadata too large"); out.write(b,0,n); } return out.toByteArray();
    }
    private static String hex(byte[] data) { StringBuilder s=new StringBuilder(); for(byte b:data)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString(); }
    private void checkFile(File file,JSONObject entry) throws Exception {
        if(!file.isFile() || file.length()!=entry.getLong("bytes"))throw new IOException("缺少或大小不符："+file.getName());
        MessageDigest h=MessageDigest.getInstance("SHA-256"); byte[] b=new byte[1024*1024]; int n;
        try(InputStream in=new FileInputStream(file)) { while((n=in.read(b))!=-1)h.update(b,0,n); }
        if(!hex(h.digest()).equals(entry.getString("sha256")))throw new IOException("SHA-256 不符："+file.getName());
    }
    private void verifyModel() throws Exception {
        verified=false; JSONArray entries=manifest();
        for(int i=0;i<entries.length();i++) { JSONObject e=entries.getJSONObject(i); showStatus("校验 "+e.getString("file")); checkFile(new File(modelDir,e.getString("file")),e); }
        verified=true;
    }
    private void importModel(Uri tree) throws Exception {
        verified=false;
        Map<String,Uri> children=new HashMap<>();
        JSONArray entries=manifest(); Set<String> expected=new HashSet<>();
        for(int i=0;i<entries.length();i++)expected.add(entries.getJSONObject(i).getString("file"));
        Uri list=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor c=getContentResolver().query(list,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)) {
            if(c==null)throw new IOException("无法列出目录");
            int seen=0;
            while(c.moveToNext()) {
                if(++seen>10000)throw new IOException("目录超过10000个条目，请选择专用模型目录");
                String name=c.getString(1); if(!expected.contains(name))continue;
                if(children.containsKey(name))throw new IOException("重复文件名："+name);
                children.put(name,DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)));
            }
        }
        for(int i=0;i<entries.length();i++) {
            JSONObject e=entries.getJSONObject(i); String name=e.getString("file"); File dest=new File(modelDir,name);
            try { checkFile(dest,e); continue; } catch(IOException absent) { /* resumable validated files */ }
            if(!children.containsKey(name))throw new IOException("目录缺少："+name);
            if(modelDir.getUsableSpace()<e.getLong("bytes")+64L*1024*1024)throw new IOException("存储空间不足："+name);
            showStatus("复制并校验 "+name+"（大文件可能需数分钟）");
            File part=new File(modelDir,name+".part");
            try {
                try(InputStream in=getContentResolver().openInputStream(children.get(name)); OutputStream out=new FileOutputStream(part)) {
                    if(in==null)throw new IOException("无法读取："+name);
                    byte[] b=new byte[1024*1024]; int n; long count=0;
                    while((n=in.read(b))!=-1) { count+=n; if(count>e.getLong("bytes"))throw new IOException("文件超出清单大小："+name); out.write(b,0,n); }
                }
                checkFile(part,e);
                if(!part.renameTo(dest))throw new IOException("无法原子替换："+name);
            } finally { if(part.exists())part.delete(); }
        }
        verifyModel(); showStatus("导入成功，全部 SHA-256 已核验。");
    }
    private void runAudio(InputStream in,String lang,String source) throws Exception {
        File wav=new File(getFilesDir(),"input-"+requestId+".wav");
        double seconds=WaveInput.canonicalize(in,wav);
        if(!verified)verifyModel();
        showStatus("模型加载 / 转写中（CPU，首次可能需要数十秒）……");
        System.loadLibrary("qwen_asr_jni");
        byte[] output=transcribe(new File(modelDir,"config.json").getAbsolutePath(),wav.getAbsolutePath(),lang,getCacheDir().getAbsolutePath());
        if(output==null)throw new IOException("Native returned null");
        String response=new String(output,StandardCharsets.UTF_8); int line=response.indexOf('\n');
        String[] metrics=response.substring(0,line).split(" "); String raw=response.substring(line+1); String text=AsrText.display(raw);
        double load=Double.parseDouble(metrics[0]), infer=Double.parseDouble(metrics[1]);
        JSONObject report=new JSONObject(); report.put("success",true); report.put("state","complete"); report.put("kind","inference"); report.put("source",source); report.put("language",lang);
        report.put("text",text); report.put("raw",raw); report.put("load_s",load); report.put("inference_s",infer);
        report.put("audio_s",seconds); report.put("rtf",infer/seconds); report.put("generated_tokens",Integer.parseInt(metrics[2]));
        report.put("truncated",false); report.put("uid",android.os.Process.myUid()); report.put("pid",android.os.Process.myPid());
        report.put("model_manifest_sha256",assetDigest("model-manifest.json")); report.put("audio_sha256",fileDigest(wav));
        saveReport(report);
        inferenceReported=true;
        lastText=text; refresh();
        showStatus(String.format(Locale.ROOT,"完成 · 加载 %.2fs / 推理 %.2fs / RTF %.3f\n每请求释放模型；非 warm 测试。",load,infer,infer/seconds));
    }
    private String assetDigest(String name) throws Exception { try(InputStream in=getAssets().open(name)) { return hex(MessageDigest.getInstance("SHA-256").digest(readSmall(in))); } }
    private static String fileDigest(File file) throws Exception { try(InputStream in=new FileInputStream(file)) { return hex(MessageDigest.getInstance("SHA-256").digest(readSmall(in))); } }
    private void saveReport(JSONObject report) throws Exception {
        report.put("request_id",requestId); report.put("timestamp_ms",System.currentTimeMillis());
        File part=new File(getFilesDir(),"last-result-"+requestId+".part");
        try(OutputStream out=new FileOutputStream(part)) { out.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
        if(!part.renameTo(new File(getFilesDir(),"last-result.json")))throw new IOException("Cannot save result");
    }
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        if(code!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        if(request==MODEL_TREE)launch(() -> importModel(uri));
        else if(request==AUDIO_FILE) { String lang=language.getSelectedItem().toString(); launch(() -> {
            try(InputStream in=getContentResolver().openInputStream(uri)) { if(in==null)throw new IOException("无法读取音频"); runAudio(in,lang,"user-selected-wav"); }
        }); }
    }
    @Override public void onBackPressed() {
        if(RUNNING.get()) { Toast.makeText(this,"任务执行中；本版不支持安全中断，请等待完成。",Toast.LENGTH_LONG).show(); return; }
        super.onBackPressed();
    }
    @Override protected void onDestroy() { if(current.get()==this)current.clear(); super.onDestroy(); }
}
