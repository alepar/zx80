# ZX Spectrum Emulator — Phase 2.9 Implementation Plan (DDCB/FDCB indexed bit/rotate)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 62 documented DDCB/FDCB-prefixed opcodes (indexed (IX+d)/(IY+d) variants of rotate/shift/BIT/SET/RES). 4 Op classes, 5 work units.

**Architecture:** All Op classes parameterized over `IndexReg`. Each reads the displacement byte from `pc+2` (BEFORE the opcode at pc+3 — Dispatcher already handles this routing). Effective address computed as `(idx.read(cpu) + signedD) and 0xFFFF`. R += 2 (DD + CB are the 2 M1 cycles). PC += 4. Undocumented "copy to r" variants (rrr ≠ 110) stay null.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-9-ddcb-fdcb-design.md`
**Branch:** `opus-4.7`
**Base commit:** spec commit. Phase 2.8 must complete first.

---

## Universal patterns

- All DDCB/FDCB ops: `cpu.bumpR(2)`, PC += 4 (prefix DD/FD + CB + d + opcode).
- Displacement at `pc+2`: `mem.read(cpu.pc + 2).toByte().toInt()` (signed).
- Effective address: `(idx.read(cpu) + d) and 0xFFFF` (wraps mod 64K).
- `operandLength = 0` (the d byte is BEFORE the opcode at pc+3, not after — informational metadata only).
- BIT/RES/SET classes validate `n in 0..7` in init.

---

## File Structure

**New files:**
```
src/main/kotlin/ru/alepar/zx80/op/ixcb/
  IxCbOps.kt                   # WU 2.9-1 (skeleton), extended in 2-4
  RotShiftIxd.kt               # WU 2.9-2
  BitIxd.kt                    # WU 2.9-3
  ResIxd.kt                    # WU 2.9-4
  SetIxd.kt                    # WU 2.9-4

src/test/kotlin/ru/alepar/zx80/op/ixcb/
  IxCbOpsTest.kt               # WU 2.9-1 (skeleton), extended in 2-4
  RotShiftIxdTest.kt           # WU 2.9-2
  BitIxdTest.kt                # WU 2.9-3
  ResIxdTest.kt                # WU 2.9-4
  SetIxdTest.kt                # WU 2.9-4
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt  # WU 2.9-1 (add IxCbOps.installInto)
```

---

## WU 2.9-1 — IxCbOps skeleton + wire into builder

### Task 1: IxCbOps skeleton

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class IxCbOpsTest {
    @Test
    fun `installInto on empty Decoder leaves ddcb and fdcb tables empty (skeleton)`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddcbCount = d.ddcb.count { it != null }
        val fdcbCount = d.fdcb.count { it != null }
        assertThat(ddcbCount).isZero
        assertThat(fdcbCount).isZero
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement skeleton.**

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the documented DDCB/FDCB-prefixed Op family into
 * decoder.ddcb and decoder.fdcb. Filled in by WUs 2.9-2 through 2.9-4.
 *
 * Documented opcodes only at rrr=110 slots (where target is (IX+d) /
 * (IY+d)). The other ~225 slots per table are undocumented "copy to
 * r" variants and stay null per the project's documented-Z80-only
 * non-goal.
 */
object IxCbOps {
    fun installInto(d: Decoder) {
        // Filled in by WUs 2.9-2 through 2.9-4.
    }
}
```

- [ ] **Step 4: Wire into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:
- Add: `import ru.alepar.zx80.op.ixcb.IxCbOps`
- In `build()`, add `IxCbOps.installInto(d)` after the existing installers.

- [ ] **Step 5: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/ \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/ \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(op): IxCbOps fragment skeleton (Phase 2.9 foundation)

