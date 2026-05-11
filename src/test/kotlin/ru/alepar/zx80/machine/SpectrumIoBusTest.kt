package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu

class SpectrumIoBusTest {
    @Test
    fun `read ULA port 0xFEFE returns row 0 ORed with 0xA0`() {
        val kb = Keyboard().apply { press(SpectrumKey.CAPS_SHIFT) }
        val bus = SpectrumIoBus(kb, Beeper(Cpu()), BorderState())
        assertThat(bus.read(0xFEFE)).isEqualTo(0xA0 or 0x1E)
    }

    @Test
    fun `read ULA port 0xFDFE returns row 1 ORed with 0xA0`() {
        // Port 0xFDFE: low byte 0xFE has A0=0 (ULA port); high byte 0xFD = 0b11111101,
        // bit 1 clear, so row 1 is selected.
        val kb = Keyboard().apply { press(SpectrumKey.A) }
        val bus = SpectrumIoBus(kb, Beeper(Cpu()), BorderState())
        assertThat(bus.read(0xFDFE)).isEqualTo(0xA0 or 0x1E)
    }

    @Test
    fun `read ULA port 0xFFFE (no rows) returns 0xBF`() {
        val bus = SpectrumIoBus(Keyboard(), Beeper(Cpu()), BorderState())
        assertThat(bus.read(0xFFFE)).isEqualTo(0xBF)
    }

    @Test
    fun `read non-ULA port (A0=1) returns 0xFF`() {
        val bus = SpectrumIoBus(Keyboard(), Beeper(Cpu()), BorderState())
        assertThat(bus.read(0xFEFF)).isEqualTo(0xFF)
    }

    @Test
    fun `write to ULA port does not throw`() {
        val bus = SpectrumIoBus(Keyboard(), Beeper(Cpu()), BorderState())
        bus.write(0xFEFE, 0x07)
    }

    @Test
    fun `write to ULA port with bit 4 set notifies beeper of bit 1`() {
        val cpu = Cpu()
        val beeper = Beeper(cpu)
        val bus = SpectrumIoBus(Keyboard(), beeper, BorderState())
        beeper.beginFrame()
        bus.write(0xFEFE, 0x10) // bit 4 set
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        assertThat(buf[0]).isEqualTo(0xA0.toByte())
        assertThat(buf[Beeper.SAMPLES_PER_FRAME - 1]).isEqualTo(0xA0.toByte())
    }

    @Test
    fun `write to ULA port with bit 4 clear leaves beeper at bit 0`() {
        val cpu = Cpu()
        val beeper = Beeper(cpu)
        val bus = SpectrumIoBus(Keyboard(), beeper, BorderState())
        beeper.beginFrame()
        bus.write(0xFEFE, 0x07) // bit 4 clear; border bits set
        val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
        beeper.render(buf)
        assertThat(buf[0]).isEqualTo(0x60.toByte())
    }

    @Test
    fun `write to ULA port with low 3 bits set updates BorderState`() {
        val border = BorderState()
        val bus = SpectrumIoBus(Keyboard(), Beeper(Cpu()), border)
        bus.write(0xFEFE, 0x05) // bits 0 and 2 set = color 5
        assertThat(border.read()).isEqualTo(5)
    }

    @Test
    fun `write to ULA port with bit 4 set does not change BorderState from its current value`() {
        val border = BorderState()
        border.write(5)
        val bus = SpectrumIoBus(Keyboard(), Beeper(Cpu()), border)
        bus.write(0xFEFE, 0x15) // bit 4 set (beeper), bits 0-2 = 5
        assertThat(border.read()).isEqualTo(5) // bit 4 is beeper-only, border stays 5
    }
}
