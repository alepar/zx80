# ZX Spectrum Emulator — Phase 2.5 Implementation Plan (PUSH/POP rr)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Z80 16-bit stack push/pop for register pairs (PUSH/POP BC/DE/HL/AF). 8 opcodes across 2 Op classes, reusing the `Cpu.push`/`Cpu.pop` helpers from Phase 2.4.

**Architecture:** Extend `RegPair` enum with a 5th value `AF` and a new `fromPushPopBits` companion helper (PUSH/POP use a different qq encoding than LD rr,nn — `qq=11` means AF, not SP). Two thin Op classes (`PushPair(src)`, `PopPair(dst)`) that delegate to `cpu.push`/`cpu.pop`. Both reject SP via `init` guard.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-5-push-pop-design.md`
**Branch:** `opus-4.7`
**Base commit:** spec commit. Phase 2.4 must complete first.

---

## Universal patterns

Same as prior phases. New notes:

- **PUSH never touches F.** POP AF *does* change F, but only because the popped low byte becomes the new F value — not from any flag computation.
- **PUSH and POP reject `RegPair.SP`** via `init { require(src/dst != RegPair.SP) }`.
- **`Cpu.push` already handles SP pre-decrement and value masking** (from Phase 2.4). PushPair just delegates: `cpu.push(mem, src.read(cpu))`.

---

## File Structure

**New files:**
```
src/main/kotlin/ru/alepar/zx80/op/stack/
  PushPair.kt                  # WU 2.5-2
  PopPair.kt                   # WU 2.5-2
  StackOps.kt                  # WU 2.5-1

src/test/kotlin/ru/alepar/zx80/op/stack/
  PushPairTest.kt              # WU 2.5-2
  PopPairTest.kt               # WU 2.5-2
  StackOpsTest.kt              # WU 2.5-1 (created), filled in 2.5-2
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt        # WU 2.5-1 (add AF + fromPushPopBits)
src/test/kotlin/ru/alepar/zx80/cpu/RegPairTest.kt    # WU 2.5-1 (extend tests)
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt  # WU 2.5-1 (add StackOps.installInto)
```

---

## WU 2.5-1 — RegPair extension + StackOps skeleton

### Task 1: Add `RegPair.AF` and `fromPushPopBits`

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/RegPairTest.kt`

- [ ] **Step 1: Failing tests**

Append to `RegPairTest.kt`:

```kotlin
@Test
fun `AF reads and writes cpu af`() {
    val cpu = Cpu().apply { a = 0x12; f = 0x34 }
    assertThat(RegPair.AF.read(cpu)).isEqualTo(0x1234)

    RegPair.AF.write(cpu, 0xABCD)
    assertThat(cpu.a).isEqualTo(0xAB)
    assertThat(cpu.f).isEqualTo(0xCD)
    assertThat(cpu.af).isEqualTo(0xABCD)
}

@Test
fun `AF mnemonic`() {
    assertThat(RegPair.AF.mnemonic).isEqualTo("AF")
}

@Test
fun `fromPushPopBits maps PUSH POP bit patterns BC=0, DE=1, HL=2, AF=3`() {
    assertThat(RegPair.fromPushPopBits(0)).isEqualTo(RegPair.BC)
    assertThat(RegPair.fromPushPopBits(1)).isEqualTo(RegPair.DE)
    assertThat(RegPair.fromPushPopBits(2)).isEqualTo(RegPair.HL)
    assertThat(RegPair.fromPushPopBits(3)).isEqualTo(RegPair.AF)
}

@Test
fun `fromPushPopBits masks to lowest 2 bits`() {
    assertThat(RegPair.fromPushPopBits(0xFC)).isEqualTo(RegPair.BC)
    assertThat(RegPair.fromPushPopBits(0xFF)).isEqualTo(RegPair.AF)
}

@Test
fun `fromBits still maps bits=3 to SP (unchanged)`() {
    assertThat(RegPair.fromBits(3)).isEqualTo(RegPair.SP)
}
```

- [ ] **Step 2: Verify fails.** `./gradlew test --tests RegPairTest` — `Unresolved reference: AF` and `fromPushPopBits`.

- [ ] **Step 3: Extend RegPair**

In `src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt`:

1. Add a 5th enum value `AF` after `SP`:

