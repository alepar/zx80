package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD rr, (nn)` — read little-endian word from memory into register pair. ED 4B/5B/6B/7B for
 * BC/DE/HL/SP. 20 T-states. R+=2. PC+=4. operandLength=2. No flag changes.
 *
 * Note: ED 6B (LD HL,(nn)) duplicates main-table 0x2A.
 */
class LdPairFromAddr(private val pair: RegPair) : Op {
    init {
        require(pair != RegPair.AF) { "LD rr,(nn) does not accept AF" }
    }

    override val operandLength = 2
    override val baseCycles = 20

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 2)
        pair.write(cpu, mem.readWord(addr))
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${pair.mnemonic}, (nn)"
}