Empty IxCbOps wired into OpTableBuilder. Will be filled by WUs 2.9-2
through 2.9-4 with documented DDCB/FDCB-prefixed indexed bit/rotate
ops at rrr=110 slots. Undocumented copy-to-r variants stay null."
```

---

## WU 2.9-2 — RotShiftIxd (14 opcodes)

### Task 1: RotShiftIxd

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxd.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.rot.RotateOp

class RotShiftIxdTest {
    @Test
    fun `RLC (IX+5) rotates byte at IX+5, advances pc by 4, r by 2, 23 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; ix = 0x4000; f = 0 }
        val mem = Memory().apply {
            write(0x100, 0xDD); write(0x101, 0xCB)
            write(0x102, 0x05)                          // d = +5
            write(0x103, 0x06)                          // RLC (IX+d) opcode
            write(0x4005, 0x80)                          // byte to rotate
        }
        RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RLC).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x01)    // 0x80 RLC → 0x01
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `SRL (IY-2) handles negative displacement`() {
        val cpu = Cpu().apply { pc = 0x100; iy = 0x4000; f = 0 }
        val mem = Memory().apply {
            write(0x102, 0xFE)                          // d = -2 (signed)
            write(0x3FFE, 0x80)
        }
        RotShiftIxd(idx = IndexReg.IY, op = RotateOp.SRL).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x40)    // 0x80 SRL → 0x40
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `RR (IX+0) folds in carry`() {
        val cpu = Cpu().apply { pc = 0x100; ix = 0x4000; f = Flags.C }
        val mem = Memory().apply {
            write(0x102, 0x00)                          // d = 0
            write(0x4000, 0x01)
        }
        RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RR).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x80)    // 0x01 RR with C=1 → 0x80
        assertThat(cpu.f and Flags.C).isNotZero          // bit 0 was 1 → new C
    }

    @Test
    fun `mnemonic format`() {
        assertThat(RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RLC).mnemonic { 0 })
            .isEqualTo("RLC (IX+d)")
        assertThat(RotShiftIxd(idx = IndexReg.IY, op = RotateOp.SRL).mnemonic { 0 })
            .isEqualTo("SRL (IY+d)")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RLC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher
import ru.alepar.zx80.op.rot.RotateOp

/**
 * `<rotate-shift-op> (IX+d)` / `<rotate-shift-op> (IY+d)` — apply a
 * CB-style rotate/shift to the byte at idx+d. 23 T-states. R+=2. PC+=4.
 *
 * Displacement is signed 8-bit at pc+2; opcode at pc+3. Effective
 * address wraps mod 64K. Reuses RotateOp.apply for the per-op bit
 * manipulation + flag computation (S/Z/PV from result, H=0, N=0,
 * C from shifted-out bit).
 */
class RotShiftIxd(private val idx: IndexReg, private val op: RotateOp) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val r = op.apply(mem.read(addr), cpu.f)
        mem.write(addr, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} (${idx.mnemonic}+d)"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 2: Extend IxCbOps + IxCbOpsTest

- [ ] **Step 1: Add installRotateShift to IxCbOps**

```kotlin
object IxCbOps {
    fun installInto(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
            installRotateShift(table, idx)
        }
    }

    private fun installRotateShift(table: Array<Op?>, idx: IndexReg) {
        for (oooBits in 0..7) {
            if (oooBits == 6) continue   // SLL — undocumented
            val op = RotateOp.fromBits(oooBits)
            val opcode = (oooBits shl 3) or 0x06   // rrr=110 (the (idx+d) target slot)
            table[opcode] = RotShiftIxd(idx, op)
        }
    }
}
```

(Add necessary imports.)

