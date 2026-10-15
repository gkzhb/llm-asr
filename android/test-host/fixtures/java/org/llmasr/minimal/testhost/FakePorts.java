package org.llmasr.minimal.testhost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.llmasr.minimal.asr.InferenceAdapter;
import org.llmasr.minimal.asr.NativeTranscription;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.ime.ImeController;
import org.llmasr.minimal.task.RequestContext;
import org.llmasr.minimal.task.TaskKind;

/** Synthetic, memory-only ports. No files, WAV encoding, AudioRecord or native loads. */
public final class FakePorts {
    private FakePorts() {}
    public static final String TEXT = "fixture sentence";

    public static void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout: " + label);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted: " + label, e);
        }
    }

    public static final class Engine {
        public final List<String> order = new ArrayList<>();
        public int loads, nativeCalls;
        public boolean failReadiness;
        public byte[] payload = "1.25 2.5 3\nlanguage English<asr_text>fixture sentence"
            .getBytes(StandardCharsets.UTF_8);
        public final InferenceAdapter.Readiness readiness = () -> {
            order.add("ready");
            if (failReadiness) throw new IOException("synthetic readiness rejection");
        };
        public final InferenceAdapter.Library library = () -> { loads++; order.add("load"); };
        public final NativeTranscription nativePort = (config, wav, language, cache, listener) -> {
            nativeCalls++; order.add("native");
            FixtureChecks.require("synthetic-config".equals(config) && "synthetic-input".equals(wav)
                && "English".equals(language) && "synthetic-cache".equals(cache), "engine arguments");
            for (int phase = 1; phase <= 4; phase++) listener.onPhase(phase);
            return payload;
        };
    }

    public static final class Queue implements Executor {
        private Runnable next;
        @Override public void execute(Runnable work) {
            if (next != null) throw new AssertionError("overlapping queued task");
            next = work;
        }
        public void drain() {
            if (next == null) throw new AssertionError("missing queued task");
            Runnable work = next; next = null; work.run();
        }
    }

    /** Captures failures outside production's presentation-error handling too. */
    public static final class Worker implements Executor, AutoCloseable {
        private Thread thread;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        @Override public void execute(Runnable work) {
            if (thread != null) throw new AssertionError("worker reused");
            thread = new Thread(() -> {
                try { work.run(); } catch (Throwable t) { failure.set(t); }
            }, "p1b-fixture-worker");
            thread.start();
        }
        @Override public void close() throws InterruptedException {
            if (thread != null) {
                thread.join(6000);
                if (thread.isAlive()) {
                    thread.interrupt();
                    thread.join(1000);
                    throw new AssertionError("worker did not finish");
                }
            }
            Throwable problem = failure.get();
            if (problem != null) throw new AssertionError("worker failure", problem);
        }
    }

    public static final class Backend implements ImeController.Backend {
        public final byte[] samples = {1, 2, 3};
        public int captures, inferences, cleanups;
        public RecordingControl control;
        public RequestContext request;
        public Runnable duringInference = () -> {};
        public boolean blockCleanup;
        public final CountDownLatch cleanupEntered = new CountDownLatch(1);
        public final CountDownLatch cleanupRelease = new CountDownLatch(1);
        public volatile boolean cleanupCompleted;
        @Override public byte[] capture(RecordingControl c, ImeController.Progress progress) {
            captures++; control = c;
            try {
                FixtureChecks.require(c.start(() -> {}), "capture admitted by real gate");
                progress.update(0.01);
                return samples;
            } finally { c.captureReleased(); }
        }
        @Override public String transcribe(RequestContext r, byte[] data, String language) {
            inferences++; request = r;
            FixtureChecks.require(r.kind == TaskKind.INFERENCE && data == samples
                && "English".equals(language), "backend arguments");
            FixtureChecks.require(control.released() && control.committed(), "release before inference");
            duringInference.run();
            return TEXT;
        }
        @Override public void cleanup(RequestContext r) {
            cleanups++;
            FixtureChecks.require(request == null || request == r, "same request cleanup");
            cleanupEntered.countDown();
            if (blockCleanup) await(cleanupRelease, "cleanup release");
            request = null;
            cleanupCompleted = true;
        }
    }
}
