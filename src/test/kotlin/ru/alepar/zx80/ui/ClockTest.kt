package ru.alepar.zx80.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClockTest {
    @Test
    fun `RealClock nowNanos is monotonically non-decreasing`() {
        val a = RealClock.nowNanos()
        val b = RealClock.nowNanos()
        assertThat(b).isGreaterThanOrEqualTo(a)
    }

    @Test
    fun `RealClock parkUntilNanos returns within a reasonable upper bound`() {
        val start = RealClock.nowNanos()
        RealClock.parkUntilNanos(start + 1_000_000) // 1ms target
        val elapsed = RealClock.nowNanos() - start
        // Loose upper bound to survive CI load; precision isn't the test, just that it returns.
        assertThat(elapsed).isLessThan(50_000_000L) // 50ms
    }
}