```kotlin
enum class RegPair(val mnemonic: String) {
    BC("BC"),
    DE("DE"),
    HL("HL"),
    SP("SP"),
    AF("AF");
    // ... rest of class
}
```

2. Extend `read` and `write` `when` blocks with the `AF` case (read returns `cpu.af`, write does `cpu.af = v`).

3. Update the class KDoc to mention that `AF` is only valid for PUSH/POP — `fromBits` (the standard rr encoding) does NOT return `AF`. Add a separate `fromPushPopBits` companion helper:

```kotlin
companion object {
    /** Standard rr encoding: 00=BC, 01=DE, 10=HL, 11=SP. Used by LD rr,nn etc. */
    fun fromBits(bits: Int): RegPair = when (bits and 0x03) {
        0 -> BC; 1 -> DE; 2 -> HL; 3 -> SP
        else -> error("unreachable")
    }

    /** PUSH/POP encoding: 00=BC, 01=DE, 10=HL, 11=AF. */
    fun fromPushPopBits(bits: Int): RegPair = when (bits and 0x03) {
        0 -> BC; 1 -> DE; 2 -> HL; 3 -> AF
        else -> error("unreachable")
    }
}
```

- [ ] **Step 4: Verify pass.** All RegPairTest tests pass (existing + 5 new = 11).

