package ru.alepar.zx80.cpu

/**
 * The two Z80 16-bit index registers IX and IY. Used by all DD/FD-prefixed Op classes via
 * parameterization.
 */
enum class IndexReg(val mnemonic: String) {
    IX("IX"),
    IY("IY");

    fun read(cpu: Cpu): Int = if (this == IX) cpu.ix else cpu.iy

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFFFF
        if (this == IX) cpu.ix = v else cpu.iy = v
    }
}
