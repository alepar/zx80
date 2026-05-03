package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class IxOpsTest {
    @Test
    fun `installInto registers LD SP, HL straggler at main 0xF9`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat(d.main[0xF9]).isSameAs(LdSpHl)
    }
}
