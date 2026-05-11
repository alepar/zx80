package ru.alepar.zx80.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.machine.Beeper

class BeeperAudioSinkTest {

    @Test
    fun `beforeFrame begins a new beeper frame and clears events`() {
        val cpu = Cpu()
        val beeper = Beeper(cpu)
        val sink = BeeperAudioSink(beeper, NoOpAudioOutput)
        // Set bit, advance time, sink.beforeFrame should reset frame markers.
        beeper.beginFrame()
        beeper.onWrite(1)
        cpu.tStates += 1000
        sink.beforeFrame()
        // After beforeFrame, the events list is cleared; render produces all-AMP_HI
        // (bit carried as 1 from prior toggle).
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        cpu.tStates += 69_888
        beeper.render(buf)
        for (b in buf) assertThat(b).isEqualTo(0xA0.toByte())
    }

    @Test
    fun `afterFrame renders to AudioOutput`() {
        val cpu = Cpu()
        val beeper = Beeper(cpu)
        val out = RecordingAudioOutput()
        val sink = BeeperAudioSink(beeper, out)
        sink.beforeFrame()
        cpu.tStates += 69_888
        sink.afterFrame()
        assertThat(out.pushed).hasSize(1)
        assertThat(out.pushed[0]).hasSize(Beeper.SAMPLES_PER_FRAME)
    }

    @Test
    fun `two frames produce two buffers`() {
        val cpu = Cpu()
        val beeper = Beeper(cpu)
        val out = RecordingAudioOutput()
        val sink = BeeperAudioSink(beeper, out)
        sink.beforeFrame()
        cpu.tStates += 69_888
        sink.afterFrame()
        sink.beforeFrame()
        cpu.tStates += 69_888
        sink.afterFrame()
        assertThat(out.pushed).hasSize(2)
    }
}
