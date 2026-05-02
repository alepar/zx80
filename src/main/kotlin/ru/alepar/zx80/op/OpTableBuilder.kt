package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.misc.MiscOps

/**
 * Builds the populated [Decoder] for production. Per-family fragments (e.g. `MiscOps`, `ExOps`,
 * `LdOps`, ...) own their own opcode-to-Op registrations; this object just calls them in order.
 *
 * Slow startup is acceptable; runtime dispatch is O(1) array index.
 */
object OpTableBuilder {

    fun build(): Decoder {
        val d = Decoder()
        MiscOps.installInto(d)
        // Family fragments slot in here as they land:
        //   ExOps.installInto(d)    (WU 2.1a-4)
        //   LdOps.installInto(d)    (Plan 2.1b)
        //   ArithOps.installInto(d) (Plan 2.2)
        //   ...
        return d
    }
}
