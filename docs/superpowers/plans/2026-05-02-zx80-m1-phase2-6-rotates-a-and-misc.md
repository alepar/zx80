# ZX Spectrum Emulator — Phase 2.6 Implementation Plan (rotates on A + DAA/CPL/SCF/CCF)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 8 main-table single-byte opcodes operating on A or flags: RLCA, RRCA, RLA, RRA, DAA, CPL, SCF, CCF.

**Architecture:** Five new `Flags` helpers (`afterRotateA`, `afterDaa`, `afterCpl`, `afterScf`, `afterCcf`) handle the per-op flag math. Eight singleton Op classes — 4 in new `op/rot/` (rotate-A variants, registered via new `RotOps` fragment) and 4 in existing `op/misc/` (DAA/CPL/SCF/CCF, registered via extended `MiscOps`). DAA uses the standard table-based BCD-adjust algorithm with dense per-boundary testing.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-6-rotates-a-and-misc-design.md`
**Branch:** `opus-4.7`
**Base commit:** spec commit. Phase 2.5 must complete first.

---

## Universal patterns

Same as prior phases. New notes:

- **Rotate-A flag rules differ from CB-prefixed rotates** (Phase 2.7). The A-variants preserve S/Z/PV; the CB-variants compute them from result. Tests for rotate-A explicitly set oldF=`Flags.S or Flags.Z or Flags.PV` and assert preservation.
- **DAA preserves N** while computing all other flags from result.
- **CCF sets H to oldC** (counterintuitive; test it).

---

## File Structure

**New files:**
```
src/main/kotlin/ru/alepar/zx80/op/rot/
  Rlca.kt                       # WU 2.6-2
  Rrca.kt                       # WU 2.6-2
  Rla.kt                        # WU 2.6-2
  Rra.kt                        # WU 2.6-2
  RotOps.kt                     # WU 2.6-2
src/main/kotlin/ru/alepar/zx80/op/misc/
  Daa.kt                        # WU 2.6-3
  Cpl.kt                        # WU 2.6-3
  Scf.kt                        # WU 2.6-3
  Ccf.kt                        # WU 2.6-3

src/test/kotlin/ru/alepar/zx80/op/rot/
  RlcaTest.kt                   # WU 2.6-2
  RrcaTest.kt                   # WU 2.6-2
  RlaTest.kt                    # WU 2.6-2
  RraTest.kt                    # WU 2.6-2
  RotOpsTest.kt                 # WU 2.6-2
src/test/kotlin/ru/alepar/zx80/op/misc/
  DaaTest.kt                    # WU 2.6-3
  CplTest.kt                    # WU 2.6-3
  ScfTest.kt                    # WU 2.6-3
  CcfTest.kt                    # WU 2.6-3
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt          # WU 2.6-1 (add 5 helpers)
src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt      # WU 2.6-1 (add ~30 tests)
src/main/kotlin/ru/alepar/zx80/op/misc/MiscOps.kt    # WU 2.6-3 (extend with 4 new registrations)
src/test/kotlin/ru/alepar/zx80/op/misc/MiscOpsTest.kt # WU 2.6-3 (assert new registrations)
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt  # WU 2.6-2 (add RotOps.installInto)
```

---

## WU 2.6-1 — Flags helpers (5 new)

### Task 1: afterRotateA

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Failing test**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterRotateA sets C from newC, clears H and N, preserves S Z PV`() {
    val oldF = Flags.S or Flags.Z or Flags.PV or Flags.N or Flags.H or Flags.C
    val r = Flags.afterRotateA(rotated = 0x42, newC = false, oldF = oldF)
    assertThat(r.value).isEqualTo(0x42)
    assertThat(r.newF and Flags.S).isNotZero        // preserved
    assertThat(r.newF and Flags.Z).isNotZero        // preserved
    assertThat(r.newF and Flags.PV).isNotZero       // preserved
    assertThat(r.newF and Flags.N).isZero            // cleared
    assertThat(r.newF and Flags.H).isZero            // cleared
    assertThat(r.newF and Flags.C).isZero            // newC=false
}

