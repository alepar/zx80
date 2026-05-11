package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.harness.SuiteResult
import ru.alepar.zx80.machine.tape.RomTrap
import ru.alepar.zx80.machine.tape.TapBlock
import ru.alepar.zx80.machine.tape.TapTapeFile
import ru.alepar.zx80.machine.tape.TapeDeck
import ru.alepar.zx80.machine.tape.TzxGroupEnd
import ru.alepar.zx80.machine.tape.TzxGroupStart
import ru.alepar.zx80.machine.tape.TzxStandardData
import ru.alepar.zx80.machine.tape.TzxTapeFile
import ru.alepar.zx80.machine.tape.TzxTextDescription
import ru.alepar.zx80.machine.tape.TzxTurboData

/**
 * Score harness suite for the ROM-trap loading path. Each check creates a synthetic tape with a
 * known payload, primes a fresh CPU/Memory as if BASIC's LOAD has just called LD-BYTES at 0x0556,
 * invokes [RomTrap.tryTrap], and asserts the expected memory + register outcome.
 *
 * Details shape: { "checks": [ { "name": "...", "passed": bool }, ... ] }
 */
class TapeTrapLoadSuite : Suite {
    override val name: String = "tape-trap-load"
    override val weight: Double = 0.10

    private data class Check(val name: String, val passed: Boolean)

    override fun run(): SuiteResult {
        val checks = mutableListOf<Check>()

        fun check(name: String, block: () -> Boolean) {
            val ok =
                try {
                    block()
                } catch (_: Exception) {
                    false
                }
            checks.add(Check(name, ok))
        }

        // 1. Single 1-byte payload at 0x6000
        check("tap-1-byte-payload") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(
                TapTapeFile(listOf(TapBlock(parityBlock(0xFF, byteArrayOf(0xAA.toByte())))))
            )
            prime(cpu, mem, flag = 0xFF, length = 2, dest = 0x6000)
            RomTrap.tryTrap(cpu, mem, deck) && mem.read(0x6000) == 0xAA
        }

        // 2. 256-byte payload
        check("tap-256-byte-block") {
            val (cpu, mem, deck) = harness()
            val payload = ByteArray(256) { (it and 0xFF).toByte() }
            deck.loadTape(TapTapeFile(listOf(TapBlock(parityBlock(0xFF, payload)))))
            prime(cpu, mem, flag = 0xFF, length = 257, dest = 0x6000)
            if (!RomTrap.tryTrap(cpu, mem, deck)) return@check false
            (0 until 256).all { mem.read(0x6000 + it) == (it and 0xFF) }
        }

        // 3. Header (flag=0x00) then data (flag=0xFF) on successive trap calls
        check("tap-multi-block-header-then-data") {
            val (cpu, mem, deck) = harness()
            val header = TapBlock(parityBlock(0x00, ByteArray(17) { 0x42 }))
            val data = TapBlock(parityBlock(0xFF, byteArrayOf(0x33, 0x44)))
            deck.loadTape(TapTapeFile(listOf(header, data)))
            prime(cpu, mem, flag = 0x00, length = 18, dest = 0x6000)
            if (!RomTrap.tryTrap(cpu, mem, deck)) return@check false
            prime(cpu, mem, flag = 0xFF, length = 3, dest = 0x7000)
            if (!RomTrap.tryTrap(cpu, mem, deck)) return@check false
            mem.read(0x6000) == 0x42 && mem.read(0x7000) == 0x33 && mem.read(0x7001) == 0x44
        }

        // 4. No tape loaded → trap returns false
        check("no-tape-no-op") {
            val (cpu, mem, deck) = harness()
            cpu.pc = 0x0556
            !RomTrap.tryTrap(cpu, mem, deck)
        }

