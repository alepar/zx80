package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.harness.SuiteResult
import ru.alepar.zx80.harness.fuse.FuseExpectedCase
import ru.alepar.zx80.harness.fuse.FuseInputCase

/**
 * Runs every FUSE test case against the configured Decoder, reporting pass/fail by exact CPU +
 * memory state match (including T-states).
 *
 * For each case we install the initial state into a fresh Cpu/Memory pair, decode and execute the
 * single instruction at PC, and then compare every register and every memory cell mentioned by the
 * expected case. A case passes only when every check matches.
 *
 * `details` shape: `{ "failures": ["<name>: <reason>", ...] }` capped at FAILURE_LIMIT entries.
 */
class FuseSuite(
    private val decoder: Decoder,
    private val inputs: List<FuseInputCase>,
    private val expected: List<FuseExpectedCase>,
) : Suite {
    override val name: String = "fuse"
    override val weight: Double = 0.7

    override fun run(): SuiteResult {
        check(inputs.size == expected.size) {
            "FUSE inputs (${inputs.size}) and expected (${expected.size}) sizes don't match"
        }
        var passed = 0
        val failures = mutableListOf<String>()

        for (i in inputs.indices) {
            val input = inputs[i]
            val want = expected[i]
            check(input.name == want.name) {
                "FUSE name mismatch at index $i: input='${input.name}' expected='${want.name}'"
            }
            val reason = runOne(input, want)
            if (reason == null) passed++ else failures.add("${input.name}: $reason")
        }

        val details = buildJsonObject {
            put("failures", JsonArray(failures.take(FAILURE_LIMIT).map { JsonPrimitive(it) }))
        }
        return SuiteResult(
            name = name,
            weight = weight,
            passed = passed,
            total = inputs.size,
            details = details,
        )
    }

    /** Execute one case. Returns null on pass, or a short description on fail. */
    private fun runOne(input: FuseInputCase, want: FuseExpectedCase): String? {
        val cpu = Cpu().apply { loadFrom(input) }
        val mem =
            Memory().apply {
                for ((addr, bytes) in input.memory) {
                    for ((offset, b) in bytes.withIndex()) {
                        write(addr + offset, b.toInt() and 0xFF)
                    }
                }
            }

        val opcodeByte = mem.read(cpu.pc)
        val op =
            decoder.main[opcodeByte] ?: return "no op for opcode 0x${"%02X".format(opcodeByte)}"
        op.execute(cpu, mem)

        if (cpu.af != want.af) return "af mismatch: ${hex4(cpu.af)} vs ${hex4(want.af)}"
        if (cpu.bc != want.bc) return "bc mismatch: ${hex4(cpu.bc)} vs ${hex4(want.bc)}"
        if (cpu.de != want.de) return "de mismatch: ${hex4(cpu.de)} vs ${hex4(want.de)}"
        if (cpu.hl != want.hl) return "hl mismatch: ${hex4(cpu.hl)} vs ${hex4(want.hl)}"
        val afAlt = (cpu.aAlt shl 8) or cpu.fAlt
        if (afAlt != want.afAlt) return "af' mismatch: ${hex4(afAlt)} vs ${hex4(want.afAlt)}"
        val bcAlt = (cpu.bAlt shl 8) or cpu.cAlt
        if (bcAlt != want.bcAlt) return "bc' mismatch: ${hex4(bcAlt)} vs ${hex4(want.bcAlt)}"
        val deAlt = (cpu.dAlt shl 8) or cpu.eAlt
        if (deAlt != want.deAlt) return "de' mismatch: ${hex4(deAlt)} vs ${hex4(want.deAlt)}"
        val hlAlt = (cpu.hAlt shl 8) or cpu.lAlt
        if (hlAlt != want.hlAlt) return "hl' mismatch: ${hex4(hlAlt)} vs ${hex4(want.hlAlt)}"
        if (cpu.ix != want.ix) return "ix mismatch: ${hex4(cpu.ix)} vs ${hex4(want.ix)}"
        if (cpu.iy != want.iy) return "iy mismatch: ${hex4(cpu.iy)} vs ${hex4(want.iy)}"
        if (cpu.sp != want.sp) return "sp mismatch: ${hex4(cpu.sp)} vs ${hex4(want.sp)}"
        if (cpu.pc != want.pc) return "pc mismatch: ${hex4(cpu.pc)} vs ${hex4(want.pc)}"
        if (cpu.i != want.i) return "i mismatch"
        if (cpu.r != want.r) return "r mismatch"
        if (cpu.iff1 != want.iff1) return "iff1 mismatch"
        if (cpu.iff2 != want.iff2) return "iff2 mismatch"
        if (cpu.im != want.im) return "im mismatch"
        if (cpu.halted != want.halted) return "halted mismatch"
        if (cpu.tStates.toInt() != want.tStatesAfter) {
            return "tstates mismatch: ${cpu.tStates} vs ${want.tStatesAfter}"
        }
        for ((addr, bytes) in want.memory) {
            for ((offset, b) in bytes.withIndex()) {
                val actual = mem.read(addr + offset)
                val expectedByte = b.toInt() and 0xFF
                if (actual != expectedByte) {
                    return "mem mismatch at 0x${"%04X".format((addr + offset) and 0xFFFF)}: " +
                        "${"%02X".format(actual)} vs ${"%02X".format(expectedByte)}"
                }
            }
        }
        return null
    }

    private fun Cpu.loadFrom(input: FuseInputCase) {
        af = input.af
        bc = input.bc
        de = input.de
        hl = input.hl
        aAlt = (input.afAlt ushr 8) and 0xFF
        fAlt = input.afAlt and 0xFF
        bAlt = (input.bcAlt ushr 8) and 0xFF
        cAlt = input.bcAlt and 0xFF
        dAlt = (input.deAlt ushr 8) and 0xFF
        eAlt = input.deAlt and 0xFF
        hAlt = (input.hlAlt ushr 8) and 0xFF
        lAlt = input.hlAlt and 0xFF
        ix = input.ix
        iy = input.iy
        sp = input.sp
        pc = input.pc
        i = input.i
        r = input.r
        iff1 = input.iff1
        iff2 = input.iff2
        im = input.im
        halted = input.halted
        tStates = 0
    }

    private fun hex4(v: Int) = "0x${v.toString(16).padStart(4, '0').uppercase()}"

    companion object {
        const val FAILURE_LIMIT = 50
    }
}
