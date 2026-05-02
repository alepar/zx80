package ru.alepar.zx80.harness.programs

import kotlinx.serialization.Serializable

@Serializable
data class ProgramExpectation(
    val name: String,
    val load_at: Int,
    val entry: Int,
    val max_cycles: Long,
    val stop_on: String = "HALT", // currently only HALT supported
    val expect: ExpectedState,
)

@Serializable
data class ExpectedState(
    val pc: Int? = null,
    val halted: Boolean? = null,
    val a: Int? = null,
    val bc: Int? = null,
    val de: Int? = null,
    val hl: Int? = null,
    val sp: Int? = null,
    /** Map of "0xNNNN" → expected byte value at that address. */
    val memory: Map<String, Int>? = null,
)
