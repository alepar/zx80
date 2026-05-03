package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, r'` under a DD or FD prefix where neither operand references H/L. Z80 quirk: the prefix is
 * a no-op for these patterns; behavior is identical to the unprefixed `LD r, r'` but PC and R
 * advance by 2 (not 1) and 8 T-states are charged. Used by ZEXDOC's `ld <bcdexya>,<bcdexya>` test
 * which patches in any 0x40-0x7F pattern.
 *
 * The same instance is installed into both `dd[]` and `fd[]` because behavior does not differ
 * between the two prefixes.
 */
class LdRegRegPrefixed(private val src: Reg, private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
