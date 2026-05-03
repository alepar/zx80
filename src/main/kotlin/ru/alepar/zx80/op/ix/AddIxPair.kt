package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `ADD IX/IY, rr` where rr in {BC, DE, IX/IY, SP}. srcBits encodes the source pair: 00=BC, 01=DE,
 * 10=Self (idx itself), 11=SP. 15 T-states. R+=2. PC+=2. Uses Flags.afterAddWord (S/Z/PV
 * preserved).
 */
class AddIxPair(private val idx: IndexReg, private val srcBits: Int) : Op {
    init {
        require(srcBits in 0..3) { "srcBits must be in 0..3; got $srcBits" }
    }

    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val src =
            when (srcBits) {
                0 -> cpu.bc
                1 -> cpu.de
                2 -> idx.read(cpu)
                3 -> cpu.sp
                else -> error("unreachable")
            }
        val r = Flags.afterAddWord(idx.read(cpu), src, cpu.f)
        idx.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher): String {
        val srcMnemonic =
            when (srcBits) {
                0 -> "BC"
                1 -> "DE"
                2 -> idx.mnemonic
                3 -> "SP"
                else -> error("unreachable")
            }
        return "ADD ${idx.mnemonic}, $srcMnemonic"
    }
}
