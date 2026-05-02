package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.harness.SuiteResult

/**
 * Counts non-null entries in the seven dispatch tables. The denominator is the unweighted total (7
 * * 256 = 1792). A more nuanced "documented opcodes only" denominator can replace this once we have
 *   a list of which entries are intentionally undefined; for now this gives a monotonic gradient as
 *   we fill in tables.
 *
 * `details` shape: `{ "missing": ["<table>:0x<XX>", ...] }` (capped at MISSING_LIMIT entries;
 * sorted by table then opcode index).
 */
class OpcodeCoverage(private val decoder: Decoder) : Suite {
    override val name: String = "opcodes"
    override val weight: Double = 0.2

    override fun run(): SuiteResult {
        val tables =
            listOf(
                "main" to decoder.main,
                "cb" to decoder.cb,
                "ed" to decoder.ed,
                "dd" to decoder.dd,
                "fd" to decoder.fd,
                "ddcb" to decoder.ddcb,
                "fdcb" to decoder.fdcb,
            )
        val total = tables.sumOf { it.second.size }
        val passed = tables.sumOf { it.second.count { op -> op != null } }

        val missing =
            tables
                .flatMap { (label, table) ->
                    table
                        .withIndex()
                        .filter { (_, op) -> op == null }
                        .map { (i, _) -> "$label:0x${"%02X".format(i)}" }
                }
                .take(MISSING_LIMIT)

        val details = buildJsonObject {
            put("missing", JsonArray(missing.map { JsonPrimitive(it) }))
        }
        return SuiteResult(
            name = name,
            weight = weight,
            passed = passed,
            total = total,
            details = details,
        )
    }

    companion object {
        const val MISSING_LIMIT = 50
    }
}
