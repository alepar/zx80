package ru.alepar.zx80.harness

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Result of running one suite. `details` is suite-specific JSON that lands verbatim under the
 * suite's key in the composite score.json. Each Suite implementation documents its own `details`
 * JSON shape in its class KDoc.
 */
@Serializable
data class SuiteResult(
    val name: String,
    val weight: Double,
    val passed: Int,
    val total: Int,
    val details: JsonElement,
) {
    val ratio: Double
        get() = if (total == 0) 0.0 else passed.toDouble() / total
}
