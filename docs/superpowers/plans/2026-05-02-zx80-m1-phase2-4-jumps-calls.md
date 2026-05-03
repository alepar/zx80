# ZX Spectrum Emulator — Phase 2.4 Implementation Plan (jumps, calls, returns)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Z80 control-flow opcodes (JP, JR, DJNZ, CALL, RET, RST and their conditional variants). 42 new opcode positions across 11 Op classes. None affect flags.

**Architecture:** New `Condition` enum (8 codes for JP/CALL/RET; first 4 also for JR). New `Cpu.push`/`Cpu.pop` extension methods centralizing Z80 stack semantics (used here by CALL/RET/RST and reused by Phase 2.5's PUSH/POP). 11 Op classes registered via a new `BranchOps.installInto(decoder)` fragment.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-4-jumps-calls-design.md`
**Branch:** `opus-4.7`
**Base commit:** `970008c` (the spec commit). Phase 2.3 must complete first.

---

## Universal patterns

Same as prior phases plus three notes specific to this batch:

- **No Op in this batch touches flags.** Per-Op tests set `cpu.f = 0xFF` (or some pattern) before and assert it's unchanged after.
- **Conditional ops have variable cycle counts** (taken vs not-taken). Test both branches per conditional Op.
- **Stack semantics are encapsulated** in `Cpu.push`/`Cpu.pop`. Use these everywhere; don't manipulate SP and memory directly inside Op classes.
- **Signed displacement** for JR/DJNZ: `mem.read(pc+1).toByte().toInt()` extends the sign correctly.

---

## File Structure

**New files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/
  Condition.kt                  # WU 2.4-1
  CpuStack.kt                   # WU 2.4-1
src/main/kotlin/ru/alepar/zx80/op/branch/
  BranchOps.kt                  # WU 2.4-1
  JpAbs.kt                      # WU 2.4-2
  JpAbsCc.kt                    # WU 2.4-2
  JpHl.kt                       # WU 2.4-2
  JrRel.kt                      # WU 2.4-3
  JrRelCc.kt                    # WU 2.4-3
  Djnz.kt                       # WU 2.4-3
  CallAbs.kt                    # WU 2.4-4
  CallAbsCc.kt                  # WU 2.4-4
  Ret.kt                        # WU 2.4-5
  RetCc.kt                      # WU 2.4-5
  Rst.kt                        # WU 2.4-5

src/test/kotlin/ru/alepar/zx80/cpu/
  ConditionTest.kt              # WU 2.4-1
  CpuStackTest.kt               # WU 2.4-1
src/test/kotlin/ru/alepar/zx80/op/branch/
  BranchOpsTest.kt              # WU 2.4-1 (created), extended in 2/3/4/5
  JpAbsTest.kt                  # WU 2.4-2
  JpAbsCcTest.kt                # WU 2.4-2
  JpHlTest.kt                   # WU 2.4-2
  JrRelTest.kt                  # WU 2.4-3
  JrRelCcTest.kt                # WU 2.4-3
  DjnzTest.kt                   # WU 2.4-3
  CallAbsTest.kt                # WU 2.4-4
  CallAbsCcTest.kt              # WU 2.4-4
  RetTest.kt                    # WU 2.4-5
  RetCcTest.kt                  # WU 2.4-5
  RstTest.kt                    # WU 2.4-5
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt  # WU 2.4-1 (add BranchOps.installInto)
```

---

## WU 2.4-1 — Foundation: Condition + Cpu.push/pop + BranchOps skeleton

### Task 1: Condition enum

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cpu/Condition.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/ConditionTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConditionTest {

    @Test
    fun `NZ is true when Z flag is clear, false when set`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.NZ.test(cpu)).isTrue
        cpu.f = Flags.Z
        assertThat(Condition.NZ.test(cpu)).isFalse
    }

    @Test
    fun `Z is true when Z flag is set, false when clear`() {
        val cpu = Cpu()
        cpu.f = Flags.Z
        assertThat(Condition.Z.test(cpu)).isTrue
        cpu.f = 0
        assertThat(Condition.Z.test(cpu)).isFalse
    }

    @Test
    fun `NC is true when C flag is clear`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.NC.test(cpu)).isTrue
        cpu.f = Flags.C
        assertThat(Condition.NC.test(cpu)).isFalse
    }

    @Test
    fun `C is true when C flag is set`() {
        val cpu = Cpu()
        cpu.f = Flags.C
        assertThat(Condition.C.test(cpu)).isTrue
    }

    @Test
    fun `PO is true when PV flag is clear (parity odd)`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.PO.test(cpu)).isTrue
        cpu.f = Flags.PV
        assertThat(Condition.PO.test(cpu)).isFalse
    }

    @Test
    fun `PE is true when PV flag is set`() {
        val cpu = Cpu().apply { f = Flags.PV }
        assertThat(Condition.PE.test(cpu)).isTrue
    }

    @Test
    fun `P is true when S flag is clear (positive)`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.P.test(cpu)).isTrue
        cpu.f = Flags.S
        assertThat(Condition.P.test(cpu)).isFalse
    }

    @Test
    fun `M is true when S flag is set (minus)`() {
        val cpu = Cpu().apply { f = Flags.S }
        assertThat(Condition.M.test(cpu)).isTrue
    }

    @Test
    fun `mnemonic matches enum name`() {
        assertThat(Condition.NZ.mnemonic).isEqualTo("NZ")
        assertThat(Condition.PO.mnemonic).isEqualTo("PO")
        assertThat(Condition.M.mnemonic).isEqualTo("M")
    }

    @Test
    fun `fromBits maps Z80 ccc encoding`() {
        assertThat(Condition.fromBits(0)).isEqualTo(Condition.NZ)
        assertThat(Condition.fromBits(1)).isEqualTo(Condition.Z)
        assertThat(Condition.fromBits(2)).isEqualTo(Condition.NC)
        assertThat(Condition.fromBits(3)).isEqualTo(Condition.C)
        assertThat(Condition.fromBits(4)).isEqualTo(Condition.PO)
        assertThat(Condition.fromBits(5)).isEqualTo(Condition.PE)
        assertThat(Condition.fromBits(6)).isEqualTo(Condition.P)
        assertThat(Condition.fromBits(7)).isEqualTo(Condition.M)
    }

    @Test
    fun `fromBits rejects out-of-range`() {
        assertThatThrownBy { Condition.fromBits(8) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Condition.fromBits(-1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
```

- [ ] **Step 2: Verify fails.** `./gradlew test --tests ConditionTest`.

- [ ] **Step 3: Implement Condition**

```kotlin
package ru.alepar.zx80.cpu

/**
 * The 8 Z80 condition codes used by JP cc, CALL cc, RET cc.
 * JR cc uses only the first 4 (NZ, Z, NC, C).
 */
enum class Condition(val mnemonic: String) {
    NZ("NZ"), Z("Z"),
    NC("NC"), C("C"),
    PO("PO"), PE("PE"),
    P("P"),  M("M");

    fun test(cpu: Cpu): Boolean = when (this) {
        NZ -> cpu.f and Flags.Z == 0
        Z  -> cpu.f and Flags.Z != 0
        NC -> cpu.f and Flags.C == 0
        C  -> cpu.f and Flags.C != 0
        PO -> cpu.f and Flags.PV == 0
        PE -> cpu.f and Flags.PV != 0
        P  -> cpu.f and Flags.S == 0
        M  -> cpu.f and Flags.S != 0
    }

    companion object {
        /** Map Z80 ccc bit pattern (0..7) to Condition. */
        fun fromBits(bits: Int): Condition {
            require(bits in 0..7) { "bits must be in 0..7; got $bits" }
            return entries[bits]
        }
    }
}
```

- [ ] **Step 4: Verify pass.** 11 tests.

### Task 2: Cpu.push/pop extension methods

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cpu/CpuStack.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/CpuStackTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CpuStackTest {

    @Test
    fun `push decrements SP twice and writes high byte then low byte (little-endian)`() {
        val cpu = Cpu().apply { sp = 0x4000 }
        val mem = Memory()
        cpu.push(mem, 0xABCD)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0xCD)   // low byte at lower address
        assertThat(mem.read(0x3FFF)).isEqualTo(0xAB)   // high byte at higher address
    }

    @Test
    fun `pop reads low byte from SP, high byte from SP+1, increments SP twice`() {
        val cpu = Cpu().apply { sp = 0x3FFE }
        val mem = Memory().apply {
            write(0x3FFE, 0xCD)
            write(0x3FFF, 0xAB)
        }
        val value = cpu.pop(mem)
        assertThat(value).isEqualTo(0xABCD)
        assertThat(cpu.sp).isEqualTo(0x4000)
    }

    @Test
    fun `push then pop round-trips a value`() {
        val cpu = Cpu().apply { sp = 0x8000 }
        val mem = Memory()
        cpu.push(mem, 0x1234)
        cpu.push(mem, 0x5678)
        assertThat(cpu.pop(mem)).isEqualTo(0x5678)
        assertThat(cpu.pop(mem)).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x8000)
    }

    @Test
    fun `push wraps SP from 0x0000 to 0xFFFE`() {
        val cpu = Cpu().apply { sp = 0x0000 }
        val mem = Memory()
        cpu.push(mem, 0x1234)
        assertThat(cpu.sp).isEqualTo(0xFFFE)
        assertThat(mem.read(0xFFFE)).isEqualTo(0x34)
        assertThat(mem.read(0xFFFF)).isEqualTo(0x12)
    }

    @Test
    fun `pop wraps SP from 0xFFFE to 0x0000`() {
        val cpu = Cpu().apply { sp = 0xFFFE }
        val mem = Memory().apply {
            write(0xFFFE, 0x34)
            write(0xFFFF, 0x12)
        }
        val value = cpu.pop(mem)
        assertThat(value).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x0000)
    }

    @Test
    fun `push masks value to 16 bits`() {
        val cpu = Cpu().apply { sp = 0x4000 }
        val mem = Memory()
        cpu.push(mem, 0x1ABCD)   // 17 bits; top bit must be discarded
        assertThat(mem.read(0x3FFE)).isEqualTo(0xCD)
        assertThat(mem.read(0x3FFF)).isEqualTo(0xAB)
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement Cpu.push/pop**

`src/main/kotlin/ru/alepar/zx80/cpu/CpuStack.kt`:

```kotlin
package ru.alepar.zx80.cpu

/**
 * Push a 16-bit value onto the Z80 stack.
 *
 * Convention: SP is decremented BEFORE writing each byte. High byte is
 * written first (at SP-1), then low byte (at SP-2). After the push,
 * SP points to the low byte. Value is masked to 16 bits.
 */
fun Cpu.push(mem: Memory, value: Int) {
    sp = (sp - 1) and 0xFFFF
    mem.write(sp, (value ushr 8) and 0xFF)
    sp = (sp - 1) and 0xFFFF
    mem.write(sp, value and 0xFF)
}

/**
 * Pop a 16-bit value from the Z80 stack.
 *
 * Convention: SP is incremented AFTER reading each byte. Low byte is
 * read from SP, then high byte from SP+1. After the pop, SP points one
 * above the original high byte.
 */
fun Cpu.pop(mem: Memory): Int {
    val lo = mem.read(sp)
    sp = (sp + 1) and 0xFFFF
    val hi = mem.read(sp)
    sp = (sp + 1) and 0xFFFF
    return (hi shl 8) or lo
}
```

- [ ] **Step 4: Verify pass.** 6 tests.

### Task 3: BranchOps skeleton + wire into builder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/BranchOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/BranchOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class BranchOpsTest {

    @Test
    fun `installInto on empty Decoder installs nothing yet (skeleton)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        // WUs 2.4-2..5 add real registrations; for now just verify
        // the call doesn't crash and the function exists.
        val totalInstalled = listOf(d.main, d.cb, d.ed, d.dd, d.fd, d.ddcb, d.fdcb)
            .sumOf { table -> table.count { it != null } }
        assertThat(totalInstalled).isZero
    }
}
```

- [ ] **Step 2: Verify fails.**

- [ ] **Step 3: Implement BranchOps skeleton**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the branch Op family (JP, JR, DJNZ, CALL, RET, RST and
 * their conditional variants) into the decoder. Called by
 * [ru.alepar.zx80.op.OpTableBuilder]. Subsequent WUs extend this
 * fragment with the actual installations.
 */
object BranchOps {
    fun installInto(d: Decoder) {
        // Filled in by WUs 2.4-2 through 2.4-5.
    }
}
```

- [ ] **Step 4: Wire into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:
- Add: `import ru.alepar.zx80.op.branch.BranchOps`
- In `build()`, add `BranchOps.installInto(d)` after the existing `installInto` calls.

- [ ] **Step 5: Verify**

`./gradlew test --tests BranchOpsTest` — passes (1 trivial assertion).
`./gradlew test` — entire suite still green.

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

`./gradlew test spotlessApply`. Expected: 11 (Condition) + 6 (CpuStack) + 1 (BranchOps) = 18 new tests.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Condition.kt \
        src/main/kotlin/ru/alepar/zx80/cpu/CpuStack.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/ConditionTest.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/CpuStackTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/BranchOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/BranchOpsTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(cpu): Condition enum + Cpu.push/pop helpers (Phase 2.4 foundation)

Condition enum models the 8 Z80 condition codes (NZ/Z/NC/C/PO/PE/P/M)
with test(cpu) and fromBits(ccc). Cpu.push/Cpu.pop are extension
methods centralizing Z80 stack semantics (pre-decrement push,
post-increment pop, little-endian word, SP wraps mod 64K). Used by
CALL/RET/RST in this batch and PUSH/POP rr in Phase 2.5. BranchOps
fragment skeleton wired into OpTableBuilder."
```

---

## WU 2.4-2 — JP family (10 opcodes)

3 Op classes: `JpAbs`, `JpAbsCc(cond)`, `JpHl`.

### Task 1: JpAbs

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/JpAbs.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/JpAbsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class JpAbsTest {
    @Test
    fun `JP nn sets pc to little-endian word at pc+1, 10 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L }
        val mem = Memory().apply {
            write(0x100, 0xC3)   // JP nn opcode
            write(0x101, 0x00)   // low byte of nn
            write(0x102, 0x80)   // high byte of nn (nn = 0x8000)
        }
        JpAbs.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `JP nn does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; f = 0xFF }
        val mem = Memory().apply {
            write(0x101, 0x00); write(0x102, 0x40)
        }
        JpAbs.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JpAbs.mnemonic { 0 }).isEqualTo("JP nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10`() {
        assertThat(JpAbs.operandLength).isEqualTo(2)
        assertThat(JpAbs.baseCycles).isEqualTo(10)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JP nn` — unconditional absolute jump. Reads little-endian 16-bit
 * address from pc+1..pc+2, sets pc to it. 10 T-states. No flag changes.
 */
object JpAbs : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = mem.readWord(cpu.pc + 1)
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JP nn"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: JpAbsCc

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/JpAbsCc.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/JpAbsCcTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class JpAbsCcTest {
    @Test
    fun `JP NZ, nn jumps when Z is clear`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; f = 0 }
        val mem = Memory().apply {
            write(0x100, 0xC2); write(0x101, 0x00); write(0x102, 0x80)
        }
        JpAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.tStates).isEqualTo(10L)   // same cost regardless
    }

    @Test
    fun `JP NZ, nn falls through when Z is set, advancing pc by 3`() {
        val cpu = Cpu().apply { pc = 0x100; tStates = 0L; f = Flags.Z }
        val mem = Memory().apply {
            write(0x100, 0xC2); write(0x101, 0x00); write(0x102, 0x80)
        }
        JpAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x103)       // not jumped
        assertThat(cpu.tStates).isEqualTo(10L)    // still 10 (JP cc cost is constant)
    }

    @Test
    fun `JP Z, nn jumps when Z is set`() {
        val cpu = Cpu().apply { pc = 0x100; f = Flags.Z }
        val mem = Memory().apply {
            write(0x101, 0x00); write(0x102, 0x40)
        }
        JpAbsCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x4000)
    }

    @Test
    fun `JP cc does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; f = 0xFF }
        val mem = Memory().apply {
            write(0x101, 0x00); write(0x102, 0x80)
        }
        JpAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JpAbsCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("JP NZ, nn")
        assertThat(JpAbsCc(cond = Condition.M).mnemonic { 0 }).isEqualTo("JP M, nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10`() {
        val op = JpAbsCc(cond = Condition.NZ)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JP cc, nn` — conditional absolute jump. Always reads nn from
 * pc+1..pc+2. 10 T-states whether taken or not. No flag changes.
 *
 * 8 opcodes (one per Condition): C2, CA, D2, DA, E2, EA, F2, FA.
 */
class JpAbsCc(private val cond: Condition) : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        cpu.pc = if (cond.test(cpu)) nn else (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JP ${cond.mnemonic}, nn"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 3: JpHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/JpHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/JpHlTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class JpHlTest {
    @Test
    fun `JP (HL) sets pc to HL value (NOT memory at HL)`() {
        val cpu = Cpu().apply { pc = 0x100; tStates = 0L; hl = 0x4000 }
        // Specifically poke memory at HL with a different value to ensure
        // we're using HL itself, not dereferencing it.
        val mem = Memory().apply { write(0x4000, 0x99); write(0x4001, 0x99) }
        JpHl.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x4000)   // HL value, not memory contents
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `JP (HL) does NOT touch flags`() {
        val cpu = Cpu().apply { hl = 0x100; f = 0xFF }
        JpHl.execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JpHl.mnemonic { 0 }).isEqualTo("JP (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(JpHl.operandLength).isZero
        assertThat(JpHl.baseCycles).isEqualTo(4)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JP (HL)` — jump to the value of HL. Despite the parens, this is
 * NOT a memory dereference — pc is set to HL itself. 4 T-states.
 * Single opcode 0xE9. No flag changes.
 */
object JpHl : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = cpu.hl
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JP (HL)"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 4: Extend BranchOps + BranchOpsTest

- [ ] **Step 1: Add installJpFamily to BranchOps**

```kotlin
object BranchOps {
    fun installInto(d: Decoder) {
        installJpFamily(d)
    }

    private fun installJpFamily(d: Decoder) {
        d.main[0xC3] = JpAbs
        d.main[0xE9] = JpHl
        // JP cc, nn — pattern 11 ccc 010 → C2, CA, D2, DA, E2, EA, F2, FA
        for (cccBits in 0..7) {
            val opcode = 0xC2 or (cccBits shl 3)
            d.main[opcode] = JpAbsCc(cond = Condition.fromBits(cccBits))
        }
    }
}
```

(Add necessary imports: `Condition`.)

- [ ] **Step 2: Replace BranchOpsTest body with real assertions**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class BranchOpsTest {

    @Test
    fun `installInto registers JP nn at 0xC3`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat(d.main[0xC3]).isSameAs(JpAbs)
    }

    @Test
    fun `installInto registers JP (HL) at 0xE9`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat(d.main[0xE9]).isSameAs(JpHl)
    }

    @Test
    fun `installInto registers JP cc, nn at 0xC2 (NZ), 0xCA (Z), 0xD2 (NC), 0xDA (C), 0xE2 (PO), 0xEA (PE), 0xF2 (P), 0xFA (M)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat((d.main[0xC2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP NZ, nn")
        assertThat((d.main[0xCA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP Z, nn")
        assertThat((d.main[0xD2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP NC, nn")
        assertThat((d.main[0xDA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP C, nn")
        assertThat((d.main[0xE2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP PO, nn")
        assertThat((d.main[0xEA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP PE, nn")
        assertThat((d.main[0xF2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP P, nn")
        assertThat((d.main[0xFA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP M, nn")
    }
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/branch/JpAbs.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/JpAbsCc.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/JpHl.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/JpAbsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/JpAbsCcTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/JpHlTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/BranchOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/BranchOpsTest.kt
git commit -m "feat(op/branch): JP family — 10 opcodes (JP nn, JP cc, JP (HL))"
```

