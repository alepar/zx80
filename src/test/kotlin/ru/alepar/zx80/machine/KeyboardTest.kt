package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KeyboardTest {
    @Test
    fun `fresh keyboard reads 0x1F across all rows`() {
        val kb = Keyboard()
        assertThat(kb.read(0x00)).isEqualTo(0x1F)
    }

    @Test
    fun `press A clears row 1 bit 0`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1E)
    }

    @Test
    fun `release A restores row to 0x1F`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1F)
    }

    @Test
    fun `refcount keeps key pressed across one early release`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1E)
    }

    @Test
    fun `refcount releases key after all presses unwound`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1F)
    }

    @Test
    fun `read 0xFE selects only row 0`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.CAPS_SHIFT)
        kb.press(SpectrumKey.A)
        assertThat(kb.read(0xFE)).isEqualTo(0x1E)
    }

    @Test
    fun `read 0xFC ANDs rows 0 and 1`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.CAPS_SHIFT)
        assertThat(kb.read(0xFC)).isEqualTo(0x1E)
    }

    @Test
    fun `read 0xFF selects no rows and returns 0x1F`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        assertThat(kb.read(0xFF)).isEqualTo(0x1F)
    }

    @Test
    fun `release of never-pressed key is a no-op`() {
        val kb = Keyboard()
        kb.release(SpectrumKey.Z)
        assertThat(kb.read(0xFE)).isEqualTo(0x1F)
    }

    @Test
    fun `releaseAll resets all rows and refcounts`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.B)
        kb.releaseAll()
        assertThat(kb.read(0x00)).isEqualTo(0x1F)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1F)
    }
}
