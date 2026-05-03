package ru.alepar.zx80.op.cb

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.bit.BitHl
import ru.alepar.zx80.op.bit.BitReg
import ru.alepar.zx80.op.rot.RotShiftHl
import ru.alepar.zx80.op.rot.RotShiftReg
import ru.alepar.zx80.op.rot.RotateOp

/**
 * Registers the entire CB-prefixed table: rotate/shift ops in CB 0x00-0x3F (minus 8 SLL slots at
 * 0x30-0x37 which are undocumented), BIT n,r in 0x40-0x7F, RES n,r in 0x80-0xBF, SET n,r in
 * 0xC0-0xFF.
 *
 * BIT/RES/SET installers are added in WUs 2.7-3, 2.7-4, 2.7-5.
 */
object CbOps {
    fun installInto(d: Decoder) {
        installRotateShift(d)
        installBit(d)
    }

    private fun installBit(d: Decoder) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                val opcode = 0x40 or (n shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) BitHl(n) else BitReg(n, Reg.fromBits(rrrBits))
            }
        }
    }

    private fun installRotateShift(d: Decoder) {
        for (oooBits in 0..7) {
            if (oooBits == 6) continue // SLL — undocumented
            val op = RotateOp.fromBits(oooBits)
            for (rrrBits in 0..7) {
                val opcode = (oooBits shl 3) or rrrBits
                d.cb[opcode] =
                    if (rrrBits == 6) RotShiftHl(op) else RotShiftReg(op, Reg.fromBits(rrrBits))
            }
        }
    }
}
