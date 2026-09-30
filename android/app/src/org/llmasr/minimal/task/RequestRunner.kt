package org.llmasr.minimal.task

import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** Production transaction independent of Android/JSON; all IO/hooks execute under one owner. */
class RequestRunner(
    coordinator: TaskCoordinator?, preflight: Preflight?, lifecycle: Lifecycle?, cleanup: Cleanup?
) {
    fun interface TaskBody { @Throws(Exception::class) fun run(ctx: RequestContext) }
    fun interface Preflight { @Throws(IOException::class) fun run(): Int }
    /** Actual JVM default methods (-jvm-default=all), also callable by existing Java implementors. */
    interface Lifecycle {
        fun admitted(ctx: RequestContext) {}
        fun busy(ctx: RequestContext) {}
        fun rejected(ctx: RequestContext) {}
        @Throws(Exception::class) fun beforeBody(ctx: RequestContext) {}
        @Throws(Exception::class) fun succeeded(ctx: RequestContext) {}
        fun cancelled(ctx: RequestContext) {}
        fun failed(ctx: RequestContext, failure: Throwable) {}
        fun cleanupFailed(ctx: RequestContext, failure: Throwable) {}
    }
    fun interface Cleanup { @Throws(IOException::class) fun run(ctx: RequestContext) }
    private val coordinator: TaskCoordinator
    private val preflight: Preflight
    private val lifecycle: Lifecycle
    private val cleanup: Cleanup

    init {
        if (coordinator == null || preflight == null || lifecycle == null || cleanup == null)
            throw IllegalArgumentException("request ports required")
        this.coordinator = coordinator
        this.preflight = preflight
        this.lifecycle = lifecycle
        this.cleanup = cleanup
    }
    fun submit(kind: TaskKind?, body: TaskBody?): Boolean = submit(kind, body, Runnable {}, Runnable {})
    /** Busy calls neither resource hook; rejected execution finalizes exactly once. */
    fun submit(kind: TaskKind?, body: TaskBody?, admitted: Runnable?, finished: Runnable?): Boolean {
        if (kind == null || body == null || admitted == null || finished == null)
            throw IllegalArgumentException("request arguments required")
        val ctx = RequestContext(UUID.randomUUID().toString(), kind)
        val finalized = AtomicBoolean()
        val finishOnce = Runnable { if (finalized.compareAndSet(false, true)) finished.run() }
        try {
            val accepted = coordinator.submit(TaskCoordinator.Task {
                try { execute(ctx, body) }
                finally { finishOnce.run() }
            }, Runnable {
                admitted.run()
                lifecycle.admitted(ctx)
            }, finishOnce)
            if (!accepted) lifecycle.busy(ctx)
            return accepted
        } catch (rejected: RejectedExecutionException) {
            finishOnce.run()
            lifecycle.rejected(ctx)
            return false
        }
    }
    private fun execute(ctx: RequestContext, body: TaskBody) {
        try {
            ctx.cleanedTemporary = preflight.run()
            lifecycle.beforeBody(ctx)
            body.run(ctx)
            lifecycle.succeeded(ctx)
        } catch (cancelled: CancellationException) {
            lifecycle.cancelled(ctx)
        } catch (failure: Exception) {
            lifecycle.failed(ctx, failure)
        } catch (failure: LinkageError) {
            lifecycle.failed(ctx, failure)
        } finally {
            try { cleanup.run(ctx) }
            catch (failure: Exception) { lifecycle.cleanupFailed(ctx, failure) }
            catch (failure: LinkageError) { lifecycle.cleanupFailed(ctx, failure) }
        }
    }
}
