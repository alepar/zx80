package ru.alepar.zx80.harness.suites

import ru.alepar.zx80.harness.SuiteResult

/**
 * Pluggable scoring suite. Implementations: OpcodeCoverage, FuseSuite, ProgramsSuite. Each is
 * invoked once per `zx80 score` run.
 */
interface Suite {
    val name: String
    val weight: Double

    fun run(): SuiteResult
}
