package ru.alepar.zx80.ui

import java.awt.image.BufferedImage
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer

/**
 * Drives the Spectrum at real-time 50Hz, tracks the flash phase, and produces the latest
 * framebuffer image on demand. Caller owns the loop — Pacer holds no thread, which makes it
 * trivially testable with a fake [Clock].
 *
 * Frame timing is drift-free: each park target is `startNanos + frameNum * FRAME_NS`. Even if
 * individual parks have jitter, the long-run average is exactly 50Hz.
 */
class Pacer(
    private val machine: Spectrum48k,
    private val renderer: UlaRenderer,
    private val clock: Clock = RealClock,
) {
    private var startNanos: Long = 0
    private var frameNum: Long = 0
    private var flashCounter: Int = 0

    /** Capture the start instant and reset counters. Call once before the first stepOneFrame. */
    fun start() {
        startNanos = clock.nowNanos()
        frameNum = 0
        flashCounter = 0
    }

    /** Run one Spectrum frame, advance the flash counter, park until the next 20ms boundary. */
    fun stepOneFrame() {
        machine.runFrame()
        flashCounter++
        frameNum++
        val nextTarget = startNanos + frameNum * FRAME_NS
        clock.parkUntilNanos(nextTarget)
    }

    /** Current flash phase. Toggles every 16 frames (50/16 ~ 3.1 Hz, the real Spectrum rate). */
    fun flashOn(): Boolean = ((flashCounter / 16) and 1) == 1

    /** Latest framebuffer image rendered from current screen RAM using the current flash phase. */
    fun currentImage(): BufferedImage = renderer.render(machine.mem, flashOn())

    companion object {
        const val FRAME_NS = 20_000_000L // 50Hz = 20ms = 20_000_000ns
    }
}
