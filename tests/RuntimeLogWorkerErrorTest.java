package org.llmasr.minimal.diagnostics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** Real worker fatal-fault recovery on the real filesystem, deterministic and
 * without production seams. Every injected fatal exits drain with the SAME
 * Error identity. Red assertions on the pre-fix worker:
 * - a fatal in load/restore/callback/save or an execute-thrown Error left the
 *   single slot permanently owned, so an append after it never submitted a new
 *   drain (old code: {@code scheduled} stayed true);
 * - a same-fatal crossing both the inner drain frame and the outer execute
 *   frame of a direct executor cleared a successor's slot (R1 interleave).
 *
 * Paths exercised (all reachable with real production objects):
 * load (File canonicalization), restore (store sink), failure callback
 * (save -1 + throwing sink), save (canonicalization on save), executor.execute
 * Error (not a rejection), direct-executor inner/outer fatal interleave with a
 * successor blocked in a real save, close during blocked IO, executor
 * rejection recovery, plus the R2 additions:
 * - close during a blocked real save that then fails with the same fatal
 *   (close never waits, closed rejects, no auto retry, never a queue-full
 *   label, worker confirmed exited);
 * - reject-before-run releasing its OWN admission before a bounded real
 *   failure-sink pause, a successor admitted into that window and blocked in a
 *   real save, an old reject frame that must not clear the successor, and a
 *   third schedule that only dirties;
 * - scoped bounded teardown of every started thread: an unconfirmed worker
 *   must refuse deletion of its in-use directory (R2-b), and only an
 *   independent release + confirmed join may delete it.
 * Ordinary RuntimeException isolation stays covered by RuntimeLogCoreTest;
 * this class only adds the fatal/owner-slot contract and safe test cleanup. */
