# ZX Spectrum Emulator — Phase 2.2 Implementation Plan (8-bit ALU + INC/DEC)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the entire 8-bit Z80 ALU family on A (ADD/ADC/SUB/SBC/AND/OR/XOR/CP across reg/(HL)/immediate sources) plus INC/DEC for 8-bit operands. ~88 new opcode positions across 7 Op classes. After this plan, opcodes count climbs by 88, FUSE pass-rate jumps significantly, composite score moves toward ~0.6.

**Architecture:** First batch with real flag computation. Centralized in `Flags` helpers (`afterAdd`/`afterSub`/`afterAnd`/`afterOr`/`afterXor`/`afterInc`/`afterDec`) returning a small `AluResult(value, newF)` data class. `AluOp` enum (ADD/ADC/SUB/SBC/AND/OR/XOR/CP) centralizes the per-operation dispatch + carry-bit-folding. Three thin Op classes (`AluAReg(op, src)`, `AluAFromHl(op)`, `AluAImm(op)`) cover all 72 ALU opcodes; four more (`IncReg(dst)`, `IncHlMem`, `DecReg(dst)`, `DecHlMem`) cover INC/DEC. All registered via `AluOps.installInto(decoder)` fragment.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-2-arithmetic-design.md`
**Branch:** `opus-4.7` (the de facto main; do NOT merge to `master`)
**Base commit:** `8196e25` (the spec commit). Phase 2.1b must complete first.

---

## Universal patterns (apply to every Op in this plan)

These conventions come from Phase 2.1a/2.1b and apply unchanged here, with one **new** addition: ALU and INC/DEC ops DO write `cpu.f`.

- Each Op is in `ru.alepar.zx80.op.alu.*`. Object singletons for stateless ops (no constructor params); classes for parameterized ops.
- `operandLength: Int` is set truthfully (0 or 1).
- `baseCycles: Int` per Z80 spec.
- `execute()` body convention:
  1. Read operand bytes from `mem.read(cpu.pc + 1)` if needed BEFORE advancing PC.
  2. Compute via `Flags.afterXxx(...)` or `AluOp.apply(...)`, getting back an `AluResult(value, newF)`.
  3. Write result to A or destination (some ops like CP don't update A).
  4. Write `cpu.f = result.newF`.
  5. Advance PC: `cpu.pc = (cpu.pc + 1 + operandLength) and 0xFFFF`.
  6. `cpu.bumpR()`.
  7. `cpu.tStates += baseCycles`.
- Mnemonic format: `"ADD A, B"`, `"ADC A, (HL)"`, `"AND A, n"`, `"INC B"`, `"INC (HL)"`, `"DEC L"`. Comma-then-space; uppercase mnemonic; matches LD style.
- Tests use AssertJ + JUnit 5 + backticked English test names.
- **New for this batch:** every Op test asserts that `cpu.f` is updated correctly. INC/DEC tests additionally assert that the C flag is preserved from oldF.

---

## File Structure

By the end of this plan the following files exist or have been modified.

**New files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/
  AluResult.kt                    # WU 2.2-1
src/main/kotlin/ru/alepar/zx80/op/alu/
  AluOp.kt                        # WU 2.2-1
  AluAReg.kt                      # WU 2.2-2
  AluAFromHl.kt                   # WU 2.2-3
  AluAImm.kt                      # WU 2.2-4
  IncReg.kt                       # WU 2.2-5
  IncHlMem.kt                     # WU 2.2-5
  DecReg.kt                       # WU 2.2-5
  DecHlMem.kt                     # WU 2.2-5
  AluOps.kt                       # WU 2.2-2 (created), extended in 3/4/5

src/test/kotlin/ru/alepar/zx80/cpu/
  AluResultTest.kt                # WU 2.2-1 (small data-class test)
src/test/kotlin/ru/alepar/zx80/op/alu/
  AluOpTest.kt                    # WU 2.2-1
  AluARegTest.kt                  # WU 2.2-2
  AluAFromHlTest.kt               # WU 2.2-3
  AluAImmTest.kt                  # WU 2.2-4
  IncRegTest.kt                   # WU 2.2-5
  IncHlMemTest.kt                 # WU 2.2-5
  DecRegTest.kt                   # WU 2.2-5
  DecHlMemTest.kt                 # WU 2.2-5
  AluOpsTest.kt                   # WU 2.2-2 (created), extended in 3/4/5
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt        # WU 2.2-1 (add helper functions)
src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt    # WU 2.2-1 (new, comprehensive helper tests)
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt # WU 2.2-2 (add AluOps.installInto)
```

---

## WU 2.2-1 — Foundation: Flags helpers + AluResult + AluOp enum

No Op classes; all the flag math + the AluOp enum that future WUs depend on.

