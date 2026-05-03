package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

/**
 * One Z80 instruction. Each opcode pattern is its own Op subtype with its own JUnit test. `execute`
 * is responsible for advancing PC, incrementing `r`, accumulating T-states, and updating flags.
 */
interface Op {
    /**
     * Bytes after the opcode byte (and any prefixes) that this instruction consumes — the operand
     * bytes (n, nn, d). Informational metadata; not read by the runtime today, but reserved for
     * future disassembler use.
     *
     * Each Op is responsible for advancing PC inside `execute()` by `1 + prefixBytes +
     * operandLength`.
     */
    val operandLength: Int

    /**
     * Documented base T-states. For conditional ops whose count varies with the condition, this is
     * the *not-taken* count; the op adds the extra cycles itself when the branch is taken.
     */
    val baseCycles: Int

    fun execute(cpu: Cpu, mem: Memory)

    fun mnemonic(operands: OperandFetcher): String
}
