package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.ex.ExOps
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
        ExOps.installInto(d)
        return d
    }
}
