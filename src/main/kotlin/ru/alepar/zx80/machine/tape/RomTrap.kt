package ru.alepar.zx80.machine.tape

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

/**
 * Detects entry to the Sinclair 48K ROM's LD-BYTES routine at 0x0556 and replaces it with an
 * instant in-memory copy of the next tape block. Transparent to BASIC's LOAD command.
 *
 * Entry contract from the Sinclair ROM (per the disassembly):
 * - A' (alternate A register) contains the expected flag byte (0x00 = header, 0xFF = data). The ROM
 *   pushes A onto the stack, swaps in A', then later compares the first tape byte.
 * - DE = byte count to load (payload only — excludes flag byte and trailing parity).
 * - IX = destination address.
 * - Carry flag of the alternate F register (F') = 1 → LOAD, = 0 → VERIFY. The caller sets up AF'
 *   via `EX AF,AF'` semantics inside the ROM's LOAD entry sequence.
 *
 * Because BASIC's LOAD always passes carry-set (load mode) before calling LD-BYTES, and the EX
 * AF,AF' has already happened by the time PC reaches 0x0556, when we trap here we know:
 * - cpu.a holds the flag byte to match (post-EX).
 * - cpu.f's carry bit selects load vs verify.
 *
 * Exit contract:
 * - On success: Carry flag SET, Z flag CLEAR. A=parity (xor of all loaded bytes), DE=0, B=0.
 *   PC=top-of-stack (synthesised RET).
 * - On failure (flag mismatch, parity mismatch, or wrong length): Carry CLEAR. PC=top-of-stack.
 */
object RomTrap {

    const val LD_BYTES_ENTRY = 0x0556

    /** Mask for the carry flag in the F register. */
    private const val FLAG_C = 0x01

    /** Mask for the zero flag in the F register. */
    private const val FLAG_Z = 0x40

    /**
     * Attempt to trap the LD-BYTES routine. Returns true iff PC was at 0x0556 with a trappable
     * block available AND the trap consumed the call. Returns false in every other case (no tape,
     * not at the entry, trap disabled, current block is turbo, tape played out).
     */
    fun tryTrap(cpu: Cpu, mem: Memory, deck: TapeDeck): Boolean {
        if (cpu.pc != LD_BYTES_ENTRY) return false
        val blockData = deck.currentTrapData() ?: return false

        val expectedFlag = cpu.a and 0xFF
        val requestedLen = cpu.de
        val dest = cpu.ix and 0xFFFF
        val isLoad = (cpu.f and FLAG_C) != 0

        // A trappable block is: flag byte (1) + payload + parity byte (1). Length passed in DE
        // is the payload length plus the trailing parity (matches the ROM's own DE convention:
        // DE = bytes-on-tape-after-flag-byte = payload + parity).
        // If the block isn't large enough, signal failure (carry clear).
        if (blockData.isEmpty()) {
            failExit(cpu, mem)
            deck.advanceBlock()
            return true
        }

        val tapeFlag = blockData[0].toInt() and 0xFF
        if (tapeFlag != expectedFlag) {
            failExit(cpu, mem)
            // Block consumed even on mismatch (the real ROM keeps reading until length expires).
            deck.advanceBlock()
            return true
        }

        // Payload bytes on tape (excludes flag and parity). DE = payload count per ROM convention.
        val tapePayload = blockData.size - 2
        if (requestedLen > tapePayload) {
            // More bytes requested than the tape has — load whatever we can then fail.
            for (i in 0 until tapePayload) {
                mem.write((dest + i) and 0xFFFF, blockData[1 + i].toInt() and 0xFF)
            }
            failExit(cpu, mem)
            deck.advanceBlock()
            return true
        }

        // Compute parity (XOR of flag + payload) and compare to the parity byte on tape.
        // The block layout is: [flag][payload bytes... requestedLen of them][parity].
        // requestedLen is the payload count only; parity is at blockData[1 + requestedLen].
        val payloadLen = requestedLen
        var parity = tapeFlag
        if (isLoad) {
            for (i in 0 until payloadLen) {
                val b = blockData[1 + i].toInt() and 0xFF
                mem.write((dest + i) and 0xFFFF, b)
                parity = parity xor b
            }
        } else {
            // VERIFY: read from memory; the parity is computed over tape bytes regardless.
            for (i in 0 until payloadLen) {
                parity = parity xor (blockData[1 + i].toInt() and 0xFF)
            }
        }
        val tapeParity = blockData[1 + payloadLen].toInt() and 0xFF

        if (parity != tapeParity) {
            failExit(cpu, mem)
            deck.advanceBlock()
            return true
        }

        // Success exit. Per ROM semantics post-LD-BYTES:
        // DE = 0, B = 0, A = parity (== tapeParity here), Carry SET, Z CLEAR.
        cpu.de = 0
        cpu.b = 0
        cpu.a = parity and 0xFF
        cpu.f = (cpu.f or FLAG_C) and FLAG_Z.inv() and 0xFF
        popReturn(cpu, mem)
        deck.advanceBlock()
        return true
    }

    /** Set the CPU to a load-failure exit state: carry clear. PC restored from stack. */
    private fun failExit(cpu: Cpu, mem: Memory) {
        cpu.f = cpu.f and FLAG_C.inv() and 0xFF
        popReturn(cpu, mem)
    }

    /**
     * Pop a 16-bit return address from the stack and set PC to it. Mirrors a Z80 `RET` instruction.
     */
    private fun popReturn(cpu: Cpu, mem: Memory) {
        val lo = mem.read(cpu.sp and 0xFFFF)
        val hi = mem.read((cpu.sp + 1) and 0xFFFF)
        cpu.sp = (cpu.sp + 2) and 0xFFFF
        cpu.pc = (hi shl 8) or lo
    }
}