@Test
fun `afterRotateA sets C when newC is true`() {
    val r = Flags.afterRotateA(rotated = 0x80, newC = true, oldF = 0)
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterRotateA masks rotated to 8 bits`() {
    val r = Flags.afterRotateA(rotated = 0x1FF, newC = false, oldF = 0)
    assertThat(r.value).isEqualTo(0xFF)
}
```

- [ ] **Step 2: Verify fails.** `./gradlew test --tests FlagsTest`.

- [ ] **Step 3: Implement afterRotateA**

Add to `Flags.kt`:

```kotlin
/**
 * Common flag rules for the 4 rotate-A opcodes (RLCA, RRCA, RLA, RRA):
 * C = newC (the bit shifted out); H = 0; N = 0; S/Z/PV preserved from oldF.
 *
 * Caller computes the rotated value and newC; this helper packages the
 * result + new F.
 */
fun afterRotateA(rotated: Int, newC: Boolean, oldF: Int): AluResult {
    var f = oldF and (S or Z or PV)
    if (newC) f = f or C
    return AluResult(rotated and 0xFF, f)
}
```

- [ ] **Step 4: Verify pass.** 3 tests.

### Task 2: afterCpl

**Files:** same.

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `afterCpl xors A with 0xFF, sets H and N, preserves S Z PV C`() {
    val oldF = Flags.S or Flags.Z or Flags.PV or Flags.C
    val r = Flags.afterCpl(0x12, oldF)
    assertThat(r.value).isEqualTo(0xED)            // 0x12 xor 0xFF
    assertThat(r.newF and Flags.S).isNotZero       // preserved
    assertThat(r.newF and Flags.Z).isNotZero       // preserved
    assertThat(r.newF and Flags.PV).isNotZero      // preserved
    assertThat(r.newF and Flags.C).isNotZero       // preserved
    assertThat(r.newF and Flags.H).isNotZero       // set
    assertThat(r.newF and Flags.N).isNotZero       // set
}

@Test
fun `afterCpl 0x00 gives 0xFF`() {
    val r = Flags.afterCpl(0x00, 0)
    assertThat(r.value).isEqualTo(0xFF)
}

@Test
fun `afterCpl 0xFF gives 0x00`() {
    val r = Flags.afterCpl(0xFF, 0)
    assertThat(r.value).isZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * CPL: A = A xor 0xFF. H = 1, N = 1; S/Z/PV/C preserved from oldF.
 */
fun afterCpl(a: Int, oldF: Int): AluResult {
    val value = (a and 0xFF) xor 0xFF
    val f = (oldF and (S or Z or PV or C)) or H or N
    return AluResult(value, f)
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: afterScf and afterCcf

- [ ] **Step 1: Failing tests**

```kotlin
@Test
fun `afterScf sets C, clears H and N, preserves S Z PV`() {
    val oldF = Flags.S or Flags.Z or Flags.PV or Flags.H or Flags.N
    val newF = Flags.afterScf(oldF)
    assertThat(newF and Flags.S).isNotZero
    assertThat(newF and Flags.Z).isNotZero
    assertThat(newF and Flags.PV).isNotZero
    assertThat(newF and Flags.H).isZero
    assertThat(newF and Flags.N).isZero
    assertThat(newF and Flags.C).isNotZero
}

@Test
fun `afterCcf toggles C, sets H to oldC, clears N, preserves S Z PV`() {
    // Case 1: C was 1
    var oldF = Flags.C or Flags.S
    var newF = Flags.afterCcf(oldF)
    assertThat(newF and Flags.C).isZero            // toggled to 0
    assertThat(newF and Flags.H).isNotZero         // gets old C value (1)
    assertThat(newF and Flags.N).isZero
    assertThat(newF and Flags.S).isNotZero         // preserved

    // Case 2: C was 0
    oldF = Flags.S or Flags.Z
    newF = Flags.afterCcf(oldF)
    assertThat(newF and Flags.C).isNotZero         // toggled to 1
    assertThat(newF and Flags.H).isZero            // gets old C value (0)
    assertThat(newF and Flags.S).isNotZero
    assertThat(newF and Flags.Z).isNotZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * SCF: set carry flag. C=1; H=0; N=0; S/Z/PV preserved.
 * Returns just the new F (A is unchanged).
 */
fun afterScf(oldF: Int): Int = (oldF and (S or Z or PV)) or C

/**
 * CCF: complement carry flag. C=!oldC; H=oldC; N=0; S/Z/PV preserved.
 * Returns just the new F.
 */
fun afterCcf(oldF: Int): Int {
    val oldC = oldF and C
    var f = oldF and (S or Z or PV)
    if (oldC == 0) f = f or C    // toggle to 1
    if (oldC != 0) f = f or H    // H gets oldC
    return f
}
```

- [ ] **Step 4: Verify.** 2 tests.

### Task 4: afterDaa (the fiddly one)

- [ ] **Step 1: Failing test**

Append to `FlagsTest.kt`. These cases come from documented Z80 reference values:

```kotlin
@Test
fun `afterDaa after ADD 0x09+0x01 = 0x0A, no flags set, adjusts to 0x10 with H`() {
    val r = Flags.afterDaa(0x0A, oldF = 0)   // N=0, no C, no H
    assertThat(r.value).isEqualTo(0x10)
    assertThat(r.newF and Flags.H).isNotZero
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.N).isZero       // N preserved (was 0)
}

@Test
fun `afterDaa after ADD 0x99+0x01 = 0x9A, adjusts to 0x00 with C and Z`() {
    // Realistic input: 0x99 + 0x01 (binary) = 0x9A; flags from add: H=0, C=0, N=0
    val r = Flags.afterDaa(0x9A, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterDaa A=0x00 N=0 no flags is no-op (Z set)`() {
    val r = Flags.afterDaa(0x00, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterDaa A=0x00 N=1 C=1 (after sub borrow) gives 0xA0 with S and C`() {
    val r = Flags.afterDaa(0x00, oldF = Flags.N or Flags.C)
    assertThat(r.value).isEqualTo(0xA0)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.N).isNotZero    // N preserved (was 1)
}

@Test
fun `afterDaa preserves N flag`() {
    var r = Flags.afterDaa(0x12, oldF = 0)         // N=0
    assertThat(r.newF and Flags.N).isZero

    r = Flags.afterDaa(0x12, oldF = Flags.N)        // N=1
    assertThat(r.newF and Flags.N).isNotZero
}

@Test
fun `afterDaa parity flag set for even bit count in result`() {
    // 0x33 has 4 ones (even parity)
    val r = Flags.afterDaa(0x33, oldF = 0)
    assertThat(r.value).isEqualTo(0x33)
    assertThat(r.newF and Flags.PV).isNotZero
}

@Test
fun `afterDaa H flag computed from result-vs-input bit 4 difference`() {
    // 0x0A → 0x10 — bit 4 changes 0 → 1, H set
    val r = Flags.afterDaa(0x0A, oldF = 0)
    assertThat(r.newF and Flags.H).isNotZero
}

@Test
fun `afterDaa S flag set when result bit 7 is set`() {
    // 0x9A → 0x00 with C set: result bit 7 is 0, S clear
    var r = Flags.afterDaa(0x9A, oldF = 0)
    assertThat(r.newF and Flags.S).isZero

    // 0x82 stays at 0x82 (no adjustment needed): bit 7 set
    r = Flags.afterDaa(0x82, oldF = 0)
    assertThat(r.newF and Flags.S).isNotZero
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement afterDaa**

Add to `Flags.kt`:

```kotlin
/**
 * BCD adjust accumulator after a previous arithmetic operation.
 * Behavior depends on N, H, C flags from the previous op.
 *
 * Algorithm (standard table-based):
 *   if N=0 (after add):
 *     if H or low nibble > 9: correction |= 0x06
 *     if C or A > 0x99: correction |= 0x60, set new C
 *     A += correction
 *   else (after sub):
 *     if H: correction |= 0x06
 *     if C: correction |= 0x60
 *     A -= correction
 *
 * Flags after:
 * - S = bit 7 of result.
 * - Z = result == 0.
 * - H = bit-4 differs between A and result (covers add and sub paths).
 * - P/V = parity of result.
 * - N = preserved from oldF.
 * - C = potentially set if high-nibble correction was applied.
 */
fun afterDaa(a: Int, oldF: Int): AluResult {
    val n = oldF and N != 0
    val cFlag = oldF and C != 0
    val hFlag = oldF and H != 0
    var correction = 0
    var newC = cFlag
    if (hFlag || (!n && (a and 0x0F) > 9)) correction = correction or 0x06
    if (cFlag || (!n && a > 0x99)) {
        correction = correction or 0x60
        newC = true
    }
    val result = (if (n) a - correction else a + correction) and 0xFF
    var f = oldF and N    // preserve N
    if (result == 0) f = f or Z
    if (result and 0x80 != 0) f = f or S
    if (parity(result)) f = f or PV
    if (newC) f = f or C
    if ((a xor result) and 0x10 != 0) f = f or H
    return AluResult(result, f)
}
```

- [ ] **Step 4: Verify.** 8 tests.

### Task 5: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

`./gradlew test spotlessApply`. Expected: 3 + 3 + 2 + 8 = 16 new FlagsTest cases.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt
git commit -m "feat(cpu): Flags helpers for rotate-A, DAA, CPL, SCF, CCF (Phase 2.6 foundation)

afterRotateA (preserves S/Z/PV; clears H/N; C from newC), afterDaa
(standard BCD-adjust algorithm; preserves N; computes S/Z/H/PV/C),
afterCpl (preserves S/Z/PV/C; sets H/N), afterScf (sets C; clears H/N;
preserves S/Z/PV), afterCcf (toggles C; H gets oldC; clears N).
Dense per-edge tests including 8 DAA boundary cases."
```

---

## WU 2.6-2 — Rotate-A ops (4 new) + RotOps fragment

### Task 1: Rlca

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/rot/Rlca.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/rot/RlcaTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RlcaTest {
    @Test
    fun `RLCA rotates A left, bit 7 to C and to bit 0`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            a = 0x85    // bit 7 set
        }
        Rlca.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x0B)            // 0x85 → 1000_0101 → 0000_1011
        assertThat(cpu.f and Flags.C).isNotZero       // bit 7 was 1
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `RLCA with bit 7 clear leaves C clear`() {
        val cpu = Cpu().apply { a = 0x42 }
        Rlca.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x84)
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `RLCA preserves S Z PV from oldF`() {
        val cpu = Cpu().apply { a = 0x42; f = Flags.S or Flags.Z or Flags.PV }
        Rlca.execute(cpu, Memory())
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rlca.mnemonic { 0 }).isEqualTo("RLCA")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Rlca.operandLength).isZero
        assertThat(Rlca.baseCycles).isEqualTo(4)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RLCA` — rotate A left circular. Bit 7 → C and → bit 0.
 * 4 T-states. Single opcode 0x07.
 *
 * Flag rules: C from bit 7 of A. H=0, N=0. S, Z, P/V preserved.
 * (NOTE: the CB-prefixed `RLC r` variants compute S/Z/PV from the
 * result; the A-variant does NOT.)
 */
object Rlca : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val newC = (a and 0x80) != 0
        val rotated = ((a shl 1) or (if (newC) 1 else 0)) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RLCA"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 2: Rrca

Same shape as Rlca but bit 0 → C and → bit 7.

**Files:** create `Rrca.kt` and `RrcaTest.kt`. Test asserts `0x85 → 0xC2` (LSB → MSB), C set.

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RRCA` — rotate A right circular. Bit 0 → C and → bit 7.
 * 4 T-states. Single opcode 0x0F.
 *
 * Flag rules: same as RLCA — C from bit 0 of A; H/N=0; S/Z/PV preserved.
 */
object Rrca : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val newC = (a and 0x01) != 0
        val rotated = ((a ushr 1) or (if (newC) 0x80 else 0)) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RRCA"
}
```

Test pattern same as Rlca but assert `0x85 → 0xC2` and C set.

### Task 3: Rla

`RLA` — rotate A left through carry. New A bit 0 = old C; new C = old bit 7.

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RLA` — rotate A left through carry. Old C bit becomes new bit 0;
 * old bit 7 becomes new C. 4 T-states. Single opcode 0x17.
 *
 * Flag rules: same as RLCA.
 */
object Rla : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val oldC = if (cpu.f and Flags.C != 0) 1 else 0
        val newC = (a and 0x80) != 0
        val rotated = ((a shl 1) or oldC) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RLA"
}
```

Test asserts: with `a = 0x80, f = Flags.C` → after RLA: `a = 0x01` (the old C became bit 0; old bit 7 became new C), `f and C` set.

### Task 4: Rra

`RRA` — rotate A right through carry. Symmetric to Rla.

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RRA` — rotate A right through carry. Old C bit becomes new bit 7;
 * old bit 0 becomes new C. 4 T-states. Single opcode 0x1F.
 *
 * Flag rules: same as RLCA.
 */
object Rra : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val oldC = if (cpu.f and Flags.C != 0) 0x80 else 0
        val newC = (a and 0x01) != 0
        val rotated = ((a ushr 1) or oldC) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RRA"
}
```

Test: with `a = 0x01, f = Flags.C` → after RRA: `a = 0x80`, `f and C` set.

### Task 5: RotOps fragment + wire into builder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/rot/RotOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/rot/RotOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class RotOpsTest {
    @Test
    fun `installInto registers RLCA at 0x07, RRCA at 0x0F, RLA at 0x17, RRA at 0x1F`() {
        val d = Decoder()
        RotOps.installInto(d)
        assertThat(d.main[0x07]).isSameAs(Rlca)
        assertThat(d.main[0x0F]).isSameAs(Rrca)
        assertThat(d.main[0x17]).isSameAs(Rla)
        assertThat(d.main[0x1F]).isSameAs(Rra)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the rotate Op family into the decoder. Currently:
 * 4 rotate-A opcodes (RLCA, RRCA, RLA, RRA). Phase 2.7 will extend
 * this fragment with the CB-prefixed RLC/RRC/RL/RR/SLA/SRA/SRL r/(HL)
 * variants.
 */
object RotOps {
    fun installInto(d: Decoder) {
        d.main[0x07] = Rlca
        d.main[0x0F] = Rrca
        d.main[0x17] = Rla
        d.main[0x1F] = Rra
    }
}
```

