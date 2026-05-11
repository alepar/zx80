package ru.alepar.zx80.harness.suites

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GameMilestoneSuiteTest {

    @Test
    fun `GameMilestoneSuite runs without throwing`() {
        val result = GameMilestoneSuite().run()
        assertThat(result).isNotNull
    }

    @Test
    fun `GameMilestoneSuite has correct name and weight`() {
        val suite = GameMilestoneSuite()
        assertThat(suite.name).isEqualTo("game-milestone")
        assertThat(suite.weight).isEqualTo(0.05)
    }

    @Test
    fun `GameMilestoneSuite reports 5 total checks`() {
        val result = GameMilestoneSuite().run()
        assertThat(result.total).isEqualTo(5)
    }

    @Test
    fun `GameMilestoneSuite all 5 checks pass`() {
        val result = GameMilestoneSuite().run()
        assertThat(result.passed).isEqualTo(5)
    }
}
