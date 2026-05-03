package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD rr, nn` — loads a 16-bit immediate into a register pair. 10 T-states. No flag changes. PC
 * advances by 3.
 *
 * Opcodes: 0x01 (BC), 0x11 (DE), 0x21 (HL), 0x31 (SP).
 */
class LdPairImm(private val pair: RegPair) : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        pair.write(cpu, nn)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${pair.mnemonic}, nn"
}
