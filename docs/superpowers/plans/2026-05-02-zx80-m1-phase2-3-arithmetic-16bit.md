# ZX Spectrum Emulator — Phase 2.3 Implementation Plan (16-bit arithmetic)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Z80 16-bit arithmetic on register pairs — `ADD HL,rr`, `INC rr`, `DEC rr`, `ADC HL,rr`, `SBC HL,rr`. 20 new opcode positions across 5 Op classes.

**Architecture:** Three new `Flags` helpers (`afterAddWord`, `afterAdcWord`, `afterSbcWord`) handle the 16-bit flag-bit math (half-carry from bit 11, overflow from bit 15). Five Op classes (`AddHlPair(src)`, `IncPair(dst)`, `DecPair(dst)`, `AdcHlPair(src)`, `SbcHlPair(src)`) — each parameterized over `RegPair`. The two ED-prefixed ones increment R by 2 and advance PC by 2.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-3-arithmetic-16bit-design.md`
**Branch:** `opus-4.7`
**Base commit:** `16998f1` (the spec commit). Phase 2.2 must complete first.

---

## Universal patterns

Same as Phase 2.1a/2.1b/2.2 plus three notes specific to this batch:

- **`INC rr` / `DEC rr` do NOT touch flags.** Tests must set `cpu.f = 0xFF` (or some specific pattern) before, and assert the post-condition is identical.
- **`ADD HL,rr` only computes H, N, C.** S, Z, P/V are preserved from `oldF`. Tests verify by setting `oldF = Flags.S or Flags.Z or Flags.PV` and asserting those bits remain set after.
- **ED-prefixed ops** (`ADC HL,rr`, `SBC HL,rr`): PC += 2; `cpu.bumpR(2)`; T-states 15.
- 16-bit arithmetic: result masked to `0xFFFF`; H from bit 11 carry; PV from bit 15 signed overflow; C from `> 0xFFFF` (or `< 0`).

---

## File Structure

**New files:**
```
src/main/kotlin/ru/alepar/zx80/op/alu/
  AddHlPair.kt                # WU 2.3-2
  IncPair.kt                  # WU 2.3-3
  DecPair.kt                  # WU 2.3-3
  AdcHlPair.kt                # WU 2.3-4
  SbcHlPair.kt                # WU 2.3-4

src/test/kotlin/ru/alepar/zx80/op/alu/
  AddHlPairTest.kt            # WU 2.3-2
  IncPairTest.kt              # WU 2.3-3
  DecPairTest.kt              # WU 2.3-3
  AdcHlPairTest.kt            # WU 2.3-4
  SbcHlPairTest.kt            # WU 2.3-4
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt          # WU 2.3-1 (add 3 word helpers)
src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt      # WU 2.3-1 (add ~15 word tests)
src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt      # WUs 2.3-2/3/4 (extend installInto)
src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt  # WUs 2.3-2/3/4 (extend assertions)
```

---

## WU 2.3-1 — Flags 16-bit helpers

### Task 1: afterAddWord

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Failing test**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterAddWord 0x1234 + 0x5678 with no carry`() {
    val r = Flags.afterAddWord(0x1234, 0x5678, oldF = 0)
    assertThat(r.value).isEqualTo(0x68AC)
    assertThat(r.newF and Flags.N).isZero
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.H).isZero
}

