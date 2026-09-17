import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;

import java.util.concurrent.atomic.*;

/** Exercise inspect and cancellation paths, recording observations OUTSIDE callbacks. */
public final class ModelNotificationTest {
    public static void main(String[] args) throws Exception {
        ModelManagementControllerTest.Fixture f=new ModelManagementControllerTest.Fixture();
        ModelManagementController c=f.controller();
        AtomicBoolean lockHeld=new AtomicBoolean(); AtomicInteger notices=new AtomicInteger();
        ModelManagementState.Listener l=() -> {
            if(Thread.holdsLock(c)) lockHeld.set(true);
            notices.incrementAndGet();
        };
        f.state.addListener(l);
        c.refreshInspect(); f.queue.run();
        if(lockHeld.get()) throw new AssertionError("INSPECT listeners must be outside controller monitor");
        if(notices.get()!=3) throw new AssertionError("INSPECT pending/terminal/finish must all notify");
        c.startImport(f.source()); c.requestCancel(f.control.peekActive().id); f.queue.run();
        if(lockHeld.get()) throw new AssertionError("cancel listeners must be outside controller monitor");
        if(f.owner.isBusy() || f.control.isBusy()) throw new AssertionError("final ownership release");
        f.state.removeListener(l);
        // Reentrant cancel from progress must preserve cancellation and newer state.
        AtomicBoolean cancelled=new AtomicBoolean();
        ModelManagementState.Listener reentrant=() -> {
            if(Thread.holdsLock(c)) lockHeld.set(true);
            ModelManagementState.Snapshot s=f.state.current();
            if(s.phase==ModelManagementState.Phase.COPYING && cancelled.compareAndSet(false,true))
                c.requestCancel(s.activeOperationId);
        };
        f.state.addListener(reentrant); c.startImport(f.source()); f.queue.run();
        if(!cancelled.get() || lockHeld.get() || f.state.current().phase!=ModelManagementState.Phase.CANCELLED)
            throw new AssertionError("reentrant cancellation preserves newer state outside monitor");
        f.state.removeListener(reentrant);
        // A different thread can acquire controller monitor from inside observer.
        AtomicBoolean acquired=new AtomicBoolean(), timedOut=new AtomicBoolean();
        ModelManagementState.Listener crossThread=() -> {
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
            Thread thread=new Thread(() -> { synchronized(c) { acquired.set(true); } done.countDown(); });
            thread.setDaemon(true); thread.start();
            try { if(!done.await(2,java.util.concurrent.TimeUnit.SECONDS)) timedOut.set(true); }
            catch(InterruptedException e) { timedOut.set(true); Thread.currentThread().interrupt(); }
        };
        f.state.addListener(crossThread); c.refreshInspect(); f.queue.run(); f.state.removeListener(crossThread);
        if(!acquired.get() || timedOut.get()) throw new AssertionError("observer must permit different-thread controller acquisition");
        System.out.println("PASS controller inspect/cancel notifications outside monitor");
    }
}
