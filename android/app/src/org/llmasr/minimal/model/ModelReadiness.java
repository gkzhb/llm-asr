package org.llmasr.minimal.model;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single source of truth for "is the bundled model ready to use right now".
 *  Android integrators must route App and IME lazy verification through
 *  this instance; this pure-Java component does not itself install that wiring.
 *
 *  State is gated by an epoch: every model change (import, delete, partial
 *  failure, inspect-found-incomplete) bumps the epoch. A caller that
 *  observed an older epoch cannot carry READY to the new epoch; the
 *  readiness tracks the epoch at which it was last marked READY, and
 *  isReady() requires that READY was set for the current epoch.
 *
 *  Listeners are weakly held; the page must remove its listener in onPause
 *  to avoid leaks. Listener exceptions never poison the writer.
 */
public final class ModelReadiness {
    public enum State { UNKNOWN, NOT_INSTALLED, INCOMPLETE, UNVERIFIED, VERIFYING, READY, INVALID }

    public interface Listener { void onChange(); }

    public static final class Snapshot {
        public final long epoch;
        public final State state;
        public final long verifiedEpoch;
        public Snapshot(long epoch, State state, long verifiedEpoch) {
            this.epoch = epoch;
            this.state = state;
            this.verifiedEpoch = verifiedEpoch;
        }
        public boolean ready() { return state == State.READY && verifiedEpoch == epoch; }
        @Override public String toString() { return "Snapshot{epoch=" + epoch + ",state=" + state + ",verified=" + verifiedEpoch + "}"; }
    }

    /** Opaque token returned by tryBeginVerify. The caller passes it back
     *  to markVerified. If invalidate has bumped the epoch in the meantime,
     *  the token is stale and markVerified is a no-op. */
    public static final class VerifyToken {
        final long epoch;
        final State previous;
        VerifyToken(long epoch, State previous) { this.epoch = epoch; this.previous = previous; }
        public long epoch() { return epoch; }
    }

    private final List<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();
    private VerifyToken pending;
    private volatile long epoch = 0L;
    private volatile State state = State.UNKNOWN;
    private volatile long verifiedEpoch = -1L;

    public synchronized Snapshot snapshot() { return new Snapshot(epoch, state, verifiedEpoch); }
    public synchronized long epoch() { return epoch; }
    public synchronized State state() { return state; }
    public synchronized boolean isReady() { return state == State.READY && verifiedEpoch == epoch; }

    /** Try to claim lazy verify for the current epoch. Returns null if the
     *  model is already verified for this epoch (no SHA needed). Otherwise
     *  transitions to VERIFYING and returns a token to pass to markVerified. */
    public VerifyToken tryBeginVerify() {
        VerifyToken token;
        synchronized (this) {
            if (isReady()) return null;
            token = beginVerify();
        }
        notifyChange();
        return token;
    }

    /** Mark the current epoch as READY for the token's epoch. A stale
     *  token (epoch mismatch) is ignored: the caller raced with an
     *  invalidate and the new epoch is no longer verified. */
    public void markVerified(VerifyToken token) {
        if (commitVerified(token)) notifyChange();
    }

    // Short, callback-free commit used inside terminal/cancel arbitration.
    public synchronized boolean commitVerified(VerifyToken token) {
        if (token == null || pending != token || token.epoch != epoch) return false;
        state = State.READY;
        verifiedEpoch = epoch;
        pending = null;
        return true;
    }

    /** Explicit verification revokes the old proof before hashing. */
    public synchronized VerifyToken beginVerify() {
        State previous = state;
        verifiedEpoch = -1;
        state = State.VERIFYING;
        return pending = new VerifyToken(epoch, previous);
    }

    public synchronized void finishUnverified(VerifyToken token) {
        if (token != null && pending == token && token.epoch == epoch) {
            pending = null;
            state = token.previous == State.INVALID ? State.INVALID : State.UNVERIFIED;
        }
    }

    /** Size inspection never manufactures SHA evidence. */
    public synchronized void inspect(ModelRepository.InspectReport report) {
        boolean any = report.orphanPartBytes > 0, complete = true;
        for (ModelReports.FileDetail f : report.files) {
            any |= f.present || f.partExists;
            complete &= f.sizeMatch;
        }
        if (!complete) {
            if (state == State.READY || pending != null) { epoch++; pending = null; }
            verifiedEpoch = -1;
            state = any ? State.INCOMPLETE : State.NOT_INSTALLED;
        } else if (state != State.READY && state != State.INVALID && state != State.VERIFYING) {
            state = State.UNVERIFIED;
        }
    }

    /** Bump epoch, mark UNKNOWN. Call when a model change begins (import,
     *  delete, inspect-found-incomplete) so any previous verified status
     *  no longer claims the new epoch. */
    public void invalidate() {
        synchronized (this) {
        epoch++;
        pending = null;
        verifiedEpoch = -1;
        state = State.UNKNOWN;
        }
        notifyChange();
    }

    /** Mark current epoch as INVALID. Bumps epoch so a fresh inspect/import
     *  starts from a clean slate. */
    public void markInvalid() {
        synchronized (this) {
        epoch++;
        pending = null;
        verifiedEpoch = -1;
        state = State.INVALID;
        }
        notifyChange();
    }

    public void addListener(Listener l) {
        if (l == null) return;
        listeners.removeIf(ref -> ref.get() == null);
        listeners.add(new WeakReference<>(l));
    }

    public void removeListener(Listener l) {
        listeners.removeIf(ref -> {
            Listener current = ref.get();
            return current == null || current == l;
        });
    }

    public void notifyChange() {
        listeners.removeIf(ref -> ref.get() == null);
        for (WeakReference<Listener> ref : listeners) {
            Listener l = ref.get();
            if (l != null) {
                try { l.onChange(); } catch (Throwable ignored) { /* listener faults must not poison the writer */ }
            }
        }
    }
}
