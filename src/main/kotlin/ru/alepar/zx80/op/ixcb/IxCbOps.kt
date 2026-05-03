package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the documented DDCB/FDCB-prefixed Op family into decoder.ddcb and decoder.fdcb. Filled
 * in by WUs 2.9-2 through 2.9-4.
 *
 * Documented opcodes only at rrr=110 slots (where target is (IX+d) / (IY+d)). The other ~225 slots
 * per table are undocumented "copy to r" variants and stay null per the project's
 * documented-Z80-only non-goal.
 */
object IxCbOps {
    fun installInto(d: Decoder) {
        // Filled in by WUs 2.9-2 through 2.9-4.
    }
}
