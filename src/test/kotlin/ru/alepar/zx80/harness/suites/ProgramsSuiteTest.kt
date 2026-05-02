package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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

    @Test
    fun `program passes when fake op halts at expected pc`() {
        // A fake op that just sets halted=true and advances pc by 1 + adds 4 T-states
        val haltOp =
            object : ru.alepar.zx80.op.Op {
                override val operandLength = 0
                override val baseCycles = 4

                override fun execute(cpu: ru.alepar.zx80.cpu.Cpu, mem: ru.alepar.zx80.cpu.Memory) {
                    cpu.halted = true
                    cpu.pc = (cpu.pc + 1) and 0xFFFF
                    cpu.tStates += 4
                }

                override fun mnemonic(operands: ru.alepar.zx80.op.OperandFetcher) = "HALT"
            }
        val decoder = Decoder().apply { main[0x76] = haltOp }
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
        val r = ProgramsSuite(decoder, listOf(program)).run()
        assertThat(r.passed).isEqualTo(1)
        assertThat(r.total).isEqualTo(1)

        val results = (r.details as JsonObject)["results"]!!.jsonArray
        val result = results.single().jsonObject
        assertThat(result["status"]!!.jsonPrimitive.content).isEqualTo("PASS")
        assertThat(result["cycles"]!!.jsonPrimitive.long).isEqualTo(4L)
        assertThat(result["reason"]).isNull()
    }

    @Test
    fun `program fails when max_cycles exceeded`() {
        // A fake op that never halts; just advances pc and adds 4 T-states each step
        val noopOp =
            object : ru.alepar.zx80.op.Op {
                override val operandLength = 0
                override val baseCycles = 4

                override fun execute(cpu: ru.alepar.zx80.cpu.Cpu, mem: ru.alepar.zx80.cpu.Memory) {
                    cpu.pc = (cpu.pc + 1) and 0xFFFF
                    cpu.tStates += 4
                }

                override fun mnemonic(operands: ru.alepar.zx80.op.OperandFetcher) = "NOP"
            }
        val decoder =
            Decoder().apply {
                // Install at every main slot so any pc lands on something
                for (i in main.indices) main[i] = noopOp
            }
        val program =
            ProgramFixture(
                bytes = ByteArray(16), // 16 bytes of 0x00, all map to noopOp
                expectation =
                    ProgramExpectation(
                        name = "infinite",
                        load_at = 0,
                        entry = 0,
                        max_cycles = 12,
                        stop_on = "HALT",
                        expect = ExpectedState(),
                    ),
            )
        val r = ProgramsSuite(decoder, listOf(program)).run()
        assertThat(r.passed).isZero
        val result = ((r.details as JsonObject)["results"]!!.jsonArray)[0].jsonObject
        assertThat(result["status"]!!.jsonPrimitive.content).isEqualTo("FAIL")
        assertThat(result["reason"]!!.jsonPrimitive.content).contains("max_cycles=12")
    }
}