public final class RuntimeLogWorkerErrorTest {
    /** Bounded teardown budget shared by every scoped group. */
    private static final long TEARDOWN_BUDGET_MS = 5000;

    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static boolean append(RuntimeLogStore store) {
        return store.append(RuntimeLogEventKind.APP_READY, RuntimeLogSource.SHARED, null, "ok");
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timeout");
    }
    /** Unbounded gate wait used ONLY inside the controlled blocks of started
     * threads: the gate is released deterministically by the test (or the group
     * finally), so a timeout there must never masquerade as an expected exit. */
    private static void awaitGate(CountDownLatch latch) throws InterruptedException {
        latch.await();
    }
    private static void join(Thread thread) throws InterruptedException {
        thread.join(5000);
        check(!thread.isAlive(), "thread timeout");
    }
    private static void waitBlocked(Thread thread) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < until) Thread.sleep(1);
        check(thread.getState() == Thread.State.BLOCKED, "worker not blocked on the persistence monitor");
    }
    private static void deleteRecursive(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }
    /** Drain runs on the test thread via runOne(); no real background race is
     * fabricated. The store/persistence/failure-callback are all production. */
    private static final class ManualExecutor implements Executor {
        final Deque<Runnable> queue = new ArrayDeque<>();
        int submissions;
        @Override public synchronized void execute(Runnable task) {
            submissions++;
            queue.add(task);
        }
        synchronized int pending() { return queue.size(); }
        void runOne() {
            Runnable task;
            synchronized (this) { task = queue.remove(); }
            task.run();
        }
    }

    /** Per invocation, never cleared by a later main invocation. Only a fresh
     * actual-exit confirmation can remove a directory from this refusal list. */
    private static final class RunCleanup {
        private final List<Path> unconfirmed = new ArrayList<>();
        void delete(Path root, Throwable original) throws IOException {
            try {
                for (Path dir : unconfirmed) {
                    if (dir.startsWith(root) || root.startsWith(dir)) {
                        throw new AssertionError("cleanup refused; worker exit unconfirmed; directory preserved: " + dir);
                    }
                }
                deleteRecursive(root);
            } catch (IOException | RuntimeException | Error failure) {
                if (original == null) throw failure;
                original.addSuppressed(failure);
            }
        }
    }

    /** All starts and first observations belong inside the caller's try/finally.
     * Register every gate before starting; a release request is not an exit. */
    private static final class ThreadScope {
        private final RunCleanup cleanup;
        private final Path dir;
        private final List<Thread> threads = new ArrayList<>();
        private final List<CountDownLatch> gates = new ArrayList<>();
        ThreadScope(RunCleanup cleanup, Path dir) { this.cleanup = cleanup; this.dir = dir; }
        Thread start(Runnable runnable) {
            Thread thread = new Thread(runnable);
            threads.add(thread);
            thread.start();
            return thread;
        }
        void addGate(CountDownLatch gate) { gates.add(gate); }
        void releaseAll() { for (CountDownLatch gate : gates) gate.countDown(); }
        /** One total nanosecond budget. Positive fractions use join(0,nanos),
         * NEVER join(0). Check actual state even after deadline or interruption. */
        boolean awaitAll(long budgetNanos) {
            long deadline = System.nanoTime() + budgetNanos;
            boolean interrupted = Thread.currentThread().isInterrupted();
            for (Thread thread : threads) {
                while (thread.isAlive() && !interrupted) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    try {
                        thread.join(remaining / 1000000, (int) (remaining % 1000000));
                    } catch (InterruptedException stop) {
                        Thread.currentThread().interrupt();
                        interrupted = true;
                    }
                }
            }
            for (Thread thread : threads) if (thread.isAlive()) return false;
            return true;
        }
    }

    private static void finishScope(ThreadScope scope, Throwable original) {
        finishScope(scope, original, TimeUnit.MILLISECONDS.toNanos(TEARDOWN_BUDGET_MS));
    }
    /** The same cleanup decision serves normal groups and the held-worker
     * negative. Cleanup failures supplement, never replace, the primary error. */
    private static void finishScope(ThreadScope scope, Throwable original, long budgetNanos) {
        scope.releaseAll();
        if (scope.awaitAll(budgetNanos)) {
            scope.cleanup.unconfirmed.remove(scope.dir);
        } else {
            if (!scope.cleanup.unconfirmed.contains(scope.dir)) scope.cleanup.unconfirmed.add(scope.dir);
            AssertionError failure = new AssertionError("worker not confirmed exited within the bounded teardown budget; directory preserved: " + scope.dir);
            if (original == null) throw failure;
            original.addSuppressed(failure);
        }
    }

    private static void loadPathFatal(Path base) throws Exception {
        Path dir = Files.createDirectory(base.resolve("load-dir"));
        Files.createDirectory(dir.resolve("files"));
        final AssertionError injected = new AssertionError("load-path");
        final AtomicBoolean fault = new AtomicBoolean(true);
        File filesDir = new File(dir.toFile(), "files") {
            @Override public File getCanonicalFile() throws IOException {
                if (fault.get()) throw injected;
                return super.getCanonicalFile();
            }
        };
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(filesDir);
        RuntimeLogStore store = new RuntimeLogStore();
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, executor);
        check(executor.pending() == 1, "constructor schedules recovery");
        Error seen = null;
        try { executor.runOne(); } catch (Error e) { seen = e; }
        check(seen == injected, "load-path fatal identity preserved");
        check(append(store), "append after load-path fatal");
        check(executor.pending() == 1,
            "append after drain fatal must schedule recovery, pending=" + executor.pending());
        fault.set(false);
        executor.runOne();
        check(persistence.load().equals(store.snapshot()), "persisted after load-path fatal recovery");
        check(worker.successfulSaves() >= 1, "successful save after recovery");
        worker.close();
        check(!worker.schedulePersist(), "closed rejects new work after fatal recovery");
    }

    private static void restorePathFatal(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("restore-dir"));
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(root.toFile());
        RuntimeLogStore store = new RuntimeLogStore();
        final AssertionError injected = new AssertionError("restore-path");
        RuntimeLogSink bad = (snapshot, dropped) -> { throw injected; };
        store.addSink(bad);
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, executor);
        check(executor.pending() == 1, "constructor schedules recovery");
        Error seen = null;
        try { executor.runOne(); } catch (Error e) { seen = e; }
        check(seen == injected, "restore-path fatal identity preserved");
        store.removeSink(bad);
        check(append(store), "append after restore-path fatal");
        check(executor.pending() == 1,
            "append after drain fatal must schedule recovery, pending=" + executor.pending());
        executor.runOne();
        check(persistence.load().equals(store.snapshot()), "persisted after restore-path fatal recovery");
        check(worker.successfulSaves() >= 1, "successful save after recovery");
        worker.close();
    }

    private static void failureCallbackFatal(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("callback-dir"));
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(root.toFile());
        RuntimeLogStore store = new RuntimeLogStore();
        final AssertionError injected = new AssertionError("failure-sink");
        final AtomicBoolean armed = new AtomicBoolean(true);
        AtomicInteger callbacks = new AtomicInteger();
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, reason -> {
            callbacks.incrementAndGet();
            if (armed.get()) throw injected;
        }, executor);
        check(executor.pending() == 1, "constructor schedules recovery");
        executor.runOne();
        check(worker.successfulSaves() == 1 && worker.lastFailure() == null, "clean first drain");
        // A managed .part directory makes the NEXT save return -1 (fixed
        // unsafe-path label) while recovery is already complete.
        Files.createDirectory(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part"));
        check(append(store), "append before failure-callback fatal");
        check(executor.pending() == 1, "append schedules the failing drain");
        Error seen = null;
        try { executor.runOne(); } catch (Error e) { seen = e; }
        check(seen == injected, "failure-callback fatal identity preserved");
        check(worker.failedSaves() == 1 && "unsafe-path".equals(worker.lastFailure()),
            "fatal recorded with the fixed label before the callback");
        armed.set(false);
        Files.delete(root.resolve(RuntimeLogPersistence.FILE_NAME + ".part"));
        check(append(store), "append after failure-callback fatal");
        check(executor.pending() == 1,
            "append after drain fatal must schedule recovery, pending=" + executor.pending());
        executor.runOne();
        check(persistence.load().equals(store.snapshot()), "persisted after failure-callback fatal recovery");
        check(worker.successfulSaves() == 2 && worker.lastFailure() == null,
            "failure state cleared after a successful replacement save");
        check(callbacks.get() == 1, "single failure callback episode");
        worker.close();
    }

    private static void savePathFatal(Path base) throws Exception {
        Path dir = Files.createDirectory(base.resolve("save-dir"));
        Files.createDirectory(dir.resolve("files"));
        final AssertionError injected = new AssertionError("save-path");
        final AtomicInteger canonicalCalls = new AtomicInteger();
        File filesDir = new File(dir.toFile(), "files") {
            @Override public File getCanonicalFile() throws IOException {
                int call = canonicalCalls.incrementAndGet();
                if (call == 2) throw injected; // load succeeded; save re-checks the parent
                return super.getCanonicalFile();
            }
        };
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(filesDir);
        RuntimeLogStore store = new RuntimeLogStore();
        append(store);
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, executor);
        check(executor.pending() == 1, "constructor schedules recovery");
        Error seen = null;
        try { executor.runOne(); } catch (Error e) { seen = e; }
        check(seen == injected, "save-path fatal identity preserved");
        check(!Files.exists(persistence.file().toPath()), "no partial write on fatal save");
        check(append(store), "append after save-path fatal");
        check(executor.pending() == 1,
            "append after drain fatal must schedule recovery, pending=" + executor.pending());
        executor.runOne();
        check(persistence.load().equals(store.snapshot()), "persisted after save-path fatal recovery");
        check(worker.successfulSaves() == 1, "successful save after recovery");
        worker.close();
    }

    private static void executeFatal(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("execute-dir"));
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(root.toFile());
        RuntimeLogStore store = new RuntimeLogStore();
        final AssertionError injected = new AssertionError("execute-path");
        final AtomicBoolean armed = new AtomicBoolean(false);
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, task -> {
            if (armed.get()) throw injected;
            executor.execute(task);
        });
        executor.runOne(); // constructor recovery drained
        armed.set(true);
        Error seen = null;
        try { append(store); } catch (Error e) { seen = e; }
        check(seen == injected, "execute fatal identity propagated to the publisher");
        check(worker.droppedSchedules() == 0 && worker.lastFailure() == null,
            "fatal is not a queue-full rejection and is not counted as dropped");
        armed.set(false);
        check(append(store), "append after execute fatal");
        check(executor.pending() == 1,
            "append after execute fatal must schedule recovery, pending=" + executor.pending());
        executor.runOne();
        check(persistence.load().equals(store.snapshot()), "persisted after execute fatal recovery");
        check(worker.successfulSaves() == 2, "constructor drain and recovery drain both saved");
        worker.close();
    }

    /** R1 red on a354fb: one fatal crosses BOTH the inner drain frame and the
     * outer execute frame of a supported direct executor. The inner frame must
     * release only its own token; while that stale frame is paused, a successor
     * takes the slot and blocks in a real save, and the stale frame's outer
     * release must not clear it. A third schedule may only dirty. Every started
     * thread (T1/T2/T3) is scope-tracked and confirmed exited on teardown. */
    private static void staleOuterReleaseFatal(Path base, RunCleanup cleanup) throws Exception {
        Path dir = Files.createDirectory(base.resolve("interleave-dir"));
        Files.createDirectory(dir.resolve("files"));
        final AssertionError injected = new AssertionError("interleave");
        final AtomicBoolean fault = new AtomicBoolean(false);
        File filesDir = new File(dir.toFile(), "files") {
            @Override public File getCanonicalFile() throws IOException {
                if (fault.get()) throw injected;
                return super.getCanonicalFile();
            }
        };
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(filesDir);
        RuntimeLogStore store = new RuntimeLogStore();
        final AtomicInteger submissions = new AtomicInteger();
        final CountDownLatch innerReturned = new CountDownLatch(1);
        final CountDownLatch windowOpen = new CountDownLatch(1);
        final AtomicReference<Throwable> t1Problem = new AtomicReference<>();
        final AtomicReference<Throwable> t2Problem = new AtomicReference<>();
        final AtomicReference<Throwable> t3Problem = new AtomicReference<>();
        final AtomicBoolean t3Returned = new AtomicBoolean();
        // Supported direct executor: runs the task inline; only a bounded latch
        // pauses the fatal between the inner drain frame and the outer frame.
        Executor interleave = task -> {
            try {
                task.run();
            } catch (Error fatal) {
                innerReturned.countDown();
                try { awaitGate(windowOpen); } catch (InterruptedException stop) { throw new AssertionError(stop); }
                throw fatal;
            }
        };
        Executor counting = task -> { submissions.incrementAndGet(); interleave.execute(task); };
        ThreadScope scope = new ThreadScope(cleanup, dir);
        scope.addGate(windowOpen);
        Throwable original = null;
        try {
            RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, counting);
            check(submissions.get() == 1 && worker.successfulSaves() == 1, "constructor admission and save");
            fault.set(true);
            Thread t1 = scope.start(() -> {
                try { check(worker.schedulePersist(), "T1 admitted"); }
                catch (Throwable problem) { t1Problem.set(problem); }
            });
            await(innerReturned); // inner drain released its OWN token; fatal paused
            fault.set(false);
            Thread t2 = null, t3 = null;
            synchronized (persistence) {
                t2 = scope.start(() -> {
                    try { t2Problem.set(worker.schedulePersist() ? null : new AssertionError("T2 not admitted")); }
                    catch (Throwable problem) { t2Problem.set(problem); }
                });
                waitBlocked(t2); // T2's inline drain is inside the real save
                check(submissions.get() == 3, "successor drain admitted while the stale frame paused, submissions=" + submissions.get());
                windowOpen.countDown(); // let the fatal cross the outer execute frame
                join(t1);
                check(t1Problem.get() == injected, "same fatal identity reached the outer execute frame");
                t3 = scope.start(() -> {
                    try { t3Returned.set(worker.schedulePersist()); }
                    catch (Throwable problem) { t3Problem.set(problem); }
                });
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (submissions.get() < 4 && !t3Returned.get() && System.nanoTime() < until) Thread.sleep(1);
                check(submissions.get() == 3,
                    "third schedule must not admit a new drain while the successor owns the slot, submissions=" + submissions.get());
                check(t3Returned.get() && t3Problem.get() == null, "third schedule only marks dirty and returns");
            }
            join(t2);
            check(t2Problem.get() == null, "successor drain completed: " + t2Problem.get());
            check(t3Returned.get() && t3Problem.get() == null, "third schedule returned cleanly");
            check(worker.successfulSaves() >= 3, "successor drains committed their saves");
            append(store);
            check(submissions.get() == 4, "later append admitted a fresh drain, submissions=" + submissions.get());
            check(persistence.load().equals(store.snapshot()), "final persisted store after interleaving");
            worker.close();
            check(!worker.schedulePersist(), "closed rejects after interleaving");
        } catch (Throwable problem) {
            original = problem;
            throw problem;
        } finally {
            finishScope(scope, original);
        }
    }

    /** Close while a real save is blocked: close never waits on IO, closed rejects
     * new admission, the blocked drain still owns the slot, and the accepted work
     * completes and persists after IO is released. The io thread is scoped and
     * confirmed exited on teardown. */
    private static void closeDuringBlockedIo(Path base, RunCleanup cleanup) throws Exception {
        Path root = Files.createDirectory(base.resolve("close-block"));
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(root.toFile());
        RuntimeLogStore store = new RuntimeLogStore();
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, executor);
        executor.runOne(); // constructor drain: recovery + empty save
        check(append(store), "append before blocked close");
        List<RuntimeLogEvent> accepted = store.snapshot();
        check(executor.pending() == 1, "append scheduled the drain");
        AtomicReference<Throwable> ioProblem = new AtomicReference<>();
        ThreadScope scope = new ThreadScope(cleanup, root);
        Throwable original = null;
        try {
            Thread io;
            synchronized (persistence) {
                io = scope.start(() -> {
                    try { executor.runOne(); } catch (Throwable problem) { ioProblem.set(problem); }
                });
                waitBlocked(io); // drain is inside the real save; the slot is still owned
                long start = System.nanoTime();
                worker.close();
                check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "close never waits on blocked IO");
                check(!worker.schedulePersist(), "closed rejects new admission");
                check(executor.pending() == 0, "no new drain admitted during the blocked save");
                check(append(store), "append still accepted by the store");
                check(executor.pending() == 0, "removed sink does not schedule after close");
            }
            join(io);
            check(ioProblem.get() == null, "accepted drain completed: " + ioProblem.get());
            check(persistence.load().equals(accepted), "accepted work persisted after close");
        } catch (Throwable problem) {
            original = problem;
            throw problem;
        } finally {
            finishScope(scope, original);
        }
    }

    /** Reject-before-run releases only its own admission: the next legal append
     * re-admits and persists; rejection keeps the fixed label and is not counted
     * as a fatal. */
    private static void rejectionRecovery(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("reject-dir"));
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(root.toFile());
        RuntimeLogStore store = new RuntimeLogStore();
        final AtomicBoolean rejecting = new AtomicBoolean(true);
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, task -> {
            if (rejecting.get()) throw new RejectedExecutionException("reject");
            executor.execute(task);
        });
        check(!worker.schedulePersist() && worker.droppedSchedules() == 2
            && "queue-full".equals(worker.lastFailure()), "rejection isolated with the fixed label");
        rejecting.set(false);
        check(append(store), "append after rejection");
        check(executor.pending() == 1, "append after rejection admitted a fresh drain");
        executor.runOne();
        check(persistence.load().equals(store.snapshot()) && worker.lastFailure() == null,
            "recovery after rejection persisted");
        worker.close();
    }

    /** R2-a close/reject handoff: a real save is blocked (latch inside the real
     * canonicalization path); close never waits on that IO, closed rejects new
     * admission, and no new drain is admitted while the save is blocked. When
     * the block is released the save throws the SAME fatal; external asserts
     * prove the original identity, that the worker actually exited, that there
     * is no auto retry, and that the fatal is never counted as a queue-full
     * rejection. Owned shutdown is not a new observation surface, and no
     * production seam is added. */
    private static void closeDuringBlockedSaveFatal(Path base, RunCleanup cleanup) throws Exception {
        Path dir = Files.createDirectory(base.resolve("close-save-fatal"));
        Files.createDirectory(dir.resolve("files"));
        final AssertionError injected = new AssertionError("close-save-fatal");
        final AtomicBoolean blockNext = new AtomicBoolean();
        final CountDownLatch saveEntered = new CountDownLatch(1);
        final CountDownLatch saveOpen = new CountDownLatch(1);
        final AtomicReference<Throwable> ioProblem = new AtomicReference<>();
        File filesDir = new File(dir.toFile(), "files") {
            @Override public File getCanonicalFile() throws IOException {
                if (blockNext.compareAndSet(true, false)) {
                    saveEntered.countDown();
                    try { awaitGate(saveOpen); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
                    throw injected;
                }
                return super.getCanonicalFile();
            }
        };
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(filesDir);
        RuntimeLogStore store = new RuntimeLogStore();
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, executor);
        check(executor.pending() == 1, "constructor schedules recovery");
        executor.runOne(); // constructor drain: recovery + empty save
        List<RuntimeLogEvent> beforeFatal = persistence.load();
        check(append(store), "append before blocked fatal close");
        check(executor.pending() == 1, "append scheduled the failing drain");
        blockNext.set(true);
        ThreadScope scope = new ThreadScope(cleanup, dir);
        scope.addGate(saveOpen);
        Throwable original = null;
        try {
            Thread io = scope.start(() -> {
                try { executor.runOne(); } catch (Throwable problem) { ioProblem.set(problem); }
            });
            await(saveEntered); // drain is inside the real save at the real path
            long start = System.nanoTime();
            worker.close();
            check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "close never waits on blocked IO");
            check(!worker.schedulePersist(), "closed rejects new admission");
            check(executor.pending() == 0, "no new drain admitted while the save is blocked");
            check(worker.droppedSchedules() == 0 && worker.lastFailure() == null,
                "blocked-save fatal is not a queue-full rejection before it is even raised");
            saveOpen.countDown(); // let the fatal cross the real save
            join(io);
            check(ioProblem.get() == injected, "same fatal identity preserved through the blocked close: " + ioProblem.get());
            check(!worker.schedulePersist(), "closed rejects after the fatal close");
            check(executor.pending() == 0, "no auto retry after the fatal close");
            check(worker.droppedSchedules() == 0, "fatal is never counted as dropped");
            check(worker.lastFailure() == null, "fatal close never claims a queue-full label");
            check(!Files.exists(persistence.file().toPath().resolveSibling(
                    RuntimeLogPersistence.FILE_NAME + ".part")), "no staging part on the fatal save");
            check(persistence.load().equals(beforeFatal), "failed fatal save left the persisted file unchanged");
        } catch (Throwable problem) {
            original = problem;
            throw problem;
        } finally {
            finishScope(scope, original);
        }
    }

    /** R2-a rejection handoff: reject-before-run releases ONLY its own
     * admission, then pauses in the real failure sink; during that window
     * another thread takes the successor slot and blocks in a real save. The
     * old reject frame must return WITHOUT clearing the successor, a third
     * schedule may only dirty, the successor actually finishes and persists,
     * and the fixed queue-full label plus dropped count are verified. Uses only
     * a supported direct executor and the existing production failure sink; no
     * hostile executor framework is fabricated. */
    private static void rejectWindowSuccessor(Path base, RunCleanup cleanup) throws Exception {
        Path dir = Files.createDirectory(base.resolve("reject-window"));
        Files.createDirectory(dir.resolve("files"));
        final AtomicBoolean blockNext = new AtomicBoolean();
        final CountDownLatch saveEntered = new CountDownLatch(1);
        final CountDownLatch saveOpen = new CountDownLatch(1);
        final CountDownLatch rejectEntered = new CountDownLatch(1);
        final CountDownLatch rejectOpen = new CountDownLatch(1);
        File filesDir = new File(dir.toFile(), "files") {
            @Override public File getCanonicalFile() throws IOException {
                if (blockNext.compareAndSet(true, false)) {
                    saveEntered.countDown();
                    try { awaitGate(saveOpen); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
                }
                return super.getCanonicalFile();
            }
        };
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(filesDir);
        RuntimeLogStore store = new RuntimeLogStore();
        final AtomicInteger submissions = new AtomicInteger();
        final AtomicBoolean rejecting = new AtomicBoolean();
        final AtomicInteger callbacks = new AtomicInteger();
        final AtomicReference<Throwable> t1Problem = new AtomicReference<>();
        final AtomicReference<Throwable> t2Problem = new AtomicReference<>();
        final AtomicReference<Throwable> t3Problem = new AtomicReference<>();
        final AtomicBoolean t1Result = new AtomicBoolean();
        final AtomicBoolean t3Returned = new AtomicBoolean();
        // Supported direct executor: counts every admission, rejects exactly one
        // admission before it runs, otherwise runs the drain inline on the caller.
        Executor counting = task -> {
            submissions.incrementAndGet();
            if (rejecting.compareAndSet(true, false)) throw new RejectedExecutionException("reject");
            task.run();
        };
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, reason -> {
            callbacks.incrementAndGet();
            if (reason.equals("queue-full")) {
                rejectEntered.countDown();
                try { awaitGate(rejectOpen); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
            }
        }, counting);
        check(submissions.get() == 1 && worker.successfulSaves() == 1, "constructor admission and save");
        check(append(store), "append before rejection");
        check(submissions.get() == 2 && worker.successfulSaves() == 2, "append drain admitted and saved");
        ThreadScope scope = new ThreadScope(cleanup, dir);
        scope.addGate(rejectOpen);
        scope.addGate(saveOpen);
        Throwable original = null;
        try {
            rejecting.set(true);
            Thread t1 = scope.start(() -> {
                try { t1Result.set(worker.schedulePersist()); }
                catch (Throwable problem) { t1Problem.set(problem); }
            });
            await(rejectEntered); // rejection released its OWN admission, then paused in the real failure sink
            check(submissions.get() == 3 && worker.droppedSchedules() == 1
                && "queue-full".equals(worker.lastFailure()) && callbacks.get() == 1,
                "rejection released its own admission with the fixed label before the sink pause");
            blockNext.set(true);
            Thread t2 = scope.start(() -> {
                try { t2Problem.set(worker.schedulePersist() ? null : new AssertionError("T2 not admitted")); }
                catch (Throwable problem) { t2Problem.set(problem); }
            });
            await(saveEntered); // successor owns the slot and is inside a real save
            check(submissions.get() == 4 && worker.successfulSaves() == 2,
                "successor admitted into the rejection window and blocked in a real save, submissions=" + submissions.get());
            rejectOpen.countDown(); // let the old reject frame return
            join(t1);
            check(!t1Result.get() && t1Problem.get() == null,
                "reject frame returned false without throwing: " + t1Problem.get());
            Thread t3 = scope.start(() -> {
                try { t3Returned.set(worker.schedulePersist()); }
                catch (Throwable problem) { t3Problem.set(problem); }
            });
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!t3Returned.get() && System.nanoTime() < until) Thread.sleep(1);
            check(submissions.get() == 4,
                "third schedule only dirties while the successor owns the slot, submissions=" + submissions.get());
            check(t3Returned.get() && t3Problem.get() == null, "third schedule returned cleanly");
            saveOpen.countDown(); // let the successor's real save finish
            join(t2);
            check(t2Problem.get() == null, "successor drain completed: " + t2Problem.get());
            check(worker.droppedSchedules() == 1 && worker.lastFailure() == null,
                "fixed queue-full label cleared by the successor's successful save; dropped count kept");
            check(persistence.load().equals(store.snapshot()), "successor persisted the full store");
            worker.close();
            check(!worker.schedulePersist(), "closed rejects after the rejection window");
        } catch (Throwable problem) {
            original = problem;
            throw problem;
        } finally {
            finishScope(scope, original);
        }
    }

    /** Real save held past the SAME finishScope and deletion guard used by
     * normal groups/main. The monitor deliberately outlives releaseAll: opening
     * test gates cannot promise that real IO has already finished. */
    private static void teardownHold(Path base, RunCleanup cleanup, boolean interruptFirstObservation) throws Exception {
        Path dir = Files.createDirectory(base.resolve("teardown-hold"));
        Files.createDirectory(dir.resolve("files"));
        CountDownLatch saveEntered = new CountDownLatch(1);
        CountDownLatch holdGate = new CountDownLatch(1);
        Object exitMonitor = new Object();
        AtomicInteger canonicalCalls = new AtomicInteger();
        AtomicReference<Throwable> ioProblem = new AtomicReference<>();
        File filesDir = new File(dir.toFile(), "files") {
            @Override public File getCanonicalFile() throws IOException {
                if (canonicalCalls.incrementAndGet() == 2) {
                    saveEntered.countDown();
                    try { awaitGate(holdGate); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
                    synchronized (exitMonitor) { /* independent owner must release this monitor */ }
                }
                return super.getCanonicalFile();
            }
        };
        RuntimeLogPersistence persistence = new RuntimeLogPersistence(filesDir);
        RuntimeLogStore store = new RuntimeLogStore();
        ManualExecutor executor = new ManualExecutor();
        RuntimeLogWorker worker = new RuntimeLogWorker(store, persistence, null, executor);
        ThreadScope scope = new ThreadScope(cleanup, dir);
        scope.addGate(holdGate);
        Throwable original = null;
        try {
            synchronized (exitMonitor) {
                Thread io = scope.start(() -> {
                    try { executor.runOne(); } catch (Throwable problem) { ioProblem.set(problem); }
                });
                if (interruptFirstObservation) Thread.currentThread().interrupt();
                await(saveEntered);
                long start = System.nanoTime();
                AssertionError refusal = null;
                try { finishScope(scope, null, 999999); }
                catch (AssertionError expected) { refusal = expected; }
                check(refusal != null && refusal.getMessage().contains("worker not confirmed exited"),
                    "shared finishScope must report the actual held worker");
                check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1),
                    "sub-millisecond remaining budget must not become infinite join(0)");
                check(io.isAlive(), "release request is not actual exit");
                AssertionError deletion = null;
                try { cleanup.delete(dir, null); }
                catch (AssertionError expected) { deletion = expected; }
                check(deletion != null && deletion.getMessage().contains("cleanup refused") && Files.exists(dir),
                    "same main deletion guard must refuse the in-use tree");
                System.out.println("SHARED_CLEANUP_REFUSED tiny-budget dir=" + dir);

                // First observation of this observer is interrupted. Its actual
                // Throwable crosses BOTH shared finally decisions back to host.
                InterruptedException observed = null;
                Throwable returned = null;
                try {
                    Thread.currentThread().interrupt();
                    try { await(saveEntered); }
                    catch (InterruptedException stop) { observed = stop; Thread.currentThread().interrupt(); throw stop; }
                    finally { finishScope(scope, observed, 999999); }
                } catch (Throwable failure) {
                    // main uses precisely this finally guard too. Catch outside
                    // it so the host checks the actual propagated object.
                    try {
                        try { throw failure; }
                        finally { cleanup.delete(dir, failure); }
                    } catch (Throwable fromCleanup) { returned = fromCleanup; }
                }
                check(observed != null && returned == observed, "original observation Throwable identity reached host");
                check(returned.getSuppressed().length == 2, "primary observation error has teardown and deletion suppressed");
                check(returned.getSuppressed()[0].getMessage().contains("worker not confirmed exited")
                    && returned.getSuppressed()[1].getMessage().contains("cleanup refused"), "real cleanup diagnostics carried to host");
                check(Thread.currentThread().isInterrupted() && Files.exists(dir), "interrupt preserved and host sees retained tree");
                System.out.println("HOST_CAUGHT_PRIMARY_WITH_SUPPRESSED noexit dir=" + dir);
            }
        } catch (Throwable problem) {
            original = problem;
            if (problem instanceof InterruptedException) Thread.currentThread().interrupt();
            throw problem;
        } finally {
            // Independent monitor release has happened before this finally.
            // Temporarily clear interruption only to allow actual-exit proof;
            // restore the caller's flag on every path. No unconditional delete.
            boolean interrupted = Thread.interrupted();
            try {
                finishScope(scope, original);
                cleanup.delete(dir, original);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
        // This test injected the interrupt itself; do not infect later selectors.
        check(Thread.interrupted(), "host resumed with the observation interrupt intact");
        check(scope.awaitAll(0), "already exited worker is confirmed even with zero remaining budget");
        check(ioProblem.get() == null, "held save actually completed: " + ioProblem.get());
        check(!Files.exists(dir), "only independent release plus actual exit permits deletion");
        worker.close();
    }

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("runtime-log-worker-error-");
        RunCleanup cleanup = new RunCleanup();
        Throwable original = null;
        try {
            boolean all = args.length == 0;
            if (all || "load".equals(args[0])) loadPathFatal(base);
            if (all || "restore".equals(args[0])) restorePathFatal(base);
            if (all || "callback".equals(args[0])) failureCallbackFatal(base);
            if (all || "save".equals(args[0])) savePathFatal(base);
            if (all || "execute".equals(args[0])) executeFatal(base);
            if (all || "interleave".equals(args[0])) staleOuterReleaseFatal(base, cleanup);
            if (all || "close-block".equals(args[0])) closeDuringBlockedIo(base, cleanup);
            if (all || "reject".equals(args[0])) rejectionRecovery(base);
            if (all || "close-fatal".equals(args[0])) closeDuringBlockedSaveFatal(base, cleanup);
            if (all || "reject-window".equals(args[0])) rejectWindowSuccessor(base, cleanup);
            if (all || "teardown-hold".equals(args[0]) || "teardown-interrupt".equals(args[0])) {
                teardownHold(base, cleanup, !all && "teardown-interrupt".equals(args[0]));
            }
            System.out.println("PASS RuntimeLogWorkerErrorTest: " + checks + " checks ("
                + (all ? "load/restore/callback/save/execute/interleave/close-block/reject/"
                       + "close-fatal/reject-window/teardown-hold"
                       : args[0]) + ")");
        } catch (Throwable problem) {
            original = problem;
            throw problem;
        } finally {
            cleanup.delete(base, original);
        }
    }
}
