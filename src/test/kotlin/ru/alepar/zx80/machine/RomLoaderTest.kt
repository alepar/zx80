package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RomLoaderTest {
    @Test
    fun `load48k returns 16384 bytes`() {
        val rom = RomLoader.load48k()
        assertThat(rom.size).isEqualTo(16_384)
    }

    @Test
    fun `load48k first byte is 0xF3 - DI - canonical Sinclair 48K ROM start`() {
        val rom = RomLoader.load48k()
        assertThat(rom[0].toInt() and 0xFF).isEqualTo(0xF3)
    }
}
