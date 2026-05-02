package ru.alepar.zx80.cpu

import ru.alepar.zx80.op.Op

/**
 * Decodes the instruction at `cpu.pc` by reading bytes from `mem` and following any prefix (CB / ED
 * / DD / FD / DDCB / FDCB) into the right sub-table on the [Decoder]. Returns the matched [Op] or
 * `null` if no Op is installed at the resolved table position.
 *
 * Does NOT advance `cpu.pc` and does NOT touch `cpu.tStates` — `Op.execute` owns those.
 */
class Dispatcher(private val decoder: Decoder) {

    fun decodeAt(cpu: Cpu, mem: Memory): Op? {
        val b0 = mem.read(cpu.pc)
        return when (b0) {
            0xCB -> decoder.cb[mem.read(cpu.pc + 1)]
            0xED -> decoder.ed[mem.read(cpu.pc + 1)]
            0xDD -> decodeIndexed(decoder.dd, decoder.ddcb, cpu, mem)
            0xFD -> decodeIndexed(decoder.fd, decoder.fdcb, cpu, mem)
            else -> decoder.main[b0]
        }
    }

    private fun decodeIndexed(
        prefixTable: Array<Op?>,
        cbTable: Array<Op?>,
        cpu: Cpu,
        mem: Memory,
    ): Op? {
        val b1 = mem.read(cpu.pc + 1)
        return if (b1 == 0xCB) {
            cbTable[mem.read(cpu.pc + 3)]
        } else {
            prefixTable[b1]
        }
    }
}