### Task 2: StackOps skeleton + wire into builder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/stack/StackOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/stack/StackOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class StackOpsTest {
    @Test
    fun `installInto on empty Decoder installs nothing yet (skeleton)`() {
        val d = Decoder()
        StackOps.installInto(d)
        val totalInstalled = listOf(d.main, d.cb, d.ed, d.dd, d.fd, d.ddcb, d.fdcb)
            .sumOf { table -> table.count { it != null } }
        assertThat(totalInstalled).isZero
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Skeleton implementation**

```kotlin
package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the stack Op family (PUSH rr, POP rr) into the decoder.
 * Called by [ru.alepar.zx80.op.OpTableBuilder]. Filled in by WU 2.5-2.
 */
object StackOps {
    fun installInto(d: Decoder) {
        // Filled in by WU 2.5-2.
    }
}
```

- [ ] **Step 4: Wire into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:
- Add: `import ru.alepar.zx80.op.stack.StackOps`
- In `build()`, add `StackOps.installInto(d)` after the existing `installInto` calls.

- [ ] **Step 5: Verify**

`./gradlew test` — entire suite still green; new StackOpsTest passes (1 trivial assertion).

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

`./gradlew test spotlessApply`. Expected: 5 (RegPair extensions) + 1 (StackOps skeleton) = 6 new tests.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/RegPairTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/stack/StackOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/stack/StackOpsTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(cpu): RegPair gains AF + fromPushPopBits (Phase 2.5 foundation)

Adds 5th RegPair enum value AF (read/writes cpu.af). New companion
helper fromPushPopBits maps the PUSH/POP-specific bit pattern
(00=BC, 01=DE, 10=HL, 11=AF) — distinct from fromBits (which has
SP at 11). Existing fromBits behavior unchanged.

Empty StackOps fragment skeleton wired into OpTableBuilder."
```

---

## WU 2.5-2 — PushPair + PopPair (8 opcodes)

### Task 1: PushPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/stack/PushPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/stack/PushPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class PushPairTest {
    @Test
    fun `PUSH BC pushes BC value (high byte at SP-1, low at SP-2), SP -= 2, 11 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            sp = 0x4000; bc = 0xABCD
        }
        val mem = Memory()
        PushPair(src = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0xCD)   // low byte
        assertThat(mem.read(0x3FFF)).isEqualTo(0xAB)   // high byte
        assertThat(cpu.bc).isEqualTo(0xABCD)            // src unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `PUSH AF pushes A as high, F as low`() {
        val cpu = Cpu().apply { sp = 0x4000; a = 0x12; f = 0x34 }
        val mem = Memory()
        PushPair(src = RegPair.AF).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x34)   // F (low)
        assertThat(mem.read(0x3FFF)).isEqualTo(0x12)   // A (high)
    }

    @Test
    fun `PUSH does NOT touch flags`() {
        val cpu = Cpu().apply { sp = 0x4000; bc = 0x1234; f = 0xAA }
        PushPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `PushPair rejects RegPair SP`() {
        assertThatThrownBy { PushPair(src = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(PushPair(src = RegPair.BC).mnemonic { 0 }).isEqualTo("PUSH BC")
        assertThat(PushPair(src = RegPair.AF).mnemonic { 0 }).isEqualTo("PUSH AF")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        val op = PushPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `PUSH rr` — push a 16-bit register pair onto the Z80 stack.
 * SP is pre-decremented twice (high byte at SP-1, low byte at SP-2 = new SP).
 * 11 T-states. No flag changes.
 *
 * Valid pairs: BC, DE, HL, AF. (SP is rejected — pushing the stack
 * pointer onto the stack itself isn't a valid Z80 operation here.)
 *
 * Opcodes: 0xC5 (BC), 0xD5 (DE), 0xE5 (HL), 0xF5 (AF).
 */
class PushPair(private val src: RegPair) : Op {
    init {
        require(src != RegPair.SP) { "PUSH does not accept SP; valid pairs are BC/DE/HL/AF" }
    }

    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.push(mem, src.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "PUSH ${src.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 2: PopPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/stack/PopPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/stack/PopPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class PopPairTest {
    @Test
    fun `POP BC pops 16-bit value into BC, SP += 2, 10 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            sp = 0x3FFE; bc = 0
        }
        val mem = Memory().apply {
            write(0x3FFE, 0xCD)   // low byte
            write(0x3FFF, 0xAB)   // high byte
        }
        PopPair(dst = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `POP AF loads A from high byte and F from low byte`() {
        val cpu = Cpu().apply { sp = 0x3FFE }
        val mem = Memory().apply {
            write(0x3FFE, 0x55)   // becomes F
            write(0x3FFF, 0xAB)   // becomes A
        }
        PopPair(dst = RegPair.AF).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0xAB)
        assertThat(cpu.f).isEqualTo(0x55)
        assertThat(cpu.af).isEqualTo(0xAB55)
    }

    @Test
    fun `POP non-AF pair does NOT touch f`() {
        val cpu = Cpu().apply { sp = 0x3FFE; f = 0xAA }
        val mem = Memory().apply { write(0x3FFE, 0xCD); write(0x3FFF, 0xAB) }
        PopPair(dst = RegPair.HL).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xAA)        // untouched
        assertThat(cpu.hl).isEqualTo(0xABCD)
    }

    @Test
    fun `PopPair rejects RegPair SP`() {
        assertThatThrownBy { PopPair(dst = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(PopPair(dst = RegPair.BC).mnemonic { 0 }).isEqualTo("POP BC")
        assertThat(PopPair(dst = RegPair.AF).mnemonic { 0 }).isEqualTo("POP AF")
    }

    @Test
    fun `operandLength=0, baseCycles=10`() {
        val op = PopPair(dst = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `POP rr` — pop a 16-bit value off the Z80 stack into a register pair.
 * SP is post-incremented twice (low byte from SP, high byte from SP+1).
 * 10 T-states.
 *
 * Valid pairs: BC, DE, HL, AF. (SP is rejected.)
 *
 * Note: POP AF loads F from the popped low byte — flags do change, but
 * are loaded from memory rather than computed.
 *
 * Opcodes: 0xC1 (BC), 0xD1 (DE), 0xE1 (HL), 0xF1 (AF).
 */
class PopPair(private val dst: RegPair) : Op {
    init {
        require(dst != RegPair.SP) { "POP does not accept SP; valid pairs are BC/DE/HL/AF" }
    }

    override val operandLength = 0
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, cpu.pop(mem))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "POP ${dst.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 3: Fill in StackOps and tests

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/stack/StackOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/stack/StackOpsTest.kt`

- [ ] **Step 1: Replace skeleton with real installer**

```kotlin
package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.RegPair

/**
 * Registers the stack Op family (PUSH rr, POP rr) into the decoder.
 * Called by [ru.alepar.zx80.op.OpTableBuilder]. PUSH/POP use the
 * AF-at-bits-3 encoding (RegPair.fromPushPopBits), distinct from
 * the SP-at-bits-3 encoding used by LD rr,nn etc.
 */
object StackOps {
    fun installInto(d: Decoder) {
        // PUSH rr — 11 qq 0101 → C5, D5, E5, F5
        // POP  rr — 11 qq 0001 → C1, D1, E1, F1
        for (qqBits in 0..3) {
            val pair = RegPair.fromPushPopBits(qqBits)
            d.main[0xC5 or (qqBits shl 4)] = PushPair(src = pair)
            d.main[0xC1 or (qqBits shl 4)] = PopPair(dst = pair)
        }
    }
}
```

- [ ] **Step 2: Replace StackOpsTest body with real assertions**

```kotlin
package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class StackOpsTest {

    @Test
    fun `installInto registers PUSH rr at 0xC5 (BC), 0xD5 (DE), 0xE5 (HL), 0xF5 (AF)`() {
        val d = Decoder()
        StackOps.installInto(d)
        assertThat((d.main[0xC5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH BC")
        assertThat((d.main[0xD5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH DE")
        assertThat((d.main[0xE5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH HL")
        assertThat((d.main[0xF5] as PushPair).mnemonic { 0 }).isEqualTo("PUSH AF")
    }

    @Test
    fun `installInto registers POP rr at 0xC1 (BC), 0xD1 (DE), 0xE1 (HL), 0xF1 (AF)`() {
        val d = Decoder()
        StackOps.installInto(d)
        assertThat((d.main[0xC1] as PopPair).mnemonic { 0 }).isEqualTo("POP BC")
        assertThat((d.main[0xD1] as PopPair).mnemonic { 0 }).isEqualTo("POP DE")
        assertThat((d.main[0xE1] as PopPair).mnemonic { 0 }).isEqualTo("POP HL")
        assertThat((d.main[0xF1] as PopPair).mnemonic { 0 }).isEqualTo("POP AF")
    }
}
```

- [ ] **Step 3: Run full suite + spotless**

`./gradlew test spotlessApply`. Expected: 6 (PushPair) + 6 (PopPair) + 2 (StackOps) - 1 (the trivial WU 2.5-1 test was replaced) = 13 new vs prior.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/stack/PushPair.kt \
        src/main/kotlin/ru/alepar/zx80/op/stack/PopPair.kt \
        src/test/kotlin/ru/alepar/zx80/op/stack/PushPairTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/stack/PopPairTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/stack/StackOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/stack/StackOpsTest.kt
git commit -m "feat(op/stack): PUSH rr and POP rr — 8 opcodes

Both Op classes parameterized over RegPair (BC/DE/HL/AF only; SP
rejected via init guard). PushPair delegates to cpu.push; PopPair
to cpu.pop. POP AF loads F directly from popped low byte."
```

---

## WU 2.5-3 — Sweep + tag

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

- [ ] **Step 4: Sanity-check FUSE C5 case (PUSH BC) passes**

```bash
python3 -c "import json; d = json.load(open('build/score.json')); failures = d['suites']['fuse']['details'].get('failures', []); has_c5 = any(f.startswith('c5:') for f in failures); print(f'failure for case c5 present: {has_c5}')"
```

Expected: `False`.

- [ ] **Step 5: Tag**

```bash
git tag -a m1-phase02-5 -m "M1 Phase 2.5 complete: PUSH/POP rr

8 new opcode positions across 2 Op classes (PushPair, PopPair) covering
PUSH/POP BC/DE/HL/AF. Foundation: RegPair gains AF value + new
fromPushPopBits companion helper for the PUSH/POP-specific qq encoding
(11=AF, distinct from LD rr,nn's qq=11=SP). Both Op classes reject
RegPair.SP via init guard.

Plan 2.6 (rotates/shifts on A + DAA/CPL/SCF/CCF) is the next batch."
```

This plan is complete.

---

## Self-Review

1. **Spec coverage:** Foundation → 2.5-1. Op classes + fragment fill-in → 2.5-2. Sweep + tag → 2.5-3.

2. **Placeholder scan:** No "TBD" / "TODO". Each Op has its concrete code shown.

3. **Type consistency:** `RegPair.AF.read/write` uses `cpu.af`. `PushPair`/`PopPair` use `cpu.push`/`cpu.pop` from Phase 2.4. Both reject `RegPair.SP` consistently.

4. **Critical assertions verified per Op class:**
   - PUSH BC byte order (low at SP-2, high at SP-1).
   - PUSH AF specifically tested for A (high) + F (low).
   - PUSH preserves f.
   - POP AF loads F from popped low byte; non-AF POP doesn't touch f.
   - SP delta after both ops.
   - Construction reject for SP.
   - Mnemonic format.