### Task 1: AluResult data class

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cpu/AluResult.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/AluResultTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AluResultTest {
    @Test
    fun `AluResult holds value and newF`() {
        val r = AluResult(value = 0x42, newF = 0x80)
        assertThat(r.value).isEqualTo(0x42)
        assertThat(r.newF).isEqualTo(0x80)
    }

    @Test
    fun `AluResult equality`() {
        assertThat(AluResult(0x10, 0x20)).isEqualTo(AluResult(0x10, 0x20))
        assertThat(AluResult(0x10, 0x20)).isNotEqualTo(AluResult(0x10, 0x21))
    }
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests AluResultTest` — `Unresolved reference: AluResult`.

- [ ] **Step 3: Implement**

```kotlin
package ru.alepar.zx80.cpu

/**
 * The (result, new F register) pair returned by `Flags.afterXxx` helpers
 * and by `AluOp.apply`. `value` is masked to 8 bits; `newF` is the full
 * computed F-register byte.
 */
data class AluResult(val value: Int, val newF: Int)
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests AluResultTest` — 2 tests pass.

### Task 2: Flags helper — parity utility

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlagsTest {
    @Test
    fun `parity is true for even number of 1 bits`() {
        assertThat(Flags.parity(0x00)).isTrue   // 0 ones — even
        assertThat(Flags.parity(0x03)).isTrue   // 2 ones — even
        assertThat(Flags.parity(0xFF)).isTrue   // 8 ones — even
        assertThat(Flags.parity(0x55)).isTrue   // 4 ones — even
    }

    @Test
    fun `parity is false for odd number of 1 bits`() {
        assertThat(Flags.parity(0x01)).isFalse  // 1 one — odd
        assertThat(Flags.parity(0x07)).isFalse  // 3 ones — odd
        assertThat(Flags.parity(0x80)).isFalse  // 1 one — odd
        assertThat(Flags.parity(0x7F)).isFalse  // 7 ones — odd
    }

    @Test
    fun `parity ignores bits above bit 7`() {
        assertThat(Flags.parity(0xFF00)).isTrue   // top bits ignored; low 8 = 0x00 = even
        assertThat(Flags.parity(0xFF01)).isFalse  // low 8 = 0x01 = odd
    }
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests FlagsTest` — `Unresolved reference: parity`.

- [ ] **Step 3: Add `parity` to Flags object**

In `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`, add to the `object Flags` body (after the existing const vals):

```kotlin
/** True iff `value` (low 8 bits only) has even parity (even number of 1 bits). */
fun parity(value: Int): Boolean = Integer.bitCount(value and 0xFF) and 1 == 0
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests FlagsTest` — 3 tests pass.

### Task 3: Flags helper — afterAdd

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterAdd 0x01 + 0x02 with no carry`() {
    val r = Flags.afterAdd(0x01, 0x02, 0)
    assertThat(r.value).isEqualTo(0x03)
    assertThat(r.newF and Flags.S).isZero        // positive
    assertThat(r.newF and Flags.Z).isZero        // not zero
    assertThat(r.newF and Flags.H).isZero        // no half-carry
    assertThat(r.newF and Flags.PV).isZero       // no overflow
    assertThat(r.newF and Flags.N).isZero        // ADD clears N
    assertThat(r.newF and Flags.C).isZero        // no carry
}

@Test
fun `afterAdd half-carry boundary 0x0F + 0x01 sets H`() {
    val r = Flags.afterAdd(0x0F, 0x01, 0)
    assertThat(r.value).isEqualTo(0x10)
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterAdd carry-out 0xFF + 0x01 sets C and Z`() {
    val r = Flags.afterAdd(0xFF, 0x01, 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.H).isNotZero      // 0x0F+1 → 0x10 inside; bit 4 carry
}

@Test
fun `afterAdd overflow 0x7F + 0x01 sets V and S`() {
    val r = Flags.afterAdd(0x7F, 0x01, 0)
    assertThat(r.value).isEqualTo(0x80)
    assertThat(r.newF and Flags.PV).isNotZero    // V: positive + positive = negative
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterAdd carry-in folds in 0x01 + 0x01 + 1 = 0x03`() {
    val r = Flags.afterAdd(0x01, 0x01, 1)
    assertThat(r.value).isEqualTo(0x03)
}

@Test
fun `afterAdd zero result 0x00 + 0x00 sets Z`() {
    val r = Flags.afterAdd(0x00, 0x00, 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isZero
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests FlagsTest` — `Unresolved reference: afterAdd`.

- [ ] **Step 3: Implement afterAdd**

Add to `Flags.kt` object:

```kotlin
/**
 * ADD/ADC: a + b + carry. Both operands treated as unsigned 8-bit.
 * Carry param must be 0 or 1.
 *
 * Flag rules:
 * - S = bit 7 of result.
 * - Z = result == 0.
 * - H = carry from bit 3 to bit 4 (i.e. (a & 0x0F) + (b & 0x0F) + carry > 0x0F).
 * - P/V = signed overflow (both operands same sign, result different sign).
 * - N = 0 (ADD clears N).
 * - C = carry from bit 7 (sum > 0xFF).
 */
fun afterAdd(a: Int, b: Int, carry: Int): AluResult {
    val sum = a + b + carry
    val value = sum and 0xFF
    var f = 0
    if (value == 0) f = f or Z
    if (value and 0x80 != 0) f = f or S
    if ((a and 0x0F) + (b and 0x0F) + carry > 0x0F) f = f or H
    // Overflow: both operands have same sign bit, result has different sign bit
    if ((a xor b) and 0x80 == 0 && (a xor value) and 0x80 != 0) f = f or PV
    if (sum > 0xFF) f = f or C
    return AluResult(value, f)
}
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests FlagsTest` — 6 new afterAdd tests pass.

### Task 4: Flags helper — afterSub

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterSub 0x05 - 0x03 with no borrow`() {
    val r = Flags.afterSub(0x05, 0x03, 0)
    assertThat(r.value).isEqualTo(0x02)
    assertThat(r.newF and Flags.N).isNotZero     // SUB sets N
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.H).isZero
}

@Test
fun `afterSub equal operands sets Z, no borrow`() {
    val r = Flags.afterSub(0x42, 0x42, 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.N).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterSub borrow 0x00 - 0x01 sets C and S, result 0xFF`() {
    val r = Flags.afterSub(0x00, 0x01, 0)
    assertThat(r.value).isEqualTo(0xFF)
    assertThat(r.newF and Flags.C).isNotZero      // borrow from bit 8
    assertThat(r.newF and Flags.H).isNotZero      // borrow from bit 4
    assertThat(r.newF and Flags.S).isNotZero      // result negative
    assertThat(r.newF and Flags.N).isNotZero
}

@Test
fun `afterSub half-borrow 0x10 - 0x01 sets H`() {
    val r = Flags.afterSub(0x10, 0x01, 0)
    assertThat(r.value).isEqualTo(0x0F)
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterSub overflow 0x80 - 0x01 sets V`() {
    val r = Flags.afterSub(0x80, 0x01, 0)
    assertThat(r.value).isEqualTo(0x7F)
    assertThat(r.newF and Flags.PV).isNotZero    // V: negative - positive = positive (overflow)
    assertThat(r.newF and Flags.S).isZero
}

@Test
fun `afterSub borrow-in folds in 0x05 - 0x02 - 1 = 0x02`() {
    val r = Flags.afterSub(0x05, 0x02, 1)
    assertThat(r.value).isEqualTo(0x02)
}
```

- [ ] **Step 2: Verify fails.**

Run: `./gradlew test --tests FlagsTest` — `Unresolved reference: afterSub`.

- [ ] **Step 3: Implement afterSub**

Add to `Flags.kt`:

```kotlin
/**
 * SUB/SBC/CP: a - b - borrow. Both operands treated as unsigned 8-bit.
 * Borrow param must be 0 or 1.
 *
 * Flag rules:
 * - S = bit 7 of result.
 * - Z = result == 0.
 * - H = borrow from bit 4 (i.e. (a & 0x0F) - (b & 0x0F) - borrow < 0).
 * - P/V = signed overflow (operands different signs, result has same sign as b).
 * - N = 1 (SUB sets N).
 * - C = borrow from bit 8 (a - b - borrow < 0).
 */
fun afterSub(a: Int, b: Int, borrow: Int): AluResult {
    val diff = a - b - borrow
    val value = diff and 0xFF
    var f = N
    if (value == 0) f = f or Z
    if (value and 0x80 != 0) f = f or S
    if ((a and 0x0F) - (b and 0x0F) - borrow < 0) f = f or H
    // Overflow: operands have different sign bits, and result has different sign bit from a
    if ((a xor b) and 0x80 != 0 && (a xor value) and 0x80 != 0) f = f or PV
    if (diff < 0) f = f or C
    return AluResult(value, f)
}
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests FlagsTest` — 6 new afterSub tests pass.

### Task 5: Flags helper — afterAnd, afterOr, afterXor

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterAnd 0xFF AND 0x0F = 0x0F, H set, C cleared, P=parity`() {
    val r = Flags.afterAnd(0xFF, 0x0F)
    assertThat(r.value).isEqualTo(0x0F)
    assertThat(r.newF and Flags.H).isNotZero       // AND always sets H
    assertThat(r.newF and Flags.N).isZero
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.PV).isNotZero      // 0x0F has 4 ones — even — parity true
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.S).isZero
}

@Test
fun `afterAnd zero result sets Z`() {
    val r = Flags.afterAnd(0xF0, 0x0F)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.PV).isNotZero      // 0 ones — even
    assertThat(r.newF and Flags.H).isNotZero
}

@Test
fun `afterAnd negative result sets S`() {
    val r = Flags.afterAnd(0xFF, 0x80)
    assertThat(r.value).isEqualTo(0x80)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.PV).isZero          // 0x80 has 1 one — odd — parity false
}

@Test
fun `afterOr 0x0F OR 0xF0 = 0xFF, H cleared, C cleared`() {
    val r = Flags.afterOr(0x0F, 0xF0)
    assertThat(r.value).isEqualTo(0xFF)
    assertThat(r.newF and Flags.H).isZero          // OR clears H
    assertThat(r.newF and Flags.N).isZero
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.PV).isNotZero      // 8 ones — even
    assertThat(r.newF and Flags.S).isNotZero       // 0xFF has bit 7
}

@Test
fun `afterOr zero result`() {
    val r = Flags.afterOr(0x00, 0x00)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
}

@Test
fun `afterXor 0xFF XOR 0x0F = 0xF0`() {
    val r = Flags.afterXor(0xFF, 0x0F)
    assertThat(r.value).isEqualTo(0xF0)
    assertThat(r.newF and Flags.H).isZero
    assertThat(r.newF and Flags.N).isZero
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.PV).isNotZero      // 4 ones — even
    assertThat(r.newF and Flags.S).isNotZero
}

@Test
fun `afterXor self gives zero`() {
    val r = Flags.afterXor(0x42, 0x42)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.PV).isNotZero
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests FlagsTest`.

- [ ] **Step 3: Implement the three helpers**

Add to `Flags.kt`:

```kotlin
/**
 * AND a, b. Result is bitwise AND of the low 8 bits of each.
 * Flags: S = bit 7 of result, Z = result == 0, H = 1, P/V = parity,
 * N = 0, C = 0.
 */
fun afterAnd(a: Int, b: Int): AluResult {
    val value = (a and b) and 0xFF
    var f = H
    if (value == 0) f = f or Z
    if (value and 0x80 != 0) f = f or S
    if (parity(value)) f = f or PV
    return AluResult(value, f)
}

/**
 * OR a, b. Result is bitwise OR.
 * Flags: S = bit 7 of result, Z = result == 0, H = 0, P/V = parity, N = 0, C = 0.
 */
fun afterOr(a: Int, b: Int): AluResult {
    val value = (a or b) and 0xFF
    var f = 0
    if (value == 0) f = f or Z
    if (value and 0x80 != 0) f = f or S
    if (parity(value)) f = f or PV
    return AluResult(value, f)
}

/**
 * XOR a, b. Result is bitwise XOR.
 * Flags: S = bit 7 of result, Z = result == 0, H = 0, P/V = parity, N = 0, C = 0.
 */
fun afterXor(a: Int, b: Int): AluResult {
    val value = (a xor b) and 0xFF
    var f = 0
    if (value == 0) f = f or Z
    if (value and 0x80 != 0) f = f or S
    if (parity(value)) f = f or PV
    return AluResult(value, f)
}
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests FlagsTest` — 7 new tests pass.

### Task 6: Flags helper — afterInc, afterDec

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterInc 0x05 -> 0x06, C preserved from oldF`() {
    val r = Flags.afterInc(0x05, oldF = Flags.C)   // C is set in oldF
    assertThat(r.value).isEqualTo(0x06)
    assertThat(r.newF and Flags.C).isNotZero       // C preserved!
    assertThat(r.newF and Flags.N).isZero          // INC clears N
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.S).isZero
    assertThat(r.newF and Flags.H).isZero
    assertThat(r.newF and Flags.PV).isZero
}

@Test
fun `afterInc 0x0F -> 0x10 sets H`() {
    val r = Flags.afterInc(0x0F, oldF = 0)
    assertThat(r.value).isEqualTo(0x10)
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterInc 0xFF -> 0x00 sets Z and H, NOT C`() {
    val r = Flags.afterInc(0xFF, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.C).isZero            // INC does NOT set C
}

@Test
fun `afterInc 0x7F -> 0x80 sets V and S`() {
    val r = Flags.afterInc(0x7F, oldF = 0)
    assertThat(r.value).isEqualTo(0x80)
    assertThat(r.newF and Flags.PV).isNotZero       // overflow
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.H).isNotZero
}

@Test
fun `afterDec 0x05 -> 0x04, C preserved`() {
    val r = Flags.afterDec(0x05, oldF = Flags.C)
    assertThat(r.value).isEqualTo(0x04)
    assertThat(r.newF and Flags.C).isNotZero       // C preserved
    assertThat(r.newF and Flags.N).isNotZero       // DEC sets N
    assertThat(r.newF and Flags.H).isZero
}

@Test
fun `afterDec 0x10 -> 0x0F sets H (half-borrow)`() {
    val r = Flags.afterDec(0x10, oldF = 0)
    assertThat(r.value).isEqualTo(0x0F)
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.N).isNotZero
}

@Test
fun `afterDec 0x01 -> 0x00 sets Z`() {
    val r = Flags.afterDec(0x01, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.H).isZero          // 0x01 - 1: low nibble 1 - 1 = 0, no borrow
}

@Test
fun `afterDec 0x80 -> 0x7F sets V`() {
    val r = Flags.afterDec(0x80, oldF = 0)
    assertThat(r.value).isEqualTo(0x7F)
    assertThat(r.newF and Flags.PV).isNotZero       // overflow
    assertThat(r.newF and Flags.S).isZero
}

@Test
fun `afterDec 0x00 -> 0xFF sets H and S, NOT C`() {
    val r = Flags.afterDec(0x00, oldF = 0)
    assertThat(r.value).isEqualTo(0xFF)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.C).isZero            // DEC does NOT set C
}
```

- [ ] **Step 2: Verify fails.**

Run: `./gradlew test --tests FlagsTest`.

- [ ] **Step 3: Implement**

Add to `Flags.kt`:

```kotlin
/**
 * INC value (8-bit). Returns (value+1 & 0xFF, F).
 *
 * Flag rules:
 * - S = bit 7 of result.
 * - Z = result == 0.
 * - H = carry from bit 3 (i.e. (value & 0x0F) + 1 > 0x0F).
 * - P/V = overflow (value was 0x7F).
 * - N = 0.
 * - C = preserved from oldF.
 */
