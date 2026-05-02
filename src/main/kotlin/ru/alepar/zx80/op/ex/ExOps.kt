package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the EX Op family (EX AF,AF'; EXX; EX DE,HL; EX (SP),HL) into the decoder. Called by
 * [ru.alepar.zx80.op.OpTableBuilder].
 */
object ExOps {
    fun installInto(d: Decoder) {
        d.main[0x08] = ExAfAfAlt
        d.main[0xD9] = Exx
        d.main[0xEB] = ExDeHl
        d.main[0xE3] = ExSpHl
    }
}
