import org.llmasr.minimal.model.*
import org.llmasr.minimal.modelmanagement.*
import java.io.*
import java.nio.file.*
import java.util.*
import java.util.concurrent.atomic.*

/** Review regressions use the real queued maintenance lane, not Android stubs. */
class ModelReviewFixTest {
    companion object {
        private fun check(value: Boolean, why: String) { if (!value) throw AssertionError(why) }
        private inline fun workerScope(worker: ModelRepositoryCancelTest.Worker, body: (ModelRepositoryCancelTest.Worker) -> Unit) {
            var primary: Throwable? = null
            try { body(worker) } catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) worker.close() else try { worker.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        private fun history() {
            for (mode in 0 until 4) {
                val f = ModelManagementControllerTest.Fixture()
                if (mode == 3) f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) { override fun deleteManaged(p: File?) { if (p!!.exists()) throw IOException("cleanup denied") } }
                var c = f.controller()
                if (mode == 0) { c.startImport(f.source()); c.requestCancel(f.control.peekActive().id) }
                if (mode == 1) { f.install(); Files.write(File(f.dir, "b").toPath(), byteArrayOf(9, 9, 9)); c.startVerify() }
                if (mode == 2) {
                    f.install(); f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) {
                        override fun deleteManaged(p: File?) { if (p!!.name.equals("b")) throw IOException("denied"); super.deleteManaged(p) }
                    }; c = f.controller(); c.startDelete(f.readiness.epoch(), true)
                }
                if (mode == 3) { val src = f.source(); src.failRead = true; c.startImport(src) }
                f.queue.run(); val old = f.state.current(); check(old.isTerminalPhase, "user terminal")
                if (mode == 1) check(ModelUiText.fileResult(old, "b").contains("SHA-256 不符"), "per-file exact SHA failure presentation")
                if (mode == 3) check(old.cleanupOutcome == ModelManagementState.Outcome.FAILED && old.orphanPartBytes >= 0, "real failed cleanup retained")
                Files.write(File(f.dir, "a").toPath(), byteArrayOf(7)); c.refreshInspect()
                check(old.activeOperationId.equals(f.state.current().activeOperationId), "inspect admission retains user identity")
                check(f.control.peekActive().kind == ModelOperationControl.Kind.INSPECT, "real separate inspect owner")
                f.queue.run(); var now = f.state.current()
                check(now.activeOperationId.equals(old.activeOperationId) && now.phase == old.phase && now.operationKind == old.operationKind, "history identity/outcome")
                check(Objects.equals(now.errorCode, old.errorCode) && now.failedFiles.equals(old.failedFiles) && now.cleanupOutcome == old.cleanupOutcome, "history failure/cleanup")
                check(now.deleteSucceeded == old.deleteSucceeded && now.deleteFailed == old.deleteFailed, "history deletion stats")
                check(now.files[0].actualBytes == 1L && !f.owner.isBusy() && !f.control.isBusy, "fresh inventory and release")
                Files.delete(File(f.dir, "a").toPath()); File(f.dir, "a").mkdir(); c.refreshInspect(); f.queue.run(); now = f.state.current()
                check(now.phase == old.phase && Objects.equals(now.errorCode, old.errorCode), "inspect error independent of history")
                check(now.readiness == ModelReadiness.State.INVALID && !f.owner.isBusy(), "inspect failure invalidates inventory")
            }
        }
        private fun terminal() {
            for (mode in 0 until 4) {
                val f = ModelManagementControllerTest.Fixture(); val c = f.controller(); if (mode == 2) f.install()
                if (mode != 3) { c.startVerify(); if (mode == 1) c.requestCancel(f.control.peekActive().id) }
                val before = AtomicReference<ModelManagementState.Snapshot>(); val rejected = AtomicBoolean(); val held = AtomicBoolean()
                val listener = ModelManagementState.Listener {
                    val s = f.state.current()
                    if (s.isTerminalPhase && before.compareAndSet(null, s)) {
                        held.set(f.owner.isBusy() && f.control.isBusy); c.requestCancel(s.activeOperationId)
                        rejected.set(f.state.current().phase == s.phase && Objects.equals(f.state.current().errorCode, s.errorCode))
                    }
                }
                f.state.addListener(listener)
                if (mode == 3) { f.queue.reject = true; check(!c.startVerify(), "executor rejection") } else f.queue.run()
                f.state.removeListener(listener); check(before.get() != null && held.get(), "terminal notification before real finalizer")
                check(rejected.get(), "late reentrant cancel must not overwrite terminal")
                check(f.state.current().phase == before.get().phase && Objects.equals(f.state.current().errorCode, before.get().errorCode), "finalizer preserves terminal")
                check(!f.owner.isBusy() && !f.control.isBusy, "finalizer release")
            }
        }
        private fun terminalWindow() {
            for (mode in 0 until 3) {
                val f = ModelManagementControllerTest.Fixture(); if (mode == 2) f.install(); val c = f.controller(); c.startVerify()
                if (mode == 1) c.requestCancel(f.control.peekActive().id)
                val pause = ModelRepositoryCancelTest.Gate(); val callbackError = AtomicReference<Throwable>()
                val listener = ModelReadiness.Listener {
                    if (f.state.current().isTerminalPhase && f.control.isBusy) try { pause.block() } catch (e: Throwable) { callbackError.set(e) }
                }
                f.readiness.addListener(listener)
                try {
                    workerScope(ModelRepositoryCancelTest.Worker { f.queue.run() }) { worker ->
                        try {
                            pause.await(); val before = f.state.current(); check(f.owner.isBusy() && f.control.isBusy, "post-terminal window retains real owner")
                            c.requestCancel(before.activeOperationId); check(!f.control.requestCancel(before.activeOperationId), "all committed terminals reject direct late cancel")
                            check(f.state.current().phase == before.phase && Objects.equals(f.state.current().errorCode, before.errorCode), "post-terminal cross-thread cancel preserves detailed result")
                            check(!c.startVerify() && !f.owner.submit {}, "no competing admission before finalizer")
                        } finally { pause.close() }
                        worker.join(); check(worker.error.get() == null && callbackError.get() == null, "bounded worker/listener failures observed outside guards")
                    }
                } finally { pause.close(); f.readiness.removeListener(listener) }
                check(!f.owner.isBusy() && !f.control.isBusy, "post-terminal worker finalized")
            }
        }
        private fun liveDeletion() {
            val f = ModelManagementControllerTest.Fixture(); f.install()
            f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) {
                override fun deleteManaged(p: File?) { if (p!!.name.equals("a")) throw IOException("denied"); super.deleteManaged(p) }
            }
            val observed = ArrayList<ModelManagementState.Snapshot>()
            val listener = ModelManagementState.Listener { val s = f.state.current(); if (s.phase == ModelManagementState.Phase.DELETING) observed.add(s) }
            f.state.addListener(listener); val c = f.controller(); c.startDelete(f.readiness.epoch(), true); f.queue.run(); f.state.removeListener(listener)
            var liveFailure = false
            for (s in observed) if (s.fileIndex == 1) liveFailure = liveFailure or (s.deleteFailed == 1 && s.deleteSucceeded == 0 && ModelUiText.task(s).contains("；失败 1"))
            check(liveFailure, "ordinary delete failure count displayed before next item/terminal")
            check(f.state.current().deleteFailed == 1 && f.state.current().deleteSucceeded == 1, "partial delete totals remain exact")
        }
        private fun boundary() {
            for (mode in 0 until 3) for (cached in booleanArrayOf(false, true)) {
                val f = ModelManagementControllerTest.Fixture(); f.install()
                val proofDir = Files.createTempDirectory("review-proof-")
                val proof = ModelVerification(f.manifest, f.readiness, ModelAndroidHelpersTest.TestIo(proofDir), ModelAndroidHelpersTest.MemoryGuard())
                f.repo = ModelRepository(f.manifest, f.dir, { 123456789 }, proof)
                val c = f.controller(); check(c.startVerify(), "boundary full verify"); f.queue.run()
                check(f.readiness.isReady, "boundary persistent proof prepared")
                if (!cached) { f.readiness.invalidate(); check(ModelAccess(f.repo, f.readiness).restoreFast(), "cold fast restore") }
                val part = File(f.dir, "a.part"); val external = File.createTempFile("model-review-target", ".bin"); Files.write(external.toPath(), byteArrayOf(8))
                if (mode == 0) Files.createSymbolicLink(part.toPath(), external.toPath()) else if (mode == 1) part.mkdir() else Files.write(part.toPath(), byteArrayOf(6))
                val nativeContinuation = AtomicBoolean(); val failure = AtomicReference<Throwable>()
                check(f.owner.submit { try { ModelAccess(f.repo, f.readiness).requireReady(); nativeContinuation.set(true) } catch (e: Throwable) { failure.set(e) } }, "lazy owner admitted"); f.queue.run()
                if (mode < 2) {
                    check(!nativeContinuation.get() && failure.get() != null, "unsafe part prevents lazy/native continuation including cached READY")
                    check(f.readiness.state() == ModelReadiness.State.INVALID, "unsafe part INVALID")
                } else check(!nativeContinuation.get() && !f.readiness.isReady && part.isFile, "safe orphan retained but blocks readiness")
                check(Files.readAllBytes(external.toPath())[0] == 8.toByte(), "external target untouched")
            }
        }
        private fun deletionText() {
            for (n in intArrayOf(1, 7, 14)) {
                val b = ModelManagementState.Builder(ModelManagementState.empty()); b.operationKind = ModelManagementState.OperationKind.DELETE; b.phase = ModelManagementState.Phase.FAILED
                b.fileName = "a"; b.fileIndex = n; b.fileCount = 7; b.deleteSucceeded = n - 1; b.deleteFailed = 1
                check(!ModelUiText.task(b.build()).contains("文件："), "terminal must omit live file denominator " + n)
            }
        }
        private fun planning() {
            for (parts in booleanArrayOf(false, true)) {
                val f = ModelManagementControllerTest.Fixture(); f.install(); if (parts) Files.write(File(f.dir, "a.part").toPath(), byteArrayOf(9))
                f.repo = ModelRepository(f.manifest, f.dir, { 0 }); val copyEvents = AtomicInteger()
                val noCopy = ModelManagementState.Listener { if (f.state.current().phase == ModelManagementState.Phase.COPYING) copyEvents.incrementAndGet() }
                f.state.addListener(noCopy); val src = ModelRepositoryCancelTest.Source(ModelManagementControllerTest.DATA); val c = f.controller()
                c.startImport(src); f.queue.run(); f.state.removeListener(noCopy)
                check(copyEvents.get() == 0 && !File(f.dir, "a.part").exists(), "zero space reuse performs no copy and reclaims safe part")
                check(f.state.current().reusedBytes == 6L, "all-local reuse exact bytes")
                check(ModelUiText.fileResult(f.state.current(), "a").contains("已复用"), "per-file reuse displayed separately from current inventory")
                check(src.opens == 0 && f.state.current().phase == ModelManagementState.Phase.SUCCEEDED, "reuse without source opens")
                val plan = f.state.current().javaClass.getField("importPlan").get(f.state.current())
                planValue(plan, "copyBytes", 0); planValue(plan, "reusedBytes", 6); planValue(plan, "requiredBytes", 0); planValue(plan, "availableAtCheck", 0)
                c.refreshInspect(); f.queue.run(); check(f.state.current().javaClass.getField("importPlan").get(f.state.current()) === plan, "inspect retains immutable plan")
            }
            val f = ModelManagementControllerTest.Fixture(); Files.write(File(f.dir, "a").toPath(), ModelManagementControllerTest.DATA)
            f.repo = ModelRepository(f.manifest, f.dir, { 17 }); val c = f.controller(); val src = f.source(); c.startImport(src); f.queue.run()
            check(f.state.current().phase == ModelManagementState.Phase.FAILED && src.opens == 0, "aggregate reject before first copy")
            var plan = f.state.current().javaClass.getField("importPlan").get(f.state.current())
            planValue(plan, "copyBytes", 3); planValue(plan, "reusedBytes", 3); planValue(plan, "requiredBytes", 3 + ModelRepository.SPACE_PAD); planValue(plan, "availableAtCheck", 17)
            check(ModelUiText.spacePlan(f.state.current()).contains("67108867 bytes") && ModelUiText.spacePlan(f.state.current()).contains("17 bytes"), "preflight UI retains exact need/available")
            try { (f.state.current().importPlan.reusableFiles as MutableList<String?>).add("bad"); throw AssertionError("mutable reuse plan") } catch (expected: UnsupportedOperationException) {}
            try { f.state.current().fileResults["bad"] = ModelReports.FileOutcome(ModelReports.FileOutcome.Status.FAILED); throw AssertionError("mutable file observations") } catch (expected: UnsupportedOperationException) {}
            val multi = ModelManagementControllerTest.Fixture(); val manifest = ModelRepositoryCancelTest.manifest(ModelManagementControllerTest.DATA, "a", "b", "c")
            multi.repo = ModelRepository(manifest, multi.dir, { 123456789 }); Files.write(File(multi.dir, "a").toPath(), ModelManagementControllerTest.DATA)
            val copies = ArrayList<ModelManagementState.Snapshot>(); val listener = ModelManagementState.Listener { val x = multi.state.current(); if (x.phase == ModelManagementState.Phase.COPYING) copies.add(x) }
            multi.state.addListener(listener); val mc = multi.controller(); mc.startImport(ModelRepositoryCancelTest.Source(ModelManagementControllerTest.DATA, "b", "c")); multi.queue.run(); multi.state.removeListener(listener)
            var last = -1L
            for (x in copies) { check(x.stageBytesDone >= last && x.stageBytesDone <= 6 && x.stageBytesTotal == 6L && x.reusedBytes == 3L, "multi-file monotonic exact cumulative copy/reuse"); last = x.stageBytesDone }
            check(last == 6L, "all new bytes counted across two copied files"); plan = multi.state.current().javaClass.getField("importPlan").get(multi.state.current())
            planValue(plan, "copyBytes", 6); planValue(plan, "reusedBytes", 3); planValue(plan, "requiredBytes", 6 + ModelRepository.SPACE_PAD)
        }
        private fun publicationHistory() {
            for (cancelAfterPublish in booleanArrayOf(false, true)) {
                val f = ModelManagementControllerTest.Fixture()
                f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) {
                    override fun publishPart(part: File?, dest: File?) {
                        super.publishPart(part, dest); if (cancelAfterPublish && dest!!.name.equals("a")) f.control.requestCancel(f.control.peekActive().id)
                    }
                }
                val src = f.source(); if (!cancelAfterPublish) src.data["b"] = byteArrayOf(9, 9, 9); val c = f.controller()
                check(c.startImport(src), "publication fixture admitted"); f.queue.run(); val s = f.state.current()
                check(Arrays.equals(Files.readAllBytes(File(f.dir, "a").toPath()), ModelManagementControllerTest.DATA), "published official A retained")
                check(!File(f.dir, "b").exists() && !f.readiness.isReady, "incomplete import cannot become READY")
                check(s.phase == if (cancelAfterPublish) ModelManagementState.Phase.CANCELLED else ModelManagementState.Phase.FAILED, "actual terminal cause")
                if (!cancelAfterPublish) check(s.failedFiles.contains("b"), "B SHA failure retained")
                val observation = ModelUiText.fileResult(s, "a")
                check(observation.contains("已复制并发布") && observation.contains("仍需全清单核验"), "published A observation must precede post-publish cancel and later B failure")
                c.refreshInspect(); f.queue.run(); check(ModelUiText.fileResult(f.state.current(), "a").equals(observation), "inspection preserves publication history")
                check(!f.owner.isBusy() && !f.control.isBusy, "publication fixture releases owner")
            }
        }
        private fun planValue(plan: Any?, field: String, value: Long) { check(plan != null && plan.javaClass.getField(field).getLong(plan) == value, "immutable plan exact " + field) }
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            when (val selected = args!![0]!!) {
                "publication" -> publicationHistory(); "window" -> terminalWindow(); "liveDeletion" -> liveDeletion(); "planning" -> planning()
                "history" -> history(); "terminal" -> terminal(); "boundary" -> boundary(); "deletion" -> deletionText(); else -> throw AssertionError(selected)
            }
            println("PASS review regression " + args[0])
        }
    }
}
