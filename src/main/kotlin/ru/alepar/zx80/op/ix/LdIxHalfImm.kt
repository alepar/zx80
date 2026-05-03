package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD <IXH|IXL|IYH|IYL>, n` — undocumented. Load an immediate byte into one half of IX/IY. 11
 * T-states. R+=2. PC+=3 (DD/FD prefix + opcode + immediate byte). No flag changes.
 */
class LdIxHalfImm(private val dst: IndexHalfReg) : Op {
    override val operandLength = 1
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read((cpu.pc + 2) and 0xFFFF)
        dst.write(cpu, n)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, n"
}
