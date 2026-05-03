package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JR e` — unconditional relative jump. The displacement byte at pc+1 is signed (-128..+127). New
 * pc = (pc + 2 + signedDisplacement) mod 64K. 12 T-states. No flag changes. Single opcode 0x18.
 */
object JrRel : Op {
    override val operandLength = 1
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        val e = mem.read(cpu.pc + 1).toByte().toInt() // signed
        cpu.pc = (cpu.pc + 2 + e) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JR e"
}
