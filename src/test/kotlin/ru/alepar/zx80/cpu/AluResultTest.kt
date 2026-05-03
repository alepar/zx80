package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AluResultTest {
    @Test
    fun `AluResult holds value and newF`() {
        val r = AluResult(value = 0x42, newF = 0x80)
        assertThat(r.value).isEqualTo(0x42)
        assertThat(r.newF).isEqualTo(0x80)
    }

    @Test
    fun `AluResult equality`() {
        assertThat(AluResult(0x10, 0x20)).isEqualTo(AluResult(0x10, 0x20))
        assertThat(AluResult(0x10, 0x20)).isNotEqualTo(AluResult(0x10, 0x21))
    }
}
