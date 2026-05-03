package ru.alepar.zx80.cpu

/**
 * Push a 16-bit value onto the Z80 stack.
 *
 * Convention: SP is decremented BEFORE writing each byte. High byte is written first (at SP-1),
 * then low byte (at SP-2). After the push, SP points to the low byte. Value is masked to 16 bits.
 */
fun Cpu.push(mem: Memory, value: Int) {
    sp = (sp - 1) and 0xFFFF
    mem.write(sp, (value ushr 8) and 0xFF)
    sp = (sp - 1) and 0xFFFF
    mem.write(sp, value and 0xFF)
}

/**
 * Pop a 16-bit value from the Z80 stack.
 *
 * Convention: SP is incremented AFTER reading each byte. Low byte is read from SP, then high byte
 * from SP+1. After the pop, SP points one above the original high byte.
 */
fun Cpu.pop(mem: Memory): Int {
    val lo = mem.read(sp)
    sp = (sp + 1) and 0xFFFF
    val hi = mem.read(sp)
    sp = (sp + 1) and 0xFFFF
    return (hi shl 8) or lo
}
