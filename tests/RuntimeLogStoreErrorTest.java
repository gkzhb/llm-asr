package org.llmasr.minimal;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogSink;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;

/** Real store recovery and publisher sequencing, with bounded joins and no reflection. */
public final class RuntimeLogStoreErrorTest {
    private static int checks;
    private static void check(boolean b, String message) { checks++; if (!b) throw new AssertionError(message); }
    private static void append(RuntimeLogStore s) { s.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok"); }
    private static void await(CountDownLatch l) throws InterruptedException {
        if (!l.await(3, TimeUnit.SECONDS)) throw new AssertionError("latch timeout");
    }
    private static void join(Thread t) throws InterruptedException {
        t.join(3000); check(!t.isAlive(), "producer must terminate");
    }
    private static void recovery() {
        RuntimeLogStore s = new RuntimeLogStore();
        RuntimeLogSink broken = (events, dropped) -> { throw new AssertionError("observer"); };
        s.addSink(broken);
        try { append(s); } catch (AssertionError oldImplementation) { /* exercise recovery after original failure */ }
        s.removeSink(broken);
        AtomicInteger delivered = new AtomicInteger();
        s.addSink((events, dropped) -> delivered.incrementAndGet());
        append(s);
        check(delivered.get() == 1, "later append must notify after failed sink is removed");
        check(s.size() == 2, "both events retained");
        for (Error error : new Error[] {new AssertionError("assertion"), new LinkageError("linkage")}) {
            RuntimeLogSink bad = (events, dropped) -> { throw error; };
            s.addSink(bad);
            Error seen = null;
            try { append(s); } catch (Error e) { seen = e; }
            check(seen == error, "observer Error still signals incomplete telemetry");
            s.removeSink(bad); append(s);
        }
        check(delivered.get() == 5, "subsequent delivery recovers after observer Error");
    }
    private static void fatalRecovery() {
        for (Error fatal : new Error[] {new OutOfMemoryError("synthetic"), new ThreadDeath()}) {
            RuntimeLogStore s = new RuntimeLogStore();
            RuntimeLogSink bad = (events, dropped) -> { throw fatal; };
            s.addSink(bad);
            Error seen = null;
            try { append(s); } catch (Error e) { seen = e; }
            check(seen == fatal, "fatal identity propagated");
            s.removeSink(bad);
            AtomicInteger deliveries = new AtomicInteger();
            s.addSink((events, dropped) -> deliveries.incrementAndGet());
            append(s);
            check(deliveries.get() == 1 && s.size() == 2, "publisher released after fatal error");
        }
    }
    private static void concurrentAndReentrant() throws Exception {
        RuntimeLogStore s = new RuntimeLogStore();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true), reenter = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Long> seen = new CopyOnWriteArrayList<>();
        s.addSink((events, dropped) -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                try { await(release); } catch (Throwable t) { failure.compareAndSet(null, t); }
                // The publisher now drains concurrently appended events.
            }
        });
        s.addSink((events, dropped) -> {
            seen.add(events.get(events.size()-1).sequence);
            if (reenter.compareAndSet(true, false)) append(s);
        });
        Thread publisher = new Thread(() -> { try { append(s); } catch (Throwable t) { failure.compareAndSet(null,t); } });
        publisher.setDaemon(true); publisher.start();
        List<Thread> producers = new ArrayList<>();
        try {
            await(entered);
            // A separate thread must read and append while the first sink blocks:
            // proving callbacks are not executed while holding the store monitor.
            for (int i=0;i<4;i++) {
                Thread t = new Thread(() -> {
                    try { s.snapshot(); for(int n=0;n<50;n++) append(s); }
                    catch(Throwable e) { failure.compareAndSet(null,e); }
                });
                t.setDaemon(true); producers.add(t); t.start();
            }
            for(Thread t:producers) join(t);
        } finally { release.countDown(); join(publisher); }
        check(failure.get()==null, "worker failure: " + failure.get());
        check(s.size()==202, "initial + 200 concurrent + one reentrant append");
        check(!seen.isEmpty() && seen.get(seen.size()-1)==201L, "latest coalesced snapshot delivered");
        long previous=-1;
        for(long seq:seen) { check(seq>previous, "strictly ordered snapshots"); previous=seq; }
        append(s);
        check(seen.get(seen.size()-1)==202L, "idle publisher restarts");
    }
    public static void main(String[] args) throws Exception {
        recovery(); fatalRecovery(); concurrentAndReentrant();
        System.out.println("PASS RuntimeLogStoreErrorTest: " + checks + " recovery/fatal/concurrent/reentrant checks");
    }
}
