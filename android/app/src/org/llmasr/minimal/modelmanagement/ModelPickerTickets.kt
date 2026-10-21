package org.llmasr.minimal.modelmanagement

/** One pending chooser; non-reused process codes disjoint from audio/export. */
class ModelPickerTickets {
    class Ticket private constructor(@JvmField val requestCode: Int) {
        companion object {
            @JvmSynthetic internal fun create(code: Int): Ticket = Ticket(code)
        }
    }
    private var next = FIRST
    private var pending: Ticket? = null
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun begin(): Ticket {
        if (pending != null) throw IllegalStateException("已有模型目录选择器，请先返回或取消")
        if (next > LAST) throw IllegalStateException("目录选择次数已达上限，请重启应用")
        val ticket = Ticket.create(next++)
        pending = ticket
        return ticket
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun valid(ticket: Ticket?): Boolean = ticket != null && pending === ticket
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Synchronized open fun release(ticket: Ticket?) { if (valid(ticket)) pending = null }
    companion object {
        const val FIRST: Int = 100
        const val LAST: Int = 999
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun isRequest(code: Int): Boolean = code >= FIRST && code <= LAST
    }
}
