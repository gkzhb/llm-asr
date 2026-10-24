package org.llmasr.minimal.testhost

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.view.inputmethod.EditorInfo
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Arrays
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.llmasr.minimal.ime.ImeController
import org.llmasr.minimal.model.ModelEntry
import org.llmasr.minimal.model.ModelManifest
import org.llmasr.minimal.model.ModelProviderBoundary
import org.llmasr.minimal.model.ModelReadiness
import org.llmasr.minimal.model.ModelReports
import org.llmasr.minimal.modelmanagement.ModelManagementController
import org.llmasr.minimal.modelmanagement.ModelManagementState
import org.llmasr.minimal.modelmanagement.ModelOperationControl
import org.llmasr.minimal.model.ModelRepository
import org.llmasr.minimal.model.SafModelSource
import org.llmasr.minimal.task.TaskCoordinator

/** SDK JUnit3 naming is intentional. All 14 methods need future device discovery.
 * Compilation proves no Android behavior, Binder/grants or production Service callbacks.
 */
@Suppress("DEPRECATION")
class FixtureInstrumentationTest : android.test.InstrumentationTestCase() {
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testEnginePayloadAndOrder() { FixtureChecks.enginePayloadAndOrder() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testReadinessRejectsBeforeLoad() { FixtureChecks.readinessRejectsBeforeLoad() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testBadBytesAreSafe() { FixtureChecks.badBytesAreSafe() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun testSingleConfirmationAndPassword() { FixtureChecks.singleConfirmationAndPassword() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun testLateResultIsInvalidated() { FixtureChecks.lateResultIsInvalidated() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testCleanupRetainsOwner() { FixtureChecks.cleanupRetainsOwner() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun testReusedBackendQueuedCancellation() { FixtureChecks.reusedBackendQueuedCancellation() }

    private fun source(provider: FakeDocumentsProvider?): SafModelSource {
        val info = ProviderInfo(); info.authority = FakeDocumentsProvider.AUTHORITY; info.exported = false
        provider!!.attachInfo(instrumentation.targetContext, info)
        val tree = DocumentsContract.buildTreeDocumentUri(FakeDocumentsProvider.AUTHORITY, "synthetic-root")
        return SafModelSource(ContentResolver.wrap(provider), tree)
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testProviderEnumerationClosesCursor() {
        val p = FakeDocumentsProvider()
        val files = source(p).enumerate(Collections.singleton("model.bin"), 2)
        assertEquals(Collections.singleton("model.bin"), files!!.keys)
        assertTrue(files["model.bin"]!!.handle() is Uri)
        assertEquals(1, p.queries.get()); assertEquals(1, p.closes.get())
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testProviderQueryErrorIsPrivate() {
        val p = FakeDocumentsProvider(); p.failQuery = true
        try { source(p).enumerate(Collections.singleton("model.bin"), 2); fail("query error accepted") }
        catch (failure: IOException) { safe(failure) }
        assertEquals(1, p.queries.get()); assertEquals(0, p.closes.get())
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testProviderCloseErrorIsPrivate() {
        val p = FakeDocumentsProvider(); p.failClose = true
        try { source(p).enumerate(Collections.singleton("model.bin"), 2); fail("close error accepted") }
        catch (failure: IOException) { safe(failure) }
        assertEquals(1, p.closes.get())
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Throws(Exception::class) open fun testProviderCancellationWaitsForQuery() {
        val p = FakeDocumentsProvider(); p.blockQuery = true
        val source = source(p)
        val cancelled = AtomicBoolean(); val returned = AtomicBoolean(); val result = AtomicReference<Throwable>()
        val worker = FakePorts.Worker(); var primary: Throwable? = null
        try {
            val owner = TaskCoordinator(worker)
            try {
                assertTrue(owner.submit {
                    try {
                        source.enumerate(Collections.singleton("model.bin"), 2, object : ModelRepository.CancelGate {
                            override fun cancelled(): Boolean = cancelled.get()
                            override fun reservePublish(): Boolean = throw AssertionError("enumeration must not reserve publication")
                        })
                    } catch (failure: Throwable) { result.set(failure) }
                    finally { returned.set(true) }
                })
                FakePorts.await(p.entered, "query entered"); cancelled.set(true)
                assertFalse(returned.get()); assertTrue(owner.isBusy()); assertFalse(owner.submit {})
                assertEquals(0, p.closes.get())
            } finally { p.release.countDown() }
            owner.awaitFree()
            assertTrue(returned.get()); assertFalse(owner.isBusy())
            assertTrue(result.get() is ModelRepository.CancelledException); assertEquals(1, p.closes.get())
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { if (primary == null) worker.close() else try { worker.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
    }
    @Throws(IOException::class)
    private fun newSafRoot(): File {
        val root = File(instrumentation.targetContext.cacheDir.canonicalFile, "m5saf-" + UUID.randomUUID())
        if (!root.mkdir()) throw IOException("synthetic root creation failed")
        return root
    }
    private inner class SafImportFixture @Throws(Exception::class) constructor(root: File?, executor: Executor?) {
        val bytes = byteArrayOf(1, 2, 3)
        val provider = FakeDocumentsProvider()
        val readiness = ModelReadiness()
        val control = ModelOperationControl()
        val state = ModelManagementState()
        val owner: TaskCoordinator
        val controller: ModelManagementController
        val saf: SafModelSource
        val installed: File
        val part: File
        val expectedSha: String
        init {
            val sourceDir = File(root, "source")
            if (!sourceDir.mkdir()) throw IOException("synthetic source creation failed")
            val sourceFile = File(sourceDir, "model.bin"); Files.write(sourceFile.toPath(), bytes)
            saf = source(provider)
            provider.setSyntheticFile(sourceFile)
            val repositoryDir = File(root, "repository"); expectedSha = sha256(bytes)
            val manifest = ModelManifest(Collections.singletonList(ModelEntry("model.bin", bytes.size.toLong(), expectedSha)))
            val repository = ModelRepository(manifest, repositoryDir) { ModelRepository.SPACE_PAD + bytes.size + 1024 }
            installed = File(repositoryDir, "model.bin"); part = File(repositoryDir, "model.bin.part")
            owner = TaskCoordinator(executor)
            controller = ModelManagementController(owner, repository, readiness, control, state)
        }
        fun counts(expected: Int) {
            assertEquals(expected, provider.queries.get()); assertEquals(expected, provider.closes.get())
            assertEquals(expected, provider.opens.get())
        }
        fun released() {
            assertFalse(owner.isBusy()); assertFalse(control.isBusy())
            assertNull(control.peekActive()); assertFalse(state.current().pageOwnerHeld)
        }
        fun unpublished() {
            assertFalse(readiness.isReady()); assertFalse(state.current().ready)
            assertFalse(installed.exists()); assertFalse(part.exists())
        }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Exception::class) open fun testSafImportOpenFailureThenRetryPreservesHistory() {
        val root = newSafRoot()
        try {
            val queue = FakePorts.Queue(); val f = SafImportFixture(root, queue)
            f.provider.failOpen = true; assertTrue(f.controller.startImport(f.saf))
            val failedId = f.state.current().activeOperationId; assertNotNull(failedId); queue.drain()
            val failed = f.state.current()
            assertEquals(ModelManagementState.Phase.FAILED, failed.phase)
            assertEquals(ModelManagementState.Outcome.FAILED, failed.lastOutcome)
            assertEquals(ModelProviderBoundary.FILE_FAILURE, failed.errorCode)
            assertFalse(failed.errorCode.contains(FakeDocumentsProvider.CANARY))
            assertTrue(failed.failedFiles.isEmpty()); assertEquals(Collections.singleton("model.bin"), failed.fileResults.keys)
            assertEquals(ModelReports.FileOutcome.Status.PENDING, failed.fileResults["model.bin"]!!.status)
            assertEquals(ModelManagementState.Outcome.SUCCEEDED, failed.cleanupOutcome)
            f.counts(1); f.unpublished(); f.released(); f.controller.refreshInspect()
            assertTrue(f.state.current().inspectBusy); queue.drain()
            val inspected = f.state.current()
            assertEquals(failedId, inspected.activeOperationId)
            assertEquals(failed.phase, inspected.phase); assertEquals(failed.lastOutcome, inspected.lastOutcome)
            assertEquals(failed.errorCode, inspected.errorCode); assertEquals(failed.failedFiles, inspected.failedFiles)
            assertEquals(failed.fileResults, inspected.fileResults); assertEquals(failed.cleanupOutcome, inspected.cleanupOutcome)
            assertFalse(inspected.inspectBusy); assertNull(inspected.inspectError)
            assertEquals(3L, inspected.expectedBytes); assertEquals(0L, inspected.installedBytes)
            assertEquals(0L, inspected.orphanPartBytes); assertEquals(1, inspected.files.size)
            assertFalse(inspected.files[0].present); f.counts(1); f.unpublished(); f.released()
            f.provider.failOpen = false; assertTrue(f.controller.startImport(f.saf))
            val retryId = f.state.current().activeOperationId
            assertNotNull(retryId); assertFalse(failedId!!.equals(retryId)); queue.drain()
            val succeeded = f.state.current()
            assertEquals(ModelManagementState.Phase.SUCCEEDED, succeeded.phase)
            assertEquals(ModelManagementState.Outcome.SUCCEEDED, succeeded.lastOutcome)
            assertEquals(retryId, succeeded.activeOperationId); assertNull(succeeded.errorCode)
            assertTrue(succeeded.failedFiles.isEmpty()); assertTrue(succeeded.ready)
            assertTrue(f.readiness.isReady()); assertEquals(ModelReadiness.State.READY, succeeded.readiness)
            val actual = Files.readAllBytes(f.installed.toPath())
            assertTrue(Arrays.equals(f.bytes, actual)); assertEquals(f.expectedSha, sha256(actual))
            assertFalse(f.part.exists()); f.counts(2); f.released()
        } finally { deleteSafRoot(root) }
    }
    /** Captures fatal errors outside RequestRunner; join precedes fixture deletion. */
    private class SafWorker : Executor, AutoCloseable {
        private var thread: Thread? = null
        private val failure = AtomicReference<Throwable>()
        override fun execute(work: Runnable?) {
            if (thread != null) throw AssertionError("SAF worker reused")
            thread = Thread({ try { work!!.run() } catch (problem: Throwable) { failure.set(problem) } }, "m5saf-open-worker")
            thread!!.start()
        }
        fun stopped(): Boolean = thread == null || !thread!!.isAlive
        @Throws(InterruptedException::class)
        override fun close() {
            if (thread != null) {
                thread!!.join(6000)
                if (thread!!.isAlive) { thread!!.interrupt(); thread!!.join(1000) }
            }
            FixtureChecks.require(stopped(), "SAF worker failed bounded join")
            val problem = failure.get(); if (problem != null) throw AssertionError("SAF worker failed", problem)
        }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Throws(Exception::class) open fun testSafImportCancellationWaitsForOpen() {
        val root = newSafRoot(); val worker = SafWorker()
        try {
            val f = SafImportFixture(root, worker); f.provider.blockOpen = true
            var operationId: String?
            val joining = worker; var primary: Throwable? = null
            try {
                try {
                    assertTrue(f.controller.startImport(f.saf)); FakePorts.await(f.provider.openEntered, "import open entered")
                    operationId = f.state.current().activeOperationId; assertNotNull(operationId)
                    f.controller.requestCancel(operationId)
                    assertEquals(ModelManagementState.Phase.CANCELLING, f.state.current().phase)
                    assertTrue(f.state.current().pageOwnerHeld); assertTrue(f.owner.isBusy())
                    assertTrue(f.control.isBusy()); assertNotNull(f.control.peekActive())
                    assertEquals(operationId, f.control.peekActive()!!.id)
                    assertFalse(f.control.peekActive()!!.isTerminated())
                    assertFalse(f.controller.startVerify()); assertFalse(f.owner.submit {})
                    assertEquals(operationId, f.state.current().activeOperationId); f.counts(1); f.unpublished()
                } finally { f.provider.openRelease.countDown() }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) joining.close() else try { joining.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
            val cancelled = f.state.current()
            assertEquals(operationId, cancelled.activeOperationId)
            assertEquals(ModelManagementState.Phase.CANCELLED, cancelled.phase)
            assertEquals(ModelManagementState.Outcome.CANCELLED, cancelled.lastOutcome)
            assertEquals(ModelManagementState.Outcome.SUCCEEDED, cancelled.cleanupOutcome)
            f.counts(1); f.unpublished(); f.released()
        } finally { if (worker.stopped()) deleteSafRoot(root) }
    }
    @Throws(Throwable::class)
    private fun onMain(action: Runnable?) {
        val failure = AtomicReference<Throwable>()
        instrumentation.runOnMainSync { try { action!!.run() } catch (t: Throwable) { failure.set(t) } }
        if (failure.get() != null) throw failure.get()
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(Throwable::class) open fun testInputConnectionAndManualInvalidation() {
        val intent = Intent(instrumentation.targetContext, ImeHostActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as ImeHostActivity
        try {
            onMain {
                val queue = FakePorts.Queue(); val backend = FakePorts.Backend()
                val c = ImeController(TaskCoordinator(queue), backend) {}
                val first = activity.field(0); val second = activity.field(1); val password = activity.field(2)
                first!!.requestFocus(); val firstInfo = EditorInfo(); val firstConnection = first.onCreateInputConnection(firstInfo)
                FixtureChecks.require(firstConnection != null, "first InputConnection")
                val initial = c.begin(firstInfo.inputType, firstInfo.imeOptions, "first")
                FixtureChecks.require(c.start(initial, "English"), "input request admitted"); queue.drain()
                FixtureChecks.require(c.commit(initial, "first", firstInfo.inputType, firstInfo.imeOptions) { text -> firstConnection!!.commitText(text, 1) }, "real InputConnection commit")
                FixtureChecks.require(!c.commit(initial, "first", firstInfo.inputType, firstInfo.imeOptions) { text -> firstConnection!!.commitText(text, 1) }, "no second input commit")
                FixtureChecks.require(FakePorts.TEXT.contentEquals(first.text), "first field exact text")
                FixtureChecks.require(c.start(initial, "English"), "late input admitted")
                c.invalidate(); second!!.requestFocus(); val nextInfo = EditorInfo(); val nextConnection = second.onCreateInputConnection(nextInfo)
                FixtureChecks.require(nextConnection != null, "second InputConnection")
                val next = c.begin(nextInfo.inputType, nextInfo.imeOptions, "second"); queue.drain()
                FixtureChecks.require(!c.commit(initial, "second", nextInfo.inputType, nextInfo.imeOptions) { text -> nextConnection!!.commitText(text, 1) } && next!!.preview().isEmpty(), "old result not routed")
                password!!.requestFocus(); val passwordInfo = EditorInfo(); val passwordConnection = password.onCreateInputConnection(passwordInfo)
                FixtureChecks.require(passwordConnection != null, "password InputConnection")
                FixtureChecks.require(c.begin(passwordInfo.inputType, passwordInfo.imeOptions, "password") == null, "real password metadata refused")
                FixtureChecks.require(second.text.length == 0 && password.text.length == 0, "other fields untouched")
                c.invalidate()
            }
        } finally { onMain(activity::finish) }
    }
    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> unchanged(value: T?): T = value as T
        private fun safe(failure: IOException?) {
            FixtureChecks.require(ModelProviderBoundary.DIRECTORY_FAILURE == failure!!.message && failure.cause == null && failure.suppressed.isEmpty(), "provider error has only fixed safe identity")
        }
        @Throws(Exception::class)
        private fun sha256(bytes: ByteArray?): String {
            val hex = StringBuilder()
            for (b in MessageDigest.getInstance("SHA-256").digest(unchanged<ByteArray>(bytes))) hex.append(String.format(java.util.Locale.ROOT, "%02x", b.toInt() and 255))
            return hex.toString()
        }
        @Throws(IOException::class)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private fun deleteSafRoot(root: File?) {
            val paths = Files.walk(root!!.toPath()); var primary: Throwable? = null
            try {
                val ordered = paths.sorted(java.util.Comparator.reverseOrder()).toArray { size -> arrayOfNulls<Path>(size) }
                for (path in ordered) Files.delete(path)
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) paths.close() else try { paths.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
    }
}