        // 5. .tzx 0x10 block traps identically to .tap
        check("tzx-0x10-traps-identically") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(
                TzxTapeFile(listOf(TzxStandardData(1000, parityBlock(0xFF, byteArrayOf(0x77)))))
            )
            prime(cpu, mem, flag = 0xFF, length = 2, dest = 0x6000)
            RomTrap.tryTrap(cpu, mem, deck) && mem.read(0x6000) == 0x77
        }

        // 6. trapEnabled = false bypasses
        check("trap-disabled-falls-through") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(
                TapTapeFile(listOf(TapBlock(parityBlock(0xFF, byteArrayOf(0xAA.toByte())))))
            )
            deck.trapEnabled = false
            prime(cpu, mem, flag = 0xFF, length = 2, dest = 0x6000)
            !RomTrap.tryTrap(cpu, mem, deck) && mem.read(0x6000) == 0
        }

        // 7. Parity mismatch → carry clear (failure exit)
        check("parity-mismatch-carry-clear") {
            val (cpu, mem, deck) = harness()
            // Construct invalid parity (0x00) instead of 0xFF^0xAA=0x55
            deck.loadTape(
                TapTapeFile(listOf(TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x00))))
            )
            prime(cpu, mem, flag = 0xFF, length = 2, dest = 0x6000)
            val ok = RomTrap.tryTrap(cpu, mem, deck)
            ok && (cpu.f and 0x01) == 0
        }

        // 8. Flag mismatch (caller wants 0xFF, tape has 0x00) → failure but block consumed
        check("flag-mismatch-consumes-block") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(
                TapTapeFile(listOf(TapBlock(parityBlock(0x00, byteArrayOf(0xAA.toByte())))))
            )
            prime(cpu, mem, flag = 0xFF, length = 2, dest = 0x6000)
            RomTrap.tryTrap(cpu, mem, deck) &&
                (cpu.f and 0x01) == 0 &&
                deck.currentBlockIndex() == 1
        }

        // 9. PC after trap = return address pushed on stack
        check("pc-set-from-return-addr") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(TapTapeFile(listOf(TapBlock(parityBlock(0x00, byteArrayOf(0x11))))))
            prime(cpu, mem, flag = 0x00, length = 2, dest = 0x6000, returnAddr = 0x1234)
            RomTrap.tryTrap(cpu, mem, deck) && cpu.pc == 0x1234
        }

        // 10. DE = 0 after success
        check("de-zero-on-success") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(TapTapeFile(listOf(TapBlock(parityBlock(0x00, byteArrayOf(0x11))))))
            prime(cpu, mem, flag = 0x00, length = 2, dest = 0x6000)
            RomTrap.tryTrap(cpu, mem, deck) && cpu.de == 0
        }

        // 11. SP restored after RET pops 2 bytes
        check("sp-restored-after-ret") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(TapTapeFile(listOf(TapBlock(parityBlock(0x00, byteArrayOf(0x11))))))
            val spBeforePrime = cpu.sp
            prime(cpu, mem, flag = 0x00, length = 2, dest = 0x6000)
            val spAfterPrime = cpu.sp
            RomTrap.tryTrap(cpu, mem, deck) &&
                cpu.sp == ((spAfterPrime + 2) and 0xFFFF) &&
                spBeforePrime == ((spAfterPrime + 2) and 0xFFFF)
        }

        // 12. Turbo block is NOT trappable
        check("tzx-turbo-not-trappable") {
            val (cpu, mem, deck) = harness()
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
            prime(cpu, mem, flag = 0xFF, length = 2, dest = 0x6000)
            !RomTrap.tryTrap(cpu, mem, deck)
        }

        // 13. Informational blocks (text/group) are skipped to the next data block
        check("info-blocks-skipped") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(
                TzxTapeFile(
                    listOf(
                        TzxTextDescription("Info"),
                        TzxGroupStart("Game"),
                        TzxStandardData(0, parityBlock(0xFF, byteArrayOf(0x55))),
                        TzxGroupEnd,
                    )
                )
            )
            prime(cpu, mem, flag = 0xFF, length = 2, dest = 0x6000)
            RomTrap.tryTrap(cpu, mem, deck) && mem.read(0x6000) == 0x55
        }

        // 14. Requested length exceeds block → failure
        check("len-exceeds-block-fails") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(
                TapTapeFile(listOf(TapBlock(parityBlock(0xFF, byteArrayOf(0xAA.toByte())))))
            )
            prime(cpu, mem, flag = 0xFF, length = 100, dest = 0x6000)
            val ok = RomTrap.tryTrap(cpu, mem, deck)
            ok && (cpu.f and 0x01) == 0
        }

        // 15. Tape played out → trap returns false
        check("tape-played-out") {
            val (cpu, mem, deck) = harness()
            deck.loadTape(TapTapeFile(listOf(TapBlock(parityBlock(0x00, byteArrayOf(0x11))))))
            prime(cpu, mem, flag = 0x00, length = 2, dest = 0x6000)
            RomTrap.tryTrap(cpu, mem, deck) // consume
            prime(cpu, mem, flag = 0x00, length = 2, dest = 0x7000)
            !RomTrap.tryTrap(cpu, mem, deck)
        }

        val passed = checks.count { it.passed }
        val details = buildJsonObject {
            put(
                "checks",
                JsonArray(
                    checks.map { c ->
                        buildJsonObject {
                            put("name", JsonPrimitive(c.name))
                            put("passed", JsonPrimitive(c.passed))
                        }
                            as JsonObject
                    }
                ),
            )
        }
        return SuiteResult(
            name = name,
            weight = weight,
            passed = passed,
            total = checks.size,
            details = details,
        )
    }

    // ---- Helpers ----

    private data class Harness(val cpu: Cpu, val mem: Memory, val deck: TapeDeck)

    private fun harness(): Harness {
        val cpu = Cpu().also { it.reset() }
        val mem = Memory()
        val deck = TapeDeck()
        return Harness(cpu, mem, deck)
    }

    private fun prime(
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
        cpu.f = cpu.f or 0x01
        cpu.sp = (cpu.sp - 2) and 0xFFFF
        mem.write(cpu.sp, returnAddr and 0xFF)
        mem.write((cpu.sp + 1) and 0xFFFF, (returnAddr ushr 8) and 0xFF)
    }

    private fun parityBlock(flag: Int, payload: ByteArray): ByteArray {
        var parity = flag
        for (b in payload) parity = parity xor (b.toInt() and 0xFF)
        val data = ByteArray(1 + payload.size + 1)
        data[0] = flag.toByte()
        payload.copyInto(data, 1)
        data[data.size - 1] = parity.toByte()
        return data
    }
}
