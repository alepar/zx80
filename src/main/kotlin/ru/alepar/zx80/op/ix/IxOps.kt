package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the DD/FD-prefixed (IX/IY) Op family into decoder.dd and decoder.fd. Filled in by WUs
 * 2.8-2 through 2.8-5. Also registers the LD SP,HL straggler at decoder.main[0xF9] (it sits
 * naturally alongside LD SP,IX and LD SP,IY).
 */
object IxOps {
    fun installInto(d: Decoder) {
        installLdSpHlStraggler(d)
    }

    private fun installLdSpHlStraggler(d: Decoder) {
        d.main[0xF9] = LdSpHl
    }
}
