package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DecoderTest {
    @Test
    fun `fresh Decoder has seven tables of 256 nulls each`() {
        val d = Decoder()
        listOf(d.main, d.cb, d.ed, d.dd, d.fd, d.ddcb, d.fdcb).forEach { table ->
            assertThat(table).hasSize(256)
            assertThat(table.all { it == null }).isTrue
        }
    }
}
