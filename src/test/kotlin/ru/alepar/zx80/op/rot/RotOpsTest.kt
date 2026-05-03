package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class RotOpsTest {
    @Test
    fun `installInto registers RLCA at 0x07, RRCA at 0x0F, RLA at 0x17, RRA at 0x1F`() {
        val d = Decoder()
        RotOps.installInto(d)
        assertThat(d.main[0x07]).isSameAs(Rlca)
        assertThat(d.main[0x0F]).isSameAs(Rrca)
        assertThat(d.main[0x17]).isSameAs(Rla)
        assertThat(d.main[0x1F]).isSameAs(Rra)
    }
}
