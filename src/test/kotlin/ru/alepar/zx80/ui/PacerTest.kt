package ru.alepar.zx80.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.machine.BorderState
import ru.alepar.zx80.machine.BorderedUlaRenderer
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer

class PacerTest {

    private fun newPacer(initialClockTime: Long = 0L): Pair<Pacer, FakeClock> {
        val machine = Spectrum48k() // no reset; synthetic state (all-zero RAM)
        val clock = FakeClock(initialClockTime)
        val pacer = Pacer(machine, BorderedUlaRenderer(UlaRenderer(), BorderState()), clock)
        return pacer to clock
    }

    @Test
    fun `start captures the current clock as startNanos`() {
        val (pacer, clock) = newPacer(initialClockTime = 5_000_000L)
        pacer.start()
        pacer.stepOneFrame()
        assertThat(clock.parks).containsExactly(5_000_000L + 20_000_000L)
    }

    @Test
    fun `stepOneFrame parks at the 20ms target`() {
        val (pacer, clock) = newPacer()
        pacer.start()
        pacer.stepOneFrame()
        assertThat(clock.parks).containsExactly(20_000_000L)
    }

    @Test
    fun `ten stepOneFrame calls produce ten evenly-spaced parks`() {
        val (pacer, clock) = newPacer()
        pacer.start()
        repeat(10) { pacer.stepOneFrame() }
        assertThat(clock.parks).isEqualTo((1..10).map { it * 20_000_000L })
    }

    @Test
    fun `flashOn is false for frames 0 through 15`() {
        val (pacer, _) = newPacer()
        pacer.start()
        assertThat(pacer.flashOn()).isFalse
        repeat(15) { pacer.stepOneFrame() }
        assertThat(pacer.flashOn()).isFalse
    }

    @Test
    fun `flashOn flips to true on frame 16`() {
        val (pacer, _) = newPacer()
        pacer.start()
        repeat(16) { pacer.stepOneFrame() }
        assertThat(pacer.flashOn()).isTrue
    }

    @Test
    fun `flashOn flips back to false on frame 32`() {
        val (pacer, _) = newPacer()
        pacer.start()
        repeat(32) { pacer.stepOneFrame() }
        assertThat(pacer.flashOn()).isFalse
    }

    @Test
    fun `currentImage returns a 352x288 BufferedImage`() {
        val (pacer, _) = newPacer()
        pacer.start()
        pacer.stepOneFrame()
        val img = pacer.currentImage()
        assertThat(img.width).isEqualTo(352)
        assertThat(img.height).isEqualTo(288)
    }

    @Test
    fun `drift-free over 100 frames`() {
        val (pacer, clock) = newPacer()
        pacer.start()
        repeat(100) { pacer.stepOneFrame() }
        assertThat(clock.parks.last()).isEqualTo(100L * 20_000_000L)
    }

    @Test
    fun `audioSink beforeFrame and afterFrame fire around each runFrame`() {
        class RecordingAudioSink : AudioSink {
            val log: MutableList<String> = mutableListOf()

            override fun beforeFrame() {
                log += "before"
            }

            override fun afterFrame() {
                log += "after"
            }
        }
        val machine = Spectrum48k()
        val clock = FakeClock()
        val sink = RecordingAudioSink()
        val pacer = Pacer(machine, BorderedUlaRenderer(UlaRenderer(), BorderState()), clock, sink)
        pacer.start()
        pacer.stepOneFrame()
        pacer.stepOneFrame()
        assertThat(sink.log).containsExactly("before", "after", "before", "after")
    }
}
