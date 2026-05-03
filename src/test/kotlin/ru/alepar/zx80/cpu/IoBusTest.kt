package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IoBusTest {
    @Test
    fun `NoIoBus returns 0xFF on every read`() {
        assertThat(NoIoBus.read(0)).isEqualTo(0xFF)
        assertThat(NoIoBus.read(0xFFFF)).isEqualTo(0xFF)
        assertThat(NoIoBus.read(0x1234)).isEqualTo(0xFF)
    }

    @Test
    fun `NoIoBus accepts writes silently`() {
        NoIoBus.write(0, 0)
        NoIoBus.write(0xFFFF, 0xFF)
        NoIoBus.write(0x1234, 0x42)
    }
}
