import org.llmasr.minimal.modelmanagement.ModelOperationControl
import org.llmasr.minimal.model.ModelReadiness

/** Direct production cancel/publish/terminal arbitration and single-active checks. */
class ModelOperationControlTest {
    companion object {
        private var checks = 0
        private fun check(condition: Boolean) { checks++; if (!condition) throw AssertionError("check $checks") }
        @JvmStatic @Throws(Exception::class) @Suppress("UNUSED_PARAMETER")
        fun main(args: Array<String>?) {
            run {
                val c = ModelOperationControl()
                val op1 = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                check(op1 != null); check(c.isBusy())
                val op2 = c.tryBegin(ModelOperationControl.Kind.VERIFY); check(op2 == null)
                c.complete(op1, true); check(!c.isBusy())
                val op3 = c.tryBegin(ModelOperationControl.Kind.DELETE); check(op3 != null); c.complete(op3, true)
            }
            run {
                val c = ModelOperationControl(); val op = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                check(!c.isCancelled(op)); c.requestCancel(op.id); check(c.isCancelled(op))
                c.requestCancel("not-this-op"); check(c.isCancelled(op))
                c.complete(op, true); check(!c.isBusy()); check(!op.isSucceeded())
            }
            run {
                val c = ModelOperationControl(); val op = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                check(c.tryReservePublish(op)); check(op.isPublishReserved())
                c.requestCancel(op.id); check(!c.tryReservePublish(op)); c.complete(op, true)
            }
            run {
                val c = ModelOperationControl(); val op = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                c.requestCancel(op.id); check(!c.tryReservePublish(op)); check(!op.isPublishReserved()); c.complete(op, false)
            }
            run {
                val c = ModelOperationControl(); val op = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                c.complete(op, true); check(op.isTerminated()); check(op.isSucceeded()); check(!c.isBusy())
            }
            run {
                val c = ModelOperationControl(); val op = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                c.complete(op, false); check(op.isTerminated()); check(!op.isSucceeded())
            }
            run {
                val c = ModelOperationControl(); check(c.peekActive() == null)
                val op = c.tryBegin(ModelOperationControl.Kind.VERIFY)
                check(c.peekActive() === op); check(c.isBusy()); c.complete(op, true); check(c.peekActive() == null)
            }
            run { val c = ModelOperationControl(); check(c.isCancelled(null)) }
            run {
                val c = ModelOperationControl(); c.requestCancel("anything")
                val op = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                c.requestCancel("wrong-id"); check(!c.isCancelled(op)); c.complete(op, true)
            }
            run {
                val c = ModelOperationControl(); val a = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                c.complete(a, true); val b = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                check(a.id() != b.id()); c.complete(b, true)
            }
            run { val c = ModelOperationControl(); check(!c.tryReservePublish(null)) }
            run {
                val c = ModelOperationControl(); val op = c.tryBegin(ModelOperationControl.Kind.IMPORT)
                c.complete(op, true); c.requestCancel(op.id); check(op.isSucceeded()); check(op.isTerminated())
            }
            run {
                val c = ModelOperationControl(); val r = ModelReadiness()
                var op = c.tryBegin(ModelOperationControl.Kind.VERIFY); var token = r.beginVerify()
                c.requestCancel(op.id); check(!c.trySucceed(op, r, token)); check(!r.isReady())
                c.complete(op, false)
                op = c.tryBegin(ModelOperationControl.Kind.VERIFY); token = r.beginVerify()
                check(c.trySucceed(op, r, token)); check(r.isReady())
                check(!c.requestCancel(op.id)); check(!op.isCancelRequested()); check(c.isBusy())
                c.complete(op, true); check(!c.tryReservePublish(op))
                op = c.tryBegin(ModelOperationControl.Kind.DELETE); check(!c.requestCancel(op.id)); c.complete(op, true)
            }
            println("PASS $checks model operation control checks (cancel/publish/terminal arbitration)")
        }
    }
}
