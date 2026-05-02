package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.harness.SuiteResult
import ru.alepar.zx80.harness.programs.ExpectedState
import ru.alepar.zx80.harness.programs.ProgramExpectation

/** A single program plus its expected outcome. */
data class ProgramFixture(val bytes: ByteArray, val expectation: ProgramExpectation)

/**
 * Runs each program fixture, comparing final CPU+memory state to the declared expectation. With the
 * current empty Decoder, every program fails at first instruction with "no op for opcode 0x..".
 *
 * `details` shape: `{ "results": [{ "name", "status": "PASS"|"FAIL", "cycles", "reason"? }, ...]
 * }`.
 */
class ProgramsSuite(private val decoder: Decoder, private val programs: List<ProgramFixture>) :
    Suite {
    override val name: String = "programs"
    override val weight: Double = 0.1

    override fun run(): SuiteResult {
        val results = programs.map { runOne(it) }
        val passed = results.count { (it["status"] as JsonPrimitive).content == "PASS" }

        val details = buildJsonObject { put("results", JsonArray(results)) }
        return SuiteResult(
            name = name,
            weight = weight,
            passed = passed,
            total = programs.size,
            details = details,
        )
    }

    private fun runOne(p: ProgramFixture): JsonObject {
        val exp = p.expectation
        val cpu = Cpu().apply { pc = exp.entry }
        val mem = Memory()
        // Per-byte write to avoid loadAt's overflow precondition for any future fixture
        for ((offset, b) in p.bytes.withIndex()) {
            mem.write(exp.load_at + offset, b.toInt() and 0xFF)
        }

        var failure: String? = null
        try {
            while (true) {
                if (exp.stop_on == "HALT" && cpu.halted) break
                if (cpu.tStates >= exp.max_cycles) {
                    failure = "exceeded max_cycles=${exp.max_cycles}"
                    break
                }
                val opcodeByte = mem.read(cpu.pc)
                val op = decoder.main[opcodeByte]
                if (op == null) {
                    failure =
                        "no op for opcode 0x${opcodeByte.toString(16)} at pc=0x${cpu.pc.toString(16)}"
                    break
                }
                op.execute(cpu, mem)
            }
            if (failure == null) failure = checkExpectations(cpu, mem, exp.expect)
        } catch (t: Throwable) {
            failure = "exception: ${t::class.simpleName}: ${t.message}"
        }

        return buildJsonObject {
            put("name", JsonPrimitive(exp.name))
            put("status", JsonPrimitive(if (failure == null) "PASS" else "FAIL"))
            put("cycles", JsonPrimitive(cpu.tStates))
            failure?.let { put("reason", JsonPrimitive(it)) }
        }
    }

    private fun checkExpectations(cpu: Cpu, mem: Memory, e: ExpectedState): String? {
        e.pc?.let { if (cpu.pc != it) return "pc=${cpu.pc} expected=$it" }
        e.halted?.let { if (cpu.halted != it) return "halted=${cpu.halted} expected=$it" }
        e.a?.let { if (cpu.a != it) return "a=${cpu.a} expected=$it" }
        e.bc?.let { if (cpu.bc != it) return "bc=${cpu.bc} expected=$it" }
        e.de?.let { if (cpu.de != it) return "de=${cpu.de} expected=$it" }
        e.hl?.let { if (cpu.hl != it) return "hl=${cpu.hl} expected=$it" }
        e.sp?.let { if (cpu.sp != it) return "sp=${cpu.sp} expected=$it" }
        e.memory?.forEach { (addrStr, expectedByte) ->
            val addr = parseHex(addrStr)
            val actual = mem.read(addr)
            if (actual != expectedByte) return "mem[$addrStr]=$actual expected=$expectedByte"
        }
        return null
    }

    private fun parseHex(s: String): Int =
        if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2).toInt(16) else s.toInt()
}
