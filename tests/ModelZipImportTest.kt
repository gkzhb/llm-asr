import org.llmasr.minimal.model.*
import org.llmasr.minimal.modelmanagement.*
import org.llmasr.minimal.task.*
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.function.Supplier
import org.llmasr.minimal.diagnostics.RuntimeLogStore

/** Synthetic fixed-seven fixture using the real JSON/archive/session/repository implementations. */
class ModelZipImportTest {
    companion object {
    private fun requireThat(ok: Boolean, message: String) { if (!ok) throw AssertionError(message) }
    /** Catalog PreparedPackage discovery and actual cleanup ownership, tiny valid ZIP. */
    private fun catalogCleanup(args: Array<String>) {
      fun check(v:Boolean,s:String)=requireThat(v,s)
  val mode=args[0];val root=File(args[1]);root.mkdir();val files=File(root,"files");files.mkdir();val cache=File(root,"cache");cache.mkdir()
  val entries=(0..6).map{ModelEntry("f$it",1,ModelPackagePolicy.sha(byteArrayOf(it.toByte())))};val fixed=FixedModelPackage(ModelManifest(entries),"repair-fixture","mnn")
  val io=object:ModelCatalog.Io{
   var bytes:ByteArray?=null;var recovery:ByteArray?=null;val journals=HashMap<String,HashMap<String,ByteArray>>()
   override fun readCatalog()=bytes;override fun writeCatalog(b:ByteArray){bytes=b.clone()}
   override fun readRecovery()=recovery;override fun writeRecovery(b:ByteArray){recovery=b.clone()};override fun clearRecovery(){recovery=null};override fun restoreCatalog(b:ByteArray?){bytes=b?.clone()}
   override fun proof(key:String)=object:ModelVerification.Io{
    override fun identity(f:File,d:Boolean)=f.absolutePath+":"+f.length()+":"+f.lastModified()
    override fun exists(f:File)=f.exists();override fun read(n:String,l:Int)=journals[key]?.get(n)
    override fun write(n:String,b:ByteArray){journals.getOrPut(key){HashMap()}[n]=b.clone()}
   }
  }
  val resident=ModelResidency(object:ModelResidency.Native{override fun prepare(c:String,t:String)=0.0;override fun release(){}},{true})
  val projected=ModelReadiness();val catalog=ModelCatalog(fixed,files,cache,{Long.MAX_VALUE},resident,io,projected);catalog.load();val model=catalog.legacy()
  val zip=File(root,"tiny.zip");java.util.zip.ZipOutputStream(zip.outputStream()).use{out->
   fun put(n:String,b:ByteArray){val e=java.util.zip.ZipEntry(n);e.method=0;e.size=b.size.toLong();e.compressedSize=e.size;e.crc=java.util.zip.CRC32().also{it.update(b)}.value;out.putNextEntry(e);out.write(b);out.closeEntry()}
   put("model-package.json",ModelPackagePolicy.legacy(fixed).json());for(i in 0..6)put("f$i",byteArrayOf(i.toByte()))
  }
  val entered=CountDownLatch(1);val release=CountDownLatch(1);val thread=AtomicReference<Thread>();val error=AtomicReference<Throwable>()
  val owner=TaskCoordinator{job->Thread({try{job.run()}catch(t:Throwable){error.set(t)}},"repair-worker").also{thread.set(it);it.start()}}
  val state=ModelManagementState();val control=ModelOperationControl();val logs=RuntimeLogStore();val controller=ModelManagementController(owner,model.repository,model.readiness,control,state,logs);controller.bindCatalog(catalog)
  val pretransfer=mode.startsWith("cancel-");val closeCount=java.util.concurrent.atomic.AtomicInteger();val cancelObserved=java.util.concurrent.atomic.AtomicBoolean()
  val listener=ModelManagementState.Listener {
   val s=state.current()
   if(pretransfer&&s.operationKind==ModelManagementState.OperationKind.IMPORT&&controller.snapshotMatches(ModelDeployment.LEGACY)&&cancelObserved.compareAndSet(false,true))controller.requestCancel(s.activeOperationId)
  };state.addListener(listener)
  val source=object:ModelZipSource(Opener{zip.inputStream()},Supplier{files},Supplier{fixed}){
   override fun remove(file:File){if(mode=="remove")throw IOException("remove archive after finals");super.remove(file)}
   override fun archiveIo()=object:ModelZipArchive.Io(){override fun closeZip(z:java.util.zip.ZipFile){super.closeZip(z);closeCount.incrementAndGet();if(mode=="blocked"||mode=="cancel-blocked"){entered.countDown();check(release.await(5,TimeUnit.SECONDS),"close gate timeout")};if(mode=="close"||mode=="blocked"||mode=="cancel-throw"||mode=="cancel-blocked")throw IOException("after real ZIP close")}}
  }
  check(controller.startImport(source),"catalog ZIP admitted");val id=state.current().activeOperationId
  try{if(mode=="blocked"||mode=="cancel-blocked"){check(entered.await(5,TimeUnit.SECONDS),"actual untransferred archive close must run before terminal");check(owner.isBusy()&&control.isBusy&&!model.readiness.isReady&&!controller.startVerify()&&!state.current().isTerminalPhase,"blocked actual close must retain owner and no proof")}}
  finally{release.countDown();thread.get().join(10000)}
  check(!thread.get().isAlive&&error.get()==null&&!owner.isBusy(),"actual worker exited before cleanup")
  if(pretransfer){
   check(cancelObserved.get(),"cancel triggered at bound IMPORT listener")
   check(closeCount.get()==1,"untransferred prepared ZIP must close exactly once before terminal")
   check(state.current().phase==ModelManagementState.Phase.CANCELLED&&state.current().cleanupOutcome==(if(mode=="cancel-normal")ModelManagementState.Outcome.SUCCEEDED else ModelManagementState.Outcome.FAILED),"pretransfer cancellation cleanup truth before terminal phase=${state.current().phase} cleanup=${state.current().cleanupOutcome} error=${state.current().errorCode} parts=${state.current().orphanPartBytes}")
  }else check(state.current().phase==ModelManagementState.Phase.FAILED&&state.current().cleanupOutcome==ModelManagementState.Outcome.FAILED,"catalog source cleanup must be FAILED")
  check(!model.readiness.isReady,"failed source close must not publish proof")
  check(File(files,"model-zip-staging/archive.part").exists()==(mode=="remove"),"retained archive truth")
  check(logs.snapshot().count{it.requestId==id}==2,"one import terminal event")
  state.removeListener(listener)
  val retry=ModelZipSource(ModelZipSource.Opener{zip.inputStream()},Supplier{files},Supplier{fixed});check(controller.startImport(retry),"retry accepted");thread.get().join(10000)
  check(!thread.get().isAlive&&error.get()==null&&!owner.isBusy()&&model.readiness.isReady,"retry succeeds after actual cleanup failure")
  println("PASS A1 production catalog ZIP $mode stickycleanup/proof/owner/retry")
    }
    private fun mJson(entries:List<ModelEntry>):String="{\"files\":["+entries.joinToString(","){"{\"sha256\":\"${it.sha256()}\",\"bytes\":${it.bytes()},\"file\":\"${it.file()}\"}"}+"],\"engine\":\"mnn\",\"modelId\":\"fixture\",\"schema\":1}"
    private fun manifest(): ModelManifest = ModelManifest((0..6).map { i ->
        val data = ByteArray(3+i) { (65+i).toByte() }
        ModelEntry("f$i.bin", data.size.toLong(), MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) })
    })
    private fun rejected(message: String, action: () -> Unit) {
        var rejected = false
        try { action() } catch (_: IOException) { rejected = true }
        requireThat(rejected, message)
    }
    @JvmStatic fun main(args: Array<String>?) {
        if (args == null || args.isEmpty()) { smoke(); return }
        if(args[0]=="catalog-cleanup"){catalogCleanup(args.copyOfRange(1,args.size));return}
        val fixtures = File(args[0])
        if(args.getOrNull(1)?.startsWith("exit-")==true){cleanupExit(fixtures,args[1]);return}
        if(args.getOrNull(1)?.startsWith("u1-")==true){u1(fixtures,args[1]);return}
        if(args.getOrNull(1)?.startsWith("repair-")==true){repair(fixtures,args[1]);return}
        if (args.getOrNull(1)?.startsWith("crash-") == true || args.getOrNull(1)?.startsWith("recover-") == true) {
            crash(fixtures,args[1],File(args[2])); return
        }
        val manifest = manifest()
        val policy = FixedModelPackage(manifest, "fixture", "mnn")
        val jsonGood=File(fixtures, "good.json").readBytes()
        policy.verifyJson(jsonGood)
        val reversed=mJson(manifest.entries().reversed())
        policy.verifyJson(reversed.toByteArray())
        policy.verifyJson(jsonGood + ByteArray(FixedModelPackage.JSON_LIMIT-jsonGood.size) {32})
        rejected("ZIP manifest actual 16KiB+1 refused") {policy.verifyJson(jsonGood+ByteArray(FixedModelPackage.JSON_LIMIT-jsonGood.size+1){32})}
        for (bad in fixtures.listFiles()!!.filter { it.name.startsWith("bad-") })
            rejected("ZIP strict JSON must reject ${bad.name}") { policy.verifyJson(bad.readBytes()) }
        val replacement = File(fixtures,"good.json").readText().replace(manifest.entries()[0].sha256(), "0".repeat(64))
        rejected("ZIP trusted manifest cannot be replaced by package hashes") {policy.verifyJson(replacement.toByteArray())}
        rejected("ZIP fixed APK fingerprint cannot accept arbitrary fixture") { FixedModelPackage.mnn(manifest) }
        for (good in listOf("stored.zip", "deflated.zip")) {
            val exactLimit=FixedModelPackage(manifest,"fixture","mnn",File(fixtures,good).length())
            ModelZipArchive.prepare(File(fixtures,good),exactLimit,ModelZipArchive.Check{}).close()
            rejected("ZIP actual archive size upper boundary") {ModelZipArchive.prepare(File(fixtures,good),FixedModelPackage(manifest,"fixture","mnn",File(fixtures,good).length()-1),ModelZipArchive.Check{}).close()}
            ModelZipArchive.prepare(File(fixtures, good), policy, ModelZipArchive.Check { }).use { archive ->
                for (e in manifest.entries()) archive.open(e.file()).use { stream ->
                    requireThat(stream.readBytes().size.toLong() == e.bytes(), "ZIP exact output")
                }
            }
        }
        for (bad in fixtures.listFiles()!!.filter { it.extension == "zip" && it.name !in listOf("stored.zip", "deflated.zip") }.sortedBy { listOf("self-trust.zip","symlink.zip","local-name.zip","crc.zip").indexOf(it.name).let { i -> if(i<0)99 else i } }) {
            val diagnostic = when (bad.name) {
                "self-trust.zip" -> "ZIP trusted manifest cannot be replaced by package hashes"
                "symlink.zip" -> "ZIP CEN symbolic link must be refused before model mutation"
                "local-name.zip" -> "ZIP local and central names must agree"
                "crc.zip" -> "ZIP bad CRC cannot pass even with trusted SHA"
                else -> "ZIP malformed package must be refused ${bad.name}"
            }
            rejected(diagnostic) { ModelZipArchive.prepare(bad, policy, ModelZipArchive.Check { }).use { } }
        }
        val root = Files.createTempDirectory("zip-contract-").toFile()
        try {
            val model = File(root, "model")
            var dirty = 0
            var published = 0
            var cleaned = false
            val source = ModelZipSource(ModelZipSource.Opener { File(fixtures,"deflated.zip").inputStream() }, Supplier { root }, Supplier { policy })
            val repository = object : ModelRepository(manifest, model, SpaceProvider { Long.MAX_VALUE }, MutationGuard { dirty++ }) {
                override fun openPart(part:File):OutputStream { requireThat(dirty>0,"ZIP durable dirty must precede first model mutation");return super.openPart(part) }
                override fun publishPart(part: File, dest: File) { published++; super.publishPart(part,dest) }
            }
            val progress = object : ModelRepository.Progress {
                override fun update(stage: ModelRepository.Stage?, file: String?, i: Int, n: Int, done: Long, total: Long, sd: Long, st: Long, reused: Long) {
                    if (stage == ModelRepository.Stage.CLEANING_ARCHIVE) cleaned = true
                    if (stage == ModelRepository.Stage.FINAL_VERIFY) requireThat(cleaned && !File(root,"model-zip-staging/archive.part").exists(), "ZIP session cleanup must finish before final SHA and proof commit")
                }
            }
            repository.importFrom(source, progress)
            requireThat(published == 7 && dirty == 1, "ZIP durable dirty must precede first model mutation")
            repository.verifyAll()
            val before = manifest.entries().associate { it.file() to File(model,it.file()).readBytes().toList() }
            published = 0; dirty = 0; cleaned = false
            repository.importFrom(source, progress)
            requireThat(published == 0 && dirty == 0, "ZIP valid existing files reused without dirty or rename")
            val malformed = ModelZipSource(ModelZipSource.Opener { File(fixtures,"sha.zip").inputStream() }, Supplier { root }, Supplier { policy })
            rejected("ZIP malicious first pass must fail") { repository.importFrom(malformed, null) }
            requireThat(dirty == 0 && before == manifest.entries().associate { it.file() to File(model,it.file()).readBytes().toList() }, "ZIP first-pass rejection must preserve valid model bytes and mutation epoch")
            rejected("ZIP conservative low space must reject") {
                source.openSession(manifest, ModelRepository.SpaceProvider { policy.totalBytes + ModelRepository.SPACE_PAD - 1 }, null, ModelRepository.NEVER_CANCEL)
            }
            var actualWritten = 0
            val tiny = object : ModelZipSource(ModelZipSource.Opener { ByteArrayInputStream(ByteArray(128)) }, Supplier { root }, Supplier { FixedModelPackage(manifest,"fixture","mnn",127) }) {
                override fun create(file:File):OutputStream = object:FilterOutputStream(super.create(file)) {
                    override fun write(b:ByteArray,off:Int,len:Int) { actualWritten += len; out.write(b,off,len) }
                }
            }
            rejected("ZIP actual byte budget must stop before overflow write") { repository.importFrom(tiny, null) }
            requireThat(actualWritten <= 127,"ZIP actual byte budget must stop before overflow write")
            requireThat(!File(root,"model-zip-staging/archive.part").exists(), "ZIP failed prepare must clean archive")
        } finally { root.deleteRecursively() }
        if (args.getOrNull(1) != "quick") {
            faults(fixtures)
            owner(fixtures)
            proof(fixtures)
            arbitration(fixtures)
            for(mode in listOf("stale","existing","part","reserve","cleanup-fail","cleanup-cancel","cleanup-bad","double-close"))repair(fixtures,"repair-$mode")
            for(mode in listOf("input","output","both","same","error","part-fail","part-cancel","part-removed"))u1(fixtures,"u1-$mode")
            for(mode in listOf("stage","raf","raf-primary","raf-same","inflater","zip-cancel","session","entry","stale-retain","stale-removed","stale-second"))cleanupExit(fixtures,"exit-$mode")
        }
        println("PASS ModelZipImportTest parser/source/repository checkpoint")
    }
    private fun crash(fixtures:File, mode:String, root:File) {
        root.mkdirs()
        val m=manifest();val ready=ModelReadiness();val io=ModelAndroidHelpersTest.TestIo(File(root,"noBackup").toPath())
        val proof=ModelVerification(m,ready,io,ModelAndroidHelpersTest.MemoryGuard())
        var armed=false
        val boundary=mode.substringAfter('-')
        val repo=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof) {
            override fun openPart(part:File):OutputStream = object:FilterOutputStream(super.openPart(part)) {
                override fun write(b:ByteArray,off:Int,len:Int){out.write(b,off,len);if(armed&&boundary=="part"){File(root,"crash-marker").writeText(boundary);Runtime.getRuntime().halt(23)}}
            }
            override fun publishPart(part:File,dest:File){super.publishPart(part,dest);if(armed&&boundary=="rename"){File(root,"crash-marker").writeText(boundary);Runtime.getRuntime().halt(23)}}
        }
        fun import() {
            val queue=java.util.ArrayList<Runnable>();val owner=TaskCoordinator(java.util.concurrent.Executor{queue.add(it)})
            val state=ModelManagementState();val c=ModelManagementController(owner,repo,ready,ModelOperationControl(),state)
            val zip=object:ModelZipSource(ModelZipSource.Opener {File(fixtures,"stored.zip").inputStream()},Supplier{root},Supplier{FixedModelPackage(m,"fixture","mnn")}) {
                override fun create(file:File):OutputStream=object:FilterOutputStream(super.create(file)) {
                    override fun write(b:ByteArray,off:Int,len:Int){out.write(b,off,len);if(armed&&boundary=="archive"){File(root,"crash-marker").writeText(boundary);Runtime.getRuntime().halt(23)}}
                }
                override fun openSession(manifest:ModelManifest,space:ModelRepository.SpaceProvider,progress:ModelRepository.Progress?,cancel:ModelRepository.CancelGate):ModelSource.Session {
                    val session=super.openSession(manifest,space,progress,cancel)
                    if(armed&&boundary=="checked"){File(root,"crash-marker").writeText(boundary);Runtime.getRuntime().halt(23)}
                    return session
                }
            }
            requireThat(c.startImport(zip),"ZIP crash/recovery controller admission")
            queue.removeAt(0).run()
            requireThat(ready.isReady && state.current().phase==ModelManagementState.Phase.SUCCEEDED && !owner.isBusy(),"ZIP crash retry must finish real proof")
        }
        if(mode.startsWith("recover-")) {
            requireThat(File(root,"crash-marker").readText()==boundary,"ZIP actual crash checkpoint identity")
            var restored=false
            try {restored=proof.restoreFast(repo)}catch(_:IOException){}
            requireThat(restored == (boundary in listOf("archive","checked","committed")),"ZIP independent JVM crash proof qualification $boundary")
            import();repo.verifyAll()
            println("PASS independent JVM ZIP recovery $boundary")
            return
        }
        import()
        if(boundary !in listOf("archive","checked")) File(root,"model/f0.bin").delete()
        io.atStage={name,kind,_,stage ->
            if(armed&&stage=="afterSync" && ((boundary=="dirty"&&name=="ledger"&&kind==1) ||
                (boundary=="receipt"&&name=="receipt"&&kind==2) || (boundary=="committed"&&name=="ledger"&&kind==3))) {File(root,"crash-marker").writeText(boundary);Runtime.getRuntime().halt(23)}
        }
        armed=true
        import()
        throw AssertionError("ZIP crash boundary not reached $boundary")
    }
    private fun source(root: File, fixtures: File, opener: ModelZipSource.Opener? = null): ModelZipSource =
        ModelZipSource(opener ?: ModelZipSource.Opener { File(fixtures,"stored.zip").inputStream() }, Supplier { root }, Supplier { FixedModelPackage(manifest(),"fixture","mnn") })
    private fun faults(fixtures: File) {
        val root = Files.createTempDirectory("zip-faults-").toFile()
        try {
            val m = manifest()
            val repo = ModelRepository(m,File(root,"model"),ModelRepository.SpaceProvider { Long.MAX_VALUE })
            val sentinel = IOException("same-primary")
            for (kind in listOf("open","read","close","same","error")) {
                val fatal = AssertionError("ordinary Error")
                val opener = ModelZipSource.Opener {
                    if (kind=="open") throw sentinel
                    object : FilterInputStream(File(fixtures,"stored.zip").inputStream()) {
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (kind=="read" || kind=="same") throw sentinel
                            if (kind=="error") throw fatal
                            return super.read(b,off,len)
                        }
                        override fun close() { super.close(); if (kind=="close" || kind=="same") throw sentinel }
                    }
                }
                var caught: Throwable? = null
                try { repo.importFrom(source(root,fixtures,opener),null) } catch (t: Throwable) { caught=t }
                requireThat(caught === if (kind=="error") fatal else sentinel,"ZIP primary/self/ordinary Error identity preserved $kind")
                requireThat(!File(root,"model-zip-staging/archive.part").exists() && !File(root,"model").exists(),"ZIP prepare fault no model mutation and cleanup $kind")
            }
            val zero = ModelZipSource.Opener { object : ByteArrayInputStream(byteArrayOf(1)) { override fun read(b:ByteArray,off:Int,len:Int)=0 } }
            rejected("ZIP no-progress input refused") { repo.importFrom(source(root,fixtures,zero),null) }
            var now = 0L
            val expired = ModelZipSource(ModelZipSource.Opener { now = ModelZipSource.DEADLINE_NANOS; File(fixtures,"stored.zip").inputStream() },Supplier { root },Supplier { FixedModelPackage(m,"fixture","mnn") },ModelZipSource.Clock { now })
            rejected("ZIP monotonic deadline checked after real open returns") { repo.importFrom(expired,null) }
            val stage = File(root,"model-zip-staging")
            File(stage,"unknown").writeText("sentinel")
            rejected("ZIP unknown staging item retained") { repo.importFrom(source(root,fixtures),null) }
            requireThat(File(stage,"unknown").readText()=="sentinel","ZIP unknown stage unchanged")
            File(stage,"unknown").delete(); stage.delete()
            val outside=File(root,"outside"); outside.mkdir(); File(outside,"sentinel").writeText("keep")
            Files.createSymbolicLink(stage.toPath(),outside.toPath())
            rejected("ZIP staging symlink refused") { repo.importFrom(source(root,fixtures),null) }
            requireThat(File(outside,"sentinel").readText()=="keep","ZIP symlink external sentinel unchanged")
            Files.delete(stage.toPath()); stage.mkdir()
            Files.createSymbolicLink(File(stage,"archive.part").toPath(),File(outside,"sentinel").toPath())
            rejected("ZIP leaf symlink refused") { repo.importFrom(source(root,fixtures),null) }
            Files.delete(File(stage,"archive.part").toPath())
            // Default directory session must retain identical read/close failures too.
            val directory=object:ModelSource {
                override fun enumerate(expected:MutableCollection<String?>?,maxEntries:Int)=m.entries().associateTo(LinkedHashMap<String?,ModelSource.UriRef?>()){it.file() to ModelSource.UriRef(it.file())}
                override fun open(ref:ModelSource.UriRef?)=object:ByteArrayInputStream(byteArrayOf(1)) {
                    override fun read(b:ByteArray,off:Int,len:Int):Int {throw sentinel}
                    override fun close(){throw sentinel}
                }
            }
            var directoryFailure:Throwable?=null
            try{repo.importFrom(directory,null)}catch(t:Throwable){directoryFailure=t}
            requireThat(directoryFailure===sentinel && !File(root,"model/f0.bin.part").exists(),"ZIP compatible directory primary self-suppression preserved")
            File(root,"model").delete()
            val cleanup = IOException("cleanup")
            val failDelete = object : ModelZipSource(ModelZipSource.Opener { throw sentinel },Supplier { root },Supplier { FixedModelPackage(m,"fixture","mnn") }) {
                override fun remove(file:File) { throw cleanup }
            }
            var primary: Throwable?=null
            try { repo.importFrom(failDelete,null) } catch(t:Throwable) { primary=t }
            requireThat(primary===sentinel && primary.suppressed.any { it===cleanup } && File(stage,"archive.part").exists(),"ZIP cleanup failure suppressed and residue retained")
            File(stage,"archive.part").delete()
            val cleanupOnly=object:ModelZipSource(ModelZipSource.Opener{File(fixtures,"stored.zip").inputStream()},Supplier{root},Supplier{FixedModelPackage(m,"fixture","mnn")}) {
                override fun remove(file:File){throw cleanup}
            }
            val prepared=cleanupOnly.openSession(m,ModelRepository.SpaceProvider{Long.MAX_VALUE},null,ModelRepository.NEVER_CANCEL)
            var closeFailure:Throwable?=null
            try{prepared.close()}catch(t:Throwable){closeFailure=t}
            requireThat(closeFailure===cleanup && File(stage,"archive.part").exists(),"ZIP session cleanup failure never success")
            File(stage,"archive.part").delete()
            for (failure in listOf("create","write","close")) {
                val bad = object : ModelZipSource(ModelZipSource.Opener { File(fixtures,"stored.zip").inputStream() },Supplier { root },Supplier { FixedModelPackage(m,"fixture","mnn") }) {
                    override fun create(file:File):OutputStream {
                        if(failure=="create") throw IOException("create")
                        return object:FilterOutputStream(super.create(file)) {
                            override fun write(b:ByteArray,off:Int,len:Int) { if(failure=="write") throw IOException("write"); out.write(b,off,len) }
                            override fun close() { super.close(); if(failure=="close") throw IOException("close") }
                        }
                    }
                }
                rejected("ZIP archive output $failure refuses") { repo.importFrom(bad,null) }
                requireThat(!File(stage,"archive.part").exists(),"ZIP output failure cleanup $failure")
            }
            val enough=policyReserve(m)
            source(root,fixtures).openSession(m,ModelRepository.SpaceProvider{enough+65536},null,ModelRepository.NEVER_CANCEL).close()
            var finalTime=0L
            val finalTimeoutSource=ModelZipSource(ModelZipSource.Opener{File(fixtures,"stored.zip").inputStream()},Supplier{root},Supplier{FixedModelPackage(m,"fixture","mnn")},ModelZipSource.Clock{finalTime})
            val finalTimeoutProgress=object:ModelRepository.Progress {
                override fun update(stage:ModelRepository.Stage?,name:String?,i:Int,n:Int,d:Long,t:Long,sd:Long,st:Long,r:Long){if(stage==ModelRepository.Stage.FINAL_VERIFY)finalTime=ModelZipSource.DEADLINE_NANOS}
            }
            rejected("ZIP deadline still enforced after session cleanup during final SHA") {repo.importFrom(finalTimeoutSource,finalTimeoutProgress)}
            requireThat(!File(stage,"archive.part").exists(),"ZIP deadline after cleanup leaves no archive")
            repo.deleteAll()
            var spacePhase=0
            val phaseProgress=object:ModelRepository.Progress {
                override fun update(stage:ModelRepository.Stage?,name:String?,i:Int,n:Int,d:Long,t:Long,sd:Long,st:Long,r:Long){if(stage==ModelRepository.Stage.ENUMERATING)spacePhase=1}
            }
            val afterArchive=ModelRepository(m,File(root,"model"),ModelRepository.SpaceProvider{if(spacePhase==0)Long.MAX_VALUE else enough-1})
            rejected("ZIP after archive C+PAD budget refusal") {afterArchive.importFrom(source(root,fixtures),phaseProgress)}
            requireThat(!File(root,"model/f0.bin").exists()&&!File(stage,"archive.part").exists(),"ZIP postarchive space refusal no publication")
            var checks=0
            val dropping=ModelRepository.SpaceProvider { if(++checks < 2) Long.MAX_VALUE else 0 }
            rejected("ZIP dynamic archive space refusal") { source(root,fixtures).openSession(m,dropping,null,ModelRepository.NEVER_CANCEL) }
            requireThat(!File(stage,"archive.part").exists(),"ZIP dynamic space cleanup")
        } finally { root.deleteRecursively() }
    }
    private fun policyReserve(m:ModelManifest):Long=m.entries().sumOf{it.bytes()}+ModelRepository.SPACE_PAD
    private fun owner(fixtures:File) {
        for (mode in listOf("open","read","close","cleanup")) {
            val root=Files.createTempDirectory("zip-blocked-").toFile()
            val entered=CountDownLatch(1);val release=CountDownLatch(1);val exited=CountDownLatch(1)
            val error=AtomicReference<Throwable>();val worker=AtomicReference<Thread>()
            fun block() { entered.countDown(); if(!release.await(5,TimeUnit.SECONDS)) throw IOException("gate timeout") }
            val executor=java.util.concurrent.Executor { task -> Thread({try {task.run()} catch(t:Throwable){error.set(t)} finally {exited.countDown()}},"zip-worker").also{worker.set(it);it.start()} }
            val owner=TaskCoordinator(executor); val ready=ModelReadiness();val control=ModelOperationControl();val state=ModelManagementState()
            val repo=ModelRepository(manifest(),File(root,"model"),ModelRepository.SpaceProvider {Long.MAX_VALUE})
            val c=ModelManagementController(owner,repo,ready,control,state)
            val zip=object:ModelZipSource(ModelZipSource.Opener {
                if(mode=="open") block()
                object:FilterInputStream(File(fixtures,"stored.zip").inputStream()) {
                    var first=true
                    override fun read(b:ByteArray,off:Int,len:Int):Int {if(mode=="read"&&first){first=false;block()};return super.read(b,off,len)}
                    override fun close(){if(mode=="close")block();super.close()}
                }
            },Supplier{root},Supplier{FixedModelPackage(manifest(),"fixture","mnn")}) {
                override fun remove(file:File) { if(mode=="cleanup") block();super.remove(file) }
            }
            try {
                requireThat(c.startImport(zip),"ZIP blocked fixture admitted")
                requireThat(entered.await(3,TimeUnit.SECONDS),"ZIP gate entered $mode")
                val id=control.peekActive().id;c.requestCancel(id)
                requireThat(owner.isBusy() && control.isBusy && state.current().phase==ModelManagementState.Phase.CANCELLING && !c.startVerify() && !c.startImport(zip),"ZIP blocked provider close retains shared owner and archive")
                requireThat(File(root,"model-zip-staging/archive.part").exists() && !ready.isReady,"ZIP live IO archive retained and no READY")
            } finally {
                release.countDown()
                requireThat(exited.await(5,TimeUnit.SECONDS),"ZIP actual worker exit before fixture cleanup")
                worker.get()?.join(1000)
                requireThat(worker.get()?.isAlive!=true,"ZIP worker actual join before fixture cleanup")
                if(!owner.isBusy()) root.deleteRecursively()
            }
            requireThat(error.get()==null && !owner.isBusy(),"ZIP worker Throwable/owner exit $mode ${error.get()}")
        }
    }
    private fun proof(fixtures:File) {
        val root=Files.createTempDirectory("zip-proof-").toFile()
        try {
            val m=manifest();val ready=ModelReadiness()
            val io=ModelAndroidHelpersTest.TestIo(File(root,"noBackup").toPath())
            val proof=ModelVerification(m,ready,io,ModelAndroidHelpersTest.MemoryGuard())
            val repo=ModelRepository(m,File(root,"model"),ModelRepository.SpaceProvider{Long.MAX_VALUE},proof)
            val queue=java.util.ArrayList<Runnable>();val owner=TaskCoordinator(java.util.concurrent.Executor {queue.add(it)})
            val control=ModelOperationControl();val state=ModelManagementState();val c=ModelManagementController(owner,repo,ready,control,state)
            requireThat(c.startImport(source(root,fixtures)),"ZIP production controller import")
            queue.removeAt(0).run()
            requireThat(ready.isReady && state.current().phase==ModelManagementState.Phase.SUCCEEDED && !owner.isBusy(),"ZIP final SHA proof committed")
            val ledger=File(root,"noBackup/ledger").readBytes();val receipt=File(root,"noBackup/receipt").readBytes();val epoch=ready.epoch()
            val identities=m.entries().associate {it.file() to io.identity(File(root,"model/${it.file()}"),false)}
            requireThat(c.startImport(source(root,fixtures,ModelZipSource.Opener{File(fixtures,"sha.zip").inputStream()})),"ZIP bad package admitted for worker validation")
            queue.removeAt(0).run()
            requireThat(ready.isReady && ready.epoch()==epoch && ledger.contentEquals(File(root,"noBackup/ledger").readBytes()) && receipt.contentEquals(File(root,"noBackup/receipt").readBytes()),"ZIP bad first pass preserves exact valid proof and epoch")
            requireThat(identities==m.entries().associate {it.file() to io.identity(File(root,"model/${it.file()}"),false)},"ZIP bad package preserves valid inode/bytes identities")
            val fresh=ModelVerification(m,ModelReadiness(),io,ModelAndroidHelpersTest.MemoryGuard())
            requireThat(fresh.restoreFast(repo),"ZIP fresh proof object restores")
            File(root,"model/f0.bin").delete()
            io.fail="open"
            rejected("ZIP durable dirty failure stops part creation") {repo.importFrom(source(root,fixtures),null)}
            requireThat(!File(root,"model/f0.bin.part").exists() && !File(root,"model/f0.bin").exists(),"ZIP durable dirty must precede first model mutation")
            io.fail=null
            repo.importFrom(source(root,fixtures),null);repo.verifyAll()
            // Partial publication is explicitly recoverable, never an all-or-none promise.
            File(root,"model/f0.bin").delete();File(root,"model/f1.bin").delete()
            val inode=io.identity(File(root,"model/f6.bin"),false)
            val partial=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof) {
                override fun publishPart(part:File,dest:File) {if(dest.name=="f1.bin")throw IOException("publish failure");super.publishPart(part,dest)}
            }
            rejected("ZIP publication failure is not success") {partial.importFrom(source(root,fixtures),null)}
            requireThat(File(root,"model/f0.bin").isFile && !File(root,"model/f1.bin").exists() && !File(root,"model/f1.bin.part").exists() && !ready.isReady,"ZIP partial correct final retained and DIRTY unready")
            requireThat(io.identity(File(root,"model/f6.bin"),false)==inode,"ZIP reused final inode retained through partial failure")
            var restored=false;try{restored=fresh.restoreFast(repo)}catch(_:IOException){}
            requireThat(!restored,"ZIP DIRTY partial proof cannot restore READY")
            repo.importFrom(source(root,fixtures),null);repo.verifyAll()
            for(kind in listOf("open","write","close","verify","publish")) {
                File(root,"model/f0.bin").delete()
                val bad=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof) {
                    override fun openPart(part:File):OutputStream {
                        if(kind=="open")throw IOException("part open")
                        return object:FilterOutputStream(super.openPart(part)) {
                            override fun write(b:ByteArray,off:Int,len:Int){if(kind=="write")throw IOException("part write");out.write(b,off,len)}
                            override fun close(){super.close();if(kind=="close")throw IOException("part close");if(kind=="verify")part.writeBytes(ByteArray(3){0})}
                        }
                    }
                    override fun publishPart(part:File,dest:File){if(kind=="publish")throw IOException("rename");super.publishPart(part,dest)}
                }
                rejected("ZIP repository $kind failure refuses") {bad.importFrom(source(root,fixtures),null)}
                requireThat(!File(root,"model/f0.bin").exists() && !File(root,"model/f0.bin.part").exists() && !ready.isReady,"ZIP part $kind failure no false publication")
                repo.importFrom(source(root,fixtures),null)
            }
        } finally {root.deleteRecursively()}
    }
    private fun arbitration(fixtures:File) {
        for(boundary in listOf("before-rename","reserved-rename","before-proof","reserved-proof")) {
            val root=Files.createTempDirectory("zip-arbitrate-").toFile()
            try {
                val m=manifest();val ready=ModelReadiness();val control=ModelOperationControl();val state=ModelManagementState()
                val io=ModelAndroidHelpersTest.TestIo(File(root,"noBackup").toPath())
                val proof=ModelVerification(m,ready,io,ModelAndroidHelpersTest.MemoryGuard())
                var cancelAccepted=false
                fun cancel(){cancelAccepted=control.requestCancel(control.peekActive().id)}
                val repo=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof) {
                    override fun publishPart(part:File,dest:File) {
                        if(boundary=="reserved-rename"&&dest.name=="f0.bin")cancel()
                        super.publishPart(part,dest)
                    }
                    override fun verifyAll(progress:Progress?,gate:CancelGate?) {
                        val wrapped=object:Progress {
                            override fun update(stage:Stage?,name:String?,index:Int,count:Int,done:Long,total:Long,sd:Long,st:Long,reused:Long){
                                progress?.update(stage,name,index,count,done,total,sd,st,reused)
                            }
                        }
                        super.verifyAll(wrapped,gate)
                        if(boundary=="before-proof")cancel()
                    }
                    override fun importFrom(source:ModelSource?,progress:Progress?,gate:CancelGate?) {
                        val wrapped=object:Progress {
                            override fun planned(plan:ModelReports.ImportPlan?){progress?.planned(plan)}
                            override fun fileResult(name:String?,result:ModelReports.FileOutcome?){progress?.fileResult(name,result)}
                            override fun update(stage:Stage?,name:String?,index:Int,count:Int,done:Long,total:Long,sd:Long,st:Long,reused:Long){
                                progress?.update(stage,name,index,count,done,total,sd,st,reused)
                                if(boundary=="before-rename"&&stage==Stage.PUBLISHING)cancel()
                            }
                        }
                        super.importFrom(source,wrapped,gate)
                    }
                }
                if(boundary=="reserved-proof")io.atStage={name,kind,_,stage->if(name=="receipt"&&kind==2&&stage=="write")cancel()}
                val queue=java.util.ArrayList<Runnable>();val owner=TaskCoordinator(java.util.concurrent.Executor{queue.add(it)})
                val c=ModelManagementController(owner,repo,ready,control,state)
                requireThat(c.startImport(source(root,fixtures)),"ZIP arbitration admission")
                queue.removeAt(0).run()
                requireThat(!owner.isBusy(),"ZIP arbitration real owner finalization")
                if(boundary=="reserved-proof")requireThat(!cancelAccepted&&ready.isReady&&state.current().phase==ModelManagementState.Phase.SUCCEEDED,"ZIP proof reservation wins late cancel")
                else {
                    requireThat(cancelAccepted&&!ready.isReady&&state.current().phase==ModelManagementState.Phase.CANCELLED,"ZIP cancel before proof cannot succeed")
                    if(boundary=="before-rename")requireThat(!File(root,"model/f0.bin").exists(),"ZIP cancel before rename publishes nothing")
                    if(boundary=="reserved-rename")requireThat(File(root,"model/f0.bin").isFile&&!File(root,"model/f1.bin").exists(),"ZIP reserved rename remains historical fact after cancel")
                }
            } finally {root.deleteRecursively()}
        }
    }
    private fun repair(fixtures:File, mode:String) {
        val root=Files.createTempDirectory("zip-repair-").toFile()
        val m=manifest()
        try {
            when(mode) {
                "repair-stale" -> {
                    val stage=File(root,"model-zip-staging");stage.mkdir();val archive=File(stage,"archive.part");archive.writeBytes(ByteArray(64))
                    var reclaimed=false
                    val space=ModelRepository.SpaceProvider{if(!archive.exists())reclaimed=true;if(reclaimed)Long.MAX_VALUE else 0}
                    val repo=ModelRepository(m,File(root,"model"),space)
                    var failure:Throwable?=null
                    try{repo.importFrom(source(root,fixtures),null)}catch(t:Throwable){failure=t}
                    requireThat(failure==null && !archive.exists(),"ZIP stale archive must reclaim before initial space reservation")
                    repo.verifyAll()
                    val sentinel=File(stage,"unknown");sentinel.writeText("keep");archive.writeBytes(byteArrayOf(1))
                    val cleanup=ArrayList<Boolean>()
                    val progress=object:ModelRepository.Progress {
                        override fun update(stage:ModelRepository.Stage?,name:String?,i:Int,n:Int,d:Long,t:Long,sd:Long,st:Long,r:Long){}
                        override fun sourceCleanup(succeeded:Boolean){cleanup.add(succeeded)}
                    }
                    rejected("ZIP unknown stage still refuses reclaim") {repo.importFrom(source(root,fixtures),progress)}
                    requireThat(sentinel.readText()=="keep" && archive.exists(),"ZIP unknown staging sentinel and old archive retained")
                    requireThat(cleanup==listOf(false),"ZIP unknown-stage listing close cannot imply stale archive cleanup")
                    sentinel.delete();archive.delete();val outside=File(root,"outside");outside.writeText("external")
                    Files.createSymbolicLink(archive.toPath(),outside.toPath())
                    rejected("ZIP stale symlink refused") {repo.importFrom(source(root,fixtures),null)}
                    requireThat(outside.readText()=="external","ZIP stale symlink external sentinel retained")
                }
                "repair-existing","repair-part","repair-reserve" -> {
                    var now=0L;var renamed=0;var cleaned=false;var lateDiskBytes=0L
                    val ready=ModelReadiness();val io=ModelAndroidHelpersTest.TestIo(File(root,"noBackup").toPath());val proof=ModelVerification(m,ready,io,ModelAndroidHelpersTest.MemoryGuard())
                    val repo=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof){override fun publishPart(part:File,dest:File){renamed++;super.publishPart(part,dest)}}
                    if(mode=="repair-existing"){repo.importFrom(source(root,fixtures),null);renamed=0}
                    val target=when(mode){"repair-existing"->ModelRepository.Stage.CHECKING_EXISTING;"repair-part"->ModelRepository.Stage.VERIFYING_FILE;else->ModelRepository.Stage.PUBLISHING}
                    val progress=object:ModelRepository.Progress {
                        override fun update(stage:ModelRepository.Stage?,name:String?,i:Int,n:Int,d:Long,t:Long,sd:Long,st:Long,r:Long){if(stage==target){now=ModelZipSource.DEADLINE_NANOS;lateDiskBytes+=d};if(stage==ModelRepository.Stage.CLEANING_ARCHIVE)cleaned=true}
                    }
                    val zip=ModelZipSource(ModelZipSource.Opener{File(fixtures,"stored.zip").inputStream()},Supplier{root},Supplier{FixedModelPackage(m,"fixture","mnn")},ModelZipSource.Clock{now})
                    val beforeWrites=io.writes.get();var failure:Throwable?=null
                    try{repo.importFrom(zip,progress)}catch(t:Throwable){failure=t}
                    requireThat(failure is IOException && renamed==0 && lateDiskBytes==0L && cleaned && !File(root,"model-zip-staging/archive.part").exists(),"ZIP session deadline must precede disk verification and publish reservation $mode")
                    if(mode=="repair-existing")requireThat(io.writes.get()==beforeWrites,"ZIP deadline during existing SHA must not dirty proof")
                    requireThat(!ready.isReady,"ZIP deadline never creates proof")
                    // A new direct mutation must be admitted only after actual source cleanup.
                    proof.beginMutation().close()
                    now=0L;cleaned=false;renamed=0;lateDiskBytes=0
                    val ownedRepo=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof){
                        override fun publishPart(part:File,dest:File){renamed++;super.publishPart(part,dest)}
                        override fun importFrom(source:ModelSource?,parent:Progress?,gate:CancelGate?){
                            val forwarding=object:Progress {
                                                                override fun planned(plan:ModelReports.ImportPlan?){parent?.planned(plan)}
                                override fun fileResult(name:String?,outcome:ModelReports.FileOutcome?){parent?.fileResult(name,outcome)}
                                override fun update(stage:Stage?,name:String?,i:Int,n:Int,d:Long,t:Long,sd:Long,st:Long,r:Long){progress.update(stage,name,i,n,d,t,sd,st,r);parent?.update(stage,name,i,n,d,t,sd,st,r)}
                            }
                            super.importFrom(source,forwarding,gate)
                        }
                    }
                    val queue=java.util.ArrayList<Runnable>();val owner=TaskCoordinator(java.util.concurrent.Executor{queue.add(it)});val state=ModelManagementState()
                    val controller=ModelManagementController(owner,ownedRepo,ready,ModelOperationControl(),state)
                    requireThat(controller.startImport(zip)&&owner.isBusy(),"ZIP deadline owner admitted")
                    queue.removeAt(0).run()
                    requireThat(!owner.isBusy()&&cleaned&&renamed==0&&lateDiskBytes==0L&&state.current().phase==ModelManagementState.Phase.FAILED&&!ready.isReady,"ZIP deadline cleanup precedes shared owner release and no proof")
                }
                "repair-cleanup-fail","repair-cleanup-cancel","repair-cleanup-bad" -> repairCleanup(fixtures,root,mode)
                "repair-double-close" -> {
                    for(same in listOf(false,true)) {
                        val first=IOException("output close");val second:Throwable=if(same)first else AssertionError("input close")
                        var inputClosed=0;var outputClosed=0;var renamed=0
                        val directory=object:ModelSource {
                            override fun enumerate(expected:MutableCollection<String?>?,maxEntries:Int)=m.entries().associateTo(LinkedHashMap<String?,ModelSource.UriRef?>()){it.file() to ModelSource.UriRef(it.file())}
                            override fun open(ref:ModelSource.UriRef?)=object:ByteArrayInputStream(ByteArray(3){65}){override fun close(){inputClosed++;throw second}}
                        }
                        val repo=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE}) {
                            override fun openPart(part:File):OutputStream=object:FilterOutputStream(super.openPart(part)){override fun close(){out.close();outputClosed++;throw first}}
                            override fun publishPart(part:File,dest:File){renamed++;super.publishPart(part,dest)}
                        }
                        var failure:Throwable?=null;try{repo.importFrom(directory,null)}catch(t:Throwable){failure=t}
                        requireThat(failure===first && inputClosed==1 && outputClosed==1 && renamed==0 && !File(root,"model/f0.bin.part").exists(),"ZIP distinct/same double close preserves primary and safe part")
                        requireThat(if(same)first.suppressed.isEmpty() else first.suppressed.size==1&&first.suppressed[0]===second,"ZIP double close suppressed Error without self suppression")
                    }
                }
                else -> throw AssertionError(mode)
            }
        } finally {if(!File(root,"retain-live-worker").exists())root.deleteRecursively()}
        println("PASS $mode")
    }
    private fun repairCleanup(fixtures:File,root:File,mode:String) {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val error=AtomicReference<Throwable>();val worker=AtomicReference<Thread>()
        val executor=java.util.concurrent.Executor{task->Thread({try{task.run()}catch(t:Throwable){error.set(t)}},"zip-cleanup-repair").also{worker.set(it);it.start()}}
        val ready=ModelReadiness();val control=ModelOperationControl();val state=ModelManagementState();val owner=TaskCoordinator(executor)
        val logs=org.llmasr.minimal.diagnostics.RuntimeLogStore()
        val repo=ModelRepository(manifest(),File(root,"model"),ModelRepository.SpaceProvider{Long.MAX_VALUE})
        val controller=ModelManagementController(owner,repo,ready,control,state,logs)
        val zip=object:ModelZipSource(ModelZipSource.Opener{File(fixtures,if(mode=="repair-cleanup-bad")"sha.zip" else "stored.zip").inputStream()},Supplier{root},Supplier{FixedModelPackage(manifest(),"fixture","mnn")}){
            override fun remove(file:File){
                if(mode!="repair-cleanup-bad"){entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw IOException("cleanup gate timeout")}
                if(mode=="repair-cleanup-fail")throw IOException("delete failed")
                super.remove(file)
            }
        }
        var liveChecked=false
        val retained=File(root,"retain-live-worker");retained.writeText("owned worker exit pending")
        requireThat(controller.startImport(zip),"ZIP repair cleanup admitted")
        val id=state.current().activeOperationId
        try {
            if(mode!="repair-cleanup-bad") {
                requireThat(entered.await(3,TimeUnit.SECONDS),"ZIP repair cleanup gate entered")
                controller.requestCancel(id)
                liveChecked=owner.isBusy()&&control.isBusy&&File(root,"model-zip-staging/archive.part").exists()&&state.current().phase==ModelManagementState.Phase.CANCELLING&&!controller.startVerify()&&!controller.startImport(zip)
            }
        } finally {release.countDown();worker.get()!!.join(5000)}
        requireThat(!worker.get()!!.isAlive,"ZIP repair cleanup actual worker exit before directory cleanup")
        retained.delete()
        requireThat(error.get()==null&&!owner.isBusy(),"ZIP repair cleanup real worker exits")
        val s=state.current();val fail=mode=="repair-cleanup-fail"
        requireThat(s.phase==(if(mode=="repair-cleanup-bad")ModelManagementState.Phase.FAILED else ModelManagementState.Phase.CANCELLED) && s.cleanupOutcome==(if(fail)ModelManagementState.Outcome.FAILED else ModelManagementState.Outcome.SUCCEEDED) && File(root,"model-zip-staging/archive.part").exists()==fail && (mode=="repair-cleanup-bad"||liveChecked),"ZIP source cleanup result must be independent of cancelling or failure phase $mode")
        requireThat(ModelUiText.task(s).contains("清理未完全成功")==fail,"ZIP cleanup UI reports actual archive residue")
        val pair=logs.snapshot().filter{it.requestId==id}
        requireThat(pair.size==2&&pair[0].kind==org.llmasr.minimal.diagnostics.RuntimeLogEventKind.MODEL_IMPORT_STARTED&&pair[1].kind==(if(mode=="repair-cleanup-bad")org.llmasr.minimal.diagnostics.RuntimeLogEventKind.MODEL_IMPORT_FAILED else org.llmasr.minimal.diagnostics.RuntimeLogEventKind.MODEL_IMPORT_CANCELLED),"ZIP repair cleanup exactly one admitted/terminal log pair")
    }
    private fun u1(fixtures:File,mode:String) {
        val root=Files.createTempDirectory("zip-u1-").toFile();val release=CountDownLatch(1);val entered=CountDownLatch(1)
        val worker=AtomicReference<Thread>();val error=AtomicReference<Throwable>();var inputClosed=0;var outputClosed=0
        val primary=IOException("first resource close");val secondary=LinkageError("other resource close")
        try {
            val m=manifest();val ready=ModelReadiness();val control=ModelOperationControl();val state=ModelManagementState()
            val io=ModelAndroidHelpersTest.TestIo(File(root,"noBackup").toPath());val proof=ModelVerification(m,ready,io,ModelAndroidHelpersTest.MemoryGuard())
            var written=false;var openedPart=false;var renamed=0;var seen:Throwable?=null
            val repo=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof) {
                override fun openPart(part:File):OutputStream {
                    openedPart=true
                    return object:FilterOutputStream(super.openPart(part)) {
                        override fun write(b:ByteArray,off:Int,len:Int){
                            if(mode=="u1-part-fail"||mode=="u1-part-removed")throw IOException("before first write")
                            out.write(b,off,len);written=true
                        }
                    }.also {if(mode=="u1-part-cancel")controllerCancel(control)}
                }
                override fun deleteManaged(file:File){if(mode.startsWith("u1-part")&&file.name.endsWith(".part")&&openedPart){if(mode=="u1-part-removed")super.deleteManaged(file);throw IOException("model part delete")};super.deleteManaged(file)}
                override fun publishPart(part:File,dest:File){renamed++;super.publishPart(part,dest)}
                override fun importFrom(source:ModelSource?,progress:Progress?,cancel:CancelGate?){try{super.importFrom(source,progress,cancel)}catch(t:Throwable){seen=t;throw t}}
            }
            val owner=TaskCoordinator(java.util.concurrent.Executor{task->Thread({try{task.run()}catch(t:Throwable){error.set(t)}},"zip-u1-close").also{worker.set(it);it.start()}})
            val controller=ModelManagementController(owner,repo,ready,control,state)
            val zip=object:ModelZipSource(ModelZipSource.Opener {
                object:FilterInputStream(File(fixtures,"stored.zip").inputStream()) {
                    override fun close(){super.close();inputClosed++;if(mode in listOf("u1-input","u1-both","u1-same","u1-error")){entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw IOException("close timeout");if(mode=="u1-error")throw secondary;throw primary}}
                }
            },Supplier{root},Supplier{FixedModelPackage(m,"fixture","mnn")}) {
                override fun create(file:File):OutputStream=object:FilterOutputStream(super.create(file)) {
                    override fun write(b:ByteArray,off:Int,len:Int){out.write(b,off,len)}
                    override fun close(){super.close();outputClosed++;if(mode=="u1-output"){entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw IOException("close timeout");throw primary};if(mode=="u1-both")throw secondary;if(mode=="u1-same")throw primary}
                }
            }
            val retained=File(root,"retain-live-worker");retained.writeText("exit pending")
            requireThat(controller.startImport(zip),"ZIP U1 close/part admitted")
            var held=true
            try {
                if(!mode.startsWith("u1-part")) {requireThat(entered.await(3,TimeUnit.SECONDS),"ZIP U1 resource close entered");held=owner.isBusy()&&control.isBusy&&File(root,"model-zip-staging/archive.part").exists()&&!controller.startVerify()}
            }finally{release.countDown();worker.get()!!.join(5000)}
            requireThat(!worker.get()!!.isAlive,"ZIP U1 worker actual exit before fixture cleanup");retained.delete()
            val s=state.current();val part=File(root,"model/f0.bin.part")
            if(mode.startsWith("u1-part")) {
                requireThat(openedPart&&!written&&(if(mode=="u1-part-removed")!part.exists() else part.isFile&&part.length()==0L)&&renamed==0&&!ready.isReady&&!File(root,"model-zip-staging/archive.part").exists()&&!owner.isBusy()&&s.cleanupOutcome==ModelManagementState.Outcome.FAILED,"ZIP successful archive cleanup cannot hide zero-byte model part failure $mode")
                requireThat(s.phase==(if(mode=="u1-part-cancel")ModelManagementState.Phase.CANCELLED else ModelManagementState.Phase.FAILED),"ZIP empty part terminal cancellation semantics")
            }else {
                requireThat(held&&!owner.isBusy()&&!File(root,"model-zip-staging/archive.part").exists()&&s.phase==ModelManagementState.Phase.FAILED&&s.cleanupOutcome==ModelManagementState.Outcome.FAILED,"ZIP prepare resource close failure must report cleanup FAILED $mode")
                requireThat(inputClosed==1&&outputClosed==1&&renamed==0&&!ready.isReady,"ZIP U1 both resources close before release")
                requireThat(seen===(if(mode=="u1-error")secondary else primary),"ZIP U1 close primary identity")
                if(mode=="u1-both")requireThat(primary.suppressed.size==1&&primary.suppressed[0]===secondary,"ZIP U1 distinct close Error suppressed")
                if(mode=="u1-same")requireThat(primary.suppressed.isEmpty(),"ZIP U1 same close no self suppression")
            }
            requireThat(ModelUiText.task(s).contains("清理未完全成功"),"ZIP U1 terminal UI cleanup warning")
            requireThat(error.get()==null,"ZIP U1 worker Throwable unexpectedly escaped")
        }finally{release.countDown();if(!File(root,"retain-live-worker").exists())root.deleteRecursively()}
        println("PASS $mode")
    }
    private fun controllerCancel(control:ModelOperationControl){requireThat(control.requestCancel(control.peekActive().id),"ZIP zero part cancel wins before writing")}
    private fun cleanupExit(fixtures:File,mode:String) {
        if(mode=="exit-notify") {
            val primary=IOException("close primary");val notify=LinkageError("notify secondary");var failed=false
            val check=object:ModelZipArchive.Check {override fun run(){};override fun cleanupFailed(){failed=true;throw notify}}
            val io=object:ModelZipArchive.Io() {override fun closeRandom(resource:java.io.RandomAccessFile){super.closeRandom(resource);throw primary}}
            var seen:Throwable?=null;try{ModelZipArchive.prepare(File(fixtures,"stored.zip"),FixedModelPackage(manifest(),"fixture","mnn"),check,io)}catch(t:Throwable){seen=t}
            requireThat(failed&&seen===primary&&primary.suppressed.size==1&&primary.suppressed[0]===notify,"ZIP cleanup failure notification preserves original close primary")
            println("PASS $mode");return
        }
        val root=Files.createTempDirectory("zip-exit-").toFile();val entered=CountDownLatch(1);val release=CountDownLatch(1);val thread=AtomicReference<Thread>();val error=AtomicReference<Throwable>()
        val first=IOException("resource close");val closeError=LinkageError("internal close error");val control=ModelOperationControl();val ready=ModelReadiness();val state=ModelManagementState();var realClosed=0;var seen:Throwable?=null;var plannedCount=0
        try {
            val m=manifest();val io=ModelAndroidHelpersTest.TestIo(File(root,"noBackup").toPath());val proof=ModelVerification(m,ready,io,ModelAndroidHelpersTest.MemoryGuard())
            if(mode.startsWith("exit-stale")){File(root,"model").mkdir();File(root,"model/f0.bin.part").writeBytes(byteArrayOf(1))}
            val repo=object:ModelRepository(m,File(root,"model"),SpaceProvider{Long.MAX_VALUE},proof){
                override fun deleteManaged(file:File){if(mode.startsWith("exit-stale")&&file.name.endsWith(".part")){if(mode!="exit-stale-retain")super.deleteManaged(file);throw first};super.deleteManaged(file)}
                override fun importFrom(source:ModelSource?,progress:Progress?,cancel:CancelGate?){
                    val p=object:Progress {
                        override fun sourceCleanup(succeeded:Boolean){progress?.sourceCleanup(succeeded)}
                        override fun modelCleanup(succeeded:Boolean){progress?.modelCleanup(succeeded)}
                        override fun planned(plan:ModelReports.ImportPlan?){if(mode=="exit-stale-second"&&++plannedCount==2)File(root,"model/f0.bin.part").writeBytes(byteArrayOf(1));progress?.planned(plan)}
                        override fun fileResult(name:String?,outcome:ModelReports.FileOutcome?){progress?.fileResult(name,outcome)}
                        override fun update(stage:Stage?,name:String?,i:Int,n:Int,d:Long,t:Long,sd:Long,st:Long,r:Long){progress?.update(stage,name,i,n,d,t,sd,st,r)}
                    }
                    try{super.importFrom(source,p,cancel)}catch(t:Throwable){seen=t;throw t}
                }
            }
            if(mode=="exit-stale-second")File(root,"model/f0.bin.part").delete()
            val owner=TaskCoordinator(java.util.concurrent.Executor{task->Thread({try{task.run()}catch(t:Throwable){error.set(t)}},"zip-owner-exit").also{thread.set(it);it.start()}})
            val c=ModelManagementController(owner,repo,ready,control,state)
            fun closeFault(){realClosed++;if(mode=="exit-zip-cancel"){entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw IOException("gate timeout")};throw first}
            val zip=object:ModelZipSource(ModelZipSource.Opener{File(fixtures,if(mode=="exit-inflater")"deflated.zip"else"stored.zip").inputStream()},Supplier{root},Supplier{FixedModelPackage(m,"fixture","mnn")}){
                override fun openStage(stage:File):java.nio.file.DirectoryStream<java.nio.file.Path>{val actual=super.openStage(stage);return object:java.nio.file.DirectoryStream<java.nio.file.Path>{override fun iterator()=actual.iterator();override fun close(){actual.close();if(mode=="exit-stage")closeFault()}}}
                override fun archiveIo():ModelZipArchive.Io=object:ModelZipArchive.Io(){
                    override fun openRandom(archive:File):java.io.RandomAccessFile=if(mode in listOf("exit-raf-primary","exit-raf-same"))object:java.io.RandomAccessFile(archive,"r"){override fun seek(at:Long){throw first}}else super.openRandom(archive)
                    override fun openZip(archive:File):java.util.zip.ZipFile {val result=super.openZip(archive);if(mode=="exit-zip-cancel")controllerCancel(control);return result}
                    override fun closeRandom(resource:java.io.RandomAccessFile){super.closeRandom(resource);if(mode=="exit-raf-primary"){realClosed++;throw closeError};if(mode=="exit-raf"||mode=="exit-raf-same")closeFault()}
                    override fun endInflater(resource:java.util.zip.Inflater){super.endInflater(resource);if(mode=="exit-inflater")closeFault()}
                    override fun closeZip(resource:java.util.zip.ZipFile){super.closeZip(resource);if(mode=="exit-zip-cancel"||mode=="exit-session")closeFault()}
                    override fun closeEntry(resource:java.io.InputStream){super.closeEntry(resource);if(mode=="exit-entry")closeFault()}
                }
            }
            val retained=File(root,"retain-live-worker");retained.writeText("pending")
            requireThat(c.startImport(zip),"ZIP cleanup exit admitted")
            try{if(mode=="exit-zip-cancel"){requireThat(entered.await(3,TimeUnit.SECONDS),"ZIP internal ZipFile close gate");requireThat(owner.isBusy()&&control.isBusy,"ZIP internal close retains owner")}}
            finally{release.countDown();thread.get()!!.join(5000)}
            requireThat(!thread.get()!!.isAlive,"ZIP cleanup exit worker actual exit");retained.delete()
            requireThat(!owner.isBusy()&&!ready.isReady&&!File(root,"model-zip-staging/archive.part").exists()&&state.current().cleanupOutcome==ModelManagementState.Outcome.FAILED,"ZIP every owned resource or stale part cleanup failure must propagate $mode")
            requireThat(seen===first||seen is ModelRepository.CancelledException,"ZIP cleanup exit primary preserved")
            if(mode=="exit-zip-cancel")requireThat(state.current().phase==ModelManagementState.Phase.CANCELLED&&(seen as Throwable).suppressed.any{it===first},"ZIP ZipFile prepare cancellation keeps primary and close suppressed")
            if(mode=="exit-raf-primary")requireThat(first.suppressed.any{it===closeError},"ZIP internal close Error suppressed on original primary")
            if(mode=="exit-raf-same")requireThat(first.suppressed.isEmpty(),"ZIP internal close same Throwable not self suppressed")
            requireThat(error.get()==null,"ZIP cleanup exit worker error")
        }finally{release.countDown();if(!File(root,"retain-live-worker").exists())root.deleteRecursively()}
        println("PASS $mode")
    }
    private fun smoke() {
        val root = Files.createTempDirectory("zip-junit-").toFile()
        try {
            val m = manifest()
            val policy = FixedModelPackage(m, "fixture", "mnn")
            val json = "{\"schema\":1,\"modelId\":\"fixture\",\"engine\":\"mnn\",\"files\":[" +
                m.entries().joinToString(",") { "{\"file\":\"${it.file()}\",\"bytes\":${it.bytes()},\"sha256\":\"${it.sha256()}\"}" } + "]}"
            val archive = File(root, "fixture.zip")
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                val payloads = linkedMapOf(FixedModelPackage.JSON_NAME to json.toByteArray())
                for ((i,e) in m.entries().withIndex()) payloads[e.file()] = ByteArray(3+i) { (65+i).toByte() }
                for ((name,bytes) in payloads) {
                    val e = java.util.zip.ZipEntry(name)
                    e.method = java.util.zip.ZipEntry.STORED; e.size = bytes.size.toLong(); e.compressedSize = e.size
                    val crc = java.util.zip.CRC32(); crc.update(bytes); e.crc = crc.value
                    e.time = 1704067200000L
                    zip.putNextEntry(e); zip.write(bytes); zip.closeEntry()
                }
            }
            val source = ModelZipSource(ModelZipSource.Opener { archive.inputStream() }, Supplier { root }, Supplier { policy })
            val repo = ModelRepository(m, File(root,"model"), ModelRepository.SpaceProvider { Long.MAX_VALUE })
            repo.importFrom(source,null); repo.verifyAll()
            requireThat(!File(root,"model-zip-staging/archive.part").exists(), "ZIP JUnit source cleanup")
        } finally { root.deleteRecursively() }
    }
    }
}
