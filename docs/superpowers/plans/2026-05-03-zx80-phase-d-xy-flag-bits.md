# Phase D — X/Y Undocumented Flag Bits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lift FUSE pass rate from 805/1356 (59%) to ≥90% by modelling X (bit 5) and Y (bit 3) of F in flag-helper output. Composite SCORE expected 0.648 → ≥0.85.

**Architecture:** Mostly mechanical — append `f = f or (result and 0x28)` to ~11 existing flag helpers in `Flags.kt`. Three non-trivial paths: 16-bit ops use the high byte (`(result ushr 8) and 0x28`), CP must use its operand byte (so a dedicated `afterCp` helper is added), BIT n,r computes flags inline so its X/Y is added there. Out of scope: BIT-on-memory, IN/OUT (need MEMPTR), SCF/CCF, block ops (other quirks).

**Tech Stack:** Kotlin (JDK 21), JUnit 5, AssertJ, Gradle, Spotless ktlint.

**Spec:** `docs/superpowers/specs/2026-05-03-zx80-phase-d-xy-flag-bits-design.md`

---

## Universal patterns

**TDD per WU:**
1. Append failing tests to `FlagsTest.kt` (or `BitRegTest.kt` for D-3)
2. Run `./gradlew test --tests FlagsTest` — confirm FAIL on the new tests
3. Edit the helper(s) to OR in `result and 0x28`
4. Re-run — confirm PASS

**Test name rules:** avoid `..`, `->`, `;` in backtick names.

**Spotless ktlint** — runs on `spotlessApply`; reformats KDocs and `apply{}` blocks. Cosmetic only.

**Beads workflow per WU:** claim → execute → `./gradlew test spotlessApply` → commit per plan → `bd close <id>`.

**Recurring test pattern:** every helper test verifies the X/Y bits with these representative inputs (cover the truth table):

```kotlin
@Test
fun `<helperName> sets X and Y from <source> bits 5 and 3`() {
    // result = 0x28 -> both X and Y set
    val r1 = Flags.<helperCall producing 0x28>
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero

    // result = 0x00 -> neither set
    val r2 = Flags.<helperCall producing 0x00>
    assertThat(r2.newF and Flags.X).isZero
    assertThat(r2.newF and Flags.Y).isZero

    // result = 0x20 -> only X
    val r3 = Flags.<helperCall producing 0x20>
    assertThat(r3.newF and Flags.X).isNotZero
    assertThat(r3.newF and Flags.Y).isZero

    // result = 0x08 -> only Y
    val r4 = Flags.<helperCall producing 0x08>
    assertThat(r4.newF and Flags.X).isZero
    assertThat(r4.newF and Flags.Y).isNotZero
}
```

The plan instances this template for each helper.

## File Structure

**Modified files:**
- `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt` — 11 helpers get `f or (result and 0x28)`; new `afterCp` helper added (WU D-3); high-byte rule for 3 word helpers (WU D-2)
- `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt` — test cases for each helper change
- `src/main/kotlin/ru/alepar/zx80/op/alu/AluOp.kt` — re-route CP to new helper (WU D-3)
- `src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt` — inline X/Y from operand (WU D-3)
- `src/test/kotlin/ru/alepar/zx80/op/bit/BitRegTest.kt` — verify operand-derived X/Y (WU D-3)

No new files except optionally a tag and a sweep commit. No structural changes.

---

## WU D-1 — Result-X/Y in 8-bit ALU helpers + CPL + DAA (9 helpers)

Beads: `<BD-ID-WU-D-1>` (file under epic `<BD-ID-EPIC-D>`)

Affects 9 helpers in `Flags.kt`: `afterAdd`, `afterSub`, `afterAnd`, `afterOr`, `afterXor`, `afterInc`, `afterDec`, `afterCpl`, `afterDaa`. Each gets one line: `f = f or (result and 0x28)` immediately before `return AluResult(...)`.

**Note:** `afterSub` is shared between SUB, SBC, and CP today. After this WU, CP will TEMPORARILY have wrong X/Y (it will follow result, but should follow operand). WU D-3 fixes that. This is acceptable transient state — the per-helper test in this WU only exercises the SUB path, not CP.

### Task 1: Add failing tests to FlagsTest

