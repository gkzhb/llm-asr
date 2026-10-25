package org.llmasr.minimal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import org.llmasr.minimal.task.TaskCoordinator

/** Explicit-action voice keyboard; worker listeners retain only a weak target. */
class AsrImeService : InputMethodService() {
    private var graph: AppGraph? = null
    private var controller: ImeController? = null
    private var session: ImeSession? = null
    private var status: TextView? = null
    private var preview: TextView? = null
    private var record: Button? = null
    private var stop: Button? = null
    private var cancel: Button? = null
    private var commit: Button? = null
    private var language: Spinner? = null
    private var visible = false
    private var alive = false
    private var setupError = "初始化失败，请打开主App检查模型。"
    private val refresh = UiRefresh(this)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open override fun onCreate() {
        super.onCreate(); alive = true
        try {
            AppGraph.install(applicationContext); graph = AppGraph.get()
            controller = ImeController(graph!!.coordinator(), graph!!.imeBackend(), refresh::onChange)
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
        record = button(row, "录音", this::startRecording)
        stop = button(row, "停止") { if (controller != null) controller!!.stop(); render() }
        cancel = button(row, "丢弃 / 重新开始", this::restartSession)
        commit = button(row, "确认输入", this::commitPreview)
        val tools = LinearLayout(ui); root.addView(tools)
        button(tools, "设置 / 授权") { leaveForExternalUi { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        button(tools, "模型管理") { leaveForExternalUi { startActivity(Intent(this, ModelManagementActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        val keyboards = LinearLayout(ui); root.addView(keyboards)
        button(keyboards, "返回上一输入法") { leaveForExternalUi(this::returnToPreviousInputMethod) }
        button(keyboards, "切换键盘") { leaveForExternalUi(this::showKeyboardPicker) }
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
        val accepted = try { switchToPreviousInputMethod() } catch (ignored: RuntimeException) { false }
        if (!accepted) showKeyboardPicker()
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
    private fun button(row: LinearLayout, label: String, action: Runnable): Button {
        val b = Button(row.context); b.setText(label); b.setAllCaps(false); b.setTextSize(12f)
        b.setMinWidth(0); b.setMinimumWidth(0); b.setOnClickListener { action.run() }
        row.addView(b, LinearLayout.LayoutParams(0, -2, 1f)); return b
    }
    private fun invalidate() { if (controller != null) controller!!.invalidate(); session = null }
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
        if (!eligible() || !isInputViewShown || !permitted()) { render(); return }
        controller!!.start(session, language!!.selectedItem.toString()); render()
    }
    private fun commitPreview() {
        if (!eligible() || !isInputViewShown) { invalidate(); render(); return }
        val info = currentInputEditorInfo
        val connection = currentInputConnection
        controller!!.commit(session, key(info), info!!.inputType, info.imeOptions,
            if (connection == null) null else ImeController.Commit { text -> connection.commitText(text, 1) })
        render()
    }
    private fun render() {
        if (status == null) return
        val allowed = eligible(); val busy = controller != null && controller!!.busy()
        status!!.setText(if (controller == null) setupError else if (!allowed) "当前会话不可用或为敏感输入框。"
            else if (!permitted()) "请点击设置 / 授权，在主App授予麦克风权限。"
            else if (busy && !controller!!.ownsTask()) "正在等待应用或上一会话处理完成……" else session!!.status())
        preview!!.setText(if (allowed) session!!.preview() else "")
        record!!.setEnabled(allowed && permitted() && !busy)
        stop!!.setEnabled(allowed && controller!!.capturing())
        cancel!!.setEnabled(alive && controller != null)
        commit!!.setEnabled(allowed && !busy && session!!.preview().isNotEmpty())
        language!!.setEnabled(!busy)
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
