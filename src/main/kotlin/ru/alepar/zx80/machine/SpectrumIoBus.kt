package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.IoBus

/**
 * Spectrum 48K ULA bus. Decodes Z80 IN/OUT ports:
 * - Read at any port with A0=0 (ULA): low 5 bits return the keyboard matrix for the rows selected
 *   by the port high byte (bit=0 = row selected). Bits 5 and 7 return 1 (bus idle). Bit 6 returns 1
 *   (EAR idle; M3 may override for tape input).
 * - Write at any port with A0=0 (ULA): low 3 bits set border color (M2.8); bit 4 is the beeper bit
 *   (M2.6). M2.5 stubs the write as a no-op so the CPU doesn't crash.
 * - Non-ULA ports (A0=1) read 0xFF and ignore writes (matches M1 NoIoBus behavior).
 */
class SpectrumIoBus(private val keyboard: Keyboard) : IoBus {
    override fun read(port: Int): Int =
        if ((port and 0x01) == 0) {
            val matrix = keyboard.read((port ushr 8) and 0xFF)
            matrix or 0xA0 // bit5=1 (idle), bit6=1 (EAR), bit7=1 (unused)
        } else 0xFF

    override fun write(port: Int, value: Int) {
        // Border (M2.8) and beeper (M2.6) writes land here. Today: no-op.
    }
}
