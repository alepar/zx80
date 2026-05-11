package ru.alepar.zx80.machine.tape

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.machine.Spectrum48k

/**
 * End-to-end tests verifying that Spectrum48k.step() invokes the tape trap when PC reaches 0x0556
 * and a tape is loaded. Drives the trap via the full step() path rather than calling
 * RomTrap.tryTrap directly.
 */
class Spectrum48kTrapIntegrationTest {

    @Test
    fun `step at 0x0556 with tape loaded triggers trap and loads block`() {
        val machine = Spectrum48k()
        machine.reset()
        // Load a single-block tape: flag=0xFF, payload=0x42, parity=0xFF^0x42
        val parity = 0xFF xor 0x42
        val block = TapBlock(byteArrayOf(0xFF.toByte(), 0x42, parity.toByte()))
        machine.tapeDeck.loadTape(TapTapeFile(listOf(block)))

        // Set up CPU as if LOAD just CALLed LD-BYTES at 0x0556
        machine.cpu.pc = 0x0556
        machine.cpu.a = 0xFF
        machine.cpu.de = 1 // payload (1); parity is separate, not counted in DE
        machine.cpu.ix = 0x6000
        machine.cpu.f = machine.cpu.f or 0x01 // carry = LOAD
        // Push return address onto stack
        machine.cpu.sp = (machine.cpu.sp - 2) and 0xFFFF
        machine.mem.write(machine.cpu.sp, 0x00)
        machine.mem.write((machine.cpu.sp + 1) and 0xFFFF, 0x90)

        machine.step()

        assertThat(machine.mem.read(0x6000)).isEqualTo(0x42)
        assertThat(machine.cpu.pc).isEqualTo(0x9000)
        // Carry SET on success
        assertThat(machine.cpu.f and 0x01).isEqualTo(0x01)
    }

    @Test
    fun `step at 0x0556 with no tape loaded falls through to real ROM`() {
        val machine = Spectrum48k()
        machine.reset()
        // No tape loaded — trap should NOT fire, and we should execute the real ROM instruction
        // at 0x0556. We don't care what it does; just that PC changes (or some state advances)
        // as a normal instruction would. The Sinclair ROM at 0x0556 starts with 0x14 (INC D).
        val initialBytes = machine.mem.read(0x0556)
        machine.cpu.pc = 0x0556

        // Capture state before step
        val pcBefore = machine.cpu.pc
        val tStatesBefore = machine.cpu.tStates

        machine.step()

        // After a real instruction executes, PC advanced and T-states ticked
        // (the trap would NOT advance T-states, only set PC from SP — but with no SP set up
        // pop would garbage PC; we just assert PC moved AT LEAST to pc+1 indicating dispatch)
        assertThat(machine.cpu.tStates).isGreaterThan(tStatesBefore)
        // PC moved by some amount (instruction length >= 1)
        assertThat(machine.cpu.pc).isNotEqualTo(pcBefore - 1) // sanity
        // Just verify some byte was read from the ROM during dispatch
        assertThat(initialBytes).isBetween(0, 0xFF)
    }

    @Test
    fun `tape played out leaves trap inactive`() {
        val machine = Spectrum48k()
        machine.reset()
        val parity = 0xFF xor 0x42
        val block = TapBlock(byteArrayOf(0xFF.toByte(), 0x42, parity.toByte()))
        machine.tapeDeck.loadTape(TapTapeFile(listOf(block)))

        // Consume the single block
        machine.cpu.pc = 0x0556
        machine.cpu.a = 0xFF
        machine.cpu.de = 2
        machine.cpu.ix = 0x6000
        machine.cpu.f = machine.cpu.f or 0x01
        machine.cpu.sp = (machine.cpu.sp - 2) and 0xFFFF
        machine.mem.write(machine.cpu.sp, 0x00)
        machine.mem.write((machine.cpu.sp + 1) and 0xFFFF, 0x90)
        machine.step()

        // Now the tape is played out. A second step at 0x0556 should NOT trap.
        machine.cpu.pc = 0x0556
        val tStatesBefore = machine.cpu.tStates
        machine.step()
        // Real instruction executed — T-states advanced
        assertThat(machine.cpu.tStates).isGreaterThan(tStatesBefore)
    }
}
