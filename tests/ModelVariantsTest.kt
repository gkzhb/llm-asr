import org.llmasr.minimal.model.*
import java.io.File

/** Production policy/catalog/proof/resident interfaces; synthetic assets, never actual ASR. */
object ModelVariantsTest {
    private fun check(value: Boolean, diagnostic: String) { if (!value) throw AssertionError(diagnostic) }
    private fun refuses(diagnostic: String, body: () -> Unit) {
        var refused = false
        try { body() } catch (_: java.io.IOException) { refused = true }
        check(refused, diagnostic)
    }
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val fixed = FixedModelPackage.mnn(ModelManifest(ModelPackagePolicy.Companion.Json(File(root, "legacy-manifest.json").readBytes(), true).readObject()["files"].let { files ->
            (files as List<*>).map { raw -> val e = raw as Map<*, *>; ModelEntry(e["file"] as String, e["bytes"] as Long, e["sha256"] as String) }
        }))
        val policies = listOf("fp16", "int8", "int4", "mixed").map { label ->
            ModelPackagePolicy.parse(File(root, "$label.json").readBytes(), fixed).also {
                check(it.precision == label.uppercase(), "declared precision must remain independent")
                it.requireConfig("llm_config.json", File(root, "$label-config.json").readBytes())
            }
        }
        check(policies.map { it.contentSha256 }.toSet().size == 4, "different graph/weight content identities must remain distinct")
        val originalPolicy = File(root, "fp16.json").readText()
        for ((label, text) in listOf(
            "duplicate root key" to originalPolicy.replaceFirst("{", "{\"schema\":2,"),
            "unknown root key" to originalPolicy.replaceFirst("{", "{\"base_dir\":\"/outside\","),
            "wrong content identity" to originalPolicy.replace(policies[0].contentSha256, "0".repeat(64)),
            "precision not arbitrary" to originalPolicy.replace("\"FP16\"", "\"INT3\""),
            "path model ID" to originalPolicy.replace("fixture-fp16", "../escape"),
            "overflow size" to originalPolicy.replaceFirst("400000000", "9223372036854775808")
        )) refuses("strict v2 $label") { ModelPackagePolicy.parse(text.toByteArray(), fixed) }
        val reordered = ModelPackagePolicy.parse(File(root, "reordered.json").readBytes(), fixed)
        check(reordered.json().contentEquals(policies[0].json()), "JSON/file order cannot create another descriptor/proof")
        for (bad in listOf("config-path", "config-template", "config-offset", "config-block")) {
            val policy = ModelPackagePolicy.parse(File(root, "$bad.json").readBytes(), fixed)
            refuses("correctly rebound malicious config must fail closed: $bad") { policy.requireConfig("llm_config.json", File(root, "$bad-config.json").readBytes()) }
        }
        val ready = ModelReadiness(); val events = ArrayList<String>()
        val resident = ModelResidency(object : ModelResidency.Native {
            override fun prepare(config: String, cache: String): Double { events.add("prepare:$config"); return 0.0 }
            override fun release() { events.add("release") }
        }) { true }
        class Memory : ModelCatalog.Io {
            var bytes: ByteArray? = null
            var recovery: ByteArray? = null
            var mutations = 0
            var proofRequests = 0
            override fun readRecovery(): ByteArray? = recovery?.clone()
            override fun writeRecovery(bytes: ByteArray) { mutations++; recovery = bytes.clone() }
            override fun clearRecovery() { mutations++; recovery = null }
            override fun restoreCatalog(bytes: ByteArray?) { mutations++; if (failRollback) throw java.io.IOException("injected rollback failure"); this.bytes = bytes?.clone() }
            val journals = HashMap<String, HashMap<String, ByteArray>>()
            override fun readCatalog(): ByteArray? = bytes?.clone()
            var failWrite = false
            var storeThenThrow = false
            var failRollback = false
            override fun writeCatalog(bytes: ByteArray) {
                mutations++
                if (failWrite) throw java.io.IOException("injected catalog durable failure")
                this.bytes = bytes.clone()
                if (storeThenThrow) throw java.io.IOException("injected post-publication failure")
            }
            override fun proof(key: String): ModelVerification.Io { proofRequests++; return object : ModelVerification.Io {
                override fun identity(file: File, directory: Boolean): String = file.absolutePath + ":" + file.length() + ":" + file.lastModified()
                override fun exists(file: File): Boolean = file.exists()
                override fun read(name: String, limit: Int): ByteArray? = journals[key]?.get(name)?.clone()
                override fun write(name: String, bytes: ByteArray) { journals.getOrPut(key) { HashMap() }[name] = bytes.clone() }
            }
        }
        }
        val metadata = Memory()
        val catalog = ModelCatalog(fixed, File(root, "files"), File(root, "cache"), { Long.MAX_VALUE }, resident, metadata, ready)
        File(root, "files").mkdir(); File(root, "cache").mkdir()
        catalog.load(); val legacy = catalog.selected()
        val a = catalog.admit(policies[0]); val b = catalog.admit(policies[1])
        check(catalog.selected() === legacy, "import must not auto-select")
        check(catalog.admit(policies[0]) === a, "identical import must reuse immutable installation")
        // The collision candidate must NOT already be installed, otherwise the
        // same-content metadata guard masks a missing modelId uniqueness check.
        val candidateJson = String(policies[2].json(), Charsets.US_ASCII).replace(policies[2].modelId, policies[0].modelId)
        val candidate = ModelPackagePolicy.parse(candidateJson.toByteArray(Charsets.US_ASCII), fixed)
        check(candidate.contentSha256 != a.key && candidate.contentSha256 != b.key, "ID collision uses valid third uninstalled content")
        candidate.requireConfig("llm_config.json", File(root, "int4-config.json").readBytes())
        var idCollisionReason: String? = null
        try { catalog.admit(candidate) } catch (failure: java.io.IOException) { idCollisionReason = failure.message }
        check(idCollisionReason == "相同模型ID对应不同内容，请使用新ID", "same ID different content must not replace good installation")
        refuses("same content metadata alias must not create second proof") { catalog.admit(ModelPackagePolicy.parse(File(root, "collision-alias.json").readBytes(), fixed)) }
        metadata.failWrite = true
        refuses("durable selection failure must not publish different selected model") { catalog.select(a.key) }
        check(catalog.selected() === legacy, "failed selection preserves previous pointer")
        metadata.failWrite = false
        metadata.storeThenThrow = true
        refuses("post-publication select reports failure") { catalog.select(a.key) }
        check(catalog.selected() === legacy, "confirmed rollback preserves previous selected pointer")
        metadata.failRollback = true
        refuses("failed rollback select reports failure") { catalog.select(a.key) }
        refuses("uncertain catalog must fail request admission") { catalog.selected() }
        val uncertainRestart = ModelCatalog(fixed, File(root, "files"), File(root, "cache"), { Long.MAX_VALUE }, resident, metadata, ModelReadiness())
        refuses("restart must honor durable pending rollback") { uncertainRestart.load() }
        metadata.storeThenThrow = false; metadata.failRollback = false
        catalog.select(legacy.key) // Same key must reconcile rather than bypass uncertainty.
        check(catalog.selected() === legacy, "same-key selection reconciles old durable pointer")
        uncertainRestart.load(); check(uncertainRestart.selected().key == legacy.key, "restart sees restored previous pointer")
        catalog.select(a.key)
        check(catalog.selected() === a && catalog.rows().count { it.selected } == 1, "selection snapshot must match row")
        // Malformed embedded recovery documents never replace a valid selected catalog.
        val goodDocument = metadata.bytes!!.clone()
        val wrongMagic = goodDocument.clone().also { it[0] = 0 }
        val wrongIdentity = java.io.ByteArrayOutputStream().also { bytes ->
            java.io.DataOutputStream(bytes).use { data ->
                data.writeInt(0x4d434132); data.writeLong(1); data.writeUTF(a.key); data.writeInt(1)
                data.writeUTF(a.installationId)
                val descriptor = String(policies[0].json(), Charsets.US_ASCII).replace(policies[0].contentSha256, "0".repeat(64)).toByteArray(Charsets.US_ASCII)
                data.writeInt(descriptor.size); data.write(descriptor)
            }
        }.toByteArray()
        val corruptions = listOf("empty" to byteArrayOf(), "truncated" to goodDocument.copyOf(9), "magic" to wrongMagic,
            "identity" to wrongIdentity, "trailing" to (goodDocument + byteArrayOf(0)))
        for ((label, invalid) in corruptions) for (alreadyLoaded in listOf(false, true)) {
            val damaged = Memory(); damaged.bytes = goodDocument.clone()
            val projected = ModelReadiness()
            val candidateCatalog = ModelCatalog(fixed, File(root,"files"), File(root,"cache"), { Long.MAX_VALUE }, resident, damaged, projected)
            if (alreadyLoaded) candidateCatalog.load()
            val marker = byteArrayOf(1) + invalid; damaged.recovery = marker.clone()
            val mutations = damaged.mutations; val proofRequests = damaged.proofRequests
            refuses("malformed recovery must reject $label loaded=$alreadyLoaded") { candidateCatalog.load() }
            check(damaged.bytes!!.contentEquals(goodDocument) && damaged.recovery?.contentEquals(marker) == true && damaged.mutations == mutations,
                "invalid recovery preserves good catalog and marker before mutation: $label loaded=$alreadyLoaded")
            check(damaged.proofRequests == proofRequests, "recovery parsing must not create proof objects")
            refuses("invalid recovery disables selected authority") { candidateCatalog.selected() }
            check(!projected.isReady, "invalid recovery projection cannot be ready")
            refuses("invalid recovery rejects installation admission") { candidateCatalog.admit(policies[2]) }
            check(damaged.proofRequests == proofRequests && damaged.mutations == mutations, "invalid recovery admission has no proof/storage side effects")
            refuses("same-key retry validates bad recovery again") { candidateCatalog.select(a.key) }
            check(damaged.mutations == mutations && damaged.bytes!!.contentEquals(goodDocument) && damaged.recovery!!.contentEquals(marker), "same-key retry preserves invalid metadata")
            // Valid authority can be retried without manufacturing or silently deleting invalid data.
            damaged.recovery = byteArrayOf(1) + goodDocument
            candidateCatalog.load(); check(candidateCatalog.selected().key == a.key, "valid previous document recovers after explicit metadata repair")
        }
        if (args.getOrNull(1) == "recovery-diagnostic") { println("PASS side-effect-free recovery validation fresh/loaded/corruption/retry"); return }
        val restarted = ModelCatalog(fixed, File(root, "files"), File(root, "cache"), { Long.MAX_VALUE }, resident, metadata, ModelReadiness())
        restarted.load()
        check(restarted.selected().key == a.key && restarted.selected().installationId == a.installationId, "catalog selection/installation must survive restart")
        metadata.storeThenThrow = true; metadata.failRollback = true
        refuses("uncertain deletion is reported") { catalog.deleted(a) }
        refuses("uncertain deletion disables stale selected authority") { catalog.selected() }
        val deleteRestart = ModelCatalog(fixed, File(root, "files"), File(root, "cache"), { Long.MAX_VALUE }, resident, metadata, ModelReadiness())
        refuses("restart cannot accept failed deletion publication") { deleteRestart.load() }
        metadata.storeThenThrow = false; metadata.failRollback = false
        catalog.select(a.key); deleteRestart.load()
        check(deleteRestart.selected().installationId == a.installationId, "same-key reconciliation restores deleted descriptor")
        check(a.repository.modelDir() != b.repository.modelDir() && a.installationId != b.installationId, "per-content roots and installation generation separate")
        metadata.failWrite = true
        refuses("failed delete catalog commit keeps installation record retryable") { catalog.deleted(a) }
        check(catalog.selected() === a, "catalog delete failure does not counterfeit completed removal")
        metadata.failWrite = false
        metadata.storeThenThrow = true
        refuses("post-publication delete rolls back descriptor") { catalog.deleted(a) }
        check(catalog.selected() === a, "failed delete publication preserves retryable selected descriptor")
        metadata.storeThenThrow = false
        catalog.deleted(a)
        refuses("deleted selected model cannot silently fallback") { catalog.selected() }
        metadata.storeThenThrow = true
        refuses("post-publication installation reports failure") { catalog.admit(policies[0]) }
        check(catalog.rows().none { it.deployment.key == a.key }, "failed install does not publish memory descriptor")
        metadata.storeThenThrow = false
        val reinstalled = catalog.admit(policies[0])
        // Repeated rollback failure during installation cannot admit the disk-only descriptor.
        metadata.storeThenThrow = true; metadata.failRollback = true
        refuses("unconfirmed install publication fails closed") { catalog.admit(policies[3]) }
        refuses("unconfirmed installation blocks selection authority") { catalog.selected() }
        val installRestart = ModelCatalog(fixed, File(root, "files"), File(root, "cache"), { Long.MAX_VALUE }, resident, metadata, ModelReadiness())
        refuses("restart cannot expose uncertain installed descriptor") { installRestart.load() }
        metadata.storeThenThrow = false; metadata.failRollback = false
        catalog.load(); installRestart.load()
        check(installRestart.rows().none { it.deployment.key == policies[3].contentSha256 }, "rollback discards failed installed descriptor")
        check(reinstalled.installationId != a.installationId, "reinstall must not reuse old proof generation")
        check(events.none { it.startsWith("prepare:") }, "metadata operations never load native")
        val corrupt = Memory(); corrupt.bytes = byteArrayOf(1, 2, 3)
        val corruptCatalog = ModelCatalog(fixed, File(root, "files"), File(root, "cache"), { Long.MAX_VALUE }, resident, corrupt, ModelReadiness())
        refuses("corrupt catalog cannot silently restore legacy selection") { corruptCatalog.load() }
        refuses("failed catalog load has no request admission") { corruptCatalog.selected() }
        val orphanPolicy = policies[2]
        File(File(File(root, "files"), "model-variants").also { it.mkdir() }, orphanPolicy.contentSha256).mkdir()
        refuses("preexisting unregistered content root must never be replaced") { catalog.admit(orphanPolicy) }
        // Tiny production repository/proof/resident request path with an injected fixed fixture policy.
        // This does NOT relax the production v2 tokenizer/config contract.
        val tinyFiles = (0..6).map { index -> ModelEntry("f$index", 1, ModelPackagePolicy.sha(byteArrayOf(index.toByte()))) }
        val tinyFixed = FixedModelPackage(ModelManifest(tinyFiles), "tiny", "mnn")
        val tinyPolicy = ModelPackagePolicy.legacy(tinyFixed)
        val tinyRoot = File(root, "tiny-files"); tinyRoot.mkdir(); File(tinyRoot, "model").mkdir()
        for (index in 0..6) File(tinyRoot, "model/f$index").writeBytes(byteArrayOf(index.toByte()))
        val tinyMetadata = Memory()
        val tinyCatalog = ModelCatalog(tinyFixed, tinyRoot, File(root, "cache"), { Long.MAX_VALUE }, resident, tinyMetadata, ModelReadiness())
        tinyCatalog.load()
        val deployment = tinyCatalog.selected()
        val proof = deployment.repository.verification()!!
        val token = proof.begin(deployment.repository)
        deployment.repository.verifyAll()
        check(proof.commit(deployment.repository, token, object : ModelVerification.CommitGate {
            override fun reserve(value: ModelReadiness.VerifyToken): Boolean = deployment.readiness.accepts(value)
            override fun succeed(value: ModelReadiness.VerifyToken): Boolean = deployment.readiness.commitVerified(value)
        }), "real complete SHA/proof commit")
        val access = ModelAccess.forCatalog(tinyCatalog, resident)
        val binding = access.pin()
        val path = access.configPath("WRONG-FALLBACK")
        check(path == File(tinyRoot, "model/config.json").absolutePath, "selected deployment path not fallback")
        access.prepare(path, access.cachePath("WRONG-CACHE")); access.requireResidentReady()
        val request = access.enterRequest()
        refuses("native active request rejects maintenance before unloading") { resident.acquire() }
        access.finishRequest(request, true); access.unpin(binding)
        check(events.last { it.startsWith("prepare:") } == "prepare:$path", "native load path matches pinned request")
        val restartTiny = ModelCatalog(tinyFixed, tinyRoot, File(root, "cache"), { Long.MAX_VALUE }, resident, tinyMetadata, ModelReadiness())
        restartTiny.load()
        check(restartTiny.selected().access.restoreFast(), "own persistent receipt light restore")
        check(tinyPolicy.trustedLegacy, "fixture policy stays explicitly legacy fixture")
        // Hash-correct strict ZIP v1 fixture traverses discovery -> one prepared source -> repository.
        val zip = File(root, "legacy.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { output ->
            val json = tinyPolicy.json()
            fun entry(name: String, bytes: ByteArray) {
                val item = java.util.zip.ZipEntry(name)
                item.method = java.util.zip.ZipEntry.STORED; item.size = bytes.size.toLong(); item.compressedSize = item.size
                item.crc = java.util.zip.CRC32().also { it.update(bytes) }.value
                output.putNextEntry(item); output.write(bytes); output.closeEntry()
            }
            entry("model-package.json", json)
            for (index in 0..6) entry("f$index", byteArrayOf(index.toByte()))
        }
        val source = ModelZipSource({ zip.inputStream() }, { root }, { tinyFixed })
        val prepared = source.preparePackage({ Long.MAX_VALUE }, null, ModelRepository.NEVER_CANCEL)
        check(prepared.policy.trustedLegacy, "archive discovery preserves v1 trust")
        val target = ModelRepository(tinyFixed.manifest, File(root, "zip-target"), { Long.MAX_VALUE })
        target.importFrom(prepared, null)
        prepared.close()
        check(!File(root, "model-zip-staging/archive.part").exists(), "prepared session closes archive before success")
        target.verifyAll()
        if (args.getOrNull(1) == "catalog-only") { println("PASS finite catalog/recovery/legacy proof contracts; no large archive or model"); return }
        // Tiny v2 archive uses an explicit constructor-injected tokenizer trust tuple.
        // Production Graph still constructs FixedModelPackage.mnn with the APK fingerprint.
        val v2json = File(root, "archive-v2.json").readBytes()
        val v2fixed = FixedModelPackage(ModelManifest(ModelPackagePolicy.Companion.Json(File(root, "archive-v2-fixed.json").readBytes(), true).readObject()["files"].let { values ->
            (values as List<*>).map { raw -> val e = raw as Map<*, *>; ModelEntry(e["file"] as String, e["bytes"] as Long, e["sha256"] as String) }
        }), "archive-fixture", "mnn")
        val archive = File(root, "variant.zip")
        fun writeVariant(manifestBytes: ByteArray, malicious: Boolean) {
            java.util.zip.ZipOutputStream(archive.outputStream()).use { output ->
                fun put(name: String, bytes: ByteArray) {
                    val e = java.util.zip.ZipEntry(name); e.method = java.util.zip.ZipEntry.STORED; e.size = bytes.size.toLong(); e.compressedSize = e.size
                    e.crc = java.util.zip.CRC32().also { it.update(bytes) }.value
                    output.putNextEntry(e); output.write(bytes); output.closeEntry()
                }
                put("model-package.json", manifestBytes)
                for (name in ModelPackagePolicy.NAMES) {
                    val file = File(root, if (malicious && name == "llm_config.json") "archive-malicious-config.json" else "archive-$name")
                    val crc = java.util.zip.CRC32()
                    file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; crc.update(buffer, 0, n) } }
                    val e = java.util.zip.ZipEntry(name); e.method = java.util.zip.ZipEntry.STORED; e.size = file.length(); e.compressedSize = e.size; e.crc = crc.value
                    output.putNextEntry(e); file.inputStream().use { it.copyTo(output, 65536) }; output.closeEntry()
                }
            }
        }
        writeVariant(v2json, false)
        val v2source = ModelZipSource({ archive.inputStream() }, { root }, { v2fixed })
        val installedFiles = File(root, "installed-v2").also { it.mkdir() }
        val installedMetadata = Memory()
        val installedCatalog = ModelCatalog(v2fixed, installedFiles, File(root, "cache"), { Long.MAX_VALUE }, resident, installedMetadata, ModelReadiness())
        installedCatalog.load()
        v2source.preparePackage({ Long.MAX_VALUE }, null, ModelRepository.NEVER_CANCEL).use { pack ->
            check(!pack.policy.trustedLegacy && pack.policy.precision == "INT4", "actual v2 ZIP declaration production path")
            val installed = installedCatalog.admit(pack.policy)
            installed.repository.importFrom(pack, null)
            val ownProof = installed.repository.verification()!!
            val ownToken = ownProof.begin(installed.repository); installed.repository.verifyAll()
            check(ownProof.commit(installed.repository, ownToken, object : ModelVerification.CommitGate {
                override fun reserve(value: ModelReadiness.VerifyToken): Boolean = installed.readiness.accepts(value)
                override fun succeed(value: ModelReadiness.VerifyToken): Boolean = installed.readiness.commitVerified(value)
            }), "custom installation own proof commit")
            installedCatalog.select(installed.key)
            val selectedAccess = ModelAccess.forCatalog(installedCatalog, resident)
            val ownBinding = selectedAccess.pin()
            try {
                val config = selectedAccess.configPath("WRONG")
                check(config.contains(installed.policy.contentSha256), "custom content root reaches actual native port")
                selectedAccess.prepare(config, selectedAccess.cachePath("WRONG")); selectedAccess.requireResidentReady()
            } finally { selectedAccess.unpin(ownBinding) }
            val restartedCustom = ModelCatalog(v2fixed, installedFiles, File(root, "cache"), { Long.MAX_VALUE }, resident, installedMetadata, ModelReadiness())
            restartedCustom.load(); check(restartedCustom.selected().access.restoreFast(), "custom selected durable proof survives restart")
            // Transplanting another install's journal must fail its content/installation digest.
            val foreign = Memory(); for ((key, journal) in installedMetadata.journals) foreign.journals[key] = HashMap(journal)
            foreign.bytes = installedMetadata.bytes!!.clone()
            val otherFiles = File(root, "wrong-custom-root").also { it.mkdir() }
            val wrongRoot = ModelCatalog(v2fixed, otherFiles, File(root, "cache"), { Long.MAX_VALUE }, resident, foreign, ModelReadiness())
            wrongRoot.load()
            refuses("custom receipt cannot authorize another root") { wrongRoot.selected().access.restoreFast() }
            // A second installed content identity shares neither proof nor resident key.
            val nextAudio = byteArrayOf(7, 8)
            val nextEntries = installed.policy.manifest.entries().map { e -> if (e.file() == "audio.mnn") ModelEntry(e.file(), nextAudio.size.toLong(), ModelPackagePolicy.sha(nextAudio)) else e }
            val nextManifest = ModelManifest(nextEntries)
            val nextJson = String(installed.policy.json(), Charsets.US_ASCII)
                .replace(installed.policy.modelId, "archive-fixture-v2-second")
                .replace(installed.policy.contentSha256, ModelPackagePolicy.content(nextManifest))
                .replace("\"file\":\"audio.mnn\",\"bytes\":1,\"sha256\":\"${installed.policy.manifest.require("audio.mnn").sha256()}\"",
                    "\"file\":\"audio.mnn\",\"bytes\":2,\"sha256\":\"${ModelPackagePolicy.sha(nextAudio)}\"")
            val second = installedCatalog.admit(ModelPackagePolicy.parse(nextJson.toByteArray(), v2fixed))
            val secondSource = object : ModelSource {
                override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> =
                    second.policy.manifest.entries().associateTo(LinkedHashMap()) { it.file() to ModelSource.UriRef(it.file()) }
                override fun open(ref: ModelSource.UriRef?): java.io.InputStream =
                    if (ref!!.handle() == "audio.mnn") nextAudio.inputStream() else File(root, "archive-${ref.handle()}").inputStream()
            }
            second.repository.importFrom(secondSource, null)
            val secondProof = second.repository.verification()!!
            val secondToken = secondProof.begin(second.repository); second.repository.verifyAll()
            check(secondProof.commit(second.repository, secondToken, object : ModelVerification.CommitGate {
                override fun reserve(value: ModelReadiness.VerifyToken): Boolean = second.readiness.accepts(value)
                override fun succeed(value: ModelReadiness.VerifyToken): Boolean = second.readiness.commitVerified(value)
            }), "second custom installation own proof")
            installedCatalog.select(second.key)
            val secondBinding = selectedAccess.pin()
            try { selectedAccess.prepare(selectedAccess.configPath("WRONG"), selectedAccess.cachePath("WRONG")) }
            finally { selectedAccess.unpin(secondBinding) }
            check(events.last { it.startsWith("prepare:") } == "prepare:${second.configPath()}" && events.count { it == "release" } >= 1,
                "A to B selection unloads old single resident then loads different content path")
            installedCatalog.select(installed.key)
            check(installed.access.restoreFast(), "reselect own installation proof remains valid")
            val previousInstallation = installed.installationId
            val savedReceipt = installedMetadata.journals[installed.key]!!["receipt"]!!.clone()
            val savedLedger = installedMetadata.journals[installed.key]!!["ledger"]!!.clone()
            installed.repository.deleteAll()
            installedCatalog.deleted(installed)
            refuses("deleted custom selection cannot run another model implicitly") { installedCatalog.selected() }
            val reinstall = installedCatalog.admit(pack.policy)
            check(reinstall.installationId != previousInstallation, "real deleted custom reinstall gets new proof domain")
            installedMetadata.journals[installed.key]!!["receipt"] = savedReceipt
            installedMetadata.journals[installed.key]!!["ledger"] = savedLedger
            refuses("old custom receipt cannot restore new installation") { reinstall.access.restoreFast() }
        }
        writeVariant(File(root, "archive-malicious.json").readBytes(), true)
        var archiveDiagnostic = ""
        try { v2source.preparePackage({ Long.MAX_VALUE }, null, ModelRepository.NEVER_CANCEL).close() }
        catch (failure: java.io.IOException) { archiveDiagnostic = failure.message ?: "" }
        check(archiveDiagnostic.contains("固定契约"), "correctly rebound malicious config/ZIP CRC must fail config contract, not hash mismatch: $archiveDiagnostic")
        check(metadata.journals.keys.none { it.contains('/') }, "proof namespace never a manifest path")
        println("PASS production v2 policy/config rebound/collision/persistence/reselection/reinstall; not native inference")
    }
}
