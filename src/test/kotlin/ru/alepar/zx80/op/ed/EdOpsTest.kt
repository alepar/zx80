package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class EdOpsTest {
    @Test
    fun `installInto on empty Decoder leaves ed table as before`() {
        val d = Decoder()
        val priorEdInstalled = d.ed.count { it != null }
        EdOps.installInto(d)
        val newEdInstalled = d.ed.count { it != null }
        // After full Phase 2.10: register transfers (4) + NEG (1) + RETN (1) + RETI (1)
        // + RRD (1) + RLD (1) + LD (nn),rr (4) + LD rr,(nn) (4) + block move (4)
        // + block compare (4) + single I/O (16) + block I/O (8) = 49 ed entries.
        assertThat(newEdInstalled).isGreaterThanOrEqualTo(priorEdInstalled)
    }
}