fun afterInc(value: Int, oldF: Int): AluResult {
    val v8 = value and 0xFF
    val result = (v8 + 1) and 0xFF
    var f = oldF and C   // preserve C only
    if (result == 0) f = f or Z
    if (result and 0x80 != 0) f = f or S
    if ((v8 and 0x0F) + 1 > 0x0F) f = f or H
    if (v8 == 0x7F) f = f or PV
    return AluResult(result, f)
}

/**
 * DEC value (8-bit). Returns (value-1 & 0xFF, F).
 *
 * Flag rules:
 * - S = bit 7 of result.
 * - Z = result == 0.
 * - H = borrow from bit 4 (i.e. (value & 0x0F) == 0).
 * - P/V = overflow (value was 0x80).
 * - N = 1.
 * - C = preserved from oldF.
 */
fun afterDec(value: Int, oldF: Int): AluResult {
    val v8 = value and 0xFF
    val result = (v8 - 1) and 0xFF
    var f = (oldF and C) or N   // preserve C, set N
    if (result == 0) f = f or Z
    if (result and 0x80 != 0) f = f or S
    if (v8 and 0x0F == 0) f = f or H        // borrow when low nibble was 0
    if (v8 == 0x80) f = f or PV
    return AluResult(result, f)
}
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests FlagsTest` — 9 new tests pass.

### Task 7: AluOp enum

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/AluOp.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/AluOpTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Flags

class AluOpTest {

    @Test
    fun `apply ADD computes sum`() {
        val r = AluOp.ADD.apply(a = 0x05, b = 0x03, oldF = 0)
        assertThat(r.value).isEqualTo(0x08)
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `apply ADC folds incoming C bit`() {
        val r = AluOp.ADC.apply(a = 0x05, b = 0x03, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x09)   // 5 + 3 + 1
    }

    @Test
    fun `apply SUB computes difference and sets N`() {
        val r = AluOp.SUB.apply(a = 0x05, b = 0x02, oldF = 0)
        assertThat(r.value).isEqualTo(0x03)
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `apply SBC folds incoming C bit as borrow`() {
        val r = AluOp.SBC.apply(a = 0x05, b = 0x02, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x02)    // 5 - 2 - 1
    }

    @Test
    fun `apply CP computes flags like SUB`() {
        val r = AluOp.CP.apply(a = 0x05, b = 0x05, oldF = 0)
        // Note: CP doesn't update A; that's handled by the caller checking updatesA.
        // The result.value here is the "would-be result" of the SUB.
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `apply AND computes AND`() {
        val r = AluOp.AND.apply(a = 0xFF, b = 0x0F, oldF = 0)
        assertThat(r.value).isEqualTo(0x0F)
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `apply OR computes OR`() {
        val r = AluOp.OR.apply(a = 0x0F, b = 0xF0, oldF = 0)
        assertThat(r.value).isEqualTo(0xFF)
    }

    @Test
    fun `apply XOR computes XOR`() {
        val r = AluOp.XOR.apply(a = 0xFF, b = 0x0F, oldF = 0)
        assertThat(r.value).isEqualTo(0xF0)
    }

    @Test
    fun `updatesA is true for all except CP`() {
        assertThat(AluOp.ADD.updatesA).isTrue
        assertThat(AluOp.ADC.updatesA).isTrue
        assertThat(AluOp.SUB.updatesA).isTrue
        assertThat(AluOp.SBC.updatesA).isTrue
        assertThat(AluOp.AND.updatesA).isTrue
        assertThat(AluOp.OR.updatesA).isTrue
        assertThat(AluOp.XOR.updatesA).isTrue
        assertThat(AluOp.CP.updatesA).isFalse
    }

    @Test
    fun `mnemonic matches enum name`() {
        assertThat(AluOp.ADD.mnemonic).isEqualTo("ADD")
        assertThat(AluOp.CP.mnemonic).isEqualTo("CP")
    }

    @Test
    fun `fromBits maps Z80 ALU encoding`() {
        // Z80 ooo encoding: 000=ADD 001=ADC 010=SUB 011=SBC 100=AND 101=XOR 110=OR 111=CP
        assertThat(AluOp.fromBits(0)).isEqualTo(AluOp.ADD)
        assertThat(AluOp.fromBits(1)).isEqualTo(AluOp.ADC)
        assertThat(AluOp.fromBits(2)).isEqualTo(AluOp.SUB)
        assertThat(AluOp.fromBits(3)).isEqualTo(AluOp.SBC)
        assertThat(AluOp.fromBits(4)).isEqualTo(AluOp.AND)
        assertThat(AluOp.fromBits(5)).isEqualTo(AluOp.XOR)   // note: XOR at 5
        assertThat(AluOp.fromBits(6)).isEqualTo(AluOp.OR)    // note: OR at 6
        assertThat(AluOp.fromBits(7)).isEqualTo(AluOp.CP)
    }
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests AluOpTest`.

