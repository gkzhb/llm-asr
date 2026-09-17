import org.llmasr.minimal.task.TaskCoordinator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Direct host tests of the production TaskCoordinator. The coordinator has
 * direct invalidation listeners; tests exercise submit(), withOwnership(),
 * isBusy(), and the deterministic rejection path through an injected
 * Executor that throws RejectedExecutionException. Awaiting uses latches
 * with brief sleeps for the rare race where the gate flips between submit
 * and assertion.
 */
public final class TaskCoordinatorTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    static void await(CountDownLatch latch) {
        try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("latch timeout"); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }

    public static void main(String[] args) throws Exception {
        // R1: submit accepts a task; gate flips busy.
        TaskCoordinator c1 = new TaskCoordinator();
        CountDownLatch enter = new CountDownLatch(1), finish = new CountDownLatch(1);
        boolean accepted = c1.submit(() -> { enter.countDown(); await(finish); });
        check(accepted);
        await(enter);
        check(c1.isBusy());
        finish.countDown();
        awaitRelease(c1);
        check(!c1.isBusy());

        // R2: duplicate submit is rejected without disturbing the running request.
        TaskCoordinator c2 = new TaskCoordinator();
        CountDownLatch r2Enter = new CountDownLatch(1), r2Finish = new CountDownLatch(1);
        boolean first = c2.submit(() -> { r2Enter.countDown(); await(r2Finish); });
        check(first && c2.isBusy());
        await(r2Enter);
        boolean second = c2.submit(() -> {});
        check(!second && c2.isBusy());
        r2Finish.countDown();
        awaitRelease(c2);
        check(!c2.isBusy());

        // R3: withOwnership runs a short transaction under the gate and releases in finally.
        TaskCoordinator c3 = new TaskCoordinator();
        String result = c3.withOwnership(() -> { check(c3.isBusy()); return "ok"; });
        check("ok".equals(result));
        check(!c3.isBusy());

        // R4: withOwnership throws if the gate is held; gate state is unchanged.
        TaskCoordinator c4 = new TaskCoordinator();
        CountDownLatch r4Enter = new CountDownLatch(1), r4Finish = new CountDownLatch(1);
        c4.submit(() -> { r4Enter.countDown(); await(r4Finish); });
        await(r4Enter);
        boolean threw = false;
        try { c4.withOwnership(() -> "should not run"); }
        catch (IllegalStateException expected) { threw = true; }
        check(threw && c4.isBusy());
        r4Finish.countDown();
        awaitRelease(c4);

        // R5: withOwnership releases the gate even when the supplier throws.
        TaskCoordinator c5 = new TaskCoordinator();
        boolean supplierThrew = false;
        try { c5.withOwnership(() -> { throw new RuntimeException("boom"); }); }
        catch (RuntimeException expected) { supplierThrew = true; }
        check(supplierThrew && !c5.isBusy());

        // R6: submit on a shut-down executor fails closed. The gate is released
        // before the throw, so a subsequent withOwnership does not see a stale
        // busy flag.
        ExecutorService dead = new ThreadPoolExecutor(0, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>()) {
            @Override public void execute(Runnable r) { throw new RejectedExecutionException("dead"); }
        };
        TaskCoordinator c6 = new TaskCoordinator(dead);
        boolean rejected = false;
        try { c6.submit(() -> {}); }
        catch (RejectedExecutionException expected) { rejected = true; }
        check(rejected && !c6.isBusy());

        // R7: a task that throws does not leave the gate held.
        TaskCoordinator c7 = new TaskCoordinator(Runnable::run);
        boolean taskFailed = false;
        try { c7.submit(() -> { throw new RuntimeException("task fail"); }); }
        catch (RuntimeException expected) { taskFailed = true; }
        check(taskFailed && !c7.isBusy());

        // R8: a single task runs to completion; subsequent tasks are accepted.
        TaskCoordinator c8 = new TaskCoordinator();
        CountDownLatch r8A = new CountDownLatch(1), r8B = new CountDownLatch(1);
        c8.submit(r8A::countDown);
        await(r8A);
        awaitRelease(c8);
        c8.submit(r8B::countDown);
        await(r8B);
        awaitRelease(c8);
        check(!c8.isBusy());

        // Direct subscriptions: duplicate registration, removal, faulty observer isolation,
        // and release invalidation sees finalized resources with owner already free.
        AdmissionBoundaryTest.Queue queue = new AdmissionBoundaryTest.Queue();
        TaskCoordinator observed = new TaskCoordinator(queue);
        AtomicInteger removed = new AtomicInteger(), good = new AtomicInteger(), finalized = new AtomicInteger();
        java.util.List<Boolean> busy = new java.util.ArrayList<>();
        java.util.List<Integer> finalizedAtRelease = new java.util.ArrayList<>();
        TaskCoordinator.Listener detached = removed::incrementAndGet;
        TaskCoordinator.Listener bad = () -> { throw new AssertionError("observer fault"); };
        TaskCoordinator.Listener runtimeBad = () -> { throw new IllegalStateException("observer fault"); };
        TaskCoordinator.Listener listener = () -> {
            good.incrementAndGet(); busy.add(observed.isBusy());
            if (!observed.isBusy()) finalizedAtRelease.add(finalized.get());
        };
        observed.addListener(detached); observed.removeListener(detached);
        observed.addListener(bad); observed.addListener(runtimeBad);
        observed.addListener(listener); observed.addListener(listener);
        check(observed.submit(() -> finalized.incrementAndGet()));
        check(!observed.submit(() -> { throw new AssertionError(); }));
        queue.drain(); observed.awaitFree();
        check(removed.get() == 0 && good.get() == 2);
        check(busy.equals(java.util.Arrays.asList(true, false)));
        check(finalizedAtRelease.equals(java.util.Collections.singletonList(1)));
        observed.removeListener(listener); observed.removeListener(bad); observed.removeListener(runtimeBad);
        observed.submit(() -> {}); queue.drain(); check(good.get() == 2);

        // Fatal notification propagates, but neither owner nor await latch is retained.
        TaskCoordinator.Listener fatal = () -> { throw new InternalError("synthetic fatal"); };
        observed.addListener(fatal);
        AtomicInteger rollback = new AtomicInteger();
        try { observed.submit(() -> { throw new AssertionError("must not execute"); }, () -> {}, rollback::incrementAndGet); throw new AssertionError("fatal swallowed"); }
        catch (InternalError expected) { check(!observed.isBusy() && rollback.get() == 1); }
        observed.awaitFree(); observed.removeListener(fatal);
        observed.submit(() -> {}); observed.addListener(fatal);
        try { queue.drain(); throw new AssertionError("release fatal swallowed"); }
        catch (InternalError expected) { check(!observed.isBusy()); }
        observed.awaitFree(); observed.removeListener(fatal);

        System.out.println("PASS " + checks + " task coordinator checks (host only; no Thread.sleep busy polling)");
    }

    private static void awaitRelease(TaskCoordinator c) {
        try { c.awaitFree(); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }
}
