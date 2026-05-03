package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `NOP*` — installed at every ED-prefixed opcode slot that is not a documented Z80 instruction
 * (e.g. ED 0x00-0x3F, parts of ED 0x80-0x9F, ED 0xC0-0xFF). Real Z80 hardware treats these as
 * 8-cycle NOPs that advance PC by 2 (the prefix byte + opcode byte). Phase F installs this blanket
 * fallback after all real ED ops have been registered, filling any still-null slots.
 */
object EdNop : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "NOP*"
}