- [ ] **Step 3: Implement AluOp**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.AluResult
import ru.alepar.zx80.cpu.Flags

/**
 * The 8 Z80 ALU operations on the accumulator. `apply(a, b, oldF)`
 * returns the result + new F register. `updatesA` is false only for CP
 * (which computes flags as if it were SUB but does not write A).
 */
enum class AluOp(val mnemonic: String, val updatesA: Boolean) {
    ADD("ADD", true),
    ADC("ADC", true),
    SUB("SUB", true),
    SBC("SBC", true),
    AND("AND", true),
    OR("OR", true),
    XOR("XOR", true),
    CP("CP", false);

    fun apply(a: Int, b: Int, oldF: Int): AluResult = when (this) {
        ADD -> Flags.afterAdd(a, b, 0)
        ADC -> Flags.afterAdd(a, b, if (oldF and Flags.C != 0) 1 else 0)
        SUB, CP -> Flags.afterSub(a, b, 0)
        SBC -> Flags.afterSub(a, b, if (oldF and Flags.C != 0) 1 else 0)
        AND -> Flags.afterAnd(a, b)
        OR -> Flags.afterOr(a, b)
        XOR -> Flags.afterXor(a, b)
    }

    companion object {
        /**
         * Map Z80 opcode bits 5-3 (the 'ooo' field of `10 ooo rrr` ALU
         * opcodes) to an AluOp.
         *
         * Encoding: 000=ADD 001=ADC 010=SUB 011=SBC 100=AND 101=XOR 110=OR 111=CP.
         */
        fun fromBits(bits: Int): AluOp = when (bits and 0x07) {
            0 -> ADD; 1 -> ADC; 2 -> SUB; 3 -> SBC
            4 -> AND; 5 -> XOR; 6 -> OR;  7 -> CP
            else -> error("unreachable")
        }
    }
}
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests AluOpTest` — 11 tests pass.

### Task 8: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with all prior tests + ~40 new (FlagsTest) + 11 (AluOpTest) + 2 (AluResultTest) = ~53 new tests.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/AluResult.kt \
        src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOp.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/AluResultTest.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpTest.kt
git commit -m "feat(cpu): Flags helpers + AluOp enum (Phase 2.2 foundation)

AluResult data class. Flags gains afterAdd/afterSub/afterAnd/afterOr/
afterXor/afterInc/afterDec helpers + parity utility. Each helper has
dense per-edge unit-test coverage (zero, sign, half-carry, overflow,
parity, INC/DEC C-preservation). AluOp enum centralizes ALU-op
dispatch + carry-bit folding. fromBits maps the Z80 ooo encoding
(XOR at 5, OR at 6) — easy to swap, hence the explicit test."
```

