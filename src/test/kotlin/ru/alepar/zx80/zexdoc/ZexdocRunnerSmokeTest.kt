package ru.alepar.zx80.zexdoc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ZexdocRunnerSmokeTest {

    @Test
    fun `runs ZEXDOC for a small budget without crashing`() {
        val rom = javaClass.getResourceAsStream("/zexdoc/ZEXDOC.COM")!!.readBytes()
        val out = StringBuilder()
        val runner = ZexdocRunner(out)
        // 1M cycles is well under a full run but enough to exercise the prologue.
        val result = runner.run(rom, maxCycles = 1_000_000L)
        assertThat(out.isNotEmpty())
            .withFailMessage("expected ZEXDOC to print prologue output; got empty")
            .isTrue()
        assertThat(result.cycles).withFailMessage("expected forward progress").isGreaterThan(0L)
    }

    @Test
    fun `warm-boot JP 0 halts cleanly`() {
        // Synthetic: at PC=0x0100 emit JP 0x0000. Runner sets mem[0]=HALT,
        // so jumping to 0x0000 should terminate the run.
        val mini = byteArrayOf(0xC3.toByte(), 0x00, 0x00) // JP 0x0000
        val out = StringBuilder()
        val runner = ZexdocRunner(out)
        val result = runner.run(mini, maxCycles = 100L)
        assertThat(result.halted)
            .withFailMessage("expected halt after JP 0x0000; result=%s", result)
            .isTrue()
    }
}
