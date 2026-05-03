package ru.alepar.zx80.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ZexdocCommandTest {
    @Test
    fun `SystemOutAppendable toString returns all appended text so the post-run output check works`() {
        val sink = ZexdocCommand.SystemOutAppendable()
        sink.append('Z')
        sink.append("80 ")
        sink.append("Tests complete\n", 0, "Tests complete\n".length)

        assertThat(sink.toString()).isEqualTo("Z80 Tests complete\n")
    }

    @Test
    fun `SystemOutAppendable toString captures null charseq as the literal null marker`() {
        val sink = ZexdocCommand.SystemOutAppendable()
        sink.append(null)
        assertThat(sink.toString()).isEqualTo("null")
    }
}
