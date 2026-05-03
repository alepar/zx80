package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class BranchOpsTest {

    @Test
    fun `installInto on empty Decoder installs nothing yet (skeleton)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        // WUs 2.4-2..5 add real registrations; for now just verify
        // the call doesn't crash and the function exists.
        val totalInstalled =
            listOf(d.main, d.cb, d.ed, d.dd, d.fd, d.ddcb, d.fdcb).sumOf { table ->
                table.count { it != null }
            }
        assertThat(totalInstalled).isZero
    }
}
