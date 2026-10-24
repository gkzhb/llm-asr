package org.llmasr.minimal.testhost

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.concurrent.atomic.AtomicInteger
import org.llmasr.minimal.asr.InferenceAdapter
import org.llmasr.minimal.asr.NativeResponse
import org.llmasr.minimal.diagnostics.RuntimeLogSource
import org.llmasr.minimal.ime.ImeController
import org.llmasr.minimal.ime.ImeSession
import org.llmasr.minimal.task.TaskCoordinator

/** Shared assertions call production objects, not a copied state machine or parser. */
class FixtureChecks private constructor() {
    companion object {
        @JvmStatic fun require(value: Boolean, label: String?) { if (!value) throw AssertionError(label) }
        @Throws(IOException::class)
        private fun run(f: FakePorts.Engine?): NativeResponse =
            InferenceAdapter(null, f!!.readiness, f.library, f.nativePort)
                .transcribe(RuntimeLogSource.IME, "fixture-request", "synthetic-config", "synthetic-input", "English", "synthetic-cache")
        @JvmStatic @Throws(Exception::class)
        fun enginePayloadAndOrder() {
            val f = FakePorts.Engine()
            val result = run(f)
            require(FakePorts.TEXT == result.display, "engine payload display")
            require(result.loadSeconds == 1.25 && result.inferenceSeconds == 2.5 && result.generatedTokens == 3, "engine metrics")
            require(f.order == Arrays.asList("ready", "load", "native") && f.loads == 1 && f.nativeCalls == 1, "engine call order")
        }
        @JvmStatic @Throws(Exception::class)
        fun readinessRejectsBeforeLoad() {
            val f = FakePorts.Engine(); f.failReadiness = true
            try { run(f); throw AssertionError("readiness accepted") }
            catch (expected: IOException) { require("synthetic readiness rejection" == expected.message, "readiness identity") }
            require(f.loads == 0 && f.nativeCalls == 0 && f.order == Arrays.asList("ready"), "readiness zero load/native")
        }
        @JvmStatic @Throws(Exception::class)
        fun badBytesAreSafe() {
            for (bad in arrayOf("synthetic-private-canary".toByteArray(StandardCharsets.UTF_8),
                    byteArrayOf('1'.code.toByte(), ' '.code.toByte(), '1'.code.toByte(), ' '.code.toByte(), '1'.code.toByte(), '\n'.code.toByte(), 255.toByte()))) {
                val f = FakePorts.Engine(); f.payload = bad
                try { run(f); throw AssertionError("bad bytes accepted") }
                catch (expected: IOException) {
                    require("Invalid native response protocol" == expected.message && expected.cause == null && expected.suppressed.isEmpty(), "bad bytes fixed safe exception")
                }
            }
        }
        @JvmStatic fun singleConfirmationAndPassword() {
            val queue = FakePorts.Queue(); val b = FakePorts.Backend()
            val owner = TaskCoordinator(queue)
            val c = ImeController(owner, b) {}
            val s = c.begin(1, 0, "first")
            require(c.start(s, "English"), "IME admitted"); queue.drain()
            val commits = AtomicInteger()
            require(c.commit(s, "first", 1, 0) { text -> require(FakePorts.TEXT == text, "commit payload"); commits.incrementAndGet(); true }, "first confirmation")
            require(!c.commit(s, "first", 1, 0) { commits.incrementAndGet(); true } && commits.get() == 1, "single confirmation only")
            require(c.begin(0x81, 0, "password") == null, "password begin refused")
            require(b.captures == 1 && b.inferences == 1 && b.cleanups == 1 && !owner.isBusy() && b.control!!.released(), "memory-only backend complete")
        }
        @JvmStatic fun lateResultIsInvalidated() {
            val queue = FakePorts.Queue(); val b = FakePorts.Backend()
            val owner = TaskCoordinator(queue); val c = ImeController(owner, b) {}
            val old = c.begin(1, 0, "first"); val fresh = arrayOfNulls<ImeSession>(1)
            b.duringInference = Runnable {
                c.invalidate(); fresh[0] = c.begin(1, 0, "second")
                require(owner.isBusy() && !c.start(fresh[0], "English"), "late request retains owner")
            }
            require(c.start(old, "English"), "late request admitted"); queue.drain()
            require(!old!!.valid() && old.preview().isEmpty() && fresh[0]!!.preview().isEmpty(), "late result discarded")
            require(!c.commit(old, "first", 1, 0) { true }, "stale commit refused")
        }
        @JvmStatic fun reusedBackendQueuedCancellation() {
            val queue = FakePorts.Queue(); val b = FakePorts.Backend()
            val owner = TaskCoordinator(queue); val c = ImeController(owner, b) {}
            val first = c.begin(1, 0, "first")
            require(c.start(first, "English"), "reuse first admitted"); queue.drain()
            require(b.captures == 1 && b.inferences == 1 && b.cleanups == 1 && !owner.isBusy() && FakePorts.TEXT == first!!.preview(), "reuse first completed")
            require(c.commit(first, "first", 1, 0) { text -> FakePorts.TEXT == text }, "reuse first committed")
            require(c.start(first, "English"), "reuse second queued")
            c.invalidate(); val next = c.begin(1, 0, "second")
            require(owner.isBusy() && !c.start(next, "English"), "queued cancel retains owner")
            queue.drain()
            require(b.captures == 1 && b.inferences == 1 && b.cleanups == 2 && !owner.isBusy() && !c.ownsTask(), "queued cancel cleans without capture or inference")
            require(!first!!.valid() && first.preview().isEmpty() && next!!.preview().isEmpty(), "queued cancel leaves no late preview")
            require(!c.commit(first, "first", 1, 0) { true }, "queued cancel cannot commit old session")
            require(c.start(next, "English"), "reuse subsequent request admitted"); queue.drain()
            require(b.captures == 2 && b.inferences == 2 && b.cleanups == 3 && !owner.isBusy() && FakePorts.TEXT == next!!.preview(), "reuse subsequent request completes")
        }
        @JvmStatic @Throws(Exception::class)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        fun cleanupRetainsOwner() {
            val b = FakePorts.Backend(); b.blockCleanup = true
            val worker = FakePorts.Worker(); var primary: Throwable? = null
            try {
                val owner = TaskCoordinator(worker); val c = ImeController(owner, b) {}
                val s = c.begin(1, 0, "cleanup")
                try {
                    require(c.start(s, "English"), "cleanup request admitted")
                    FakePorts.await(b.cleanupEntered, "cleanup entered")
                    require(!b.cleanupCompleted, "cleanup must wait for release")
                    require(owner.isBusy() && !owner.submit {}, "cleanup retains exclusive owner")
                    c.invalidate(); require(owner.isBusy(), "invalidate cannot release cleanup owner")
                } finally { b.cleanupRelease.countDown() }
                owner.awaitFree()
                require(b.cleanupCompleted && b.cleanups == 1 && !owner.isBusy(), "cleanup complete then free")
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                if (primary == null) worker.close()
                else try { worker.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) }
            }
        }
    }
}
