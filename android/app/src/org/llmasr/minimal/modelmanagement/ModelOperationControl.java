package org.llmasr.minimal.modelmanagement;

import java.util.UUID;
import org.llmasr.minimal.model.ModelReadiness;

/** Request-scoped arbitration. No monitor is held across filesystem/provider IO.
 * A reservation authorizes exactly one rename; the next call is a new race.
 * Success includes the epoch-checked READY commit, before accepting late cancel.
 * complete releases this slot only during the shared owner's finalization.
 */
public final class ModelOperationControl {
    public enum Kind { INSPECT, IMPORT, VERIFY, DELETE }
    public static final class Operation {
        public final String id = UUID.randomUUID().toString();
        public final Kind kind;
        public final long startedAt = System.currentTimeMillis();
        private volatile boolean cancelRequested, publishReserved, terminated, succeeded;
        Operation(Kind kind) { this.kind = kind; }
        public String id() { return id; }
        public Kind kind() { return kind; }
        public long startedAt() { return startedAt; }
        public boolean isCancelRequested() { return cancelRequested; }
        public boolean isPublishReserved() { return publishReserved; }
        public boolean isTerminated() { return terminated; }
        public boolean isSucceeded() { return succeeded; }
    }
    private volatile Operation active;
    public synchronized Operation tryBegin(Kind kind) {
        if (kind == null) throw new IllegalArgumentException("kind required");
        if (active != null) return null;
        return active = new Operation(kind);
    }
    /** false means stale, non-cancellable, or success already committed. */
    public synchronized boolean requestCancel(String id) {
        Operation op = active;
        if (op == null || !op.id.equals(id) || op.terminated || op.succeeded
                || op.kind == Kind.DELETE || op.kind == Kind.INSPECT) return false;
        op.cancelRequested = true;
        return true;
    }
    public boolean isCancelled(Operation op) { return op == null || op.cancelRequested; }
    public synchronized boolean tryReservePublish(Operation op) {
        if (op == null || active != op || op.terminated || op.succeeded || op.cancelRequested) return false;
        op.publishReserved = true;
        return true;
    }
    /** No IO or listener callbacks here. Null token is for inspect/delete only. */
    public synchronized boolean trySucceed(Operation op, ModelReadiness readiness, ModelReadiness.VerifyToken token) {
        if (op == null || active != op || op.terminated || op.cancelRequested) return false;
        if (token != null && !readiness.commitVerified(token)) return false;
        op.succeeded = true;
        return true;
    }
    /** Terminal arbitration is not owner release. No IO or callbacks. */
    public synchronized void decideTerminal(Operation op) {
        if (active == op && op != null) op.terminated = true;
    }
    public synchronized void complete(Operation op, boolean success) {
        if (op == null || active != op) return;
        op.succeeded = op.succeeded || (success && !op.cancelRequested);
        op.terminated = true;
        active = null;
    }
    public Operation peekActive() { return active; }
    public boolean isBusy() { return active != null; }
}
