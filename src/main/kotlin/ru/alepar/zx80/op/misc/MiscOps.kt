package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the misc Op family (NOP, HALT, DI, EI, IM 0/1/2) into the decoder. Called by
 * [ru.alepar.zx80.op.OpTableBuilder].
 */
object MiscOps {
    fun installInto(d: Decoder) {
        d.main[0x00] = Nop
        d.main[0x76] = Halt
        d.main[0xF3] = Di
        d.main[0xFB] = Ei
        d.main[0x27] = Daa
        d.main[0x2F] = Cpl
        d.main[0x37] = Scf
        d.main[0x3F] = Ccf
        d.ed[0x46] = Im(0)
        d.ed[0x56] = Im(1)
        d.ed[0x5E] = Im(2)
    }
}
