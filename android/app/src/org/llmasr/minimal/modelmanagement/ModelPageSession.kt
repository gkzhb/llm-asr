package org.llmasr.minimal.modelmanagement

/** Foreground page identity. Rotation transfers only operation ID, never a picker/URI. */
class ModelPageSession<T>(private val tickets: ModelPickerTickets?, private val controller: ModelManagementController?, restoredId: String?) {
    fun interface Importer<T> { fun start(value: T?): Boolean }
    private var ticket: ModelPickerTickets.Ticket? = null
    private var deferred: T? = null
    private var callbackReceived = false
    private var foreground = false
    private var destroyed = false
    private var ownedId: String? = restoredId
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun beginPicker(): Int {
        if (destroyed || !foreground) throw IllegalStateException("请返回前台重新选择目录")
        ticket = tickets!!.begin()
        callbackReceived = false
        return ticket!!.requestCode
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun result(code: Int, value: T?): Boolean {
        if (destroyed || !tickets!!.valid(ticket) || ticket!!.requestCode != code || callbackReceived) return false
        callbackReceived = true
        deferred = value
        if (value == null) abandonPicker()
        return true
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun consume(importer: Importer<T>?): Boolean {
        if (!foreground || destroyed || deferred == null || !tickets!!.valid(ticket)) return false
        val value = deferred
        abandonPicker()
        val accepted = importer!!.start(value)
        if (accepted) adoptStarted()
        return accepted
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun hasDeferred(): Boolean = deferred != null
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun foreground(value: Boolean) { foreground = value }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun adoptStarted() { ownedId = controller!!.state().current().activeOperationId }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun ownedId(): String? = ownedId
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun ownsActive(): Boolean {
        val op = controller!!.opControl().peekActive()
        return op != null && op.id == ownedId
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun cancellable(): Boolean {
        val op = controller!!.opControl().peekActive()
        return op != null && op.id == ownedId && !op.isTerminated && !op.isSucceeded && !op.isCancelRequested &&
            (op.kind == ModelOperationControl.Kind.IMPORT || op.kind == ModelOperationControl.Kind.VERIFY)
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun cancelOwn() { if (ownsActive()) controller!!.requestCancel(ownedId) }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun stopped(changingConfigurations: Boolean) { foreground = false; if (!changingConfigurations) cancelOwn() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun abandonPicker() { tickets!!.release(ticket); ticket = null; deferred = null; callbackReceived = false }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun destroy() { destroyed = true; foreground = false; abandonPicker() }
}
