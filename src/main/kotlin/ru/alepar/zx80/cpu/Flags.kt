package ru.alepar.zx80.cpu

/**
 * Z80 flag-bit constants. The `f` register holds (from MSB to LSB):
 *
 * S Z X H Y P/V N C
 *
 * X (bit 5) and Y (bit 3) are undocumented copies of result bits — we track them only because the
 * FUSE test suite checks them; they don't influence any documented branch.
 */
object Flags {
    const val C = 0x01 // Carry
    const val N = 0x02 // Add/Subtract
    const val PV = 0x04 // Parity / Overflow
    const val Y = 0x08 // Undocumented (bit 3 of result)
    const val H = 0x10 // Half-carry
    const val X = 0x20 // Undocumented (bit 5 of result)
    const val Z = 0x40 // Zero
    const val S = 0x80 // Sign
}
