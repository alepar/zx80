package ru.alepar.zx80.zexdoc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

/**
 * Implements the subset of CP/M BDOS calls that ZEXDOC.COM uses:
 * - syscall 2 (C=2): print the character in E to the output.
 * - syscall 9 (C=9): print the `$`-terminated string at DE to the output.
 *
 * The handler ONLY produces side effects (writes to `out`). It does NOT advance PC, push, or pop —
 * those are the job of the `RET` (0xC9) byte that the runner places at 0x0005.
 *
 * Unknown syscall numbers are a no-op (some ZEXDOC variants probe other calls; we ignore them since
 * they're not load-bearing for documented-instruction CRC matching).
 */
class BdosHandler(private val out: Appendable) {
    fun handle(cpu: Cpu, mem: Memory) {
        when (cpu.c) {
            2 -> out.append((cpu.e and 0xFF).toChar())
            9 -> {
                var addr = cpu.de
                while (true) {
                    val b = mem.read(addr) and 0xFF
                    if (b == '$'.code) break
                    out.append(b.toChar())
                    addr = (addr + 1) and 0xFFFF
                }
            }
            else -> {
                /* no-op */
            }
        }
    }
}
