package ru.alepar.zx80.cpu

/**
 * The four undocumented Z80 IX/IY half registers (IXH, IXL, IYH, IYL). Each one knows its parent
 * 16-bit IndexReg (IX or IY) and which byte (high or low) it accesses. Used by Phase 2.12 Op
 * classes for undocumented half-register opcodes that ZEXDOC exercises.
 */
enum class IndexHalfReg(val mnemonic: String, val parent: IndexReg, val isHigh: Boolean) {
    IXH("IXH", IndexReg.IX, true),
    IXL("IXL", IndexReg.IX, false),
    IYH("IYH", IndexReg.IY, true),
    IYL("IYL", IndexReg.IY, false);

    fun read(cpu: Cpu): Int {
        val full = parent.read(cpu)
        return if (isHigh) (full ushr 8) and 0xFF else full and 0xFF
    }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFF
        val full = parent.read(cpu)
        val composed = if (isHigh) (v shl 8) or (full and 0xFF) else (full and 0xFF00) or v
        parent.write(cpu, composed)
    }
}
