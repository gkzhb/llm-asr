package org.llmasr.minimal.transcription;

/** Main-thread TXT export page/picker. Holds the per-page ticket; rotation
 * destroys the ticket. A consumed provider write may finish independently of
 * the page (cannot be retracted once admitted).
 *
 * The page does NOT capture Activity in any worker closure. The only fields
 * are the controller, the pageId, the active ticket, the deferred target, and
 * lifecycle flags. The worker in TextExportController only sees the
 * controller and a final target.
 *
 * Parameterized on the target type (android.net.Uri in production,
 * String/Object in host tests) so the page can be unit-tested without an
 * Android runtime.
 */
public final class TextExportPage<T> {
    private final TextExportController<T> controller;
    private final long pageId;
    private TextExportController.Ticket ticket;
    private T deferred;
    private boolean received, foreground, destroyed;

    public TextExportPage(TextExportController<T> controller) {
        if (controller == null) throw new IllegalArgumentException("controller required");
        this.controller = controller;
        this.pageId = controller.newPage();
    }

    public long pageId() { return pageId; }

    /** Begin a new ticket. No-op if the page is not foreground, already has a
     * ticket, or has been destroyed. */
    public TextExportController.Ticket begin(ResultState resultState) {
        if (ticket != null && !controller.owns(ticket)) ticket = null;
        if (destroyed || !foreground || ticket != null) return null;
        TextExportController.Ticket t = controller.begin(pageId, resultState);
        if (t == null) return null;
        ticket = t;
        received = false;
        deferred = null;
        return ticket;
    }

    /** Foreign callback rejection: if the request code is not the active
     * ticket's request code, ignore. Replay of an old request code is also
     * rejected. Returns true if the call was accepted (queued or admitted). */
    public boolean onResult(int requestCode, T target) {
        if (destroyed) return false;
        if (ticket == null || received || ticket.requestCode != requestCode) return false;
        received = true;
        if (target == null) {
            // Picker cancelled.
            controller.revoke(ticket);
            ticket = null;
            return true;
        }
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
        TextExportController.Ticket selected = ticket;
        T target = deferred;
        deferred = null;
        // Retain ticket identity until completion/destroy: queued work remains revocable.
        controller.admit(selected, target);
    }

    /** Lifecycle destroy: revoke any unadmitted ticket. The slot for an
     * admitted (in-flight) write is NOT released: that belongs to the
     * controller's worker. */
    public void destroy() {
        destroyed = true;
        foreground = false;
        TextExportController.Ticket t = ticket;
        ticket = null;
        deferred = null;
        if (t != null) controller.revoke(t);
    }
}
