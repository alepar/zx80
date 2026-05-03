# ZX Spectrum Emulator — Phase 2.7 Implementation Plan (CB-prefixed)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the entire CB-prefixed table — 248 documented opcodes (RLC/RRC/RL/RR/SLA/SRA/SRL on r/(HL), plus BIT/SET/RES n,r/(HL)). 8 Op classes, 6 work units. SLL (CB 0x30-0x37) stays unimplemented per the project's "documented Z80 only" non-goal.

**Architecture:** New `RotateOp` enum (7 values; fromBits rejects bits=6 SLL) parameterizes the 7 documented rotate/shift operations. Each Op class is maximally parameterized: `RotShiftReg(op, src)` covers 49 register-target opcodes; `RotShiftHl(op)` covers the 7 (HL) variants; `BitReg(n, src)`/`BitHl(n)`/`SetReg(n, dst)`/`SetHl(n)`/`ResReg(n, dst)`/`ResHl(n)` cover the 192 BIT/SET/RES opcodes. All registered via a single `CbOps.installInto(decoder)` fragment.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-7-cb-prefixed-design.md`
**Branch:** `opus-4.7`
**Base commit:** spec commit. Phase 2.6 must complete first.

---

## Universal patterns

Same as prior phases. New notes:

- **All CB ops are prefixed:** PC += 2; `cpu.bumpR(2)`. T-states vary per Op class.
- **CB rotate/shift ops compute S/Z/PV from result** — distinct from the rotate-A variants in Phase 2.6 which preserve them. Tests must assert this distinction explicitly.
- **BIT preserves C and ignores S/P/V** (those bits are undocumented per Z80 spec — we leave them at 0 in F).
- **SET/RES don't touch any flags.**
- **SLL slots (CB 0x30-0x37) intentionally null** — `CbOpsTest` asserts this.

---

## File Structure

**New files:**
```
src/main/kotlin/ru/alepar/zx80/op/rot/
  RotateOp.kt                  # WU 2.7-1
  RotShiftReg.kt               # WU 2.7-2
  RotShiftHl.kt                # WU 2.7-2
src/main/kotlin/ru/alepar/zx80/op/bit/
  BitReg.kt                    # WU 2.7-3
  BitHl.kt                     # WU 2.7-3
  ResReg.kt                    # WU 2.7-4
  ResHl.kt                     # WU 2.7-4
  SetReg.kt                    # WU 2.7-5
  SetHl.kt                     # WU 2.7-5
src/main/kotlin/ru/alepar/zx80/op/cb/
  CbOps.kt                     # WU 2.7-2 (skeleton + rotate/shift), extended in 3/4/5

src/test/kotlin/ru/alepar/zx80/op/rot/
  RotateOpTest.kt              # WU 2.7-1
  RotShiftRegTest.kt           # WU 2.7-2
  RotShiftHlTest.kt            # WU 2.7-2
src/test/kotlin/ru/alepar/zx80/op/bit/
  BitRegTest.kt                # WU 2.7-3
  BitHlTest.kt                 # WU 2.7-3
  ResRegTest.kt                # WU 2.7-4
  ResHlTest.kt                 # WU 2.7-4
  SetRegTest.kt                # WU 2.7-5
  SetHlTest.kt                 # WU 2.7-5
src/test/kotlin/ru/alepar/zx80/op/cb/
  CbOpsTest.kt                 # WU 2.7-2 (skeleton), extended in 3/4/5
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt          # WU 2.7-1 (add 7 helpers)
src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt      # WU 2.7-1 (add ~21 tests)
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt  # WU 2.7-2 (add CbOps.installInto)
```

---

## WU 2.7-1 — RotateOp enum + 7 Flags helpers

### Task 1: afterRlc

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Failing test**

Append to `FlagsTest.kt`:

```kotlin
@Test
fun `afterRlc 0x80 rotates to 0x01 with C set, Z clear`() {
    val r = Flags.afterRlc(0x80)
    assertThat(r.value).isEqualTo(0x01)
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.S).isZero
    assertThat(r.newF and Flags.H).isZero
    assertThat(r.newF and Flags.N).isZero
    assertThat(r.newF and Flags.PV).isNotZero    // 0x01 has 1 bit (odd) — wait, parity TRUE means even
}

@Test
fun `afterRlc 0x00 stays 0x00 with Z set, no C`() {
    val r = Flags.afterRlc(0x00)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.PV).isNotZero    // 0 bits = even parity = PV set
}

@Test
fun `afterRlc 0x55 rotates to 0xAA, S set`() {
    val r = Flags.afterRlc(0x55)
    assertThat(r.value).isEqualTo(0xAA)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.C).isZero        // bit 7 of 0x55 was 0
}
```

(The first test's PV expectation is wrong — `0x01` has 1 bit set which is odd parity → PV cleared. Let me fix: `assertThat(r.newF and Flags.PV).isZero`.)

Corrected:

```kotlin
@Test
fun `afterRlc 0x80 rotates to 0x01 with C set, Z clear`() {
    val r = Flags.afterRlc(0x80)
    assertThat(r.value).isEqualTo(0x01)
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.S).isZero
    assertThat(r.newF and Flags.H).isZero
    assertThat(r.newF and Flags.N).isZero
    assertThat(r.newF and Flags.PV).isZero       // 0x01 has 1 bit (odd parity)
}
```

- [ ] **Step 2: Verify fails.** `./gradlew test --tests FlagsTest`.

- [ ] **Step 3: Implement afterRlc**

Add to `Flags.kt`:

```kotlin
/**
 * RLC: rotate value left circular. Bit 7 → C and → bit 0.
 * Flag rules: S/Z from result; H=0; PV=parity; N=0; C from old bit 7.
 */