- [ ] **Step 2: Replace IxCbOpsTest skeleton with real assertions**

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class IxCbOpsTest {

    @Test
    fun `installInto registers RLC (IX+d) at DDCB 0x06, RRC at 0x0E, ... SRL at 0x3E`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.ddcb[0x06] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RLC (IX+d)")
        assertThat((d.ddcb[0x0E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RRC (IX+d)")
        assertThat((d.ddcb[0x16] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RL (IX+d)")
        assertThat((d.ddcb[0x1E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RR (IX+d)")
        assertThat((d.ddcb[0x26] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SLA (IX+d)")
        assertThat((d.ddcb[0x2E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SRA (IX+d)")
        assertThat((d.ddcb[0x3E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SRL (IX+d)")
    }

    @Test
    fun `installInto registers same shape for FDCB (IY+d)`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.fdcb[0x06] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RLC (IY+d)")
        assertThat((d.fdcb[0x3E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SRL (IY+d)")
    }

    @Test
    fun `installInto leaves SLL slots (DDCB 0x36, FDCB 0x36) null`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat(d.ddcb[0x36]).isNull()
        assertThat(d.fdcb[0x36]).isNull()
    }

    @Test
    fun `installInto leaves undocumented copy-to-r rotate slots null (rrr != 110)`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        // Spot-check: 0x00 (RLC B undocumented), 0x07 (RLC A undocumented), 0x37 (SLL undocumented)
        assertThat(d.ddcb[0x00]).isNull()
        assertThat(d.ddcb[0x07]).isNull()
        assertThat(d.ddcb[0x37]).isNull()
    }

    @Test
    fun `installInto registers exactly 7 documented rotate-shift opcodes per index`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddcbRotShiftCount = (0x00..0x3F).count { d.ddcb[it] != null }
        val fdcbRotShiftCount = (0x00..0x3F).count { d.fdcb[it] != null }
        assertThat(ddcbRotShiftCount).isEqualTo(7)
        assertThat(fdcbRotShiftCount).isEqualTo(7)
    }
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxd.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/RotShiftIxdTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt
git commit -m "feat(op/ixcb): RotShiftIxd — 14 documented rotate/shift (IX+d)/(IY+d) ops

7 RotateOp × 2 IndexReg = 14 opcodes installed at the rrr=110 slot
of each row in DDCB/FDCB 0x00-0x3F. SLL slots (0x36) skipped.
Undocumented copy-to-r variants stay null. 23T, R+=2, PC+=4."
```

---

## WU 2.9-3 — BitIxd (16 opcodes)

### Task 1: BitIxd

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/BitIxd.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/BitIxdTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class BitIxdTest {
    @Test
    fun `BIT 7, (IX+5) tests bit 7 of byte at IX+5, advances pc by 4, r by 2, 20 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; ix = 0x4000; f = 0 }
        val mem = Memory().apply {
            write(0x102, 0x05)                          // d = +5
            write(0x4005, 0x80)                          // byte at IX+5
        }
        BitIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isZero            // bit 7 set → Z clear
        assertThat(cpu.f and Flags.H).isNotZero         // BIT always sets H
        assertThat(cpu.f and Flags.N).isZero
        assertThat(mem.read(0x4005)).isEqualTo(0x80)    // memory unchanged
        assertThat(cpu.ix).isEqualTo(0x4000)             // IX unchanged
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(20L)           // 20T not 23T (BIT only reads)
    }

    @Test
    fun `BIT 0, (IY-1) sets Z when bit is clear`() {
        val cpu = Cpu().apply { pc = 0x100; iy = 0x4000; f = 0 }
        val mem = Memory().apply {
            write(0x102, 0xFF)                          // d = -1
            write(0x3FFF, 0xFE)                          // bit 0 is clear
        }
        BitIxd(idx = IndexReg.IY, n = 0).execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `BIT preserves C flag`() {
        val cpu = Cpu().apply { ix = 0x4000; f = Flags.C }
        val mem = Memory().apply { write(0x102, 0); write(0x4000, 0x80) }
        BitIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `BitIxd rejects n outside 0..7`() {
        assertThatThrownBy { BitIxd(idx = IndexReg.IX, n = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { BitIxd(idx = IndexReg.IX, n = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(BitIxd(idx = IndexReg.IX, n = 0).mnemonic { 0 }).isEqualTo("BIT 0, (IX+d)")
        assertThat(BitIxd(idx = IndexReg.IY, n = 7).mnemonic { 0 }).isEqualTo("BIT 7, (IY+d)")
    }

    @Test
    fun `operandLength=0, baseCycles=20`() {
        val op = BitIxd(idx = IndexReg.IX, n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(20)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `BIT n, (IX+d)` / `BIT n, (IY+d)` — test bit n of memory at idx+d.
 * 20 T-states (only reads, doesn't write — distinct from RES/SET (IX+d)
 * which are 23T). R+=2. PC+=4.
 *
 * Flag rules:
 * - Z = !(bit n of byte at idx+d)
 * - H = 1
 * - N = 0
 * - C preserved
 * - S, P/V undocumented — left at 0
 */
class BitIxd(private val idx: IndexReg, private val n: Int) : Op {
    init { require(n in 0..7) { "BIT bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 20

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val bit = (mem.read(addr) shr n) and 1
        var f = cpu.f and Flags.C   // preserve C
        f = f or Flags.H
        if (bit == 0) f = f or Flags.Z
        cpu.f = f
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "BIT $n, (${idx.mnemonic}+d)"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 2: Extend IxCbOps + IxCbOpsTest

- [ ] **Step 1: Add installBit to IxCbOps**

```kotlin
object IxCbOps {
    fun installInto(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
            installRotateShift(table, idx)
            installBit(table, idx)
        }
    }

    // ... existing installRotateShift ...

    private fun installBit(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0x40 or (n shl 3) or 0x06   // rrr=110
            table[opcode] = BitIxd(idx, n)
        }
    }
}
```

- [ ] **Step 2: Add IxCbOpsTest assertions**

```kotlin
@Test
fun `installInto registers BIT n,(IX+d) at DDCB 0x46, 0x4E, 0x56, ..., 0x7E`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    assertThat((d.ddcb[0x46] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 0, (IX+d)")
    assertThat((d.ddcb[0x4E] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 1, (IX+d)")
    assertThat((d.ddcb[0x76] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 6, (IX+d)")
    assertThat((d.ddcb[0x7E] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 7, (IX+d)")
}

@Test
fun `installInto registers BIT n,(IY+d) at FDCB 0x46-0x7E`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    assertThat((d.fdcb[0x46] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 0, (IY+d)")
    assertThat((d.fdcb[0x7E] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 7, (IY+d)")
}

@Test
fun `installInto registers exactly 8 BIT opcodes per index in DDCB 0x40-0x7F`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    val ddcbBitCount = (0x40..0x7F).count { d.ddcb[it] != null }
    assertThat(ddcbBitCount).isEqualTo(8)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/BitIxd.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/BitIxdTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt
git commit -m "feat(op/ixcb): BitIxd — 16 BIT n,(IX+d)/(IY+d) opcodes

8 bit positions × 2 IndexReg = 16. 20T (BIT only reads). Z=!(bit),
H=1, N=0, C preserved, S/PV left at 0 (undocumented). Memory unchanged.
Installed at rrr=110 slots in DDCB/FDCB 0x40-0x7F."
```

---

## WU 2.9-4 — ResIxd + SetIxd (32 opcodes)

### Task 1: ResIxd

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/ResIxd.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/ResIxdTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class ResIxdTest {
    @Test
    fun `RES 7, (IX+5) clears bit 7 of byte at IX+5, advances pc by 4, r by 2, 23 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; ix = 0x4000; f = 0xFF }
        val mem = Memory().apply {
            write(0x102, 0x05)
            write(0x4005, 0xFF)
        }
        ResIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x7F)
        assertThat(cpu.f).isEqualTo(0xFF)              // RES doesn't touch flags
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `RES 0, (IY-2) handles negative displacement`() {
        val cpu = Cpu().apply { pc = 0x100; iy = 0x4000 }
        val mem = Memory().apply {
            write(0x102, 0xFE)                          // d = -2
            write(0x3FFE, 0xFF)
        }
        ResIxd(idx = IndexReg.IY, n = 0).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0xFE)
    }

    @Test
    fun `ResIxd rejects n outside 0..7`() {
        assertThatThrownBy { ResIxd(idx = IndexReg.IX, n = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(ResIxd(idx = IndexReg.IX, n = 0).mnemonic { 0 }).isEqualTo("RES 0, (IX+d)")
        assertThat(ResIxd(idx = IndexReg.IY, n = 7).mnemonic { 0 }).isEqualTo("RES 7, (IY+d)")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = ResIxd(idx = IndexReg.IX, n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RES n, (IX+d)` / `RES n, (IY+d)` — clear bit n of memory at idx+d.
 * 23 T-states. R+=2. PC+=4. No flags affected.
 */
class ResIxd(private val idx: IndexReg, private val n: Int) : Op {
    init { require(n in 0..7) { "RES bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val mask = (1 shl n).inv() and 0xFF
        mem.write(addr, mem.read(addr) and mask)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RES $n, (${idx.mnemonic}+d)"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 2: SetIxd

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ixcb/SetIxd.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ixcb/SetIxdTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class SetIxdTest {
    @Test
    fun `SET 7, (IX+5) sets bit 7 of byte at IX+5, 23 T-states, no flags`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; ix = 0x4000; f = 0xFF }
        val mem = Memory().apply {
            write(0x102, 0x05)
            write(0x4005, 0x00)
        }
        SetIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x80)
        assertThat(cpu.f).isEqualTo(0xFF)              // unchanged
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `SET 0, (IY-1) handles negative displacement`() {
        val cpu = Cpu().apply { pc = 0x100; iy = 0x4000 }
        val mem = Memory().apply {
            write(0x102, 0xFF)                          // d = -1
            write(0x3FFF, 0xFE)
        }
        SetIxd(idx = IndexReg.IY, n = 0).execute(cpu, mem)
        assertThat(mem.read(0x3FFF)).isEqualTo(0xFF)
    }

    @Test
    fun `SetIxd rejects n outside 0..7`() {
        assertThatThrownBy { SetIxd(idx = IndexReg.IX, n = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SetIxd(idx = IndexReg.IX, n = 0).mnemonic { 0 }).isEqualTo("SET 0, (IX+d)")
        assertThat(SetIxd(idx = IndexReg.IY, n = 7).mnemonic { 0 }).isEqualTo("SET 7, (IY+d)")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = SetIxd(idx = IndexReg.IX, n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SET n, (IX+d)` / `SET n, (IY+d)` — set bit n of memory at idx+d.
 * 23 T-states. R+=2. PC+=4. No flags affected.
 */
class SetIxd(private val idx: IndexReg, private val n: Int) : Op {
    init { require(n in 0..7) { "SET bit number must be 0..7; got $n" } }

    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val bit = 1 shl n
        mem.write(addr, mem.read(addr) or bit)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SET $n, (${idx.mnemonic}+d)"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 3: Extend IxCbOps + IxCbOpsTest with RES + SET installers

- [ ] **Step 1: Add installRes and installSet to IxCbOps**

```kotlin
object IxCbOps {
    fun installInto(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
            installRotateShift(table, idx)
            installBit(table, idx)
            installRes(table, idx)
            installSet(table, idx)
        }
    }

    // ... existing methods ...

    private fun installRes(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0x80 or (n shl 3) or 0x06
            table[opcode] = ResIxd(idx, n)
        }
    }

    private fun installSet(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0xC0 or (n shl 3) or 0x06
            table[opcode] = SetIxd(idx, n)
        }
    }
}
```

- [ ] **Step 2: Add IxCbOpsTest assertions**

```kotlin
@Test
fun `installInto registers RES n,(IX+d) at DDCB 0x86-0xBE`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    assertThat((d.ddcb[0x86] as ResIxd).mnemonic { 0 }).isEqualTo("RES 0, (IX+d)")
    assertThat((d.ddcb[0x8E] as ResIxd).mnemonic { 0 }).isEqualTo("RES 1, (IX+d)")
    assertThat((d.ddcb[0xBE] as ResIxd).mnemonic { 0 }).isEqualTo("RES 7, (IX+d)")
    val count = (0x80..0xBF).count { d.ddcb[it] != null }
    assertThat(count).isEqualTo(8)
}

@Test
fun `installInto registers SET n,(IY+d) at FDCB 0xC6-0xFE`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    assertThat((d.fdcb[0xC6] as SetIxd).mnemonic { 0 }).isEqualTo("SET 0, (IY+d)")
    assertThat((d.fdcb[0xFE] as SetIxd).mnemonic { 0 }).isEqualTo("SET 7, (IY+d)")
    val count = (0xC0..0xFF).count { d.fdcb[it] != null }
    assertThat(count).isEqualTo(8)
}

@Test
fun `installInto fills exactly 31 documented opcodes per index, 62 total`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    val ddcbCount = d.ddcb.count { it != null }
    val fdcbCount = d.fdcb.count { it != null }
    assertThat(ddcbCount).isEqualTo(31)            // 7 + 8 + 8 + 8
    assertThat(fdcbCount).isEqualTo(31)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/ResIxd.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/SetIxd.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/ResIxdTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/SetIxdTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt
git commit -m "feat(op/ixcb): ResIxd + SetIxd — 32 documented (IX+d)/(IY+d) bit ops

8 bit positions × 2 IndexReg × 2 ops = 32. 23T (read + modify + write).
No flags affected. Installed at rrr=110 slots in DDCB/FDCB 0x80-0xBF
(RES) and 0xC0-0xFF (SET). DDCB+FDCB now have 31 documented opcodes
each = 62 total."
```

---

## WU 2.9-5 — Sweep + tag

### Task 1: Verification + tag

- [ ] **Step 1: Clean build**

`./gradlew clean check installDist` — BUILD SUCCESSFUL.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count climbs by 62.

- [ ] **Step 3: Spotless check**

`./gradlew spotlessCheck` — green.

- [ ] **Step 4: Sanity-check key FUSE cases pass**

```bash
python3 -c "
import json
d = json.load(open('build/score.json'))
failures = d['suites']['fuse']['details'].get('failures', [])
for op in ['ddcb 06', 'ddcb 46', 'ddcb 86', 'ddcb c6', 'fdcb 06']:
    has = any(f.startswith(f'{op}:') for f in failures)
    print(f'  case {op}: {\"FAIL\" if has else \"PASS\"}')"
```

(Adjust FUSE case naming format as needed — DDCB cases may be named differently in the FUSE corpus.)

Expected: all PASS.

- [ ] **Step 5: Tag**

```bash
git tag -a m1-phase02-9 -m "M1 Phase 2.9 complete: DDCB/FDCB indexed bit/rotate

62 documented opcodes across 4 Op classes (RotShiftIxd, BitIxd, ResIxd,
SetIxd) all parameterized over IndexReg. Documented opcodes installed
only at rrr=110 slots; the other ~225 slots per table are undocumented
copy-to-r variants and stay null.

Plan 2.10 (ED-prefixed remainder + Trap B fix for block ops) is the
next batch."
```

This plan is complete.

---

## Self-Review

1. **Spec coverage:** Skeleton → 2.9-1. RotShiftIxd → 2.9-2. BitIxd → 2.9-3. ResIxd + SetIxd → 2.9-4. Sweep + tag → 2.9-5.

2. **Placeholder scan:** No "TBD" / "TODO". Each Op class has its full code shown.

3. **Type consistency:** All Op classes parameterized over `IndexReg`. All read displacement at pc+2 with `mem.read(...).toByte().toInt()`. All compute `(idx.read(cpu) + d) and 0xFFFF` for effective address. All `cpu.bumpR(2)` and PC+=4. RotShiftIxd reuses RotateOp.apply.

4. **Critical assertions per Op:**
   - All 4 Op classes have explicit signed-displacement tests (positive AND negative).
   - BIT (IX+d) cycle count is 20, not 23 — explicit test.
   - RES/SET don't touch flags — set f=0xFF before, assert after.
   - BIT preserves C — explicit test.
   - n in 0..7 validation in init for BIT/RES/SET.
   - IxCbOpsTest asserts SLL slots stay null + total counts (7+8+8+8 = 31 per table = 62 total).
   - IxCbOpsTest asserts undocumented "copy to r" slots stay null (spot-checks at rrr ≠ 110).

5. **Mnemonic format:** `"RLC (IX+d)"`, `"BIT 7, (IY+d)"`, `"RES 0, (IX+d)"`, `"SET 7, (IY+d)"`.

6. **Reuses existing foundations:** `RotateOp` from Phase 2.7, `IndexReg` from Phase 2.8, `Flags` constants. No new helpers needed.
