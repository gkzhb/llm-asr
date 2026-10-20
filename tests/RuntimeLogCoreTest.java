package org.llmasr.minimal.diagnostics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.llmasr.minimal.diagnostics.RuntimeLogClock;
import org.llmasr.minimal.diagnostics.RuntimeLogCodec;
import org.llmasr.minimal.diagnostics.RuntimeLogEvent;
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind;
import org.llmasr.minimal.diagnostics.RuntimeLogPersistence;
import org.llmasr.minimal.diagnostics.RuntimeLogSink;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;
import org.llmasr.minimal.diagnostics.RuntimeLogWorker;

/** Host tests execute only production logging core; real temporary filesystem,
 * real symlinks and threads. No Android/native runtime correctness claim. */
public final class RuntimeLogCoreTest {
    private static final String ID = "12345678-abcd-1234-9876-123456789abc";
    private static int groups;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, Class<? extends Exception> type) throws Exception {
        try { action.run(); } catch (Exception expected) {
            if (type.isInstance(expected)) return;
            throw expected;
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
    private static RuntimeLogEvent event(long sequence) {
        return new RuntimeLogEvent(sequence, 1700000000123L, -100, RuntimeLogEventKind.INFERENCE_COMPLETED,
            RuntimeLogSource.IME, ID, "ok", 1234567890L);
    }
    private static boolean append(RuntimeLogStore store) {
        return store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok");
    }
    private static void passed(String message) { groups++; System.out.println("PASS " + message); }
    private static void await(CountDownLatch latch) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), "latch timeout");
    }
    private static void join(Thread thread) throws Exception {
        thread.join(5000);
        check(!thread.isAlive(), "thread timeout");
    }
    private static void waitBlocked(Thread thread) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < until) Thread.sleep(1);
        check(thread.getState() == Thread.State.BLOCKED, "worker not blocked on persistence monitor");
    }
    private static final class ManualExecutor implements Executor {
        final Deque<Runnable> queue = new ArrayDeque<>();
        int submissions;
        int highWater;
        @Override public synchronized void execute(Runnable task) {
            submissions++;
            queue.add(task);
            highWater = Math.max(highWater, queue.size());
        }
        synchronized int pending() { return queue.size(); }
        void runOne() {
            Runnable task;
            synchronized (this) { task = queue.remove(); }
            task.run();
        }
    }

    private static void eventBoundary() throws Exception {
        RuntimeLogClock.Fake clock = new RuntimeLogClock.Fake(1700000000123L, 500);
        RuntimeLogStore store = new RuntimeLogStore(3, clock);
        for (String secret : new String[]{"秘密内容", "secret", "deadbeef", "用户音频", "file:///private/model",
                "/private/path", "https://provider", "exception-message", "ok\nsecret", "OK"}) {
            check(!store.append(RuntimeLogEventKind.REQUEST_FAILURE, RuntimeLogSource.APP, ID, secret), "private detail rejected");
            rejects(() -> RuntimeLogEvent.sanitizeDetail(secret), IllegalArgumentException.class);
        }
        for (String bad : new String[]{"", "deadbeef", "------------------------------------",
                "1234567-abcd-1234-9876-123456789abcd", ID.toUpperCase(Locale.ROOT), "秘密"})
            check(!store.append(RuntimeLogEventKind.REQUEST_FAILURE, RuntimeLogSource.APP, bad, "ok"), "UUID exact shape");
        check(!store.append(null, RuntimeLogSource.APP, ID, "ok"), "null enum");
        check(!store.append(RuntimeLogEventKind.REQUEST_SUCCESS, RuntimeLogSource.APP, ID, "ok", -2), "negative duration");
        rejects(() -> new RuntimeLogEvent(-1, 0, 0, RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, null, null), IllegalArgumentException.class);
        rejects(() -> new RuntimeLogStore(1001), IllegalArgumentException.class);
        check(store.append(RuntimeLogEventKind.MODEL_LOAD_STARTED, RuntimeLogSource.APP, ID, "ok"), "start");
        long start = clock.monotonicNanos();
        clock.advance(-10000, 1234567890L);
        check(store.append(RuntimeLogEventKind.MODEL_LOAD_COMPLETED, RuntimeLogSource.APP, ID, "ok",
            clock.monotonicNanos() - start), "duration append");
        List<RuntimeLogEvent> snapshot = store.snapshot();
        check(snapshot.get(0).sequence == 0 && snapshot.get(1).sequence == 1, "rejects do not consume sequence");
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+05:30"));
            String line = RuntimeLogStore.formatEvent(snapshot.get(1));
            check(line.startsWith("2023-11-15 03:43:10.123 +05:30"), "timestamp milliseconds/offset: " + line);
            check(line.contains("elapsed_ms=1234.567890"), "monotonic duration despite wall rollback");
            check(!RuntimeLogStore.formatEvent(snapshot.get(0)).contains("elapsed_ms"), "unknown duration omitted");
        } finally { TimeZone.setDefault(original); }
        RuntimeLogEvent e = snapshot.get(1);
        check(e.equals(e.withSequence(e.sequence)) && e.hashCode() == e.withSequence(e.sequence).hashCode(), "value equality");
        check(!e.equals(new RuntimeLogEvent(e.sequence, e.wallMillis, e.monotonicNanos, e.kind, e.source, e.requestId, e.detail, 1)), "duration equality");
        rejects(() -> snapshot.clear(), UnsupportedOperationException.class);
        for (int i = 0; i < 10; i++) append(store);
        check(snapshot.size() == 2 && snapshot.get(0).sequence == 0, "snapshot frozen");
        check(store.size() == 3 && store.droppedCount() == 9, "bounded eviction");
        for (String detail : new String[]{"native-runtime", "native-null", "parse-failed"})
            check(store.append(RuntimeLogEventKind.REQUEST_FAILURE, RuntimeLogSource.IME, ID, detail), "existing fixed caller label");
        passed("typed privacy boundary, canonical UUID, timestamp/elapsed formatting, immutable bounded snapshots");
    }

    private static void publication() throws Exception {
        RuntimeLogStore store = new RuntimeLogStore();
        List<Long> delivered = Collections.synchronizedList(new ArrayList<>());
        List<Integer> drops = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        store.addSink((snapshot, dropped) -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                try { await(release); } catch (Exception failure) { throw new AssertionError(failure); }
            }
        });
        store.addSink((snapshot, dropped) -> { throw new IllegalStateException("private listener error"); });
        store.addSink((snapshot, dropped) -> {
            delivered.add(snapshot.get(snapshot.size() - 1).sequence);
            drops.add(dropped);
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread publisher = new Thread(() -> {
            try { check(append(store), "first append"); } catch (Throwable t) { failure.set(t); }
        });
        publisher.start(); await(entered);
        Thread[] producers = new Thread[6];
        for (int i = 0; i < producers.length; i++) {
            producers[i] = new Thread(() -> {
                try { for (int n = 0; n < 500; n++) check(append(store), "concurrent append"); }
                catch (Throwable t) { failure.set(t); }
            });
            producers[i].start();
        }
        for (Thread producer : producers) join(producer);
        check(store.size() == 1000 && store.droppedCount() == 2001, "blocked listener does not lock producers");
        release.countDown(); join(publisher);
        check(failure.get() == null, "thread failure: " + failure.get());
        check(delivered.equals(Arrays.asList(0L, 3000L)), "serialized latest-only publication: " + delivered);
        check(drops.equals(Arrays.asList(0, 2001)), "cumulative sink drop count");
        long previous = 2000;
        for (RuntimeLogEvent e : store.snapshot()) check(e.sequence == ++previous, "contiguous insertion sequence");
        RuntimeLogStore reentrant = new RuntimeLogStore(3);
        AtomicBoolean nested = new AtomicBoolean();
        List<Long> order = new ArrayList<>();
        reentrant.addSink((s, d) -> { if (nested.compareAndSet(false, true)) append(reentrant); });
        reentrant.addSink((s, d) -> order.add(s.get(s.size() - 1).sequence));
        append(reentrant);
        check(order.equals(Arrays.asList(0L, 1L)), "reentrant publication ordered");
        passed("real concurrent sequence/eviction, serialized coalescing sinks, reentrancy and listener fault isolation");
    }

    private static void persistence(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("persist"));
        RuntimeLogPersistence p = new RuntimeLogPersistence(root.toFile());
        check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.MISSING, "missing");
        List<RuntimeLogEvent> events = Arrays.asList(event(2), event(5));
        check(p.save(events, null) == 2, "save");
        check(new RuntimeLogPersistence(root.toFile()).load().equals(events), "reopen exact values");
        Path file = p.file().toPath();
        byte[] valid = Files.readAllBytes(file);
        String text = new String(valid, StandardCharsets.UTF_8);
        List<byte[]> corrupt = new ArrayList<>();
        for (int i = 0; i < valid.length; i++) corrupt.add(Arrays.copyOf(valid, i));
        for (String bad : new String[]{text + "junk", text.replace("END", "END\nEND"),
                text.replace("V1\t2", "V1\t1"), text.replace("\tok\n", "\t秘密\n"),
                text.replace("\tok\n", "\tsecret\n"), text.replace(ID, "deadbeef"),
                text.replace("INFERENCE_COMPLETED", "UNKNOWN"), text.replace("\n5\t", "\n2\t"),
                text.replace("\n5\t", "\n1\t"), text.replace("\n2\t", "\n02\t"),
                text.replace("\t1234567890\t", "\t-2\t"), text.replace("\tok\n", "\t\n"),
                text.replace("\tIME\t", "\tIME\textra\t"), "[]", "[{\"seq\":0}]garbage"})
            corrupt.add(bad.getBytes(StandardCharsets.UTF_8));
        corrupt.add(new byte[]{(byte) 0xc3, (byte) 0x28});
        for (byte[] bad : corrupt) {
            Files.write(file, bad);
            check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.CORRUPT, "reject entire malformed file");
        }
        Files.write(file, new byte[(int) RuntimeLogPersistence.MAX_FILE_BYTES + 1]);
        check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.OVERSIZED, "oversized load");
        List<RuntimeLogEvent> noisy = new ArrayList<>();
        for (int i = 0; i < 20000; i++) noisy.add(event(i));
        check(p.save(noisy, null) == 1000, "large input suffix bounded before encode");
        check(Files.size(file) <= RuntimeLogPersistence.MAX_FILE_BYTES && p.load().get(0).sequence == 19000, "file cap/newest suffix");
        byte[] saved = Files.readAllBytes(file);
        check(p.save(Arrays.asList(event(4), event(3)), reason -> { throw new IllegalStateException(); }) == -1,
            "invalid sequence and throwing failure sink isolated");
        check(Arrays.equals(saved, Files.readAllBytes(file)), "invalid save leaves final intact");
        Files.write(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part"), "incomplete secret".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("unrelated"), new byte[]{9});
        check(p.load().size() == 1000, "stale part never recovered");
        check(p.save(events, null) == 2 && !Files.exists(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part")), "stale regular part replaced atomically");
        check(Files.readAllBytes(root.resolve("unrelated"))[0] == 9, "unknown files untouched");
        passed("real file reopen, strict complete parser (all truncated prefixes), corrupt/private/oversize rejection, hard caps and stale part");
    }

    private static void paths(Path base) throws Exception {
        Path real = Files.createDirectories(base.resolve("user/0/app/files"));
        Path alias = base.resolve("data");
        Files.createSymbolicLink(alias, base.resolve("user/0"));
        File noIo = new File(alias.resolve("app/files").toString()) {
            @Override public File getCanonicalFile() { throw new AssertionError("constructor disk IO"); }
        };
        new RuntimeLogPersistence(noIo);
        RuntimeLogPersistence p = new RuntimeLogPersistence(alias.resolve("app/files").toFile());
        List<RuntimeLogEvent> events = Collections.singletonList(event(0));
        check(p.save(events, null) == 1 && p.load().equals(events), "trusted ancestor alias allowed");
        Path finalFile = real.resolve(RuntimeLogPersistence.FILE_NAME);
        Path outside = Files.write(base.resolve("outside"), new byte[]{7, 8});
        for (String leaf : new String[]{RuntimeLogPersistence.FILE_NAME, RuntimeLogPersistence.FILE_NAME + ".part"}) {
            Path managed = real.resolve(leaf);
            for (Path target : new Path[]{outside, base.resolve("dangling-target")}) {
                Files.deleteIfExists(managed);
                Files.createSymbolicLink(managed, target);
                check(p.load().isEmpty() && p.status() == RuntimeLogPersistence.Status.UNSAFE_PATH, "symlink load rejected");
                check(p.save(events, null) == -1 && p.status() == RuntimeLogPersistence.Status.UNSAFE_PATH, "symlink save rejected");
                check(Arrays.equals(Files.readAllBytes(outside), new byte[]{7,8}) && !Files.exists(base.resolve("dangling-target")), "outside untouched");
                check(Files.isSymbolicLink(managed), "link not removed");
                Files.delete(managed);
            }
            Files.deleteIfExists(managed); Files.createDirectory(managed);
            check(p.save(events, null) == -1 && p.load().isEmpty(), "managed directory rejected");
            Files.delete(managed);
            check(p.save(events, null) == 1, "recovery after unsafe leaf removed");
        }
        // Failed staging cannot replace an existing valid final.
        byte[] good = Files.readAllBytes(finalFile);
        Files.createDirectory(real.resolve(RuntimeLogPersistence.FILE_NAME + ".part"));
        check(p.save(Collections.singletonList(event(1)), null) == -1, "disk failure");
        check(Arrays.equals(good, Files.readAllBytes(finalFile)), "failed save final unchanged");
        Files.delete(real.resolve(RuntimeLogPersistence.FILE_NAME + ".part"));
        Path alternate = Files.createDirectories(base.resolve("alternate/app/files"));
        Files.delete(alias); Files.createSymbolicLink(alias, alternate.getParent().getParent());
        check(p.save(events, null) == -1 && p.status() == RuntimeLogPersistence.Status.UNSAFE_PATH, "pinned parent alias retarget refused");
        check(!Files.exists(alternate.resolve(RuntimeLogPersistence.FILE_NAME)), "no retargeted write");
        RuntimeLogPersistence missing = new RuntimeLogPersistence(base.resolve("absent-parent").toFile());
        AtomicReference<String> reason = new AtomicReference<>();
        check(missing.save(events, reason::set) == -1 && "unsafe-path".equals(reason.get()), "missing trusted root fixed failure");
        // Permission denial exercises a genuine IOException, not just path validation.
        Path readonly = Files.createDirectory(base.resolve("readonly"));
        Set<java.nio.file.attribute.PosixFilePermission> permissions = Files.getPosixFilePermissions(readonly);
        try {
            Files.setPosixFilePermissions(readonly, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
            RuntimeLogPersistence denied = new RuntimeLogPersistence(readonly.toFile());
            check(denied.save(events, null) == -1 && denied.status() == RuntimeLogPersistence.Status.IO_ERROR,
                "real filesystem permission denied (run as non-root)");
        } finally { Files.setPosixFilePermissions(readonly, permissions); }
        passed("real final/part symlinks including dangling, leaf directories, trusted parent alias pinning, failed atomic staging and permission-denied IO");
    }

    private static void workers(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("worker"));
        RuntimeLogPersistence p = new RuntimeLogPersistence(root.toFile());
        p.save(Arrays.asList(event(40), event(41)), null);
        RuntimeLogStore store = new RuntimeLogStore(4);
        append(store);
        List<RuntimeLogEvent> early = store.snapshot();
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, p, null, executor);
        check(executor.pending() == 1 && store.size() == 1, "constructor schedules but does not load");
        append(store); worker.restoreFromDisk();
        check(executor.pending() == 1, "startup coalesced");
        executor.runOne();
        List<RuntimeLogEvent> restored = store.snapshot();
        check(restored.size() == 4 && restored.get(0).sequence == 40 && restored.get(2).sequence == 42
            && restored.get(3).sequence == 43, "history precedes and preserves early appends");
        check(early.get(0).sequence == 0 && restored.get(2).wallMillis == early.get(0).wallMillis, "early snapshot stable after rebase");
        check(p.load().equals(restored), "merged recovery persisted");
        worker.restoreFromDisk(); executor.runOne();
        check(store.snapshot().equals(restored), "one recovery only");
        int before = executor.submissions;
        Thread blocked;
        synchronized (p) {
            append(store);
            blocked = new Thread(executor::runOne);
            blocked.start(); waitBlocked(blocked);
            for (int i = 0; i < 10000; i++) check(append(store), "noisy append while disk blocked");
            check(executor.pending() == 0 && executor.submissions == before + 1, "only running drain and dirty bit");
        }
        join(blocked);
        check(p.load().equals(store.snapshot()), "latest dirty snapshot saved after blocked write");
        check(executor.highWater == 1 && worker.droppedSchedules() == 0, "bounded task count");
        check(store.droppedCount() == 10001, "recovery plus noisy rotation drops");
        append(store); worker.close(); worker.close();
        check(!worker.schedulePersist(), "closed rejects new work");
        executor.runOne();
        check(p.load().equals(store.snapshot()), "close drains accepted work");
        append(store); check(executor.pending() == 0, "close removes sink");
        ExecutorService injected = Executors.newSingleThreadExecutor();
        RuntimeLogWorker external = new RuntimeLogWorker(new RuntimeLogStore(), p, null, injected);
        external.close(); check(!injected.isShutdown(), "injected executor not owned");
        injected.shutdown(); check(injected.awaitTermination(5, TimeUnit.SECONDS), "external drained");
        // Saturation of the injected executor is isolated; next append retries dirty work.
        AtomicBoolean rejecting = new AtomicBoolean(true);
        ManualExecutor retry = new ManualExecutor();
        RuntimeLogStore rejectedStore = new RuntimeLogStore();
        RuntimeLogWorker rejected = new RuntimeLogWorker(rejectedStore, p, reason -> { throw new IllegalStateException(); },
            task -> { if (rejecting.get()) throw new RejectedExecutionException(); retry.execute(task); });
        check(rejected.droppedSchedules() == 1 && "queue-full".equals(rejected.lastFailure()), "plain rejected executor safe");
        rejecting.set(false); append(rejectedStore); retry.runOne();
        check(rejected.successfulSaves() > 0 && rejected.lastFailure() == null, "retry after executor rejection");
        rejected.close();
        // Direct executor is honored for deterministic tests, never discarded to null.
        RuntimeLogWorker direct = new RuntimeLogWorker(new RuntimeLogStore(), p, null, Runnable::run);
        check(direct.successfulSaves() == 1, "plain executor executed"); direct.close();
        passed("plain/injected executors, first asynchronous recovery preserving early events, blocked write + 10000 appends, latest coalescing/close/retry");
    }

    private static void ownedWorker(Path base) throws Exception {
        RuntimeLogPersistence p = new RuntimeLogPersistence(Files.createDirectory(base.resolve("owned")).toFile());
        RuntimeLogStore store = new RuntimeLogStore();
        RuntimeLogWorker worker;
        synchronized (p) {
            worker = new RuntimeLogWorker(store, p, null);
            check(append(store), "owned worker constructor and append never wait for disk");
            long start = System.nanoTime();
            worker.close();
            check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "close never waits on blocked IO");
        }
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (worker.successfulSaves() == 0 && System.nanoTime() < until) Thread.sleep(1);
        check(worker.successfulSaves() > 0 && p.load().equals(store.snapshot()), "owned executor drains accepted startup after close");
        passed("production-owned background executor: no synchronous startup disk, nonblocking close and graceful accepted drain");
    }

    private static void workerFailures(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("failure"));
        Path part = Files.createDirectory(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part"));
        RuntimeLogStore store = new RuntimeLogStore();
        RuntimeLogPersistence p = new RuntimeLogPersistence(root.toFile());
        ManualExecutor executor = new ManualExecutor();
        AtomicInteger failures = new AtomicInteger();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, p, reason -> {
            failures.incrementAndGet();
            store.append(RuntimeLogEventKind.LOG_PERSISTENCE_FAILED, RuntimeLogSource.SHARED, null, reason);
        }, executor);
        executor.runOne();
        check(failures.get() == 1 && "unsafe-path".equals(worker.lastFailure()) && worker.failedSaves() == 1,
            "failure callback append cannot create recursive storm");
        for (int i = 0; i < 10; i++) append(store);
        executor.runOne();
        check(failures.get() == 1 && worker.failedSaves() == 2, "same failure deduped");
        Files.delete(part); append(store); executor.runOne();
        check(worker.lastFailure() == null && p.load().equals(store.snapshot()), "IO repaired, status and persisted store recover");
        Files.createDirectory(part); append(store); executor.runOne();
        check(failures.get() == 2 && worker.failedSaves() == 4, "new failure episode observable, single callback + finite dirty retry");
        worker.close();
        // Typed legal extreme history is normalized, never discarded as corrupt.
        Path overflow = Files.createDirectory(base.resolve("overflow"));
        RuntimeLogPersistence max = new RuntimeLogPersistence(overflow.toFile());
        max.save(Collections.singletonList(event(Long.MAX_VALUE - 1)), null);
        RuntimeLogStore live = new RuntimeLogStore(); append(live);
        ManualExecutor tasks = new ManualExecutor();
        RuntimeLogWorker overflowWorker = new RuntimeLogWorker(live, max, null, tasks);
        tasks.runOne(); check(live.size() == 2 && live.snapshot().get(0).sequence == 0
            && live.snapshot().get(1).sequence == 1 && append(live), "legal extreme history and early event retained");
        check(overflowWorker.recoveryStatus() == RuntimeLogPersistence.Status.OK, "legal extreme recovery remains OK after save");
        overflowWorker.close(); tasks.runOne();
        passed("observable/deduped disk failure, recursive callback isolation, recovery from failure and hostile sequence overflow");
    }

    /** These fixtures exercise the real Store/Worker/Persistence, never reflected counters. */
    private static final class SequenceScope implements AutoCloseable {
        final Path root = Files.createTempDirectory("runtime-sequence-");
        final List<Thread> threads = new ArrayList<>();
        final List<CountDownLatch> gates = new ArrayList<>();
        final List<RuntimeLogWorker> workers = new ArrayList<>();
        final List<ManualExecutor> executors = new ArrayList<>();
        SequenceScope() throws IOException {}
        CountDownLatch gate() { CountDownLatch gate = new CountDownLatch(1); gates.add(gate); return gate; }
        void start(Thread thread) { threads.add(thread); thread.start(); }
        RuntimeLogWorker worker(RuntimeLogStore store, RuntimeLogPersistence persistence, ManualExecutor executor) {
            RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, executor);
            workers.add(worker); executors.add(executor); return worker;
        }
        @Override public void close() throws Exception {
            for (CountDownLatch gate : gates) gate.countDown();
            Throwable failure = null;
            boolean interrupted = Thread.interrupted(), alive = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            for (Thread thread : threads) {
                while (thread.isAlive()) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) break;
                    try { thread.join(left / 1_000_000, (int) (left % 1_000_000)); }
                    catch (InterruptedException problem) {
                        interrupted = true;
                        if (failure == null) failure = problem; else failure.addSuppressed(problem);
                    }
                }
                alive |= thread.isAlive();
            }
            if (alive) {
                IOException problem = new IOException("sequence worker not terminated; preserving fixture");
                if (failure == null) failure = problem; else failure.addSuppressed(problem);
            } else {
                for (int i = 0; i < workers.size(); i++) {
                    try {
                        workers.get(i).close();
                        ManualExecutor executor = executors.get(i);
                        int remaining = 32;
                        while (executor.pending() > 0 && remaining-- > 0) executor.runOne();
                        check(executor.pending() == 0, "sequence cleanup drain bounded");
                    } catch (Throwable problem) {
                        if (failure == null) failure = problem; else failure.addSuppressed(problem);
                    }
                }
                try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                    for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                        Files.deleteIfExists(path);
                } catch (Throwable problem) {
                    if (failure == null) failure = problem; else failure.addSuppressed(problem);
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof Exception) throw (Exception) failure;
        }
    }

    private static final class SequenceClock implements RuntimeLogClock {
        long wall = 1700000000123L, mono = -900;
        int walls, monos;
        boolean broken;
        @Override public long wallTimeMillis() {
            walls++;
            if (broken) throw new IllegalStateException("synthetic clock");
            return wall--;
        }
        @Override public long monotonicNanos() { monos++; return mono++; }
    }

    private static void ordered(List<RuntimeLogEvent> values) {
        long previous = -1;
        for (RuntimeLogEvent value : values) {
            check(value.sequence > previous && value.sequence < Long.MAX_VALUE, "sequence snapshot strictly ordered");
            previous = value.sequence;
        }
    }
    private static void sameFields(RuntimeLogEvent expected, RuntimeLogEvent actual) {
        check(expected.withSequence(actual.sequence).equals(actual), "sequence rebase preserves every other field");
    }
    private static byte[] sequenceBytes(List<RuntimeLogEvent> values) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RuntimeLogCodec.writeSnapshot(values, out); return out.toByteArray();
    }
    private static void appendMany(RuntimeLogStore store, int count, String label) {
        for (int i = 0; i < count; i++) check(append(store), label + " append rejected at i=" + i);
        ordered(store.snapshot());
    }

    private static void nearMaxProgress(boolean early) throws Exception {
        try (SequenceScope scope = new SequenceScope()) {
            int capacity = 4;
            RuntimeLogPersistence p = new RuntimeLogPersistence(scope.root.toFile());
            List<RuntimeLogEvent> history = Collections.singletonList(event(Long.MAX_VALUE - (early ? 3 : 2)));
            check(p.save(history, null) == 1, "near-max fixture save");
            check(new RuntimeLogPersistence(scope.root.toFile()).load().equals(history), "near-max fixture is legal V1");
            RuntimeLogStore store = new RuntimeLogStore(capacity, new SequenceClock());
            if (early) check(append(store), "near-max early append");
            List<RuntimeLogEvent> frozenEarly = store.snapshot();
            ManualExecutor tasks = new ManualExecutor();
            RuntimeLogWorker worker = scope.worker(store, p, tasks); tasks.runOne();
            check(worker.recoveryStatus() == RuntimeLogPersistence.Status.OK, "near-max legal recovery OK");
            List<RuntimeLogEvent> frozen = store.snapshot();
            byte[] frozenBytes = sequenceBytes(frozen);
            check(frozen.get(0).equals(history.get(0)), "near-max representable history unchanged");
            appendMany(store, 4 * capacity + 7, "near-max " + (early ? "early" : "no-early"));
            check(store.size() == capacity && store.droppedCount() == history.size() + frozenEarly.size() + 4 * capacity + 7 - capacity,
                "near-max real evictions only");
            check(frozen.get(0).equals(history.get(0)) && Arrays.equals(frozenBytes, sequenceBytes(frozen)), "high snapshot stays frozen");
            if (early) check(frozenEarly.get(0).sequence == 0, "early captured ID unchanged");
            tasks.runOne(); check(p.load().equals(store.snapshot()), "near-max final snapshot persisted");
            worker.close();
            for (int restart = 0; restart < 2; restart++) {
                List<RuntimeLogEvent> saved = p.load();
                RuntimeLogStore reopened = new RuntimeLogStore(capacity, new SequenceClock());
                if (restart == 1) check(append(reopened), "restart early event");
                RuntimeLogPersistence disk = new RuntimeLogPersistence(scope.root.toFile());
                ManualExecutor queued = new ManualExecutor();
                RuntimeLogWorker next = scope.worker(reopened, disk, queued); queued.runOne();
                check(next.recoveryStatus() == RuntimeLogPersistence.Status.OK && next.lastFailure() == null, "restart still OK, not corrupt fallback");
                List<RuntimeLogEvent> loaded = reopened.snapshot();
                int skip = restart == 1 ? 1 : 0;
                for (int i = 0; i < saved.size() - skip; i++) check(loaded.get(i).equals(saved.get(i + skip)), "restart preserves history suffix");
                appendMany(reopened, 4 * capacity + 7, "restart " + restart);
                queued.runOne(); check(disk.load().equals(reopened.snapshot()), "restart latest persisted exact");
                next.close(); p = disk;
            }
        }
    }

    private static void legalMaxRecovery() throws Exception {
        try (SequenceScope scope = new SequenceScope()) {
            RuntimeLogPersistence p = new RuntimeLogPersistence(scope.root.toFile());
            List<RuntimeLogEvent> history = Collections.singletonList(event(Long.MAX_VALUE - 1));
            check(p.save(history, null) == 1, "max history save legal");
            RuntimeLogStore store = new RuntimeLogStore(4, new SequenceClock()); check(append(store), "max early");
            RuntimeLogEvent early = store.snapshot().get(0);
            ManualExecutor tasks = new ManualExecutor(); RuntimeLogWorker worker = scope.worker(store, p, tasks); tasks.runOne();
            check(worker.recoveryStatus() == RuntimeLogPersistence.Status.OK && store.size() == 2,
                "legal max history was discarded or marked corrupt");
            check(store.snapshot().get(0).sequence == 0 && store.snapshot().get(1).sequence == 1, "unrepresentable recovery dense order");
            sameFields(history.get(0), store.snapshot().get(0)); sameFields(early, store.snapshot().get(1));
            check(store.droppedCount() == 0 && p.load().equals(store.snapshot()), "normalize is not drop; persisted V1 exact");
            appendMany(store, 4 * store.capacity() + 7, "max restored"); tasks.runOne(); worker.close();
            RuntimeLogStore reopened = new RuntimeLogStore(4);
            ManualExecutor queued = new ManualExecutor();
            RuntimeLogWorker next = scope.worker(reopened, new RuntimeLogPersistence(scope.root.toFile()), queued); queued.runOne();
            check(next.recoveryStatus() == RuntimeLogPersistence.Status.OK, "max normalized history restarts OK");
            appendMany(reopened, 23, "max restarted"); queued.runOne();
        }
    }

    private static void sequenceRecoveryMatrix() throws Exception {
        for (int capacity : new int[] {1, 4, RuntimeLogStore.DEFAULT_CAPACITY}) {
            RuntimeLogStore ordinary = new RuntimeLogStore(capacity, new SequenceClock());
            for (int i = 0; i < capacity + 2; i++) check(append(ordinary), "ordinary early");
            List<RuntimeLogEvent> early = ordinary.snapshot(); long oldDrop = ordinary.droppedCount();
            ordinary.restore(Arrays.asList(event(2), event(5)));
            List<RuntimeLogEvent> current = ordinary.snapshot();
            check(current.get(current.size() - 1).sequence == capacity + 7L, "ordinary offset uses next, not retained size");
            check(ordinary.droppedCount() == oldDrop + 2, "ordinary merge drops exact");
            check(early.get(0).sequence == 2, "ordinary early snapshot unchanged");
            List<RuntimeLogEvent> once = ordinary.snapshot(); ordinary.restore(Collections.singletonList(event(1)));
            check(ordinary.snapshot().equals(once), "restore once only");
            RuntimeLogStore dense = new RuntimeLogStore(capacity, new SequenceClock());
            for (int i = 0; i < capacity + 2; i++) check(append(dense), "dense early");
            List<RuntimeLogEvent> before = dense.snapshot();
            dense.restore(Arrays.asList(event(7), event(Long.MAX_VALUE - 1)));
            current = dense.snapshot();
            check(dense.droppedCount() == 4 && current.size() == capacity, "dense trim only counts two merge evictions plus prior two");
            for (int i = 0; i < capacity; i++) {
                check(current.get(i).sequence == i + 2L, "dense-before-trim exact indices"); sameFields(before.get(i), current.get(i));
            }
            appendMany(dense, 4 * capacity + 7, "dense capacity " + capacity);
            RuntimeLogStore single = new RuntimeLogStore(capacity, new SequenceClock());
            single.restore(Collections.singletonList(event(Long.MAX_VALUE - 1)));
            check(single.snapshot().get(0).sequence == 0, "max no-early normalized");
            appendMany(single, 4 * capacity + 7, "no-early capacity " + capacity);
        }
        RuntimeLogStore gaps = new RuntimeLogStore(4); gaps.restore(Arrays.asList(event(2), event(5)));
        check(gaps.snapshot().equals(Arrays.asList(event(2), event(5))) && append(gaps) && gaps.snapshot().get(2).sequence == 6, "ordinary gap values exact");
        RuntimeLogStore empty = new RuntimeLogStore(4); empty.restore(Collections.emptyList());
        check(append(empty) && empty.snapshot().get(0).sequence == 0, "empty restore");
        RuntimeLogStore nearGap = new RuntimeLogStore(4); nearGap.restore(Arrays.asList(event(7), event(Long.MAX_VALUE - 2)));
        check(nearGap.snapshot().get(0).sequence == 7 && append(nearGap) && append(nearGap), "gapped legal history progresses across exhaustion");
        ordered(nearGap.snapshot());
    }

    private static void sequenceRejectsAndSnapshots() throws Exception {
        SequenceClock clock = new SequenceClock(); RuntimeLogStore store = new RuntimeLogStore(4, clock);
        store.restore(Arrays.asList(event(7), event(Long.MAX_VALUE - 2)));
        check(store.append(RuntimeLogEventKind.REQUEST_SUCCESS, RuntimeLogSource.APP, ID, "ok", 17), "reach max next");
        List<RuntimeLogEvent> before = store.snapshot(); byte[] bytes = sequenceBytes(before);
        AtomicInteger notifications = new AtomicInteger(); store.addSink((s, d) -> notifications.incrementAndGet());
        int walls = clock.walls, monos = clock.monos;
        check(!store.append(null, RuntimeLogSource.APP, ID, "ok"), "invalid enum at max");
        check(!store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, "bad", "ok"), "invalid UUID at max");
        check(!store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, ID, "private-sentinel"), "invalid detail at max");
        check(!store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.APP, ID, "ok", -2), "invalid duration at max");
        check(clock.walls == walls + 4 && clock.monos == monos + 4, "invalid fields retain one clock read per attempt");
        clock.broken = true; walls = clock.walls; monos = clock.monos;
        check(!append(store) && clock.walls == walls + 1 && clock.monos == monos, "clock failure reads once, rejects"); clock.broken = false;
        check(store.snapshot().equals(before) && store.droppedCount() == 0 && notifications.get() == 0, "invalid max append has no state or notification side effects");
        walls = clock.walls; monos = clock.monos;
        check(append(store) && clock.walls == walls + 1 && clock.monos == monos + 1, "valid max rebase reads clocks only once");
        List<RuntimeLogEvent> rebased = store.snapshot();
        for (int i = 0; i < before.size(); i++) {
            check(rebased.get(i).sequence == i, "append dense retained order"); sameFields(before.get(i), rebased.get(i));
        }
        check(notifications.get() == 1 && store.droppedCount() == 0, "rebase emits only completed append");
        check(before.get(1).sequence == Long.MAX_VALUE - 2 && before.get(2).sequence == Long.MAX_VALUE - 1, "old high IDs unchanged");
        check(Arrays.equals(bytes, sequenceBytes(before)), "old captured codec bytes unchanged");
        rejects(() -> before.clear(), UnsupportedOperationException.class); ordered(rebased);
        RuntimeLogStore full = new RuntimeLogStore(1); full.restore(Collections.singletonList(event(Long.MAX_VALUE - 2)));
        check(append(full) && append(full) && full.snapshot().get(0).sequence == 1 && full.droppedCount() == 2, "full append dense then trim exact");
        RuntimeLogStore invalid = new RuntimeLogStore(2); check(append(invalid), "invalid history early");
        List<RuntimeLogEvent> early = invalid.snapshot();
        for (List<RuntimeLogEvent> bad : Arrays.asList(Arrays.asList(event(7), null), Arrays.asList(event(7), event(7)), Arrays.asList(event(7), event(2)))) {
            rejects(() -> invalid.restore(bad), IllegalArgumentException.class);
            check(invalid.snapshot().equals(early) && invalid.droppedCount() == 0, "invalid full history rejected before mutation");
        }
        invalid.restore(Arrays.asList(event(2), event(5))); check(invalid.snapshot().get(1).sequence == 6, "invalid restore did not consume one-shot");
        try (SequenceScope scope = new SequenceScope()) {
            RuntimeLogPersistence p = new RuntimeLogPersistence(scope.root.toFile());
            List<RuntimeLogEvent> legal = Arrays.asList(event(2), event(5)); check(p.save(legal, null) == 2, "invalid file fixture");
            String text = new String(Files.readAllBytes(p.file().toPath()), StandardCharsets.UTF_8);
            for (String changed : new String[] {text.replace("\n5\t", "\n2\t"), text.replace("\n5\t", "\n1\t"), text.replace("\n5\t", "\n" + Long.MAX_VALUE + "\t")}) {
                Files.write(p.file().toPath(), changed.getBytes(StandardCharsets.UTF_8));
                RuntimeLogStore live = new RuntimeLogStore(4); check(append(live), "corrupt file early");
                ManualExecutor tasks = new ManualExecutor(); RuntimeLogWorker worker = scope.worker(live, p, tasks); tasks.runOne();
                check(worker.recoveryStatus() == RuntimeLogPersistence.Status.CORRUPT && live.size() == 1 && live.snapshot().get(0).sequence == 0,
                    "actual invalid V1 still CORRUPT; early retained"); worker.close();
            }
        }
    }

    private static void sequenceSinks() throws Exception {
        RuntimeLogStore store = new RuntimeLogStore(4); store.restore(Collections.singletonList(event(Long.MAX_VALUE - 2)));
        check(append(store), "sink reach max");
        AtomicInteger seen = new AtomicInteger();
        store.addSink((s, d) -> { throw new IllegalStateException("synthetic sink"); });
        store.addSink((s, d) -> seen.incrementAndGet()); check(append(store) && seen.get() == 1, "RuntimeException isolated at rollover");
        RuntimeLogStore fatalStore = new RuntimeLogStore(4); fatalStore.restore(Collections.singletonList(event(Long.MAX_VALUE - 2)));
        check(append(fatalStore), "fatal reach max");
        Error injected = new AssertionError("synthetic sequence sink"); RuntimeLogSink bad = (s, d) -> { throw injected; };
        fatalStore.addSink(bad); Error caught = null;
        try { append(fatalStore); } catch (Error failure) { caught = failure; }
        check(caught == injected && fatalStore.size() == 3 && fatalStore.snapshot().get(2).sequence == 2, "rollover commits then propagates exact sink Error");
        fatalStore.removeSink(bad); fatalStore.addSink((s, d) -> seen.incrementAndGet());
        check(append(fatalStore) && seen.get() == 2, "publisher recovers after rollover fatal");
        RuntimeLogStore reentrant = new RuntimeLogStore(4); reentrant.restore(Collections.singletonList(event(Long.MAX_VALUE - 2)));
        check(append(reentrant), "reentrant reach max");
        AtomicBoolean once = new AtomicBoolean(); List<List<RuntimeLogEvent>> delivered = new ArrayList<>();
        reentrant.addSink((s, d) -> { if (once.compareAndSet(false, true)) append(reentrant); });
        reentrant.addSink((s, d) -> delivered.add(s)); check(append(reentrant), "rollover with reentry");
        check(delivered.size() == 2 && delivered.get(0).size() == 3 && delivered.get(1).size() == 4 && delivered.get(1).get(3).sequence == 3,
            "one reentrant append drains newer completed snapshot only");
    }

    private static void sequencePublication() throws Exception {
        try (SequenceScope scope = new SequenceScope()) {
            RuntimeLogStore store = new RuntimeLogStore(8, new SequenceClock());
            store.restore(Collections.singletonList(event(Long.MAX_VALUE - 2)));
            CountDownLatch entered = scope.gate(), release = scope.gate();
            AtomicBoolean once = new AtomicBoolean(); AtomicReference<Throwable> failure = new AtomicReference<>();
            List<List<RuntimeLogEvent>> delivered = new CopyOnWriteArrayList<>();
            store.addSink((s, d) -> {
                if (once.compareAndSet(false, true)) {
                    entered.countDown();
                    try { await(release); } catch (Throwable problem) { failure.compareAndSet(null, problem); }
                }
            });
            store.addSink((s, d) -> delivered.add(s));
            Thread publisher = new Thread(() -> {
                try { check(append(store), "cross-limit initial append"); } catch (Throwable problem) { failure.compareAndSet(null, problem); }
            });
            scope.start(publisher); await(entered);
            List<Thread> producers = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Thread producer = new Thread(() -> {
                    try {
                        store.snapshot();
                        for (int j = 0; j < 11; j++) check(append(store), "cross-limit concurrent append");
                    } catch (Throwable problem) { failure.compareAndSet(null, problem); }
                });
                producers.add(producer); scope.start(producer);
            }
            for (Thread producer : producers) join(producer);
            check(failure.get() == null && store.size() == 8 && store.droppedCount() == 27, "cross-limit producers finish while sink blocked, exact eviction");
            List<RuntimeLogEvent> latest = store.snapshot(); ordered(latest);
            release.countDown(); join(publisher);
            check(failure.get() == null && delivered.size() == 2, "cross-limit publisher drains exactly old then latest");
            check(delivered.get(0).get(1).sequence == Long.MAX_VALUE - 1 && delivered.get(1).equals(latest), "numeric decrease is not stale publication");
            check(delivered.get(0).get(1).wallMillis > latest.get(latest.size() - 1).wallMillis, "payload clock proves newer causal snapshot after numeric rollover");
            check(append(store) && delivered.size() == 3, "publisher restarts after extreme coalescing");
        }
    }

    private static void sequenceCase(String name) throws Exception {
        switch (name) {
            case "seq-noearly": nearMaxProgress(false); break;
            case "seq-early": nearMaxProgress(true); break;
            case "seq-overflow": legalMaxRecovery(); break;
            case "seq-protection":
                sequenceRecoveryMatrix(); sequenceRejectsAndSnapshots(); sequenceSinks(); sequencePublication(); break;
            default: throw new IllegalArgumentException("unknown sequence case");
        }
        passed("sequence case " + name);
    }

    private static void export(Path base) throws Exception {
        RuntimeLogStore store = new RuntimeLogStore();
        store.append(RuntimeLogEventKind.INFERENCE_COMPLETED, RuntimeLogSource.IME, ID, "ok", 1234567890L);
        List<RuntimeLogEvent> ticket = store.snapshot();
        append(store);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long bytes = RuntimeLogCodec.writeSnapshot(ticket, out);
        byte[] expected = (RuntimeLogCodec.formatLine(ticket.get(0)) + "\n").getBytes(StandardCharsets.UTF_8);
        check(bytes == expected.length && Arrays.equals(out.toByteArray(), expected), "captured UTF8 snapshot, actual byte count not event count");
        ByteArrayOutputStream untouched = new ByteArrayOutputStream();
        rejects(() -> RuntimeLogCodec.writeSnapshot(Collections.nCopies(1001, event(0)), untouched), IOException.class);
        check(untouched.size() == 0, "oversized snapshot preflight before any write");
        rejects(() -> RuntimeLogCodec.writeSnapshot(ticket, new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("private provider message"); }
        }), IOException.class);
        rejects(() -> RuntimeLogCodec.writeSnapshot(ticket, new ByteArrayOutputStream() {
            @Override public void flush() throws IOException { throw new IOException("flush"); }
        }), IOException.class);
        AtomicBoolean closed = new AtomicBoolean(), success = new AtomicBoolean();
        try (OutputStream failingClose = new ByteArrayOutputStream() {
            @Override public void close() throws IOException { closed.set(true); throw new IOException("close"); }
        }) {
            RuntimeLogCodec.writeSnapshot(ticket, failingClose);
        } catch (IOException failure) { check(closed.get(), "failure from owner close"); }
        // Model the required owner pattern: success only after try-with-resources completes.
        try {
            try (OutputStream failingClose = new ByteArrayOutputStream() {
                @Override public void close() throws IOException { throw new IOException("close"); }
            }) { RuntimeLogCodec.writeSnapshot(ticket, failingClose); }
            success.set(true);
        } catch (IOException expectedFailure) { /* owner records fixed export failure */ }
        check(!success.get(), "owner never claims success before close");
        // Cap is now enforced by the codec itself; no callback path can lie.
        ByteArrayOutputStream capped = new ByteArrayOutputStream();
        rejects(() -> RuntimeLogCodec.writeSnapshot(Collections.singletonList(event(0)),
            new OutputStream() {
                @Override public void write(int b) throws IOException { capped.write(b); }
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    // Simulate a malicious callback trying to write past the cap.
                    super.write(b, off, (int) Math.min(len, RuntimeLogCodec.MAX_EXPORT_BYTES));
                    throw new IOException("malicious-oversize");
                }
            }), IOException.class);
        // Empty/null argument validation belongs to the codec, not the worker.
        rejects(() -> RuntimeLogCodec.writeSnapshot(null, new ByteArrayOutputStream()), IllegalArgumentException.class);
        rejects(() -> RuntimeLogCodec.writeSnapshot(ticket, null), IllegalArgumentException.class);
        rejects(() -> RuntimeLogCodec.writeSnapshot(Arrays.asList(event(0), null), new ByteArrayOutputStream()), IOException.class);
        passed("immutable picker-time UTF8 export through RuntimeLogCodec, exact byte preflight/cap, write/flush failures, close owned by caller");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) { sequenceCase(args[0]); return; }
        Path base = Files.createTempDirectory("runtime-log-core-");
        try {
            eventBoundary(); publication(); persistence(base); paths(base);
            workers(base); ownedWorker(base); workerFailures(base); export(base);
            sequenceCase("seq-noearly"); sequenceCase("seq-early");
            sequenceCase("seq-overflow"); sequenceCase("seq-protection");
            // Fatal drain/executor exits must release the single slot; grouped here
            // to keep the existing runtimeLogCore JUnit entry (no new JUnit count).
            RuntimeLogWorkerErrorTest.main(new String[0]);
            System.out.println("PASS RuntimeLogCoreTest: " + groups + " groups");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(base)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }
}
