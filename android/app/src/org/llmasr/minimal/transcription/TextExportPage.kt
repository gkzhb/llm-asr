package org.llmasr.minimal.transcription

/** Main-thread TXT picker. Queued work remains revocable until actual write admission. */
class TextExportPage<T>(controller: TextExportController<T>?) {
    private val controller: TextExportController<T>
    private val pageId: Long
    private var ticket: TextExportController.Ticket? = null
    private var deferred: T? = null
    private var received = false
    private var foreground = false
    private var destroyed = false
    init {
        if (controller == null) throw IllegalArgumentException("controller required")
        this.controller = controller
        pageId = controller.newPage()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun pageId(): Long = pageId
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun begin(resultState: ResultState?): TextExportController.Ticket? {
        if (ticket != null && !controller.owns(ticket)) ticket = null
        if (destroyed || !foreground || ticket != null) return null
        val selected = controller.begin(pageId, resultState) ?: return null
        ticket = selected
        received = false
        deferred = null
        return ticket
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun onResult(requestCode: Int, target: T?): Boolean {
        if (destroyed) return false
        if (ticket == null || received || ticket!!.requestCode != requestCode) return false
        received = true
        if (target == null) { controller.revoke(ticket); ticket = null; return true }
        deferred = target
        consume()
        return true
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun foreground(value: Boolean) { foreground = value && !destroyed; consume() }
    private fun consume() {
        if (!foreground || ticket == null || !received || deferred == null) return
        val selected = ticket
        val target = deferred
        deferred = null
        controller.admit(selected, target)
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun destroy() {
        destroyed = true
        foreground = false
        val selected = ticket
        ticket = null
        deferred = null
        if (selected != null) controller.revoke(selected)
    }
}
