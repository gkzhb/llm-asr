package org.llmasr.minimal

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import org.llmasr.minimal.diagnostics.LogExportController
import org.llmasr.minimal.diagnostics.LogExportPage
import org.llmasr.minimal.diagnostics.RuntimeLogEvent
import org.llmasr.minimal.diagnostics.RuntimeLogStore
import org.llmasr.minimal.diagnostics.RuntimeLogText
import org.llmasr.minimal.platform.ActivityWindow

/** Cache-only foreground viewer; graph's bounded lane owns provider IO. */
class LogsActivity : Activity() {
    private var pickerUnavailable = false
    private var graph: AppGraph? = null
    @Suppress("DEPRECATION")
    private val windowBack = ActivityWindow.BackRegistration(this, this::onBackPressed)
    private var page: LogExportPage<Uri>? = null
    private var body: TextView? = null
    private var summary: TextView? = null
    private var status: TextView? = null
    private var export: Button? = null
    private var scroll: ScrollView? = null
    private var visible = false
    private var rendered: List<RuntimeLogEvent>? = null
    private val refresh = Refresh(this)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    protected open override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        try { AppGraph.install(applicationContext); graph = AppGraph.get() }
        catch (failure: java.io.IOException) {
            val text = TextView(this); text.setText("初始化失败，请重新安装完整应用。"); setContentView(text)
            ActivityWindow.configure(this, text); return
        }
        page = LogExportPage(graph!!.logExports())
        val root = LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(dp(16), dp(12), dp(16), dp(12))
        root.setBackgroundColor(Color.rgb(248, 250, 252))
        scroll = ScrollView(this); scroll!!.setFillViewport(true)
        scroll!!.setBackgroundColor(Color.rgb(248, 250, 252)); scroll!!.addView(root); setContentView(scroll)
        ActivityWindow.configure(this, scroll)
        button(root, "‹ 返回", this::finish)
        text(root, "运行日志", 24)
        text(root, "App / 语音输入法共用日志。日期、毫秒、时区及阶段耗时可见。\n仅保存固定事件，不包含转写正文、录音、外部路径或原始异常。保留最近 1000 条；突然结束进程可能丢失未刷盘日志。", 14)
        export = button(root, "导出日志文件（UTF-8 TXT）", this::beginExport)
        status = text(root, if (saved == null) "" else "页面重建，旧选择已失效，请重新导出。", 15)
        text(root, "导出到您选择的位置；云文件提供方可能联网同步。导出失败可能留下不完整外部文件，不会自动重试或删除该文件。", 14)
        summary = text(root, "", 14)
        button(root, "查看最新日志（底部）") { scroll!!.post { scroll!!.fullScroll(View.FOCUS_DOWN) } }
        body = text(root, "", 13); body!!.setTextIsSelectable(true)
    }
    private fun dp(n: Int): Int = Math.round(n * resources.displayMetrics.density)
    private fun text(root: LinearLayout, value: String, size: Int): TextView {
        val v = TextView(this); v.setText(value); v.setTextSize(size.toFloat())
        v.setTextColor(Color.rgb(17, 24, 39)); v.setPadding(0, dp(8), 0, dp(8)); root.addView(v); return v
    }
    private fun button(root: LinearLayout, title: String, action: Runnable): Button {
        val b = Button(this); b.setText(title); b.setAllCaps(false); b.setMinHeight(dp(48))
        b.setTextColor(Color.rgb(17, 24, 39)); b.setOnClickListener { action.run() }; root.addView(b); return b
    }
    private fun beginExport() {
        if (!visible || page == null) return
        val ticket = page!!.begin(graph!!.runtimeLogStore()!!.snapshot())
        if (ticket == null) { status!!.setText("暂无日志或另一次导出尚未结束，请等待后重试。"); return }
        pickerUnavailable = false
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "qwen-asr-logs-" + System.currentTimeMillis() + ".txt")
        try { startActivityForResult(intent, ticket.requestCode) }
        catch (unavailable: RuntimeException) { page!!.abandon(true); pickerUnavailable = true; status!!.setText(PICKER_UNAVAILABLE) }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    protected open override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (page != null) page!!.result(request, if (result == RESULT_OK && data != null) data.data else null)
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    protected open override fun onResume() {
        super.onResume(); windowBack.resumed(); visible = true
        if (page == null) return
        page!!.foreground(true); refresh.start()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    protected open override fun onPause() {
        windowBack.paused()
        visible = false; if (page != null) page!!.foreground(false)
        refresh.stop(); super.onPause()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    protected open override fun onDestroy() { if (page != null) page!!.destroy(); refresh.stop(); super.onDestroy() }
    private fun render() {
        if (!visible || graph == null || isFinishing || isDestroyed) return
        val snapshot = graph!!.runtimeLogStore()!!.snapshot()
        if (!snapshot.equals(rendered)) {
            val b = StringBuilder()
            for (e in snapshot) b.append(RuntimeLogText.event(e.kind)).append('\n').append(RuntimeLogStore.formatEvent(e)).append("\n\n")
            rendered = snapshot; body!!.setText(b)
        }
        val worker = graph!!.runtimeLogWorker()!!
        summary!!.setText("最近 " + snapshot.size + " / 1000 条；本进程淘汰 " + graph!!.runtimeLogStore()!!.droppedCount()
            + " 条。\n初始历史读取：" + worker.recoveryStatus()
            + if (worker.lastFailure() == null) "\n本地保存：无当前错误（异步刷盘）" else "\n本地保存异常：" + worker.lastFailure() + "；新事件将触发重试。")
        val state = graph!!.logExports().state()
        status!!.setText(if (pickerUnavailable) PICKER_UNAVAILABLE else RuntimeLogText.export(state))
        export!!.setEnabled(snapshot.isNotEmpty() && !state.busy())
    }
    private class Refresh(a: LogsActivity?) : Runnable {
        private val target = WeakReference(a)
        private val main = Handler(Looper.getMainLooper())
        fun start() { stop(); main.post(this) }
        fun stop() { main.removeCallbacks(this) }
        override fun run() {
            val a = target.get()
            if (a == null || !a.visible) return
            a.render(); main.postDelayed(this, 1000)
        }
    }
    companion object { private const val PICKER_UNAVAILABLE = "无法打开保存选择器，请返回后重试。" }
}
