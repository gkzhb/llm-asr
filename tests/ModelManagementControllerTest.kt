import org.llmasr.minimal.transcription.*
import org.llmasr.minimal.modelmanagement.*
import org.llmasr.minimal.model.*
import org.llmasr.minimal.task.*
import org.llmasr.minimal.diagnostics.*
import java.io.*
import java.nio.file.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Deterministic tests of actual maintenance admission/worker/runner/IO ports. */
class ModelManagementControllerTest {
    internal class Queue : Executor {
        internal var pending: Runnable? = null; internal var reject = false
        override fun execute(command: Runnable?) {
            if (reject) throw RejectedExecutionException()
            if (pending != null) throw AssertionError("queued twice")
            pending = command
        }
        internal fun run() { val r = pending; pending = null; r!!.run() }
    }
    internal open class Fixture {
        internal val queue = Queue(); internal val owner = TaskCoordinator(queue); internal val readiness = ModelReadiness()
        internal val control = ModelOperationControl(); internal val state = ModelManagementState()
        internal val dir = ModelRepositoryCancelTest.dir(); internal val manifest = ModelRepositoryCancelTest.manifest(DATA, "a", "b")
        internal var repo = ModelRepository(manifest, dir, { 123456789 })
        internal val asr = AppState(); internal val reports = RequestRunnerTest.FakeReports(); internal val statusWrites = AtomicInteger()
        private val appObserver = AppState.Listener { statusWrites.incrementAndGet() }
        init {
            asr.setLastText("ASR preserved"); asr.setLastStatus("ASR status"); asr.addListener(appObserver)
            val appRunner = RequestRunner(owner, { 0 }, AppRequestPolicy(reports, object : AppRequestPolicy.State {
                override fun lastText(): String? = asr.lastText()
                override fun setLastText(s: String?) { asr.setLastText(s) }
                override fun lastStatus(): String? = asr.lastStatus()
                override fun setLastStatus(s: String?) { asr.setLastStatus(s) }
            }), {})
            appRunner.submit(TaskKind.INFERENCE) {}; queue.run()
            asr.setLastText("ASR preserved"); asr.setLastStatus("ASR status"); statusWrites.set(0)
        }
        internal open fun controller(): ModelManagementController = ModelManagementController(owner, repo, readiness, control, state)
        internal fun source(): ModelRepositoryCancelTest.Source = ModelRepositoryCancelTest.Source(DATA, "a", "b")
        internal fun install() { for (n in arrayOf("a", "b")) Files.write(File(dir, n).toPath(), DATA) }
        internal fun ready() { readiness.markVerified(readiness.tryBeginVerify()) }
        internal fun isolated() {
            check(reports.log.equals(Arrays.asList("pending:INFERENCE", "terminal:INFERENCE"))); check(asr.lastText().equals("ASR preserved"))
            check(asr.lastStatus().equals("ASR status")); check(statusWrites.get() == 0)
        }
        internal fun terminal(phase: ModelManagementState.Phase) {
            check(state.current().phase == phase); check(!control.isBusy); check(!owner.isBusy()); check(!state.current().pageOwnerHeld); isolated()
        }
    }
    private class LoggingFixture : Fixture(), AutoCloseable {
        val logs = RuntimeLogStore()
        override fun controller(): ModelManagementController = ModelManagementController(owner, repo, readiness, control, state, logs)
        override fun close() {
            logCheck(queue.pending == null && !owner.isBusy() && !control.isBusy, "logging fixture must be finalized before directory deletion")
            if (!Files.exists(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) return
            val paths = Files.walk(dir.toPath()); var primary: Throwable? = null
            try { val it = paths.sorted(Comparator.reverseOrder()).iterator(); while (it.hasNext()) Files.delete(it.next()) }
            catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) paths.close() else try { paths.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
    }
    companion object {
        private var checks = 0
        internal val DATA = byteArrayOf(1, 2, 3)
        private const val LOG_SECRET = "content://private-sentinel/model-path transcript-sentinel"
        private fun check(b: Boolean) { checks++; if (!b) throw AssertionError("check " + checks) }
        private fun logCheck(value: Boolean, message: String) { checks++; if (!value) throw AssertionError(message) }
        private inline fun loggingFixture(body: (LoggingFixture) -> Unit) {
            val f = LoggingFixture(); var primary: Throwable? = null
            try { body(f) } catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) f.close() else try { f.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        private inline fun workerScope(w: ModelRepositoryCancelTest.Worker, body: (ModelRepositoryCancelTest.Worker) -> Unit) {
            var primary: Throwable? = null
            try { body(w) } catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) w.close() else try { w.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        private fun startLogged(c: ModelManagementController, f: LoggingFixture, kind: ModelOperationControl.Kind?, source: ModelSource?): Boolean = when (kind!!) {
            ModelOperationControl.Kind.IMPORT -> c.startImport(source)
            ModelOperationControl.Kind.VERIFY -> c.startVerify()
            ModelOperationControl.Kind.DELETE -> c.startDelete(f.readiness.epoch(), true)
            else -> throw AssertionError("explicit operation required")
        }
        private fun logPair(f: LoggingFixture, id: String?, kind: ModelOperationControl.Kind, outcome: String, diagnostic: String) {
            val pair = ArrayList<RuntimeLogEvent>()
            for (event in f.logs.snapshot()) if (Objects.equals(id, event.requestId)) pair.add(event)
            logCheck(id != null && pair.size == 2, diagnostic)
            logCheck(pair[0].kind == RuntimeLogEventKind.valueOf("MODEL_" + kind + "_STARTED") && pair[1].kind == RuntimeLogEventKind.valueOf("MODEL_" + kind + "_" + outcome), "maintenance exact terminal kind: " + kind + "/" + outcome)
            logCheck(pair[0].source == RuntimeLogSource.SHARED && pair[1].source == RuntimeLogSource.SHARED, "maintenance fixed source")
            val detail = if ("COMPLETED".equals(outcome)) "ok" else if ("CANCELLED".equals(outcome)) "cancelled" else "failed"
            logCheck("ok".equals(pair[0].detail) && detail.equals(pair[1].detail), "maintenance fixed detail"); logCheck(pair[0].sequence < pair[1].sequence, "maintenance ordered log pair")
            val encoded = ByteArrayOutputStream(); RuntimeLogCodec.writeSnapshot(f.logs.snapshot(), encoded)
            val text = String(encoded.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
            logCheck(!text.contains("private-sentinel") && !text.contains("transcript-sentinel") && !text.contains("InternalError") && !text.contains("IOException") && !text.contains("\tat "), "maintenance exported logs exclude exception/path/text/stack")
        }
        private fun loggingRejection(kind: ModelOperationControl.Kind) = loggingFixture { f ->
            val c = f.controller(); f.queue.reject = true; logCheck(!startLogged(c, f, kind, f.source()), "maintenance rejected submission returns false")
            val id = f.state.current().activeOperationId; f.terminal(ModelManagementState.Phase.FAILED); logPair(f, id, kind, "FAILED", "maintenance rejection terminal missing: " + kind)
        }
        private fun loggingAbnormal() = loggingFixture { f ->
            val fatal = InternalError(LOG_SECRET)
            f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) { override fun verifyAll(p: Progress?, gate: CancelGate?) { throw fatal } }
            val c = f.controller(); logCheck(c.startVerify(), "maintenance abnormal task admitted"); val id = f.control.peekActive().id
            var seen: Throwable? = null; try { f.queue.run() } catch (failure: Throwable) { seen = failure }
            logCheck(seen === fatal, "maintenance abnormal exact Error identity"); f.terminal(ModelManagementState.Phase.FAILED)
            logPair(f, id, ModelOperationControl.Kind.VERIFY, "FAILED", "maintenance abnormal terminal missing: VERIFY")
        }
        private fun loggingObserver(admission: Boolean) = loggingFixture { f ->
            f.install(); val c = f.controller(); val fatal = InternalError(LOG_SECRET); val fired = AtomicBoolean(); val lockHeld = AtomicBoolean()
            val observer = ModelManagementState.Listener {
                if (Thread.holdsLock(c)) lockHeld.set(true)
                val s = f.state.current(); if (s.pageOwnerHeld && s.isTerminalPhase != admission && fired.compareAndSet(false, true)) throw fatal
            }
            f.state.addListener(observer); var seen: Throwable? = null
            try {
                try { if (!admission) logCheck(c.startVerify(), "maintenance observer task admitted"); if (admission) c.startVerify() else f.queue.run() }
                catch (failure: Throwable) { seen = failure }
            } finally { f.state.removeListener(observer) }
            logCheck(fired.get() && !lockHeld.get(), "maintenance observer reached outside monitor"); logCheck(seen === fatal, "maintenance observer exact Error identity")
            f.terminal(if (admission) ModelManagementState.Phase.FAILED else ModelManagementState.Phase.SUCCEEDED)
            logPair(f, f.state.current().activeOperationId, ModelOperationControl.Kind.VERIFY, if (admission) "FAILED" else "COMPLETED", if (admission) "maintenance admission log pair missing after observer error" else "maintenance decided terminal missing after observer error")
        }
        private fun loggingOrdinary(kind: ModelOperationControl.Kind, outcome: String) = loggingFixture { f ->
            f.install(); var source: ModelSource = f.source()
            if ("FAILED".equals(outcome)) {
                if (kind == ModelOperationControl.Kind.IMPORT) source = object : ModelSource {
                    override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> { throw IOException(LOG_SECRET) }
                    override fun open(ref: ModelSource.UriRef?): InputStream { throw AssertionError("must not open") }
                } else f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) {
                    override fun verifyAll(p: Progress?, gate: CancelGate?) { throw IOException(LOG_SECRET) }
                    override fun deleteManaged(path: File?) { throw IOException(LOG_SECRET) }
                }
            }
            val c = f.controller(); logCheck(startLogged(c, f, kind, source), "maintenance normal task admitted"); val id = f.control.peekActive().id
            if ("CANCELLED".equals(outcome)) c.requestCancel(id)
            f.queue.run(); val phase = if ("COMPLETED".equals(outcome)) ModelManagementState.Phase.SUCCEEDED else ModelManagementState.Phase.valueOf(outcome)
            f.terminal(phase); c.requestCancel(id); logCheck(f.state.current().phase == phase, "maintenance late cancel preserves terminal")
            logPair(f, id, kind, outcome, "maintenance normal terminal must occur exactly once")
        }
        private fun loggingNoAdmissionAndRetry() = loggingFixture { f ->
            val c = f.controller(); logCheck(f.owner.submit {}, "logging owner busy setup")
            logCheck(!c.startVerify() && !c.startImport(f.source()), "logging busy requests rejected"); logCheck(f.logs.size() == 0 && !f.control.isBusy, "busy requests have no maintenance events")
            f.queue.run(); logCheck(!c.startDelete(f.readiness.epoch() + 1, true), "logging stale confirmation rejected")
            c.refreshInspect(); f.queue.run(); logCheck(f.logs.size() == 0 && !f.owner.isBusy() && !f.control.isBusy, "inspect/stale zero maintenance events")
            f.queue.reject = true; logCheck(!c.startVerify(), "logging retry rejection setup"); val oldId = f.state.current().activeOperationId
            f.queue.reject = false; logCheck(c.startImport(f.source()), "logging retry accepted"); val newId = f.control.peekActive().id
            f.queue.run(); f.terminal(ModelManagementState.Phase.SUCCEEDED)
            logCheck(!oldId.equals(newId) && f.logs.size() == 4, "logging retry uses new UUID without duplicate terminal")
            logPair(f, oldId, ModelOperationControl.Kind.VERIFY, "FAILED", "logging old rejection pair"); logPair(f, newId, ModelOperationControl.Kind.IMPORT, "COMPLETED", "logging new retry pair")
        }
        private fun loggingTelemetry(sinkError: Boolean, workerError: Boolean) = loggingFixture { f ->
            f.install(); val fatal = InternalError(LOG_SECRET)
            if (workerError) f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) { override fun verifyAll(p: Progress?, gate: CancelGate?) { throw fatal } }
            val sink = RuntimeLogSink { _, _ -> if (sinkError) throw InternalError(LOG_SECRET); throw IllegalStateException(LOG_SECRET) }
            f.logs.addSink(sink); val c = f.controller(); var seen: Throwable? = null
            try { logCheck(c.startVerify(), "logging optional telemetry admission"); try { f.queue.run() } catch (failure: Throwable) { seen = failure } }
            finally { f.logs.removeSink(sink) }
            logCheck(seen === if (workerError) fatal else null, "optional telemetry preserves original worker Error identity")
            f.terminal(if (workerError) ModelManagementState.Phase.FAILED else ModelManagementState.Phase.SUCCEEDED)
            logPair(f, f.state.current().activeOperationId, ModelOperationControl.Kind.VERIFY, if (workerError) "FAILED" else "COMPLETED", "optional telemetry must not duplicate or skip emit attempt")
        }
        private fun loggingProtection() {
            for (kind in arrayOf(ModelOperationControl.Kind.IMPORT, ModelOperationControl.Kind.VERIFY, ModelOperationControl.Kind.DELETE)) {
                loggingOrdinary(kind, "COMPLETED"); loggingOrdinary(kind, "FAILED"); if (kind != ModelOperationControl.Kind.DELETE) loggingOrdinary(kind, "CANCELLED")
            }
            loggingNoAdmissionAndRetry(); for (sinkError in booleanArrayOf(false, true)) for (workerError in booleanArrayOf(false, true)) loggingTelemetry(sinkError, workerError)
        }
        private fun logging(name: String?) {
            when (name!!) {
                "logging-rejection-import" -> loggingRejection(ModelOperationControl.Kind.IMPORT)
                "logging-rejection-verify" -> loggingRejection(ModelOperationControl.Kind.VERIFY)
                "logging-rejection-delete" -> loggingRejection(ModelOperationControl.Kind.DELETE)
                "logging-abnormal" -> loggingAbnormal()
                "logging-terminal-observer" -> loggingObserver(false)
                "logging-admission-observer" -> loggingObserver(true)
                "logging-protection" -> loggingProtection()
                else -> throw IllegalArgumentException("unknown logging case")
            }
        }
        private fun persistentControllerFaults() {
            for (stage in listOf("open", "write", "flush", "close", "fsync", "rename", "dirsync")) {
                ModelAndroidHelpersTest.PersistentFixture().use { f ->
                    f.install(); val q = Queue(); val owner = TaskCoordinator(q); val control = ModelOperationControl(); val state = ModelManagementState()
                    val c = ModelManagementController(owner, f.repo, f.ready, control, state)
                    f.io.beforeWrite = { name -> if (name == "receipt") f.io.fail = stage }
                    check(c.startVerify()); q.run(); check(!owner.isBusy() && !control.isBusy && !f.ready.isReady)
                    check(state.current().phase == ModelManagementState.Phase.FAILED)
                    f.io.fail = null; f.io.beforeWrite = null
                    try { check(!ModelVerification(f.manifest, ModelReadiness(), f.io, ModelAndroidHelpersTest.MemoryGuard()).restoreFast(f.repo)) } catch (expected: IOException) { }
                }
            }
        }
        private fun waitForPublicationLock(thread: Thread, method: String) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (System.nanoTime() < deadline) {
                val waiting = thread.state == Thread.State.WAITING
                val actualCall = thread.stackTrace.any { it.className == "org.llmasr.minimal.model.ModelVerification" && it.methodName == method }
                if (waiting && actualCall) return
                if (!thread.isAlive) throw AssertionError("publication waiter exited before lock: $method")
                Thread.yield()
            }
            throw AssertionError("actual publication disk wait not observed: $method")
        }
        private fun publicationWaiters(mode: String) {
            ModelAndroidHelpersTest.PersistentFixture().use { f ->
                f.install()
                val events = Collections.synchronizedList(ArrayList<String>())
                val q=Queue(); val owner=TaskCoordinator(q); val control=ModelOperationControl(); val state=ModelManagementState()
                val c=ModelManagementController(owner,f.repo,f.ready,control,state)
                val publication=CountDownLatch(1); val publishRelease=CountDownLatch(1)
                val assetEntered=CountDownLatch(1); val assetRelease=CountDownLatch(1)
                val controllerError=AtomicReference<Throwable>(); val mutationError=AtomicReference<Throwable>(); val restoreError=AtomicReference<Throwable>()
                val mutationStarted=CountDownLatch(1); val restoreStarted=CountDownLatch(1); val restoreDone=CountDownLatch(1)
                val restoreAccepted=AtomicBoolean(); val dirtyDurable=AtomicBoolean(); val assetWritten=AtomicBoolean()
                val publicationEpoch=AtomicLong(-1)
                f.io.atStage={name,kind,_,stage ->
                    if (name=="ledger" && kind==3 && stage=="afterRename") {
                        events.add("COMMITTED-visible-before-succeed"); publicationEpoch.set(f.ready.epoch()); publication.countDown()
                        if (!publishRelease.await(5,TimeUnit.SECONDS)) throw IOException("publication barrier timeout")
                        events.add("publication-released")
                    }
                    if (name=="ledger" && kind==1 && stage=="afterSync" && publication.count==0L) {
                        dirtyDurable.set(true); events.add("waiting-mutator-DIRTY-durable")
                    }
                }
                val direct=object:ModelRepository(f.manifest,f.model.toFile(),{Long.MAX_VALUE},f.proof) {
                    override fun deleteManaged(file:File?) {
                        events.add("first-asset-write-boundary"); assetEntered.countDown()
                        if (!assetRelease.await(5,TimeUnit.SECONDS)) throw IOException("asset barrier timeout")
                        super.deleteManaged(file); assetWritten.set(true); events.add("asset-deleted")
                    }
                }
                var mutationThread:Thread?=null; var restoreThread:Thread?=null
                check(c.startVerify())
                val controllerThread=f.worker { try { q.run() } catch(t:Throwable) { controllerError.set(t) } finally { events.add("controller-true-exit") } }
                try {
                    check(publication.await(2,TimeUnit.SECONDS))
                    check(owner.isBusy()&&control.isBusy&&!control.peekActive().isSucceeded)
                    if (mode!="restore") {
                        mutationThread=f.worker { mutationStarted.countDown(); events.add("direct-delete-invoked-after-publication"); try { direct.deleteAll() } catch(t:Throwable) { mutationError.set(t) } finally { events.add("mutator-true-exit") } }
                        check(mutationStarted.await(1,TimeUnit.SECONDS)); waitForPublicationLock(mutationThread!!,"beginMutation")
                        events.add("mutator-confirmed-disk-wait")
                    }
                    if (mode!="delete") {
                        restoreThread=f.worker { restoreStarted.countDown(); events.add("restore-invoked-after-publication"); try { restoreAccepted.set(f.proof.restoreFast(f.repo)); events.add("restore-return-"+restoreAccepted.get()) } catch(expected:IOException) { events.add("restore-refused") } catch(t:Throwable) { restoreError.set(t) } finally { restoreDone.countDown(); events.add("restore-true-exit") } }
                        check(restoreStarted.await(1,TimeUnit.SECONDS)); waitForPublicationLock(restoreThread!!,"restoreFast")
                        events.add("restore-confirmed-disk-wait")
                    }
                    check(owner.isBusy()&&control.isBusy&&!control.peekActive().isSucceeded)
                    check(assetEntered.count==1L&&!assetWritten.get()&&Files.exists(f.model.resolve("a")))
                    if (restoreThread!=null) check(restoreDone.count==1L)
                    events.add("external-pre-release-owner-busy-no-asset-no-restore-return")
                    publishRelease.countDown()
                    if (mutationThread!=null) {
                        check(assetEntered.await(3,TimeUnit.SECONDS))
                        check(dirtyDurable.get() && !assetWritten.get() && Files.exists(f.model.resolve("a")))
                        val ledger=f.io.read("ledger",16384)!!
                        check(java.nio.ByteBuffer.wrap(ledger,4,4).int==1)
                        check(f.ready.epoch()>publicationEpoch.get()&&!f.ready.isReady)
                        var usable=false;try { f.access.requireReady(); usable=true } catch(expected:IOException) { }
                        check(!usable)
                        events.add("external-DIRTY-before-asset-current-invalid-guard-refused")
                    }
                } finally {
                    publishRelease.countDown(); assetRelease.countDown()
                    controllerThread.join(6000); mutationThread?.join(6000); restoreThread?.join(6000)
                }
                check(!controllerThread.isAlive&&mutationThread?.isAlive!=true&&restoreThread?.isAlive!=true)
                check(controllerError.get()==null&&mutationError.get()==null&&restoreError.get()==null)
                check(!owner.isBusy()&&!control.isBusy)
                check(state.current().phase==ModelManagementState.Phase.SUCCEEDED||state.current().phase==ModelManagementState.Phase.FAILED)
                if (mode=="restore") {
                    check(restoreAccepted.get()&&f.ready.isReady); f.access.requireReady(); check(!assetWritten.get())
                } else {
                    check(assetWritten.get()&&!Files.exists(f.model.resolve("a"))&&!f.ready.isReady)
                    var usable=false;try { f.access.requireReady(); usable=true } catch(expected:IOException) { };check(!usable)
                }
                events.add("external-all-real-workers-exited-owner-control-free")
                println("PASS reverse publication $mode events="+synchronized(events){ArrayList(events)}+" restoreAccepted="+restoreAccepted.get()+" controller="+state.current().phase)
            }
        }
        private fun committedFaults() {
            for (mode in listOf("delete","restore","combined")) publicationWaiters(mode)
            for (stage in listOf("open","write","flush","close","fsync","rename","dirsync","afterRename")) {
                ModelAndroidHelpersTest.PersistentFixture().use { f ->
                    f.install(); val q=Queue();val owner=TaskCoordinator(q);val control=ModelOperationControl();val state=ModelManagementState()
                    val c=ModelManagementController(owner,f.repo,f.ready,control,state)
                    val primary=IOException("committed-$stage"); val secondary=IOException("rollback-$stage")
                    f.io.faults.add(ModelAndroidHelpersTest.TestIo.Fault("ledger",3,1,stage,primary))
                    f.io.faults.add(ModelAndroidHelpersTest.TestIo.Fault("ledger",1,2,"open",secondary))
                    val entered=CountDownLatch(1);val release=CountDownLatch(1);val workerError=AtomicReference<Throwable>()
                    f.io.atStage={name,kind,occurrence,point -> if(name=="ledger"&&kind==1&&occurrence==2&&point=="open") { entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw IOException("rollback timeout") } }
                    check(c.startVerify())
                    val thread=f.worker { try { q.run() }catch(failure:Throwable){workerError.set(failure)} }
                    try {
                        check(entered.await(2,TimeUnit.SECONDS)); check(owner.isBusy()&&control.isBusy&&!f.ready.isReady)
                        check(!control.requestCancel(control.peekActive().id));check(!c.startVerify())
                    }finally{release.countDown();thread.join(4000)}
                    check(!thread.isAlive&&workerError.get()==null&&!owner.isBusy()&&!control.isBusy)
                    check(state.current().phase==ModelManagementState.Phase.FAILED&&!f.ready.isReady)
                    check(primary.suppressed.size==1&&primary.suppressed[0]===secondary)
                    f.io.faults.clear();f.io.atStage=null
                    // If the final ledger rename became visible despite sync failure and
                    // rollback also failed, next-process qualification is intentionally
                    // limited to complete COMMITTED continuity; current process never READY.
                    var restored=false;try{restored=ModelVerification(f.manifest,ModelReadiness(),f.io,ModelAndroidHelpersTest.MemoryGuard()).restoreFast(f.repo)}catch(expected:IOException){}
                    check(restored==(stage=="dirsync"||stage=="afterRename"))
                }
            }
            ModelAndroidHelpersTest.PersistentFixture().use { f ->
                f.install();val q=Queue();val owner=TaskCoordinator(q);val control=ModelOperationControl();val state=ModelManagementState()
                val c=ModelManagementController(owner,f.repo,f.ready,control,state);val entered=CountDownLatch(1);val release=CountDownLatch(1);val error=AtomicReference<Throwable>()
                f.io.atStage={name,kind,_,point ->if(name=="ledger"&&kind==3&&point=="open"){entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw IOException("late epoch timeout")}}
                check(c.startVerify());val thread=f.worker{try{q.run()}catch(t:Throwable){error.set(t)}}
                try{check(entered.await(2,TimeUnit.SECONDS));f.ready.invalidate();check(owner.isBusy()&&!f.ready.isReady)}finally{release.countDown();thread.join(4000)}
                check(!thread.isAlive&&error.get()==null&&state.current().phase==ModelManagementState.Phase.FAILED&&!f.ready.isReady)
            }
        }
        private fun sessionCleanupLogging() = loggingFixture { f ->
            val source=object:ModelSource {
                override fun enumerate(expected:MutableCollection<String?>?,maxEntries:Int)=f.source().enumerate(expected,maxEntries)
                override fun open(ref:ModelSource.UriRef?)=f.source().open(ref)
                override fun openSession(manifest:ModelManifest,space:ModelRepository.SpaceProvider,progress:ModelRepository.Progress?,cancel:ModelRepository.CancelGate):ModelSource.Session {
                    val delegate=super.openSession(manifest,space,progress,cancel)
                    return object:ModelSource.Session {
                        override fun archiveBytes()=1L
                        override fun enumerate(expected:MutableCollection<String?>?,maxEntries:Int)=delegate.enumerate(expected,maxEntries)
                        override fun open(ref:ModelSource.UriRef?)=delegate.open(ref)
                        override fun close(){delegate.close();progress?.update(ModelRepository.Stage.CLEANING_ARCHIVE,null,0,0,0,0,0,0,0);progress?.sourceCleanup(false);throw IOException("cleanup failure")}
                    }
                }
            }
            val c=f.controller();logCheck(c.startImport(source),"source session close fault admitted")
            val id=f.control.peekActive().id;f.queue.run()
            logCheck(!f.readiness.isReady && f.state.current().phase==ModelManagementState.Phase.FAILED && f.state.current().cleanupOutcome==ModelManagementState.Outcome.FAILED && !f.owner.isBusy(),"session cleanup blocks final SHA/proof success")
            logPair(f,id,ModelOperationControl.Kind.IMPORT,"FAILED","session cleanup exactly one log pair")
        }
        private fun loggingAll() { for (n in arrayOf("logging-rejection-import", "logging-rejection-verify", "logging-rejection-delete", "logging-abnormal", "logging-terminal-observer", "logging-admission-observer", "logging-protection")) logging(n) }
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            if (args!!.size > 0 && args[0]=="corefault") { committedFaults(); println("PASS final COMMITTED/controller owner/rollback/lateepoch faults");return }
            if (args.size > 0) { logging(args[0]); println("PASS maintenance logging case " + args[0] + ": " + checks + " checks"); return }
            loggingAll(); sessionCleanupLogging()
            persistentControllerFaults()
            committedFaults()
            run {
                val f = Fixture(); val c = f.controller(); val old = AtomicInteger(); val fresh = AtomicInteger()
                val oldListener = ModelManagementState.Listener { old.incrementAndGet() }; val newListener = ModelManagementState.Listener { fresh.incrementAndGet() }
                f.state.addListener(oldListener); check(c.startImport(f.source())); check(old.get() > 0)
                f.state.removeListener(oldListener); val oldCount = old.get(); f.state.addListener(newListener); f.queue.run()
                check(old.get() == oldCount); check(fresh.get() > 2); f.terminal(ModelManagementState.Phase.SUCCEEDED); f.state.removeListener(newListener)
            }
            run {
                val f = Fixture(); val c = f.controller(); check(f.owner.submit {}); val before = f.state.current()
                check(!c.startVerify()); check(!c.startImport(f.source())); check(!c.startDelete(0, true)); c.refreshInspect()
                check(f.control.peekActive() == null); check(f.state.current() === before); f.queue.run()
                f.queue.reject = true; check(!c.startVerify()); check(!f.control.isBusy); check(!f.owner.isBusy())
                f.queue.reject = false; check(c.startImport(f.source())); f.queue.run(); f.terminal(ModelManagementState.Phase.SUCCEEDED)
            }
            run {
                val f = Fixture(); val c = f.controller(); val src = f.source(); check(c.startImport(src)); val id = f.control.peekActive().id
                check(f.owner.isBusy()); check(!c.startVerify()); c.requestCancel("stale"); check(!f.control.peekActive().isCancelRequested)
                c.requestCancel(id); check(f.state.current().phase == ModelManagementState.Phase.CANCELLING)
                f.queue.run(); f.terminal(ModelManagementState.Phase.CANCELLED); check(src.opens == 0); check(f.readiness.state() == ModelReadiness.State.NOT_INSTALLED)
                check(c.startImport(src)); c.requestCancel(id); f.queue.run(); f.terminal(ModelManagementState.Phase.SUCCEEDED)
                check(f.readiness.isReady); check(f.state.current().installedBytes == 6L); check(f.state.current().files[0].verified)
                c.requestCancel(f.state.current().activeOperationId); check(f.state.current().phase == ModelManagementState.Phase.SUCCEEDED)
            }
            run {
                val f = Fixture(); check(f.dir.delete()); val c = f.controller(); c.refreshInspect(); check(f.state.current().files.isEmpty()); check(!f.dir.exists())
                f.queue.run(); check(f.state.current().files.size == 2); check(f.state.current().expectedBytes == 6L); check(f.state.current().availableBytes == 123456789L)
                check(!f.dir.exists()); check(f.readiness.state() == ModelReadiness.State.NOT_INSTALLED)
                f.dir.mkdir(); Files.write(File(f.dir, "a").toPath(), byteArrayOf(9)); c.refreshInspect(); f.queue.run()
                check(f.state.current().installedBytes == 1L); check(f.readiness.state() == ModelReadiness.State.INCOMPLETE)
                f.install(); c.refreshInspect(); f.queue.run(); check(f.readiness.state() == ModelReadiness.State.UNVERIFIED)
            }
            run {
                val f = Fixture(); f.install(); f.ready()
                f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) {
                    override fun verifyAll(p: Progress?, g: CancelGate?) {
                        check(!f.readiness.isReady); check(f.readiness.state() == ModelReadiness.State.VERIFYING); super.verifyAll(p, g); f.readiness.invalidate()
                    }
                }
                val c = f.controller(); check(c.startVerify()); f.queue.run(); f.terminal(ModelManagementState.Phase.FAILED); check(!f.readiness.isReady)
            }
            run {
                val f = Fixture(); f.install(); f.ready()
                f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) { override fun verifyAll(p: Progress?, g: CancelGate?) { super.verifyAll(p, g); f.control.requestCancel(f.control.peekActive().id) } }
                val c = f.controller(); check(c.startVerify()); f.queue.run(); f.terminal(ModelManagementState.Phase.CANCELLED); check(!f.readiness.isReady); check(f.readiness.state() == ModelReadiness.State.UNVERIFIED)
            }
            run {
                val f = Fixture(); f.install(); f.ready(); val epoch = f.readiness.epoch()
                val bad = object : ModelSource {
                    override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> { throw IOException("provider permission") }
                    override fun open(ref: ModelSource.UriRef?): InputStream { throw AssertionError("must not open") }
                }
                val c = f.controller(); check(c.startImport(bad)); f.queue.run(); f.terminal(ModelManagementState.Phase.FAILED); check(f.readiness.isReady); check(f.readiness.epoch() == epoch)
                check(c.startVerify()); c.requestCancel(f.control.peekActive().id); f.queue.run(); f.terminal(ModelManagementState.Phase.CANCELLED); check(f.readiness.state() == ModelReadiness.State.UNVERIFIED)
            }
            run {
                val f = Fixture(); f.install(); f.ready(); Files.write(File(f.dir, "b").toPath(), byteArrayOf(8, 8, 8)); val c = f.controller()
                check(c.startImport(ModelRepositoryCancelTest.Source(DATA))); f.queue.run(); f.terminal(ModelManagementState.Phase.FAILED); check(!f.readiness.isReady); check(f.readiness.state() == ModelReadiness.State.INVALID)
            }
            run {
                val f = Fixture(); f.install(); f.ready(); Files.write(File(f.dir, "b").toPath(), byteArrayOf(9, 9, 9)); val c = f.controller()
                check(c.startVerify()); f.queue.run(); f.terminal(ModelManagementState.Phase.FAILED); check(f.readiness.state() == ModelReadiness.State.INVALID); check(f.state.current().failedFiles.equals(Arrays.asList("b")))
            }
            run {
                val f = Fixture(); f.install(); f.ready(); val epoch = f.readiness.epoch(); val fail = AtomicBoolean(true); Files.write(File(f.dir, "unknown").toPath(), DATA)
                f.repo = object : ModelRepository(f.manifest, f.dir, { 987654321 }) {
                    override fun deleteManaged(file: File?) {
                        check(!f.readiness.isReady); check(f.readiness.epoch() > epoch)
                        if (file!!.name.equals("b") && fail.get()) throw IOException("denied"); super.deleteManaged(file)
                    }
                }
                val c = f.controller(); check(!c.startDelete(epoch, false)); check(!c.startDelete(epoch + 1, true)); check(!f.control.isBusy)
                check(c.startDelete(epoch, true)); c.requestCancel(f.control.peekActive().id); check(!f.control.peekActive().isCancelRequested)
                f.queue.run(); f.terminal(ModelManagementState.Phase.FAILED); check(f.state.current().deleteSucceeded == 1); check(f.state.current().deleteFailed == 1)
                check(f.state.current().installedBytes == 3L); check(f.state.current().availableBytes == 987654321L); check(f.readiness.state() == ModelReadiness.State.INCOMPLETE)
                fail.set(false); check(!c.startDelete(epoch, true)); check(c.startDelete(f.readiness.epoch(), true)); f.queue.run(); f.terminal(ModelManagementState.Phase.SUCCEEDED)
                check(f.readiness.state() == ModelReadiness.State.NOT_INSTALLED); check(f.state.current().unexpectedFiles.equals(Arrays.asList("unknown"))); check(File(f.dir, "unknown").exists())
            }
            run {
                val f = Fixture(); val gate = ModelRepositoryCancelTest.Gate()
                f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) { override fun deleteManaged(file: File?) { if (file!!.exists()) throw IOException("cleanup denied"); super.deleteManaged(file) } }
                val c = f.controller(); val src = f.source(); src.closeGate = gate; check(c.startImport(src))
                workerScope(ModelRepositoryCancelTest.Worker { f.queue.run() }) { w ->
                    try { gate.await(); c.requestCancel(f.control.peekActive().id); check(f.owner.isBusy()); check(!c.startVerify()); check(!f.owner.submit {}) }
                    finally { gate.close() }; w.join(); check(w.error.get() == null)
                }
                f.terminal(ModelManagementState.Phase.CANCELLED); check(f.state.current().cleanupOutcome == ModelManagementState.Outcome.FAILED)
                check(f.state.current().orphanPartBytes == 3L); check(f.readiness.state() == ModelReadiness.State.INCOMPLETE)
            }
            run {
                val f = Fixture(); val gate = ModelRepositoryCancelTest.Gate()
                f.repo = object : ModelRepository(f.manifest, f.dir, { 123456789 }) { override fun deleteManaged(file: File?) { if (file!!.exists()) gate.block(); super.deleteManaged(file) } }
                val c = f.controller(); val src = f.source(); src.failRead = true; check(c.startImport(src))
                workerScope(ModelRepositoryCancelTest.Worker { f.queue.run() }) { w ->
                    try { gate.await(); c.requestCancel(f.control.peekActive().id); check(f.owner.isBusy()); check(!c.startVerify()) } finally { gate.close() }
                    w.join(); check(w.error.get() == null)
                }
                f.terminal(ModelManagementState.Phase.CANCELLED); check(f.state.current().orphanPartBytes == 0L); check(f.state.current().cleanupOutcome == ModelManagementState.Outcome.SUCCEEDED)
            }
            run {
                val f = Fixture(); f.install(); f.ready(); val c = f.controller(); check(c.startDelete(f.readiness.epoch(), true)); f.readiness.invalidate(); f.queue.run()
                f.terminal(ModelManagementState.Phase.FAILED); check(File(f.dir, "a").exists()); Files.delete(File(f.dir, "a").toPath()); File(f.dir, "a").mkdir()
                check(c.startDelete(f.readiness.epoch(), true)); f.queue.run(); f.terminal(ModelManagementState.Phase.FAILED); check(File(f.dir, "b").exists()); check(f.readiness.state() == ModelReadiness.State.INVALID)
            }
            println("PASS " + checks + " deterministic model controller/connected runner isolation checks")
        }
    }
}
