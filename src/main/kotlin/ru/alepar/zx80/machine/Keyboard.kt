package ru.alepar.zx80.machine

import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Spectrum 8x5 key matrix state, safe for concurrent read by the Pacer thread (via
 * SpectrumIoBus.read) and write by the EDT (via SpectrumWindow's KeyListener calling
 * press/release).
 *
 * Each of the 8 rows is an int in [rows]. The low 5 bits indicate state for the 5 keys: 1 =
 * released, 0 = pressed. Bits 5-7 of a row are always 1 (idle); SpectrumIoBus adds the bus pull-ups
 * for the upper bits.
 *
 * `pressCounts` refcounts each SpectrumKey so multiple host keys mapping to the same Spectrum key
 * release cleanly (e.g. Backspace = CAPS_SHIFT + K0; if the user holds Shift then taps Backspace,
 * the Shift-release should NOT clear CAPS_SHIFT while Backspace is still down). `pressCounts` is
 * EDT-only — no synchronization needed; all KeyEvents fire on the EDT, and tests call press/release
 * directly on the test thread (single-threaded JUnit execution).
 */
class Keyboard {
    private val rows = AtomicIntegerArray(8).also { for (i in 0 until 8) it.set(i, 0xFF) }
    private val pressCounts = IntArray(SpectrumKey.values().size)

    /** Press a Spectrum key. Refcounted. EDT-only. */
    fun press(key: SpectrumKey) {
        val idx = key.ordinal
        pressCounts[idx]++
        if (pressCounts[idx] == 1) {
            rows.getAndUpdate(key.row) { it and (1 shl key.bit).inv() }
        }
    }

    /** Release a Spectrum key. Refcounted. EDT-only. Safe even if never pressed. */
    fun release(key: SpectrumKey) {
        val idx = key.ordinal
        if (pressCounts[idx] == 0) return
        pressCounts[idx]--
        if (pressCounts[idx] == 0) {
            rows.getAndUpdate(key.row) { it or (1 shl key.bit) }
        }
    }

    /** Reset every refcount and row to "all keys released". Used on window focus loss. */
    fun releaseAll() {
        for (i in pressCounts.indices) pressCounts[i] = 0
        for (r in 0 until 8) rows.set(r, 0xFF)
    }

    /**
     * ULA read: high byte of port 0xFE is the row-select pattern (bit=0 means row selected).
     * Multiple rows can be selected; result is the bitwise AND of all selected rows. Returns the
     * low 5 bits only.
     */
    fun read(rowSelectByte: Int): Int {
        var result = 0xFF
        for (r in 0 until 8) {
            if ((rowSelectByte shr r) and 1 == 0) result = result and rows.get(r)
        }
        return result and 0x1F
    }
}
