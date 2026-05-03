package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `LD (BC), A` and `LD (DE), A` — write A to memory[pair]. 7 T-states. No flag changes. */
class LdBcDeFromA(private val pair: RegPair) : Op {
    init {
        require(pair == RegPair.BC || pair == RegPair.DE) {
            "LdBcDeFromA accepts only BC or DE; got $pair"
        }
    }

    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        mem.write(pair.read(cpu), cpu.a)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (${pair.mnemonic}), A"
}
