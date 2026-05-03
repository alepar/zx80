package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.rot.RotateOp

/**
 * Registers the documented DDCB/FDCB-prefixed Op family into decoder.ddcb and decoder.fdcb.
 *
 * Documented opcodes are at rrr=110 slots (where target is (IX+d) / (IY+d)). For the rotate/shift,
 * RES, and SET blocks the other rrr slots are undocumented "copy to r" variants implemented as
 * separate ops. For the BIT block (Phase F), the undocumented mirror slots all map to the same
 * BitIxd instance as the documented form — BIT has no result so the rrr field is ignored on real
 * Z80 hardware.
 */
object IxCbOps {
    fun installInto(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
            installRotateShift(table, idx)
            installRotateShiftCopyback(table, idx)
            installBit(table, idx)
            installRes(table, idx)
            installResCopyback(table, idx)
            installSet(table, idx)
            installSetCopyback(table, idx)
        }
    }

    private fun installRotateShift(table: Array<Op?>, idx: IndexReg) {
        for (oooBits in 0..7) {
            // Phase 2.13: oooBits=6 (SLL) is now installed. Phase 2.12 added SLL to RotateOp; this
            // table previously left it null per the documented-Z80-only carve-out.
            val op = RotateOp.fromBits(oooBits)
            val opcode = (oooBits shl 3) or 0x06
            table[opcode] = RotShiftIxd(idx, op)
        }
    }

    private fun installRotateShiftCopyback(table: Array<Op?>, idx: IndexReg) {
        for (oooBits in 0..7) {
            val op = RotateOp.fromBits(oooBits)
            for (rrrBits in 0..7) {
                if (rrrBits == 6) continue // documented memory-only form, handled above
                val opcode = (oooBits shl 3) or rrrBits
                table[opcode] = RotShiftIxdCopyback(idx, op, Reg.fromBits(rrrBits))
            }
        }
    }

    private fun installBit(table: Array<Op?>, idx: IndexReg) {
        // Phase F: install the BIT op at all 8 rrr slots per (n, prefix). The undocumented
        // BIT n,r,(IX+d) mirror slots (rrr != 6) behave identically to the documented BIT n,(IX+d)
        // form on real Z80 hardware — BIT has no result, so the register field is ignored. ZEXDOC
        // doesn't exercise these mirrors but FUSE does.
        for (n in 0..7) {
            val opInstance = BitIxd(idx, n)
            for (rrrBits in 0..7) {
                val opcode = 0x40 or (n shl 3) or rrrBits
                table[opcode] = opInstance
            }
        }
    }

    private fun installRes(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0x80 or (n shl 3) or 0x06
            table[opcode] = ResIxd(idx, n)
        }
    }

    private fun installResCopyback(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                if (rrrBits == 6) continue
                val opcode = 0x80 or (n shl 3) or rrrBits
                table[opcode] = ResIxdCopyback(idx, n, Reg.fromBits(rrrBits))
            }
        }
    }

    private fun installSet(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0xC0 or (n shl 3) or 0x06
            table[opcode] = SetIxd(idx, n)
        }
    }

    private fun installSetCopyback(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                if (rrrBits == 6) continue
                val opcode = 0xC0 or (n shl 3) or rrrBits
                table[opcode] = SetIxdCopyback(idx, n, Reg.fromBits(rrrBits))
            }
        }
    }
}