- [ ] **Step 4: Wire into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:
- Add: `import ru.alepar.zx80.op.rot.RotOps`
- In `build()`, add `RotOps.installInto(d)` after the existing installers.

- [ ] **Step 5: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/rot/ \
        src/test/kotlin/ru/alepar/zx80/op/rot/ \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(op/rot): RLCA, RRCA, RLA, RRA — 4 rotate-A opcodes

Each preserves S/Z/PV from oldF (unlike CB-prefixed RLC r/RRC r/RL r/
RR r in Phase 2.7 which compute them). All 4T. New RotOps fragment
wired into OpTableBuilder."
```

---

## WU 2.6-3 — Daa, Cpl, Scf, Ccf (4 new) + extend MiscOps

### Task 1: Daa

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Daa.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/DaaTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class DaaTest {
    @Test
    fun `DAA after ADD 0x0A becomes 0x10 with H, advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; a = 0x0A; f = 0 }
        Daa.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x10)
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `DAA preserves N flag`() {
        val cpu = Cpu().apply { a = 0x42; f = Flags.N }
        Daa.execute(cpu, Memory())
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Daa.mnemonic { 0 }).isEqualTo("DAA")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Daa.operandLength).isZero
        assertThat(Daa.baseCycles).isEqualTo(4)
    }
}
```

(Per-edge DAA correctness is covered by FlagsTest's afterDaa cases.)

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DAA` — BCD adjust accumulator. Adjusts A based on N, H, C from a
 * previous arithmetic operation so the result is correct in BCD.
 * 4 T-states. Single opcode 0x27. Flag rules: see Flags.afterDaa.
 */