fun afterRlc(value: Int): AluResult {
    val v = value and 0xFF
    val newC = (v and 0x80) != 0
    val result = ((v shl 1) or (if (newC) 1 else 0)) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}

private fun computeRotateShiftFlags(result: Int, newC: Boolean): Int {
    var f = 0
    if (result == 0) f = f or Z
    if (result and 0x80 != 0) f = f or S
    if (parity(result)) f = f or PV
    if (newC) f = f or C
    return f
}
```

- [ ] **Step 4: Verify pass.** 3 tests.

### Task 2: afterRrc

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `afterRrc 0x01 rotates to 0x80 with C set, S set`() {
    val r = Flags.afterRrc(0x01)
    assertThat(r.value).isEqualTo(0x80)
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.Z).isZero
}

@Test
fun `afterRrc 0x00 stays 0x00 with Z set`() {
    val r = Flags.afterRrc(0x00)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterRrc 0x02 gives 0x01, no C`() {
    val r = Flags.afterRrc(0x02)
    assertThat(r.value).isEqualTo(0x01)
    assertThat(r.newF and Flags.C).isZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * RRC: rotate value right circular. Bit 0 → C and → bit 7.
 */
fun afterRrc(value: Int): AluResult {
    val v = value and 0xFF
    val newC = (v and 0x01) != 0
    val result = ((v ushr 1) or (if (newC) 0x80 else 0)) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: afterRl

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `afterRl 0x80 with C=1 gives 0x01 with C set (old C in bit 0, old bit 7 to C)`() {
    val r = Flags.afterRl(0x80, oldF = Flags.C)
    assertThat(r.value).isEqualTo(0x01)
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterRl 0x80 with C=0 gives 0x00 with Z and C set`() {
    val r = Flags.afterRl(0x80, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterRl 0x40 with C=0 gives 0x80, S set`() {
    val r = Flags.afterRl(0x40, oldF = 0)
    assertThat(r.value).isEqualTo(0x80)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.C).isZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * RL: rotate value left through carry. Old C bit becomes new bit 0;
 * old bit 7 becomes new C.
 */
fun afterRl(value: Int, oldF: Int): AluResult {
    val v = value and 0xFF
    val oldC = if (oldF and C != 0) 1 else 0
    val newC = (v and 0x80) != 0
    val result = ((v shl 1) or oldC) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 4: afterRr

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `afterRr 0x01 with C=1 gives 0x80 with C set`() {
    val r = Flags.afterRr(0x01, oldF = Flags.C)
    assertThat(r.value).isEqualTo(0x80)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterRr 0x01 with C=0 gives 0x00 with Z and C set`() {
    val r = Flags.afterRr(0x01, oldF = 0)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterRr 0x02 with C=0 gives 0x01, no C`() {
    val r = Flags.afterRr(0x02, oldF = 0)
    assertThat(r.value).isEqualTo(0x01)
    assertThat(r.newF and Flags.C).isZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * RR: rotate value right through carry. Old C bit becomes new bit 7;
 * old bit 0 becomes new C.
 */
fun afterRr(value: Int, oldF: Int): AluResult {
    val v = value and 0xFF
    val oldC = if (oldF and C != 0) 0x80 else 0
    val newC = (v and 0x01) != 0
    val result = ((v ushr 1) or oldC) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 5: afterSla

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `afterSla 0x80 gives 0x00 with Z and C set (logical left shift)`() {
    val r = Flags.afterSla(0x80)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterSla 0x40 gives 0x80, S set, no C`() {
    val r = Flags.afterSla(0x40)
    assertThat(r.value).isEqualTo(0x80)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterSla 0xFF gives 0xFE, S and C set`() {
    val r = Flags.afterSla(0xFF)
    assertThat(r.value).isEqualTo(0xFE)
    assertThat(r.newF and Flags.S).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * SLA: shift left arithmetic. Bit 0 always becomes 0; bit 7 → C.
 */
fun afterSla(value: Int): AluResult {
    val v = value and 0xFF
    val newC = (v and 0x80) != 0
    val result = (v shl 1) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 6: afterSra

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `afterSra 0x80 gives 0xC0 with sign bit preserved (arithmetic right shift)`() {
    val r = Flags.afterSra(0x80)
    assertThat(r.value).isEqualTo(0xC0)
    assertThat(r.newF and Flags.S).isNotZero       // bit 7 still set
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterSra 0x01 gives 0x00 with Z and C set`() {
    val r = Flags.afterSra(0x01)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterSra 0x42 gives 0x21, no C, no sign change`() {
    val r = Flags.afterSra(0x42)
    assertThat(r.value).isEqualTo(0x21)
    assertThat(r.newF and Flags.S).isZero
    assertThat(r.newF and Flags.C).isZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * SRA: shift right arithmetic. Bit 7 is preserved (sign-extending); bit 0 → C.
 */
fun afterSra(value: Int): AluResult {
    val v = value and 0xFF
    val newC = (v and 0x01) != 0
    val result = ((v ushr 1) or (v and 0x80)) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 7: afterSrl

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `afterSrl 0x80 gives 0x40 with no sign preservation (logical right shift)`() {
    val r = Flags.afterSrl(0x80)
    assertThat(r.value).isEqualTo(0x40)
    assertThat(r.newF and Flags.S).isZero          // bit 7 cleared (zero-fill)
    assertThat(r.newF and Flags.C).isZero
}

@Test
fun `afterSrl 0x01 gives 0x00 with Z and C set`() {
    val r = Flags.afterSrl(0x01)
    assertThat(r.value).isZero
    assertThat(r.newF and Flags.Z).isNotZero
    assertThat(r.newF and Flags.C).isNotZero
}

@Test
fun `afterSrl 0xFF gives 0x7F, no C`() {
    val r = Flags.afterSrl(0xFF)
    assertThat(r.value).isEqualTo(0x7F)
    assertThat(r.newF and Flags.S).isZero
    assertThat(r.newF and Flags.C).isNotZero
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
/**
 * SRL: shift right logical. Bit 7 always becomes 0; bit 0 → C.
 */
fun afterSrl(value: Int): AluResult {
    val v = value and 0xFF
    val newC = (v and 0x01) != 0
    val result = (v ushr 1) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 8: RotateOp enum

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/rot/RotateOp.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/rot/RotateOpTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Flags

class RotateOpTest {
    @Test
    fun `apply RLC delegates to Flags afterRlc`() {
        val r = RotateOp.RLC.apply(0x80, oldF = 0)
        assertThat(r.value).isEqualTo(0x01)
    }

    @Test
    fun `apply RR folds in oldF carry`() {
        val r = RotateOp.RR.apply(0x01, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x80)
    }

    @Test
    fun `apply SRA preserves sign`() {
        val r = RotateOp.SRA.apply(0x80, oldF = 0)
        assertThat(r.value).isEqualTo(0xC0)
    }

    @Test
    fun `apply SRL clears top bit`() {
        val r = RotateOp.SRL.apply(0x80, oldF = 0)
        assertThat(r.value).isEqualTo(0x40)
    }

    @Test
    fun `mnemonic matches op name`() {
        assertThat(RotateOp.RLC.mnemonic).isEqualTo("RLC")
        assertThat(RotateOp.SRL.mnemonic).isEqualTo("SRL")
    }

    @Test
    fun `fromBits maps 0=RLC 1=RRC 2=RL 3=RR 4=SLA 5=SRA 7=SRL`() {
        assertThat(RotateOp.fromBits(0)).isEqualTo(RotateOp.RLC)
        assertThat(RotateOp.fromBits(1)).isEqualTo(RotateOp.RRC)
        assertThat(RotateOp.fromBits(2)).isEqualTo(RotateOp.RL)
        assertThat(RotateOp.fromBits(3)).isEqualTo(RotateOp.RR)
        assertThat(RotateOp.fromBits(4)).isEqualTo(RotateOp.SLA)
        assertThat(RotateOp.fromBits(5)).isEqualTo(RotateOp.SRA)
        assertThat(RotateOp.fromBits(7)).isEqualTo(RotateOp.SRL)
    }

    @Test
    fun `fromBits rejects 6 (SLL undocumented)`() {
        assertThatThrownBy { RotateOp.fromBits(6) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("SLL")
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement RotateOp**

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.AluResult
import ru.alepar.zx80.cpu.Flags

/**
 * The 7 documented Z80 rotate/shift operations used by the CB-prefixed
 * table. SLL (shift-left-logical) at the bits=6 slot is undocumented and
 * not modeled.
 */
enum class RotateOp(val mnemonic: String) {
    RLC("RLC"),
    RRC("RRC"),
    RL("RL"),
    RR("RR"),
    SLA("SLA"),
    SRA("SRA"),
    SRL("SRL");

    fun apply(value: Int, oldF: Int): AluResult = when (this) {
        RLC -> Flags.afterRlc(value)
        RRC -> Flags.afterRrc(value)
        RL  -> Flags.afterRl(value, oldF)
        RR  -> Flags.afterRr(value, oldF)
        SLA -> Flags.afterSla(value)
        SRA -> Flags.afterSra(value)
        SRL -> Flags.afterSrl(value)
    }

    companion object {
        /**
         * Map CB opcode bits 5-3 (the 'ooo' field) to a RotateOp.
         * Encoding: 0=RLC, 1=RRC, 2=RL, 3=RR, 4=SLA, 5=SRA, 6=SLL (rejected), 7=SRL.
         */
        fun fromBits(bits: Int): RotateOp = when (bits and 0x07) {
            0 -> RLC; 1 -> RRC
            2 -> RL;  3 -> RR
            4 -> SLA; 5 -> SRA
            6 -> error("bits=6 is SLL (undocumented); not modeled")
            7 -> SRL
            else -> error("unreachable")
        }
    }
}
```

- [ ] **Step 4: Verify.** 7 tests.

### Task 9: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

`./gradlew test spotlessApply`. Expected: 21 (Flags helpers) + 7 (RotateOp) = 28 new tests.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/rot/RotateOp.kt \
        src/test/kotlin/ru/alepar/zx80/op/rot/RotateOpTest.kt
git commit -m "feat(cpu): RotateOp enum + 7 Flags helpers (Phase 2.7 foundation)

afterRlc/Rrc/Rl/Rr/Sla/Sra/Srl helpers each compute S/Z/PV/H/N/C
from result + the bit shifted into C. RotateOp enum dispatches via
apply(value, oldF). fromBits maps the CB ooo encoding (0=RLC, 1=RRC,
2=RL, 3=RR, 4=SLA, 5=SRA, 7=SRL) and rejects bits=6 (SLL undocumented).
Common compute path factored into private computeRotateShiftFlags."
```

---

## WU 2.7-2 — RotShiftReg + RotShiftHl + CbOps skeleton

### Task 1: RotShiftReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/rot/RotShiftReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftRegTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class RotShiftRegTest {
    @Test
    fun `RLC B updates B and F, advances pc by 2, r by 2, 8 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; b = 0x80; f = 0 }
        RotShiftReg(op = RotateOp.RLC, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x01)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x102)        // CB-prefixed
        assertThat(cpu.r).isEqualTo(2)              // R+=2
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `RR A folds in carry`() {
        val cpu = Cpu().apply { a = 0x01; f = Flags.C }
        RotShiftReg(op = RotateOp.RR, src = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x80)
    }

    @Test
    fun `SRA L preserves sign bit`() {
        val cpu = Cpu().apply { l = 0x80 }
        RotShiftReg(op = RotateOp.SRA, src = Reg.L).execute(cpu, Memory())
        assertThat(cpu.l).isEqualTo(0xC0)
    }

    @Test
    fun `SRL H clears top bit`() {
        val cpu = Cpu().apply { h = 0x80 }
        RotShiftReg(op = RotateOp.SRL, src = Reg.H).execute(cpu, Memory())
        assertThat(cpu.h).isEqualTo(0x40)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(RotShiftReg(op = RotateOp.RLC, src = Reg.B).mnemonic { 0 }).isEqualTo("RLC B")
        assertThat(RotShiftReg(op = RotateOp.SRL, src = Reg.A).mnemonic { 0 }).isEqualTo("SRL A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = RotShiftReg(op = RotateOp.RLC, src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<rotate-shift-op> r` — apply a CB-prefixed rotate/shift operation
 * to an 8-bit register. 8 T-states. R+=2 (CB-prefixed). PC+=2.
 *
 * Covers 49 opcodes in the CB 0x00-0x3F block where rrr (low 3 bits)
 * != 110. (HL) variants are handled by RotShiftHl.
 */
class RotShiftReg(private val op: RotateOp, private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(src.read(cpu), cpu.f)
        src.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} ${src.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 2: RotShiftHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/rot/RotShiftHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftHlTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RotShiftHlTest {
    @Test
    fun `RLC (HL) reads and writes byte at HL, 15 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; hl = 0x4000; f = 0 }
        val mem = Memory().apply { write(0x4000, 0x80) }
        RotShiftHl(op = RotateOp.RLC).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x01)
        assertThat(cpu.hl).isEqualTo(0x4000)        // HL unchanged
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(RotShiftHl(op = RotateOp.RLC).mnemonic { 0 }).isEqualTo("RLC (HL)")
        assertThat(RotShiftHl(op = RotateOp.SRL).mnemonic { 0 }).isEqualTo("SRL (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = RotShiftHl(op = RotateOp.RLC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<rotate-shift-op> (HL)` — apply a CB-prefixed rotate/shift to the
 * byte at memory[HL]. 15 T-states. R+=2. PC+=2.
 *
 * Covers 7 opcodes (one per RotateOp) at the rrr=110 slot of each
 * row in CB 0x00-0x3F.
 */
class RotShiftHl(private val op: RotateOp) : Op {
    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(mem.read(cpu.hl), cpu.f)
        mem.write(cpu.hl, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} (HL)"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: CbOps skeleton + register rotate/shift opcodes + wire into builder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.cb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.rot.RotShiftHl
import ru.alepar.zx80.op.rot.RotShiftReg

class CbOpsTest {
    @Test
    fun `installInto registers rotate-shift block in cb 0x00-0x3F (skipping SLL at 0x30-0x37)`() {
        val d = Decoder()
        CbOps.installInto(d)

        // RLC B at 0x00
        assertThat((d.cb[0x00] as RotShiftReg).mnemonic { 0 }).isEqualTo("RLC B")
        // RLC (HL) at 0x06
        assertThat((d.cb[0x06] as RotShiftHl).mnemonic { 0 }).isEqualTo("RLC (HL)")
        // RLC A at 0x07
        assertThat((d.cb[0x07] as RotShiftReg).mnemonic { 0 }).isEqualTo("RLC A")
        // RRC B at 0x08
        assertThat((d.cb[0x08] as RotShiftReg).mnemonic { 0 }).isEqualTo("RRC B")
        // SLA B at 0x20
        assertThat((d.cb[0x20] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLA B")
        // SRA L at 0x2D
        assertThat((d.cb[0x2D] as RotShiftReg).mnemonic { 0 }).isEqualTo("SRA L")
        // SRL B at 0x38
        assertThat((d.cb[0x38] as RotShiftReg).mnemonic { 0 }).isEqualTo("SRL B")
        // SRL (HL) at 0x3E
        assertThat((d.cb[0x3E] as RotShiftHl).mnemonic { 0 }).isEqualTo("SRL (HL)")
    }

    @Test
    fun `installInto leaves SLL slots (CB 0x30-0x37) null`() {
        val d = Decoder()
        CbOps.installInto(d)
        for (slot in 0x30..0x37) {
            assertThat(d.cb[slot]).`as`("SLL slot 0x%02X must be null", slot).isNull()
        }
    }

    @Test
    fun `installInto registers exactly 56 documented rotate-shift opcodes in CB 0x00-0x3F`() {
        val d = Decoder()
        CbOps.installInto(d)
        val count = (0x00..0x3F).count { d.cb[it] != null }
        assertThat(count).isEqualTo(56)        // 64 slots - 8 SLL = 56
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement skeleton + rotate/shift installer.**

```kotlin
package ru.alepar.zx80.op.cb

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.rot.RotShiftHl
import ru.alepar.zx80.op.rot.RotShiftReg
import ru.alepar.zx80.op.rot.RotateOp

/**
 * Registers the entire CB-prefixed table: rotate/shift ops in
 * CB 0x00-0x3F (minus 8 SLL slots at 0x30-0x37 which are undocumented),
 * BIT n,r in 0x40-0x7F, RES n,r in 0x80-0xBF, SET n,r in 0xC0-0xFF.
 *
 * BIT/RES/SET installers are added in WUs 2.7-3, 2.7-4, 2.7-5.
 */
object CbOps {
    fun installInto(d: Decoder) {
        installRotateShift(d)
    }

    private fun installRotateShift(d: Decoder) {
        for (oooBits in 0..7) {
            if (oooBits == 6) continue   // SLL — undocumented
            val op = RotateOp.fromBits(oooBits)
            for (rrrBits in 0..7) {
                val opcode = (oooBits shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) RotShiftHl(op) else RotShiftReg(op, Reg.fromBits(rrrBits))
            }
        }
    }
}
```

- [ ] **Step 4: Wire into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:
- Add: `import ru.alepar.zx80.op.cb.CbOps`
- In `build()`, add `CbOps.installInto(d)` after the existing installers.

- [ ] **Step 5: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/rot/RotShiftReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/rot/RotShiftHl.kt \
        src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftHlTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(op): CB-prefixed rotate/shift block (56 opcodes)

RotShiftReg(op, src) parameterized over (RotateOp, Reg) covers 49
register opcodes. RotShiftHl(op) covers 7 (HL) variants (15T vs 8T).
SLL slots (CB 0x30-0x37) deliberately left null per non-goal.
New CbOps fragment wired into OpTableBuilder."
```

---

## WU 2.7-3 — BIT n,r/(HL) (64 opcodes)

### Task 1: BitReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/bit/BitRegTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class BitRegTest {
    @Test
    fun `BIT 7, B sets Z when bit 7 of B is clear`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; b = 0x7F; f = 0 }
        BitReg(n = 7, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.Z).isNotZero       // bit 7 is 0 → Z set
        assertThat(cpu.f and Flags.H).isNotZero       // BIT always sets H
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.b).isEqualTo(0x7F)              // B unchanged
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `BIT 7, B clears Z when bit 7 of B is set`() {
        val cpu = Cpu().apply { b = 0x80; f = Flags.Z }
        BitReg(n = 7, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `BIT 0, A on 0x01`() {
        val cpu = Cpu().apply { a = 0x01 }
        BitReg(n = 0, src = Reg.A).execute(cpu, Memory())
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `BIT preserves C flag`() {
        val cpu = Cpu().apply { b = 0x80; f = Flags.C }
        BitReg(n = 7, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero        // preserved
    }

    @Test
    fun `BitReg rejects n outside 0..7`() {
        assertThatThrownBy { BitReg(n = 8, src = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { BitReg(n = -1, src = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(BitReg(n = 0, src = Reg.B).mnemonic { 0 }).isEqualTo("BIT 0, B")
        assertThat(BitReg(n = 7, src = Reg.A).mnemonic { 0 }).isEqualTo("BIT 7, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = BitReg(n = 0, src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `BIT n, r` — test bit n of register r. 8 T-states. R+=2. PC+=2.
 *
 * Flag rules:
 * - Z = !(r and (1 shl n))   (i.e. set if the tested bit is 0).
 * - H = 1.
 * - N = 0.
 * - C = preserved from oldF.
 * - S, P/V = undocumented per Z80 spec — left at 0.
 *
 * The register is NOT modified.
 */
class BitReg(private val n: Int, private val src: Reg) : Op {
    init { require(n in 0..7) { "BIT bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = (src.read(cpu) shr n) and 1
        var f = cpu.f and Flags.C   // preserve C
        f = f or Flags.H
        if (bit == 0) f = f or Flags.Z
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "BIT $n, ${src.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 7 tests.

### Task 2: BitHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/bit/BitHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/bit/BitHlTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class BitHlTest {
    @Test
    fun `BIT 7, (HL) reads byte at HL and tests bit 7, 12 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; hl = 0x4000; f = 0 }
        val mem = Memory().apply { write(0x4000, 0x80) }
        BitHl(n = 7).execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isZero            // bit 7 set → Z clear
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(mem.read(0x4000)).isEqualTo(0x80)    // memory unchanged
        assertThat(cpu.hl).isEqualTo(0x4000)             // HL unchanged
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(12L)           // 12T not 15T (BIT only reads)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(BitHl(n = 0).mnemonic { 0 }).isEqualTo("BIT 0, (HL)")
        assertThat(BitHl(n = 7).mnemonic { 0 }).isEqualTo("BIT 7, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=12`() {
        val op = BitHl(n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(12)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `BIT n, (HL)` — test bit n of memory[HL]. 12 T-states (only reads,
 * doesn't write — distinct from SET/RES (HL) which are 15T). R+=2.
 * PC+=2. Memory unchanged. Flag rules same as BitReg.
 */
class BitHl(private val n: Int) : Op {
    init { require(n in 0..7) { "BIT bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = (mem.read(cpu.hl) shr n) and 1
        var f = cpu.f and Flags.C
        f = f or Flags.H
        if (bit == 0) f = f or Flags.Z
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "BIT $n, (HL)"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: Extend CbOps + CbOpsTest with BIT installer

- [ ] **Step 1: Add installBit to CbOps**

```kotlin
object CbOps {
    fun installInto(d: Decoder) {
        installRotateShift(d)
        installBit(d)
    }

    // ... existing installRotateShift ...

    private fun installBit(d: Decoder) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                val opcode = 0x40 or (n shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) BitHl(n) else BitReg(n, Reg.fromBits(rrrBits))
            }
        }
    }
}
```

(Add imports: `import ru.alepar.zx80.op.bit.BitReg`, `BitHl`.)

- [ ] **Step 2: Add CbOpsTest assertions**

```kotlin
@Test
fun `installInto registers BIT n,r at CB 0x40-0x7F (64 opcodes)`() {
    val d = Decoder()
    CbOps.installInto(d)
    // BIT 0,B at 0x40
    assertThat((d.cb[0x40] as BitReg).mnemonic { 0 }).isEqualTo("BIT 0, B")
    // BIT 0,(HL) at 0x46
    assertThat((d.cb[0x46] as BitHl).mnemonic { 0 }).isEqualTo("BIT 0, (HL)")
    // BIT 0,A at 0x47
    assertThat((d.cb[0x47] as BitReg).mnemonic { 0 }).isEqualTo("BIT 0, A")
    // BIT 7,B at 0x78
    assertThat((d.cb[0x78] as BitReg).mnemonic { 0 }).isEqualTo("BIT 7, B")
    // BIT 7,(HL) at 0x7E
    assertThat((d.cb[0x7E] as BitHl).mnemonic { 0 }).isEqualTo("BIT 7, (HL)")
    // BIT 7,A at 0x7F
    assertThat((d.cb[0x7F] as BitReg).mnemonic { 0 }).isEqualTo("BIT 7, A")

    val count = (0x40..0x7F).count { d.cb[it] != null }
    assertThat(count).isEqualTo(64)
}
```

(Add imports: `BitReg`, `BitHl`.)

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/bit/BitHl.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/BitRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/BitHlTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt
git commit -m "feat(op/bit): BIT n,r and BIT n,(HL) — 64 CB opcodes

BIT n,r is 8T; BIT n,(HL) is 12T (BIT only reads, doesn't write —
distinct from SET/RES (HL) which are 15T). Z set if tested bit is 0;
H always 1; N=0; C preserved; S/PV left at 0 (undocumented bits).
Register/memory not modified."
```

---

## WU 2.7-4 — RES n,r/(HL) (64 opcodes)

### Task 1: ResReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/bit/ResReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/bit/ResRegTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class ResRegTest {
    @Test
    fun `RES 7, B clears bit 7 of B, advances pc by 2, r by 2, 8 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; b = 0xFF; f = 0xFF }
        ResReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x7F)
        assertThat(cpu.f).isEqualTo(0xFF)              // RES doesn't touch flags
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `RES 0, A clears bit 0 of A`() {
        val cpu = Cpu().apply { a = 0xFF }
        ResReg(n = 0, dst = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xFE)
    }

    @Test
    fun `RES 3, L on 0xFF gives 0xF7`() {
        val cpu = Cpu().apply { l = 0xFF }
        ResReg(n = 3, dst = Reg.L).execute(cpu, Memory())
        assertThat(cpu.l).isEqualTo(0xF7)
    }

    @Test
    fun `RES preserves the register value when bit was already clear`() {
        val cpu = Cpu().apply { b = 0x7F }
        ResReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x7F)
    }

    @Test
    fun `ResReg rejects n outside 0..7`() {
        assertThatThrownBy { ResReg(n = 8, dst = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(ResReg(n = 0, dst = Reg.B).mnemonic { 0 }).isEqualTo("RES 0, B")
        assertThat(ResReg(n = 7, dst = Reg.A).mnemonic { 0 }).isEqualTo("RES 7, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = ResReg(n = 0, dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RES n, r` — clear bit n of register r. 8 T-states. R+=2. PC+=2.
 * No flags affected.
 */
class ResReg(private val n: Int, private val dst: Reg) : Op {
    init { require(n in 0..7) { "RES bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val mask = (1 shl n).inv() and 0xFF
        dst.write(cpu, dst.read(cpu) and mask)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RES $n, ${dst.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 7 tests.

### Task 2: ResHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/bit/ResHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/bit/ResHlTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ResHlTest {
    @Test
    fun `RES 7, (HL) reads, clears bit 7, writes back, 15 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; hl = 0x4000; f = 0xFF }
        val mem = Memory().apply { write(0x4000, 0xFF) }
        ResHl(n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x7F)
        assertThat(cpu.f).isEqualTo(0xFF)              // unchanged
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(ResHl(n = 0).mnemonic { 0 }).isEqualTo("RES 0, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = ResHl(n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RES n, (HL)` — clear bit n of memory[HL]. 15 T-states (read +
 * modify + write). R+=2. PC+=2. No flags affected.
 */
class ResHl(private val n: Int) : Op {
    init { require(n in 0..7) { "RES bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val mask = (1 shl n).inv() and 0xFF
        mem.write(cpu.hl, mem.read(cpu.hl) and mask)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RES $n, (HL)"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: Extend CbOps + CbOpsTest with RES installer

- [ ] **Step 1: Add installRes to CbOps**

```kotlin
object CbOps {
    fun installInto(d: Decoder) {
        installRotateShift(d)
        installBit(d)
        installRes(d)
    }

    // ... existing installers ...

    private fun installRes(d: Decoder) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                val opcode = 0x80 or (n shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) ResHl(n) else ResReg(n, Reg.fromBits(rrrBits))
            }
        }
    }
}
```

- [ ] **Step 2: Add CbOpsTest assertions**

```kotlin
@Test
fun `installInto registers RES n,r at CB 0x80-0xBF (64 opcodes)`() {
    val d = Decoder()
    CbOps.installInto(d)
    assertThat((d.cb[0x80] as ResReg).mnemonic { 0 }).isEqualTo("RES 0, B")
    assertThat((d.cb[0x86] as ResHl).mnemonic { 0 }).isEqualTo("RES 0, (HL)")
    assertThat((d.cb[0x87] as ResReg).mnemonic { 0 }).isEqualTo("RES 0, A")
    assertThat((d.cb[0xB8] as ResReg).mnemonic { 0 }).isEqualTo("RES 7, B")
    assertThat((d.cb[0xBE] as ResHl).mnemonic { 0 }).isEqualTo("RES 7, (HL)")
    assertThat((d.cb[0xBF] as ResReg).mnemonic { 0 }).isEqualTo("RES 7, A")

    val count = (0x80..0xBF).count { d.cb[it] != null }
    assertThat(count).isEqualTo(64)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/bit/ResReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/bit/ResHl.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/ResRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/ResHlTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt
git commit -m "feat(op/bit): RES n,r and RES n,(HL) — 64 CB opcodes

RES n,r is 8T; RES n,(HL) is 15T. Clear bit n; no flags affected."
```

---

## WU 2.7-5 — SET n,r/(HL) (64 opcodes)

### Task 1: SetReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/bit/SetReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/bit/SetRegTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class SetRegTest {
    @Test
    fun `SET 7, B sets bit 7 of B, advances pc by 2, r by 2, 8 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; b = 0x00; f = 0xFF }
        SetReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x80)
        assertThat(cpu.f).isEqualTo(0xFF)              // SET doesn't touch flags
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `SET 0, A on 0xFE gives 0xFF`() {
        val cpu = Cpu().apply { a = 0xFE }
        SetReg(n = 0, dst = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xFF)
    }

    @Test
    fun `SET preserves register value when bit was already set`() {
        val cpu = Cpu().apply { b = 0x80 }
        SetReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x80)
    }

    @Test
    fun `SetReg rejects n outside 0..7`() {
        assertThatThrownBy { SetReg(n = 8, dst = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SetReg(n = 0, dst = Reg.B).mnemonic { 0 }).isEqualTo("SET 0, B")
        assertThat(SetReg(n = 7, dst = Reg.A).mnemonic { 0 }).isEqualTo("SET 7, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = SetReg(n = 0, dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SET n, r` — set bit n of register r. 8 T-states. R+=2. PC+=2.
 * No flags affected.
 */
class SetReg(private val n: Int, private val dst: Reg) : Op {
    init { require(n in 0..7) { "SET bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = 1 shl n
        dst.write(cpu, dst.read(cpu) or bit)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SET $n, ${dst.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 2: SetHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/bit/SetHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/bit/SetHlTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class SetHlTest {
    @Test
    fun `SET 7, (HL) reads, sets bit 7, writes back, 15 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; hl = 0x4000; f = 0xFF }
        val mem = Memory().apply { write(0x4000, 0x00) }
        SetHl(n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x80)
        assertThat(cpu.f).isEqualTo(0xFF)              // unchanged
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SetHl(n = 3).mnemonic { 0 }).isEqualTo("SET 3, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = SetHl(n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SET n, (HL)` — set bit n of memory[HL]. 15 T-states. R+=2. PC+=2.
 * No flags affected.
 */
class SetHl(private val n: Int) : Op {
    init { require(n in 0..7) { "SET bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = 1 shl n
        mem.write(cpu.hl, mem.read(cpu.hl) or bit)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SET $n, (HL)"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: Extend CbOps + CbOpsTest with SET installer

- [ ] **Step 1: Add installSet to CbOps**

```kotlin
object CbOps {
    fun installInto(d: Decoder) {
        installRotateShift(d)
        installBit(d)
        installRes(d)
        installSet(d)
    }

    // ... existing installers ...

    private fun installSet(d: Decoder) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                val opcode = 0xC0 or (n shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) SetHl(n) else SetReg(n, Reg.fromBits(rrrBits))
            }
        }
    }
}
```

- [ ] **Step 2: Add CbOpsTest assertions**

```kotlin
@Test
fun `installInto registers SET n,r at CB 0xC0-0xFF (64 opcodes)`() {
    val d = Decoder()
    CbOps.installInto(d)
    assertThat((d.cb[0xC0] as SetReg).mnemonic { 0 }).isEqualTo("SET 0, B")
    assertThat((d.cb[0xC6] as SetHl).mnemonic { 0 }).isEqualTo("SET 0, (HL)")
    assertThat((d.cb[0xC7] as SetReg).mnemonic { 0 }).isEqualTo("SET 0, A")
    assertThat((d.cb[0xF8] as SetReg).mnemonic { 0 }).isEqualTo("SET 7, B")
    assertThat((d.cb[0xFE] as SetHl).mnemonic { 0 }).isEqualTo("SET 7, (HL)")
    assertThat((d.cb[0xFF] as SetReg).mnemonic { 0 }).isEqualTo("SET 7, A")

    val count = (0xC0..0xFF).count { d.cb[it] != null }
    assertThat(count).isEqualTo(64)
}

@Test
fun `installInto fills 248 documented CB opcodes (256 minus 8 SLL slots)`() {
    val d = Decoder()
    CbOps.installInto(d)
    val total = d.cb.count { it != null }
    assertThat(total).isEqualTo(248)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/bit/SetReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/bit/SetHl.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/SetRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/SetHlTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt
git commit -m "feat(op/bit): SET n,r and SET n,(HL) — 64 CB opcodes

SET n,r is 8T; SET n,(HL) is 15T. Set bit n; no flags affected.
CB table now has 248 documented opcodes installed; 8 SLL slots null."
```

---

## WU 2.7-6 — Sweep + tag

### Task 1: Verification + tag

- [ ] **Step 1: Clean build**

`./gradlew clean check installDist` — BUILD SUCCESSFUL.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count climbs by 248. Composite score climbs noticeably (CB is a substantial chunk of FUSE).

- [ ] **Step 3: Spotless check**

`./gradlew spotlessCheck` — green.

- [ ] **Step 4: Sanity-check FUSE cb 00 (RLC B), cb 40 (BIT 0,B), cb 80 (RES 0,B), cb c0 (SET 0,B) all pass**

```bash
python3 -c "
import json
d = json.load(open('build/score.json'))
failures = d['suites']['fuse']['details'].get('failures', [])
for op in ['cb 00', 'cb 40', 'cb 80', 'cb c0']:
    has = any(f.startswith(f'{op}:') for f in failures)
    print(f'  case {op}: {\"FAIL\" if has else \"PASS\"}')"
```

(Note: FUSE case names for CB-prefixed opcodes typically use the format `cb XX` with a space. Adjust if FUSE format differs.)

Expected: all 4 PASS. The 8 SLL cases (`cb 30` through `cb 37`) will FAIL because we don't model them — that's by design.

- [ ] **Step 5: Tag**

```bash
git tag -a m1-phase02-7 -m "M1 Phase 2.7 complete: CB-prefixed table

248 documented opcodes across 8 Op classes:
- RotShiftReg + RotShiftHl cover 56 rotate/shift ops (RLC/RRC/RL/RR/SLA/SRA/SRL × r/(HL))
- BitReg + BitHl cover 64 BIT n,r/(HL)
- ResReg + ResHl cover 64 RES n,r/(HL)
- SetReg + SetHl cover 64 SET n,r/(HL)

Foundation: RotateOp enum + 7 Flags helpers (afterRlc/Rrc/Rl/Rr/Sla/
Sra/Srl). SLL slots (CB 0x30-0x37) deliberately null per the
documented-Z80-only non-goal.

Plan 2.8 (DD/FD-prefixed IX/IY) is the next batch."
```

This plan is complete.

---

## Self-Review

1. **Spec coverage:** Foundation (RotateOp + 7 Flags helpers) → 2.7-1. Rotate/shift Op classes → 2.7-2. BIT → 2.7-3. RES → 2.7-4. SET → 2.7-5. Sweep + tag → 2.7-6.

2. **Placeholder scan:** No "TBD" / "TODO". Each Op class has its full code shown.

3. **Type consistency:** All Op classes follow the same pattern: PC+=2, `cpu.bumpR(2)`, T-states. RotShiftReg/Hl delegate to `RotateOp.apply`. BIT/RES/SET each parameterized over (n, dst).

4. **Critical assertions per Op:**
   - All CB rotate/shift Ops have explicit "Z is set when result is 0" tests.
   - SRA tests with 0x80 → 0xC0 (sign preserved); SRL tests with 0x80 → 0x40 (zero-fill).
   - RL/RR tests fold in C=1 oldF.
   - BIT preserves C; sets H; sets Z when bit is 0.
   - SET/RES don't touch flags (test sets f=0xFF before, asserts f=0xFF after).
   - Construction reject for n outside 0..7 in BIT/SET/RES.
   - CbOpsTest explicitly asserts SLL slots (0x30-0x37) remain null.
   - CbOpsTest counts total = 248.

5. **Cycle count differences:** All register variants 8T. (HL) variants: BIT=12T, rotate/shift=15T, SET/RES=15T. Tests assert each.

6. **R+=2 for all CB ops** (CB-prefixed). Tests assert.