**Files:**
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Append failing tests**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterAdd sets X and Y from result bits 5 and 3`() {
    // 0x10 + 0x18 = 0x28 -> X+Y both set
    val r1 = Flags.afterAdd(0x10, 0x18, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0x10 + 0x10 = 0x20 -> only X
    val r2 = Flags.afterAdd(0x10, 0x10, 0)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
    // 0x00 + 0x08 = 0x08 -> only Y
    val r3 = Flags.afterAdd(0x00, 0x08, 0)
    assertThat(r3.newF and Flags.X).isZero
    assertThat(r3.newF and Flags.Y).isNotZero
    // 0x00 + 0x00 = 0x00 -> neither
    val r4 = Flags.afterAdd(0x00, 0x00, 0)
    assertThat(r4.newF and Flags.X).isZero
    assertThat(r4.newF and Flags.Y).isZero
}

@Test
fun `afterSub sets X and Y from result bits 5 and 3`() {
    // 0x30 - 0x08 = 0x28 -> X+Y both set
    val r1 = Flags.afterSub(0x30, 0x08, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0x40 - 0x20 = 0x20 -> only X
    val r2 = Flags.afterSub(0x40, 0x20, 0)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterAnd sets X and Y from result bits 5 and 3`() {
    // 0xFF and 0x28 = 0x28 -> X+Y
    val r1 = Flags.afterAnd(0xFF, 0x28)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0xFF and 0x20 = 0x20 -> only X
    val r2 = Flags.afterAnd(0xFF, 0x20)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterOr sets X and Y from result bits 5 and 3`() {
    val r1 = Flags.afterOr(0x20, 0x08)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    val r2 = Flags.afterOr(0x00, 0x00)
    assertThat(r2.newF and Flags.X).isZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterXor sets X and Y from result bits 5 and 3`() {
    val r1 = Flags.afterXor(0x00, 0x28)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    val r2 = Flags.afterXor(0xFF, 0xD7) // 0xFF xor 0xD7 = 0x28
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isNotZero
}

@Test
fun `afterInc sets X and Y from result bits 5 and 3`() {
    val r1 = Flags.afterInc(0x27, 0) // 0x27 + 1 = 0x28
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    val r2 = Flags.afterInc(0x07, 0) // 0x07 + 1 = 0x08
    assertThat(r2.newF and Flags.X).isZero
    assertThat(r2.newF and Flags.Y).isNotZero
}

@Test
fun `afterDec sets X and Y from result bits 5 and 3`() {
    val r1 = Flags.afterDec(0x29, 0) // 0x29 - 1 = 0x28
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    val r2 = Flags.afterDec(0x21, 0) // 0x21 - 1 = 0x20
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterCpl sets X and Y from result bits 5 and 3`() {
    // 0xD7 xor 0xFF = 0x28 -> X+Y
    val r1 = Flags.afterCpl(0xD7, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0xDF xor 0xFF = 0x20 -> only X
    val r2 = Flags.afterCpl(0xDF, 0)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterDaa sets X and Y from result bits 5 and 3`() {
    // 0x28, no flags -> result = 0x28 (no correction needed) -> X+Y
    val r1 = Flags.afterDaa(0x28, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0x20, no flags -> result = 0x20 -> only X
    val r2 = Flags.afterDaa(0x20, 0)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests FlagsTest`
Expected: 9 new tests FAIL (existing pass).

### Task 2: Modify Flags.kt — add X/Y to 9 helpers

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`

- [ ] **Step 1: Update afterAdd**

In `Flags.kt`, find:
```kotlin
        if (sum > 0xFF) f = f or C
        return AluResult(value, f)
    }
```
in `afterAdd` (around line 44–46) and replace with:
```kotlin
        if (sum > 0xFF) f = f or C
        f = f or (value and 0x28)
        return AluResult(value, f)
    }
```

- [ ] **Step 2: Update afterSub**

In `afterSub` (around line 69–71) — same pattern: insert `f = f or (value and 0x28)` immediately before `return AluResult(value, f)`.

- [ ] **Step 3: Update afterAnd, afterOr, afterXor**

In each (lines ~83, 96, 109): insert `f = f or (value and 0x28)` immediately before the `return AluResult(value, f)`.

- [ ] **Step 4: Update afterInc, afterDec**

In each (lines ~131, 153): insert `f = f or (result and 0x28)` immediately before the `return AluResult(result, f)`. (Note: variable is `result`, not `value`, in these helpers.)

- [ ] **Step 5: Update afterCpl**

Locate (around line 229):
```kotlin
    fun afterCpl(a: Int, oldF: Int): AluResult {
        val value = (a and 0xFF) xor 0xFF
        val f = (oldF and (S or Z or PV or C)) or H or N
        return AluResult(value, f)
    }
```
Replace with:
```kotlin
    fun afterCpl(a: Int, oldF: Int): AluResult {
        val value = (a and 0xFF) xor 0xFF
        val f = (oldF and (S or Z or PV or C)) or H or N or (value and 0x28)
        return AluResult(value, f)
    }
```

- [ ] **Step 6: Update afterDaa**

In `afterDaa` (around line 369): insert `f = f or (result and 0x28)` immediately before the `return AluResult(result, f)`.

- [ ] **Step 7: Run to verify pass**

Run: `./gradlew test --tests FlagsTest`
Expected: all FlagsTest tests pass (existing + 9 new).

- [ ] **Step 8: Run full suite to check no regressions**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. (Some FUSE tests that previously failed will now pass — they're counted by the harness, not by JUnit, so JUnit suite is still green.)

### Task 3: WU verification + commit

- [ ] **Step 1: Run spotless**

Run: `./gradlew spotlessApply`

- [ ] **Step 2: Capture FUSE delta** (optional sanity check)

Run: `./build/install/zx80/bin/zx80 score --suite=fuse | head -1`
Expected: FUSE pass count climbed from 805 toward (but probably not yet at) 90%. The 16-bit ops, rotates, and CP/BIT are still wrong; full target is reached after WU D-4.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt
git commit -m "feat(cpu): X/Y flag bits in 8-bit ALU + INC/DEC + CPL + DAA helpers

Phase D WU 1 of 4. Append result-derived X/Y (bits 5 and 3 of the
result byte) to afterAdd, afterSub, afterAnd, afterOr, afterXor,
afterInc, afterDec, afterCpl, afterDaa. Lifts FUSE pass rate. CP path
still wrong — fixed by WU D-3.

Closes <BD-ID-WU-D-1>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

Replace `<BD-ID-WU-D-1>` with the actual beads ID.

- [ ] **Step 4: Close beads**

Run: `bd close <BD-ID-WU-D-1>`

---

## WU D-2 — Result-X/Y in 16-bit ops + rotate/shift helpers (5 helpers)

Beads: `<BD-ID-WU-D-2>` (file under epic).

16-bit pair ops use the **high byte** of result for X/Y: `(result ushr 8) and 0x28`. Rotate/shift ops use result-low-byte.

Affects 5 helpers: `afterAddWord`, `afterAdcWord`, `afterSbcWord`, `afterRotateA`, `computeRotateShiftFlags`.

### Task 1: Add failing tests

**Files:**
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Append failing tests**

```kotlin
@Test
fun `afterAddWord sets X and Y from result high byte bits 5 and 3`() {
    // 0x1020 + 0x1808 = 0x2828 -> high byte 0x28 -> X+Y
    val r1 = Flags.afterAddWord(0x1020, 0x1808, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0x1000 + 0x1000 = 0x2000 -> high byte 0x20 -> only X
    val r2 = Flags.afterAddWord(0x1000, 0x1000, 0)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
    // 0x0000 + 0x0800 = 0x0800 -> high byte 0x08 -> only Y
    val r3 = Flags.afterAddWord(0x0000, 0x0800, 0)
    assertThat(r3.newF and Flags.X).isZero
    assertThat(r3.newF and Flags.Y).isNotZero
}

@Test
fun `afterAdcWord sets X and Y from result high byte bits 5 and 3`() {
    // 0x1020 + 0x1808 = 0x2828, no carry -> high byte 0x28 -> X+Y
    val r1 = Flags.afterAdcWord(0x1020, 0x1808, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0x1000 + 0x1000 with carry-in C=1 = 0x2001 -> high byte 0x20 -> only X
    val r2 = Flags.afterAdcWord(0x1000, 0x1000, Flags.C)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterSbcWord sets X and Y from result high byte bits 5 and 3`() {
    // 0x4000 - 0x1800 = 0x2800 -> high byte 0x28
    val r1 = Flags.afterSbcWord(0x4000, 0x1800, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // 0x3000 - 0x1000 = 0x2000 -> high byte 0x20 -> only X
    val r2 = Flags.afterSbcWord(0x3000, 0x1000, 0)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterRotateA sets X and Y from result bits 5 and 3`() {
    // rotated value = 0x28 -> X+Y both set
    val r1 = Flags.afterRotateA(0x28, false, 0)
    assertThat(r1.newF and Flags.X).isNotZero
    assertThat(r1.newF and Flags.Y).isNotZero
    // rotated value = 0x00 -> neither
    val r2 = Flags.afterRotateA(0x00, false, 0)
    assertThat(r2.newF and Flags.X).isZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `RLC RRC RL RR SLA SRA SLL SRL set X and Y from result bits 5 and 3`() {
    // RLC of 0x14 -> bit 7 was 0, shift left -> 0x28 -> X+Y
    val rlc = Flags.afterRlc(0x14)
    assertThat(rlc.newF and Flags.X).isNotZero
    assertThat(rlc.newF and Flags.Y).isNotZero
    // SLA of 0x14 -> 0x28
    val sla = Flags.afterSla(0x14)
    assertThat(sla.newF and Flags.X).isNotZero
    assertThat(sla.newF and Flags.Y).isNotZero
    // SRL of 0x50 -> 0x28
    val srl = Flags.afterSrl(0x50)
    assertThat(srl.newF and Flags.X).isNotZero
    assertThat(srl.newF and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests FlagsTest`
Expected: 5 new tests FAIL.

### Task 2: Modify Flags.kt — add X/Y to 5 helpers

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`

- [ ] **Step 1: Update afterAddWord**

Locate (around line 163):
```kotlin
    fun afterAddWord(a: Int, b: Int, oldF: Int): AluResult {
        val sum = a + b
        val value = sum and 0xFFFF
        var f = oldF and (S or Z or PV) // preserve these
        if ((a and 0x0FFF) + (b and 0x0FFF) > 0x0FFF) f = f or H
        if (sum > 0xFFFF) f = f or C
        return AluResult(value, f)
    }
```
Replace with:
```kotlin
    fun afterAddWord(a: Int, b: Int, oldF: Int): AluResult {
        val sum = a + b
        val value = sum and 0xFFFF
        var f = oldF and (S or Z or PV) // preserve these
        if ((a and 0x0FFF) + (b and 0x0FFF) > 0x0FFF) f = f or H
        if (sum > 0xFFFF) f = f or C
        f = f or ((value ushr 8) and 0x28)
        return AluResult(value, f)
    }
```

- [ ] **Step 2: Update afterAdcWord**

Locate (around line 181):
```kotlin
        if (sum > 0xFFFF) f = f or C
        return AluResult(value, f)
    }
```
in `afterAdcWord` and replace with:
```kotlin
        if (sum > 0xFFFF) f = f or C
        f = f or ((value ushr 8) and 0x28)
        return AluResult(value, f)
    }
```

- [ ] **Step 3: Update afterSbcWord**

Locate (around line 213):
```kotlin
        if (diff < 0) f = f or C
        return AluResult(value, f)
    }
```
in `afterSbcWord` and replace with:
```kotlin
        if (diff < 0) f = f or C
        f = f or ((value ushr 8) and 0x28)
        return AluResult(value, f)
    }
```

- [ ] **Step 4: Update afterRotateA**

Locate (around line 222):
```kotlin
    fun afterRotateA(rotated: Int, newC: Boolean, oldF: Int): AluResult {
        var f = oldF and (S or Z or PV)
        if (newC) f = f or C
        return AluResult(rotated and 0xFF, f)
    }
```
Replace with:
```kotlin
    fun afterRotateA(rotated: Int, newC: Boolean, oldF: Int): AluResult {
        val value = rotated and 0xFF
        var f = oldF and (S or Z or PV)
        if (newC) f = f or C
        f = f or (value and 0x28)
        return AluResult(value, f)
    }
```

- [ ] **Step 5: Update computeRotateShiftFlags**

Locate (around line 342):
```kotlin
    private fun computeRotateShiftFlags(result: Int, newC: Boolean): Int {
        var f = 0
        if (result == 0) f = f or Z
        if (result and 0x80 != 0) f = f or S
        if (parity(result)) f = f or PV
        if (newC) f = f or C
        return f
    }
```
Replace with:
```kotlin
    private fun computeRotateShiftFlags(result: Int, newC: Boolean): Int {
        var f = 0
        if (result == 0) f = f or Z
        if (result and 0x80 != 0) f = f or S
        if (parity(result)) f = f or PV
        if (newC) f = f or C
        f = f or (result and 0x28)
        return f
    }
```

- [ ] **Step 6: Run to verify pass**

Run: `./gradlew test --tests FlagsTest`
Expected: all FlagsTest tests pass.

- [ ] **Step 7: Run full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

### Task 3: WU verification + commit

- [ ] **Step 1: Run spotless**

Run: `./gradlew spotlessApply`

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt
git commit -m "feat(cpu): X/Y flag bits in 16-bit ALU + rotate/shift helpers

Phase D WU 2 of 4. Append (result high byte) X/Y to afterAddWord,
afterAdcWord, afterSbcWord. Append result X/Y to afterRotateA and the
shared computeRotateShiftFlags helper (covers RLC/RRC/RL/RR/SLA/SRA/
SLL/SRL).

Closes <BD-ID-WU-D-2>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-D-2>`

---

## WU D-3 — CP separate path + BIT n,r operand-X/Y

Beads: `<BD-ID-WU-D-3>` (file under epic).

Two non-trivial paths in this WU:
1. CP cannot share `afterSub` — its X/Y come from operand `b`, not result. Add `Flags.afterCp(a, b, borrow)` and re-route in `AluOp.apply`.
2. `BitReg` computes flags inline — add `(src.read(cpu) and 0x28)` to its inline F.

### Task 1: Add Flags.afterCp + failing tests

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Append failing tests for afterCp**

```kotlin
@Test
fun `afterCp sets X and Y from operand bits 5 and 3 NOT from result`() {
    // a=0x80, b=0x28 -> result=0x58 (X clear, Y clear in result)
    // but X+Y should come from b=0x28 -> both set
    val r1 = Flags.afterCp(0x80, 0x28, 0)
    assertThat(r1.value).isEqualTo(0x58) // result = a - b
    assertThat(r1.newF and Flags.X).isNotZero // from b, not result
    assertThat(r1.newF and Flags.Y).isNotZero
    // a=0x40, b=0x20 -> result=0x20, b=0x20 -> only X
    val r2 = Flags.afterCp(0x40, 0x20, 0)
    assertThat(r2.newF and Flags.X).isNotZero
    assertThat(r2.newF and Flags.Y).isZero
}

@Test
fun `afterCp matches afterSub on all flags except X and Y`() {
    val a = 0x42
    val b = 0x10
    val cp = Flags.afterCp(a, b, 0)
    val sub = Flags.afterSub(a, b, 0)
    // value identical
    assertThat(cp.value).isEqualTo(sub.value)
    // flags identical except X, Y
    val maskExceptXY = (Flags.S or Flags.Z or Flags.H or Flags.PV or Flags.N or Flags.C)
    assertThat(cp.newF and maskExceptXY).isEqualTo(sub.newF and maskExceptXY)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests FlagsTest`
Expected: FAIL (`Unresolved reference: afterCp`).

- [ ] **Step 3: Add afterCp to Flags.kt**

After `afterSub` (around line 71) in `Flags.kt`, insert:

```kotlin
/**
 * CP a, b: same arithmetic as SUB but X/Y come from operand b (not result), per Z80 quirk.
 * The returned `value` is `(a - b) and 0xFF`; callers ignore it (CP doesn't write A) but flags
 * are based on it.
 */
fun afterCp(a: Int, b: Int, borrow: Int): AluResult {
    val diff = a - b - borrow
    val value = diff and 0xFF
    var f = N
    if (value == 0) f = f or Z
    if (value and 0x80 != 0) f = f or S
    if ((a and 0x0F) - (b and 0x0F) - borrow < 0) f = f or H
    if ((a xor b) and 0x80 != 0 && (a xor value) and 0x80 != 0) f = f or PV
    if (diff < 0) f = f or C
    f = f or (b and 0x28) // X/Y from operand
    return AluResult(value, f)
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests FlagsTest`
Expected: all pass.

### Task 2: Re-route CP through afterCp

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/alu/AluOp.kt`

- [ ] **Step 1: Update AluOp.apply**

In `AluOp.kt`, locate:
```kotlin
SUB,
CP -> Flags.afterSub(a, b, 0)
SBC -> Flags.afterSub(a, b, if (oldF and Flags.C != 0) 1 else 0)
```
Replace with:
```kotlin
SUB -> Flags.afterSub(a, b, 0)
CP -> Flags.afterCp(a, b, 0)
SBC -> Flags.afterSub(a, b, if (oldF and Flags.C != 0) 1 else 0)
```

- [ ] **Step 2: Run all tests to confirm regression-clean**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — existing CP-related tests (e.g. AluAReg with CP, FuseSuiteTest) still pass since `afterCp` produces matching flags except for X/Y, which existing tests don't currently assert on.

### Task 3: Add operand-X/Y to BitReg

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/bit/BitRegTest.kt`

- [ ] **Step 1: Add failing test in BitRegTest**

Append to `BitRegTest.kt`:

```kotlin
@Test
fun `BIT n, r sets X and Y from operand bits 5 and 3 NOT from bit-test result`() {
    // operand = 0x28 -> X+Y both set in F regardless of which bit is tested
    val cpu = Cpu().apply { b = 0x28 }
    BitReg(0, Reg.B).execute(cpu, Memory())
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero

    val cpu2 = Cpu().apply { b = 0x20 }
    BitReg(7, Reg.B).execute(cpu2, Memory())
    assertThat(cpu2.f and Flags.X).isNotZero
    assertThat(cpu2.f and Flags.Y).isZero

    val cpu3 = Cpu().apply { c = 0x08 }
    BitReg(3, Reg.C).execute(cpu3, Memory())
    assertThat(cpu3.f and Flags.X).isZero
    assertThat(cpu3.f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests BitRegTest`
Expected: FAIL on the new test.

- [ ] **Step 3: Update BitReg.execute**

In `src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt`, locate:
```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    val bit = (src.read(cpu) shr n) and 1
    var f = cpu.f and Flags.C
    f = f or Flags.H
    if (bit == 0) f = f or Flags.Z
    cpu.f = f
    cpu.pc = (cpu.pc + 2) and 0xFFFF
    cpu.bumpR(2)
    cpu.tStates += baseCycles
}
```
Replace with:
```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    val operand = src.read(cpu)
    val bit = (operand shr n) and 1
    var f = cpu.f and Flags.C
    f = f or Flags.H
    if (bit == 0) f = f or Flags.Z
    f = f or (operand and 0x28) // X/Y from operand
    cpu.f = f
    cpu.pc = (cpu.pc + 2) and 0xFFFF
    cpu.bumpR(2)
    cpu.tStates += baseCycles
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests BitRegTest`
Expected: all pass.

- [ ] **Step 5: Run full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

### Task 4: WU verification + commit

- [ ] **Step 1: Run spotless**

Run: `./gradlew spotlessApply`

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/main/kotlin/ru/alepar/zx80/op/alu/AluOp.kt \
        src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/BitRegTest.kt
git commit -m "feat(cpu): CP and BIT n,r get X/Y from operand (Z80 quirk)

Phase D WU 3 of 4. CP's X/Y come from operand b, not from the
subtraction result; add a dedicated Flags.afterCp helper and re-route
CP in AluOp.apply. BIT n,r's X/Y come from the operand register being
tested; add the inline OR in BitReg.execute. BIT n,(HL) and BIT n,
(IX+d) remain incorrect — they need MEMPTR (out of scope per Phase D
spec).

Closes <BD-ID-WU-D-3>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-D-3>`

---

## WU D-4 — Sweep + tag m1-phase-d

Beads: `<BD-ID-WU-D-4>`.

### Task 1: Verification

- [ ] **Step 1: Run full suite**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: ZEXDOC regression check**

Run: `timeout 600 ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc-d.log`

Expected: `Tests complete` line appears; `grep -c 'IllegalStateException' /tmp/zexdoc-d.log` returns 0; `grep -c 'ERROR \*\*\*\*' /tmp/zexdoc-d.log` returns 0.

If any of these checks fail, STOP — Phase D introduced a regression. Investigate before tagging.

- [ ] **Step 3: Capture FUSE delta + score**

Run: `./build/install/zx80/bin/zx80 score`
Expected SCORE line: composite ≥ 0.85. FUSE pass count ≥ 1220/1356 (≥ 90%). Capture exact line.

If FUSE < 90%, examine `build/score.json` failures. Check whether ALL remaining failures fall into one of:
- `BIT n,(HL)` / `BIT n,(IX+d)` → MEMPTR-dependent, expected
- `SCF` / `CCF` → quirky X/Y rule, expected
- `LDI/LDD/LDIR/LDDR/CPI/CPD/CPIR/CPDR` → block-op X/Y quirk, expected
- `IN r,(C)` → MEMPTR-dependent, expected

If ALL fall into those: Phase D is complete; file Phase E only if user wants to push further. If failures appear elsewhere, investigate.

- [ ] **Step 4: Apply tag**

```bash
git tag -a m1-phase-d -m "Phase D: X/Y undocumented flag bits modelled (no MEMPTR)

Trivial result-derived X/Y added to 11 flag helpers; high-byte rule
for 3 word helpers; dedicated afterCp path for CP's operand-derived
X/Y; inline X/Y in BitReg from the tested register. Out-of-scope: BIT
on memory and IN/OUT (MEMPTR-dependent), SCF/CCF and block ops (other
quirks).

SCORE: <PASTE-SCORE-LINE-HERE>
FUSE pass: <X>/1356 (was 805/1356)
ZEXDOC: clean, regression-free."
```

Replace placeholders with captured values from steps 2-3.

### Task 2: Close beads + epic

- [ ] **Step 1: Close WU D-4**

Run: `bd close <BD-ID-WU-D-4> --reason="Phase D sweep complete; m1-phase-d tag applied; FUSE pass-rate climbed to <X>/1356."`

- [ ] **Step 2: Close Phase D epic**

Run: `bd close <BD-ID-EPIC-D> --reason="All 4 WUs (D-1 through D-4) closed; FUSE >= 90%."`

- [ ] **Step 3: File Phase E if requested**

If the user wants to push further (toward FUSE 100%), file Phase E:

```bash
bd create --type=epic --priority=3 \
  --title="Phase E: MEMPTR + remaining flag-edge quirks (BIT-on-memory, SCF/CCF, block ops)" \
  --description="Residual FUSE failures after Phase D. Modelling MEMPTR (Z80's hidden 16-bit WZ register) closes BIT n,(HL), BIT n,(IX+d), and IN r,(C). Special-rule X/Y for SCF/CCF and the LDI/LDD/CPI/CPD block ops closes the rest. Implementation: add cpu.memptr field, update each memory-touching op to maintain it per Z80 update rules (~15 unique rules), then update affected flag helpers to consume MEMPTR for X/Y. Estimated 30-40 op classes touched."
```

If not requested, skip.

---

## Self-Review

**1. Spec coverage:**

| Spec section | Plan task |
|---|---|
| `afterAdd, afterSub, afterAnd, afterOr, afterXor, afterInc, afterDec, afterCpl, afterDaa` get result-X/Y | WU D-1 Tasks 1-2 |
| `afterAddWord, afterAdcWord, afterSbcWord` get high-byte X/Y | WU D-2 Tasks 1-2 |
| `afterRotateA` and `computeRotateShiftFlags` get result-X/Y | WU D-2 Tasks 1-2 |
| `afterCp` helper added; `AluOp.apply` re-routes CP | WU D-3 Tasks 1-2 |
| `BitReg` inline X/Y from operand | WU D-3 Task 3 |
| Out-of-scope (BIT-on-memory, SCF/CCF, block ops, IN/OUT): no plan task | acknowledged in spec |
| ZEXDOC regression-check, FUSE ≥90%, score ≥0.85 | WU D-4 Task 1 Step 3 |
| Tag `m1-phase-d` | WU D-4 Task 1 Step 4 |
| Phase E filing if requested | WU D-4 Task 2 Step 3 |

All spec sections covered.

**2. Placeholder scan:** Plan uses `<BD-ID-WU-D-N>`, `<BD-ID-EPIC-D>`, `<PASTE-SCORE-LINE-HERE>`, `<X>` — these are runtime substitutions filled in by the executing agent (the actual beads IDs after WUs are filed; the actual SCORE output). This is documented usage, not placeholder gaps.

**3. Type consistency:** `afterCp` parameter list `(a: Int, b: Int, borrow: Int)` matches `afterSub` for parallelism. `Flags.X` and `Flags.Y` constants already exist (Flags.kt:15-17). The `afterCp` test uses `r1.value`, `r1.newF`, matching existing `AluResult` fields. All test cases use `Flags.X`, `Flags.Y`, `Flags.S` etc. — established constants. The `(value and 0x28)` and `(result and 0x28)` patterns are syntactically interchangeable; plan uses whichever variable name appears in the helper being modified.

**4. Test name lint:** No backtick names contain `..`, `->`, or `;`.