@Test
fun `afterAddWord half-carry boundary 0x0FFF + 0x0001 sets H`() {
    val r = Flags.afterAddWord(0x0FFF, 0x0001, oldF = 0)
    assertThat(r.value).isEqualTo(0x1000)
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterAddWord carry boundary 0xFFFF + 0x0001 wraps and sets C`() {
    val r = Flags.afterAddWord(0xFFFF, 0x0001, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.H).isNotZero       // 0x0FFF+1 → 0x1000 inside, bit-11 carry
}

@Test
fun `afterAddWord preserves S, Z, PV from oldF`() {
    val oldF = Flags.S or Flags.Z or Flags.PV or Flags.C   // C will be overwritten
    val r = Flags.afterAddWord(0x1234, 0x0001, oldF)
    assertThat(r.newF and Flags.S).isNotZero       // preserved
    assertThat(r.newF and Flags.Z).isNotZero       // preserved
    assertThat(r.newF and Flags.PV).isNotZero      // preserved
    assertThat(r.newF and Flags.C).isZero          // overwritten (no carry)
    assertThat(r.newF and Flags.N).isZero          // ADD clears N
}
```

- [ ] **Step 2: Verify fails.** Run: `./gradlew test --tests FlagsTest` → `Unresolved reference: afterAddWord`.

- [ ] **Step 3: Implement afterAddWord**

In `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`, add to the object body:

```kotlin
/**
 * 16-bit ADD: a + b. Only H, N, C are computed; S, Z, P/V are
 * PRESERVED from oldF. (Unique to ADD HL,rr; ADC/SBC HL,rr compute
 * all flags.)
 *
 * - H = carry from bit 11 (i.e. (a & 0x0FFF) + (b & 0x0FFF) > 0x0FFF).
 * - N = 0.
 * - C = sum > 0xFFFF.
 */
fun afterAddWord(a: Int, b: Int, oldF: Int): AluResult {
    val sum = a + b
    val value = sum and 0xFFFF
    var f = oldF and (S or Z or PV)   // preserve these
    if ((a and 0x0FFF) + (b and 0x0FFF) > 0x0FFF) f = f or H
    if (sum > 0xFFFF) f = f or C
    return AluResult(value, f)
}
```

- [ ] **Step 4: Verify pass.** 4 tests pass.

### Task 2: afterAdcWord

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Failing test**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterAdcWord 0x0001 + 0x0001 + carry=1 = 0x0003, all flag categories computed`() {
    val r = Flags.afterAdcWord(0x0001, 0x0001, oldF = Flags.C)
    assertThat(r.value).isEqualTo(0x0003)
    assertThat(r.newF and Flags.S).isZero
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.N).isZero      // ADC clears N
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterAdcWord half-carry from bit 11`() {
    val r = Flags.afterAdcWord(0x0FFE, 0x0001, oldF = Flags.C)   // 0x0FFE + 1 + 1 = 0x1000
    assertThat(r.value).isEqualTo(0x1000)
    assertThat(r.newF and Flags.H).isNotZero
}

@Test
fun `afterAdcWord overflow at 0x7FFF + 0x0001 sets V and S`() {
    val r = Flags.afterAdcWord(0x7FFF, 0x0001, oldF = 0)
    assertThat(r.value).isEqualTo(0x8000)
    assertThat(r.newF and Flags.PV).isNotZero
    assertThat(r.newF and Flags.S).isNotZero
}

@Test
fun `afterAdcWord zero result with carry-out`() {
    val r = Flags.afterAdcWord(0xFFFF, 0x0001, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}
```

- [ ] **Step 2: Verify fails.** 

- [ ] **Step 3: Implement afterAdcWord**

```kotlin
/**
 * 16-bit ADC: a + b + carry. ALL flags computed.
 *
 * - S = bit 15 of result.
 * - Z = result == 0.
 * - H = carry from bit 11 (with carry-in folded).
 * - P/V = signed overflow at bit 15.
 * - N = 0.
 * - C = sum > 0xFFFF.
 */
fun afterAdcWord(a: Int, b: Int, oldF: Int): AluResult {
    val carry = if (oldF and C != 0) 1 else 0
    val sum = a + b + carry
    val value = sum and 0xFFFF
    var f = 0
    if (value == 0) f = f or Z
    if (value and 0x8000 != 0) f = f or S
    if ((a and 0x0FFF) + (b and 0x0FFF) + carry > 0x0FFF) f = f or H
    if ((a xor b) and 0x8000 == 0 && (a xor value) and 0x8000 != 0) f = f or PV
    if (sum > 0xFFFF) f = f or C
    return AluResult(value, f)
}
```

- [ ] **Step 4: Verify pass.** 4 tests.

### Task 3: afterSbcWord

**Files:** same as Task 2.

- [ ] **Step 1: Failing test**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterSbcWord 0x0005 - 0x0002 - borrow=0`() {
    val r = Flags.afterSbcWord(0x0005, 0x0002, oldF = 0)
    assertThat(r.value).isEqualTo(0x0003)
    assertThat(r.newF and Flags.N).isNotZero      // SBC sets N
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterSbcWord folds in borrow-in`() {
    val r = Flags.afterSbcWord(0x0005, 0x0002, oldF = Flags.C)
    assertThat(r.value).isEqualTo(0x0002)         // 5 - 2 - 1
}

@Test
fun `afterSbcWord borrow at 0x0000 - 0x0001 wraps to 0xFFFF, sets C, S, H`() {
    val r = Flags.afterSbcWord(0x0000, 0x0001, oldF = 0)
    assertThat(r.value).isEqualTo(0xFFFF)
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.H).isNotZero
}

@Test
fun `afterSbcWord overflow 0x8000 - 0x0001 sets V`() {
    val r = Flags.afterSbcWord(0x8000, 0x0001, oldF = 0)
    assertThat(r.value).isEqualTo(0x7FFF)
    assertThat(r.newF and Flags.PV).isNotZero
    assertThat(r.newF and Flags.S).isZero
}

@Test
fun `afterSbcWord equal operands sets Z`() {
    val r = Flags.afterSbcWord(0x1234, 0x1234, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.N).isNotZero
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement afterSbcWord**

```kotlin
/**
 * 16-bit SBC: a - b - borrow. ALL flags computed.
 *
 * - S = bit 15 of result.
 * - Z = result == 0.
 * - H = borrow from bit 12 (with borrow-in folded).
 * - P/V = signed overflow.
 * - N = 1.
 * - C = a - b - borrow < 0.
 */
fun afterSbcWord(a: Int, b: Int, oldF: Int): AluResult {
    val borrow = if (oldF and C != 0) 1 else 0
    val diff = a - b - borrow
    val value = diff and 0xFFFF
    var f = N
    if (value == 0) f = f or Z
    if (value and 0x8000 != 0) f = f or S
    if ((a and 0x0FFF) - (b and 0x0FFF) - borrow < 0) f = f or H
    if ((a xor b) and 0x8000 != 0 && (a xor value) and 0x8000 != 0) f = f or PV
    if (diff < 0) f = f or C
    return AluResult(value, f)
}
```

- [ ] **Step 4: Verify pass.** 5 tests.

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless.** `./gradlew test spotlessApply`. Expected: ~13 new FlagsTest cases pass.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt
git commit -m "feat(cpu): Flags 16-bit helpers (Phase 2.3 foundation)

afterAddWord (preserves S/Z/P/V from oldF; computes H/N/C only),
afterAdcWord and afterSbcWord (full flag updates). Half-carry from
bit 11; overflow from bit 15. Dense per-edge tests."
```

---

## WU 2.3-2 — AddHlPair (4 opcodes)

### Task 1: AddHlPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/AddHlPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/AddHlPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class AddHlPairTest {
    @Test
    fun `ADD HL, BC adds BC to HL, advances pc, increments r, adds 11 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x1234; bc = 0x5678
        }
        AddHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x68AC)
        assertThat(cpu.bc).isEqualTo(0x5678)   // src unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `ADD HL preserves S, Z, PV from oldF`() {
        val cpu = Cpu().apply { hl = 0x0001; bc = 0x0001; f = Flags.S or Flags.Z or Flags.PV }
        AddHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `ADD HL, HL doubles HL`() {
        val cpu = Cpu().apply { hl = 0x4000 }
        AddHlPair(src = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x8000)
    }

    @Test
    fun `ADD HL, SP works`() {
        val cpu = Cpu().apply { hl = 0x1000; sp = 0x2000 }
        AddHlPair(src = RegPair.SP).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3000)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AddHlPair(src = RegPair.BC).mnemonic { 0 }).isEqualTo("ADD HL, BC")
        assertThat(AddHlPair(src = RegPair.SP).mnemonic { 0 }).isEqualTo("ADD HL, SP")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        val op = AddHlPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
```

- [ ] **Step 2: Verify fails.** 

- [ ] **Step 3: Implement AddHlPair**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `ADD HL, rr` — 16-bit add with HL as destination. 11 T-states.
 * Only H, N, C are computed; S, Z, P/V preserved from oldF.
 *
 * Opcodes: 0x09 (BC), 0x19 (DE), 0x29 (HL), 0x39 (SP).
 */
class AddHlPair(private val src: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterAddWord(cpu.hl, src.read(cpu), cpu.f)
        cpu.hl = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "ADD HL, ${src.mnemonic}"
}
```

- [ ] **Step 4: Verify pass.** 6 tests.

### Task 2: Extend AluOps + AluOpsTest

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt`

- [ ] **Step 1: Add installAddHl**

Add to `AluOps.kt`:
- Import: `import ru.alepar.zx80.cpu.RegPair`
- Inside `object AluOps`, add to `installInto(d)`: `installAddHl(d)`
- New private method:

```kotlin
private fun installAddHl(d: Decoder) {
    // ADD HL,rr — 00 ss 1001 → 0x09, 0x19, 0x29, 0x39
    for (ssBits in 0..3) {
        val opcode = 0x09 or (ssBits shl 4)
        d.main[opcode] = AddHlPair(src = RegPair.fromBits(ssBits))
    }
}
```

- [ ] **Step 2: Add AluOpsTest assertions**

Append to `AluOpsTest.kt`:

```kotlin
@Test
fun `installInto registers ADD HL,rr at 0x09, 0x19, 0x29, 0x39`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.main[0x09] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, BC")
    assertThat((d.main[0x19] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, DE")
    assertThat((d.main[0x29] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, HL")
    assertThat((d.main[0x39] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, SP")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/alu/AddHlPair.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AddHlPairTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt
git commit -m "feat(op/alu): ADD HL,rr — 4 opcodes for 16-bit add into HL"
```

---

## WU 2.3-3 — IncPair + DecPair (8 opcodes, NO flag changes)

### Task 1: IncPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/IncPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/IncPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class IncPairTest {
    @Test
    fun `INC BC increments BC by 1, advances pc, increments r, adds 6 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            bc = 0x1234
        }
        IncPair(dst = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.bc).isEqualTo(0x1235)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(6L)
    }

    @Test
    fun `INC SP wraps from 0xFFFF to 0x0000`() {
        val cpu = Cpu().apply { sp = 0xFFFF }
        IncPair(dst = RegPair.SP).execute(cpu, Memory())
        assertThat(cpu.sp).isZero
    }

    @Test
    fun `INC rr does NOT affect any flag`() {
        val cpu = Cpu().apply { hl = 0x0000; f = 0xFF }
        IncPair(dst = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)        // every flag bit preserved
    }

    @Test
    fun `mnemonic format`() {
        assertThat(IncPair(dst = RegPair.BC).mnemonic { 0 }).isEqualTo("INC BC")
        assertThat(IncPair(dst = RegPair.SP).mnemonic { 0 }).isEqualTo("INC SP")
    }

    @Test
    fun `operandLength=0, baseCycles=6`() {
        val op = IncPair(dst = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(6)
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement IncPair**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INC rr` — 16-bit increment of a register pair. 6 T-states.
 * **Does NOT affect flags** (unlike 8-bit INC r).
 *
 * Opcodes: 0x03 (BC), 0x13 (DE), 0x23 (HL), 0x33 (SP).
 */
class IncPair(private val dst: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 6

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, dst.read(cpu) + 1)   // RegPair.write masks to 16 bits
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INC ${dst.mnemonic}"
}
```

- [ ] **Step 4: Verify pass.** 5 tests.

### Task 2: DecPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/DecPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/DecPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class DecPairTest {
    @Test
    fun `DEC BC decrements BC by 1, advances pc, increments r, adds 6 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            bc = 0x1234
        }
        DecPair(dst = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.bc).isEqualTo(0x1233)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(6L)
    }

    @Test
    fun `DEC HL wraps from 0x0000 to 0xFFFF`() {
        val cpu = Cpu().apply { hl = 0x0000 }
        DecPair(dst = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0xFFFF)
    }

    @Test
    fun `DEC rr does NOT affect any flag`() {
        val cpu = Cpu().apply { de = 0x1000; f = 0xFF }
        DecPair(dst = RegPair.DE).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(DecPair(dst = RegPair.HL).mnemonic { 0 }).isEqualTo("DEC HL")
    }

    @Test
    fun `operandLength=0, baseCycles=6`() {
        val op = DecPair(dst = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(6)
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement DecPair**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC rr` — 16-bit decrement of a register pair. 6 T-states.
 * **Does NOT affect flags** (unlike 8-bit DEC r).
 *
 * Opcodes: 0x0B (BC), 0x1B (DE), 0x2B (HL), 0x3B (SP).
 */
class DecPair(private val dst: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 6

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, dst.read(cpu) - 1)   // RegPair.write masks to 16 bits
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC ${dst.mnemonic}"
}
```

- [ ] **Step 4: Verify pass.** 5 tests.

### Task 3: Extend AluOps + AluOpsTest

- [ ] **Step 1: Add installIncDecPair**

In `AluOps.kt`:
- Inside `installInto(d)`, add `installIncDecPair(d)`.
- New private method:

```kotlin
private fun installIncDecPair(d: Decoder) {
    // INC rr — 00 dd 0011 → 0x03, 0x13, 0x23, 0x33
    // DEC rr — 00 dd 1011 → 0x0B, 0x1B, 0x2B, 0x3B
    for (ddBits in 0..3) {
        d.main[0x03 or (ddBits shl 4)] = IncPair(dst = RegPair.fromBits(ddBits))
        d.main[0x0B or (ddBits shl 4)] = DecPair(dst = RegPair.fromBits(ddBits))
    }
}
```

- [ ] **Step 2: Add AluOpsTest assertions**

```kotlin
@Test
fun `installInto registers INC rr at 0x03, 0x13, 0x23, 0x33`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.main[0x03] as IncPair).mnemonic { 0 }).isEqualTo("INC BC")
    assertThat((d.main[0x13] as IncPair).mnemonic { 0 }).isEqualTo("INC DE")
    assertThat((d.main[0x23] as IncPair).mnemonic { 0 }).isEqualTo("INC HL")
    assertThat((d.main[0x33] as IncPair).mnemonic { 0 }).isEqualTo("INC SP")
}

@Test
fun `installInto registers DEC rr at 0x0B, 0x1B, 0x2B, 0x3B`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.main[0x0B] as DecPair).mnemonic { 0 }).isEqualTo("DEC BC")
    assertThat((d.main[0x1B] as DecPair).mnemonic { 0 }).isEqualTo("DEC DE")
    assertThat((d.main[0x2B] as DecPair).mnemonic { 0 }).isEqualTo("DEC HL")
    assertThat((d.main[0x3B] as DecPair).mnemonic { 0 }).isEqualTo("DEC SP")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/alu/IncPair.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/DecPair.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/IncPairTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/DecPairTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt
git commit -m "feat(op/alu): INC rr and DEC rr — 8 opcodes, NO flag changes

Unlike their 8-bit counterparts, the 16-bit pair INC/DEC do not affect
any flags. Tests verify by setting f=0xFF before and asserting it's
still 0xFF after."
```

---

## WU 2.3-4 — AdcHlPair + SbcHlPair (8 ED-prefixed opcodes)

### Task 1: AdcHlPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/AdcHlPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/AdcHlPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class AdcHlPairTest {
    @Test
    fun `ADC HL, BC adds BC + carry, ED-prefixed advances pc by 2, r by 2, 15 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x1234; bc = 0x1000; f = Flags.C
        }
        AdcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x2235)            // 0x1234 + 0x1000 + 1
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `ADC HL, HL doubles HL plus carry`() {
        val cpu = Cpu().apply { hl = 0x4000; f = Flags.C }
        AdcHlPair(src = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x8001)            // 0x4000 + 0x4000 + 1
    }

    @Test
    fun `ADC HL sets PV on overflow`() {
        val cpu = Cpu().apply { hl = 0x7FFF; bc = 0x0001; f = 0 }
        AdcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x8000)
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.S).isNotZero
    }

    @Test
    fun `ADC HL, SP works`() {
        val cpu = Cpu().apply { hl = 0x1000; sp = 0x2000; f = 0 }
        AdcHlPair(src = RegPair.SP).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3000)
        assertThat(cpu.f and Flags.N).isZero            // ADC clears N
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AdcHlPair(src = RegPair.BC).mnemonic { 0 }).isEqualTo("ADC HL, BC")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = AdcHlPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement AdcHlPair**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `ADC HL, rr` — 16-bit ADC into HL. ED-prefixed → PC+=2, R+=2.
 * 15 T-states. ALL flags computed.
 *
 * Opcodes: ED 0x4A (BC), ED 0x5A (DE), ED 0x6A (HL), ED 0x7A (SP).
 */
class AdcHlPair(private val src: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterAdcWord(cpu.hl, src.read(cpu), cpu.f)
        cpu.hl = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "ADC HL, ${src.mnemonic}"
}
```

- [ ] **Step 4: Verify pass.** 6 tests.

### Task 2: SbcHlPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/SbcHlPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/SbcHlPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class SbcHlPairTest {
    @Test
    fun `SBC HL, BC subtracts BC + borrow, sets N, ED-prefixed`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x5000; bc = 0x1000; f = Flags.C
        }
        SbcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3FFF)            // 0x5000 - 0x1000 - 1
        assertThat(cpu.f and Flags.N).isNotZero         // SBC sets N
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `SBC HL, HL with no carry gives 0 and sets Z`() {
        val cpu = Cpu().apply { hl = 0x1234; f = 0 }
        SbcHlPair(src = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `SBC HL borrow at 0x0000 - 0x0001 wraps and sets C`() {
        val cpu = Cpu().apply { hl = 0x0000; bc = 0x0001; f = 0 }
        SbcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0xFFFF)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.S).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SbcHlPair(src = RegPair.SP).mnemonic { 0 }).isEqualTo("SBC HL, SP")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = SbcHlPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement SbcHlPair**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SBC HL, rr` — 16-bit SBC from HL. ED-prefixed → PC+=2, R+=2.
 * 15 T-states. ALL flags computed; N set.
 *
 * Opcodes: ED 0x42 (BC), ED 0x52 (DE), ED 0x62 (HL), ED 0x72 (SP).
 */
class SbcHlPair(private val src: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterSbcWord(cpu.hl, src.read(cpu), cpu.f)
        cpu.hl = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SBC HL, ${src.mnemonic}"
}
```

