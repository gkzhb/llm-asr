package org.llmasr.minimal.modelmanagement;

/** Actual page lifecycle policy, independent of Android callbacks for deterministic tests.
 * The page supplies foreground AND unlocked eligibility. Only its own operation is cancelled.
 * Configuration recreation transfers operation ID, never a picker ticket or URI. */
public final class ModelPageSession<T> {
    public interface Importer<T> { boolean start(T value); }
    private final ModelPickerTickets tickets;
    private final ModelManagementController controller;
    private ModelPickerTickets.Ticket ticket;
    private T deferred;
    private boolean callbackReceived, foreground, destroyed;
    private String ownedId;
    public ModelPageSession(ModelPickerTickets tickets, ModelManagementController controller, String restoredId) {
        this.tickets = tickets; this.controller = controller; ownedId = restoredId;
    }
    public int beginPicker() {
        if (destroyed || !foreground) throw new IllegalStateException("请返回前台重新选择目录");
        ticket = tickets.begin(); callbackReceived = false; return ticket.requestCode;
    }
    public boolean result(int code, T value) {
        if (destroyed || !tickets.valid(ticket) || ticket.requestCode != code || callbackReceived) return false;
        callbackReceived = true; deferred = value;
        if (value == null) abandonPicker();
        return true;
    }
    public boolean consume(Importer<T> importer) {
        if (!foreground || destroyed || deferred == null || !tickets.valid(ticket)) return false;
        T value = deferred;
        abandonPicker(); // consumed even if busy; never silently queued/replayed.
        boolean accepted = importer.start(value);
        if (accepted) adoptStarted();
        return accepted;
    }
    public boolean hasDeferred() { return deferred != null; }
    public void foreground(boolean value) { foreground = value; }
    public void adoptStarted() { ownedId = controller.state().current().activeOperationId; }
    public String ownedId() { return ownedId; }
    public boolean ownsActive() {
        ModelOperationControl.Operation op = controller.opControl().peekActive();
        return op != null && op.id.equals(ownedId);
    }
    public boolean cancellable() {
        ModelOperationControl.Operation op = controller.opControl().peekActive();
        return op != null && op.id.equals(ownedId) && !op.isTerminated() && !op.isSucceeded() && !op.isCancelRequested()
            && (op.kind == ModelOperationControl.Kind.IMPORT || op.kind == ModelOperationControl.Kind.VERIFY);
    }
    public void cancelOwn() { if (ownsActive()) controller.requestCancel(ownedId); }
    public void stopped(boolean changingConfigurations) {
        foreground = false;
        if (!changingConfigurations) cancelOwn();
    }
    public void abandonPicker() { tickets.release(ticket); ticket = null; deferred = null; callbackReceived = false; }
    public void destroy() { destroyed = true; foreground = false; abandonPicker(); }
}
