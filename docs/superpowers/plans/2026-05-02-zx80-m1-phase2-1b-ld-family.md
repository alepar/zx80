# ZX Spectrum Emulator — Phase 2.1b Implementation Plan (LD family)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the entire Z80 LD instruction family — ~80 new opcode positions across ~12 Op classes — building on Phase 2.1a's foundation. After this plan, opcodes count climbs from 11 to ~91 and the composite score moves from 0.106 toward 0.35–0.50.

**Architecture:** Each Op follows the established Phase 2.1a pattern (object/class implementing `Op`, advance PC, call `cpu.bumpR()`, add T-states, do work). Parameterized classes for opcode families with shared behavior (`LdRegReg(src, dst)`, `LdPairImm(pair)`, etc.). New foundation: `Reg.fromBits(bits)` companion helper for opcode-bit-pattern → Reg mapping, and a new `RegPair` enum (BC/DE/HL/SP) with `read`/`write`/`fromBits`. All ops registered via a single `LdOps.installInto(decoder)` fragment wired into `OpTableBuilder.build()`.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-1b-ld-family-design.md`
**Branch:** `opus-4.7` (the de facto main; do NOT merge to `master`)
**Base commit:** `98e1cfd` (the spec commit)

---

## Universal patterns (apply to every Op in this plan)

These conventions come from Phase 2.1a and apply unchanged here:

- Each Op is in `ru.alepar.zx80.op.ld.*`. Object singletons for stateless ops; classes with constructor params for parameterized families.
- `operandLength: Int` is set truthfully (0 / 1 / 2). Read by no one currently — informational metadata for a future disassembler. Each Op manually advances PC by `1 + operandLength`.
- `baseCycles: Int` per Z80 spec (see spec doc's per-Op table).
- `execute()` body convention:
  1. Read any operand bytes from `mem.read(cpu.pc + 1)` etc. BEFORE advancing PC.
  2. Do the actual work.
  3. Advance PC: `cpu.pc = (cpu.pc + 1 + operandLength) and 0xFFFF`.
  4. Increment R: `cpu.bumpR()` (default by=1; LD ops in this batch are all unprefixed).
  5. Add T-states: `cpu.tStates += baseCycles`.
- None of the LD ops in this batch touches flags. Per-Op tests verify this.
- Mnemonic format: `"LD <dst>, <src>"` (uppercase mnemonic + comma-then-space). For ops with operands: use `OperandFetcher` (currently the test passes `{ 0 }`; that's enough to cover the contract).
- Tests use AssertJ + JUnit 5 + backticked English test names, mirroring Phase 2.1a.

---

## File Structure

By the end of this plan the following files exist or have been modified.

**New files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/
  RegPair.kt                        # WU 2.1b-1
src/main/kotlin/ru/alepar/zx80/op/ld/
  LdRegReg.kt                       # WU 2.1b-2
  LdRegFromHl.kt                    # WU 2.1b-2
  LdHlFromReg.kt                    # WU 2.1b-2
  LdRegImm.kt                       # WU 2.1b-3
  LdHlMemImm.kt                     # WU 2.1b-3
  LdPairImm.kt                      # WU 2.1b-4
  LdAFromBcDe.kt                    # WU 2.1b-5
  LdBcDeFromA.kt                    # WU 2.1b-5
  LdAFromAddr.kt                    # WU 2.1b-5
  LdAddrFromA.kt                    # WU 2.1b-5
  LdHlFromAddr.kt                   # WU 2.1b-5
  LdAddrFromHl.kt                   # WU 2.1b-5
  LdOps.kt                          # WU 2.1b-2 (created), extended in 3/4/5

src/test/kotlin/ru/alepar/zx80/cpu/
  RegFromBitsTest.kt                # WU 2.1b-1 (added to RegTest if it exists, else new)
  RegPairTest.kt                    # WU 2.1b-1
src/test/kotlin/ru/alepar/zx80/op/ld/
  LdRegRegTest.kt                   # WU 2.1b-2
  LdRegFromHlTest.kt                # WU 2.1b-2
  LdHlFromRegTest.kt                # WU 2.1b-2
  LdRegImmTest.kt                   # WU 2.1b-3
  LdHlMemImmTest.kt                 # WU 2.1b-3
  LdPairImmTest.kt                  # WU 2.1b-4
  LdAFromBcDeTest.kt                # WU 2.1b-5
  LdBcDeFromATest.kt                # WU 2.1b-5
  LdAFromAddrTest.kt                # WU 2.1b-5
  LdAddrFromATest.kt                # WU 2.1b-5
  LdHlFromAddrTest.kt               # WU 2.1b-5
  LdAddrFromHlTest.kt               # WU 2.1b-5
  LdOpsTest.kt                      # WU 2.1b-2 (created), extended in 3/4/5
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/Reg.kt          # WU 2.1b-1 (add fromBits companion helper)
src/main/kotlin/ru/alepar/zx80/op/Op.kt            # WU 2.1b-1 (clarify operandLength KDoc)
src/test/kotlin/ru/alepar/zx80/cpu/RegTest.kt      # WU 2.1b-1 (add fromBits tests)
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt # WU 2.1b-2 (add LdOps.installInto)
```

---

## WU 2.1b-1 — Foundation: Reg.fromBits + RegPair + Op.operandLength doc

No Op classes; pure foundation.

### Task 1: Add Reg.fromBits companion helper

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Reg.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/RegTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `src/test/kotlin/ru/alepar/zx80/cpu/RegTest.kt`:

```kotlin
@Test
fun `fromBits maps Z80 r-field bit patterns to Reg`() {
    assertThat(Reg.fromBits(0)).isEqualTo(Reg.B)
    assertThat(Reg.fromBits(1)).isEqualTo(Reg.C)
    assertThat(Reg.fromBits(2)).isEqualTo(Reg.D)
    assertThat(Reg.fromBits(3)).isEqualTo(Reg.E)
    assertThat(Reg.fromBits(4)).isEqualTo(Reg.H)
    assertThat(Reg.fromBits(5)).isEqualTo(Reg.L)
    assertThat(Reg.fromBits(7)).isEqualTo(Reg.A)
}

@Test
fun `fromBits rejects bits=6 because (HL) is not a register`() {
    org.assertj.core.api.Assertions.assertThatThrownBy { Reg.fromBits(6) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("(HL)")
}

@Test
fun `fromBits rejects bits outside 0..7`() {
    org.assertj.core.api.Assertions.assertThatThrownBy { Reg.fromBits(8) }
        .isInstanceOf(IllegalArgumentException::class.java)
    org.assertj.core.api.Assertions.assertThatThrownBy { Reg.fromBits(-1) }
        .isInstanceOf(IllegalArgumentException::class.java)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests RegTest`
Expected: FAIL with `Unresolved reference: fromBits`.

