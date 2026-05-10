package ru.alepar.zx80.cpu

/**
 * Decides whether a write to a given address should land on the underlying byte array. Used by
 * [Memory] to model the Spectrum's ROM/RAM boundary at 0x4000 (and other future memory-map shapes).
 */
fun interface WritePolicy {
    fun shouldWrite(addr: Int): Boolean
}

/** Default M1 policy: every address is writable. Preserves existing test/harness behavior. */
object OpenPolicy : WritePolicy {
    override fun shouldWrite(addr: Int): Boolean = true
}

/**
 * Spectrum 48K policy: addresses below [limit] are read-only. Writes to them complete the bus cycle
 * but the byte is dropped — matching real Z80 semantics on a ROM-mapped region.
 */
class ReadOnlyBelow(private val limit: Int) : WritePolicy {
    override fun shouldWrite(addr: Int): Boolean = (addr and 0xFFFF) >= limit
}
