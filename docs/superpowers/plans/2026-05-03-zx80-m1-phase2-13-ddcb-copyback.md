# Phase 2.13 — DDCB/FDCB Undocumented Copy-back Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ZEXDOC run to completion by adding the 3 DDCB/FDCB undocumented copy-back Op classes (rotate/shift, RES, SET) plus SLL on indexed, then conditionally tag `m1-cpu-complete` if no CRC errors emerge.

**Architecture:** Mirror Phase 2.9 structure: each new Op class lives in `src/main/kotlin/ru/alepar/zx80/op/ixcb/` and has the same DDCB-prefixed shape (`pc += 4`, `bumpR(2)`, `baseCycles = 23`, signed displacement at pc+2). Each adds one extra step beyond its memory-only counterpart: also write the result to a register. Three new install methods extend `IxCbOps`. SLL on indexed is enabled by removing one `if (oooBits == 6) continue` line in the existing `installRotateShift`.

**Tech Stack:** Kotlin (JDK 21), JUnit 5, AssertJ, Gradle, Spotless ktlint.

**Spec:** `docs/superpowers/specs/2026-05-03-zx80-m1-phase2-13-ddcb-copyback-design.md`

---

## Universal patterns

These repeat across every WU; understand once.

**TDD cycle per Op class:**
1. Write the test file (FAIL because Op class doesn't exist)
2. Run `./gradlew test --tests <ClassName>Test` to confirm FAIL
3. Implement the Op class
4. Re-run to confirm PASS

**Standard DDCB-prefixed Op shape:**
- `pc += 4` (DD/FD prefix + CB prefix + displacement byte + opcode byte)
- `bumpR(2)` (DD/FD + CB count as two M1 cycles)
- `baseCycles = 23`, `operandLength = 0`
- Displacement byte at `pc+2`, opcode at `pc+3` (consumed by dispatcher)
- Effective address = `(idx.read(cpu) + d.toByte().toInt()) and 0xFFFF`

**Test name rules:**
- Backtick names CAN'T contain `..`, `->`, or `;` — substitute "to" / commas

**spotlessApply** reformats KDocs and `apply { … }` blocks — accept those changes.

**Beads workflow per WU:**
1. `bd update <id> --status=in_progress`
2. Execute WU
3. `./gradlew test spotlessApply` (verify suite passes)
4. Commit per the WU's commit message
5. `bd close <id>`

## File Structure

**New files:**
- `src/main/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdCopyback.kt` — WU 2.13-1
- `src/main/kotlin/ru/alepar/zx80/op/ixcb/ResIxdCopyback.kt` — WU 2.13-2
- `src/main/kotlin/ru/alepar/zx80/op/ixcb/SetIxdCopyback.kt` — WU 2.13-3
- Test files mirror each (in `src/test/kotlin/ru/alepar/zx80/op/ixcb/`)

**Existing files (modified):**
- `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt` — install methods extended (every WU)
- `src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt` — install assertions extended (every WU)

---

## WU 2.13-1 — RotShiftIxdCopyback + SLL on indexed (114 install positions)

Mirrors Phase 2.9's `RotShiftIxd` but with a register copy-back step. Also fixes the Phase 2.9 SLL skip.

### Task 1: RotShiftIxdCopyback class

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdCopyback.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdCopybackTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdCopybackTest.kt`:

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.rot.RotateOp

class RotShiftIxdCopybackTest {
    @Test
    fun `RLC (IX+1), B rotates memory and copies result to B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0
            }
        // displacement at pc+2 = 0x102
        val mem =
            Memory().apply {
                write(0x102, 0x01) // d = +1
                write(0x4001, 0x80) // value to rotate
            }
        RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.B).execute(cpu, mem)
        // 0x80 RLC -> 0x01, C=1
        assertThat(mem.read(0x4001)).isEqualTo(0x01)
        assertThat(cpu.b).isEqualTo(0x01) // copy-back
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `signed negative displacement wraps`() {
        val cpu =
            Cpu().apply {
                pc = 0x200
                ix = 0x10
            }
        // d = -16 (0xF0), addr = 0x10 + (-16) = 0x00
        val mem =
            Memory().apply {
                write(0x202, 0xF0)
                write(0x00, 0x55)
            }
        RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.A).execute(cpu, mem)
        // 0x55 RLC -> 0xAA
        assertThat(mem.read(0x00)).isEqualTo(0xAA)
        assertThat(cpu.a).isEqualTo(0xAA)
    }

    @Test
    fun `SLL (IY+0), C copies result to C and shifts left, sets bit 0`() {
        val cpu = Cpu().apply { iy = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0x55)
            }
        cpu.pc = 0x100
        RotShiftIxdCopyback(IndexReg.IY, RotateOp.SLL, Reg.C).execute(cpu, mem)
        // 0x55 SLL -> shift left to 0xAA, set bit 0 -> 0xAB
        assertThat(mem.read(0x4000)).isEqualTo(0xAB)
        assertThat(cpu.c).isEqualTo(0xAB)
    }

    @Test
    fun `mnemonic format is OP (idx+d), dst`() {
        assertThat(
            RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.B).mnemonic { 0 }
        ).isEqualTo("RLC (IX+d), B")
        assertThat(
            RotShiftIxdCopyback(IndexReg.IY, RotateOp.SRL, Reg.A).mnemonic { 0 }
        ).isEqualTo("SRL (IY+d), A")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests RotShiftIxdCopybackTest`
Expected: FAIL with `Unresolved reference: RotShiftIxdCopyback`.

- [ ] **Step 3: Implement RotShiftIxdCopyback**

`src/main/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdCopyback.kt`:

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher
import ru.alepar.zx80.op.rot.RotateOp

/**
 * `<rotate-shift-op> (IX+d), r` — undocumented. Rotate or shift the byte at idx+d, write back to
 * memory AND copy the result to register r. 23 T-states. R+=2. PC+=4. Same flag math as the
 * documented memory-only `RotShiftIxd` form (S/Z/PV from result, H=0, N=0, C from shifted-out
 * bit).
 *
 * Covers 112 of the 128 rotate/shift slots in DDCB+FDCB tables (8 ops × 7 register dsts × 2
 * prefixes). The remaining 16 slots (rrr=110) are the documented memory-only form handled by
 * RotShiftIxd.
 */
class RotShiftIxdCopyback(
    private val idx: IndexReg,
    private val op: RotateOp,
    private val dst: Reg,
) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val r = op.apply(mem.read(addr), cpu.f)
        mem.write(addr, r.value)
        dst.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "${op.mnemonic} (${idx.mnemonic}+d), ${dst.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests RotShiftIxdCopybackTest`
