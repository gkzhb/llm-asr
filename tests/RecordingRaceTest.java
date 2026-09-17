import org.llmasr.minimal.audio.RecordingControl;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic session-gate interleavings; no Android microphone or sleeps. */
public final class RecordingRaceTest {
    static int checks;
    static void check(boolean condition) { checks++; if(!condition)throw new AssertionError("check "+checks); }
    static void await(CountDownLatch latch) {
        try { if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("latch timeout"); }
        catch(InterruptedException e) { throw new AssertionError(e); }
    }
    static void join(Thread t) throws Exception { t.join(4000); check(!t.isAlive()); }
    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((t,e) -> error.compareAndSet(null,e));
        // R1: cancel/pause wins while the fake backend is parked before start gate.
        RecordingControl before=new RecordingControl(); AtomicInteger starts=new AtomicInteger();
        CountDownLatch ready=new CountDownLatch(1), go=new CountDownLatch(1);
        Thread worker=new Thread(() -> {
            ready.countDown(); await(go);
            try { before.start(starts::incrementAndGet); } finally { before.captureReleased(); }
        }); worker.start(); await(ready); check(before.cancel()); go.countDown(); join(worker);
        check(starts.get()==0 && before.released() && before.cancelled()); check(!before.tryCommitInference());

        // Start wins the gate: cancellation cannot linearize inside backend.start().
        RecordingControl active=new RecordingControl(); CountDownLatch entered=new CountDownLatch(1), finishStart=new CountDownLatch(1);
        AtomicBoolean starting=new AtomicBoolean(), overlap=new AtomicBoolean();
        CountDownLatch cancelled=new CountDownLatch(1), cancelAttempt=new CountDownLatch(1);
        worker=new Thread(() -> {
            try {
                active.start(() -> { starting.set(true); entered.countDown(); await(finishStart); starting.set(false); });
                await(cancelled);
            } finally { active.captureReleased(); }
        }); worker.start(); await(entered);
        Thread cancel=new Thread(() -> { cancelAttempt.countDown(); active.cancel(); overlap.set(starting.get()); cancelled.countDown(); });
        cancel.start(); await(cancelAttempt); finishStart.countDown(); join(cancel); join(worker);
        check(!overlap.get() && active.cancelled() && active.released()); check(!active.tryCommitInference());

        // R2: stop -> release -> pause wins before inference commit; zero inference/WAV publications.
        RecordingControl handoff=new RecordingControl(); AtomicInteger inference=new AtomicInteger(), wavs=new AtomicInteger();
        CountDownLatch released=new CountDownLatch(1), commit=new CountDownLatch(1);
        worker=new Thread(() -> {
            handoff.start(() -> {}); handoff.stop(); handoff.captureReleased(); released.countDown(); await(commit);
            if(handoff.tryCommitInference()) { wavs.incrementAndGet(); inference.incrementAndGet(); }
        }); worker.start(); await(released); check(handoff.cancel()); commit.countDown(); join(worker);
        check(inference.get()==0 && wavs.get()==0 && !handoff.committed());

        // Commit wins: late Back/pause reports cancellation unavailable and cannot undo inference.
        RecordingControl committed=new RecordingControl(); committed.start(() -> {});
        check(!committed.tryCommitInference()); committed.stop(); committed.captureReleased();
        check(committed.tryCommitInference()); check(!committed.cancel()); check(!committed.cancelled());
        check(!committed.tryCommitInference()); check(!committed.start(() -> { throw new AssertionError("restarted"); }));

        // Exception cleanup acknowledgement never permits a failed start to reach inference.
        RecordingControl failed=new RecordingControl(); boolean threw=false;
        try { failed.start(() -> { throw new IllegalStateException("fake start failure"); }); }
        catch(IllegalStateException expected) { threw=true; } finally { failed.captureReleased(); }
        check(threw && failed.released()); check(!failed.tryCommitInference());
        check(!failed.start(() -> { throw new AssertionError("start after release"); }));
        if(error.get()!=null)throw new AssertionError("worker failure",error.get());
        System.out.println("PASS "+checks+" recording lifecycle gate checks (fake backend; not device release latency)");
    }
}
