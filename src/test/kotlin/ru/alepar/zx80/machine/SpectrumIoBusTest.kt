package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpectrumIoBusTest {
    @Test
    fun `read ULA port 0xFEFE returns row 0 ORed with 0xA0`() {
        val kb = Keyboard().apply { press(SpectrumKey.CAPS_SHIFT) }
        val bus = SpectrumIoBus(kb)
        assertThat(bus.read(0xFEFE)).isEqualTo(0xA0 or 0x1E)
    }

    @Test
    fun `read ULA port 0xFDFE returns row 1 ORed with 0xA0`() {
        // Port 0xFDFE: low byte 0xFE has A0=0 (ULA port); high byte 0xFD = 0b11111101,
        // bit 1 clear, so row 1 is selected.
        val kb = Keyboard().apply { press(SpectrumKey.A) }
        val bus = SpectrumIoBus(kb)
        assertThat(bus.read(0xFDFE)).isEqualTo(0xA0 or 0x1E)
    }

    @Test
    fun `read ULA port 0xFFFE (no rows) returns 0xBF`() {
        val bus = SpectrumIoBus(Keyboard())
        assertThat(bus.read(0xFFFE)).isEqualTo(0xBF)
    }

    @Test
    fun `read non-ULA port (A0=1) returns 0xFF`() {
        val bus = SpectrumIoBus(Keyboard())
        assertThat(bus.read(0xFEFF)).isEqualTo(0xFF)
    }

    @Test
    fun `write to ULA port does not throw`() {
        val bus = SpectrumIoBus(Keyboard())
        bus.write(0xFEFE, 0x07)
    }
}