---

## WU 2.2-2 — AluAReg + AluOps fragment + wire into builder

The big WU: covers 56 opcode positions across the 0x80-0xBF block.

### Task 1: AluAReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/AluAReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/AluARegTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class AluARegTest {
    @Test
    fun `ADD A, B updates A and F, advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; a = 0x05; b = 0x03 }
        AluAReg(op = AluOp.ADD, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x08)
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.b).isEqualTo(0x03)   // src unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `ADC A, B folds in carry`() {
        val cpu = Cpu().apply { a = 0x05; b = 0x03; f = Flags.C }
        AluAReg(op = AluOp.ADC, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x09)
    }

    @Test
    fun `SUB A, B sets N`() {
        val cpu = Cpu().apply { a = 0x05; b = 0x03 }
        AluAReg(op = AluOp.SUB, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x02)
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `CP A, B does NOT update A but sets flags`() {
        val cpu = Cpu().apply { a = 0x05; b = 0x05 }
        AluAReg(op = AluOp.CP, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x05)             // A unchanged
        assertThat(cpu.f and Flags.Z).isNotZero       // but Z is set
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `AND A, B clears C, sets H`() {
        val cpu = Cpu().apply { a = 0xFF; b = 0x0F; f = Flags.C }
        AluAReg(op = AluOp.AND, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x0F)
        assertThat(cpu.f and Flags.C).isZero
        assertThat(cpu.f and Flags.H).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AluAReg(op = AluOp.ADD, src = Reg.B).mnemonic { 0 }).isEqualTo("ADD A, B")
        assertThat(AluAReg(op = AluOp.CP, src = Reg.A).mnemonic { 0 }).isEqualTo("CP A, A")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        val op = AluAReg(op = AluOp.ADD, src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests AluARegTest`.

- [ ] **Step 3: Implement AluAReg**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<ALU> A, r` — apply an 8-bit ALU operation between A and a register.
 * Covers the 56 opcodes in the 0x80-0xBF block where rrr (low 3 bits) != 110.
 * 4 T-states. `op.updatesA` controls whether A is written (false only for CP).
 */
class AluAReg(private val op: AluOp, private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(cpu.a, src.read(cpu), cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, ${src.mnemonic}"
}
```

- [ ] **Step 4: Verify pass**

Run: `./gradlew test --tests AluARegTest` — 7 tests pass.

### Task 2: AluOps fragment + wire into OpTableBuilder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class AluOpsTest {

    @Test
    fun `installInto registers ALU A,r at 0x80, 0x81, 0x87 (ADD A,B/A,C/A,A)`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x80] as AluAReg).mnemonic { 0 }).isEqualTo("ADD A, B")
        assertThat((d.main[0x81] as AluAReg).mnemonic { 0 }).isEqualTo("ADD A, C")
        assertThat((d.main[0x87] as AluAReg).mnemonic { 0 }).isEqualTo("ADD A, A")
    }

    @Test
    fun `installInto registers SUB A,r at 0x90 onwards`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x90] as AluAReg).mnemonic { 0 }).isEqualTo("SUB A, B")
        assertThat((d.main[0x97] as AluAReg).mnemonic { 0 }).isEqualTo("SUB A, A")
    }

    @Test
    fun `installInto registers XOR A,r at 0xA8 onwards (note XOR at ooo=5)`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0xA8] as AluAReg).mnemonic { 0 }).isEqualTo("XOR A, B")
        assertThat((d.main[0xAF] as AluAReg).mnemonic { 0 }).isEqualTo("XOR A, A")
    }

    @Test
    fun `installInto registers OR A,r at 0xB0 onwards (note OR at ooo=6)`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0xB0] as AluAReg).mnemonic { 0 }).isEqualTo("OR A, B")
        assertThat((d.main[0xB7] as AluAReg).mnemonic { 0 }).isEqualTo("OR A, A")
    }

    @Test
    fun `installInto registers CP A,r at 0xB8 onwards`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0xB8] as AluAReg).mnemonic { 0 }).isEqualTo("CP A, B")
        assertThat((d.main[0xBF] as AluAReg).mnemonic { 0 }).isEqualTo("CP A, A")
    }

    @Test
    fun `installInto registers exactly 56 reg-source ALU opcodes in 0x80-0xBF`() {
        val d = Decoder()
        AluOps.installInto(d)
        // 64 slots in 0x80-0xBF; minus 8 (rrr=110, the (HL) variants, registered later) = 56
        val count = (0x80..0xBF).count {
            d.main[it] is AluAReg
        }
        assertThat(count).isEqualTo(56)
    }
}
```

- [ ] **Step 2: Verify fails**

Run: `./gradlew test --tests AluOpsTest`.

- [ ] **Step 3: Implement AluOps**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg

/**
 * Registers the 8-bit ALU + INC/DEC Op family into the decoder.
 * Called by [ru.alepar.zx80.op.OpTableBuilder]. Subsequent WUs extend
 * this fragment with ALU A,(HL); ALU A,n; INC; DEC.
 *
 * The 0x80-0xBF block is the ALU A,r table. Bits 5-3 encode the ALU
 * op (ooo); bits 2-0 encode the source register (rrr). rrr=110 means
 * source is (HL) — handled by a separate Op class registered later.
 */
object AluOps {
    fun installInto(d: Decoder) {
        installAluAReg(d)
    }

    private fun installAluAReg(d: Decoder) {
        for (oooBits in 0..7) {
            val op = AluOp.fromBits(oooBits)
            for (rrrBits in 0..7) {
                if (rrrBits == 6) continue   // (HL) variant — separate Op class, registered later
                val opcode = 0x80 or (oooBits shl 3) or rrrBits
                d.main[opcode] = AluAReg(op = op, src = Reg.fromBits(rrrBits))
            }
        }
    }
}
```