object Daa : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDaa(cpu.a, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DAA"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: Cpl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Cpl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/CplTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CplTest {
    @Test
    fun `CPL xors A with 0xFF, sets H and N, preserves S Z PV C`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            a = 0x12; f = Flags.S or Flags.Z or Flags.PV or Flags.C
        }
        Cpl.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xED)
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.f and Flags.S).isNotZero        // preserved
        assertThat(cpu.f and Flags.Z).isNotZero        // preserved
        assertThat(cpu.f and Flags.PV).isNotZero       // preserved
        assertThat(cpu.f and Flags.C).isNotZero        // preserved
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpl.mnemonic { 0 }).isEqualTo("CPL")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Cpl.operandLength).isZero
        assertThat(Cpl.baseCycles).isEqualTo(4)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CPL` — A = A xor 0xFF. 4 T-states. Single opcode 0x2F.
 * H=1, N=1; S/Z/PV/C preserved.
 */
object Cpl : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterCpl(cpu.a, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CPL"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: Scf

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Scf.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/ScfTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class ScfTest {
    @Test
    fun `SCF sets C, clears H and N, preserves S Z PV, leaves A untouched`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            a = 0x42; f = Flags.S or Flags.Z or Flags.PV or Flags.H or Flags.N
        }
        Scf.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)               // unchanged
        assertThat(cpu.f and Flags.C).isNotZero        // set
        assertThat(cpu.f and Flags.H).isZero            // cleared
        assertThat(cpu.f and Flags.N).isZero            // cleared
        assertThat(cpu.f and Flags.S).isNotZero        // preserved
        assertThat(cpu.f and Flags.Z).isNotZero        // preserved
        assertThat(cpu.f and Flags.PV).isNotZero       // preserved
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Scf.mnemonic { 0 }).isEqualTo("SCF")
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SCF` — Set Carry Flag. 4 T-states. Single opcode 0x37.
 * C=1; H=0; N=0; S/Z/PV preserved. A unchanged.
 */
