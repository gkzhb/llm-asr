import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Direct host tests of the production ModelOperationControl. Covers the
 *  cancel/publish/terminal arbitration contract and the single-active
 *  invariant. */
public final class ModelOperationControlTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }

    public static void main(String[] args) throws Exception {
        // R1: only one operation is active at a time. tryBegin returns null
        //     when a second op is requested.
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation op1 = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            check(op1 != null);
            check(c.isBusy());
            ModelOperationControl.Operation op2 = c.tryBegin(ModelOperationControl.Kind.VERIFY);
            check(op2 == null);
            c.complete(op1, true);
            check(!c.isBusy());
            ModelOperationControl.Operation op3 = c.tryBegin(ModelOperationControl.Kind.DELETE);
            check(op3 != null);
            c.complete(op3, true);
        }

        // R2: requestCancel latches the flag; isCancelled reflects it.
        //     Stale cancels for a different opId are ignored.
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            check(!c.isCancelled(op));
            c.requestCancel(op.id);
            check(c.isCancelled(op));
            // Stale cancel for a different opId is ignored.
            c.requestCancel("not-this-op");
            check(c.isCancelled(op));
            // After completion, no new op is active.
            c.complete(op, true);
            check(!c.isBusy());
            check(!op.isSucceeded());
        }

        // R3: tryReservePublish settles the cancel/publish race.
        //     - Before cancel: returns true, sets publishReserved.
        //     - After cancel (but not yet reserved): returns false.
        //     - After reservation + cancel: that rename may finish, but the next reservation loses.
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            check(c.tryReservePublish(op));
            check(op.isPublishReserved());
            // A previous reservation does not authorize another file.
            c.requestCancel(op.id);
            check(!c.tryReservePublish(op)); // the next file must lose to cancel.
            c.complete(op, true);
        }
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            c.requestCancel(op.id);
            // Cancel won: publish refused.
            check(!c.tryReservePublish(op));
            check(!op.isPublishReserved());
            c.complete(op, false);
        }

        // R4: complete() clears the active slot and latches terminal state.
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            c.complete(op, true);
            check(op.isTerminated());
            check(op.isSucceeded());
            check(!c.isBusy());
        }
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            c.complete(op, false);
            check(op.isTerminated());
            check(!op.isSucceeded());
        }

        // R5: peekActive returns the live op without modifying.
        {
            ModelOperationControl c = new ModelOperationControl();
            check(c.peekActive() == null);
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.VERIFY);
            check(c.peekActive() == op);
            // peekActive does not change anything.
            check(c.isBusy());
            c.complete(op, true);
            check(c.peekActive() == null);
        }

        // R6: isCancelled(null) returns true. The repository's checkpoint
        //     code path calls isCancelled(op) where op is local; if a
        //     caller passes null we treat it as cancelled to fail closed.
        {
            ModelOperationControl c = new ModelOperationControl();
            check(c.isCancelled(null));
        }

        // R7: requestCancel is a no-op when no op is active or the id
        //     does not match. No exception, no flag latched on the wrong op.
        {
            ModelOperationControl c = new ModelOperationControl();
            c.requestCancel("anything");
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            c.requestCancel("wrong-id");
            check(!c.isCancelled(op));
            c.complete(op, true);
        }

        // R8: tryBegin produces unique operation ids; a second op after
        //     completion also has a different id.
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation a = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            c.complete(a, true);
            ModelOperationControl.Operation b = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            check(!a.id().equals(b.id()));
            c.complete(b, true);
        }

        // R9: tryReservePublish(null) returns false (no reservation for a
        //     missing op; checkpoint code paths can pass through).
        {
            ModelOperationControl c = new ModelOperationControl();
            check(!c.tryReservePublish(null));
        }

        // R10: terminal arbitration semantics — once completed, the op is
        //      terminal even if a late cancel arrives. The controller
        //      observes the cancelled op and reports CANCELLED only if the
        //      operation actually ended in CANCELLED state, not in
        //      SUCCEEDED. This guards against a race where cancel arrives
        //      after complete() and a re-read incorrectly reports
        //      cancelled.
        {
            ModelOperationControl c = new ModelOperationControl();
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.IMPORT);
            c.complete(op, true);
            // Op is succeeded; a stale requestCancel must not turn it into
            // a "cancelled" op in the controller's view because the op is
            // already terminal.
            c.requestCancel(op.id);
            check(op.isSucceeded());
            check(op.isTerminated());
        }

        {
            ModelOperationControl c = new ModelOperationControl();
            org.llmasr.minimal.model.ModelReadiness r = new org.llmasr.minimal.model.ModelReadiness();
            ModelOperationControl.Operation op = c.tryBegin(ModelOperationControl.Kind.VERIFY);
            org.llmasr.minimal.model.ModelReadiness.VerifyToken token = r.beginVerify();
            c.requestCancel(op.id);
            check(!c.trySucceed(op, r, token)); check(!r.isReady());
            c.complete(op, false);
            op = c.tryBegin(ModelOperationControl.Kind.VERIFY); token = r.beginVerify();
            check(c.trySucceed(op, r, token)); check(r.isReady());
            check(!c.requestCancel(op.id)); check(!op.isCancelRequested());
            check(c.isBusy()); // success is committed, owner still finalizing
            c.complete(op, true);
            check(!c.tryReservePublish(op));
            op = c.tryBegin(ModelOperationControl.Kind.DELETE);
            check(!c.requestCancel(op.id)); c.complete(op, true);
        }

        System.out.println("PASS " + checks + " model operation control checks (cancel/publish/terminal arbitration)");
    }
}
