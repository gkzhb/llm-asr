package org.llmasr.minimal.testhost

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.llmasr.minimal.asr.InferenceAdapter
import org.llmasr.minimal.asr.NativeTranscription
import org.llmasr.minimal.audio.RecordingControl
import org.llmasr.minimal.ime.ImeController
import org.llmasr.minimal.task.RequestContext
import org.llmasr.minimal.task.TaskKind

/** Synthetic, memory-only ports. No files, WAV encoding, AudioRecord or native loads. */
class FakePorts private constructor() {
    companion object {
        const val TEXT: String = "fixture sentence"
        @JvmStatic fun await(latch: CountDownLatch?, label: String?) {
            try {
                if (!latch!!.await(5, TimeUnit.SECONDS)) throw AssertionError("timeout: " + label)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AssertionError("interrupted: " + label, e)
            }
        }
    }
    class Engine {
        @JvmField val order: MutableList<String> = ArrayList()
        @JvmField var loads: Int = 0
        @JvmField var nativeCalls: Int = 0
        @JvmField var failReadiness: Boolean = false
        @JvmField var payload: ByteArray? = "1.25 2.5 3\nlanguage English<asr_text>fixture sentence".toByteArray(StandardCharsets.UTF_8)
        @JvmField val readiness: InferenceAdapter.Readiness = InferenceAdapter.Readiness {
            order.add("ready")
            if (failReadiness) throw IOException("synthetic readiness rejection")
        }
        @JvmField val library: InferenceAdapter.Library = InferenceAdapter.Library { loads++; order.add("load"); Unit }
        @JvmField val nativePort: NativeTranscription = NativeTranscription { config, wav, language, cache, listener ->
            nativeCalls++; order.add("native")
            FixtureChecks.require("synthetic-config" == config && "synthetic-input" == wav &&
                "English" == language && "synthetic-cache" == cache, "engine arguments")
            for (phase in 1..4) listener!!.onPhase(phase)
            payload
        }
    }
    class Queue : Executor {
        private var next: Runnable? = null
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        override fun execute(work: Runnable?) {
            if (next != null) throw AssertionError("overlapping queued task")
            next = work
        }
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun drain() {
            if (next == null) throw AssertionError("missing queued task")
            val work = next; next = null; work!!.run()
        }
    }
    /** Captures failures outside production's presentation-error handling too. */
    class Worker : Executor, AutoCloseable {
        private var thread: Thread? = null
        private val failure = AtomicReference<Throwable>()
        override fun execute(work: Runnable?) {
            if (thread != null) throw AssertionError("worker reused")
            thread = Thread({ try { work!!.run() } catch (t: Throwable) { failure.set(t) } }, "p1b-fixture-worker")
            thread!!.start()
        }
        @Throws(InterruptedException::class)
        override fun close() {
            if (thread != null) {
                thread!!.join(6000)
                if (thread!!.isAlive) {
                    thread!!.interrupt()
                    thread!!.join(1000)
                    throw AssertionError("worker did not finish")
                }
            }
            val problem = failure.get()
            if (problem != null) throw AssertionError("worker failure", problem)
        }
    }
    class Backend : ImeController.Backend {
        @JvmField val samples: ByteArray = byteArrayOf(1, 2, 3)
        @JvmField var captures: Int = 0
        @JvmField var inferences: Int = 0
        @JvmField var cleanups: Int = 0
        @JvmField var control: RecordingControl? = null
        @JvmField var request: RequestContext? = null
        @JvmField var duringInference: Runnable? = Runnable {}
        @JvmField var blockCleanup: Boolean = false
        @JvmField val cleanupEntered = CountDownLatch(1)
        @JvmField val cleanupRelease = CountDownLatch(1)
        @JvmField @Volatile var cleanupCompleted: Boolean = false
        override fun capture(control: RecordingControl?, progress: ImeController.Progress?): ByteArray? {
            captures++; this.control = control
            try {
                FixtureChecks.require(control!!.start {}, "capture admitted by real gate")
                progress!!.update(0.01)
                return samples
            } finally { control!!.captureReleased() }
        }
        override fun transcribe(request: RequestContext?, wav: ByteArray?, language: String?): String? {
            inferences++; this.request = request
            FixtureChecks.require(request!!.kind == TaskKind.INFERENCE && wav === samples &&
                "English" == language, "backend arguments")
            FixtureChecks.require(control!!.released() && control!!.committed(), "release before inference")
            duringInference!!.run()
            return TEXT
        }
        override fun cleanup(request: RequestContext?) {
            cleanups++
            FixtureChecks.require(this.request == null || this.request === request, "same request cleanup")
            cleanupEntered.countDown()
            if (blockCleanup) await(cleanupRelease, "cleanup release")
            this.request = null
            cleanupCompleted = true
        }
    }
}
