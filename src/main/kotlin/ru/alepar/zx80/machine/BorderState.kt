package ru.alepar.zx80.machine

/**
 * Current Spectrum border color (3 bits, 0..7). Pacer-thread writer via SpectrumIoBus.write;
 * EDT reader via BorderedUlaRenderer.render. Volatile is sufficient — int writes/reads are
 * atomic on JVM, and volatile ensures cross-thread visibility.
 *
 * Initial color is 0 (black). Border is always non-bright on real Spectrum hardware.
 */
class BorderState {
    @Volatile private var colorValue: Int = 0

    fun write(value: Int) {
        colorValue = value and 0x07
    }

    fun read(): Int = colorValue
}
