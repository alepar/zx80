package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.harness.programs.ExpectedState
import ru.alepar.zx80.harness.programs.ProgramExpectation

class ProgramsSuiteTest {
    @Test
    fun `with empty decoder a HALT-stopping program fails`() {
        val program =
            ProgramFixture(
                bytes = byteArrayOf(0x76),
                expectation =
                    ProgramExpectation(
                        name = "halt_only",
                        load_at = 0,
                        entry = 0,
                        max_cycles = 8,
                        stop_on = "HALT",
                        expect = ExpectedState(pc = 1, halted = true),
                    ),
            )
        val suite = ProgramsSuite(Decoder(), listOf(program))
        val r = suite.run()
        assertThat(r.name).isEqualTo("programs")
        assertThat(r.weight).isEqualTo(0.1)
        assertThat(r.passed).isZero
        assertThat(r.total).isEqualTo(1)
    }

    @Test
    fun `details lists results per program with PASS or FAIL status`() {
        val program =
            ProgramFixture(
                bytes = byteArrayOf(0x76),
                expectation =
                    ProgramExpectation(
                        name = "halt_only",
                        load_at = 0,
                        entry = 0,
                        max_cycles = 8,
                        stop_on = "HALT",
                        expect = ExpectedState(pc = 1, halted = true),
                    ),
            )
        val r = ProgramsSuite(Decoder(), listOf(program)).run()
        val results = (r.details as JsonObject)["results"]!!.jsonArray
        assertThat(results).hasSize(1)
        val result = results.single().jsonObject
        assertThat(result["name"]!!.jsonPrimitive.content).isEqualTo("halt_only")
        assertThat(result["status"]!!.jsonPrimitive.content).isEqualTo("FAIL")
        assertThat(result["reason"]!!.jsonPrimitive.content).contains("0x76")
    }
}
