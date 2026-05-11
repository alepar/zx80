package ru.alepar.zx80.machine.tape

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class RomTrapTest {

    private fun freshCpu(): Cpu = Cpu().also { it.reset() }

    /**
     * Sets up the CPU as if BASIC's LOAD has just CALLed LD-BYTES at 0x0556. Pushes a return
     * address onto the stack so the trap's synthesised RET takes the CPU back there.
     */
    private fun primeForLoad(
        cpu: Cpu,
        mem: Memory,
        flag: Int,
        length: Int,
        dest: Int,
        returnAddr: Int = 0x9000,
    ) {
        cpu.pc = 0x0556
        cpu.a = flag and 0xFF
        cpu.de = length and 0xFFFF
        cpu.ix = dest and 0xFFFF
        cpu.f = cpu.f or 0x01 // carry set = LOAD mode
        // Push return address
        cpu.sp = (cpu.sp - 2) and 0xFFFF
        mem.write(cpu.sp, returnAddr and 0xFF)
        mem.write((cpu.sp + 1) and 0xFFFF, (returnAddr ushr 8) and 0xFF)
    }

    @Test
    fun `trap with no tape returns false`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        cpu.pc = 0x0556
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isFalse
    }

    @Test
    fun `trap with PC not at LD_BYTES returns false`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0x01, 0x01)))))
        cpu.pc = 0x1234
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isFalse
    }

    @Test
    fun `tap single-byte payload loaded into memory at IX`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        // Block: [flag=0xFF, payload=0xAA, parity=flag^payload=0x55]
        val block = TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x55.toByte()))
        deck.loadTape(TapTapeFile(listOf(block)))
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)

        val ok = RomTrap.tryTrap(cpu, mem, deck)
        assertThat(ok).isTrue
        assertThat(mem.read(0x6000)).isEqualTo(0xAA)
        assertThat(cpu.pc).isEqualTo(0x9000)
        // Carry set on success
        assertThat(cpu.f and 0x01).isEqualTo(0x01)
        // Z clear on success
        assertThat(cpu.f and 0x40).isEqualTo(0)
        assertThat(cpu.de).isEqualTo(0)
    }

    @Test
    fun `tap 256-byte payload loaded into memory`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        // Flag + 256 payload bytes + parity
        val payload = ByteArray(256) { (it and 0xFF).toByte() }
        var parity = 0xFF
        for (b in payload) parity = parity xor (b.toInt() and 0xFF)
        val rawData = ByteArray(1 + 256 + 1)
        rawData[0] = 0xFF.toByte()
        payload.copyInto(rawData, 1)
        rawData[rawData.size - 1] = parity.toByte()
        deck.loadTape(TapTapeFile(listOf(TapBlock(rawData))))

        primeForLoad(cpu, mem, flag = 0xFF, length = 256, dest = 0x6000)
        val ok = RomTrap.tryTrap(cpu, mem, deck)
        assertThat(ok).isTrue
        for (i in 0 until 256) {
            assertThat(mem.read(0x6000 + i)).isEqualTo(i and 0xFF)
        }
    }

    @Test
    fun `multi-block tape loads on successive trap calls`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        val block1 = TapBlock(byteArrayOf(0x00, 0x11, 0x11)) // flag^payload=0x11, parity=0x11
        val block2 = TapBlock(byteArrayOf(0xFF.toByte(), 0x22, 0xDD.toByte())) // 0xFF^0x22=0xDD
        deck.loadTape(TapTapeFile(listOf(block1, block2)))

        primeForLoad(cpu, mem, flag = 0x00, length = 1, dest = 0x6000)
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isTrue
        assertThat(mem.read(0x6000)).isEqualTo(0x11)

        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x7000)
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isTrue
        assertThat(mem.read(0x7000)).isEqualTo(0x22)
    }

    @Test
    fun `parity mismatch returns carry clear`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        // Correct parity for 0xFF and 0xAA would be 0x55; we put 0x00 to force a mismatch.
        val block = TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x00))
        deck.loadTape(TapTapeFile(listOf(block)))
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)
        val ok = RomTrap.tryTrap(cpu, mem, deck)
        assertThat(ok).isTrue
        // Carry clear on failure
        assertThat(cpu.f and 0x01).isEqualTo(0)
        assertThat(cpu.pc).isEqualTo(0x9000)
    }

    @Test
    fun `flag mismatch returns carry clear and consumes block`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        // Tape has a HEADER block (flag=0x00) but caller expects DATA (flag=0xFF).
        val block = TapBlock(byteArrayOf(0x00, 0xAA.toByte(), 0xAA.toByte()))
        deck.loadTape(TapTapeFile(listOf(block)))
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)
        val ok = RomTrap.tryTrap(cpu, mem, deck)
        assertThat(ok).isTrue
        assertThat(cpu.f and 0x01).isEqualTo(0)
        // Block consumed (advance happens on mismatch — matches real-ROM "keep reading" behavior)
        assertThat(deck.currentBlockIndex()).isEqualTo(1)
    }

    @Test
    fun `tzx 0x10 standard block behaves identically to tap`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        val data = byteArrayOf(0xFF.toByte(), 0x42, (0xFF xor 0x42).toByte())
        val tzx = TzxTapeFile(listOf(TzxStandardData(1000, data)))
        deck.loadTape(tzx)
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)
        val ok = RomTrap.tryTrap(cpu, mem, deck)
        assertThat(ok).isTrue
        assertThat(mem.read(0x6000)).isEqualTo(0x42)
    }

    @Test
    fun `trapEnabled false bypasses the trap`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(
            TapTapeFile(listOf(TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x55.toByte()))))
        )
        deck.trapEnabled = false
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isFalse
        // Memory untouched
        assertThat(mem.read(0x6000)).isEqualTo(0)
    }

    @Test
    fun `tzx turbo block is not trappable and trap returns false`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(
            TzxTapeFile(
                listOf(
                    TzxTurboData(
                        2168,
                        667,
                        735,
                        855,
                        1710,
                        8063,
                        8,
                        1000,
                        byteArrayOf(0xFF.toByte(), 0xAA.toByte()),
                    )
                )
            )
        )
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isFalse
    }

    @Test
    fun `informational blocks are skipped to next data block`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(
            TzxTapeFile(
                listOf(
                    TzxTextDescription("Info"),
                    TzxGroupStart("Game"),
                    TzxStandardData(0, byteArrayOf(0xFF.toByte(), 0x77, (0xFF xor 0x77).toByte())),
                    TzxGroupEnd,
                )
            )
        )
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)
        val ok = RomTrap.tryTrap(cpu, mem, deck)
        assertThat(ok).isTrue
        assertThat(mem.read(0x6000)).isEqualTo(0x77)
    }

    @Test
    fun `tape played out returns false`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(
            TapTapeFile(listOf(TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x55.toByte()))))
        )
        // Advance past the only block
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x6000)
        RomTrap.tryTrap(cpu, mem, deck) // consume the block
        // Second trap attempt: no blocks remain
        primeForLoad(cpu, mem, flag = 0xFF, length = 1, dest = 0x7000)
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isFalse
    }

    @Test
    fun `pc set from synthesised RET equals pushed return address`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0x10, 0x10)))))
        primeForLoad(cpu, mem, flag = 0x00, length = 1, dest = 0x6000, returnAddr = 0x1234)
        assertThat(RomTrap.tryTrap(cpu, mem, deck)).isTrue
        assertThat(cpu.pc).isEqualTo(0x1234)
    }

    @Test
    fun `de is zero on successful exit`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(
            TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0xAA.toByte(), 0xAA.toByte()))))
        )
        primeForLoad(cpu, mem, flag = 0x00, length = 1, dest = 0x6000)
        RomTrap.tryTrap(cpu, mem, deck)
        assertThat(cpu.de).isEqualTo(0)
        assertThat(cpu.b).isEqualTo(0)
    }

    @Test
    fun `sp is restored after popping the return address`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        deck.loadTape(TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0x42, 0x42)))))
        primeForLoad(cpu, mem, flag = 0x00, length = 1, dest = 0x6000)
        val spBeforeTrap = cpu.sp
        RomTrap.tryTrap(cpu, mem, deck)
        // After RET pops 2 bytes, SP increments by 2
        assertThat(cpu.sp).isEqualTo((spBeforeTrap + 2) and 0xFFFF)
    }

    @Test
    fun `requested length larger than block returns failure`() {
        val cpu = freshCpu()
        val mem = Memory()
        val deck = TapeDeck()
        // Tape block has 1 payload byte + parity, but caller asks for 10 bytes
        deck.loadTape(
            TapTapeFile(listOf(TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x55.toByte()))))
        )
        primeForLoad(cpu, mem, flag = 0xFF, length = 10, dest = 0x6000)
        val ok = RomTrap.tryTrap(cpu, mem, deck)
        assertThat(ok).isTrue
        assertThat(cpu.f and 0x01).isEqualTo(0) // carry clear = failure
    }
}