- [ ] **Step 3: Add the companion helper**

In `src/main/kotlin/ru/alepar/zx80/cpu/Reg.kt`, add a companion object inside the enum class (after the `write` method):

```kotlin
companion object {
    /**
     * Map a Z80 r-field bit pattern (0..7) to the corresponding [Reg].
     * Bits=6 means `(HL)` — a memory access, not a register — and is
     * rejected. Callers handling `(HL)`-bearing opcodes branch on bits
     * before calling this.
     */
    fun fromBits(bits: Int): Reg {
        require(bits in 0..7) { "bits must be in 0..7; got $bits" }
        require(bits != 6) { "bits=6 is (HL), not a register" }
        return when (bits) {
            0 -> B; 1 -> C; 2 -> D; 3 -> E
            4 -> H; 5 -> L; 7 -> A
            else -> error("unreachable")
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests RegTest`
Expected: all RegTest tests pass (4 prior + 3 new = 7).

### Task 2: Create RegPair enum

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/RegPairTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RegPairTest {

    @Test
    fun `read returns the corresponding Cpu pair value`() {
        val cpu = Cpu().apply {
            bc = 0x1122; de = 0x3344; hl = 0x5566; sp = 0x7788
        }
        assertThat(RegPair.BC.read(cpu)).isEqualTo(0x1122)
        assertThat(RegPair.DE.read(cpu)).isEqualTo(0x3344)
        assertThat(RegPair.HL.read(cpu)).isEqualTo(0x5566)
        assertThat(RegPair.SP.read(cpu)).isEqualTo(0x7788)
    }

    @Test
    fun `write updates the corresponding Cpu pair value`() {
        val cpu = Cpu()
        RegPair.BC.write(cpu, 0x1122)
        RegPair.DE.write(cpu, 0x3344)
        RegPair.HL.write(cpu, 0x5566)
        RegPair.SP.write(cpu, 0x7788)
        assertThat(cpu.bc).isEqualTo(0x1122)
        assertThat(cpu.de).isEqualTo(0x3344)
        assertThat(cpu.hl).isEqualTo(0x5566)
        assertThat(cpu.sp).isEqualTo(0x7788)
    }

    @Test
    fun `write masks to 16 bits`() {
        val cpu = Cpu()
        RegPair.BC.write(cpu, 0x12345)   // 17-bit; top bit must be discarded
        assertThat(cpu.bc).isEqualTo(0x2345)

        RegPair.SP.write(cpu, -1)
        assertThat(cpu.sp).isEqualTo(0xFFFF)
    }

    @Test
    fun `mnemonic matches canonical Z80 pair name`() {
        assertThat(RegPair.BC.mnemonic).isEqualTo("BC")
        assertThat(RegPair.DE.mnemonic).isEqualTo("DE")
        assertThat(RegPair.HL.mnemonic).isEqualTo("HL")
        assertThat(RegPair.SP.mnemonic).isEqualTo("SP")
    }

    @Test
    fun `fromBits maps register-pair bit patterns BC=0, DE=1, HL=2, SP=3`() {
        assertThat(RegPair.fromBits(0)).isEqualTo(RegPair.BC)
        assertThat(RegPair.fromBits(1)).isEqualTo(RegPair.DE)
        assertThat(RegPair.fromBits(2)).isEqualTo(RegPair.HL)
        assertThat(RegPair.fromBits(3)).isEqualTo(RegPair.SP)
    }

    @Test
    fun `fromBits masks to lowest 2 bits`() {
        assertThat(RegPair.fromBits(0xFC)).isEqualTo(RegPair.BC)   // 0xFC and 3 = 0
        assertThat(RegPair.fromBits(0xFD)).isEqualTo(RegPair.DE)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests RegPairTest`
Expected: FAIL with `Unresolved reference: RegPair`.

- [ ] **Step 3: Implement RegPair**

`src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt`:

```kotlin
package ru.alepar.zx80.cpu

/**
 * The four 16-bit register pairs that LD `rr,nn` and similar opcodes
 * can name. Uses the BC/DE/HL/SP encoding (bits 4-5 of the opcode).
 *
 * Note: PUSH/POP use a different encoding where bits=11 means AF
 * instead of SP. That's a separate concern and lives in its own enum
 * (or is added as an alternate `fromPushPopBits` factory) when those
 * opcodes land.
 */
enum class RegPair(val mnemonic: String) {
    BC("BC"),
    DE("DE"),
    HL("HL"),
    SP("SP");

    fun read(cpu: Cpu): Int = when (this) {
        BC -> cpu.bc
        DE -> cpu.de
        HL -> cpu.hl
        SP -> cpu.sp
    }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFFFF
        when (this) {
            BC -> cpu.bc = v
            DE -> cpu.de = v
            HL -> cpu.hl = v
            SP -> cpu.sp = v
        }
    }

    companion object {
        /** Map a Z80 register-pair bit pattern (low 2 bits used) to [RegPair]. */
        fun fromBits(bits: Int): RegPair = when (bits and 0x03) {
            0 -> BC
            1 -> DE
            2 -> HL
            3 -> SP
            else -> error("unreachable")
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests RegPairTest`
Expected: 6 tests pass.

### Task 3: Update Op.operandLength KDoc

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/Op.kt`

- [ ] **Step 1: Read the existing Op interface**

Read: `src/main/kotlin/ru/alepar/zx80/op/Op.kt`

Locate the existing KDoc for `operandLength` (currently brief, says "Bytes after the opcode byte that this instruction consumes (n, nn, d).").

- [ ] **Step 2: Replace with clearer KDoc**

Replace the `operandLength` KDoc with:

```kotlin
/**
 * Bytes after the opcode byte (and any prefixes) that this instruction
 * consumes — the operand bytes (n, nn, d). Informational metadata; not
 * read by the runtime today, but reserved for future disassembler use.
 *
 * Each Op is responsible for advancing PC inside `execute()` by
 * `1 + prefixBytes + operandLength`.
 */
val operandLength: Int
```

- [ ] **Step 3: Verify nothing else broke**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL with all prior tests still passing (no behavior changed; just doc).

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with 102 prior + 3 (Reg.fromBits) + 6 (RegPair) = 111 tests passing.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Reg.kt \
        src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt \
        src/main/kotlin/ru/alepar/zx80/op/Op.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/RegTest.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/RegPairTest.kt
git commit -m "feat(cpu): Reg.fromBits + RegPair enum (Phase 2.1b foundation)

Reg.fromBits maps Z80 r-field bit patterns (0..7) to Reg, rejecting
bits=6 = (HL). RegPair models the four 16-bit pairs (BC/DE/HL/SP) used
by LD rr,nn and similar opcodes. Op.operandLength KDoc clarified."
```

---

## WU 2.1b-2 — LD r,r' family (the 0x40-0x7F block)

3 Op classes: `LdRegReg(src, dst)`, `LdRegFromHl(dst)`, `LdHlFromReg(src)`. 63 opcode positions. Plus the `LdOps.installInto` skeleton + wiring into `OpTableBuilder`.

### Task 1: LdRegReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdRegReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdRegRegTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegRegTest {
    @Test
    fun `LD B, C copies C into B and leaves flags untouched`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            b = 0x11; c = 0x22; f = 0xAA
        }
        LdRegReg(src = Reg.C, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x22)
        assertThat(cpu.c).isEqualTo(0x22)   // src untouched
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
        assertThat(cpu.f).isEqualTo(0xAA)   // flags untouched
    }

    @Test
    fun `LD A, A is a no-op on register state but still ticks pc, r, tStates`() {
        val cpu = Cpu().apply { a = 0x42; pc = 0x100; tStates = 0L }
        LdRegReg(src = Reg.A, dst = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic format is LD dst, src`() {
        assertThat(LdRegReg(src = Reg.C, dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, C")
        assertThat(LdRegReg(src = Reg.A, dst = Reg.L).mnemonic { 0 }).isEqualTo("LD L, A")
    }

    @Test
    fun `operandLength is 0 and baseCycles is 4`() {
        val op = LdRegReg(src = Reg.B, dst = Reg.C)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdRegRegTest`
Expected: FAIL with `Unresolved reference: LdRegReg`.

- [ ] **Step 3: Implement LdRegReg**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD dst, src` — copies an 8-bit register into another. The 49 reg-to-reg
 * combinations populate the 0x40-0x7F block (minus the (HL) variants and
 * the HALT slot at 0x76). 4 T-states. No flag changes.
 */
class LdRegReg(private val src: Reg, private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdRegRegTest`
Expected: 4 tests pass.

### Task 2: LdRegFromHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdRegFromHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdRegFromHlTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegFromHlTest {
    @Test
    fun `LD B, (HL) copies byte at HL into B`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000; b = 0x00; f = 0xAA
        }
        val mem = Memory().apply { write(0x4000, 0x42) }
        LdRegFromHl(dst = Reg.B).execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4000)   // hl unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)       // flags untouched
    }

    @Test
    fun `LD A, (HL) works for the A destination`() {
        val cpu = Cpu().apply { hl = 0x100; a = 0 }
        val mem = Memory().apply { write(0x100, 0x99) }
        LdRegFromHl(dst = Reg.A).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRegFromHl(dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, (HL)")
        assertThat(LdRegFromHl(dst = Reg.A).mnemonic { 0 }).isEqualTo("LD A, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdRegFromHl(dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdRegFromHlTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdRegFromHl**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, (HL)` — copies the byte at memory[HL] into register r. 7 T-states.
 * No flag changes. HL is not modified.
 */
class LdRegFromHl(private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, mem.read(cpu.hl))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, (HL)"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdRegFromHlTest`
Expected: 4 tests pass.

### Task 3: LdHlFromReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdHlFromReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdHlFromRegTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdHlFromRegTest {
    @Test
    fun `LD (HL), B writes B into memory at HL`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000; b = 0x42; f = 0xAA
        }
        val mem = Memory()
        LdHlFromReg(src = Reg.B).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.b).isEqualTo(0x42)         // src unchanged
        assertThat(cpu.hl).isEqualTo(0x4000)       // hl unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)          // flags untouched
    }

    @Test
    fun `LD (HL), A works for the A source`() {
        val cpu = Cpu().apply { hl = 0x100; a = 0x99 }
        val mem = Memory()
        LdHlFromReg(src = Reg.A).execute(cpu, mem)
        assertThat(mem.read(0x100)).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdHlFromReg(src = Reg.B).mnemonic { 0 }).isEqualTo("LD (HL), B")
        assertThat(LdHlFromReg(src = Reg.A).mnemonic { 0 }).isEqualTo("LD (HL), A")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdHlFromReg(src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdHlFromRegTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdHlFromReg**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (HL), r` — writes register r into memory[HL]. 7 T-states.
 * No flag changes.
 */
class LdHlFromReg(private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        mem.write(cpu.hl, src.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (HL), ${src.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdHlFromRegTest`
Expected: 4 tests pass.

### Task 4: LdOps fragment + wire into OpTableBuilder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Write the failing LdOps test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg

class LdOpsTest {

    @Test
    fun `installInto registers all 49 LD r,r prime opcodes in the 0x40-0x7F block`() {
        val d = Decoder()
        LdOps.installInto(d)
        // Spot-check several reg-to-reg opcodes by mnemonic.
        // 0x40 = LD B, B ; 0x41 = LD B, C ; 0x47 = LD B, A
        assertThat((d.main[0x40] as LdRegReg).mnemonic { 0 }).isEqualTo("LD B, B")
        assertThat((d.main[0x41] as LdRegReg).mnemonic { 0 }).isEqualTo("LD B, C")
        assertThat((d.main[0x47] as LdRegReg).mnemonic { 0 }).isEqualTo("LD B, A")
        // 0x78 = LD A, B ; 0x7F = LD A, A
        assertThat((d.main[0x78] as LdRegReg).mnemonic { 0 }).isEqualTo("LD A, B")
        assertThat((d.main[0x7F] as LdRegReg).mnemonic { 0 }).isEqualTo("LD A, A")
    }

    @Test
    fun `installInto registers LD r,(HL) opcodes (0x46, 0x4E, 0x56, 0x5E, 0x66, 0x6E, 0x7E)`() {
        val d = Decoder()
        LdOps.installInto(d)
        assertThat((d.main[0x46] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD B, (HL)")
        assertThat((d.main[0x4E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD C, (HL)")
        assertThat((d.main[0x56] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD D, (HL)")
        assertThat((d.main[0x5E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD E, (HL)")
        assertThat((d.main[0x66] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD H, (HL)")
        assertThat((d.main[0x6E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD L, (HL)")
        assertThat((d.main[0x7E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD A, (HL)")
    }

    @Test
    fun `installInto registers LD (HL),r opcodes (0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x77)`() {
        val d = Decoder()
        LdOps.installInto(d)
        assertThat((d.main[0x70] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), B")
        assertThat((d.main[0x71] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), C")
        assertThat((d.main[0x72] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), D")
        assertThat((d.main[0x73] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), E")
        assertThat((d.main[0x74] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), H")
        assertThat((d.main[0x75] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), L")
        assertThat((d.main[0x77] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), A")
    }

    @Test
    fun `installInto leaves the HALT slot at 0x76 untouched`() {
        // 0x76 is HALT in the 0x40-0x7F block; LdOps must skip it.
        val d = Decoder()
        LdOps.installInto(d)
        assertThat(d.main[0x76]).isNull()   // LdOps doesn't touch this; MiscOps will
    }

    @Test
    fun `installInto registers exactly 63 opcodes in the 0x40-0x7F block`() {
        val d = Decoder()
        LdOps.installInto(d)
        val count = (0x40..0x7F).count { d.main[it] != null }
        // 64 slots in 0x40-0x7F; minus 1 (HALT at 0x76) = 63
        assertThat(count).isEqualTo(63)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdOpsTest`
Expected: FAIL with `Unresolved reference: LdOps`.

- [ ] **Step 3: Implement LdOps with the 0x40-0x7F block registration**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg

/**
 * Registers the LD Op family into the decoder. Called by
 * [ru.alepar.zx80.op.OpTableBuilder]. Subsequent WUs extend this
 * fragment with more LD variants (LD r,n; LD rr,nn; LD with memory
 * addresses).
 *
 * The 0x40-0x7F block (minus 0x76=HALT) is the LD r,r' table. Bits 3-5
 * encode dst; bits 0-2 encode src. dst==6 means LD (HL),r; src==6 means
 * LD r,(HL).
 */
object LdOps {
    fun installInto(d: Decoder) {
        installRegToReg(d)
    }

    private fun installRegToReg(d: Decoder) {
        for (dstBits in 0..7) {
            for (srcBits in 0..7) {
                val opcode = 0x40 or (dstBits shl 3) or srcBits
                if (opcode == 0x76) continue   // HALT slot — handled by MiscOps
                d.main[opcode] = when {
                    dstBits == 6 && srcBits == 6 -> error("unreachable; opcode 0x76 was filtered")
                    dstBits == 6 -> LdHlFromReg(src = Reg.fromBits(srcBits))
                    srcBits == 6 -> LdRegFromHl(dst = Reg.fromBits(dstBits))
                    else -> LdRegReg(src = Reg.fromBits(srcBits), dst = Reg.fromBits(dstBits))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify LdOps tests pass**

Run: `./gradlew test --tests LdOpsTest`
Expected: 5 tests pass.

- [ ] **Step 5: Wire LdOps into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:

1. Add: `import ru.alepar.zx80.op.ld.LdOps`
2. In `build()`, add `LdOps.installInto(d)` right after `ExOps.installInto(d)`.

The file should look like:

```kotlin
package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.ex.ExOps
import ru.alepar.zx80.op.ld.LdOps
import ru.alepar.zx80.op.misc.MiscOps

object OpTableBuilder {

    fun build(): Decoder {
        val d = Decoder()
        MiscOps.installInto(d)
        ExOps.installInto(d)
        LdOps.installInto(d)
        return d
    }
}
```

### Task 5: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with all prior tests + 4 (LdRegReg) + 4 (LdRegFromHl) + 4 (LdHlFromReg) + 5 (LdOps) = 17 new tests, totaling ~128.

- [ ] **Step 2: End-to-end smoke**

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count is now 11 + 63 = 74. Composite score climbs significantly. Capture exact headline.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ld/ \
        src/test/kotlin/ru/alepar/zx80/op/ld/ \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(op/ld): LD r,r' family — the 0x40-0x7F block (63 opcodes)

LdRegReg parameterized over (src, dst) covers 49 reg-to-reg opcodes;
LdRegFromHl and LdHlFromReg cover the 14 (HL) variants. HALT at 0x76
is correctly skipped (handled by MiscOps). 7 R-cycles per op for
(HL) variants; 4 for register-only."
```

---

## WU 2.1b-3 — LD r,n family (immediate to register + LD (HL),n)

2 Op classes: `LdRegImm(dst)` for `LD r,n` (8 opcodes including `LD A,n`), `LdHlMemImm` for `LD (HL),n`.

### Task 1: LdRegImm

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdRegImm.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdRegImmTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegImmTest {
    @Test
    fun `LD B, n reads the immediate byte into B and advances pc by 2`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            b = 0x00; f = 0xAA
        }
        val mem = Memory().apply {
            write(0x100, 0x06)   // LD B, n opcode
            write(0x101, 0x42)   // immediate value
        }
        LdRegImm(dst = Reg.B).execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD A, n works for A`() {
        val cpu = Cpu().apply { pc = 0x200 }
        val mem = Memory().apply {
            write(0x200, 0x3E)
            write(0x201, 0x99)
        }
        LdRegImm(dst = Reg.A).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x99)
        assertThat(cpu.pc).isEqualTo(0x202)
    }

    @Test
    fun `LD r, n wraps pc mod 64K`() {
        val cpu = Cpu().apply { pc = 0xFFFE }
        val mem = Memory().apply {
            write(0xFFFE, 0x06)
            write(0xFFFF, 0x42)
        }
        LdRegImm(dst = Reg.B).execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x0000)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRegImm(dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, n")
        assertThat(LdRegImm(dst = Reg.A).mnemonic { 0 }).isEqualTo("LD A, n")
    }

    @Test
    fun `operandLength=1, baseCycles=7`() {
        val op = LdRegImm(dst = Reg.B)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdRegImmTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdRegImm**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, n` — loads an 8-bit immediate into register r. 7 T-states.
 * No flag changes. PC advances by 2 (opcode + immediate byte).
 */
class LdRegImm(private val dst: Reg) : Op {
    override val operandLength = 1
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        dst.write(cpu, n)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, n"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdRegImmTest`
Expected: 5 tests pass.

### Task 2: LdHlMemImm

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdHlMemImm.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdHlMemImmTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdHlMemImmTest {
    @Test
    fun `LD (HL), n writes immediate byte into memory at HL`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000; f = 0xAA
        }
        val mem = Memory().apply {
            write(0x100, 0x36)   // LD (HL), n opcode
            write(0x101, 0x42)   // immediate value
        }
        LdHlMemImm.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4000)   // hl unchanged
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdHlMemImm.mnemonic { 0 }).isEqualTo("LD (HL), n")
    }

    @Test
    fun `operandLength=1, baseCycles=10`() {
        assertThat(LdHlMemImm.operandLength).isEqualTo(1)
        assertThat(LdHlMemImm.baseCycles).isEqualTo(10)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdHlMemImmTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdHlMemImm**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (HL), n` — writes an 8-bit immediate into memory at HL.
 * 10 T-states. No flag changes.
 */
object LdHlMemImm : Op {
    override val operandLength = 1
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        mem.write(cpu.hl, n)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (HL), n"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdHlMemImmTest`
Expected: 3 tests pass.

### Task 3: Extend LdOps + LdOpsTest

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ld/LdOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ld/LdOpsTest.kt`

- [ ] **Step 1: Add LdRegImm + LdHlMemImm registrations**

In `LdOps.kt`, add a new private method and call it from `installInto`:

```kotlin
object LdOps {
    fun installInto(d: Decoder) {
        installRegToReg(d)
        installImmediate(d)
    }

    private fun installRegToReg(d: Decoder) {
        // ... existing code unchanged ...
    }

    private fun installImmediate(d: Decoder) {
        // LD r, n — opcode pattern: 00 rrr 110 where rrr is dst register bits.
        // Opcodes: 0x06 (B), 0x0E (C), 0x16 (D), 0x1E (E), 0x26 (H), 0x2E (L), 0x3E (A).
        for (dstBits in 0..7) {
            if (dstBits == 6) continue   // 0x36 = LD (HL), n, registered separately
            val opcode = 0x06 or (dstBits shl 3)
            d.main[opcode] = LdRegImm(dst = Reg.fromBits(dstBits))
        }
        d.main[0x36] = LdHlMemImm
    }
}
```

(Make sure the imports `ru.alepar.zx80.cpu.Reg` is already present from WU 2.1b-2 — it is.)

- [ ] **Step 2: Add tests for these registrations**

Append to `LdOpsTest.kt`:

```kotlin
@Test
fun `installInto registers LD r, n at 0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x3E`() {
    val d = Decoder()
    LdOps.installInto(d)
    assertThat((d.main[0x06] as LdRegImm).mnemonic { 0 }).isEqualTo("LD B, n")
    assertThat((d.main[0x0E] as LdRegImm).mnemonic { 0 }).isEqualTo("LD C, n")
    assertThat((d.main[0x16] as LdRegImm).mnemonic { 0 }).isEqualTo("LD D, n")
    assertThat((d.main[0x1E] as LdRegImm).mnemonic { 0 }).isEqualTo("LD E, n")
    assertThat((d.main[0x26] as LdRegImm).mnemonic { 0 }).isEqualTo("LD H, n")
    assertThat((d.main[0x2E] as LdRegImm).mnemonic { 0 }).isEqualTo("LD L, n")
    assertThat((d.main[0x3E] as LdRegImm).mnemonic { 0 }).isEqualTo("LD A, n")
}

@Test
fun `installInto registers LD (HL), n at 0x36`() {
    val d = Decoder()
    LdOps.installInto(d)
    assertThat(d.main[0x36]).isSameAs(LdHlMemImm)
}
```

- [ ] **Step 3: Run all LdOps tests**

Run: `./gradlew test --tests "Ld*Test"`
Expected: all pass.

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with 5 (LdRegImm) + 3 (LdHlMemImm) + 2 (LdOpsTest extensions) = 10 new tests.

- [ ] **Step 2: End-to-end smoke**

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count is now 74 + 8 = 82. Score climbs.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ld/ \
        src/test/kotlin/ru/alepar/zx80/op/ld/
git commit -m "feat(op/ld): LD r,n + LD (HL),n (8 opcodes, immediate-to-register)"
```

---

## WU 2.1b-4 — LD rr,nn family (16-bit immediate to register pair)

1 Op class: `LdPairImm(pair)`. 4 opcodes: 0x01 (BC), 0x11 (DE), 0x21 (HL), 0x31 (SP). 10 T-states each. operandLength=2.

This is also the natural place to introduce the address-fetch helper that the spec mentioned (Open Question 2 — yes, do it now since this is the second Op needing little-endian word reads from `mem[pc+1..pc+2]`).

### Task 1: Add a small helper for little-endian word read

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Memory.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/MemoryTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `MemoryTest.kt`:

```kotlin
@Test
fun `readWord reads little-endian 16-bit word at addr (low byte first)`() {
    val mem = Memory().apply {
        write(0x100, 0xCD)   // low
        write(0x101, 0xAB)   // high
    }
    assertThat(mem.readWord(0x100)).isEqualTo(0xABCD)
}

@Test
fun `readWord wraps mod 64K when addr+1 crosses 0xFFFF`() {
    val mem = Memory().apply {
        write(0xFFFF, 0xCD)
        write(0x0000, 0xAB)
    }
    assertThat(mem.readWord(0xFFFF)).isEqualTo(0xABCD)
}

@Test
fun `writeWord writes little-endian 16-bit word at addr (low byte first)`() {
    val mem = Memory()
    mem.writeWord(0x100, 0xABCD)
    assertThat(mem.read(0x100)).isEqualTo(0xCD)
    assertThat(mem.read(0x101)).isEqualTo(0xAB)
}

@Test
fun `writeWord masks value to 16 bits`() {
    val mem = Memory()
    mem.writeWord(0x100, 0x1ABCD)
    assertThat(mem.read(0x100)).isEqualTo(0xCD)
    assertThat(mem.read(0x101)).isEqualTo(0xAB)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests MemoryTest`
Expected: FAIL with `Unresolved reference: readWord`.

- [ ] **Step 3: Implement readWord and writeWord on Memory**

In `src/main/kotlin/ru/alepar/zx80/cpu/Memory.kt`, add to the Memory class (after the existing `loadAt` method):

```kotlin
/**
 * Read a 16-bit little-endian word at `addr`. Low byte at `addr`,
 * high byte at `addr + 1` (mod 64K). Returns Int in 0..0xFFFF.
 */
fun readWord(addr: Int): Int = read(addr) or (read(addr + 1) shl 8)

/**
 * Write a 16-bit value as little-endian. Low byte at `addr`, high
 * byte at `addr + 1` (mod 64K). Value masked to 16 bits.
 */
fun writeWord(addr: Int, value: Int) {
    write(addr, value and 0xFF)
    write(addr + 1, (value ushr 8) and 0xFF)
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests MemoryTest`
Expected: 4 new tests pass + all prior MemoryTest tests still pass.

### Task 2: LdPairImm

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdPairImm.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdPairImmTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdPairImmTest {
    @Test
    fun `LD BC, nn reads little-endian 16-bit immediate into BC`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            f = 0xAA
        }
        val mem = Memory().apply {
            write(0x100, 0x01)   // LD BC, nn opcode
            write(0x101, 0xCD)   // low byte of nn
            write(0x102, 0xAB)   // high byte of nn
        }
        LdPairImm(pair = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD SP, nn loads stack pointer`() {
        val cpu = Cpu().apply { pc = 0x200 }
        val mem = Memory().apply {
            write(0x200, 0x31)
            write(0x201, 0x00)
            write(0x202, 0x80)
        }
        LdPairImm(pair = RegPair.SP).execute(cpu, mem)
        assertThat(cpu.sp).isEqualTo(0x8000)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdPairImm(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD BC, nn")
        assertThat(LdPairImm(pair = RegPair.HL).mnemonic { 0 }).isEqualTo("LD HL, nn")
        assertThat(LdPairImm(pair = RegPair.SP).mnemonic { 0 }).isEqualTo("LD SP, nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10`() {
        val op = LdPairImm(pair = RegPair.BC)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdPairImmTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdPairImm**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD rr, nn` — loads a 16-bit immediate into a register pair.
 * 10 T-states. No flag changes. PC advances by 3.
 *
 * Opcodes: 0x01 (BC), 0x11 (DE), 0x21 (HL), 0x31 (SP).
 */
class LdPairImm(private val pair: RegPair) : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        pair.write(cpu, nn)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${pair.mnemonic}, nn"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdPairImmTest`
Expected: 4 tests pass.

### Task 3: Extend LdOps + LdOpsTest

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ld/LdOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ld/LdOpsTest.kt`

- [ ] **Step 1: Add LdPairImm registrations**

In `LdOps.kt`, add a new private method:

```kotlin
object LdOps {
    fun installInto(d: Decoder) {
        installRegToReg(d)
        installImmediate(d)
        installPairImmediate(d)
    }

    // ... existing private methods unchanged ...

    private fun installPairImmediate(d: Decoder) {
        // LD rr, nn — opcode pattern: 00 pp 0001 where pp is RegPair bits.
        // Opcodes: 0x01 (BC), 0x11 (DE), 0x21 (HL), 0x31 (SP).
        for (pairBits in 0..3) {
            val opcode = 0x01 or (pairBits shl 4)
            d.main[opcode] = LdPairImm(pair = RegPair.fromBits(pairBits))
        }
    }
}
```

Add the import: `import ru.alepar.zx80.cpu.RegPair`.

- [ ] **Step 2: Add tests for these registrations**

Append to `LdOpsTest.kt`:

```kotlin
@Test
fun `installInto registers LD rr, nn at 0x01, 0x11, 0x21, 0x31`() {
    val d = Decoder()
    LdOps.installInto(d)
    assertThat((d.main[0x01] as LdPairImm).mnemonic { 0 }).isEqualTo("LD BC, nn")
    assertThat((d.main[0x11] as LdPairImm).mnemonic { 0 }).isEqualTo("LD DE, nn")
    assertThat((d.main[0x21] as LdPairImm).mnemonic { 0 }).isEqualTo("LD HL, nn")
    assertThat((d.main[0x31] as LdPairImm).mnemonic { 0 }).isEqualTo("LD SP, nn")
}
```

- [ ] **Step 3: Run LdOps tests**

Run: `./gradlew test --tests "Ld*Test"`
Expected: all pass.

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL. New tests: 4 (Memory.readWord/writeWord) + 4 (LdPairImm) + 1 (LdOps ext) = 9.

- [ ] **Step 2: Smoke**

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected: opcodes 82 + 4 = 86.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Memory.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/MemoryTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/ld/LdPairImm.kt \
        src/test/kotlin/ru/alepar/zx80/op/ld/LdPairImmTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/ld/LdOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ld/LdOpsTest.kt
git commit -m "feat(op/ld): LD rr,nn (4 opcodes) + Memory.readWord/writeWord helpers

LD BC/DE/HL/SP, nn loads 16-bit immediate into a register pair.
Memory gains little-endian readWord/writeWord helpers used here and
by upcoming LD A,(nn) / LD HL,(nn) ops in the next WU."
```

---

## WU 2.1b-5 — LD with memory addresses

6 Op classes covering 8 opcodes: LD A,(BC), LD A,(DE), LD (BC),A, LD (DE),A, LD A,(nn), LD (nn),A, LD HL,(nn), LD (nn),HL.

The first 4 use `LdAFromBcDe(pair)` / `LdBcDeFromA(pair)` parameterized over `RegPair.BC` and `RegPair.DE` (matching the `Im(mode)` precedent). The last 4 are individual ops since they use a different addressing mode (16-bit absolute).

### Task 1: LdAFromBcDe (LD A,(BC) and LD A,(DE))

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdAFromBcDe.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdAFromBcDeTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdAFromBcDeTest {
    @Test
    fun `LD A, (BC) reads byte at BC into A`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            bc = 0x4000; a = 0; f = 0xAA
        }
        val mem = Memory().apply { write(0x4000, 0x42) }
        LdAFromBcDe(pair = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.bc).isEqualTo(0x4000)   // unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD A, (DE) reads byte at DE into A`() {
        val cpu = Cpu().apply { de = 0x100; a = 0 }
        val mem = Memory().apply { write(0x100, 0x99) }
        LdAFromBcDe(pair = RegPair.DE).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAFromBcDe(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD A, (BC)")
        assertThat(LdAFromBcDe(pair = RegPair.DE).mnemonic { 0 }).isEqualTo("LD A, (DE)")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdAFromBcDe(pair = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }

    @Test
    fun `LdAFromBcDe rejects HL or SP`() {
        assertThatThrownBy { LdAFromBcDe(pair = RegPair.HL) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LdAFromBcDe(pair = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests LdAFromBcDeTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD A, (BC)` and `LD A, (DE)` — read byte at memory[pair] into A.
 * 7 T-states. No flag changes. Only BC and DE are valid pairs for this op
 * (the (HL) and (SP) variants don't exist in the Z80 ISA at this opcode shape).
 */
class LdAFromBcDe(private val pair: RegPair) : Op {
    init {
        require(pair == RegPair.BC || pair == RegPair.DE) {
            "LdAFromBcDe accepts only BC or DE; got $pair"
        }
    }

    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.a = mem.read(pair.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD A, (${pair.mnemonic})"
}
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests LdAFromBcDeTest`
Expected: 5 tests pass.

### Task 2: LdBcDeFromA (LD (BC),A and LD (DE),A)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdBcDeFromA.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdBcDeFromATest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdBcDeFromATest {
    @Test
    fun `LD (BC), A writes A into memory at BC`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            bc = 0x4000; a = 0x42; f = 0xAA
        }
        val mem = Memory()
        LdBcDeFromA(pair = RegPair.BC).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.a).isEqualTo(0x42)        // unchanged
        assertThat(cpu.bc).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD (DE), A writes A into memory at DE`() {
        val cpu = Cpu().apply { de = 0x100; a = 0x99 }
        val mem = Memory()
        LdBcDeFromA(pair = RegPair.DE).execute(cpu, mem)
        assertThat(mem.read(0x100)).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdBcDeFromA(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD (BC), A")
        assertThat(LdBcDeFromA(pair = RegPair.DE).mnemonic { 0 }).isEqualTo("LD (DE), A")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdBcDeFromA(pair = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }

    @Test
    fun `LdBcDeFromA rejects HL or SP`() {
        assertThatThrownBy { LdBcDeFromA(pair = RegPair.HL) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LdBcDeFromA(pair = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (BC), A` and `LD (DE), A` — write A to memory[pair].
 * 7 T-states. No flag changes.
 */
class LdBcDeFromA(private val pair: RegPair) : Op {
    init {
        require(pair == RegPair.BC || pair == RegPair.DE) {
            "LdBcDeFromA accepts only BC or DE; got $pair"
        }
    }

    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        mem.write(pair.read(cpu), cpu.a)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (${pair.mnemonic}), A"
}
```

- [ ] **Step 4: Verify pass.** 5 tests.

### Task 3: LdAFromAddr (LD A,(nn))

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdAFromAddr.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdAFromAddrTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdAFromAddrTest {
    @Test
    fun `LD A, (nn) reads byte at little-endian address into A`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; a = 0; f = 0xAA }
        val mem = Memory().apply {
            write(0x100, 0x3A)   // opcode
            write(0x101, 0x00)   // low byte of nn
            write(0x102, 0x40)   // high byte of nn (so nn = 0x4000)
            write(0x4000, 0x42)
        }
        LdAFromAddr.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(13L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAFromAddr.mnemonic { 0 }).isEqualTo("LD A, (nn)")
    }

    @Test
    fun `operandLength=2, baseCycles=13`() {
        assertThat(LdAFromAddr.operandLength).isEqualTo(2)
        assertThat(LdAFromAddr.baseCycles).isEqualTo(13)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD A, (nn)` — load A from memory at the little-endian 16-bit
 * absolute address. 13 T-states. No flag changes. PC advances by 3.
 */
object LdAFromAddr : Op {
    override val operandLength = 2
    override val baseCycles = 13

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 1)
        cpu.a = mem.read(addr)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD A, (nn)"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 4: LdAddrFromA (LD (nn),A)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdAddrFromA.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdAddrFromATest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdAddrFromATest {
    @Test
    fun `LD (nn), A writes A to memory at little-endian address`() {
        val cpu = Cpu().apply { pc = 0x100; tStates = 0L; a = 0x42; f = 0xAA }
        val mem = Memory().apply {
            write(0x100, 0x32)
            write(0x101, 0x00)
            write(0x102, 0x40)
        }
        LdAddrFromA.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.a).isEqualTo(0x42)   // unchanged
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(13L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAddrFromA.mnemonic { 0 }).isEqualTo("LD (nn), A")
    }

    @Test
    fun `operandLength=2, baseCycles=13`() {
        assertThat(LdAddrFromA.operandLength).isEqualTo(2)
        assertThat(LdAddrFromA.baseCycles).isEqualTo(13)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (nn), A` — write A to memory at the little-endian 16-bit absolute
 * address. 13 T-states. No flag changes. PC advances by 3.
 */
object LdAddrFromA : Op {
    override val operandLength = 2
    override val baseCycles = 13

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 1)
        mem.write(addr, cpu.a)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (nn), A"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 5: LdHlFromAddr (LD HL,(nn))

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdHlFromAddr.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdHlFromAddrTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdHlFromAddrTest {
    @Test
    fun `LD HL, (nn) reads little-endian word at nn into HL`() {
        val cpu = Cpu().apply { pc = 0x100; tStates = 0L; hl = 0; f = 0xAA }
        val mem = Memory().apply {
            write(0x100, 0x2A)
            write(0x101, 0x00)   // low byte of nn
            write(0x102, 0x40)   // high byte of nn (nn = 0x4000)
            write(0x4000, 0xCD)  // becomes L
            write(0x4001, 0xAB)  // becomes H
        }
        LdHlFromAddr.execute(cpu, mem)
        assertThat(cpu.l).isEqualTo(0xCD)
        assertThat(cpu.h).isEqualTo(0xAB)
        assertThat(cpu.hl).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(16L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdHlFromAddr.mnemonic { 0 }).isEqualTo("LD HL, (nn)")
    }

    @Test
    fun `operandLength=2, baseCycles=16`() {
        assertThat(LdHlFromAddr.operandLength).isEqualTo(2)
        assertThat(LdHlFromAddr.baseCycles).isEqualTo(16)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD HL, (nn)` — read 16-bit word from memory at absolute address nn
 * into HL. Little-endian: low byte at nn → L, high byte at nn+1 → H.
 * 16 T-states. No flag changes. PC advances by 3.
 */
object LdHlFromAddr : Op {
    override val operandLength = 2
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 1)
        cpu.hl = mem.readWord(addr)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD HL, (nn)"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 6: LdAddrFromHl (LD (nn),HL)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ld/LdAddrFromHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ld/LdAddrFromHlTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdAddrFromHlTest {
    @Test
    fun `LD (nn), HL writes HL as little-endian word to memory at nn`() {
        val cpu = Cpu().apply { pc = 0x100; tStates = 0L; hl = 0xABCD; f = 0xAA }
        val mem = Memory().apply {
            write(0x100, 0x22)
            write(0x101, 0x00)
            write(0x102, 0x40)
        }
        LdAddrFromHl.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0xCD)   // low byte = L
        assertThat(mem.read(0x4001)).isEqualTo(0xAB)   // high byte = H
        assertThat(cpu.hl).isEqualTo(0xABCD)            // unchanged
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(16L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAddrFromHl.mnemonic { 0 }).isEqualTo("LD (nn), HL")
    }

    @Test
    fun `operandLength=2, baseCycles=16`() {
        assertThat(LdAddrFromHl.operandLength).isEqualTo(2)
        assertThat(LdAddrFromHl.baseCycles).isEqualTo(16)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (nn), HL` — write HL as a 16-bit little-endian word to memory at
 * absolute address nn. Low byte of HL (= L) at nn, high byte (= H) at
 * nn+1. 16 T-states. No flag changes. PC advances by 3.
 */
object LdAddrFromHl : Op {
    override val operandLength = 2
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 1)
        mem.writeWord(addr, cpu.hl)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (nn), HL"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 7: Extend LdOps + LdOpsTest

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ld/LdOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ld/LdOpsTest.kt`

- [ ] **Step 1: Add memory-address registrations**

In `LdOps.kt`, add a new private method:

```kotlin
object LdOps {
    fun installInto(d: Decoder) {
        installRegToReg(d)
        installImmediate(d)
        installPairImmediate(d)
        installMemoryAddress(d)
    }

    // ... existing private methods unchanged ...

    private fun installMemoryAddress(d: Decoder) {
        // LD A,(BC) / LD A,(DE) — opcodes 0x0A and 0x1A
        d.main[0x0A] = LdAFromBcDe(pair = RegPair.BC)
        d.main[0x1A] = LdAFromBcDe(pair = RegPair.DE)
        // LD (BC),A / LD (DE),A — opcodes 0x02 and 0x12
        d.main[0x02] = LdBcDeFromA(pair = RegPair.BC)
        d.main[0x12] = LdBcDeFromA(pair = RegPair.DE)
        // LD HL,(nn) / LD (nn),HL — opcodes 0x2A and 0x22
        d.main[0x2A] = LdHlFromAddr
        d.main[0x22] = LdAddrFromHl
        // LD A,(nn) / LD (nn),A — opcodes 0x3A and 0x32
        d.main[0x3A] = LdAFromAddr
        d.main[0x32] = LdAddrFromA
    }
}
```

- [ ] **Step 2: Add LdOps test**

Append to `LdOpsTest.kt`:

```kotlin
@Test
fun `installInto registers LD A,(BC) and LD A,(DE) at 0x0A and 0x1A`() {
    val d = Decoder()
    LdOps.installInto(d)
    assertThat((d.main[0x0A] as LdAFromBcDe).mnemonic { 0 }).isEqualTo("LD A, (BC)")
    assertThat((d.main[0x1A] as LdAFromBcDe).mnemonic { 0 }).isEqualTo("LD A, (DE)")
}

@Test
fun `installInto registers LD (BC),A and LD (DE),A at 0x02 and 0x12`() {
    val d = Decoder()
    LdOps.installInto(d)
    assertThat((d.main[0x02] as LdBcDeFromA).mnemonic { 0 }).isEqualTo("LD (BC), A")
    assertThat((d.main[0x12] as LdBcDeFromA).mnemonic { 0 }).isEqualTo("LD (DE), A")
}

@Test
fun `installInto registers LD HL,(nn) at 0x2A and LD (nn),HL at 0x22`() {
    val d = Decoder()
    LdOps.installInto(d)
    assertThat(d.main[0x2A]).isSameAs(LdHlFromAddr)
    assertThat(d.main[0x22]).isSameAs(LdAddrFromHl)
}

@Test
fun `installInto registers LD A,(nn) at 0x3A and LD (nn),A at 0x32`() {
    val d = Decoder()
    LdOps.installInto(d)
    assertThat(d.main[0x3A]).isSameAs(LdAFromAddr)
    assertThat(d.main[0x32]).isSameAs(LdAddrFromA)
}
```

- [ ] **Step 3: Run all LdOps tests**

Run: `./gradlew test --tests "Ld*Test"`
Expected: all pass.

### Task 8: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL. New tests this WU: ~22.

- [ ] **Step 2: Smoke**

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected: opcodes 86 + 8 = 94. Score climbs.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ld/ \
        src/test/kotlin/ru/alepar/zx80/op/ld/
git commit -m "feat(op/ld): LD with memory addresses (8 opcodes)

LD A,(BC), LD A,(DE), LD (BC),A, LD (DE),A — parameterized over
RegPair. LD A,(nn), LD (nn),A, LD HL,(nn), LD (nn),HL — singletons
using the new Memory.readWord/writeWord helpers."
```

---

## WU 2.1b-6 — Sweep + tag

Final verification + cleanup + tagging.

### Task 1: Full clean build + verification

- [ ] **Step 1: Clean build from scratch**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected headline (approximate; exact FUSE pass count may vary): `SCORE: 0.4xx  (opcodes 94/1792, fuse N/1356, programs 1/1)`.

Capture the exact string for the tag annotation.

- [ ] **Step 3: Inspect score.json**

```bash
head -25 build/score.json
```

Expected: `score > 0.3`, `suites.opcodes.passed = 94`, `suites.programs.passed = 1`, `suites.fuse.passed` significantly higher than 10.

- [ ] **Step 4: Verify all stub commands still work**

```bash
./build/install/zx80/bin/zx80 run     2>&1 | head -1
./build/install/zx80/bin/zx80 disasm  2>&1 | head -1
./build/install/zx80/bin/zx80 bench   2>&1 | head -1
./build/install/zx80/bin/zx80 zexdoc  2>&1 | head -1
```

Each prints "not yet implemented" and exits 0.

- [ ] **Step 5: Spotless final check**

Run: `./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Sanity-check FUSE 40 passes (LD B,B is the lowest LD opcode tested)**

```bash
python3 -c "import json; d = json.load(open('build/score.json')); failures = d['suites']['fuse']['details'].get('failures', []); has_40 = any(f.startswith('40:') for f in failures); print(f'failure for case 40 present: {has_40}')"
```

Expected: `failure for case 40 present: False` (LD B, B works correctly).

If `40` IS in the failures list, the dispatch path or `LdRegReg` has a bug. BLOCK and report.

### Task 2: Tag the milestone

- [ ] **Step 1: Verify clean tree**

```bash
git status
```
Expected: clean.

- [ ] **Step 2: Create the annotated tag**

```bash
git tag -a m1-phase02-1b -m "M1 Phase 2.1b complete: full LD instruction family

~80 new opcode positions across 12 Op classes covering LD r,r' (the
big 0x40-0x7F block), LD r,n, LD rr,nn, and LD with various memory
addressing modes. Foundation: Reg.fromBits, RegPair enum,
Memory.readWord/writeWord helpers. nop_loop still passes; composite
score climbed significantly from 0.106.

Plan 2.2 (arithmetic family) is the next batch."
```

- [ ] **Step 3: Verify the tag**

```bash
git tag --list 'm1-*'
git show m1-phase02-1b | head -30
git log --oneline opus-4.7 ^master | head -20
```

Expected:
- `m1-*` list now includes `m1-phase01-harness-baseline`, `m1-phase02-1a`, `m1-phase02-1b`.
- Recent commits include the WU 2.1b series.

This plan is complete.

---

## Self-Review

Performed inline. Notes:

1. **Spec coverage:** Every section of the Phase 2.1b spec maps to a task. Foundation (Reg.fromBits + RegPair) → WU 2.1b-1. LD r,r' family → WU 2.1b-2. LD r,n family → WU 2.1b-3. LD rr,nn family → WU 2.1b-4. LD with memory addresses → WU 2.1b-5. Sweep + tag → WU 2.1b-6. Memory.readWord/writeWord helpers were added in WU 2.1b-4 (the second user) per the spec's Open Question 2.

2. **Placeholder scan:** No "TBD" / "TODO" / "implement later" / "Similar to Task N" patterns. Every Op has its own concrete code shown verbatim. Pattern repetition is honest (the LD ops genuinely follow the same template).

3. **Type consistency:** `Reg.fromBits(bits)`, `RegPair.fromBits(bits)`, `Memory.readWord(addr)`, `Memory.writeWord(addr, value)`, `cpu.bumpR()` — all signatures match across uses. Mnemonic format `"LD <dst>, <src>"` consistent. Each Op's PC advance follows `(cpu.pc + 1 + operandLength) and 0xFFFF`.

4. **Skipped opcodes:** `LD r,r'` registration explicitly skips 0x76 (HALT slot) per the spec. `LD r,n` registration skips bits=6 (LD (HL),n is registered separately). Tests verify these skips.

5. **The LdAFromBcDe / LdBcDeFromA `require(pair == BC || pair == DE)` validation** matches the precedent set by `Im.init { require(mode in 0..2) }` — fail at construction, not at runtime.
