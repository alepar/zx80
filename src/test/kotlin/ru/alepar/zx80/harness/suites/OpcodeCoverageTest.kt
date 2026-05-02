package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

class OpcodeCoverageTest {
    @Test
    fun `empty decoder reports zero passing`() {
        val suite = OpcodeCoverage(Decoder())
        val r = suite.run()
        assertThat(r.name).isEqualTo("opcodes")
        assertThat(r.weight).isEqualTo(0.2)
        assertThat(r.passed).isZero
        assertThat(r.total).isEqualTo(7 * 256)
    }

    @Test
    fun `installed ops are counted`() {
        val d = Decoder()
        d.main[0x00] = StubOp
        d.cb[0xFF] = StubOp
        d.ed[0x44] = StubOp
        val r = OpcodeCoverage(d).run()
        assertThat(r.passed).isEqualTo(3)
    }

    @Test
    fun `details lists missing opcodes by table and index`() {
        val d = Decoder()
        d.main[0x00] = StubOp
        val r = OpcodeCoverage(d).run()
        val missing = (r.details as JsonObject)["missing"]!!.jsonArray
        // First missing entry should be main:0x01 (since 0x00 is filled)
        assertThat(missing.first().jsonPrimitive.content).isEqualTo("main:0x01")
    }

    @Test
    fun `missing list is capped at MISSING_LIMIT entries`() {
        val r = OpcodeCoverage(Decoder()).run() // empty Decoder; everything is missing
        val missing = (r.details as JsonObject)["missing"]!!.jsonArray
        assertThat(missing).hasSize(OpcodeCoverage.MISSING_LIMIT)
    }
}

private object StubOp : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {}

    override fun mnemonic(operands: OperandFetcher) = "STUB"
}
