package org.llmasr.minimal.transcription

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Process-wide UI state, with weak invalidation listeners and no cached busy flag.
 * Text/revision/export epoch live in ResultState; this is the App policy facade.
 */
class AppState {
    fun interface Listener { fun onChange() }
    private val resultState = ResultState()
    private val lastStatus = AtomicReference("模型尚未校验，请打开模型管理检查或导入。")
    private val listeners = CopyOnWriteArrayList<WeakReference<Listener>>()

    fun resultState(): ResultState = resultState
    fun lastStatus(): String = lastStatus.get()
    fun setLastStatus(value: String?) {
        lastStatus.set(value ?: "")
        notifyChange()
    }
    fun lastText(): String = resultState.currentText()
    fun setLastText(value: String?) {
        resultState.setText(value ?: "")
        notifyChange()
    }
    fun applyEdit(expectedRevision: Long, edited: String?): Long {
        val r = resultState.applyEdit(expectedRevision, edited)
        if (r > 0) notifyChange()
        return r
    }
    fun clearText() {
        resultState.clear()
        notifyChange()
    }
    fun addListener(l: Listener?) {
        if (l == null) return
        listeners.removeIf { ref -> ref.get() == null }
        listeners.add(WeakReference(l))
    }
    fun removeListener(l: Listener?) {
        listeners.removeIf { ref ->
            val current = ref.get()
            current == null || current === l
        }
    }
    fun notifyChange() {
        listeners.removeIf { ref -> ref.get() == null }
        for (ref in listeners) {
            val l = ref.get()
            if (l != null) {
                try { l.onChange() }
                catch (ignored: Throwable) { /* listener faults must not poison the writer */ }
            }
        }
    }
}
