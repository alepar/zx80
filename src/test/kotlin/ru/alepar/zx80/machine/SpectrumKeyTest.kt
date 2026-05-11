package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpectrumKeyTest {
    @Test
    fun `CAPS_SHIFT is at row 0 bit 0`() {
        assertThat(SpectrumKey.CAPS_SHIFT.row).isEqualTo(0)
        assertThat(SpectrumKey.CAPS_SHIFT.bit).isEqualTo(0)
    }

    @Test
    fun `SPACE is at row 7 bit 0`() {
        assertThat(SpectrumKey.SPACE.row).isEqualTo(7)
        assertThat(SpectrumKey.SPACE.bit).isEqualTo(0)
    }

    @Test
    fun `B is at row 7 bit 4`() {
        assertThat(SpectrumKey.B.row).isEqualTo(7)
        assertThat(SpectrumKey.B.bit).isEqualTo(4)
    }

    @Test
    fun `there are exactly 40 keys`() {
        assertThat(SpectrumKey.values()).hasSize(40)
    }
}
