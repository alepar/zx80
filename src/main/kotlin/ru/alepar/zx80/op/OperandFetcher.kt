package ru.alepar.zx80.op

/**
 * Read-only view of the bytes following the opcode byte at a given PC. Used by `Op.mnemonic` so
 * disassembly works against a memory snapshot without needing the live CPU. Index 0 is the byte
 * immediately after the opcode (or after the prefix byte for prefixed instructions).
 */
fun interface OperandFetcher {
    fun byteAt(operandIndex: Int): Int
}
