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
}