---

## WU 2.4-3 — JR + DJNZ family (6 opcodes)

3 Op classes: `JrRel`, `JrRelCc`, `Djnz`. All use signed 8-bit displacement.

### Task 1: JrRel

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/JrRel.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/JrRelTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class JrRelTest {
    @Test
    fun `JR e with positive displacement jumps forward`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L }
        val mem = Memory().apply {
            write(0x100, 0x18)
            write(0x101, 0x05)   // displacement = +5
        }
        JrRel.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x107)   // 0x100 + 2 + 5
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `JR e with negative displacement jumps backward`() {
        val cpu = Cpu().apply { pc = 0x100 }
        val mem = Memory().apply {
            write(0x100, 0x18)
            write(0x101, 0xFE)   // displacement = -2 (signed)
        }
        JrRel.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x100)   // 0x100 + 2 + (-2) = 0x100 (infinite loop on self)
    }

    @Test
    fun `JR e wraps PC mod 64K`() {
        val cpu = Cpu().apply { pc = 0xFFFE }
        val mem = Memory().apply {
            write(0xFFFE, 0x18)
            write(0xFFFF, 0x05)
        }
        JrRel.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x0005)   // 0xFFFE + 2 + 5 = 0x10005, mod 64K = 5
    }

    @Test
    fun `JR e does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; f = 0xFF }
        val mem = Memory().apply { write(0x101, 0x05) }
        JrRel.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JrRel.mnemonic { 0 }).isEqualTo("JR e")
    }

    @Test
    fun `operandLength=1, baseCycles=12`() {
        assertThat(JrRel.operandLength).isEqualTo(1)
        assertThat(JrRel.baseCycles).isEqualTo(12)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JR e` — unconditional relative jump. The displacement byte at pc+1
 * is signed (-128..+127). New pc = (pc + 2 + signedDisplacement) mod 64K.
 * 12 T-states. No flag changes. Single opcode 0x18.
 */
