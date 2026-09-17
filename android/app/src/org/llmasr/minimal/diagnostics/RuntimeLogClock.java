package org.llmasr.minimal.diagnostics;

/** Wall + monotonic clock. Production uses System time; tests inject a fake
 * clock so timestamps are deterministic and slow / fast advancing is
 * possible. */
public interface RuntimeLogClock {
    long wallTimeMillis();
    long monotonicNanos();
    /** Test-only helper: advance both clocks. Production no-op. */
    default void advance(long wallMillisDelta, long monotonicNanosDelta) {
        throw new UnsupportedOperationException("clock does not support advance");
    }

    final class System implements RuntimeLogClock {
        @Override public long wallTimeMillis() { return java.lang.System.currentTimeMillis(); }
        @Override public long monotonicNanos() { return java.lang.System.nanoTime(); }
    }

    /** In-memory clock for tests. */
    final class Fake implements RuntimeLogClock {
        private long wall;
        private long mono;
        public Fake() { this(0L, 0L); }
        public Fake(long wall, long mono) { this.wall = wall; this.mono = mono; }
        @Override public long wallTimeMillis() { return wall; }
        @Override public long monotonicNanos() { return mono; }
        @Override public void advance(long wallDelta, long monoDelta) { wall += wallDelta; mono += monoDelta; }
        public void setWall(long value) { wall = value; }
        public void setMonotonic(long value) { mono = value; }
    }
}
