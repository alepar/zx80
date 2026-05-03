package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class StackOpsTest {

    @Test
    fun `installInto registers PUSH rr at 0xC5 (BC), 0xD5 (DE), 0xE5 (HL), 0xF5 (AF)`() {
        val d = Decoder()
        StackOps.installInto(d)
        assertThat((d.main[0xC5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH BC")
        assertThat((d.main[0xD5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH DE")
        assertThat((d.main[0xE5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH HL")
        assertThat((d.main[0xF5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH AF")
    }

    @Test
    fun `installInto registers POP rr at 0xC1 (BC), 0xD1 (DE), 0xE1 (HL), 0xF1 (AF)`() {
        val d = Decoder()
        StackOps.installInto(d)
        assertThat((d.main[0xC1] as PopPair).mnemonic { 0 }).isEqualTo("POP BC")
        assertThat((d.main[0xD1] as PopPair).mnemonic { 0 }).isEqualTo("POP DE")
        assertThat((d.main[0xE1] as PopPair).mnemonic { 0 }).isEqualTo("POP HL")
        assertThat((d.main[0xF1] as PopPair).mnemonic { 0 }).isEqualTo("POP AF")
    }
}
