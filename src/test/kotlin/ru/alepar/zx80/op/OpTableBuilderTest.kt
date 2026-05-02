package ru.alepar.zx80.op

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpTableBuilderTest {

    @Test
    fun `build returns a Decoder with no Ops installed yet`() {
        val d = OpTableBuilder.build()
        val tables = listOf(d.main, d.cb, d.ed, d.dd, d.fd, d.ddcb, d.fdcb)
        val totalInstalled = tables.sumOf { table -> table.count { it != null } }
        assertThat(totalInstalled).isZero
    }
}
