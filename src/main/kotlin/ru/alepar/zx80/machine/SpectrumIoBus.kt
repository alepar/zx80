package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.machine.tape.TapeDeck

/**
 * Spectrum 48K ULA bus. Decodes Z80 IN/OUT ports:
 * - Read at any port with A0=0 (ULA): low 5 bits return the keyboard matrix for the rows selected
 *   by the port high byte (bit=0 = row selected). Bits 5 and 7 return 1 (bus idle). Bit 6 returns
 *   the EAR level: idle-high (0x40) normally, or driven by [tapeDeck] when pulse mode is active.
 * - Write at any port with A0=0 (ULA): low 3 bits set border color (M2.8); bit 4 is the beeper bit
 *   (M2.6). M2.5 stubs the write as a no-op so the CPU doesn't crash.
 * - Non-ULA ports (A0=1) read 0xFF and ignore writes (matches M1 NoIoBus behavior).
 */
class SpectrumIoBus(
    private val keyboard: Keyboard,
    private val beeper: Beeper,
    private val border: BorderState,
    private val tapeDeck: TapeDeck = TapeDeck(),
    private val tStates: () -> Long = { 0L },
) : IoBus {
    override fun read(port: Int): Int =
        if ((port and 0x01) == 0) {
            val matrix = keyboard.read((port ushr 8) and 0xFF)
            val ear = tapeDeck.earLevel(tStates())
            matrix or 0xA0 or ear // bit7=1 (unused), bit6=EAR (from tapeDeck), bit5=1 (idle)
        } else 0xFF

    override fun write(port: Int, value: Int) {
        if ((port and 0x01) == 0) {
            beeper.onWrite((value ushr 4) and 1)
            border.write(value and 0x07)
        }
    }
}