object Scf : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.f = Flags.afterScf(cpu.f)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SCF"
}
```

- [ ] **Step 4: Verify.** 2 tests.

### Task 4: Ccf

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Ccf.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/CcfTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CcfTest {
    @Test
    fun `CCF with C=1 toggles to C=0, H gets old C value (1)`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            a = 0x42; f = Flags.C or Flags.S
        }
        Ccf.execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isZero            // toggled
        assertThat(cpu.f and Flags.H).isNotZero        // gets oldC=1
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.S).isNotZero        // preserved
        assertThat(cpu.a).isEqualTo(0x42)               // unchanged
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `CCF with C=0 toggles to C=1, H gets old C value (0)`() {
        val cpu = Cpu().apply { f = Flags.S or Flags.Z }
        Ccf.execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ccf.mnemonic { 0 }).isEqualTo("CCF")
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CCF` — Complement Carry Flag. 4 T-states. Single opcode 0x3F.
 * C=!oldC; H=oldC (counterintuitive); N=0; S/Z/PV preserved. A unchanged.
 */
object Ccf : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.f = Flags.afterCcf(cpu.f)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CCF"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 5: Extend MiscOps + MiscOpsTest

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/misc/MiscOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/misc/MiscOpsTest.kt`

- [ ] **Step 1: Add 4 registrations**

In `MiscOps.kt`, extend the `installInto(d)` body:

```kotlin
object MiscOps {
    fun installInto(d: Decoder) {
        // ... existing registrations (NOP, HALT, DI, EI, IM 0/1/2) ...
        d.main[0x27] = Daa
        d.main[0x2F] = Cpl
        d.main[0x37] = Scf
        d.main[0x3F] = Ccf
    }
}
```

- [ ] **Step 2: Add MiscOpsTest assertions**

```kotlin
@Test
fun `installInto registers DAA at 0x27, CPL at 0x2F, SCF at 0x37, CCF at 0x3F`() {
    val d = Decoder()
    MiscOps.installInto(d)
    assertThat(d.main[0x27]).isSameAs(Daa)
    assertThat(d.main[0x2F]).isSameAs(Cpl)
    assertThat(d.main[0x37]).isSameAs(Scf)
    assertThat(d.main[0x3F]).isSameAs(Ccf)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/misc/Daa.kt \
        src/main/kotlin/ru/alepar/zx80/op/misc/Cpl.kt \
        src/main/kotlin/ru/alepar/zx80/op/misc/Scf.kt \
        src/main/kotlin/ru/alepar/zx80/op/misc/Ccf.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/DaaTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/CplTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/ScfTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/CcfTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/misc/MiscOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/MiscOpsTest.kt
git commit -m "feat(op/misc): DAA, CPL, SCF, CCF — 4 flag-manipulation opcodes

DAA uses the standard table-based BCD-adjust algorithm. CPL flips A
and sets H/N. SCF sets C and clears H/N. CCF toggles C and sets H to
oldC (counterintuitively). All preserve S/Z/PV. All 4T."
```

---

## WU 2.6-4 — Sweep + tag

### Task 1: Verification + tag

- [ ] **Step 1: Clean build**

`./gradlew clean check installDist` — BUILD SUCCESSFUL.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count climbs by 8.

- [ ] **Step 3: Spotless check**

`./gradlew spotlessCheck` — green.

- [ ] **Step 4: Sanity-check the 8 FUSE cases pass**

```bash
python3 -c "
import json
d = json.load(open('build/score.json'))
failures = d['suites']['fuse']['details'].get('failures', [])
for op in ['07', '0f', '17', '1f', '27', '2f', '37', '3f']:
    has = any(f.startswith(f'{op}:') for f in failures)
    print(f'  case {op}: {\"FAIL\" if has else \"PASS\"}')"
```

Expected: all 8 PASS. If `27` (DAA) fails: revisit afterDaa edge cases — likely an H or PV computation bug.

- [ ] **Step 5: Tag**

```bash
git tag -a m1-phase02-6 -m "M1 Phase 2.6 complete: rotates on A + DAA/CPL/SCF/CCF

8 new opcode positions across 8 singleton Op classes. Foundation:
5 Flags helpers (afterRotateA, afterDaa, afterCpl, afterScf, afterCcf).
DAA uses standard table-based BCD-adjust algorithm with 8+ boundary
tests. Rotate-A variants preserve S/Z/PV (unlike CB-prefixed rotates
in Phase 2.7).

Plan 2.7 (CB-prefixed rotates/shifts/BIT/SET/RES — 256 opcodes) is
the next batch."
```

This plan is complete.

---

## Self-Review

1. **Spec coverage:** Foundation → 2.6-1. Rotate-A ops → 2.6-2. DAA/CPL/SCF/CCF → 2.6-3. Sweep + tag → 2.6-4.

2. **Placeholder scan:** No "TBD" / "TODO". Each Op has its concrete code shown.

3. **Type consistency:** All 5 new Flags helpers return `AluResult` (3 of them) or `Int` (afterScf, afterCcf for ops that don't modify A). Each Op's `execute` body delegates to the helper and updates `cpu.a` (when applicable) + `cpu.f`.

4. **Critical assertions per Op:**
   - All 4 rotate-A ops have explicit S/Z/PV preservation tests.
   - Rla/Rra explicitly tested with C=1 oldF (carry-in fold).
   - DAA tests include the famously-tricky `0x9A → 0x00 with C set` case via afterDaa unit tests.
   - CCF tests both C=1 (toggle to 0, H gets 1) and C=0 (toggle to 1, H gets 0) paths.
   - SCF and CPL tests verify A is unchanged (SCF) / changed (CPL).
