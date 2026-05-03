package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class StackOpsTest {
    @Test
    fun `installInto on empty Decoder installs nothing yet (skeleton)`() {
        val d = Decoder()
        StackOps.installInto(d)
        val totalInstalled =
            listOf(d.main, d.cb, d.ed, d.dd, d.fd, d.ddcb, d.fdcb).sumOf { table ->
                table.count { it != null }
            }
        assertThat(totalInstalled).isZero
    }
}
