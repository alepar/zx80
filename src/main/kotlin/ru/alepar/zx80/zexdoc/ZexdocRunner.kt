package ru.alepar.zx80.zexdoc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Dispatcher
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.OpTableBuilder

data class ZexdocResult(val output: String, val halted: Boolean, val cycles: Long)

/**
 * Runs a CP/M-80 binary (notably ZEXDOC.COM) with a minimal BDOS trap.
 *
 * Conventions:
 * - The binary loads at 0x0100 (CP/M TPA).
 * - Stack at 0xFFFE (just below the 64K boundary).
 * - mem[0x0005] is set to RET (0xC9). Before each step, if PC == 0x0005, the BdosHandler is invoked
 *   to perform the syscall side effect; the RET then pops the return address naturally, so PC, SP,
 *   and stack contents stay consistent with how a real CP/M would look from the program's
 *   perspective.
 * - mem[0x0000] is set to HALT (0x76) so that ZEXDOC's warm-boot exit (CALL 0x0000 or JP 0x0000)
 *   terminates the run cleanly instead of executing whatever happens to be at offset 0.
 */
class ZexdocRunner(private val out: Appendable) {
    fun run(rom: ByteArray, maxCycles: Long = Long.MAX_VALUE): ZexdocResult {
        val cpu =
            Cpu().apply {
                pc = 0x0100
                sp = 0xFFFE
            }
        val mem = Memory()
        for ((i, b) in rom.withIndex()) {
            mem.write(0x0100 + i, b.toInt() and 0xFF)
        }
        mem.write(0x0005, 0xC9) // RET at BDOS entry
        mem.write(0x0000, 0x76) // HALT at warm-boot vector

        val decoder = OpTableBuilder.build()
        val dispatcher = Dispatcher(decoder)
        val handler = BdosHandler(out)

        while (!cpu.halted && cpu.tStates < maxCycles) {
            if (cpu.pc == 0x0005) {
                handler.handle(cpu, mem)
                // Fall through: the RET at 0x0005 will execute and pop the return address.
            }
            val op =
                dispatcher.decodeAt(cpu, mem)
                    ?: error(
                        "no dispatch route for opcode 0x${mem.read(cpu.pc).toString(16)} at pc=0x${cpu.pc.toString(16)}"
                    )
            op.execute(cpu, mem)
        }
        return ZexdocResult(output = out.toString(), halted = cpu.halted, cycles = cpu.tStates)
    }
}
