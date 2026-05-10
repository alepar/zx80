package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpectrumPaletteTest {
    @Test
    fun `black is 0x000000 in both phases`() {
        assertThat(SpectrumPalette.color(0, bright = false)).isEqualTo(0x000000)
        assertThat(SpectrumPalette.color(0, bright = true)).isEqualTo(0x000000)
    }

    @Test
    fun `white non-bright is 0xCDCDCD`() {
        assertThat(SpectrumPalette.color(7, bright = false)).isEqualTo(0xCDCDCD)
    }

    @Test
    fun `white bright is 0xFFFFFF`() {
        assertThat(SpectrumPalette.color(7, bright = true)).isEqualTo(0xFFFFFF)
    }

    @Test
    fun `red non-bright is 0xCD0000`() {
        assertThat(SpectrumPalette.color(2, bright = false)).isEqualTo(0xCD0000)
    }

    @Test
    fun `cyan bright is 0x00FFFF`() {
        assertThat(SpectrumPalette.color(5, bright = true)).isEqualTo(0x00FFFF)
    }
}
