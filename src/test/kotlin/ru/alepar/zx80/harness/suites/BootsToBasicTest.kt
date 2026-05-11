package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.op.OpTableBuilder

class BootsToBasicTest {

    @Test
    fun `Suite runs without throwing on a fresh decoder`() {
        val suite = BootsToBasic(OpTableBuilder.build())
        val result = suite.run()
        assertThat(result.passed).isBetween(0, 3)
        assertThat(result.total).isEqualTo(3)
    }

    @Test
    fun `All three sub-checks pass on production decoder (gold path)`() {
        val suite = BootsToBasic(OpTableBuilder.build())
        val result = suite.run()
        assertThat(result.passed).isEqualTo(3)
    }

    @Test
    fun `Result has weight 0_1 and name boots-to-basic`() {
        val result = BootsToBasic(OpTableBuilder.build()).run()
        assertThat(result.name).isEqualTo("boots-to-basic")
        assertThat(result.weight).isEqualTo(0.1)
    }

    @Test
    fun `Details include frames-mid and frames-end fields`() {
        val result = BootsToBasic(OpTableBuilder.build()).run()
        val details = result.details.jsonObject
        assertThat(details).containsKey("frames-mid")
        assertThat(details).containsKey("frames-end")
        assertThat(details).containsKey("checks")
    }
}
