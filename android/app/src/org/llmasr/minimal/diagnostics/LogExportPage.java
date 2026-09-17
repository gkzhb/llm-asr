package org.llmasr.minimal.diagnostics;

import java.util.List;

/** Main-thread page/picker identity, testable without Android. Rotation destroys
 * the ticket; a consumed provider write may finish independently of the page. */
public final class LogExportPage<T> {
    private final LogExportController<T> controller;
    private final long pageId;
    private LogExportController.Ticket ticket;
    private T deferred;
    private boolean received, foreground, destroyed;

    public LogExportPage(LogExportController<T> controller) {
        this.controller = controller;
        pageId = controller.newPage();
    }
    public LogExportController.Ticket begin(List<RuntimeLogEvent> snapshot) {
        if (destroyed || !foreground || ticket != null) return null;
        ticket = controller.begin(pageId, snapshot);
        received = false;
        return ticket;
    }
    public boolean result(int code, T target) {
        if (destroyed || ticket == null || received || ticket.requestCode != code) return false;
        received = true;
        if (target == null) { abandon(false); return true; }
        deferred = target;
        consume();
        return true;
    }
    public void foreground(boolean value) {
        foreground = value && !destroyed;
        consume();
    }
    private void consume() {
        if (!foreground || ticket == null || !received || deferred == null) return;
        LogExportController.Ticket selected = ticket;
        T target = deferred;
        ticket = null; deferred = null;
        controller.submit(selected, target);
    }
    public void abandon(boolean expired) {
        controller.abandon(ticket, expired);
        ticket = null; deferred = null; received = false;
    }
    public void destroy() { destroyed = true; foreground = false; abandon(true); }
}