- [ ] **Step 4: Verify pass.** 5 tests.

### Task 3: Extend AluOps + AluOpsTest

- [ ] **Step 1: Add installAdcSbcHl**

In `AluOps.kt`:
- Inside `installInto(d)`, add `installAdcSbcHl(d)`.
- New private method:

```kotlin
private fun installAdcSbcHl(d: Decoder) {
    // ADC HL,rr — ED 01 ss 1010 → ED 0x4A, ED 0x5A, ED 0x6A, ED 0x7A
    // SBC HL,rr — ED 01 ss 0010 → ED 0x42, ED 0x52, ED 0x62, ED 0x72
    for (ssBits in 0..3) {
        d.ed[0x4A or (ssBits shl 4)] = AdcHlPair(src = RegPair.fromBits(ssBits))
        d.ed[0x42 or (ssBits shl 4)] = SbcHlPair(src = RegPair.fromBits(ssBits))
    }
}
```

- [ ] **Step 2: Add AluOpsTest assertions**

```kotlin
@Test
fun `installInto registers ADC HL,rr in ed table`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.ed[0x4A] as AdcHlPair).mnemonic { 0 }).isEqualTo("ADC HL, BC")
    assertThat((d.ed[0x5A] as AdcHlPair).mnemonic { 0 }).isEqualTo("ADC HL, DE")
    assertThat((d.ed[0x6A] as AdcHlPair).mnemonic { 0 }).isEqualTo("ADC HL, HL")
    assertThat((d.ed[0x7A] as AdcHlPair).mnemonic { 0 }).isEqualTo("ADC HL, SP")
}

@Test
fun `installInto registers SBC HL,rr in ed table`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.ed[0x42] as SbcHlPair).mnemonic { 0 }).isEqualTo("SBC HL, BC")
    assertThat((d.ed[0x52] as SbcHlPair).mnemonic { 0 }).isEqualTo("SBC HL, DE")
    assertThat((d.ed[0x62] as SbcHlPair).mnemonic { 0 }).isEqualTo("SBC HL, HL")
    assertThat((d.ed[0x72] as SbcHlPair).mnemonic { 0 }).isEqualTo("SBC HL, SP")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/alu/AdcHlPair.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/SbcHlPair.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AdcHlPairTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/SbcHlPairTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt
git commit -m "feat(op/alu): ADC HL,rr and SBC HL,rr — 8 ED-prefixed opcodes

PC+=2, R+=2, 15 T-states. All flags computed. Half-carry from bit 11;
overflow from bit 15."
```

