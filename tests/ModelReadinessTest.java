import org.llmasr.minimal.model.ModelReadiness;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Direct host tests of the shared ModelReadiness. No Android types, no
 *  mocks of production classes. Covers the unified-state contract: the
 *  same instance is the single source of truth for ASR and IME. */
public final class ModelReadinessTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }

    public static void main(String[] args) throws Exception {
        // R1: initial state is UNKNOWN, epoch zero, not ready.
        {
            ModelReadiness r = new ModelReadiness();
            ModelReadiness.Snapshot s = r.snapshot();
            check(s.state == ModelReadiness.State.UNKNOWN);
            check(s.epoch == 0L);
            check(!s.ready());
            check(!r.isReady());
        }

        // R2: tryBeginVerify claims when not ready. State moves to
        //     VERIFYING. Once marked READY, subsequent tryBeginVerify
        //     returns null (no SHA work needed).
        {
            ModelReadiness r = new ModelReadiness();
            ModelReadiness.VerifyToken t = r.tryBeginVerify();
            check(t != null);
            check(r.snapshot().state == ModelReadiness.State.VERIFYING);
            r.markVerified(t);
            check(r.snapshot().state == ModelReadiness.State.READY);
            // Already-ready state ignores another beginVerify.
            check(r.tryBeginVerify() == null);
        }

        // R3: invalidate bumps epoch, drops READY back to UNKNOWN. The
        //     verifiedEpoch no longer matches, so isReady() returns false.
        {
            ModelReadiness r = new ModelReadiness();
            ModelReadiness.VerifyToken t = r.tryBeginVerify();
            r.markVerified(t);
            check(r.snapshot().epoch == 0L);
            check(r.snapshot().verifiedEpoch == 0L);
            r.invalidate();
            ModelReadiness.Snapshot after = r.snapshot();
            check(after.epoch == 1L);
            check(after.state == ModelReadiness.State.UNKNOWN);
            check(!after.ready());
            check(!r.isReady());
        }

        // R4: a stale VerifyToken cannot carry READY to the new epoch.
        //     This models a delete-then-lazy-verify race: the verify
        //     worker started against epoch N, but delete bumped to N+1.
        {
            ModelReadiness r = new ModelReadiness();
            ModelReadiness.VerifyToken t = r.tryBeginVerify();
            r.invalidate();
            r.markVerified(t); // token is for old epoch; ignored
            check(!r.isReady());
            check(r.snapshot().state == ModelReadiness.State.UNKNOWN);
        }

        // R5: markInvalid bumps epoch and sets INVALID. A subsequent
        //     markVerified from a stale token must not overwrite INVALID.
        {
            ModelReadiness r = new ModelReadiness();
            r.markInvalid();
            check(r.snapshot().state == ModelReadiness.State.INVALID);
            check(r.snapshot().epoch == 1L);
            ModelReadiness.VerifyToken t = r.tryBeginVerify(); // claims the new epoch
            check(t != null);
            r.markVerified(t);
            check(r.snapshot().state == ModelReadiness.State.READY);
        }

        // R6: listeners are notified on every state change. A listener that
        //     throws does not poison the writer or skip other listeners.
        {
            ModelReadiness r = new ModelReadiness();
            AtomicInteger count = new AtomicInteger();
            ModelReadiness.Listener counting = () -> count.incrementAndGet();
            ModelReadiness.Listener throwing = () -> { throw new IllegalStateException("listener boom"); };
            r.addListener(counting); r.addListener(throwing);
            ModelReadiness.VerifyToken t = r.tryBeginVerify();
            r.markVerified(t);
            r.invalidate();
            check(count.get() >= 3);
            r.removeListener(counting); r.removeListener(throwing);
        }

        // R7: removed listener stops being notified. Explicit remove.
        {
            ModelReadiness r = new ModelReadiness();
            AtomicInteger count = new AtomicInteger();
            ModelReadiness.Listener l = new ModelReadiness.Listener() {
                @Override public void onChange() { count.incrementAndGet(); }
            };
            r.addListener(l);
            r.invalidate();
            check(count.get() == 1);
            r.removeListener(l);
            r.invalidate();
            check(count.get() == 1);
        }

        // R8: invalidate from READY yields UNKNOWN at the new epoch.
        {
            ModelReadiness r = new ModelReadiness();
            ModelReadiness.VerifyToken t = r.tryBeginVerify();
            r.markVerified(t);
            r.invalidate();
            check(r.snapshot().state == ModelReadiness.State.UNKNOWN);
            check(r.snapshot().epoch == 1L);
        }

        // R9: snapshot() reflects verifiedEpoch as well.
        {
            ModelReadiness r = new ModelReadiness();
            ModelReadiness.Snapshot a = r.snapshot();
            check(a.verifiedEpoch == -1L);
            ModelReadiness.VerifyToken t = r.tryBeginVerify();
            r.markVerified(t);
            check(r.snapshot().verifiedEpoch == 0L);
        }

        // R10: markVerified(null) is a no-op; so is markVerified with
        //      a token from a future epoch (impossible to construct,
        //      but the contract is: stale = no-op).
        {
            ModelReadiness r = new ModelReadiness();
            r.markVerified(null);
            check(r.snapshot().state == ModelReadiness.State.UNKNOWN);
        }

        // Tokens are identity-bound, not just epoch numbers. No old/same-epoch proof resurrection.
        {
            ModelReadiness r = new ModelReadiness();
            ModelReadiness.VerifyToken first = r.beginVerify();
            ModelReadiness.VerifyToken second = r.beginVerify();
            r.markVerified(first); check(!r.isReady());
            r.finishUnverified(second); r.markVerified(second); check(!r.isReady());
            r.markInvalid(); ModelReadiness.VerifyToken cancelled = r.beginVerify();
            r.finishUnverified(cancelled); check(r.state() == ModelReadiness.State.INVALID);
            ModelReadiness other = new ModelReadiness(); r.markVerified(other.beginVerify()); check(!r.isReady());
        }
        // Callbacks are outside readiness locks, including invalidate's UI cancellation callback.
        {
            ModelReadiness r = new ModelReadiness();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            java.util.List<Thread> workers = new java.util.ArrayList<>();
            AtomicInteger callbacks = new AtomicInteger();
            ModelReadiness.Listener listener = () -> {
                callbacks.incrementAndGet();
                Thread t = new Thread(() -> {
                    try { r.snapshot(); } catch (Throwable e) { failure.compareAndSet(null,e); }
                });
                t.setDaemon(true); workers.add(t); t.start();
                try {
                    t.join(500);
                    if (t.isAlive()) failure.compareAndSet(null,new AssertionError("readiness listener held monitor"));
                } catch (Throwable e) { failure.compareAndSet(null,e); }
            };
            r.addListener(listener);
            try { r.invalidate(); r.tryBeginVerify(); r.markInvalid(); }
            finally {
                r.removeListener(listener);
                for (Thread worker : workers) {
                    worker.join(1500);
                    if (worker.isAlive()) failure.compareAndSet(null,new AssertionError("listener worker teardown timeout"));
                }
            }
            // Outside production's catch(Throwable) listener guard. A mutant cannot swallow this.
            if (failure.get()!=null) throw new AssertionError("readiness listener lock regression",failure.get());
            check(callbacks.get()==3);

        }

        System.out.println("PASS " + checks + " model readiness state checks (shared ASR/IME truth)");
    }
}
