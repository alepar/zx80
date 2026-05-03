package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class IxCbOpsTest {
    @Test
    fun `installInto on empty Decoder leaves ddcb and fdcb tables empty (skeleton)`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddcbCount = d.ddcb.count { it != null }
        val fdcbCount = d.fdcb.count { it != null }
        assertThat(ddcbCount).isZero
        assertThat(fdcbCount).isZero
    }
}