- [ ] **Step 4: Verify AluOps tests**

Run: `./gradlew test --tests AluOpsTest` — 6 tests pass.

- [ ] **Step 5: Wire AluOps into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:

1. Add: `import ru.alepar.zx80.op.alu.AluOps`
2. In `build()`, add `AluOps.installInto(d)` (after `LdOps.installInto(d)`).

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: End-to-end smoke**

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count climbs by 56. Composite score climbs.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/alu/AluAReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluARegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(op/alu): AluAReg — 56 ALU A,r opcodes in 0x80-0xBF block

Parameterized over (op, src). CP correctly leaves A unchanged. (HL)
variants handled by AluAFromHl in the next WU."
```

---

## WU 2.2-3 — AluAFromHl (8 opcodes for ALU A,(HL))

### Task 1: AluAFromHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/AluAFromHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/AluAFromHlTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class AluAFromHlTest {
    @Test
    fun `ADD A, (HL) reads byte at HL, adds to A, 7 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000; a = 0x05
        }
        val mem = Memory().apply { write(0x4000, 0x03) }
        AluAFromHl(op = AluOp.ADD).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x08)
        assertThat(cpu.hl).isEqualTo(0x4000)   // hl unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
    }

    @Test
    fun `CP A, (HL) does not update A`() {
        val cpu = Cpu().apply { hl = 0x100; a = 0x42 }
        val mem = Memory().apply { write(0x100, 0x42) }
        AluAFromHl(op = AluOp.CP).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AluAFromHl(op = AluOp.ADD).mnemonic { 0 }).isEqualTo("ADD A, (HL)")
        assertThat(AluAFromHl(op = AluOp.XOR).mnemonic { 0 }).isEqualTo("XOR A, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = AluAFromHl(op = AluOp.ADD)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<ALU> A, (HL)` — apply an 8-bit ALU operation between A and the byte
 * at memory[HL]. 8 opcodes (one per AluOp), pattern `10 ooo 110`.
 * 7 T-states.
 */
class AluAFromHl(private val op: AluOp) : Op {
    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(cpu.a, mem.read(cpu.hl), cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, (HL)"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: Extend AluOps + AluOpsTest

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt`

- [ ] **Step 1: Add installAluAFromHl**

```kotlin
object AluOps {
    fun installInto(d: Decoder) {
        installAluAReg(d)
        installAluAFromHl(d)
    }

    // ... existing installAluAReg unchanged ...

    private fun installAluAFromHl(d: Decoder) {
        // ALU A,(HL) — opcode pattern 10 ooo 110 → 0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE
        for (oooBits in 0..7) {
            val opcode = 0x80 or (oooBits shl 3) or 0x06
            d.main[opcode] = AluAFromHl(op = AluOp.fromBits(oooBits))
        }
    }
}
```

- [ ] **Step 2: Add AluOpsTest assertions**

Append to `AluOpsTest.kt`:

```kotlin
@Test
fun `installInto registers ALU A,(HL) at 0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.main[0x86] as AluAFromHl).mnemonic { 0 }).isEqualTo("ADD A, (HL)")
    assertThat((d.main[0x8E] as AluAFromHl).mnemonic { 0 }).isEqualTo("ADC A, (HL)")
    assertThat((d.main[0x96] as AluAFromHl).mnemonic { 0 }).isEqualTo("SUB A, (HL)")
    assertThat((d.main[0x9E] as AluAFromHl).mnemonic { 0 }).isEqualTo("SBC A, (HL)")
    assertThat((d.main[0xA6] as AluAFromHl).mnemonic { 0 }).isEqualTo("AND A, (HL)")
    assertThat((d.main[0xAE] as AluAFromHl).mnemonic { 0 }).isEqualTo("XOR A, (HL)")
    assertThat((d.main[0xB6] as AluAFromHl).mnemonic { 0 }).isEqualTo("OR A, (HL)")
    assertThat((d.main[0xBE] as AluAFromHl).mnemonic { 0 }).isEqualTo("CP A, (HL)")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/alu/AluAFromHl.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluAFromHlTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt
git commit -m "feat(op/alu): AluAFromHl — 8 ALU A,(HL) opcodes"
```

---

## WU 2.2-4 — AluAImm (8 opcodes for ALU A,n)

### Task 1: AluAImm

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/AluAImm.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/AluAImmTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class AluAImmTest {
    @Test
    fun `ADD A, n reads immediate, adds to A, advances pc by 2, 7 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            a = 0x05
        }
        val mem = Memory().apply {
            write(0x100, 0xC6)   // ADD A, n
            write(0x101, 0x03)
        }
        AluAImm(op = AluOp.ADD).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x08)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
    }

    @Test
    fun `CP A, n does not update A`() {
        val cpu = Cpu().apply { pc = 0x100; a = 0x42 }
        val mem = Memory().apply { write(0x101, 0x42) }
        AluAImm(op = AluOp.CP).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AluAImm(op = AluOp.ADD).mnemonic { 0 }).isEqualTo("ADD A, n")
        assertThat(AluAImm(op = AluOp.OR).mnemonic { 0 }).isEqualTo("OR A, n")
    }

    @Test
    fun `operandLength=1, baseCycles=7`() {
        val op = AluAImm(op = AluOp.ADD)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<ALU> A, n` — apply an 8-bit ALU operation between A and an immediate
 * byte. 8 opcodes (one per AluOp), pattern `11 ooo 110`. 7 T-states.
 * PC advances by 2 (opcode + immediate).
 */
class AluAImm(private val op: AluOp) : Op {
    override val operandLength = 1
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        val r = op.apply(cpu.a, n, cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, n"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: Extend AluOps + AluOpsTest

- [ ] **Step 1: Add installAluAImm**

```kotlin
object AluOps {
    fun installInto(d: Decoder) {
        installAluAReg(d)
        installAluAFromHl(d)
        installAluAImm(d)
    }

    // ... existing methods unchanged ...

    private fun installAluAImm(d: Decoder) {
        // ALU A,n — opcode pattern 11 ooo 110 → 0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE
        for (oooBits in 0..7) {
            val opcode = 0xC0 or (oooBits shl 3) or 0x06
            d.main[opcode] = AluAImm(op = AluOp.fromBits(oooBits))
        }
    }
}
```

- [ ] **Step 2: Add AluOpsTest assertion**

```kotlin
@Test
fun `installInto registers ALU A,n at 0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.main[0xC6] as AluAImm).mnemonic { 0 }).isEqualTo("ADD A, n")
    assertThat((d.main[0xCE] as AluAImm).mnemonic { 0 }).isEqualTo("ADC A, n")
    assertThat((d.main[0xD6] as AluAImm).mnemonic { 0 }).isEqualTo("SUB A, n")
    assertThat((d.main[0xDE] as AluAImm).mnemonic { 0 }).isEqualTo("SBC A, n")
    assertThat((d.main[0xE6] as AluAImm).mnemonic { 0 }).isEqualTo("AND A, n")
    assertThat((d.main[0xEE] as AluAImm).mnemonic { 0 }).isEqualTo("XOR A, n")
    assertThat((d.main[0xF6] as AluAImm).mnemonic { 0 }).isEqualTo("OR A, n")
    assertThat((d.main[0xFE] as AluAImm).mnemonic { 0 }).isEqualTo("CP A, n")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/alu/AluAImm.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluAImmTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt
git commit -m "feat(op/alu): AluAImm — 8 ALU A,n immediate opcodes"
```

---

## WU 2.2-5 — INC and DEC (16 opcodes)

4 Op classes: `IncReg(dst)`, `IncHlMem`, `DecReg(dst)`, `DecHlMem`.

### Task 1: IncReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/IncReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/IncRegTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class IncRegTest {
    @Test
    fun `INC B updates B and F, advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            b = 0x05
        }
        IncReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x06)
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `INC preserves C flag`() {
        val cpu = Cpu().apply { b = 0x05; f = Flags.C }
        IncReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `INC 0xFF wraps to 0x00, sets Z and H, NOT C`() {
        val cpu = Cpu().apply { b = 0xFF }
        IncReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncReg(dst = Reg.B).mnemonic { 0 }).isEqualTo("INC B")
        assertThat(IncReg(dst = Reg.A).mnemonic { 0 }).isEqualTo("INC A")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        val op = IncReg(dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INC r` — increment an 8-bit register, updating flags. 4 T-states.
 * 7 opcodes (one per Reg in B/C/D/E/H/L/A): pattern `00 rrr 100` minus rrr=110.
 *
 * The C flag is preserved (unique to INC/DEC).
 */
class IncReg(private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterInc(dst.read(cpu), cpu.f)
        dst.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INC ${dst.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 2: IncHlMem

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/IncHlMem.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/IncHlMemTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class IncHlMemTest {
    @Test
    fun `INC (HL) increments byte at HL, 11 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000
        }
        val mem = Memory().apply { write(0x4000, 0x05) }
        IncHlMem.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x06)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `INC (HL) preserves C flag`() {
        val cpu = Cpu().apply { hl = 0x100; f = Flags.C }
        val mem = Memory().apply { write(0x100, 0x05) }
        IncHlMem.execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncHlMem.mnemonic { 0 }).isEqualTo("INC (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        assertThat(IncHlMem.operandLength).isZero
        assertThat(IncHlMem.baseCycles).isEqualTo(11)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INC (HL)` — increment the byte at memory[HL], updating flags.
 * 11 T-states. Single opcode 0x34. C flag preserved.
 */
object IncHlMem : Op {
    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterInc(mem.read(cpu.hl), cpu.f)
        mem.write(cpu.hl, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INC (HL)"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 3: DecReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/DecReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/DecRegTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class DecRegTest {
    @Test
    fun `DEC B updates B and F, sets N, advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            b = 0x05
        }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x04)
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `DEC preserves C flag`() {
        val cpu = Cpu().apply { b = 0x05; f = Flags.C }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `DEC 0x01 sets Z`() {
        val cpu = Cpu().apply { b = 0x01 }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `DEC 0x00 wraps to 0xFF, sets H and S, NOT C`() {
        val cpu = Cpu().apply { b = 0x00 }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0xFF)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(DecReg(dst = Reg.A).mnemonic { 0 }).isEqualTo("DEC A")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        val op = DecReg(dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC r` — decrement an 8-bit register, updating flags. 4 T-states.
 * 7 opcodes (one per Reg in B/C/D/E/H/L/A): pattern `00 rrr 101` minus rrr=110.
 *
 * The N flag is set; C flag is preserved (unique to INC/DEC).
 */
class DecReg(private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDec(dst.read(cpu), cpu.f)
        dst.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC ${dst.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 4: DecHlMem

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/alu/DecHlMem.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/alu/DecHlMemTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class DecHlMemTest {
    @Test
    fun `DEC (HL) decrements byte at HL, sets N, 11 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; hl = 0x4000 }
        val mem = Memory().apply { write(0x4000, 0x05) }
        DecHlMem.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x04)
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `DEC (HL) preserves C flag`() {
        val cpu = Cpu().apply { hl = 0x100; f = Flags.C }
        val mem = Memory().apply { write(0x100, 0x05) }
        DecHlMem.execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(DecHlMem.mnemonic { 0 }).isEqualTo("DEC (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        assertThat(DecHlMem.operandLength).isZero
        assertThat(DecHlMem.baseCycles).isEqualTo(11)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC (HL)` — decrement the byte at memory[HL], updating flags.
 * 11 T-states. Single opcode 0x35. N set, C preserved.
 */
object DecHlMem : Op {
    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDec(mem.read(cpu.hl), cpu.f)
        mem.write(cpu.hl, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC (HL)"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 5: Extend AluOps + AluOpsTest with INC/DEC

- [ ] **Step 1: Add installInc and installDec to AluOps**

```kotlin
object AluOps {
    fun installInto(d: Decoder) {
        installAluAReg(d)
        installAluAFromHl(d)
        installAluAImm(d)
        installInc(d)
        installDec(d)
    }

    // ... existing methods unchanged ...

    private fun installInc(d: Decoder) {
        // INC r — pattern 00 rrr 100 → 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C
        for (rrrBits in 0..7) {
            val opcode = 0x04 or (rrrBits shl 3)
            d.main[opcode] = if (rrrBits == 6) IncHlMem else IncReg(dst = Reg.fromBits(rrrBits))
        }
    }

    private fun installDec(d: Decoder) {
        // DEC r — pattern 00 rrr 101 → 0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D
        for (rrrBits in 0..7) {
            val opcode = 0x05 or (rrrBits shl 3)
            d.main[opcode] = if (rrrBits == 6) DecHlMem else DecReg(dst = Reg.fromBits(rrrBits))
        }
    }
}
```

- [ ] **Step 2: Add AluOpsTest assertions**

Append to `AluOpsTest.kt`:

```kotlin
@Test
fun `installInto registers INC r at 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x3C`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.main[0x04] as IncReg).mnemonic { 0 }).isEqualTo("INC B")
    assertThat((d.main[0x0C] as IncReg).mnemonic { 0 }).isEqualTo("INC C")
    assertThat((d.main[0x14] as IncReg).mnemonic { 0 }).isEqualTo("INC D")
    assertThat((d.main[0x1C] as IncReg).mnemonic { 0 }).isEqualTo("INC E")
    assertThat((d.main[0x24] as IncReg).mnemonic { 0 }).isEqualTo("INC H")
    assertThat((d.main[0x2C] as IncReg).mnemonic { 0 }).isEqualTo("INC L")
    assertThat((d.main[0x3C] as IncReg).mnemonic { 0 }).isEqualTo("INC A")
}

@Test
fun `installInto registers INC (HL) at 0x34`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat(d.main[0x34]).isSameAs(IncHlMem)
}

