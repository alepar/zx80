package ru.alepar.zx80.op

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpTableBuilderTest {

    @Test
    fun `build returns a Decoder with at least the misc family installed`() {
        val d = OpTableBuilder.build()
        // Spot check: NOP at 0x00 and IM 1 at ED 0x56
        assertThat(d.main[0x00]).isNotNull
        assertThat(d.ed[0x56]).isNotNull
    }
}
