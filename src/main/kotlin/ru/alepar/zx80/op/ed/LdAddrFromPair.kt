package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (nn), rr` — write 16-bit register pair to little-endian memory. ED 43/53/63/73 for
 * BC/DE/HL/SP. 20 T-states. R+=2. PC+=4. operandLength=2. No flag changes.
 *
 * Note: ED 63 (LD (nn),HL) duplicates main-table 0x22; both are documented and FUSE tests both.
 */
class LdAddrFromPair(private val pair: RegPair) : Op {
    init {
        require(pair != RegPair.AF) { "LD (nn),rr does not accept AF" }
    }

    override val operandLength = 2
    override val baseCycles = 20

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 2)
        mem.writeWord(addr, pair.read(cpu))
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (nn), ${pair.mnemonic}"
}
