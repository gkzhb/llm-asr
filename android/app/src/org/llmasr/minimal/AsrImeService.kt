package org.llmasr.minimal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.lang.ref.WeakReference
import org.llmasr.minimal.ime.ImeController
import org.llmasr.minimal.ime.ImeFieldPolicy
import org.llmasr.minimal.ime.ImeSession
import org.llmasr.minimal.model.ModelReadiness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.llmasr.minimal.task.TaskCoordinator

/** Explicit-action voice keyboard; worker listeners retain only a weak target. */
class AsrImeService : InputMethodService() {
    private var graph: AppGraph? = null
    private var controller: ImeController? = null
    private var session: ImeSession? = null
    private var rerecordFor: ImeSession? = null
    private var status: TextView? = null
    private var preview: TextView? = null
    private var record: Button? = null
    private var stop: Button? = null
    private var cancel: Button? = null
    private var commit: Button? = null
    private var language: Spinner? = null
    private var historyPanel: LinearLayout? = null
    private var historyScroll: ScrollView? = null
    private var historyButton: Button? = null
    private var historyOpen = false
    private var historyEpoch = 0L
    private val historyMain = Handler(Looper.getMainLooper())
    private var visible = false
    private var alive = false
    private var setupError = "初始化失败，请打开主App检查模型。"
    private val refresh = UiRefresh(this)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun onCreate() {
        super.onCreate(); alive = true
        try {
            AppGraph.install(applicationContext); graph = AppGraph.get()
            controller = ImeController(graph!!.coordinator(), graph!!.imeBackend(), refresh::onChange, graph!!.imeHistory())
            graph!!.coordinator()!!.addListener(refresh)
            graph!!.readiness()!!.addListener(refresh)
        } catch (e: Exception) { setupError = "初始化失败，请打开主App检查安装。" }
        catch (e: LinkageError) { setupError = "初始化失败，请打开主App检查安装。" }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open override fun onEvaluateFullscreenMode(): Boolean = false
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun onCreateInputView(): View {
        val ui: Context = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_NoActionBar)
        val root = LinearLayout(ui); root.setOrientation(LinearLayout.VERTICAL)
        root.setBackgroundColor(Color.rgb(245, 247, 250))
        val pad = (8 * resources.displayMetrics.density).toInt(); root.setPadding(pad, pad, pad, pad)
        status = TextView(ui); status!!.setTextColor(Color.rgb(55, 65, 81)); root.addView(status)
        preview = TextView(ui); preview!!.setTextSize(18f); preview!!.setTextColor(Color.rgb(17, 24, 39))
        preview!!.setPadding(pad, pad, pad, pad)
        val scroll = ScrollView(ui); scroll.setBackgroundColor(Color.WHITE); scroll.addView(preview)
        root.addView(scroll, LinearLayout.LayoutParams(-1, (72 * resources.displayMetrics.density).toInt()))
        language = Spinner(ui)
        language!!.setAdapter(ArrayAdapter<String>(ui, android.R.layout.simple_spinner_dropdown_item, arrayOf("Chinese", "English", "auto")))
        root.addView(language)
        val row = LinearLayout(ui); root.addView(row)
        language!!.setMinimumHeight(dp(48)); language!!.contentDescription = "转写语言"; language!!.tooltipText = "转写语言"
        record = button(row, "", "开始录音", Icon.MIC, this::startRecording)
        stop = button(row, "", "停止录音并转写", Icon.STOP) { if (controller != null) controller!!.stop(); render() }
        cancel = button(row, "", "重新开始会话", Icon.CROSS, this::restartSession)
        commit = button(row, "", "输入预览文本", Icon.CHECK, this::commitPreview)
        val tools = LinearLayout(ui); tools.setPadding(0, dp(8), 0, 0); root.addView(tools)
        button(tools, "App", "打开App", Icon.APP) { leaveForExternalUi { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) } }
        button(tools, "上一键盘", "返回上一输入法", Icon.PREVIOUS, this::returnToPreviousInputMethod)
        button(tools, "键盘", "选择输入法", Icon.KEYBOARD) { leaveForExternalUi(this::showKeyboardPicker) }
        historyButton = button(tools, "历史", "查看最近50条文字", Icon.HISTORY) { toggleHistory() }
        historyPanel = LinearLayout(ui); historyPanel!!.setOrientation(LinearLayout.VERTICAL)
        historyScroll = ScrollView(ui); historyScroll!!.addView(historyPanel)
        root.addView(historyScroll, LinearLayout.LayoutParams(-1, dp(160)))
        closeHistory()
        render(); return root
    }
    private fun leaveForExternalUi(launch: Runnable) {
        visible = false; session = null
        if (controller != null) controller!!.leaveForExternalUi({ requestHideSelf(0) }, launch)
        else {
            try { requestHideSelf(0) } catch (ignored: RuntimeException) { /* Best-effort hide; still attempt the user action. */ }
            try { launch.run() } catch (ignored: RuntimeException) {}
        }
        render()
    }
    private fun returnToPreviousInputMethod() {
        visible = false; invalidate() // Return invalidates without clearing platform show intent.
        try {
            val accepted = try { switchToPreviousInputMethod() } catch (ignored: RuntimeException) { false }
            if (!accepted) showKeyboardPicker()
        } catch (ignored: RuntimeException) { /* Preserve the external-action boundary, including an unavailable picker hint. */ }
        render()
    }
    private fun showKeyboardPicker() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
            if (imm != null) { imm.showInputMethodPicker(); return }
        } catch (ignored: RuntimeException) { /* Do not expose platform/editor exception details. */ }
        Toast.makeText(this, "暂时无法切换，请使用系统输入法选择入口。", Toast.LENGTH_SHORT).show()
    }
    private fun restartSession() {
        if (!alive || controller == null || !isInputViewShown || currentInputEditorInfo == null) return
        visible = true; renewSession(); render()
    }
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun semantics(button: Button, description: String) { button.contentDescription = description; button.tooltipText = description }
    private fun button(row: LinearLayout, label: String, description: String, icon: Icon, action: Runnable): Button {
        val b = Button(row.context); b.setText(label); b.setAllCaps(false); b.setTextSize(12f)
        b.setMinWidth(dp(48)); b.setMinimumWidth(dp(48)); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48))
        semantics(b, description)
        val drawable = ActionIcon(icon); drawable.setBounds(0, 0, dp(24), dp(24))
        b.setCompoundDrawables(null, drawable, null, null); b.setOnClickListener { action.run() }
        row.addView(b, LinearLayout.LayoutParams(0, -2, 1f)); return b
    }
    private enum class Icon { MIC, STOP, CROSS, CHECK, APP, PREVIOUS, KEYBOARD, HISTORY }
    /** Font-independent 24dp symbols; normal Button background retains pressed/disabled feedback. */
    private class ActionIcon(private val icon: Icon) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 41, 55); strokeWidth = 2f; style = Paint.Style.STROKE }
        override fun draw(canvas: Canvas) {
            val checkpoint = canvas.save()
            try {
                canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
                when (icon) {
                    Icon.MIC -> { canvas.drawRoundRect(9f, 2f, 15f, 15f, 3f, 3f, paint); canvas.drawArc(6f, 7f, 18f, 19f, 0f, 180f, false, paint); canvas.drawLine(12f, 19f, 12f, 22f, paint); canvas.drawLine(8f, 22f, 16f, 22f, paint) }
                    Icon.HISTORY -> { canvas.drawArc(3f, 3f, 21f, 21f, 0f, 360f, false, paint); path(canvas, floatArrayOf(12f, 5f, 12f, 12f, 17f, 15f)) }
                    Icon.STOP -> canvas.drawRect(5f, 5f, 19f, 19f, paint)
                    Icon.CROSS -> { canvas.drawLine(5f, 5f, 19f, 19f, paint); canvas.drawLine(19f, 5f, 5f, 19f, paint) }
                    Icon.CHECK -> path(canvas, floatArrayOf(3f, 12f, 9f, 18f, 21f, 5f))
                    Icon.APP -> { path(canvas, floatArrayOf(2f, 11f, 12f, 2f, 22f, 11f)); path(canvas, floatArrayOf(5f, 9f, 5f, 21f, 19f, 21f, 19f, 9f)); canvas.drawRect(10f, 14f, 14f, 21f, paint) }
                    Icon.KEYBOARD, Icon.PREVIOUS -> {
                        canvas.drawRect(2f, 5f, 22f, 19f, paint)
                        if (icon == Icon.PREVIOUS) { path(canvas, floatArrayOf(11f, 8f, 7f, 12f, 11f, 16f)); canvas.drawLine(7f, 12f, 18f, 12f, paint) }
                        else { for (x in 5..19 step 4) { canvas.drawPoint(x.toFloat(), 9f, paint); canvas.drawPoint(x.toFloat(), 13f, paint) }; canvas.drawLine(7f, 16f, 17f, 16f, paint) }
                    }
                }
            } finally { canvas.restoreToCount(checkpoint) }
        }
        private fun path(canvas: Canvas, points: FloatArray) {
            val path = Path(); path.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1]); canvas.drawPath(path, paint)
        }
        override fun isStateful(): Boolean = true
        override fun onStateChange(state: IntArray): Boolean {
            paint.color = if (state.contains(android.R.attr.state_enabled)) Color.rgb(31, 41, 55) else Color.rgb(140, 145, 153)
            invalidateSelf(); return true
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
    private fun invalidate() { closeHistory(); rerecordFor = null; if (controller != null) controller!!.invalidate(); session = null }
    private fun renewSession() {
        invalidate()
        val info = currentInputEditorInfo
        if (alive && visible && controller != null && info != null) session = controller!!.begin(info.inputType, info.imeOptions, key(info))
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun onStartInput(info: EditorInfo?, restarting: Boolean) { super.onStartInput(info, restarting); renewSession(); render() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting); visible = true; renewSession(); render()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open override fun onWindowShown() { super.onWindowShown(); render() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun onFinishInputView(finishingInput: Boolean) { visible = false; invalidate(); render(); super.onFinishInputView(finishingInput) }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open override fun onFinishInput() { visible = false; invalidate(); render(); super.onFinishInput() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open override fun onWindowHidden() { visible = false; invalidate(); render(); super.onWindowHidden() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS") open override fun onUnbindInput() { visible = false; invalidate(); render(); super.onUnbindInput() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun onDestroy() {
        alive = false; visible = false; invalidate()
        if (graph != null) { graph!!.coordinator()!!.removeListener(refresh); graph!!.readiness()!!.removeListener(refresh) }
        refresh.detach(); super.onDestroy()
    }
    private fun permitted(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun eligible(): Boolean {
        val info = currentInputEditorInfo
        return alive && visible && session != null && session!!.valid() && info != null && session!!.fieldKey!!.equals(key(info)) && !ImeFieldPolicy.isSensitive(info.inputType, info.imeOptions)
    }
    private fun startRecording() {
        if (!eligible() || !isInputViewShown || !permitted() || graph?.modelAccess() == null) { render(); return }
        closeHistory()
        val expected = session!!
        if (expected.preview().isNotEmpty() && rerecordFor !== expected) {
            rerecordFor = expected; render(); return // Inline confirmation: Service has no Activity dialog token.
        }
        rerecordFor = null
        if (session === expected && eligible() && isInputViewShown && permitted()) controller!!.start(expected, language!!.selectedItem.toString(), CaptureGate(this, expected))
        render()
    }
    private fun commitPreview() {
        rerecordFor = null
        if (!eligible() || !isInputViewShown) { invalidate(); render(); return }
        val info = currentInputEditorInfo
        val connection = currentInputConnection
        controller!!.commit(session, key(info), info!!.inputType, info.imeOptions,
            if (connection == null) null else ImeController.Commit { text -> connection.commitText(text, 1) })
        render()
    }
    private fun render() {
        if (status == null) return
        if (session != null && (!eligible() || !isInputViewShown || (!permitted() && controller?.ownsTask() == true))) invalidate()
        val allowed = eligible()
        if (!allowed || !isInputViewShown || controller?.busy() == true) closeHistory()
        historyButton?.isEnabled = allowed && isInputViewShown && controller?.busy() == false
        val presentation = controller?.presentation(); val busy = presentation?.busy == true
        val wired = graph?.modelAccess() != null
        val state = presentation?.state ?: ImeController.UiState.UNAVAILABLE
        if (!allowed || !permitted() || !isInputViewShown || busy || presentation?.hasPreview != true || rerecordFor !== session) rerecordFor = null
        val confirmRerecord = rerecordFor != null
        val stage = if (confirmRerecord) "丢弃此预览并重录？再次点麦克风确认；点勾保留并输入。" else when (state) {
            ImeController.UiState.PREPARING -> "准备模型 · 录音结束后等待转写"
            ImeController.UiState.RECORDING -> "录音中 · 最长30秒"
            ImeController.UiState.TRANSCRIBING -> if (presentation?.discarded == true) "结果已丢弃，转写继续" else "转写中，完成后预览"
            ImeController.UiState.CANCELLING -> "正在取消，等待收尾"
            ImeController.UiState.ERROR -> "失败，请检查原因后重试"
            ImeController.UiState.OTHER_BUSY -> "应用或上一会话处理中"
            else -> if (presentation?.hasPreview == true) "检查预览后输入" else "点录音开始 · 最长30秒"
        }
        status!!.setText(if (controller == null) setupError else if (presentation?.ownsTask == true) stage + (if (allowed && presentation.detail.isNotEmpty()) "\n" + presentation.detail else "")
            else if (!allowed) "此输入框不可录音，请切换到普通输入框。"
            else if (!permitted()) "请在App授权麦克风；授权不开始录音。"
            else if (state != ImeController.UiState.ERROR && !wired) "模型管理不可用，请打开App检查安装。"
            else stage + (if (presentation?.detail?.isNotEmpty() == true) "\n" + presentation.detail else ""))
        preview!!.setText(if (allowed) session!!.preview() else "")
        record!!.setEnabled(allowed && isInputViewShown && permitted() && !busy && wired)
        stop!!.setEnabled(allowed && state == ImeController.UiState.RECORDING && controller!!.capturing())
        cancel!!.setEnabled(alive && controller != null && state != ImeController.UiState.CANCELLING)
        commit!!.setEnabled(allowed && !busy && presentation?.hasPreview == true)
        semantics(record!!, if (confirmRerecord) "确认丢弃预览并录音" else if (state == ImeController.UiState.ERROR) "重试录音" else if (presentation?.hasPreview == true) "重新录音" else "开始录音")
        semantics(cancel!!, when (state) {
            ImeController.UiState.PREPARING -> "取消准备并丢弃"
            ImeController.UiState.RECORDING -> "取消录音并丢弃"
            ImeController.UiState.TRANSCRIBING -> "丢弃本次结果，转写继续"
            ImeController.UiState.CANCELLING -> "正在取消，等待任务释放"
            ImeController.UiState.ERROR -> "清除失败并重新开始"
            else -> if (presentation?.hasPreview == true) "丢弃预览" else "重新开始会话"
        })
        language!!.setEnabled(!busy)
    }
    private fun closeHistory() {
        historyOpen = false; historyEpoch++
        historyPanel?.removeAllViews(); historyPanel?.visibility = View.GONE
        historyScroll?.visibility = View.GONE
    }
    private fun toggleHistory() {
        if (historyOpen) { closeHistory(); return }
        if (!eligible() || !isInputViewShown || controller?.busy() != false) return
        historyOpen = true; historyPanel?.visibility = View.VISIBLE; historyScroll?.visibility = View.VISIBLE
        requestHistory(null)
    }
    private fun requestHistory(deleteId: String?) {
        val store = graph?.imeHistory() ?: return
        val expected = session ?: return
        val revision = expected.revision()
        if (!historyOpen || !eligible() || !isInputViewShown || controller?.busy() != false) { closeHistory(); return }
        val epoch = ++historyEpoch
        historyPanel?.removeAllViews()
        val weak = WeakReference(this)
        val main = historyMain
        val callback = org.llmasr.minimal.ime.ImeHistoryStore.Callback { snapshot ->
            main.post {
                val service = weak.get() ?: return@post
                if (!service.historyValid(expected, revision, epoch)) return@post
                service.showHistory(snapshot, expected, revision, epoch)
            }
        }
        val accepted = when (deleteId) {
            null -> store.snapshot(callback)
            "" -> store.clear(callback)
            else -> store.delete(deleteId, callback)
        }
        if (!accepted) historyPanel?.addView(TextView(this).apply { text = "历史繁忙，请稍后重新打开。" })
    }
    private fun historyValid(expected: ImeSession, revision: Long, epoch: Long): Boolean =
        alive && historyOpen && historyEpoch == epoch && session === expected && expected.revision() == revision &&
            eligible() && isInputViewShown && controller?.busy() == false
    private fun showHistory(snapshot: org.llmasr.minimal.ime.ImeHistoryStore.Snapshot, expected: ImeSession, revision: Long, epoch: Long) {
        val panel = historyPanel ?: return
        panel.removeAllViews()
        if (!snapshot.available) { panel.addView(TextView(panel.context).apply { text = "历史暂不可用；已保留原文件。" }); return }
        val tools = LinearLayout(panel.context); panel.addView(tools)
        button(tools, "清空", "清空文字历史", Icon.CROSS) { if (historyValid(expected, revision, epoch)) requestHistory("") }
        button(tools, "关闭", "关闭文字历史", Icon.CROSS) { closeHistory() }
        if (snapshot.records.isEmpty()) panel.addView(TextView(panel.context).apply { text = "暂无文字历史" })
        for (entry in snapshot.records) {
            val row = LinearLayout(panel.context); panel.addView(row)
            val label = if (entry.originalText.length > 120) entry.originalText.take(120) + "…" else entry.originalText
            button(row, label, "恢复此条到预览", Icon.CHECK) {
                if (historyValid(expected, revision, epoch) && controller!!.restoreHistory(expected, revision, entry)) { closeHistory(); render() }
            }
            button(row, "删除", "删除此条文字历史", Icon.CROSS) { if (historyValid(expected, revision, epoch)) requestHistory(entry.id) }
        }
    }
    /** Main-thread snapshot of the actual editor/visibility/permission after slow preparation.
     * Weak target only; a timed-out queued gate can never authorize late capture. */
    private class CaptureGate(service: AsrImeService, private val expected: ImeSession) : ImeController.CaptureIntent {
        private val target = WeakReference(service)
        private val main = Handler(Looper.getMainLooper())
        override fun start(hardwareStart: Runnable): Boolean {
            fun invoke(): Boolean {
                val service = target.get()
                if (service == null || service.session !== expected || !service.eligible() ||
                    !service.isInputViewShown || !service.permitted()) return false
                hardwareStart.run() // Same main turn as final platform eligibility; no worker/main snapshot gap.
                return true
            }
            if (Looper.myLooper() == Looper.getMainLooper()) return invoke()
            val state = AtomicInteger(0) // queued=0, running=1, abandoned=2, complete=3
            val done = CountDownLatch(1)
            val result = AtomicBoolean(false)
            val failure = AtomicReference<Throwable>()
            val action = Runnable {
                if (!state.compareAndSet(0, 1)) return@Runnable
                try { result.set(invoke()) } catch (caught: Throwable) { failure.set(caught) }
                finally { state.set(3); done.countDown() }
            }
            if (!main.post(action)) return false
            var interrupted = false
            try {
                try {
                    if (!done.await(5, TimeUnit.SECONDS) && state.compareAndSet(0, 2)) return false
                } catch (caught: InterruptedException) {
                    interrupted = true
                    if (state.compareAndSet(0, 2)) return false
                }
                // A running hardware start cannot be abandoned: worker retains AudioRecord
                // and shared owner until its real return, including failure/interrupt paths.
                while (done.count != 0L) {
                    try { done.await() } catch (caught: InterruptedException) { interrupted = true }
                }
                failure.get()?.let { throw it }
                return result.get()
            } finally {
                main.removeCallbacks(action)
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
        override fun allowed(): Boolean {
            val result = AtomicBoolean(false)
            val done = CountDownLatch(1)
            val check = Runnable {
                try {
                    val service = target.get()
                    result.set(service != null && service.session === expected && service.eligible() &&
                        service.isInputViewShown && service.permitted())
                } finally { done.countDown() }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) check.run()
            else {
                if (!main.post(check)) return false
                try { if (!done.await(5, TimeUnit.SECONDS)) return false }
                catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); return false }
                finally { main.removeCallbacks(check) }
            }
            return result.get()
        }
    }
    private class UiRefresh(service: AsrImeService?) : TaskCoordinator.Listener, ModelReadiness.Listener, Runnable {
        private val target = WeakReference(service)
        private val main = Handler(Looper.getMainLooper())
        override fun onChange() { main.post(this) }
        fun detach() { main.removeCallbacks(this) }
        override fun run() { val service = target.get(); if (service != null && service.alive) service.render() }
    }
    companion object {
        private fun key(info: EditorInfo?): String = if (info == null) "" else java.lang.String.valueOf(info.packageName) + ":" + info.fieldId + ":" + java.lang.String.valueOf(info.fieldName) + ":" + info.inputType + ":" + info.imeOptions
    }
}
