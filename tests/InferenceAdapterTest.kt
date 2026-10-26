package org.llmasr.minimal

import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.*
import org.llmasr.minimal.diagnostics.*
import org.llmasr.minimal.diagnostics.RuntimeLogEventKind.*
import org.llmasr.minimal.asr.*
import org.llmasr.minimal.model.*
import org.llmasr.minimal.task.*
import org.llmasr.minimal.transcription.AppRequestPolicy

/** Same production App/IME orchestration; injected native port, no Android/JNI runtime. */
class InferenceAdapterTest {
    private fun interface Checked { fun run() }
    private class Fixture {
        val clock = RuntimeLogClock.Fake(1700000000123L, 123000L); val store = RuntimeLogStore(1000, clock)
        val callbacks = ArrayList<InferencePhaseListener?>(); var readiness = 0; var libraries = 0; var calls = 0
        var ready = InferenceAdapter.Readiness { readiness++; clock.advance(-1000, 2000000) }
        var library = InferenceAdapter.Library { libraries++; clock.advance(1, 3000000) }
        var bridge = NativeTranscription { config, wav, language, cache, listener ->
            calls++; callbacks.add(listener)
            check(config!!.equals(PRIVATE) && wav!!.equals(PRIVATE) && language!!.equals("Chinese") && cache!!.equals(PRIVATE), "all inference arguments preserved")
            complete(listener); PAYLOAD
        }
        fun complete(listener: InferencePhaseListener?) {
            listener!!.onPhase(1); clock.advance(-2000, 5000000); listener.onPhase(2); clock.advance(1, 7000000)
            listener.onPhase(3); clock.advance(1, 11000000); listener.onPhase(4)
        }
        fun run(source: RuntimeLogSource?, id: String?): NativeResponse = InferenceAdapter(store, ready, library, bridge).transcribe(source, id, PRIVATE, PRIVATE, "Chinese", PRIVATE)
        fun run(): NativeResponse = run(RuntimeLogSource.APP, UUID.randomUUID().toString())
    }
    companion object {
        private const val PRIVATE = "私密转写 alice@example.test content://private/secret.wav"
        private val PAYLOAD = ("99.25 321.5 7\nlanguage Chinese<asr_text>" + PRIVATE).toByteArray(StandardCharsets.UTF_8)
        private var groups = 0; private var checks = 0
        private fun check(ok: Boolean, why: String) { checks++; if (!ok) throw AssertionError(why) }
        private fun group(name: String, test: Checked) { test.run(); groups++; println("PASS " + name) }
        private fun kinds(store: RuntimeLogStore): List<RuntimeLogEventKind> { val result = ArrayList<RuntimeLogEventKind>(); for (e in store.snapshot()) result.add(e.kind); return result }
        private fun count(store: RuntimeLogStore, kind: RuntimeLogEventKind): Long = store.snapshot().stream().filter { it.kind == kind }.count()
        private fun sequence(store: RuntimeLogStore, vararg expected: RuntimeLogEventKind) { check(kinds(store).equals(Arrays.asList(*expected)), "event sequence " + kinds(store)) }
        private fun payload(response: NativeResponse) {
            val expected = NativeResponse.parse(PAYLOAD); check(response.raw.equals(expected.raw) && response.display.equals(expected.display), "private result preserved")
            check(response.loadSeconds == 99.25 && response.inferenceSeconds == 321.5 && response.generatedTokens == 7, "protocol numeric data unchanged")
        }
        private fun noSuccess(store: RuntimeLogStore) { check(count(store, REQUEST_SUCCESS) == 0L, "no request success") }
        private fun successAndTime() {
            val f = Fixture(); val app = UUID.randomUUID().toString(); val ime = UUID.randomUUID().toString(); payload(f.run(RuntimeLogSource.APP, app))
            sequence(f.store, MODEL_ACCESS_STARTED, MODEL_ACCESS_COMPLETED, MODEL_LOAD_STARTED, MODEL_LOAD_COMPLETED, INFERENCE_STARTED, INFERENCE_COMPLETED, REQUEST_SUCCESS)
            val first = f.store.snapshot(); check(first[0].wallMillis == 1700000000123L, "actual initial wall timestamp"); check(first[1].wallMillis < first[0].wallMillis, "wall rollback retained")
            check(first[1].elapsedNanos == 2000000L && first[3].elapsedNanos == 5000000L && first[5].elapsedNanos == 11000000L && first[6].elapsedNanos == 28000000L, "numeric monotonic durations not reconstructed from response metrics")
            check(first[2].monotonicNanos == 5123000L && first[3].monotonicNanos == 10123000L, "phase timestamps from same store clock")
            check(first[3].formatLocal().contains("elapsed_ms=5.000000"), "numeric rendered duration"); payload(f.run(RuntimeLogSource.IME, ime))
            check(!app.equals(ime) && f.readiness == 2 && f.libraries == 2 && f.calls == 2, "two independent requests")
            for (i in 0 until 14) { val e = f.store.snapshot()[i]; check(e.sequence == i.toLong() && e.requestId!!.equals(if (i < 7) app else ime) && e.source == if (i < 7) RuntimeLogSource.APP else RuntimeLogSource.IME, "source and task ID") }
            check(f.callbacks[0] !== f.callbacks[1], "per-request callback, no global listener")
        }
        private fun sharedInstance() {
            val f = Fixture(); val unloaded: Any = JniNativeTranscription()
            check(unloaded is NativeTranscription, "standalone JNI bridge constructs without library loading")
            val shared = InferenceAdapter(f.store, f.ready, f.library, f.bridge); check(f.readiness == 0 && f.libraries == 0 && f.calls == 0, "graph-style construction is lazy")
            val app = UUID.randomUUID().toString(); val ime = UUID.randomUUID().toString(); payload(shared.transcribe(RuntimeLogSource.APP, app, PRIVATE, PRIVATE, "Chinese", PRIVATE)); payload(shared.transcribe(RuntimeLogSource.IME, ime, PRIVATE, PRIVATE, "Chinese", PRIVATE))
            check(f.readiness == 2 && f.libraries == 2 && f.calls == 2, "same adapter performs readiness/load each request"); check(f.callbacks[0] !== f.callbacks[1], "shared adapter retains no request listener")
            for (i in 0 until 14) { val e = f.store.snapshot()[i]; check(e.requestId!!.equals(if (i < 7) app else ime) && e.source == if (i < 7) RuntimeLogSource.APP else RuntimeLogSource.IME, "shared instance keeps APP/IME request attribution") }
        }
        private fun realReadiness() {
            val root = Files.createTempDirectory("inference-model-")
            try {
                val data = byteArrayOf(1, 2, 3); val hash = StringBuilder(); for (b in MessageDigest.getInstance("SHA-256").digest(data)) hash.append(String.format("%02x", b.toInt() and 255))
                val manifest = ModelManifest(Collections.singletonList(ModelEntry("model.bin", 3, hash.toString()))); val shaRuns = AtomicInteger(); val boundaries = AtomicInteger()
                val repo = object : ModelRepository(manifest, root.toFile(), { Long.MAX_VALUE }) {
                    override fun verifyAll(progress: Progress?, gate: CancelGate?) { shaRuns.incrementAndGet(); super.verifyAll(progress, gate) }
                    override fun validateManagedBoundary(gate: CancelGate?) { boundaries.incrementAndGet(); super.validateManagedBoundary(gate) }
                }
                Files.write(root.resolve("model.bin"), data); val readiness = ModelReadiness()
                val journal = Files.createDirectory(root.resolve("proof"))
                val io = object : ModelVerification.Io {
                    override fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)
                    override fun identity(file: File, directory: Boolean): String = Files.readAttributes(file.toPath(), "unix:dev,ino,mode,size,lastModifiedTime,ctime", LinkOption.NOFOLLOW_LINKS).toSortedMap().toString()
                    override fun read(name: String, limit: Int): ByteArray? {
                        val p = journal.resolve(name); if (!Files.exists(p)) return null
                        Files.newInputStream(p).use { input -> val out = ByteArrayOutputStream(); while (true) { val b = input.read(); if (b < 0) return out.toByteArray(); if (out.size() == limit) throw IOException("limit"); out.write(b) } }
                    }
                    override fun write(name: String, bytes: ByteArray) {
                        val temp = journal.resolve("$name.tmp")
                        FileOutputStream(temp.toFile()).use { out -> out.write(bytes); out.fd.sync() }
                        Files.move(temp, journal.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        java.nio.channels.FileChannel.open(journal, StandardOpenOption.READ).use { it.force(true) }
                    }
                }
                val proof = ModelVerification(manifest, readiness, io)
                val managed = object : ModelRepository(manifest, root.toFile(), { Long.MAX_VALUE }, proof) {
                    override fun verifyAll(progress: Progress?, gate: CancelGate?) { shaRuns.incrementAndGet(); super.verifyAll(progress, gate) }
                }
                val access = ModelAccess(managed, readiness); val f = Fixture(); f.ready = InferenceAdapter.Readiness { access.requireReady() }
                try { f.run(); throw AssertionError("missing proof reaches native") } catch (expected: IOException) { }
                check(shaRuns.get() == 0 && f.calls == 0, "missing proof never hashes or loads")
                val control = org.llmasr.minimal.modelmanagement.ModelOperationControl(); val op = control.tryBegin(org.llmasr.minimal.modelmanagement.ModelOperationControl.Kind.VERIFY)
                val token = proof.begin(managed); managed.verifyAll(null, ModelRepository.NEVER_CANCEL)
                check(proof.commit(managed, token, object : ModelVerification.CommitGate {
                    override fun reserve(t: ModelReadiness.VerifyToken): Boolean = control.tryReserveProofCommit(op, readiness, t)
                    override fun succeed(t: ModelReadiness.VerifyToken): Boolean = control.trySucceed(op, readiness, t)
                }), "explicit full verify"); control.complete(op, true)
                payload(f.run()); payload(f.run(RuntimeLogSource.IME, UUID.randomUUID().toString()))
                check(readiness.isReady && shaRuns.get() == 1, "explicit SHA then request zero hash")
                check(count(f.store, MODEL_ACCESS_COMPLETED) == 2L && count(f.store, MODEL_LOAD_COMPLETED) == 2L, "cached readiness still has real per-request memory load")
                check(count(f.store, MODEL_VERIFY_COMPLETED) == 0L, "cached access never claims a SHA-only completion")
                readiness.invalidate(); Files.write(root.resolve("model.bin"), byteArrayOf(9, 9, 9))
                try { f.run(); throw AssertionError("bad SHA accepted") } catch (expected: IOException) {}
                check(shaRuns.get() == 1 && f.calls == 2 && !readiness.isReady, "proof failure never hashes or reaches native")
                check(count(f.store, MODEL_ACCESS_FAILED) == 2L && count(f.store, REQUEST_FAILURE) == 2L, "proof failures logged")
            } finally { deleteTree(root) }
        }
        private fun failureIdentity() {
            for (stage in 0 until 3) for (type in 0 until 4) {
                val f = Fixture(); val original: Throwable = when (type) { 0 -> IOException(PRIVATE); 1 -> IllegalStateException(PRIVATE); 2 -> UnsatisfiedLinkError(PRIVATE); else -> OutOfMemoryError(PRIVATE) }
                if (stage == 0) f.ready = InferenceAdapter.Readiness { throw original }
                else if (stage == 1) f.library = InferenceAdapter.Library { throw original }
                else f.bridge = NativeTranscription { _, _, _, _, _ -> throw original }
                try { f.run(); throw AssertionError("failure accepted") } catch (actual: Throwable) { check(actual === original, "original failure identity incl checked JNI throwables/VM errors") }
                noSuccess(f.store); check(count(f.store, REQUEST_FAILURE) == 1L && count(f.store, MODEL_LOAD_STARTED) == 0L, "one real failure, no load")
                if (stage == 0) check(f.libraries == 0 && f.calls == 0, "readiness gates library/native")
            }
        }
        private fun resultFailures() {
            val bad = arrayOf<ByteArray?>(null, ByteArray(0), "missing".toByteArray(StandardCharsets.UTF_8), (PRIVATE + " 1 1\ntext").toByteArray(StandardCharsets.UTF_8), "NaN 1 1\ntext".toByteArray(StandardCharsets.UTF_8), "1 1 129\ntext".toByteArray(StandardCharsets.UTF_8), byteArrayOf('1'.code.toByte(), ' '.code.toByte(), '1'.code.toByte(), ' '.code.toByte(), '1'.code.toByte(), '\n'.code.toByte(), 0xff.toByte()))
            for (bytes in bad) {
                val f = Fixture(); f.bridge = NativeTranscription { _, _, _, _, listener -> f.complete(listener); bytes }
                try { f.run(); throw AssertionError("invalid bytes accepted") } catch (expected: IOException) { check(!expected.toString().contains(PRIVATE) && expected.cause == null, "fixed protocol exception") }
                sequence(f.store, MODEL_ACCESS_STARTED, MODEL_ACCESS_COMPLETED, MODEL_LOAD_STARTED, MODEL_LOAD_COMPLETED, INFERENCE_STARTED, INFERENCE_COMPLETED, REQUEST_FAILURE)
                check(f.store.snapshot()[6].detail!!.equals(if (bytes == null) "native-null" else "parse-failed"), "correct failure label")
            }
        }
        private fun phaseFailures() {
            for (boundary in 0..4) {
                val f = Fixture(); val emitted = boundary; val original = RuntimeException(PRIVATE)
                f.bridge = NativeTranscription { _, _, _, _, listener -> for (code in 1..emitted) { listener!!.onPhase(code); f.clock.advance(1, 123) }; throw original }
                try { f.run(); throw AssertionError("native failure accepted") } catch (actual: RuntimeException) { check(actual === original, "native identity") }
                noSuccess(f.store); check(count(f.store, REQUEST_FAILURE) == 1L, "one terminal failure")
                check(count(f.store, MODEL_LOAD_FAILED) == if (boundary == 1) 1L else 0L, "load failure only during real load")
                check(count(f.store, INFERENCE_FAILED) == if (boundary == 3) 1L else 0L, "inference failure only during response")
                check(count(f.store, MODEL_LOAD_COMPLETED) == if (boundary >= 2) 1L else 0L, "no fabricated load completion")
                check(count(f.store, INFERENCE_COMPLETED) == if (boundary == 4) 1L else 0L, "no fabricated inference completion")
            }
        }
        private fun invalidCallbacks() {
            val invalid = arrayOf(intArrayOf(), intArrayOf(2, 1, 2, 3, 4), intArrayOf(1, 1, 2, 3, 4), intArrayOf(1, 2, 2, 3, 4), intArrayOf(1, 3, 2, 3, 4), intArrayOf(1, 2, 3, 3, 4), intArrayOf(1, 2, 3, 4, 4), intArrayOf(1, 2, 3), intArrayOf(99, 1, 2, 3, 4), intArrayOf(0), intArrayOf(-1))
            for (codes in invalid) {
                val f = Fixture(); val saved = AtomicReference<InferencePhaseListener>()
                f.bridge = NativeTranscription { _, _, _, _, listener -> saved.set(listener); for (code in codes) listener!!.onPhase(code); PAYLOAD }
                payload(f.run()); noSuccess(f.store); check(count(f.store, REQUEST_FAILURE) == 0L && count(f.store, LOG_TELEMETRY_FAILED) == 1L, "malformed telemetry is NOT inference failure; one telemetry failure")
                val before = f.store.size(); for (code in 1..4) saved.get().onPhase(code); check(f.store.size() == before, "late success cannot revive invalid request")
            }
            val f = Fixture(); payload(f.run()); val stale = f.callbacks[0]; var before = f.store.size(); stale!!.onPhase(99); stale.onPhase(1); check(f.store.size() == before, "success terminal closes callback")
            f.bridge = NativeTranscription { _, _, _, _, listener -> stale.onPhase(1); stale.onPhase(4); f.complete(listener); PAYLOAD }
            payload(f.run()); check(count(f.store, REQUEST_SUCCESS) == 2L, "old request cannot affect new one")
            val crossThread = Fixture(); crossThread.bridge = NativeTranscription { _, _, _, _, listener ->
                val thread = Thread { listener!!.onPhase(1) }; thread.start(); try { thread.join(1000) } catch (e: InterruptedException) { throw IOException("test interrupted") }
                check(!thread.isAlive, "bounded callback thread"); crossThread.complete(listener); PAYLOAD
            }
            payload(crossThread.run()); noSuccess(crossThread.store); check(count(crossThread.store, LOG_TELEMETRY_FAILED) == 1L, "wrong-thread callback rejected")
            val failed = Fixture(); val failedCallback = AtomicReference<InferencePhaseListener>(); val malformedFailure = Fixture(); val actualFailure = IOException(PRIVATE)
            malformedFailure.bridge = NativeTranscription { _, _, _, _, listener -> listener!!.onPhase(2); listener.onPhase(1); throw actualFailure }
            try { malformedFailure.run(); throw AssertionError("expected real failure") } catch (actual: IOException) { check(actual === actualFailure, "telemetry failure cannot mask real failure") }
            noSuccess(malformedFailure.store); check(count(malformedFailure.store, LOG_TELEMETRY_FAILED) == 1L && count(malformedFailure.store, REQUEST_FAILURE) == 1L, "real failure still logged after invalid callbacks")
            failed.bridge = NativeTranscription { _, _, _, _, listener -> failedCallback.set(listener); listener!!.onPhase(1); throw IOException(PRIVATE) }
            try { failed.run(); throw AssertionError("expected failure") } catch (expected: IOException) {}
            before = failed.store.size(); for (code in 1..4) failedCallback.get().onPhase(code); check(failed.store.size() == before, "late completion after failed callback cannot mint success")
        }
        private fun loggingFaultsAndPrivacy() {
            for (at in 1..4) for (callbackError in arrayOf<Throwable>(IOException(PRIVATE), IllegalStateException(PRIVATE), AssertionError(PRIVATE))) {
                val callbackFault = Fixture(); val failureAt = at
                callbackFault.bridge = NativeTranscription { _, _, _, _, listener ->
                    for (code in 1..4) try { if (code == failureAt) throw callbackError; listener!!.onPhase(code) } catch (loggingOnly: Throwable) { break }
                    PAYLOAD
                }
                payload(callbackFault.run()); noSuccess(callbackFault.store)
                check(count(callbackFault.store, LOG_TELEMETRY_FAILED) == 1L && count(callbackFault.store, REQUEST_FAILURE) == 0L, "isolated callback throw leaves successful ASR but incomplete telemetry")
            }
            val runtimeSink = Fixture(); runtimeSink.store.addSink { _, _ -> throw IllegalStateException(PRIVATE) }; payload(runtimeSink.run()); check(count(runtimeSink.store, REQUEST_SUCCESS) == 1L, "core-isolated sink fault preserves complete sequence")
            val errorSink = Fixture(); errorSink.store.addSink { _, _ -> throw AssertionError(PRIVATE) }; payload(errorSink.run()); noSuccess(errorSink.store)
            check(count(errorSink.store, LOG_TELEMETRY_FAILED) == 1L && count(errorSink.store, REQUEST_FAILURE) == 0L, "non-RuntimeException instrumentation faults isolated and marked")
            val broken = object : RuntimeLogClock { override fun wallTimeMillis(): Long { throw AssertionError(PRIVATE) }; override fun monotonicNanos(): Long { throw IllegalStateException(PRIVATE) } }
            val unavailable = RuntimeLogStore(100, broken); val good = NativeTranscription { _, _, _, _, listener -> for (code in 1..4) listener!!.onPhase(code); PAYLOAD }
            payload(InferenceAdapter(unavailable, {}, {}, good).transcribe(RuntimeLogSource.APP, UUID.randomUUID().toString(), PRIVATE, PRIVATE, "Chinese", PRIVATE))
            check(unavailable.size() == 0, "store entirely unavailable does not change inference result"); val nativeError = IOException(PRIVATE)
            try { InferenceAdapter(unavailable, {}, {}, { _, _, _, _, _ -> throw nativeError }).transcribe(RuntimeLogSource.IME, UUID.randomUUID().toString(), PRIVATE, PRIVATE, "Chinese", PRIVATE); throw AssertionError("expected error") }
            catch (e: IOException) { check(e === nativeError, "logging errors cannot mask original native failure") }
            payload(InferenceAdapter(null, {}, {}, good).transcribe(RuntimeLogSource.APP, UUID.randomUUID().toString(), PRIVATE, PRIVATE, "Chinese", PRIVATE))
            val privacy = Fixture(); payload(privacy.run()); privacy.ready = InferenceAdapter.Readiness { throw IOException(PRIVATE) }
            try { privacy.run(); throw AssertionError("expected privacy failure") } catch (expected: IOException) {}
            val root = Files.createTempDirectory("inference-logs-")
            try {
                val persistence = RuntimeLogPersistence(root.toFile()); check(persistence.save(privacy.store.snapshot(), RuntimeLogPersistence.FailureSink.None()) == privacy.store.size(), "persist actual adapter events")
                val reopened = RuntimeLogPersistence(root.toFile()).load(); check(reopened.equals(privacy.store.snapshot()), "new vocabulary survives real persistence reopen")
                val out = ByteArrayOutputStream(); RuntimeLogCodec.writeSnapshot(reopened, out); val disk = String(Files.readAllBytes(persistence.file().toPath()), StandardCharsets.UTF_8); val exported = String(out.toByteArray(), StandardCharsets.UTF_8)
                for (secret in arrayOf(PRIVATE, "alice@", "content://", "secret.wav", "99.25", "321.5", "私密")) check(!disk.contains(secret) && !exported.contains(secret), "no payload/path/exception/metric text in persisted/exported logs")
            } finally { deleteTree(root) }
        }
        private fun ownerBoundaries() {
            val owner = TaskCoordinator(java.util.concurrent.Executor { it.run() }); val cleaned = AtomicInteger(); val reportFailures = AtomicInteger(); val ids = ArrayList<String?>(); val f = Fixture()
            val state = object : AppRequestPolicy.State {
                var text: String? = ""; var status: String? = ""
                override fun lastText(): String? = text
                override fun setLastText(value: String?) { text = value }
                override fun lastStatus(): String? = status
                override fun setLastStatus(value: String?) { status = value }
            }
            val reports = object : AppRequestPolicy.Reports {
                override fun writePending(ctx: RequestContext?) { check(owner.isBusy(), "pending owns task") }
                override fun writeTerminal(ctx: RequestContext?) { throw IOException(PRIVATE) }
                override fun writeFailure(ctx: RequestContext?, cause: Throwable?) { reportFailures.incrementAndGet() }
                override fun writeCancel(ctx: RequestContext?) { throw AssertionError("unexpected cancel") }
            }
            val runner = RequestRunner(owner, { 0 }, AppRequestPolicy(reports, state), { check(owner.isBusy(), "cleanup retains ownership"); cleaned.incrementAndGet() })
            for (source in arrayOf(RuntimeLogSource.APP, RuntimeLogSource.IME)) {
                check(runner.submit(TaskKind.INFERENCE) { ctx ->
                    check(owner.isBusy() && !owner.submit {}, "adapter caller holds exclusive owner"); ids.add(ctx.requestId); payload(f.run(source, ctx.requestId))
                    check(count(f.store, REQUEST_SUCCESS) == ids.size.toLong(), "adapter success before external report/IME commit")
                }, "accepted request"); check(!owner.isBusy(), "external report failure releases owner")
            }
            check(!ids[0]!!.equals(ids[1]) && cleaned.get() == 2 && reportFailures.get() == 2, "real runner UUID and cleanup boundaries")
            check(count(f.store, REQUEST_SUCCESS) == 2L && count(f.store, REQUEST_FAILURE) == 0L, "external failure is NOT rewritten as inference adapter failure")
        }
        private fun adapterConstructions(source: String): Int {
            val code = java.util.regex.Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\n]*|/\\*[\\s\\S]*?\\*/").matcher(source).replaceAll(" ")
            val calls = java.util.regex.Pattern.compile("\\b(?:new\\s+)?InferenceAdapter\\s*\\(").matcher(code); var count = 0; while (calls.find()) count++; return count
        }
        private fun checkNoAdapterConstruction(source: String) { if (adapterConstructions(source) != 0) throw AssertionError("inference caller must not construct adapter") }
        private fun checkSingleAdapter(source: String) { if (adapterConstructions(source) != 1) throw AssertionError("graph must construct exactly one inference adapter") }
        private fun checkImeSavedInference(source: String) {
            val diagnostic = "IME saved inference source order"
            val tokens = java.util.regex.Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\n]*|/\\*[\\s\\S]*?\\*/").matcher(source)
            val stripped = StringBuffer()
            while (tokens.find()) tokens.appendReplacement(stripped, java.util.regex.Matcher.quoteReplacement(if (tokens.group().equals("\"config.json\"")) tokens.group() else " "))
            tokens.appendTail(stripped); val code = stripped.toString(); val start = "override open fun transcribe("; val end = "return result.display"
            val begin = code.indexOf(start); val finish = code.indexOf(end, begin + 1)
            if (begin < 0 || finish < 0 || code.indexOf(start, begin + 1) >= 0) throw AssertionError(diagnostic)
            val body = code.substring(begin, finish)
            val required = arrayOf("val targetInference = inference", "val source = RuntimeLogSource.IME", "val requestId = targetRequest.requestId", "val config = File(modelRepository!!.modelDir(), \"config.json\").absolutePath", "val wavPath = targetRequest.inputWav!!.absolutePath", "val selectedLanguage = language", "val cache = context.cacheDir()!!.absolutePath", "val result = targetInference!!.transcribe(source, requestId, config, wavPath, selectedLanguage, cache)")
            var previous = -1
            for (part in required) {
                val at = body.indexOf(part); if (at <= previous || body.indexOf(part, at + 1) >= 0) throw AssertionError(diagnostic)
                val lineEnd = body.indexOf('\n', at); val line = body.substring(at, if (lineEnd < 0) body.length else lineEnd).trim()
                if (!line.equals(part)) throw AssertionError(diagnostic); previous = at
            }
            val beforeInvoke = body.substring(0, previous)
            if (java.util.regex.Pattern.compile("\\b(?:inference|targetInference)\\s*!!").matcher(beforeInvoke).find()) throw AssertionError(diagnostic)
        }
        private fun imeSavedInferenceFixtures(source: String) {
            checkImeSavedInference(source)
            val receiver = "val targetInference = inference"; val ime = "val source = RuntimeLogSource.IME"
            val invoke = "val result = targetInference!!.transcribe(source, requestId, config, wavPath, selectedLanguage, cache)"
            val config = "val config = File(modelRepository!!.modelDir(), \"config.json\").absolutePath"; val cache = "val cache = context.cacheDir()!!.absolutePath"
            val swapped = source.replace(config, "__ORDER_SWAP__").replace(cache, config).replace("__ORDER_SWAP__", cache)
            val cases = arrayOf(arrayOf("receiver removed", source.replace(receiver, "")), arrayOf("receiver replaced", source.replace(receiver, "val targetInference = other")), arrayOf("source removed", source.replace(ime, "")), arrayOf("source wrong", source.replace(ime, "val source = RuntimeLogSource.APP")), arrayOf("invoke removed", source.replace(invoke, "")), arrayOf("invoke comment", source.replace(invoke, "/* " + invoke + " */")), arrayOf("invoke string", source.replace(invoke, "val decoy = \"" + invoke + "\"")), arrayOf("early saved receiver", source.replace(receiver, receiver + "!!")), arrayOf("early receiver use", source.replace(ime, "targetInference!!\n        " + ime)), arrayOf("model cache swapped", swapped))
            for (fixture in cases) {
                check(!fixture[1].equals(source), "IME source mutant anchor " + fixture[0]); var rejected = false
                try { checkImeSavedInference(fixture[1]) } catch (failure: AssertionError) { rejected = "IME saved inference source order".equals(failure.message) }
                check(rejected, "same IME source checker rejects " + fixture[0])
            }
            checkImeSavedInference(source)
        }
        private fun sourceContracts() {
            for (valid in arrayOf("new InferenceAdapter(log,a,b,c)", "val shared = InferenceAdapter(log,a,b,c)")) {
                checkSingleAdapter(valid)
                for (bad in arrayOf(valid.replace("InferenceAdapter(", "RemovedAdapter("), valid + "; InferenceAdapter(null,a,b,c)")) {
                    var rejected = false; try { checkSingleAdapter(bad) } catch (failure: AssertionError) { rejected = "graph must construct exactly one inference adapter".equals(failure.message) }
                    check(rejected, "actual graph checker rejects missing/different-argument duplicate")
                }
            }
            val graphBase = Paths.get("android/app/src/org/llmasr/minimal/AppGraph"); val graphJava = Paths.get(graphBase.toString() + ".java"); val graphKotlin = Paths.get(graphBase.toString() + ".kt")
            check(Files.isRegularFile(graphJava) != Files.isRegularFile(graphKotlin), "unique graph source"); checkSingleAdapter(read(if (Files.isRegularFile(graphJava)) graphJava else graphKotlin))
            for (fixture in arrayOf("new InferenceAdapter(null,a,b,c);", "val copy = InferenceAdapter(null,a,b,c)")) {
                var rejected = false; try { checkNoAdapterConstruction(fixture) } catch (failure: AssertionError) { rejected = "inference caller must not construct adapter".equals(failure.message) }
                check(rejected, "actual caller checker rejects Java/Kotlin constructor fixture")
            }
            check(adapterConstructions("val injected: InferenceAdapter? = null // InferenceAdapter(a,b,c,d)\n") == 0 && adapterConstructions("String note = \"InferenceAdapter(a,b,c,d)\";") == 0, "caller guard ignores types/comments/literals")
            val callers = LinkedHashMap<String, String>(); callers["AsrOperation"] = "transcription"; callers["ImeBackend"] = "ime"
            for (e in callers.entries) {
                val caller = e.key; val sub = e.value; val base = Paths.get("android/app/src/org/llmasr/minimal/" + sub + "/" + caller)
                val javaSource = Paths.get(base.toString() + ".java"); val kotlinSource = Paths.get(base.toString() + ".kt")
                check(Files.isRegularFile(javaSource) != Files.isRegularFile(kotlinSource), "unique actual caller source " + caller)
                val kotlin = Files.isRegularFile(kotlinSource); val s = read(if (kotlin) kotlinSource else javaSource); checkNoAdapterConstruction(s)
                if (kotlin && caller.equals("ImeBackend")) imeSavedInferenceFixtures(s) else check(s.contains(if (kotlin) "inference!!.transcribe(RuntimeLogSource." else "inference.transcribe(RuntimeLogSource."), "actual " + caller + " uses shared production adapter")
                check(s.contains(if (kotlin) "private val inference: InferenceAdapter?" else "this.inference = inference;") && !s.contains("MainActivity"), "graph-injected inference port " + caller)
                check(!s.contains("NativeResponse.parse") && !s.contains("onTerminal") && !s.contains("new LoggingInferencePhaseListener"), "no duplicate caller orchestration " + caller)
            }
            val cpp = read(Paths.get("native/apk/asr_jni.cpp")); val notifier = cpp.substring(cpp.indexOf("struct PhaseNotifier"), cpp.indexOf("void throwFailure"))
            check(notifier.contains("env->ExceptionCheck() || listener == nullptr") && notifier.contains("env->ExceptionCheck() || !onPhase"), "original pending exceptions bypass all JNI logging")
            check(notifier.contains("GetMethodID(cls, \"onPhase\", \"(I)V\")") && notifier.contains("DeleteLocalRef(cls)"), "runtime method signature and class ref cleanup")
            check(!cpp.contains("VALUES") && !cpp.contains("onTerminal") && !cpp.contains("notifier.terminal"), "no reflection/early terminal")
            check(notifier.contains("onPhase = nullptr; // no later callbacks"), "throwing callback disables future success sequence")
            ordered(cpp, "notifier.phase(PHASE_MODEL_LOAD_STARTED)", "Llm::createLLM(c)", "llm->set_config(settings)", "llm->load()", "notifier.phase(PHASE_MODEL_LOAD_COMPLETED)", "notifier.phase(PHASE_INFERENCE_STARTED)", "llm->response(", "ctx->status!=LlmStatus::NORMAL_FINISHED", "notifier.phase(PHASE_INFERENCE_COMPLETED)", "env->NewByteArray(")
            check(cpp.contains("if (!out || env->ExceptionCheck()) return nullptr;") && cpp.contains("if (env->ExceptionCheck()) { env->DeleteLocalRef(out); return nullptr; }"), "allocation/copy pending exception retained")
            check(cpp.contains("std::lock_guard<std::mutex> lock(engine_mutex)") && cpp.contains("std::unique_ptr<Llm> llm"), "mutex and per-request RAII preserved")
            val settings = cpp.substring(cpp.indexOf("const std::string settings="), cpp.indexOf("if (!llm->set_config"))
            check(settings.contains("\\\"async\\\":false,\\\"sampler_type\\\":\\\"greedy\\\",\\\"max_new_tokens\\\":128,") && settings.contains("\\\"backend_type\\\":\\\"cpu\\\",\\\"thread_num\\\":2,\\\"tmp_path\\\":\\\"") && settings.contains("\\\"asr_language\\\":\\\"") && settings.contains("(lang==\"auto\"?\"\":lang)"), "native settings/max tokens/CPU preserved")
            check(cpp.contains("llm->response(\"<audio>\"+a+\"</audio>\",&raw)") && cpp.contains("ctx->gen_seq_len << \"\\n\" << raw.str()"), "input and result protocol unchanged")
            check(cpp.indexOf("Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener(JNIEnv*,") < cpp.indexOf("Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribe(JNIEnv*"), "forward declaration before wrapper")
            for (phase in InferencePhase.values()) check(cpp.contains("PHASE_" + phase.name + " = " + phase.code + ";"), "explicit ABI matches " + phase)
        }
        private fun ordered(source: String, vararg parts: String) { var at = -1; for (part in parts) { val next = source.indexOf(part, at + 1); check(next > at, "native boundary order " + part); at = next } }
        private fun read(path: Path): String = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        private fun deleteTree(root: Path) {
            val paths = Files.walk(root); var primary: Throwable? = null
            try { val it = paths.sorted(Comparator.reverseOrder()).iterator(); while (it.hasNext()) Files.delete(it.next()) }
            catch (failure: Throwable) { primary = failure; throw failure }
            finally { if (primary == null) paths.close() else try { paths.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) } }
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            group("App/IME validated result, task IDs, phase timestamps and monotonic durations", ::successAndTime)
            group("one shared adapter, lazy JNI construction and per-request APP/IME listeners", ::sharedInstance)
            group("real ModelAccess explicit SHA/proof readiness and request rejection", ::realReadiness)
            group("readiness/library/native IOException RuntimeException LinkageError VM error identity", ::failureIdentity)
            group("null and malformed native protocol never request success", ::resultFailures)
            group("load/inference failure boundaries", ::phaseFailures)
            group("missing duplicate out-of-order stale wrong-thread callbacks fail telemetry closed", ::invalidCallbacks)
            group("listener/store Throwable isolation and persisted/exported privacy", ::loggingFaultsAndPrivacy)
            group("production RequestRunner owner/cleanup and external commit boundary", ::ownerBoundaries)
            group("actual App/IME wiring and real JNI source contracts (not JNI runtime)", ::sourceContracts)
            println("PASS InferenceAdapterTest: " + groups + " groups, " + checks + " checks")
        }
    }
}
