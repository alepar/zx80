package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

class DispatcherTest {

    @Test
    fun `decodes unprefixed opcode from main table`() {
        val decoder = Decoder().apply { main[0x42] = StubOp("MAIN_42") }
        val cpu = Cpu().apply { pc = 0x100 }
        val mem = Memory().apply { write(0x100, 0x42) }
        val op = Dispatcher(decoder).decodeAt(cpu, mem)
        assertThat(op).isNotNull
        assertThat(op!!.mnemonic(StubFetcher)).isEqualTo("MAIN_42")
    }

    @Test
    fun `returns null for unimplemented main opcode`() {
        val cpu = Cpu().apply { pc = 0x100 }
        val mem = Memory().apply { write(0x100, 0x99) }
        assertThat(Dispatcher(Decoder()).decodeAt(cpu, mem)).isNull()
    }

    @Test
    fun `decodes CB-prefixed opcode from cb table`() {
        val decoder = Decoder().apply { cb[0x10] = StubOp("CB_10") }
        val cpu = Cpu().apply { pc = 0x200 }
        val mem =
            Memory().apply {
                write(0x200, 0xCB)
                write(0x201, 0x10)
            }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("CB_10")
    }

    @Test
    fun `decodes ED-prefixed opcode from ed table`() {
        val decoder = Decoder().apply { ed[0x46] = StubOp("ED_46") }
        val cpu = Cpu().apply { pc = 0x300 }
        val mem =
            Memory().apply {
                write(0x300, 0xED)
                write(0x301, 0x46)
            }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("ED_46")
    }

    @Test
    fun `decodes DD-prefixed opcode from dd table when second byte is not CB`() {
        val decoder = Decoder().apply { dd[0x21] = StubOp("DD_21") }
        val cpu = Cpu().apply { pc = 0x400 }
        val mem =
            Memory().apply {
                write(0x400, 0xDD)
                write(0x401, 0x21)
            }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("DD_21")
    }

    @Test
    fun `decodes FD-prefixed opcode from fd table when second byte is not CB`() {
        val decoder = Decoder().apply { fd[0x21] = StubOp("FD_21") }
        val cpu = Cpu().apply { pc = 0x500 }
        val mem =
            Memory().apply {
                write(0x500, 0xFD)
                write(0x501, 0x21)
            }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("FD_21")
    }

    @Test
    fun `decodes DDCB-prefixed opcode reads opcode byte at pc plus 3 (after displacement)`() {
        val decoder = Decoder().apply { ddcb[0x06] = StubOp("DDCB_06") }
        val cpu = Cpu().apply { pc = 0x600 }
        val mem =
            Memory().apply {
                write(0x600, 0xDD)
                write(0x601, 0xCB)
                write(0x602, 0x05) // displacement byte
                write(0x603, 0x06) // actual opcode
            }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("DDCB_06")
    }

    @Test
    fun `decodes FDCB-prefixed opcode reads opcode byte at pc plus 3 (after displacement)`() {
        val decoder = Decoder().apply { fdcb[0xFE] = StubOp("FDCB_FE") }
        val cpu = Cpu().apply { pc = 0x700 }
        val mem =
            Memory().apply {
                write(0x700, 0xFD)
                write(0x701, 0xCB)
                write(0x702, 0x00)
                write(0x703, 0xFE)
            }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("FDCB_FE")
    }

    @Test
    fun `DDCB lookup wraps mod 64K when pc near end of memory`() {
        // Place DD CB d xx straddling 0xFFFD..0x0000
        val decoder = Decoder().apply { ddcb[0x42] = StubOp("DDCB_42_WRAP") }
        val cpu = Cpu().apply { pc = 0xFFFD }
        val mem =
            Memory().apply {
                write(0xFFFD, 0xDD)
                write(0xFFFE, 0xCB)
                write(0xFFFF, 0x00) // displacement
                write(0x0000, 0x42) // actual opcode (wrapped)
            }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("DDCB_42_WRAP")
    }
}

private class StubOp(val name: String) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {}

    override fun mnemonic(operands: OperandFetcher) = name
}

private object StubFetcher : OperandFetcher {
    override fun byteAt(operandIndex: Int) = 0
}
