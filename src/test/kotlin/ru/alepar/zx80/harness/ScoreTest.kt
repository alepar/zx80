package ru.alepar.zx80.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test
import ru.alepar.zx80.harness.suites.Suite

class ScoreTest {
    @Test
    fun `composite score is weight-normalised sum of suite ratios`() {
        val suites =
            listOf(
                stubSuite("opcodes", 0.2, passed = 0, total = 100),
                stubSuite("fuse", 0.7, passed = 50, total = 100),
                stubSuite("programs", 0.1, passed = 0, total = 5),
            )
        val composite = Score.compute(suites)
        // ratios: 0, 0.5, 0
        // weighted: 0.2*0 + 0.7*0.5 + 0.1*0 = 0.35
        assertThat(composite.score).isEqualTo(0.35, offset(1e-9))
    }

    @Test
    fun `empty suites yields zero score and empty parens headline`() {
        val c = Score.compute(emptyList())
        assertThat(c.score).isEqualTo(0.0)
        assertThat(c.headline()).isEqualTo("SCORE: 0.000  ()")
    }

    @Test
    fun `headline format`() {
        val suites =
            listOf(
                stubSuite("opcodes", 0.2, passed = 0, total = 1792),
                stubSuite("fuse", 0.7, passed = 0, total = 1289),
                stubSuite("programs", 0.1, passed = 0, total = 5),
            )
        val composite = Score.compute(suites)
        assertThat(composite.headline())
            .isEqualTo("SCORE: 0.000  (opcodes 0/1792, fuse 0/1289, programs 0/5)")
    }

    @Test
    fun `serialised JSON contains every suite's details verbatim`() {
        val suites =
            listOf(stubSuite("opcodes", 0.2, 1, 1, JsonObject(mapOf("k" to JsonPrimitive("v")))))
        val composite = Score.compute(suites)
        val json = composite.toJson(prettyPrint = false)
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        assertThat(
                parsed["suites"]!!
                    .jsonObject["opcodes"]!!
                    .jsonObject["details"]!!
                    .jsonObject["k"]!!
                    .jsonPrimitive
                    .content
            )
            .isEqualTo("v")
    }

    @Test
    fun `headline rounds to three decimal places`() {
        val suites = listOf(stubSuite("x", 1.0, 333, 1000)) // ratio 0.333; weighted 0.333
        val composite = Score.compute(suites)
        assertThat(composite.headline()).contains("SCORE: 0.333")
    }

    @Test
    fun `serialised JSON has score timestamp git suites`() {
        val suites = listOf(stubSuite("x", 1.0, 0, 1))
        val json = Score.compute(suites).toJson(prettyPrint = false)
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        assertThat(parsed.keys).containsExactlyInAnyOrder("score", "timestamp", "git", "suites")
    }

    private fun stubSuite(
        name: String,
        weight: Double,
        passed: Int,
        total: Int,
        details: kotlinx.serialization.json.JsonElement = buildJsonObject {},
    ): Suite =
        object : Suite {
            override val name = name
            override val weight = weight

            override fun run() = SuiteResult(name, weight, passed, total, details)
        }
}
