package ru.alepar.zx80.cpu

/**
 * Z80 CPU state. All 8-bit registers are stored as Int in 0..255 to sidestep signed-byte
 * arithmetic. Pair accessors compose/decompose on each call; pair setters mask to 16 bits.
 *
 * The alternate register set uses `aAlt`, `bAlt`, ... naming (Kotlin doesn't allow primes in
 * identifiers); Z80 documentation refers to these as `A'`, `B'`, ...
 */
class Cpu {
    // Main 8-bit register set
    var a: Int = 0
    var f: Int = 0
    var b: Int = 0
    var c: Int = 0
    var d: Int = 0
    var e: Int = 0
    var h: Int = 0
    var l: Int = 0

    // Alternate 8-bit register set (swapped by EX AF,AF' and EXX)
    var aAlt: Int = 0
    var fAlt: Int = 0
    var bAlt: Int = 0
    var cAlt: Int = 0
    var dAlt: Int = 0
    var eAlt: Int = 0
    var hAlt: Int = 0
    var lAlt: Int = 0

    // 16-bit registers
    var ix: Int = 0
    var iy: Int = 0
    var sp: Int = 0
    var pc: Int = 0

    // Special 8-bit registers
    var i: Int = 0
    var r: Int = 0

    /**
     * MEMPTR (a.k.a. WZ) — undocumented internal 16-bit register that leaks into the X/Y flag bits
     * for BIT n,(HL), BIT n,(IX/IY+d), IN r,(C), and IN A,(n). Initialized from FUSE input cases;
     * only the 5 leaking-into-flags op classes update or consume it (Phase E scope).
     */
    var memptr: Int = 0

    // Interrupt state
    var iff1: Boolean = false
    var iff2: Boolean = false
    var im: Int = 0
    var halted: Boolean = false

    /**
     * Cycle accumulator (T-states since reset). The convention is that each `Op.execute()` adds its
     * own T-states; the dispatcher does not touch this field.
     */
    var tStates: Long = 0

    /** I/O bus; defaults to NoIoBus (returns 0xFF on read, ignores writes). */
    var io: IoBus = NoIoBus

    // Register-pair convenience accessors
    var af: Int
        get() = (a shl 8) or f
        set(value) {
            val v = value and 0xFFFF
            a = (v ushr 8) and 0xFF
            f = v and 0xFF
        }

    var bc: Int
        get() = (b shl 8) or c
        set(value) {
            val v = value and 0xFFFF
            b = (v ushr 8) and 0xFF
            c = v and 0xFF
        }

    var de: Int
        get() = (d shl 8) or e
        set(value) {
            val v = value and 0xFFFF
            d = (v ushr 8) and 0xFF
            e = v and 0xFF
        }

    var hl: Int
        get() = (h shl 8) or l
        set(value) {
            val v = value and 0xFFFF
            h = (v ushr 8) and 0xFF
            l = v and 0xFF
        }

    /**
     * Increment the R register, preserving its top bit (which the Z80 keeps unchanged across normal
     * increments). Bottom 7 bits wrap mod 128.
     *
     * Use `bumpR(2)` for prefixed instructions (CB/ED/DD/FD), since the M1 cycle for the prefix and
     * the M1 cycle for the opcode each tick R.
     */
    fun bumpR(by: Int = 1) {
        r = (r and 0x80) or ((r + by) and 0x7F)
    }

    /**
     * Z80 hardware reset: PC=I=R=0, IFF1=IFF2=false, IM=0, halted=false, MEMPTR=0, tStates=0. SP
     * and the main register pairs are set to 0xFFFF (Z80 power-on convention; Sinclair's ROM sets
     * SP explicitly within the first dozen instructions). Alternates and IX/IY likewise 0xFFFF for
     * parity with real-hardware indeterminate state.
     */
    fun reset() {
        a = 0xFF
        f = 0xFF
        b = 0xFF
        c = 0xFF
        d = 0xFF
        e = 0xFF
        h = 0xFF
        l = 0xFF
        aAlt = 0xFF
        fAlt = 0xFF
        bAlt = 0xFF
        cAlt = 0xFF
        dAlt = 0xFF
        eAlt = 0xFF
        hAlt = 0xFF
        lAlt = 0xFF
        ix = 0xFFFF
        iy = 0xFFFF
        sp = 0xFFFF
        pc = 0x0000
        i = 0
        r = 0
        memptr = 0
        iff1 = false
        iff2 = false
        im = 0
        halted = false
        tStates = 0L
    }
}
