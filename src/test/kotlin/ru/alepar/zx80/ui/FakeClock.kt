package ru.alepar.zx80.ui

/**
 * Test helper. Snap-to-target Clock — parkUntilNanos jumps `now` straight to the target. Records
 * every park target so tests can assert the cadence.
 */
class FakeClock(initial: Long = 0L) : Clock {
    private var now: Long = initial
    val parks: MutableList<Long> = mutableListOf()

    override fun nowNanos(): Long = now

    override fun parkUntilNanos(target: Long) {
        parks += target
        if (target > now) now = target
    }
}