@Test
fun `installInto registers DEC r at 0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x3D`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat((d.main[0x05] as DecReg).mnemonic { 0 }).isEqualTo("DEC B")
    assertThat((d.main[0x0D] as DecReg).mnemonic { 0 }).isEqualTo("DEC C")
    assertThat((d.main[0x15] as DecReg).mnemonic { 0 }).isEqualTo("DEC D")
    assertThat((d.main[0x1D] as DecReg).mnemonic { 0 }).isEqualTo("DEC E")
    assertThat((d.main[0x25] as DecReg).mnemonic { 0 }).isEqualTo("DEC H")
    assertThat((d.main[0x2D] as DecReg).mnemonic { 0 }).isEqualTo("DEC L")
    assertThat((d.main[0x3D] as DecReg).mnemonic { 0 }).isEqualTo("DEC A")
}

@Test
fun `installInto registers DEC (HL) at 0x35`() {
    val d = Decoder()
    AluOps.installInto(d)
    assertThat(d.main[0x35]).isSameAs(DecHlMem)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/alu/IncReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/IncHlMem.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/DecReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/DecHlMem.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/IncRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/IncHlMemTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/DecRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/DecHlMemTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/alu/AluOpsTest.kt
git commit -m "feat(op/alu): INC and DEC for register and (HL) — 16 opcodes

C flag preserved (unique to INC/DEC). N flag cleared by INC, set by DEC.
4 T-states for register variants, 11 for (HL)."
```

---

## WU 2.2-6 — Sweep + tag

Final verification + tagging.

### Task 1: Full clean build + verification

- [ ] **Step 1: Clean build**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: composite score significantly above prior baseline. Opcode count climbs by 88 (56 AluAReg + 8 AluAFromHl + 8 AluAImm + 7 IncReg + 1 IncHlMem + 7 DecReg + 1 DecHlMem).

Capture exact headline.

- [ ] **Step 3: Inspect score.json**

```bash
head -25 build/score.json
```

Expected: `score` higher than prior baseline; `suites.opcodes.passed` higher by 88; `suites.fuse.passed` significantly higher.

- [ ] **Step 4: Spotless final check**

Run: `./gradlew spotlessCheck`
Expected: green.

- [ ] **Step 5: Sanity-check FUSE 80 case (ADD A,B) passes**

```bash
python3 -c "import json; d = json.load(open('build/score.json')); failures = d['suites']['fuse']['details'].get('failures', []); has_80 = any(f.startswith('80:') for f in failures); print(f'failure for case 80 present: {has_80}')"
```

Expected: `False` (ADD A, B works).

If `80` is in failures: BLOCK and report — flag computation in afterAdd is wrong somewhere.

### Task 2: Tag

- [ ] **Step 1: Verify clean tree** (`git status`).

- [ ] **Step 2: Create the annotated tag**

```bash
git tag -a m1-phase02-2 -m "M1 Phase 2.2 complete: 8-bit ALU + INC/DEC

88 new opcode positions across 7 Op classes. ADD/ADC/SUB/SBC/AND/OR/XOR/CP
on A from register, (HL), and immediate sources, plus INC and DEC for
register and (HL). First batch with real flag computation. Centralized
in Flags helpers + AluOp enum.

Plan 2.3 (16-bit arithmetic) is the next batch."
```

- [ ] **Step 3: Verify the tag**

```bash
git tag --list 'm1-*'
git show m1-phase02-2 | head -20
```

This plan is complete.

---

## Self-Review

Performed inline. Notes:

1. **Spec coverage:** Every section of the Phase 2.2 spec maps to a task. Foundation (Flags + AluResult + AluOp) → WU 2.2-1. AluAReg → WU 2.2-2. AluAFromHl → WU 2.2-3. AluAImm → WU 2.2-4. INC/DEC → WU 2.2-5. Sweep + tag → WU 2.2-6.

2. **Placeholder scan:** No "TBD" / "TODO" / "implement later". Every Op has its own concrete code shown. Per-Flags-helper test code is verbatim and dense.

3. **Type consistency:** `Flags.afterXxx(...)`, `AluOp.apply(a, b, oldF)`, `AluResult(value, newF)`, `cpu.bumpR()`, `Reg.fromBits(bits)` — all signatures consistent. Mnemonic format `"<MNEMONIC> A, <src>"` for ALU, `"INC <reg>"` / `"DEC <reg>"` / `"INC (HL)"` / `"DEC (HL)"` for INC/DEC.

4. **Skipped opcodes:** `installAluAReg` skips rrr=110 (handled by AluAFromHl). `installInc`/`installDec` install IncHlMem / DecHlMem at the rrr=110 slots.

5. **Tests for the 'special' cases**: CP not updating A, INC/DEC preserving C flag, AND setting H, ADC/SBC folding in carry — all explicitly tested per Op class.
