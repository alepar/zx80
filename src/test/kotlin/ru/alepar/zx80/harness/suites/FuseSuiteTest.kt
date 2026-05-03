package ru.alepar.zx80.harness.suites

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.harness.fuse.FuseExpectedCase
import ru.alepar.zx80.harness.fuse.FuseInputCase

class FuseSuiteTest {
    @Test
    fun `name and weight are constants`() {
        val suite = FuseSuite(Decoder(), emptyList(), emptyList())
        assertThat(suite.name).isEqualTo("fuse")
        assertThat(suite.weight).isEqualTo(0.7)
    }

    @Test
    fun `with empty decoder all cases fail`() {
        val inputs = listOf(syntheticInput("00"), syntheticInput("01"))
        val expected = listOf(syntheticExpected("00"), syntheticExpected("01"))
        val suite = FuseSuite(Decoder(), inputs, expected)
        val r = suite.run()
        assertThat(r.name).isEqualTo("fuse")
        assertThat(r.weight).isEqualTo(0.7)
        assertThat(r.passed).isZero
        assertThat(r.total).isEqualTo(2)
    }

    @Test
    fun `mismatched names between inputs and expected throws`() {
        val inputs = listOf(syntheticInput("00"))
        val expected = listOf(syntheticExpected("99"))
        val suite = FuseSuite(Decoder(), inputs, expected)
        assertThatThrownBy { suite.run() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("00")
            .hasMessageContaining("99")
    }

    @Test
    fun `runOne loops dispatch until tStatesToRun is reached (Trap B fix)`() {
        val stubOp =
            object : ru.alepar.zx80.op.Op {
                override val operandLength = 0
                override val baseCycles = 4
                var executions = 0

                override fun execute(cpu: ru.alepar.zx80.cpu.Cpu, mem: ru.alepar.zx80.cpu.Memory) {
                    executions++
                    cpu.tStates += 4
                    cpu.pc = (cpu.pc + 1) and 0xFFFF
                }

                override fun mnemonic(operands: ru.alepar.zx80.op.OperandFetcher) = "STUB"
            }
        val decoder = Decoder().apply { main[0x00] = stubOp }
        val input =
            FuseInputCase(
                name = "loop-test",
                af = 0,
                bc = 0,
                de = 0,
                hl = 0,
                afAlt = 0,
                bcAlt = 0,
                deAlt = 0,
                hlAlt = 0,
                ix = 0,
                iy = 0,
                sp = 0,
                pc = 0,
                memptr = 0,
                i = 0,
                r = 0,
                iff1 = false,
                iff2 = false,
                im = 0,
                halted = false,
                tStatesToRun = 12,
                memory = listOf(0 to byteArrayOf(0, 0, 0)),
            )
        val expected =
            FuseExpectedCase(
                name = "loop-test",
                af = 0,
                bc = 0,
                de = 0,
                hl = 0,
                afAlt = 0,
                bcAlt = 0,
                deAlt = 0,
                hlAlt = 0,
                ix = 0,
                iy = 0,
                sp = 0,
                pc = 3,
                memptr = 0,
                i = 0,
                r = 0,
                iff1 = false,
                iff2 = false,
                im = 0,
                halted = false,
                tStatesAfter = 12,
                memory = emptyList(),
            )
        val suite = FuseSuite(decoder, listOf(input), listOf(expected))
        val r = suite.run()
        assertThat(r.passed).isEqualTo(1)
        assertThat(stubOp.executions).isEqualTo(3)
    }

    @Test
    fun `mismatched list sizes throws`() {
        val suite = FuseSuite(Decoder(), listOf(syntheticInput("00")), emptyList())
        assertThatThrownBy { suite.run() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("1")
            .hasMessageContaining("0")
    }

    private fun syntheticInput(name: String) =
        FuseInputCase(
            name = name,
            af = 0,
            bc = 0,
            de = 0,
            hl = 0,
            afAlt = 0,
            bcAlt = 0,
            deAlt = 0,
            hlAlt = 0,
            ix = 0,
            iy = 0,
            sp = 0,
            pc = 0,
            memptr = 0,
            i = 0,
            r = 0,
            iff1 = false,
            iff2 = false,
            im = 0,
            halted = false,
            tStatesToRun = 4,
            memory = emptyList(),
        )

    private fun syntheticExpected(name: String) =
        FuseExpectedCase(
            name = name,
            af = 0,
            bc = 0,
            de = 0,
            hl = 0,
            afAlt = 0,
            bcAlt = 0,
            deAlt = 0,
            hlAlt = 0,
            ix = 0,
            iy = 0,
            sp = 0,
            pc = 0,
            memptr = 0,
            i = 0,
            r = 0,
            iff1 = false,
            iff2 = false,
            im = 0,
            halted = false,
            tStatesAfter = 4,
            memory = emptyList(),
        )
}