object JrRel : Op {
    override val operandLength = 1
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        val e = mem.read(cpu.pc + 1).toByte().toInt()   // signed
        cpu.pc = (cpu.pc + 2 + e) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JR e"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 2: JrRelCc

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/JrRelCc.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/JrRelCcTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class JrRelCcTest {
    @Test
    fun `JR NZ, e jumps when Z is clear, 12 T-states taken`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; f = 0 }
        val mem = Memory().apply {
            write(0x100, 0x20); write(0x101, 0x05)
        }
        JrRelCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x107)
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `JR NZ, e falls through when Z is set, 7 T-states not-taken`() {
        val cpu = Cpu().apply { pc = 0x100; tStates = 0L; f = Flags.Z }
        val mem = Memory().apply {
            write(0x100, 0x20); write(0x101, 0x05)
        }
        JrRelCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x102)        // not jumped
        assertThat(cpu.tStates).isEqualTo(7L)      // 7, not 12
    }

    @Test
    fun `JR rejects PO, PE, P, M conditions (only NZ Z NC C valid)`() {
        assertThatThrownBy { JrRelCc(cond = Condition.PO) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { JrRelCc(cond = Condition.M) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `JR cc does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; f = Flags.Z or Flags.C }
        val mem = Memory().apply { write(0x101, 0x05) }
        JrRelCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(Flags.Z or Flags.C)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JrRelCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("JR NZ, e")
        assertThat(JrRelCc(cond = Condition.C).mnemonic { 0 }).isEqualTo("JR C, e")
    }

    @Test
    fun `operandLength=1, baseCycles=7 (not-taken cost; taken adds 5)`() {
        val op = JrRelCc(cond = Condition.NZ)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JR cc, e` — conditional relative jump. Only 4 conditions valid
 * (NZ, Z, NC, C). 7 T-states not-taken; 12 T-states taken (base 7 + 5
 * extra). Always reads displacement from pc+1.
 *
 * 4 opcodes: 0x20 (NZ), 0x28 (Z), 0x30 (NC), 0x38 (C).
 */
class JrRelCc(private val cond: Condition) : Op {
    init {
        require(cond in setOf(Condition.NZ, Condition.Z, Condition.NC, Condition.C)) {
            "JR cc accepts only NZ/Z/NC/C; got $cond"
        }
    }

    override val operandLength = 1
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val e = mem.read(cpu.pc + 1).toByte().toInt()
        if (cond.test(cpu)) {
            cpu.pc = (cpu.pc + 2 + e) and 0xFFFF
            cpu.tStates += 5         // extra cycles for the taken branch
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JR ${cond.mnemonic}, e"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 3: Djnz

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/Djnz.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/DjnzTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class DjnzTest {
    @Test
    fun `DJNZ decrements B, jumps if B != 0 after, 13 T-states taken`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; b = 0x05 }
        val mem = Memory().apply {
            write(0x100, 0x10); write(0x101, 0xFE)   // displacement -2
        }
        Djnz.execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x04)
        assertThat(cpu.pc).isEqualTo(0x100)         // 0x100 + 2 + (-2) = 0x100
        assertThat(cpu.tStates).isEqualTo(13L)
    }

    @Test
    fun `DJNZ decrements B, falls through when B becomes 0, 8 T-states not-taken`() {
        val cpu = Cpu().apply { pc = 0x100; tStates = 0L; b = 0x01 }
        val mem = Memory().apply {
            write(0x100, 0x10); write(0x101, 0x05)
        }
        Djnz.execute(cpu, mem)
        assertThat(cpu.b).isZero
        assertThat(cpu.pc).isEqualTo(0x102)         // not jumped
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `DJNZ wraps B from 0x00 to 0xFF and JUMPS (since 0xFF != 0)`() {
        val cpu = Cpu().apply { pc = 0x100; b = 0x00 }
        val mem = Memory().apply { write(0x101, 0x10) }
        Djnz.execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x112)        // 0x100 + 2 + 0x10
    }

    @Test
    fun `DJNZ does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; b = 0x05; f = 0xFF }
        val mem = Memory().apply { write(0x101, 0x05) }
        Djnz.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Djnz.mnemonic { 0 }).isEqualTo("DJNZ e")
    }

    @Test
    fun `operandLength=1, baseCycles=8 (not-taken)`() {
        assertThat(Djnz.operandLength).isEqualTo(1)
        assertThat(Djnz.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DJNZ e` — Decrement B; jump relative if B != 0. The decrement does
 * NOT update flags (despite being a decrement). 8 T-states if B=0
 * after; 13 T-states if jumped. Single opcode 0x10.
 */
object Djnz : Op {
    override val operandLength = 1
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.b = (cpu.b - 1) and 0xFF
        val e = mem.read(cpu.pc + 1).toByte().toInt()
        if (cpu.b != 0) {
            cpu.pc = (cpu.pc + 2 + e) and 0xFFFF
            cpu.tStates += 5         // extra cycles for taken branch
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DJNZ e"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Task 4: Extend BranchOps + BranchOpsTest

- [ ] **Step 1: Add installJrAndDjnz to BranchOps**

```kotlin
object BranchOps {
    fun installInto(d: Decoder) {
        installJpFamily(d)
        installJrAndDjnz(d)
    }

    // ... existing installJpFamily ...

    private fun installJrAndDjnz(d: Decoder) {
        d.main[0x18] = JrRel
        d.main[0x10] = Djnz
        // JR cc, e — 0x20 (NZ), 0x28 (Z), 0x30 (NC), 0x38 (C)
        d.main[0x20] = JrRelCc(cond = Condition.NZ)
        d.main[0x28] = JrRelCc(cond = Condition.Z)
        d.main[0x30] = JrRelCc(cond = Condition.NC)
        d.main[0x38] = JrRelCc(cond = Condition.C)
    }
}
```

- [ ] **Step 2: Add BranchOpsTest assertions**

```kotlin
@Test
fun `installInto registers JR e at 0x18 and DJNZ e at 0x10`() {
    val d = Decoder()
    BranchOps.installInto(d)
    assertThat(d.main[0x18]).isSameAs(JrRel)
    assertThat(d.main[0x10]).isSameAs(Djnz)
}

@Test
fun `installInto registers JR cc, e at 0x20 (NZ), 0x28 (Z), 0x30 (NC), 0x38 (C)`() {
    val d = Decoder()
    BranchOps.installInto(d)
    assertThat((d.main[0x20] as JrRelCc).mnemonic { 0 }).isEqualTo("JR NZ, e")
    assertThat((d.main[0x28] as JrRelCc).mnemonic { 0 }).isEqualTo("JR Z, e")
    assertThat((d.main[0x30] as JrRelCc).mnemonic { 0 }).isEqualTo("JR NC, e")
    assertThat((d.main[0x38] as JrRelCc).mnemonic { 0 }).isEqualTo("JR C, e")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/branch/JrRel.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/JrRelCc.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/Djnz.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/JrRelTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/JrRelCcTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/DjnzTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/BranchOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/BranchOpsTest.kt
git commit -m "feat(op/branch): JR + DJNZ — 6 opcodes with signed displacement

JR e (12T), JR cc,e (7 not-taken / 12 taken; only NZ/Z/NC/C),
DJNZ e (8 not-taken / 13 taken). Displacement is signed 8-bit.
DJNZ decrements B without updating flags."
```

---

## WU 2.4-4 — CALL family (9 opcodes)

2 Op classes: `CallAbs`, `CallAbsCc`.

### Task 1: CallAbs

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/CallAbs.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/CallAbsTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class CallAbsTest {
    @Test
    fun `CALL nn pushes pc+3 and jumps, 17 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; sp = 0x4000 }
        val mem = Memory().apply {
            write(0x100, 0xCD); write(0x101, 0x00); write(0x102, 0x80)
        }
        CallAbs.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x03)   // low byte of 0x103
        assertThat(mem.read(0x3FFF)).isEqualTo(0x01)   // high byte of 0x103
        assertThat(cpu.tStates).isEqualTo(17L)
    }

    @Test
    fun `CALL does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x4000; f = 0xFF }
        val mem = Memory().apply { write(0x101, 0x00); write(0x102, 0x80) }
        CallAbs.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(CallAbs.mnemonic { 0 }).isEqualTo("CALL nn")
    }

    @Test
    fun `operandLength=2, baseCycles=17`() {
        assertThat(CallAbs.operandLength).isEqualTo(2)
        assertThat(CallAbs.baseCycles).isEqualTo(17)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CALL nn` — push the return address (pc+3) onto the stack and jump
 * to nn. 17 T-states. No flag changes.
 */
object CallAbs : Op {
    override val operandLength = 2
    override val baseCycles = 17

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        val returnAddr = (cpu.pc + 3) and 0xFFFF
        cpu.push(mem, returnAddr)
        cpu.pc = nn
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CALL nn"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: CallAbsCc

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/CallAbsCc.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/CallAbsCcTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CallAbsCcTest {
    @Test
    fun `CALL Z, nn taken pushes return addr and jumps, 17 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L; sp = 0x4000; f = Flags.Z
        }
        val mem = Memory().apply {
            write(0x100, 0xCC); write(0x101, 0x00); write(0x102, 0x80)
        }
        CallAbsCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x03)
        assertThat(cpu.tStates).isEqualTo(17L)
    }

    @Test
    fun `CALL Z, nn not-taken falls through, no push, 10 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; tStates = 0L; sp = 0x4000; f = 0
        }
        val mem = Memory().apply {
            write(0x100, 0xCC); write(0x101, 0x00); write(0x102, 0x80)
        }
        CallAbsCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.sp).isEqualTo(0x4000)        // SP unchanged
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `CALL cc does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x4000; f = 0xFF }
        val mem = Memory().apply { write(0x101, 0x00); write(0x102, 0x80) }
        CallAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(CallAbsCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("CALL NZ, nn")
        assertThat(CallAbsCc(cond = Condition.M).mnemonic { 0 }).isEqualTo("CALL M, nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10 (not-taken cost; taken adds 7)`() {
        val op = CallAbsCc(cond = Condition.NZ)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CALL cc, nn` — conditional call. Always reads nn. 10 T-states not
 * taken; 17 T-states taken (base 10 + 7 extra). Push happens only on
 * taken branch.
 *
 * 8 opcodes (one per Condition): C4, CC, D4, DC, E4, EC, F4, FC.
 */
class CallAbsCc(private val cond: Condition) : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        if (cond.test(cpu)) {
            val returnAddr = (cpu.pc + 3) and 0xFFFF
            cpu.push(mem, returnAddr)
            cpu.pc = nn
            cpu.tStates += 7         // extra cycles for taken branch
        } else {
            cpu.pc = (cpu.pc + 3) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CALL ${cond.mnemonic}, nn"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 3: Extend BranchOps + BranchOpsTest

- [ ] **Step 1: Add installCallFamily**

```kotlin
object BranchOps {
    fun installInto(d: Decoder) {
        installJpFamily(d)
        installJrAndDjnz(d)
        installCallFamily(d)
    }

    // ... existing methods ...

    private fun installCallFamily(d: Decoder) {
        d.main[0xCD] = CallAbs
        // CALL cc, nn — pattern 11 ccc 100 → C4, CC, D4, DC, E4, EC, F4, FC
        for (cccBits in 0..7) {
            val opcode = 0xC4 or (cccBits shl 3)
            d.main[opcode] = CallAbsCc(cond = Condition.fromBits(cccBits))
        }
    }
}
```

- [ ] **Step 2: Add BranchOpsTest assertions**

```kotlin
@Test
fun `installInto registers CALL nn at 0xCD`() {
    val d = Decoder()
    BranchOps.installInto(d)
    assertThat(d.main[0xCD]).isSameAs(CallAbs)
}

@Test
fun `installInto registers CALL cc, nn at 0xC4 (NZ) through 0xFC (M)`() {
    val d = Decoder()
    BranchOps.installInto(d)
    assertThat((d.main[0xC4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL NZ, nn")
    assertThat((d.main[0xCC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL Z, nn")
    assertThat((d.main[0xD4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL NC, nn")
    assertThat((d.main[0xDC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL C, nn")
    assertThat((d.main[0xE4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL PO, nn")
    assertThat((d.main[0xEC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL PE, nn")
    assertThat((d.main[0xF4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL P, nn")
    assertThat((d.main[0xFC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL M, nn")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/branch/CallAbs.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/CallAbsCc.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/CallAbsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/CallAbsCcTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/BranchOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/BranchOpsTest.kt
git commit -m "feat(op/branch): CALL family — 9 opcodes (CALL nn, CALL cc nn)

CallAbs pushes pc+3 and jumps. CallAbsCc reads nn always; pushes and
jumps only if condition taken (10T not-taken, 17T taken)."
```

---

## WU 2.4-5 — RET + RST family (17 opcodes)

3 Op classes: `Ret`, `RetCc`, `Rst`.

### Task 1: Ret

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/Ret.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/RetTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class RetTest {
    @Test
    fun `RET pops pc from stack, increments SP, 10 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; sp = 0x3FFE }
        val mem = Memory().apply {
            write(0x3FFE, 0x34)   // low byte
            write(0x3FFF, 0x12)   // high byte (return addr = 0x1234)
        }
        Ret.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `RET does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x3FFE; f = 0xFF }
        val mem = Memory().apply { write(0x3FFE, 0x00); write(0x3FFF, 0x40) }
        Ret.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ret.mnemonic { 0 }).isEqualTo("RET")
    }

    @Test
    fun `operandLength=0, baseCycles=10`() {
        assertThat(Ret.operandLength).isZero
        assertThat(Ret.baseCycles).isEqualTo(10)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RET` — pop the return address from the stack into pc. 10 T-states.
 * No flag changes. Single opcode 0xC9.
 */
object Ret : Op {
    override val operandLength = 0
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = cpu.pop(mem)
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RET"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: RetCc

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/RetCc.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/RetCcTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RetCcTest {
    @Test
    fun `RET Z taken pops pc, 11 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L; sp = 0x3FFE; f = Flags.Z
        }
        val mem = Memory().apply { write(0x3FFE, 0x34); write(0x3FFF, 0x12) }
        RetCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `RET Z not-taken falls through (pc+1), no pop, 5 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; tStates = 0L; sp = 0x3FFE; f = 0
        }
        val mem = Memory().apply { write(0x3FFE, 0x34); write(0x3FFF, 0x12) }
        RetCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.sp).isEqualTo(0x3FFE)         // SP unchanged
        assertThat(cpu.tStates).isEqualTo(5L)
    }

    @Test
    fun `RET cc does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x3FFE; f = Flags.Z }
        val mem = Memory().apply { write(0x3FFE, 0x00); write(0x3FFF, 0x40) }
        RetCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(Flags.Z)
    }

    @Test
    fun `mnemonic`() {
        assertThat(RetCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("RET NZ")
        assertThat(RetCc(cond = Condition.M).mnemonic { 0 }).isEqualTo("RET M")
    }

    @Test
    fun `operandLength=0, baseCycles=5 (not-taken cost; taken adds 6)`() {
        val op = RetCc(cond = Condition.NZ)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(5)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RET cc` — conditional return. 5 T-states not-taken; 11 T-states
 * taken (base 5 + 6 extra). Pop happens only on taken branch.
 *
 * 8 opcodes (one per Condition): C0, C8, D0, D8, E0, E8, F0, F8.
 */
class RetCc(private val cond: Condition) : Op {
    override val operandLength = 0
    override val baseCycles = 5

    override fun execute(cpu: Cpu, mem: Memory) {
        if (cond.test(cpu)) {
            cpu.pc = cpu.pop(mem)
            cpu.tStates += 6         // extra cycles for taken branch
        } else {
            cpu.pc = (cpu.pc + 1) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RET ${cond.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 3: Rst

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/branch/Rst.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/branch/RstTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class RstTest {
    @Test
    fun `RST 18H pushes pc+1 and jumps to 0x18, 11 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; sp = 0x4000 }
        val mem = Memory()
        Rst(target = 0x18).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x18)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x01)   // low byte of 0x101
        assertThat(mem.read(0x3FFF)).isEqualTo(0x01)   // high byte of 0x101
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `RST 00H jumps to 0x0000`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x4000 }
        Rst(target = 0x00).execute(cpu, Memory())
        assertThat(cpu.pc).isZero
    }

    @Test
    fun `RST 38H jumps to 0x0038`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x4000 }
        Rst(target = 0x38).execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x38)
    }

    @Test
    fun `RST does NOT touch flags`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x4000; f = 0xFF }
        Rst(target = 0x10).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `Rst rejects target outside the 8 valid values`() {
        assertThatThrownBy { Rst(target = 0x05) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Rst(target = 0x40) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rst(target = 0x00).mnemonic { 0 }).isEqualTo("RST 00H")
        assertThat(Rst(target = 0x18).mnemonic { 0 }).isEqualTo("RST 18H")
        assertThat(Rst(target = 0x38).mnemonic { 0 }).isEqualTo("RST 38H")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        val op = Rst(target = 0x00)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RST p` — single-byte CALL to a fixed page-zero address. Pushes pc+1
 * (return address) and jumps to `target`. 11 T-states. No flag changes.
 *
 * 8 valid targets: 0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38.
 * Opcodes: 0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF.
 */
class Rst(private val target: Int) : Op {
    init {
        require(target in VALID_TARGETS) {
            "RST target must be one of $VALID_TARGETS; got 0x${target.toString(16)}"
        }
    }

    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val returnAddr = (cpu.pc + 1) and 0xFFFF
        cpu.push(mem, returnAddr)
        cpu.pc = target
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "RST ${target.toString(16).padStart(2, '0').uppercase()}H"

    companion object {
        val VALID_TARGETS = setOf(0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38)
    }
}
```

- [ ] **Step 4: Verify.** 7 tests.

### Task 4: Extend BranchOps + BranchOpsTest with RET/RST

- [ ] **Step 1: Add installRetFamily and installRstFamily**

```kotlin
object BranchOps {
    fun installInto(d: Decoder) {
        installJpFamily(d)
        installJrAndDjnz(d)
        installCallFamily(d)
        installRetFamily(d)
        installRstFamily(d)
    }

    // ... existing private methods ...

    private fun installRetFamily(d: Decoder) {
        d.main[0xC9] = Ret
        // RET cc — pattern 11 ccc 000 → C0, C8, D0, D8, E0, E8, F0, F8
        for (cccBits in 0..7) {
            val opcode = 0xC0 or (cccBits shl 3)
            d.main[opcode] = RetCc(cond = Condition.fromBits(cccBits))
        }
    }

    private fun installRstFamily(d: Decoder) {
        // RST p — pattern 11 ttt 111 → C7, CF, D7, DF, E7, EF, F7, FF
        for (tttBits in 0..7) {
            val opcode = 0xC7 or (tttBits shl 3)
            d.main[opcode] = Rst(target = tttBits * 8)
        }
    }
}
```

- [ ] **Step 2: Add BranchOpsTest assertions**

```kotlin
@Test
fun `installInto registers RET at 0xC9`() {
    val d = Decoder()
    BranchOps.installInto(d)
    assertThat(d.main[0xC9]).isSameAs(Ret)
}

@Test
fun `installInto registers RET cc at 0xC0 (NZ) through 0xF8 (M)`() {
    val d = Decoder()
    BranchOps.installInto(d)
    assertThat((d.main[0xC0] as RetCc).mnemonic { 0 }).isEqualTo("RET NZ")
    assertThat((d.main[0xC8] as RetCc).mnemonic { 0 }).isEqualTo("RET Z")
    assertThat((d.main[0xD0] as RetCc).mnemonic { 0 }).isEqualTo("RET NC")
    assertThat((d.main[0xD8] as RetCc).mnemonic { 0 }).isEqualTo("RET C")
    assertThat((d.main[0xE0] as RetCc).mnemonic { 0 }).isEqualTo("RET PO")
    assertThat((d.main[0xE8] as RetCc).mnemonic { 0 }).isEqualTo("RET PE")
    assertThat((d.main[0xF0] as RetCc).mnemonic { 0 }).isEqualTo("RET P")
    assertThat((d.main[0xF8] as RetCc).mnemonic { 0 }).isEqualTo("RET M")
}

@Test
fun `installInto registers RST p at 0xC7 (00H) through 0xFF (38H)`() {
    val d = Decoder()
    BranchOps.installInto(d)
    assertThat((d.main[0xC7] as Rst).mnemonic { 0 }).isEqualTo("RST 00H")
    assertThat((d.main[0xCF] as Rst).mnemonic { 0 }).isEqualTo("RST 08H")
    assertThat((d.main[0xD7] as Rst).mnemonic { 0 }).isEqualTo("RST 10H")
    assertThat((d.main[0xDF] as Rst).mnemonic { 0 }).isEqualTo("RST 18H")
    assertThat((d.main[0xE7] as Rst).mnemonic { 0 }).isEqualTo("RST 20H")
    assertThat((d.main[0xEF] as Rst).mnemonic { 0 }).isEqualTo("RST 28H")
    assertThat((d.main[0xF7] as Rst).mnemonic { 0 }).isEqualTo("RST 30H")
    assertThat((d.main[0xFF] as Rst).mnemonic { 0 }).isEqualTo("RST 38H")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/branch/Ret.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/RetCc.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/Rst.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/RetTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/RetCcTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/RstTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/branch/BranchOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/branch/BranchOpsTest.kt
git commit -m "feat(op/branch): RET + RST — 17 opcodes

Ret pops pc (10T). RetCc pops only if taken (5T not-taken, 11T taken).
Rst pushes pc+1 and jumps to one of 8 fixed page-zero addresses (11T)."
```

---

## WU 2.4-6 — Sweep + tag

### Task 1: Verification + tag

- [ ] **Step 1: Clean build**

`./gradlew clean check installDist` — BUILD SUCCESSFUL.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count climbs by 42. Composite score climbs noticeably (control flow is heavily exercised in FUSE).

- [ ] **Step 3: Spotless check**

`./gradlew spotlessCheck` — green.

- [ ] **Step 4: Sanity-check FUSE C3 case (JP nn) passes**

```bash
python3 -c "import json; d = json.load(open('build/score.json')); failures = d['suites']['fuse']['details'].get('failures', []); has_c3 = any(f.startswith('c3:') for f in failures); print(f'failure for case c3 present: {has_c3}')"
```

Expected: `False`.

- [ ] **Step 5: Tag**

```bash
git tag -a m1-phase02-4 -m "M1 Phase 2.4 complete: jumps, calls, returns

42 new opcode positions across 11 Op classes covering JP/JR/DJNZ/CALL/
RET/RST and their conditional variants. None affect flags. Foundation:
Condition enum (8 codes), Cpu.push/Cpu.pop centralizing Z80 stack
semantics. Control flow now possible — fib10 program becomes runnable
once it lands in Phase 2.11.

Plan 2.5 (PUSH/POP rr) is the next batch."
```

This plan is complete.

---

## Self-Review

1. **Spec coverage:** Every section maps to a task. Foundation → 2.4-1. JP family → 2.4-2. JR + DJNZ → 2.4-3. CALL family → 2.4-4. RET + RST → 2.4-5. Sweep + tag → 2.4-6.

2. **Placeholder scan:** No "TBD" / "TODO". Each Op has its concrete code shown.

3. **Type consistency:** `Condition.test(cpu)` / `Condition.fromBits(bits)` consistent. `cpu.push(mem, value)` / `cpu.pop(mem): Int` consistent. PC advance: ops set `cpu.pc` directly (jumping) or `(pc + N) and 0xFFFF` (falling through).

4. **Critical assertions verified per Op class:**
   - All Ops have explicit "f preserved" tests.
   - Conditional ops have BOTH taken and not-taken T-state tests.
   - CALL/RST tests verify the exact pushed return address (pc+3 for CALL, pc+1 for RST).
   - JpHl test specifically pokes memory at HL to ensure we're using HL value, not dereferencing.
   - JR/DJNZ tests cover negative displacement and PC wrap.
   - RST validates target argument in init.
   - JrRelCc validates condition argument in init (only NZ/Z/NC/C valid).

5. **Mnemonic format consistency:** `"JP nn"`, `"JP NZ, nn"`, `"JR e"`, `"DJNZ e"`, `"CALL Z, nn"`, `"RET M"`, `"RST 18H"`. Comma-then-space; uppercase mnemonic.
