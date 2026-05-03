package ru.alepar.zx80.zexdoc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class BdosHandlerTest {

    @Test
    fun `syscall 2 prints char in E`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val cpu =
            Cpu().apply {
                c = 2
                e = 'X'.code
            }
        handler.handle(cpu, Memory())
        assertThat(out.toString()).isEqualTo("X")
    }

    @Test
    fun `syscall 9 prints dollar-terminated string at DE`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val mem = Memory()
        val s = "Hi!\$"
        for ((i, ch) in s.withIndex()) mem.write(0x200 + i, ch.code)
        val cpu =
            Cpu().apply {
                c = 9
                de = 0x200
            }
        handler.handle(cpu, mem)
        assertThat(out.toString()).isEqualTo("Hi!")
    }

    @Test
    fun `syscall 9 stops at first dollar even with later content`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val mem = Memory()
        val s = "OK\$junk"
        for ((i, ch) in s.withIndex()) mem.write(0x300 + i, ch.code)
        val cpu =
            Cpu().apply {
                c = 9
                de = 0x300
            }
        handler.handle(cpu, mem)
        assertThat(out.toString()).isEqualTo("OK")
    }

    @Test
    fun `unknown syscall is no-op`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val cpu = Cpu().apply { c = 42 }
        handler.handle(cpu, Memory())
        assertThat(out.toString()).isEqualTo("")
    }
}
