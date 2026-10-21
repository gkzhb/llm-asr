package org.llmasr.minimal.diagnostics

/** Wall + monotonic clock. Production uses System time; tests inject a fake
 * clock so timestamps are deterministic and slow / fast advancing is possible. */
interface RuntimeLogClock {
    fun wallTimeMillis(): Long
    fun monotonicNanos(): Long
    /** Test-only helper: production clocks reject advancing. */
    fun advance(wallMillisDelta: Long, monotonicNanosDelta: Long) {
        throw UnsupportedOperationException("clock does not support advance")
    }

    class System : RuntimeLogClock {
        // Preserve the original non-final Java method flags while the class stays final.
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun wallTimeMillis(): Long = java.lang.System.currentTimeMillis()
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun monotonicNanos(): Long = java.lang.System.nanoTime()
    }

    /** In-memory clock for tests. */
    class Fake : RuntimeLogClock {
        private var wall: Long
        private var mono: Long
        constructor() : this(0L, 0L)
        constructor(wall: Long, mono: Long) { this.wall = wall; this.mono = mono }
        // Preserve the original non-final Java method flags, including the setters.
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun wallTimeMillis(): Long = wall
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun monotonicNanos(): Long = mono
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open override fun advance(wallDelta: Long, monoDelta: Long) { wall += wallDelta; mono += monoDelta }
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun setWall(value: Long) { wall = value }
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun setMonotonic(value: Long) { mono = value }
    }
}
