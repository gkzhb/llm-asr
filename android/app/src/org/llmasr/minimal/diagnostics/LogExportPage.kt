package org.llmasr.minimal.diagnostics

/** Main-thread picker identity; consumed provider IO outlives the page. */
class LogExportPage<T>(private val controller: LogExportController<T>?) {
    private val pageId: Long = controller!!.newPage()
    private var ticket: LogExportController.Ticket? = null
    private var deferred: T? = null
    private var received = false
    private var foreground = false
    private var destroyed = false
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun begin(snapshot: List<RuntimeLogEvent?>?): LogExportController.Ticket? {
        if (destroyed || !foreground || ticket != null) return null
        ticket = controller!!.begin(pageId, snapshot)
        received = false
        return ticket
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun result(code: Int, target: T?): Boolean {
        if (destroyed || ticket == null || received || ticket!!.requestCode != code) return false
        received = true
        if (target == null) { abandon(false); return true }
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
        ticket = null
        deferred = null
        controller!!.submit(selected, target)
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun abandon(expired: Boolean) { controller!!.abandon(ticket, expired); ticket = null; deferred = null; received = false }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun destroy() { destroyed = true; foreground = false; abandon(true) }
}
