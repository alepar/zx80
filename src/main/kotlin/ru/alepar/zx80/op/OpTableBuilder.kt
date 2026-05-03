package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.alu.AluOps
import ru.alepar.zx80.op.branch.BranchOps
import ru.alepar.zx80.op.ex.ExOps
import ru.alepar.zx80.op.ld.LdOps
import ru.alepar.zx80.op.misc.MiscOps
import ru.alepar.zx80.op.stack.StackOps

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
        LdOps.installInto(d)
        AluOps.installInto(d)
        BranchOps.installInto(d)
        StackOps.installInto(d)
        return d
    }
}
