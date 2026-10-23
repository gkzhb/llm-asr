import org.llmasr.minimal.modelmanagement.*
import org.llmasr.minimal.model.*
import java.io.*
import java.util.*
import java.util.concurrent.*

/** Executes real provider call/stream boundaries, not ContentResolver stubs. */
class ModelProviderBoundaryTest {
    companion object {
        private fun check(b: Boolean, why: String) { if (!b) throw AssertionError(why) }
        private fun providerFailure(kind: Int) {
            val secret = "Private account alice@example.test Confidential"
            when (kind) {
                0 -> throw CancellationException(secret)
                1 -> {
                    val e = ModelRepository.CancelledException(); e.initCause(IOException(secret))
                    (e as java.lang.Throwable).addSuppressed(IOException(secret)); throw e
                }
                2 -> throw SecurityException(secret)
                3 -> throw FileNotFoundException(secret)
                4 -> throw LinkageError(secret)
                else -> throw IllegalStateException(secret)
            }
        }
        private fun assertSafe(e: Throwable) {
            check(e is IOException && e !is ModelRepository.CancelledException, "provider-authored cancellation is safe failure, not own gate")
            check(e.message!!.equals(ModelProviderBoundary.FILE_FAILURE) || e.message!!.equals(ModelProviderBoundary.DIRECTORY_FAILURE), "fixed safe message")
            check(e.cause == null && e.suppressed.isEmpty(), "provider cause/suppressed cannot escape")
        }
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<out String?>?) {
            for (k in 0 until 6) {
                val kind = k
                try {
                    ModelProviderBoundary.call<Any?>(ModelProviderBoundary.DIRECTORY_FAILURE) { providerFailure(kind); null }
                    throw AssertionError("directory supplier did not fail")
                } catch (e: IOException) { assertSafe(e) }
                for (stage in 0 until 4) {
                    val fault = stage
                    try {
                        val input = ModelProviderBoundary.open {
                            if (fault == 0) providerFailure(kind)
                            object : InputStream() {
                                override fun read(): Int { providerFailure(kind); return -1 }
                                override fun read(b: ByteArray?, off: Int, len: Int): Int { providerFailure(kind); return -1 }
                                override fun close() { if (fault == 3) providerFailure(kind) }
                            }
                        }
                        if (fault == 1) input.read(); if (fault == 2) input.read(ByteArray(3), 0, 3); if (fault == 3) input.close()
                        throw AssertionError("boundary did not fail " + fault)
                    } catch (e: IOException) { assertSafe(e) }
                }
                val f = ModelManagementControllerTest.Fixture(); val c = f.controller()
                val source = object : ModelSource {
                    override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> {
                        val m: MutableMap<String?, ModelSource.UriRef?> = HashMap(); for (name in expected!!) m[name] = ModelSource.UriRef(name); return m
                    }
                    override fun open(ref: ModelSource.UriRef?): InputStream = ModelProviderBoundary.open { providerFailure(kind); null }
                }
                c.startImport(source); f.queue.run()
                check(f.state.current().phase == ModelManagementState.Phase.FAILED, "unsolicited provider cancellation FAILED")
                check(f.state.current().errorCode.equals(ModelProviderBoundary.FILE_FAILURE), "snapshot sanitized")
                check(!ModelUiText.task(f.state.current()).contains("Confidential") && f.reports.log.equals(Arrays.asList("pending:INFERENCE", "terminal:INFERENCE")), "UI/report isolation")
            }
            try {
                ModelProviderBoundary.checkpoint(object : ModelRepository.CancelGate {
                    override fun cancelled(): Boolean = true
                    override fun reservePublish(): Boolean = false
                }); throw AssertionError("gate")
            } catch (own: ModelRepository.CancelledException) { check(own.message!!.equals("已取消") && own.cause == null, "genuine typed gate cancellation") }
            val f = ModelManagementControllerTest.Fixture(); val c = f.controller(); val close = ModelRepositoryCancelTest.Gate()
            val src = object : ModelSource {
                override fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?> {
                    val m: MutableMap<String?, ModelSource.UriRef?> = HashMap(); for (name in expected!!) m[name] = ModelSource.UriRef(name); return m
                }
                override fun open(ref: ModelSource.UriRef?): InputStream = ModelProviderBoundary.open {
                    object : ByteArrayInputStream(ModelManagementControllerTest.DATA) { override fun close() { close.block(); providerFailure(0) } }
                }
            }
            c.startImport(src)
            val w = ModelRepositoryCancelTest.Worker { f.queue.run() }; var primary: Throwable? = null
            try {
                try { close.await(); c.requestCancel(f.control.peekActive().id); check(f.owner.isBusy() && !c.startVerify(), "owner retained through boundary close") }
                finally { close.close() }
                w.join(); check(w.error.get() == null, "worker failure observed")
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                if (primary == null) w.close()
                else try { w.close() } catch (closing: Throwable) { (primary as java.lang.Throwable).addSuppressed(closing) }
            }
            check(f.state.current().phase == ModelManagementState.Phase.CANCELLED && !f.owner.isBusy(), "genuine gate wins after sanitized close failure")
            check(f.state.current().errorCode.equals(ModelProviderBoundary.FILE_FAILURE), "cancel does not expose provider message")
            println("PASS production provider boundary exception/stream/owner regressions")
        }
    }
}
