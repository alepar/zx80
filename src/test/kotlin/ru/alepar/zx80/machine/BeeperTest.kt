package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu

class BeeperTest {

    private fun newBeeper(): Pair<Beeper, Cpu> {
        val cpu = Cpu()
        return Beeper(cpu) to cpu
    }

    @Test
    fun `no events render constant AMP_LO buffer`() {
        val (beeper, _) = newBeeper()
        beeper.beginFrame()
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        for (b in buf) assertThat(b).isEqualTo(0x60.toByte())
    }

    @Test
    fun `single mid-frame toggle to 1 first half AMP_LO second half AMP_HI`() {
        val (beeper, cpu) = newBeeper()
        beeper.beginFrame()
        cpu.tStates += 34_944 // half a frame
        beeper.onWrite(1)
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        assertThat(buf[0]).isEqualTo(0x60.toByte())
        assertThat(buf[479]).isEqualTo(0x60.toByte())
        assertThat(buf[480]).isEqualTo(0xA0.toByte())
        assertThat(buf[959]).isEqualTo(0xA0.toByte())
    }

    @Test
    fun `two toggles produce three regions`() {
        val (beeper, cpu) = newBeeper()
        beeper.beginFrame()
        cpu.tStates += 17_472 // 1/4 frame
        beeper.onWrite(1)
        cpu.tStates += 34_944 // another 1/2 frame -> 3/4 mark
        beeper.onWrite(0)
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        // 0..239 LO, 240..719 HI, 720..959 LO
        assertThat(buf[0]).isEqualTo(0x60.toByte())
        assertThat(buf[239]).isEqualTo(0x60.toByte())
        assertThat(buf[240]).isEqualTo(0xA0.toByte())
        assertThat(buf[719]).isEqualTo(0xA0.toByte())
        assertThat(buf[720]).isEqualTo(0x60.toByte())
        assertThat(buf[959]).isEqualTo(0x60.toByte())
    }

    @Test
    fun `cross-frame state carries last bit of frame N becomes sample 0 of frame N+1`() {
        val (beeper, cpu) = newBeeper()
        beeper.beginFrame()
        cpu.tStates += 34_944
        beeper.onWrite(1)
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        // Frame N+1: no events, bit stays at 1 across the whole frame.
        beeper.beginFrame()
        cpu.tStates += 69_888
        beeper.render(buf)
        for (b in buf) assertThat(b).isEqualTo(0xA0.toByte())
    }

    @Test
    fun `idempotent onWrite same bit twice records only one event`() {
        val (beeper, cpu) = newBeeper()
        beeper.beginFrame()
        cpu.tStates += 17_472
        beeper.onWrite(1)
        cpu.tStates += 34_944
        beeper.onWrite(1) // same value — no event
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        // 0..239 LO, 240..959 HI
        assertThat(buf[239]).isEqualTo(0x60.toByte())
        assertThat(buf[240]).isEqualTo(0xA0.toByte())
        assertThat(buf[959]).isEqualTo(0xA0.toByte())
    }

    @Test
    fun `edge-of-frame toggle at offset 0`() {
        val (beeper, _) = newBeeper()
        beeper.beginFrame()
        beeper.onWrite(1) // toggle at t=0
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        for (b in buf) assertThat(b).isEqualTo(0xA0.toByte())
    }

    @Test
    fun `multiple frames retain bit across boundaries`() {
        val (beeper, cpu) = newBeeper()
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        // Frame 1: toggle to 1 mid-frame.
        beeper.beginFrame()
        cpu.tStates += 34_944
        beeper.onWrite(1)
        beeper.render(buf)
        // Frame 2: toggle back to 0 at 1/4.
        beeper.beginFrame()
        cpu.tStates += 17_472
        beeper.onWrite(0)
        cpu.tStates += 52_416 // rest of frame
        beeper.render(buf)
        // 0..239 HI (carried from frame 1), 240..959 LO.
        assertThat(buf[0]).isEqualTo(0xA0.toByte())
        assertThat(buf[239]).isEqualTo(0xA0.toByte())
        assertThat(buf[240]).isEqualTo(0x60.toByte())
        assertThat(buf[959]).isEqualTo(0x60.toByte())
    }
}
