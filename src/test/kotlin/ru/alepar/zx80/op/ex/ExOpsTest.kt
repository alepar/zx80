package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class ExOpsTest {
    @Test
    fun `installInto registers ExAfAfAlt at 0x08, Exx at 0xD9, ExDeHl at 0xEB, ExSpHl at 0xE3`() {
        val d = Decoder()
        ExOps.installInto(d)
        assertThat(d.main[0x08]).isSameAs(ExAfAfAlt)
        assertThat(d.main[0xD9]).isSameAs(Exx)
        assertThat(d.main[0xEB]).isSameAs(ExDeHl)
        assertThat(d.main[0xE3]).isSameAs(ExSpHl)
    }
}
