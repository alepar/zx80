package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RST p` — single-byte CALL to a fixed page-zero address. Pushes pc+1 (return address) and jumps
 * to `target`. 11 T-states. No flag changes.
 *
 * 8 valid targets: 0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38. Opcodes: 0xC7, 0xCF, 0xD7, 0xDF,
 * 0xE7, 0xEF, 0xF7, 0xFF.
 */
class Rst(private val target: Int) : Op {
    init {
        require(target in VALID_TARGETS) {
            "RST target must be one of $VALID_TARGETS; got 0x${target.toString(16)}"
        }
    }

    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val returnAddr = (cpu.pc + 1) and 0xFFFF
        cpu.push(mem, returnAddr)
        cpu.pc = target
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "RST ${target.toString(16).padStart(2, '0').uppercase()}H"

    companion object {
        val VALID_TARGETS = setOf(0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38)
    }
}