---

## WU 2.3-5 — Sweep + tag

### Task 1: Verification + tag

- [ ] **Step 1: Clean build**

`./gradlew clean check installDist` — BUILD SUCCESSFUL.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count climbs by 20 vs prior baseline. Composite score ticks up modestly (16-bit ops are a smaller chunk of FUSE than 8-bit ALU).

- [ ] **Step 3: Spotless check**

`./gradlew spotlessCheck` — green.

- [ ] **Step 4: Sanity-check FUSE 09 case (ADD HL,BC) passes**

```bash
python3 -c "import json; d = json.load(open('build/score.json')); failures = d['suites']['fuse']['details'].get('failures', []); has_09 = any(f.startswith('09:') for f in failures); print(f'failure for case 09 present: {has_09}')"
```

Expected: `False`.

- [ ] **Step 5: Tag**

```bash
git tag -a m1-phase02-3 -m "M1 Phase 2.3 complete: 16-bit arithmetic

20 new opcode positions across 5 Op classes: ADD HL,rr (4 main, 11T,
partial flag update), INC rr / DEC rr (8 main, 6T, no flag changes),
ADC HL,rr / SBC HL,rr (8 ED-prefixed, 15T, full flag update).
Foundation: Flags.afterAddWord/afterAdcWord/afterSbcWord with 16-bit
half-carry (bit 11) and overflow (bit 15) boundaries.

Plan 2.4 (jumps and calls) is the next batch."
```

This plan is complete.

---

## Self-Review

1. **Spec coverage:** Every section of the Phase 2.3 spec maps to a task. Foundation (3 Flags helpers) → WU 2.3-1. AddHlPair → 2.3-2. IncPair + DecPair → 2.3-3. AdcHlPair + SbcHlPair → 2.3-4. Sweep + tag → 2.3-5.

2. **Placeholder scan:** No "TBD" / "TODO". Every Op has its concrete code.

3. **Type consistency:** `Flags.afterAddWord/afterAdcWord/afterSbcWord` all return `AluResult`. `cpu.bumpR()` for unprefixed; `cpu.bumpR(2)` for ED-prefixed. PC advance: `+1` for unprefixed; `+2` for ED-prefixed.

4. **Critical assertions verified:**
   - `AddHlPair`: explicit S/Z/PV preservation test.
   - `IncPair`/`DecPair`: explicit "f preserved" tests (set f=0xFF, assert f=0xFF after).
   - `AdcHlPair`/`SbcHlPair`: explicit PC+=2 and R+=2 tests; 15 T-states.
   - 16-bit boundaries: half-carry at 0x0FFF, overflow at 0x7FFF, carry at 0xFFFF.
