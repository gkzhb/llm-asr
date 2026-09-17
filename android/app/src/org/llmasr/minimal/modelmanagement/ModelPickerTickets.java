package org.llmasr.minimal.modelmanagement;

/** One process pending chooser; bounded non-reused codes disjoint from audio/export.
 * No Activity, URI, path, permissions or task ownership is stored here. */
public final class ModelPickerTickets {
    public static final int FIRST = 100, LAST = 999;
    public static final class Ticket {
        public final int requestCode;
        private Ticket(int code) { requestCode = code; }
    }
    private int next = FIRST;
    private Ticket pending;
    public synchronized Ticket begin() {
        if (pending != null) throw new IllegalStateException("已有模型目录选择器，请先返回或取消");
        if (next > LAST) throw new IllegalStateException("目录选择次数已达上限，请重启应用");
        return pending = new Ticket(next++);
    }
    public synchronized boolean valid(Ticket ticket) { return ticket != null && pending == ticket; }
    public synchronized void release(Ticket ticket) { if (valid(ticket)) pending = null; }
    public static boolean isRequest(int code) { return code >= FIRST && code <= LAST; }
}
