package ru.alepar.zx80.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class SuiteResultTest {
    @Test
    fun `ratio is passed over total`() {
        val r = SuiteResult(name = "x", weight = 1.0, passed = 3, total = 10, details = empty())
        assertThat(r.ratio).isEqualTo(0.3, within(1e-9))
    }

    @Test
    fun `ratio is zero when total is zero`() {
        val r = SuiteResult(name = "x", weight = 1.0, passed = 0, total = 0, details = empty())
        assertThat(r.ratio).isEqualTo(0.0)
    }

    @Test
    fun `ratio is one when all pass`() {
        val r = SuiteResult(name = "x", weight = 1.0, passed = 7, total = 7, details = empty())
        assertThat(r.ratio).isEqualTo(1.0)
    }

    private fun empty(): JsonObject = buildJsonObject {}
}