Expected: 5 tests pass.

### Task 2: Remove SLL skip + wire copyback into IxCbOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt`

- [ ] **Step 1: Write the failing IxCbOps tests**

Append to `IxCbOpsTest.kt`:

```kotlin
@Test
fun `installInto registers SLL (IX+d) at DDCB 0x36`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    assertThat((d.ddcb[0x36] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SLL (IX+d)")
    assertThat((d.fdcb[0x36] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SLL (IY+d)")
}

@Test
fun `installInto registers RotShiftIxdCopyback at non-rrr=6 slots`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    // DDCB 0x00 = RLC (IX+d), B
    assertThat((d.ddcb[0x00] as RotShiftIxdCopyback).mnemonic { 0 })
        .isEqualTo("RLC (IX+d), B")
    // DDCB 0x07 = RLC (IX+d), A
    assertThat((d.ddcb[0x07] as RotShiftIxdCopyback).mnemonic { 0 })
        .isEqualTo("RLC (IX+d), A")
    // DDCB 0x30 = SLL (IX+d), B (oooBits=6, rrr=0)
    assertThat((d.ddcb[0x30] as RotShiftIxdCopyback).mnemonic { 0 })
        .isEqualTo("SLL (IX+d), B")
    // FDCB 0x3F = SRL (IY+d), A (oooBits=7, rrr=7)
    assertThat((d.fdcb[0x3F] as RotShiftIxdCopyback).mnemonic { 0 })
        .isEqualTo("SRL (IY+d), A")
}

@Test
fun `installInto fills the entire rotate-shift block 0x00-0x3F under DDCB and FDCB`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    val ddCount = (0x00..0x3F).count { d.ddcb[it] != null }
    val fdCount = (0x00..0x3F).count { d.fdcb[it] != null }
    assertThat(ddCount).isEqualTo(64)
    assertThat(fdCount).isEqualTo(64)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: FAIL — DDCB 0x36 still null and copy-back slots still null.

- [ ] **Step 3: Update IxCbOps**

In `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt`:

1. Add import: `import ru.alepar.zx80.cpu.Reg`
2. In `installInto`, add `installRotateShiftCopyback(table, idx)` after the existing `installRotateShift` call:

```kotlin
fun installInto(d: Decoder) {
    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
        installRotateShift(table, idx)
        installRotateShiftCopyback(table, idx)   // NEW — Phase 2.13 WU 2.13-1
        installBit(table, idx)
        installRes(table, idx)
        installSet(table, idx)
    }
}
```

3. Modify `installRotateShift` to remove the SLL skip:

```kotlin
private fun installRotateShift(table: Array<Op?>, idx: IndexReg) {
    for (oooBits in 0..7) {
        // Phase 2.13: oooBits=6 (SLL) is now installed. Phase 2.12 added SLL to RotateOp; this
        // table previously left it null per the documented-Z80-only carve-out.
        val op = RotateOp.fromBits(oooBits)
        val opcode = (oooBits shl 3) or 0x06
        table[opcode] = RotShiftIxd(idx, op)
    }
}
```

4. Add the new install method:

```kotlin
private fun installRotateShiftCopyback(table: Array<Op?>, idx: IndexReg) {
    for (oooBits in 0..7) {
        val op = RotateOp.fromBits(oooBits)
        for (rrrBits in 0..7) {
            if (rrrBits == 6) continue // documented memory-only form, handled above
            val opcode = (oooBits shl 3) or rrrBits
            table[opcode] = RotShiftIxdCopyback(idx, op, Reg.fromBits(rrrBits))
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: all pass.

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdCopyback.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdCopybackTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt
git commit -m "feat(op/ixcb): RotShiftIxdCopyback + SLL on indexed (114 install positions)

RotShiftIxdCopyback covers the 112 undocumented DDCB/FDCB slots where
rotate/shift writes the result both to memory[IX+d] AND to a register.
Removes Phase 2.9's oooBits=6 skip in installRotateShift, enabling
the 2 SLL-on-indexed slots (DDCB 0x36, FDCB 0x36) using the existing
RotShiftIxd class.

Closes <BD-ID-WU-2.13-1> (WU 2.13-1).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

(Replace `<BD-ID-WU-2.13-1>` with the actual beads ID once the issue is filed under epic `zx80-bws`.)

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-2.13-1>`

---

## WU 2.13-2 — ResIxdCopyback (112 install positions)

Same pattern as Phase 2.9's `ResIxd` plus a register copy-back step.

### Task 1: ResIxdCopyback class

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/ResIxdCopyback.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/ResIxdCopybackTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ixcb/ResIxdCopybackTest.kt`:

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class ResIxdCopybackTest {
    @Test
    fun `RES 0, (IX+1), B clears bit 0 of memory and copies to B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0xFF
                f = 0x55 // verify flags untouched
            }
        val mem =
            Memory().apply {
                write(0x102, 0x01)
                write(0x4001, 0x0F)
            }
        ResIxdCopyback(IndexReg.IX, n = 0, dst = Reg.B).execute(cpu, mem)
        // 0x0F RES 0 -> 0x0E
        assertThat(mem.read(0x4001)).isEqualTo(0x0E)
        assertThat(cpu.b).isEqualTo(0x0E)
        assertThat(cpu.f).isEqualTo(0x55) // no flag changes
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `RES 7, (IY+0), A clears high bit of memory and copies to A`() {
        val cpu = Cpu().apply { iy = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0xFF)
            }
        cpu.pc = 0x100
        ResIxdCopyback(IndexReg.IY, n = 7, dst = Reg.A).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x7F)
        assertThat(cpu.a).isEqualTo(0x7F)
    }

    @Test
    fun `signed negative displacement wraps`() {
        val cpu =
            Cpu().apply {
                pc = 0x200
                ix = 0x10
            }
        val mem =
            Memory().apply {
                write(0x202, 0xF0) // d = -16
                write(0x00, 0xFF)
            }
        ResIxdCopyback(IndexReg.IX, n = 3, dst = Reg.C).execute(cpu, mem)
        // 0xFF RES 3 -> 0xF7
        assertThat(mem.read(0x00)).isEqualTo(0xF7)
        assertThat(cpu.c).isEqualTo(0xF7)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ResIxdCopyback(IndexReg.IX, 3, Reg.B).mnemonic { 0 })
            .isEqualTo("RES 3, (IX+d), B")
        assertThat(ResIxdCopyback(IndexReg.IY, 7, Reg.A).mnemonic { 0 })
            .isEqualTo("RES 7, (IY+d), A")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = ResIxdCopyback(IndexReg.IX, 0, Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests ResIxdCopybackTest`
Expected: FAIL.

- [ ] **Step 3: Implement ResIxdCopyback**

`src/main/kotlin/ru/alepar/zx80/op/ixcb/ResIxdCopyback.kt`:

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RES n, (IX+d), r` — undocumented. Clear bit n of memory[IX+d], write back to memory AND copy
 * the result to register r. 23 T-states. R+=2. PC+=4. No flag changes.
 *
 * Covers 112 of the 128 RES slots in DDCB+FDCB tables (8 bits × 7 register dsts × 2 prefixes). The
 * remaining 16 slots (rrr=110) are the documented memory-only form handled by ResIxd.
 */
class ResIxdCopyback(
    private val idx: IndexReg,
    private val n: Int,
    private val dst: Reg,
) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val result = mem.read(addr) and (0xFF xor (1 shl n))
        mem.write(addr, result)
        dst.write(cpu, result)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "RES $n, (${idx.mnemonic}+d), ${dst.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests ResIxdCopybackTest`
Expected: 5 tests pass.

### Task 2: Wire ResIxdCopyback into IxCbOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt`

- [ ] **Step 1: Write the failing IxCbOps test**

Append to `IxCbOpsTest.kt`:

```kotlin
@Test
fun `installInto registers ResIxdCopyback at non-rrr=6 slots`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    // DDCB 0x80 = RES 0, (IX+d), B
    assertThat((d.ddcb[0x80] as ResIxdCopyback).mnemonic { 0 })
        .isEqualTo("RES 0, (IX+d), B")
    // DDCB 0x87 = RES 0, (IX+d), A
    assertThat((d.ddcb[0x87] as ResIxdCopyback).mnemonic { 0 })
        .isEqualTo("RES 0, (IX+d), A")
    // DDCB 0xBF = RES 7, (IX+d), A
    assertThat((d.ddcb[0xBF] as ResIxdCopyback).mnemonic { 0 })
        .isEqualTo("RES 7, (IX+d), A")
    // FDCB 0xB0 = RES 6, (IY+d), B
    assertThat((d.fdcb[0xB0] as ResIxdCopyback).mnemonic { 0 })
        .isEqualTo("RES 6, (IY+d), B")
}

@Test
fun `installInto fills the entire RES block 0x80-0xBF under DDCB and FDCB`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    val ddCount = (0x80..0xBF).count { d.ddcb[it] != null }
    val fdCount = (0x80..0xBF).count { d.fdcb[it] != null }
    assertThat(ddCount).isEqualTo(64)
    assertThat(fdCount).isEqualTo(64)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: FAIL.

- [ ] **Step 3: Update IxCbOps.installInto**

In `IxCbOps.kt`:

```kotlin
fun installInto(d: Decoder) {
    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
        installRotateShift(table, idx)
        installRotateShiftCopyback(table, idx)
        installBit(table, idx)
        installRes(table, idx)
        installResCopyback(table, idx)        // NEW — Phase 2.13 WU 2.13-2
        installSet(table, idx)
    }
}

private fun installResCopyback(table: Array<Op?>, idx: IndexReg) {
    for (n in 0..7) {
        for (rrrBits in 0..7) {
            if (rrrBits == 6) continue
            val opcode = 0x80 or (n shl 3) or rrrBits
            table[opcode] = ResIxdCopyback(idx, n, Reg.fromBits(rrrBits))
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: all pass.

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/ResIxdCopyback.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/ResIxdCopybackTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt
git commit -m "feat(op/ixcb): ResIxdCopyback — 112 undocumented RES indexed copy-back ops

RES n, (IX+d), r — clear bit n of memory[IX+d], write back to memory
AND copy result to register r. Covers DDCB/FDCB rrr ∈ {0..5,7} slots in
the RES block (0x80-0xBF). The 16 documented rrr=6 slots stay handled
by ResIxd.

Closes <BD-ID-WU-2.13-2> (WU 2.13-2).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-2.13-2>`

---

## WU 2.13-3 — SetIxdCopyback (112 install positions)

Mirror of WU 2.13-2 but for SET — bit-set instead of bit-reset.

### Task 1: SetIxdCopyback class

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/SetIxdCopyback.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/SetIxdCopybackTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ixcb/SetIxdCopybackTest.kt`:

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class SetIxdCopybackTest {
    @Test
    fun `SET 0, (IX+1), B sets bit 0 of memory and copies to B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0
                f = 0x55
            }
        val mem =
            Memory().apply {
                write(0x102, 0x01)
                write(0x4001, 0x00)
            }
        SetIxdCopyback(IndexReg.IX, n = 0, dst = Reg.B).execute(cpu, mem)
        assertThat(mem.read(0x4001)).isEqualTo(0x01)
        assertThat(cpu.b).isEqualTo(0x01)
        assertThat(cpu.f).isEqualTo(0x55)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `SET 7, (IY+0), A sets high bit of memory and copies to A`() {
        val cpu = Cpu().apply { iy = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0x00)
            }
        cpu.pc = 0x100
        SetIxdCopyback(IndexReg.IY, n = 7, dst = Reg.A).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x80)
        assertThat(cpu.a).isEqualTo(0x80)
    }

    @Test
    fun `SET preserves already-set bits`() {
        val cpu = Cpu().apply { ix = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0x55)
            }
        cpu.pc = 0x100
        SetIxdCopyback(IndexReg.IX, n = 1, dst = Reg.C).execute(cpu, mem)
        // 0x55 = 01010101, set bit 1 -> 01010111 = 0x57
        assertThat(mem.read(0x4000)).isEqualTo(0x57)
        assertThat(cpu.c).isEqualTo(0x57)
    }

    @Test
    fun `mnemonic`() {
        assertThat(SetIxdCopyback(IndexReg.IX, 3, Reg.B).mnemonic { 0 })
            .isEqualTo("SET 3, (IX+d), B")
        assertThat(SetIxdCopyback(IndexReg.IY, 0, Reg.A).mnemonic { 0 })
            .isEqualTo("SET 0, (IY+d), A")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = SetIxdCopyback(IndexReg.IX, 0, Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests SetIxdCopybackTest`
Expected: FAIL.

- [ ] **Step 3: Implement SetIxdCopyback**

`src/main/kotlin/ru/alepar/zx80/op/ixcb/SetIxdCopyback.kt`:

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SET n, (IX+d), r` — undocumented. Set bit n of memory[IX+d], write back to memory AND copy the
 * result to register r. 23 T-states. R+=2. PC+=4. No flag changes.
 *
 * Covers 112 of the 128 SET slots in DDCB+FDCB tables (8 bits × 7 register dsts × 2 prefixes).
 * The remaining 16 slots (rrr=110) are the documented memory-only form handled by SetIxd.
 */
class SetIxdCopyback(
    private val idx: IndexReg,
    private val n: Int,
    private val dst: Reg,
) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val result = mem.read(addr) or (1 shl n)
        mem.write(addr, result)
        dst.write(cpu, result)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "SET $n, (${idx.mnemonic}+d), ${dst.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests SetIxdCopybackTest`
Expected: 5 tests pass.

### Task 2: Wire SetIxdCopyback into IxCbOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt`

- [ ] **Step 1: Write the failing IxCbOps test**

Append to `IxCbOpsTest.kt`:

```kotlin
@Test
fun `installInto registers SetIxdCopyback at non-rrr=6 slots`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    // DDCB 0xC0 = SET 0, (IX+d), B
    assertThat((d.ddcb[0xC0] as SetIxdCopyback).mnemonic { 0 })
        .isEqualTo("SET 0, (IX+d), B")
    // DDCB 0xC7 = SET 0, (IX+d), A
    assertThat((d.ddcb[0xC7] as SetIxdCopyback).mnemonic { 0 })
        .isEqualTo("SET 0, (IX+d), A")
    // DDCB 0xFF = SET 7, (IX+d), A
    assertThat((d.ddcb[0xFF] as SetIxdCopyback).mnemonic { 0 })
        .isEqualTo("SET 7, (IX+d), A")
    // FDCB 0xCF = SET 1, (IY+d), A
    assertThat((d.fdcb[0xCF] as SetIxdCopyback).mnemonic { 0 })
        .isEqualTo("SET 1, (IY+d), A")
}

@Test
fun `installInto fills the entire SET block 0xC0-0xFF under DDCB and FDCB`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    val ddCount = (0xC0..0xFF).count { d.ddcb[it] != null }
    val fdCount = (0xC0..0xFF).count { d.fdcb[it] != null }
    assertThat(ddCount).isEqualTo(64)
    assertThat(fdCount).isEqualTo(64)
}

@Test
fun `installInto fills all 256 slots in DDCB and FDCB tables`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    val ddCount = (0..255).count { d.ddcb[it] != null }
    val fdCount = (0..255).count { d.fdcb[it] != null }
    assertThat(ddCount).isEqualTo(256)
    assertThat(fdCount).isEqualTo(256)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: FAIL.

- [ ] **Step 3: Update IxCbOps.installInto**

In `IxCbOps.kt`:

```kotlin
fun installInto(d: Decoder) {
    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
        installRotateShift(table, idx)
        installRotateShiftCopyback(table, idx)
        installBit(table, idx)
        installRes(table, idx)
        installResCopyback(table, idx)
        installSet(table, idx)
        installSetCopyback(table, idx)        // NEW — Phase 2.13 WU 2.13-3
    }
}

private fun installSetCopyback(table: Array<Op?>, idx: IndexReg) {
    for (n in 0..7) {
        for (rrrBits in 0..7) {
            if (rrrBits == 6) continue
            val opcode = 0xC0 or (n shl 3) or rrrBits
            table[opcode] = SetIxdCopyback(idx, n, Reg.fromBits(rrrBits))
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: all pass — DDCB and FDCB tables now fully populated (256/256 each).

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/SetIxdCopyback.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/SetIxdCopybackTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt
git commit -m "feat(op/ixcb): SetIxdCopyback — 112 undocumented SET indexed copy-back ops

SET n, (IX+d), r — set bit n of memory[IX+d], write back to memory AND
copy result to register r. Covers DDCB/FDCB rrr ∈ {0..5,7} slots in
the SET block (0xC0-0xFF). DDCB and FDCB tables are now fully
populated (256 entries each).

Closes <BD-ID-WU-2.13-3> (WU 2.13-3).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-2.13-3>`

---

## WU 2.13-4 — Sweep + tag m1-phase02-13 + (conditional) m1-cpu-complete

### Task 1: Verification

- [ ] **Step 1: Run full suite**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Capture score**

Run: `./build/install/zx80/bin/zx80 score`
Expected: opcodes count climbs from 850 to ~1186 (+336). Composite score should rise from 0.479. Capture exact `SCORE:` line.

- [ ] **Step 3: Run ZEXDOC and analyze output**

Run: `timeout 600 ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc.log`

Expected first-pass behavior:
- Run completes (no `IllegalStateException`).
- `Tests complete` line appears.
- Either zero `ERROR ****` lines (the M1-clean case) OR some `ERROR ****` lines (CRC mismatches → flag-edge work needed).

Run sanity checks on the log:
```bash
grep -c "IllegalStateException" /tmp/zexdoc.log
grep -c "Tests complete" /tmp/zexdoc.log
grep -c "ERROR \*\*\*\*" /tmp/zexdoc.log
```

If `IllegalStateException` count > 0 (still crashing): STOP — a new dispatch gap surfaced. Read the stack trace, identify the missing slot, file as Phase 2.14 issue. Do NOT tag.

If `Tests complete` count is 0: STOP — ZEXDOC ran past max-cycles or hung. Investigate.

If both look healthy, proceed to Step 4.

- [ ] **Step 4: Tag m1-phase02-13**

```bash
git tag -a m1-phase02-13 -m "Phase 2.13: DDCB/FDCB undocumented copy-back forms

3 new Op classes (RotShiftIxdCopyback, ResIxdCopyback, SetIxdCopyback)
covering 336 install positions, plus 2 SLL-on-indexed positions via
removing Phase 2.9's oooBits=6 skip. DDCB and FDCB tables are now
fully populated (256 entries each). ZEXDOC runs to completion.

Final SCORE: <REPLACE-WITH-SCORE-LINE>
ZEXDOC error count: <REPLACE-WITH-COUNT>"
```

- [ ] **Step 5: Conditional m1-cpu-complete**

If `ERROR ****` count == 0 in `/tmp/zexdoc.log`:

```bash
git tag -a m1-cpu-complete -m "M1: Z80 CPU complete

ZEXDOC runs cleanly with zero CRC errors. Documented ISA + relevant
undocumented ops all implemented across phases 2.1b through 2.13.
Programs suite 5/5. Ready for M2 (Spectrum machine: ROM, ULA,
keyboard)."
```

If `ERROR ****` count > 0:

1. Capture the failing test names: `grep "ERROR" /tmp/zexdoc.log`
2. File a Phase D issue in beads:
   ```bash
   bd create --type=epic --priority=2 \
     --title="Phase D: model X/Y undocumented flag bits (5, 3) for ZEXDOC clean run" \
     --description="ZEXDOC after Phase 2.13 emits N CRC errors in groups: <list-from-log>. The likely cause is unmodelled X/Y bits (bits 5 and 3 of F register) — copies of the result for ALU ops, or memptr-derived for BIT and block ops. After Phase D lands, retry ZEXDOC and tag m1-cpu-complete."
   ```
3. Add dependency: `bd dep add zx80-jfb <PHASE-D-EPIC-ID>` (uses parent-child if applicable; `--type=parent-child` if needed)
4. Do NOT tag m1-cpu-complete.

### Task 2: Close beads + epic

- [ ] **Step 1: Close WU 2.13-4**

```bash
bd close <BD-ID-WU-2.13-4> --reason="ZEXDOC ran to completion; m1-phase02-13 tag applied."
```

- [ ] **Step 2: Close Phase 2.13 epic**

```bash
bd close zx80-bws --reason="All 4 WUs (2.13-1 through 2.13-4) closed; DDCB/FDCB tables fully populated; ZEXDOC runs to completion."
```

- [ ] **Step 3: If m1-cpu-complete tagged, close zx80-jfb and zx80-949**

```bash
bd close zx80-jfb --reason="ZEXDOC clean; m1-cpu-complete tagged."
bd close zx80-949 --reason="Phase 2.11 work delivered (programs 5/5 + ZEXDOC infrastructure + ZEXDOC clean run). M1 closed."
```

If `m1-cpu-complete` was NOT tagged, leave `zx80-jfb` and `zx80-949` open — they'll close when Phase D lands.

- [ ] **Step 4: Final status report**

Run: `bd stats; git tag --sort=-creatordate | head -10; ./build/install/zx80/bin/zx80 score`

Expected closed-out state:
- All Phase 2.13 WUs closed
- `m1-phase02-13` tag present
- Optionally `m1-cpu-complete` tag present
- Beads stats show ~85 closed issues, ~3 open (zx80-jfb, zx80-949 if Phase D needed; otherwise 0)

---

## Self-Review

**1. Spec coverage:**

| Spec section | Plan coverage |
|---|---|
| RotShiftIxdCopyback (112 ops) | WU 2.13-1 Task 1 |
| Removing oooBits=6 skip in installRotateShift (2 SLL slots) | WU 2.13-1 Task 2 Step 3 |
| ResIxdCopyback (112 ops) | WU 2.13-2 Task 1 |
| SetIxdCopyback (112 ops) | WU 2.13-3 Task 1 |
| IxCbOps install loop extensions | WU 2.13-1/2/3 Task 2 |
| BIT undocumented mirror (out of scope) | acknowledged in spec; not in plan |
| ZEXDOC validation gates (clean run, no IllegalStateException) | WU 2.13-4 Task 1 Steps 3-5 |
| `m1-phase02-13` tag | WU 2.13-4 Task 1 Step 4 |
| Conditional `m1-cpu-complete` tag | WU 2.13-4 Task 1 Step 5 |
| Phase D filing on CRC errors | WU 2.13-4 Task 1 Step 5 |
| Beads epic + cross-phase close | WU 2.13-4 Task 2 |

All spec sections are covered.

**2. Placeholder scan:** Plan uses `<BD-ID-WU-2.13-N>` and `<PHASE-D-EPIC-ID>` and `<REPLACE-WITH-SCORE-LINE>` and `<REPLACE-WITH-COUNT>` as deliberate substitutions — the executing agent fills these in from runtime context (the actual beads IDs after WUs are filed; the actual SCORE output; the error count). This is documented usage, not a placeholder gap.

**3. Type consistency:** All three new Op classes follow the same shape (`baseCycles=23`, `operandLength=0`, `pc += 4`, `bumpR(2)`). Constructor parameter naming is consistent (`idx`, `op`/`n`, `dst`). Mnemonics follow the same format pattern. `IxCbOps.installInto` calls all six install methods in the order documented in the spec.

**4. Test name lint:** No backtick test names contain `..`, `->`, or `;`. The unsafe combinations were avoided.
