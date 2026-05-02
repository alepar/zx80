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

    @Test
    fun `installedCount counts non-null entries across all tables`() {
        val d = Decoder()
        assertThat(d.installedCount()).isZero
    }

    @Test
    fun `installedCount sees newly installed ops`() {
        val d = Decoder()
        d.main[0x00] = NoOp // install one
        d.cb[0xFF] = NoOp // install another
        assertThat(d.installedCount()).isEqualTo(2)
    }
}

/** Minimal stub Op for tests that don't care about behaviour. */
private object NoOp : ru.alepar.zx80.op.Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {}

    override fun mnemonic(operands: ru.alepar.zx80.op.OperandFetcher) = "NOP"
}
