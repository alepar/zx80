package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WritePolicyTest {
    @Test
    fun `OpenPolicy permits all addresses`() {
        assertThat(OpenPolicy.shouldWrite(0x0000)).isTrue
        assertThat(OpenPolicy.shouldWrite(0x3FFF)).isTrue
        assertThat(OpenPolicy.shouldWrite(0x4000)).isTrue
        assertThat(OpenPolicy.shouldWrite(0xFFFF)).isTrue
    }

    @Test
    fun `ReadOnlyBelow rejects writes below limit`() {
        val policy = ReadOnlyBelow(0x4000)
        assertThat(policy.shouldWrite(0x0000)).isFalse
        assertThat(policy.shouldWrite(0x1234)).isFalse
        assertThat(policy.shouldWrite(0x3FFF)).isFalse
    }

    @Test
    fun `ReadOnlyBelow permits writes at or above limit`() {
        val policy = ReadOnlyBelow(0x4000)
        assertThat(policy.shouldWrite(0x4000)).isTrue
        assertThat(policy.shouldWrite(0x8000)).isTrue
        assertThat(policy.shouldWrite(0xFFFF)).isTrue
    }
}
