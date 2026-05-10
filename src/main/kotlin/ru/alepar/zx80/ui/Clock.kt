package ru.alepar.zx80.ui

import java.util.concurrent.locks.LockSupport

/**
 * Wall-clock + park abstraction. Production uses [RealClock]; tests inject a fake clock to verify
 * drift-free pacing without sleeping. `LockSupport.parkNanos` is used over `Thread.sleep` because
 * the latter rounds up to 1ms before calling the VM and discards sub-ms precision; on Linux with
 * hrtimers, parkNanos gives ~50us accuracy.
 */
interface Clock {
    fun nowNanos(): Long

    fun parkUntilNanos(target: Long)
}

object RealClock : Clock {
    override fun nowNanos(): Long = System.nanoTime()

    override fun parkUntilNanos(target: Long) {
        val now = nowNanos()
        if (target > now) LockSupport.parkNanos(target - now)
    }
}
