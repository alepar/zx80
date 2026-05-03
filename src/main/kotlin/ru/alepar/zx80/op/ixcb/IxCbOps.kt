package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.rot.RotateOp

/**
 * Registers the documented DDCB/FDCB-prefixed Op family into decoder.ddcb and decoder.fdcb.
 *
 * Documented opcodes only at rrr=110 slots (where target is (IX+d) / (IY+d)). The other ~225 slots
 * per table are undocumented "copy to r" variants and stay null per the project's
 * documented-Z80-only non-goal.
 */
object IxCbOps {
    fun installInto(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
            installRotateShift(table, idx)
        }
    }

    private fun installRotateShift(table: Array<Op?>, idx: IndexReg) {
        for (oooBits in 0..7) {
            if (oooBits == 6) continue // SLL — undocumented
            val op = RotateOp.fromBits(oooBits)
            val opcode = (oooBits shl 3) or 0x06
            table[opcode] = RotShiftIxd(idx, op)
        }
    }
}
