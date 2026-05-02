package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScoreCommandTest {

    private fun newCli() =
        Zx80Cli()
            .subcommands(
                ScoreCommand(),
                RunCommand(),
                DisasmCommand(),
                BenchCommand(),
                ZexdocCommand(),
            )

    @Test
    fun `score on empty decoder reports SCORE colon zero`() {
        val result = newCli().test("score")
        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).contains("SCORE: 0.000")
        assertThat(result.stdout).contains("opcodes 0/")
        assertThat(result.stdout).contains("fuse 0/")
        assertThat(result.stdout).contains("programs 0/1")
    }

    @Test
    fun `score with suite filter runs only one suite`() {
        val result = newCli().test("score --suite=opcodes")
        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).contains("opcodes 0/")
        assertThat(result.stdout).doesNotContain("fuse")
    }

    @Test
    fun `unknown suite filter is rejected`() {
        val result = newCli().test("score --suite=bogus")
        // Clikt usually maps the IllegalArgumentException to a non-zero exit; we only assert
        // that the offending suite name surfaces somewhere (stderr or stdout) so this stays
        // robust against minor changes in Clikt's error formatting.
        assertThat(result.statusCode).isNotZero()
        assertThat((result.stderr + result.stdout).lowercase()).contains("bogus")
    }
}
