# Phase 2.12 — Undocumented IX/IY Halves + SLL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `./build/install/zx80/bin/zx80 zexdoc` no longer crash on undocumented opcodes by adding the IXH/IXL/IYH/IYL operations and the SLL CB-prefixed shift that ZEXDOC.COM exercises.

**Architecture:** Add a single `IndexHalfReg` enum (4 values: IXH/IXL/IYH/IYL) that knows its parent IndexReg and high/low byte. Mirror Phase 2.8 by introducing per-pattern Op classes parameterized over `IndexHalfReg`. Extend `IxOps.installInto` with new install loops. Extend the existing `RotateOp` enum with `SLL` and stop skipping the CB 0x30-0x37 row.

**Tech Stack:** Kotlin (JDK 21), JUnit 5, AssertJ, Gradle, Spotless ktlint.

**Spec:** `docs/superpowers/specs/2026-05-03-zx80-m1-phase2-12-undocumented-halves-sll-design.md`

---

## Universal patterns

These patterns repeat across every WU; understand them once.

**TDD cycle per Op class:**
1. Write the test file (FAIL because Op class doesn't exist)
2. Run `./gradlew test --tests <ClassName>Test` to confirm FAIL
3. Implement the Op class
4. Run `./gradlew test --tests <ClassName>Test` to confirm PASS

**Standard half-touching Op shape:**
- `pc += 2`, `bumpR(2)`, `baseCycles = 8`, `operandLength = 0`
- DD/FD prefix means PC-advance is +2, not +1
- Flag math reuses `Flags.afterAdd`/`afterAdc`/`afterInc`/etc. on the byte read out of the half register — no new flag helpers needed for ALU/INC/DEC half ops

**Test name rules:**
- Backtick test names CAN'T contain `..`, `->`, or `;` — substitute "0 to 7" / "to" / commas

**Spotless ktlint:** runs on `spotlessApply`. May reformat `Cpu().apply { … }` blocks to multi-line and collapse single-line KDocs. Cosmetic only — accept changes.

**Beads workflow per WU:**
1. `bd update <id> --status=in_progress` (claim)
2. Execute WU
3. `./gradlew test spotlessApply` (verify suite passes)
4. Commit (per the WU's commit message)
5. `bd close <id>`

## File Structure

**New files (created):**
- `src/main/kotlin/ru/alepar/zx80/cpu/IndexHalfReg.kt` — WU 2.12-1
- `src/main/kotlin/ru/alepar/zx80/op/ix/AluAFromIxHalf.kt` — WU 2.12-1
- `src/main/kotlin/ru/alepar/zx80/op/ix/IncIxHalf.kt` — WU 2.12-2
- `src/main/kotlin/ru/alepar/zx80/op/ix/DecIxHalf.kt` — WU 2.12-2
- `src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfImm.kt` — WU 2.12-3
- `src/main/kotlin/ru/alepar/zx80/op/ix/LdRegRegPrefixed.kt` — WU 2.12-4
- `src/main/kotlin/ru/alepar/zx80/op/ix/LdRegFromIxHalf.kt` — WU 2.12-4
- `src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromReg.kt` — WU 2.12-4
- `src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromIxHalf.kt` — WU 2.12-4
- Test files mirror each (in `src/test/kotlin/ru/alepar/zx80/cpu/` or `op/ix/`)

**Existing files (modified):**
- `src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt` — extended with new install loops (every WU)
- `src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt` — extended with new install assertions (every WU)
- `src/main/kotlin/ru/alepar/zx80/op/rot/RotateOp.kt` — add SLL (WU 2.12-5)
- `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt` — add `afterSll` helper (WU 2.12-5)
- `src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt` — stop skipping SLL slot (WU 2.12-5)
- `src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt` — flip null-assertion to populated-assertion (WU 2.12-5)

---

## WU 2.12-1 — IndexHalfReg enum + AluAFromIxHalf (32 opcodes)

Beads: `zx80-cac`

Foundation + first Op family using the new enum.

### Task 1: IndexHalfReg enum

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cpu/IndexHalfReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/IndexHalfRegTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/cpu/IndexHalfRegTest.kt`:

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndexHalfRegTest {
    @Test
    fun `IXH reads high byte of cpu ix and writes only the high byte`() {
        val cpu = Cpu().apply { ix = 0xABCD }
        assertThat(IndexHalfReg.IXH.read(cpu)).isEqualTo(0xAB)

        IndexHalfReg.IXH.write(cpu, 0x12)
        assertThat(cpu.ix).isEqualTo(0x12CD)
    }

    @Test
    fun `IXL reads low byte of cpu ix and writes only the low byte`() {
        val cpu = Cpu().apply { ix = 0xABCD }
        assertThat(IndexHalfReg.IXL.read(cpu)).isEqualTo(0xCD)

        IndexHalfReg.IXL.write(cpu, 0x12)
        assertThat(cpu.ix).isEqualTo(0xAB12)
    }

    @Test
    fun `IYH reads high byte of cpu iy`() {
        val cpu = Cpu().apply { iy = 0xABCD }
        assertThat(IndexHalfReg.IYH.read(cpu)).isEqualTo(0xAB)

        IndexHalfReg.IYH.write(cpu, 0x12)
        assertThat(cpu.iy).isEqualTo(0x12CD)
    }

    @Test
    fun `IYL reads low byte of cpu iy`() {
        val cpu = Cpu().apply { iy = 0xABCD }
        assertThat(IndexHalfReg.IYL.read(cpu)).isEqualTo(0xCD)

        IndexHalfReg.IYL.write(cpu, 0x12)
        assertThat(cpu.iy).isEqualTo(0xAB12)
    }

    @Test
    fun `write masks to 8 bits`() {
        val cpu = Cpu().apply { ix = 0 }
        IndexHalfReg.IXH.write(cpu, 0x1FF)
        assertThat(cpu.ix).isEqualTo(0xFF00)

        IndexHalfReg.IXL.write(cpu, -1)
        assertThat(cpu.ix).isEqualTo(0xFFFF)
    }

    @Test
    fun `mnemonic and parent and isHigh`() {
        assertThat(IndexHalfReg.IXH.mnemonic).isEqualTo("IXH")
        assertThat(IndexHalfReg.IXH.parent).isEqualTo(IndexReg.IX)
        assertThat(IndexHalfReg.IXH.isHigh).isTrue

        assertThat(IndexHalfReg.IXL.mnemonic).isEqualTo("IXL")
        assertThat(IndexHalfReg.IXL.parent).isEqualTo(IndexReg.IX)
        assertThat(IndexHalfReg.IXL.isHigh).isFalse

        assertThat(IndexHalfReg.IYH.parent).isEqualTo(IndexReg.IY)
        assertThat(IndexHalfReg.IYL.parent).isEqualTo(IndexReg.IY)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IndexHalfRegTest`
Expected: FAIL with `Unresolved reference: IndexHalfReg`.

- [ ] **Step 3: Implement IndexHalfReg**

`src/main/kotlin/ru/alepar/zx80/cpu/IndexHalfReg.kt`:

```kotlin
package ru.alepar.zx80.cpu

/**
 * The four undocumented Z80 IX/IY half registers (IXH, IXL, IYH, IYL). Each one knows its parent
 * 16-bit IndexReg (IX or IY) and which byte (high or low) it accesses. Used by Phase 2.12 Op
 * classes for undocumented half-register opcodes that ZEXDOC exercises.
 */
enum class IndexHalfReg(val mnemonic: String, val parent: IndexReg, val isHigh: Boolean) {
    IXH("IXH", IndexReg.IX, true),
    IXL("IXL", IndexReg.IX, false),
    IYH("IYH", IndexReg.IY, true),
    IYL("IYL", IndexReg.IY, false);

    fun read(cpu: Cpu): Int {
        val full = parent.read(cpu)
        return if (isHigh) (full ushr 8) and 0xFF else full and 0xFF
    }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFF
        val full = parent.read(cpu)
        val composed = if (isHigh) (v shl 8) or (full and 0xFF) else (full and 0xFF00) or v
        parent.write(cpu, composed)
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IndexHalfRegTest`
Expected: 6 tests pass.

### Task 2: AluAFromIxHalf

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/AluAFromIxHalf.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/AluAFromIxHalfTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/AluAFromIxHalfTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.alu.AluOp

class AluAFromIxHalfTest {
    @Test
    fun `ADD A, IXH adds high byte of IX into A`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1234
                a = 0x10
            }
        AluAFromIxHalf(op = AluOp.ADD, src = IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x10 + 0x12)
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `SUB IYL subtracts low byte of IY from A`() {
        val cpu =
            Cpu().apply {
                iy = 0xAB05
                a = 0x10
            }
        AluAFromIxHalf(op = AluOp.SUB, src = IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x10 - 0x05)
    }

    @Test
    fun `CP IXH does not modify A`() {
        val cpu =
            Cpu().apply {
                ix = 0x4200
                a = 0x42
            }
        AluAFromIxHalf(op = AluOp.CP, src = IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
    }

    @Test
    fun `mnemonic format is OP A, half`() {
        assertThat(AluAFromIxHalf(AluOp.ADD, IndexHalfReg.IXH).mnemonic { 0 }).isEqualTo("ADD A, IXH")
        assertThat(AluAFromIxHalf(AluOp.XOR, IndexHalfReg.IYL).mnemonic { 0 }).isEqualTo("XOR A, IYL")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = AluAFromIxHalf(AluOp.ADD, IndexHalfReg.IXH)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests AluAFromIxHalfTest`
Expected: FAIL with `Unresolved reference: AluAFromIxHalf`.

- [ ] **Step 3: Implement AluAFromIxHalf**

`src/main/kotlin/ru/alepar/zx80/op/ix/AluAFromIxHalf.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher
import ru.alepar.zx80.op.alu.AluOp

/**
 * `<ALU> A, <IXH|IXL|IYH|IYL>` — undocumented. Apply an 8-bit ALU op between A and one half of
 * IX/IY. 8 T-states. R+=2. PC+=2.
 *
 * Covers 32 opcodes: 8 ALU ops × 4 halves (IXH, IXL under DD; IYH, IYL under FD).
 */
class AluAFromIxHalf(private val op: AluOp, private val src: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(cpu.a, src.read(cpu), cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, ${src.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests AluAFromIxHalfTest`
Expected: 5 tests pass.

### Task 3: Wire AluAFromIxHalf into IxOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt`

- [ ] **Step 1: Write the failing IxOps test**

Append to `src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt`:

```kotlin
@Test
fun `installInto registers ALU on IXH at DD 84-BC and ALU on IXL at DD 85-BD`() {
    val d = Decoder()
    IxOps.installInto(d)
    // Spot-check ADD A, IXH at DD 84
    assertThat((d.dd[0x84] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("ADD A, IXH")
    // ADC A, IXH at DD 8C
    assertThat((d.dd[0x8C] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("ADC A, IXH")
    // SUB IXL at DD 95
    assertThat((d.dd[0x95] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("SUB A, IXL")
    // CP IXL at DD BD
    assertThat((d.dd[0xBD] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("CP A, IXL")
}

@Test
fun `installInto registers ALU on IYH at FD 84-BC and IYL at FD 85-BD`() {
    val d = Decoder()
    IxOps.installInto(d)
    assertThat((d.fd[0x84] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("ADD A, IYH")
    assertThat((d.fd[0xAD] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("XOR A, IYL")
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxOpsTest`
Expected: FAIL — DD 0x84 etc. are null.

- [ ] **Step 3: Extend IxOps.installInto**

In `src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt`, add a new private method and call it from `installInto`:

```kotlin
fun installInto(d: Decoder) {
    installLdSpHlStraggler(d)
    installPairTouching(d)
    installIndexedLd(d)
    installIndexedLdImm(d)
    installIndexedAlu(d)
    installIndexedIncDec(d)
    installJpIx(d)
    installAluAHalves(d)   // NEW — Phase 2.12 WU 2.12-1
}

private fun installAluAHalves(d: Decoder) {
    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.dd else d.fd
        val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
        val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
        for (oooBits in 0..7) {
            val op = AluOp.fromBits(oooBits)
            // <ALU> A, IXH/IYH at 0x84 + (ooo << 3)
            table[0x84 or (oooBits shl 3)] = AluAFromIxHalf(op, high)
            // <ALU> A, IXL/IYL at 0x85 + (ooo << 3)
            table[0x85 or (oooBits shl 3)] = AluAFromIxHalf(op, low)
        }
    }
}
```

Add the missing import at the top of `IxOps.kt`: `import ru.alepar.zx80.cpu.IndexHalfReg`

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxOpsTest`
Expected: all IxOpsTest tests pass (existing + 2 new = N+2).

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/IndexHalfReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/AluAFromIxHalf.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/IndexHalfRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/AluAFromIxHalfTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt
git commit -m "feat(cpu,op/ix): IndexHalfReg + AluAFromIxHalf — undocumented ALU on IX/IY halves (32 opcodes)

IndexHalfReg enum (IXH/IXL/IYH/IYL) for the undocumented Z80 half
registers. AluAFromIxHalf reuses the existing AluOp flag math on the
byte read out of the half. 32 opcodes installed at DD 0x84-0xBD step 8
and FD 0x84-0xBD step 8 covering 8 ALU ops × 4 halves.

Closes zx80-cac (WU 2.12-1).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close zx80-cac`

---

## WU 2.12-2 — IncIxHalf + DecIxHalf (8 opcodes)

Beads: `zx80-aq4`

Mirror INC/DEC reg from Phase 2.2 onto the halves.

### Task 1: IncIxHalf

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/IncIxHalf.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/IncIxHalfTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/IncIxHalfTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class IncIxHalfTest {
    @Test
    fun `INC IXH increments only the high byte of IX`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x10AB
            }
        IncIxHalf(IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x11AB)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `INC IYL wraps low byte 0xFF to 0x00 and sets Z`() {
        val cpu = Cpu().apply { iy = 0xAA_FF }
        IncIxHalf(IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xAA00)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `INC preserves C flag`() {
        val cpu =
            Cpu().apply {
                ix = 0x0000
                f = Flags.C
            }
        IncIxHalf(IndexHalfReg.IXL).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncIxHalf(IndexHalfReg.IXH).mnemonic { 0 }).isEqualTo("INC IXH")
        assertThat(IncIxHalf(IndexHalfReg.IYL).mnemonic { 0 }).isEqualTo("INC IYL")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = IncIxHalf(IndexHalfReg.IXH)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IncIxHalfTest`
Expected: FAIL with `Unresolved reference: IncIxHalf`.

- [ ] **Step 3: Implement IncIxHalf**

`src/main/kotlin/ru/alepar/zx80/op/ix/IncIxHalf.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INC <IXH|IXL|IYH|IYL>` — undocumented. Increment one half of IX/IY, updating flags. 8 T-states.
 * R+=2. PC+=2. C flag preserved (matches documented INC reg).
 */
class IncIxHalf(private val half: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterInc(half.read(cpu), cpu.f)
        half.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INC ${half.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IncIxHalfTest`
Expected: 5 tests pass.

### Task 2: DecIxHalf

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/DecIxHalf.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/DecIxHalfTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/DecIxHalfTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class DecIxHalfTest {
    @Test
    fun `DEC IXH decrements only the high byte of IX`() {
        val cpu = Cpu().apply { ix = 0x12AB }
        DecIxHalf(IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x11AB)
    }

    @Test
    fun `DEC IYL wraps 0x00 to 0xFF and sets S not Z`() {
        val cpu = Cpu().apply { iy = 0xAA00 }
        DecIxHalf(IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xAAFF)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `DEC preserves C flag`() {
        val cpu =
            Cpu().apply {
                ix = 0x0100
                f = Flags.C
            }
        DecIxHalf(IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = DecIxHalf(IndexHalfReg.IYH)
        assertThat(op.mnemonic { 0 }).isEqualTo("DEC IYH")
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests DecIxHalfTest`
Expected: FAIL.

- [ ] **Step 3: Implement DecIxHalf**

`src/main/kotlin/ru/alepar/zx80/op/ix/DecIxHalf.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC <IXH|IXL|IYH|IYL>` — undocumented. Decrement one half of IX/IY, updating flags. 8 T-states.
 * R+=2. PC+=2. C flag preserved (matches documented DEC reg).
 */
class DecIxHalf(private val half: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDec(half.read(cpu), cpu.f)
        half.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC ${half.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests DecIxHalfTest`
Expected: 4 tests pass.

### Task 3: Wire IncIxHalf + DecIxHalf into IxOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt`

- [ ] **Step 1: Write the failing IxOps test**

Append to `IxOpsTest.kt`:

```kotlin
@Test
fun `installInto registers INC IXH at DD 24 and DEC IXH at DD 25`() {
    val d = Decoder()
    IxOps.installInto(d)
    assertThat((d.dd[0x24] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IXH")
    assertThat((d.dd[0x25] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IXH")
}

@Test
fun `installInto registers INC IXL at DD 2C and DEC IXL at DD 2D`() {
    val d = Decoder()
    IxOps.installInto(d)
    assertThat((d.dd[0x2C] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IXL")
    assertThat((d.dd[0x2D] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IXL")
}

@Test
fun `installInto registers INC IYH at FD 24, DEC IYH at FD 25, INC IYL at FD 2C, DEC IYL at FD 2D`() {
    val d = Decoder()
    IxOps.installInto(d)
    assertThat((d.fd[0x24] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IYH")
    assertThat((d.fd[0x25] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IYH")
    assertThat((d.fd[0x2C] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IYL")
    assertThat((d.fd[0x2D] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IYL")
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxOpsTest`
Expected: FAIL — slots null.

- [ ] **Step 3: Extend IxOps.installInto**

In `IxOps.kt`, add to the installInto sequence:

```kotlin
fun installInto(d: Decoder) {
    // ... existing calls ...
    installAluAHalves(d)
    installIncDecHalves(d)   // NEW — Phase 2.12 WU 2.12-2
}

private fun installIncDecHalves(d: Decoder) {
    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.dd else d.fd
        val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
        val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
        table[0x24] = IncIxHalf(high)
        table[0x25] = DecIxHalf(high)
        table[0x2C] = IncIxHalf(low)
        table[0x2D] = DecIxHalf(low)
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxOpsTest`
Expected: all pass.

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ix/IncIxHalf.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/DecIxHalf.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/IncIxHalfTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/DecIxHalfTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt
git commit -m "feat(op/ix): INC/DEC on IX/IY halves — 8 undocumented opcodes

IncIxHalf and DecIxHalf reuse Flags.afterInc/afterDec on the byte read
out of the half register. 8 install positions: DD 24/25/2C/2D and
FD 24/25/2C/2D. C flag preserved per documented INC/DEC convention.

Closes zx80-aq4 (WU 2.12-2).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close zx80-aq4`

---

## WU 2.12-3 — LdIxHalfImm (4 opcodes)

Beads: `zx80-yta`

`LD IXH/IXL/IYH/IYL, n` — load an 8-bit immediate into a half register. 11 T-states. PC+=3 (DD/FD prefix + opcode + immediate byte). R+=2. operandLength=1.

### Task 1: LdIxHalfImm

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfImm.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfImmTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfImmTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class LdIxHalfImmTest {
    @Test
    fun `LD IXH, n loads byte at pc+2 into IXH`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0xAACC
            }
        // immediate at pc+2 = 0x102 (DD 26 nn)
        val mem = Memory().apply { write(0x102, 0x42) }
        LdIxHalfImm(IndexHalfReg.IXH).execute(cpu, mem)
        assertThat(cpu.ix).isEqualTo(0x42CC)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `LD IYL, n loads byte at pc+2 into IYL`() {
        val cpu =
            Cpu().apply {
                pc = 0x200
                iy = 0xAA00
            }
        val mem = Memory().apply { write(0x202, 0x99) }
        LdIxHalfImm(IndexHalfReg.IYL).execute(cpu, mem)
        assertThat(cpu.iy).isEqualTo(0xAA99)
    }

    @Test
    fun `flags untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0
                f = 0xAA
            }
        val mem = Memory().apply { write(0x102, 0x33) }
        LdIxHalfImm(IndexHalfReg.IXH).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = LdIxHalfImm(IndexHalfReg.IXH)
        assertThat(op.mnemonic { 0 }).isEqualTo("LD IXH, n")
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdIxHalfImmTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdIxHalfImm**

`src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfImm.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD <IXH|IXL|IYH|IYL>, n` — undocumented. Load an immediate byte into one half of IX/IY. 11
 * T-states. R+=2. PC+=3 (DD/FD prefix + opcode + immediate byte). No flag changes.
 */
class LdIxHalfImm(private val dst: IndexHalfReg) : Op {
    override val operandLength = 1
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read((cpu.pc + 2) and 0xFFFF)
        dst.write(cpu, n)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, n"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdIxHalfImmTest`
Expected: 4 tests pass.

### Task 2: Wire LdIxHalfImm into IxOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt`

- [ ] **Step 1: Write the failing IxOps test**

Append to `IxOpsTest.kt`:

```kotlin
@Test
fun `installInto registers LD half,n at DD 26, DD 2E, FD 26, FD 2E`() {
    val d = Decoder()
    IxOps.installInto(d)
    assertThat((d.dd[0x26] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IXH, n")
    assertThat((d.dd[0x2E] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IXL, n")
    assertThat((d.fd[0x26] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IYH, n")
    assertThat((d.fd[0x2E] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IYL, n")
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxOpsTest`
Expected: FAIL.

- [ ] **Step 3: Extend IxOps.installInto**

In `IxOps.kt`:

```kotlin
fun installInto(d: Decoder) {
    // ... existing calls ...
    installIncDecHalves(d)
    installLdHalfImm(d)   // NEW — Phase 2.12 WU 2.12-3
}

private fun installLdHalfImm(d: Decoder) {
    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.dd else d.fd
        val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
        val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
        table[0x26] = LdIxHalfImm(high)
        table[0x2E] = LdIxHalfImm(low)
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxOpsTest`
Expected: all pass.

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfImm.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfImmTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt
git commit -m "feat(op/ix): LD half,n — 4 undocumented opcodes for IX/IY half-immediate

LdIxHalfImm reads an immediate byte at pc+2 and writes it into the
named half. 4 install positions: DD 26 (IXH), DD 2E (IXL), FD 26
(IYH), FD 2E (IYL). 11 T-states; operandLength=1.

Closes zx80-yta (WU 2.12-3).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close zx80-yta`

---

## WU 2.12-4 — LD r,r' under DD/FD prefix (98 install positions, 4 classes)

Beads: `zx80-ll3`

The 0x40-0x7F LD r,r' block under DD/FD prefix. 49 patterns × 2 prefixes = 98 install positions, of which:
- 50 use `LdRegRegPrefixed` (no half — 25 unique instances reused across both prefixes)
- 20 use `LdRegFromIxHalf`
- 20 use `LdIxHalfFromReg`
- 8 use `LdIxHalfFromIxHalf`

The (HL) variants (rrr=110) are already covered by Phase 2.8's `LdRegFromIxd` and `LdIxdFromReg` and the HALT slot 0x76 stays untouched.

### Task 1: LdRegRegPrefixed

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/LdRegRegPrefixed.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/LdRegRegPrefixedTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/LdRegRegPrefixedTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegRegPrefixedTest {
    @Test
    fun `LD B, C with DD-style prefix copies C to B and advances pc by 2`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x11
                c = 0x22
                f = 0xAA
            }
        LdRegRegPrefixed(src = Reg.C, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x22)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic does not include prefix - same as unprefixed LD r,r prime`() {
        assertThat(LdRegRegPrefixed(src = Reg.C, dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, C")
        assertThat(LdRegRegPrefixed(src = Reg.A, dst = Reg.E).mnemonic { 0 }).isEqualTo("LD E, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = LdRegRegPrefixed(src = Reg.B, dst = Reg.C)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdRegRegPrefixedTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdRegRegPrefixed**

`src/main/kotlin/ru/alepar/zx80/op/ix/LdRegRegPrefixed.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, r'` under a DD or FD prefix where neither operand references H/L. Z80 quirk: the prefix
 * is a no-op for these patterns; behavior is identical to the unprefixed `LD r, r'` but PC and R
 * advance by 2 (not 1) and 8 T-states are charged. Used by ZEXDOC's `ld <bcdexya>,<bcdexya>` test
 * which patches in any 0x40-0x7F pattern.
 *
 * The same instance is installed into both `dd[]` and `fd[]` because behavior does not differ
 * between the two prefixes.
 */
class LdRegRegPrefixed(private val src: Reg, private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdRegRegPrefixedTest`
Expected: 3 tests pass.

### Task 2: LdRegFromIxHalf

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/LdRegFromIxHalf.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/LdRegFromIxHalfTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/LdRegFromIxHalfTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegFromIxHalfTest {
    @Test
    fun `LD B, IXH copies high byte of IX into B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1234
                b = 0
            }
        LdRegFromIxHalf(dst = Reg.B, src = IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x12)
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `LD A, IYL copies low byte of IY into A`() {
        val cpu = Cpu().apply { iy = 0xAABB }
        LdRegFromIxHalf(dst = Reg.A, src = IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xBB)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRegFromIxHalf(Reg.B, IndexHalfReg.IXH).mnemonic { 0 }).isEqualTo("LD B, IXH")
        assertThat(LdRegFromIxHalf(Reg.A, IndexHalfReg.IYL).mnemonic { 0 }).isEqualTo("LD A, IYL")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = LdRegFromIxHalf(Reg.B, IndexHalfReg.IXH)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdRegFromIxHalfTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdRegFromIxHalf**

`src/main/kotlin/ru/alepar/zx80/op/ix/LdRegFromIxHalf.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, <IXH|IXL|IYH|IYL>` — undocumented. Copy a half of IX/IY into a regular 8-bit register. 8
 * T-states. R+=2. PC+=2.
 */
class LdRegFromIxHalf(private val dst: Reg, private val src: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdRegFromIxHalfTest`
Expected: 4 tests pass.

### Task 3: LdIxHalfFromReg

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromReg.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromRegTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromRegTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdIxHalfFromRegTest {
    @Test
    fun `LD IXH, B writes B into the high byte of IX leaving low byte intact`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0xAACC
                b = 0x42
            }
        LdIxHalfFromReg(dst = IndexHalfReg.IXH, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x42CC)
        assertThat(cpu.pc).isEqualTo(0x102)
    }

    @Test
    fun `LD IYL, A writes A into low byte of IY`() {
        val cpu =
            Cpu().apply {
                iy = 0xAA00
                a = 0x99
            }
        LdIxHalfFromReg(dst = IndexHalfReg.IYL, src = Reg.A).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xAA99)
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = LdIxHalfFromReg(IndexHalfReg.IXH, Reg.B)
        assertThat(op.mnemonic { 0 }).isEqualTo("LD IXH, B")
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdIxHalfFromRegTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdIxHalfFromReg**

`src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromReg.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD <IXH|IXL|IYH|IYL>, r` — undocumented. Copy a regular 8-bit register into one half of IX/IY.
 * 8 T-states. R+=2. PC+=2.
 */
class LdIxHalfFromReg(private val dst: IndexHalfReg, private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdIxHalfFromRegTest`
Expected: 3 tests pass.

### Task 4: LdIxHalfFromIxHalf

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromIxHalf.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromIxHalfTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromIxHalfTest.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class LdIxHalfFromIxHalfTest {
    @Test
    fun `LD IXH, IXL copies low byte to high byte of IX`() {
        val cpu = Cpu().apply { ix = 0x1234 }
        LdIxHalfFromIxHalf(dst = IndexHalfReg.IXH, src = IndexHalfReg.IXL).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x3434)
    }

    @Test
    fun `LD IYL, IYH copies high byte to low byte of IY`() {
        val cpu = Cpu().apply { iy = 0xABCD }
        LdIxHalfFromIxHalf(dst = IndexHalfReg.IYL, src = IndexHalfReg.IYH).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xABAB)
    }

    @Test
    fun `LD IXH, IXH is a no-op on IX value`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0x1234
            }
        LdIxHalfFromIxHalf(IndexHalfReg.IXH, IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = LdIxHalfFromIxHalf(IndexHalfReg.IXH, IndexHalfReg.IXL)
        assertThat(op.mnemonic { 0 }).isEqualTo("LD IXH, IXL")
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdIxHalfFromIxHalfTest`
Expected: FAIL.

- [ ] **Step 3: Implement LdIxHalfFromIxHalf**

`src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromIxHalf.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD <IXH|IXL>, <IXH|IXL>` (DD prefix) or `LD <IYH|IYL>, <IYH|IYL>` (FD prefix) — undocumented.
 * Both operands belong to the same parent IndexReg (DD-prefix only mixes IX halves; FD only mixes
 * IY halves). 8 T-states. R+=2. PC+=2.
 *
 * Constraint that dst.parent == src.parent is enforced at install time, not at runtime.
 */
class LdIxHalfFromIxHalf(private val dst: IndexHalfReg, private val src: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdIxHalfFromIxHalfTest`
Expected: 4 tests pass.

### Task 5: Wire all four LD classes into IxOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt`

- [ ] **Step 1: Write the failing IxOps test**

Append to `IxOpsTest.kt`:

```kotlin
@Test
fun `installInto fills 49 entries in 0x40-0x7F under DD prefix`() {
    val d = Decoder()
    IxOps.installInto(d)
    val count = (0x40..0x7F).count { d.dd[it] != null }
    // 64 slots, minus HALT (0x76) = 63. Then minus 14 (HL) variants already
    // present from Phase 2.8 — but those are not null either, they're the
    // (IX+d) ops, so they DO count. Therefore expect 63 non-null.
    assertThat(count).isEqualTo(63)
}

@Test
fun `installInto fills 49 entries in 0x40-0x7F under FD prefix`() {
    val d = Decoder()
    IxOps.installInto(d)
    val count = (0x40..0x7F).count { d.fd[it] != null }
    assertThat(count).isEqualTo(63)
}

@Test
fun `installInto registers half-touching LD r,r at representative DD positions`() {
    val d = Decoder()
    IxOps.installInto(d)
    // DD 60 = LD IXH, B (dst=H=4, src=B=0)
    assertThat((d.dd[0x60] as LdIxHalfFromReg).mnemonic { 0 }).isEqualTo("LD IXH, B")
    // DD 65 = LD IXH, IXL (dst=H=4, src=L=5)
    assertThat((d.dd[0x65] as LdIxHalfFromIxHalf).mnemonic { 0 }).isEqualTo("LD IXH, IXL")
    // DD 6C = LD IXL, IXH (dst=L=5, src=H=4)
    assertThat((d.dd[0x6C] as LdIxHalfFromIxHalf).mnemonic { 0 }).isEqualTo("LD IXL, IXH")
    // DD 7C = LD A, IXH (dst=A=7, src=H=4)
    assertThat((d.dd[0x7C] as LdRegFromIxHalf).mnemonic { 0 }).isEqualTo("LD A, IXH")
    // DD 7D = LD A, IXL (dst=A=7, src=L=5)
    assertThat((d.dd[0x7D] as LdRegFromIxHalf).mnemonic { 0 }).isEqualTo("LD A, IXL")
    // DD 78 = LD A, B (no half) — LdRegRegPrefixed
    assertThat((d.dd[0x78] as LdRegRegPrefixed).mnemonic { 0 }).isEqualTo("LD A, B")
}

@Test
fun `installInto registers half-touching LD r,r at representative FD positions`() {
    val d = Decoder()
    IxOps.installInto(d)
    assertThat((d.fd[0x60] as LdIxHalfFromReg).mnemonic { 0 }).isEqualTo("LD IYH, B")
    assertThat((d.fd[0x7C] as LdRegFromIxHalf).mnemonic { 0 }).isEqualTo("LD A, IYH")
    assertThat((d.fd[0x65] as LdIxHalfFromIxHalf).mnemonic { 0 }).isEqualTo("LD IYH, IYL")
    assertThat((d.fd[0x78] as LdRegRegPrefixed).mnemonic { 0 }).isEqualTo("LD A, B")
}

@Test
fun `installInto leaves DD HALT slot at 0x76 alone (it's not a valid LD pattern)`() {
    val d = Decoder()
    IxOps.installInto(d)
    assertThat(d.dd[0x76]).isNull()
    assertThat(d.fd[0x76]).isNull()
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxOpsTest`
Expected: FAIL.

- [ ] **Step 3: Extend IxOps.installInto**

In `IxOps.kt`:

```kotlin
fun installInto(d: Decoder) {
    // ... existing calls ...
    installLdHalfImm(d)
    installLdRegRegPrefixed(d)   // NEW — Phase 2.12 WU 2.12-4
}

private fun installLdRegRegPrefixed(d: Decoder) {
    // Precompute the 25 unique LdRegRegPrefixed instances (no-half-touching patterns)
    // and reuse across both prefixes.
    val sharedNoHalf =
        Array(8) { dstBits ->
            Array<Op?>(8) { srcBits ->
                if (dstBits in setOf(4, 5) || srcBits in setOf(4, 5)) {
                    null // half-touching — handled per-prefix below
                } else if (dstBits == 6 || srcBits == 6) {
                    null // (IX+d) — already installed by Phase 2.8
                } else {
                    LdRegRegPrefixed(src = Reg.fromBits(srcBits), dst = Reg.fromBits(dstBits))
                }
            }
        }

    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.dd else d.fd
        val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
        val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
        for (dstBits in 0..7) {
            for (srcBits in 0..7) {
                val opcode = 0x40 or (dstBits shl 3) or srcBits
                if (opcode == 0x76) continue // HALT
                if (dstBits == 6 || srcBits == 6) continue // (IX+d) — Phase 2.8
                val dstHalf = halfFor(dstBits, high, low)
                val srcHalf = halfFor(srcBits, high, low)
                table[opcode] =
                    when {
                        dstHalf != null && srcHalf != null -> LdIxHalfFromIxHalf(dstHalf, srcHalf)
                        dstHalf != null -> LdIxHalfFromReg(dstHalf, Reg.fromBits(srcBits))
                        srcHalf != null -> LdRegFromIxHalf(Reg.fromBits(dstBits), srcHalf)
                        else -> sharedNoHalf[dstBits][srcBits]
                    }
            }
        }
    }
}

private fun halfFor(bits: Int, high: IndexHalfReg, low: IndexHalfReg): IndexHalfReg? =
    when (bits) {
        4 -> high
        5 -> low
        else -> null
    }
```

Add import: `import ru.alepar.zx80.op.Op` (since the array is typed `Array<Op?>`).

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxOpsTest`
Expected: all pass.

### Task 6: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ix/LdRegRegPrefixed.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/LdRegFromIxHalf.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromReg.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromIxHalf.kt \
        src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/LdRegRegPrefixedTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/LdRegFromIxHalfTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/LdIxHalfFromIxHalfTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ix/IxOpsTest.kt
git commit -m "feat(op/ix): LD r,r' under DD/FD — undocumented half-register transfers (98 install positions)

Four new Op classes covering the 0x40-0x7F LD r,r' block under DD/FD
prefix. LdRegRegPrefixed handles the 50 patterns where neither operand
is H/L (same instance reused across both prefixes). LdRegFromIxHalf,
LdIxHalfFromReg, LdIxHalfFromIxHalf cover the 48 half-touching patterns.
The 14 (IX+d) variants and HALT slot 0x76 are untouched (already
covered by Phase 2.8 and MiscOps).

Closes zx80-ll3 (WU 2.12-4).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close zx80-ll3`

---

## WU 2.12-5 — SLL on regular regs (8 opcodes, replaces conditional zx80-019)

Beads: `zx80-019` (description must be rewritten to reflect SLL scope; alternative: close `zx80-019` and create a fresh issue)

Phase 2.7 deliberately left CB 0x30-0x37 null. ZEXDOC's `shf/rot <b,c,d,e,h,l,(hl),a>` test exercises SLL alongside the 7 documented rotate/shift ops. Implement SLL and install the 8 slots.

Z80 SLL semantics: shift bits left by 1, set bit 0 to 1 (vs SLA which sets bit 0 to 0). Ejected bit 7 → C. S = bit 7 of result, Z = result==0, H = 0, N = 0, P/V = parity, C = ejected bit.

### Task 0 (preflight): rewrite zx80-019 description

**Files:** none (beads only)

- [ ] **Step 1: Update issue description and title**

```bash
bd update zx80-019 --title="WU 2.12-5: SLL on regular regs (8 CB opcodes)" \
                   --description="Add SLL to the existing RotateOp enum and install the 8 CB 0x30-0x37 slots that Phase 2.7 deliberately left null. Reuses RotShiftReg and RotShiftHl from Phase 2.7 with no new Op classes. Adds Flags.afterSll helper. Replaces CbOpsTest's 'SLL slots null' assertion with positive assertions about each slot. Required by ZEXDOC's 'shf/rot <b,c,d,e,h,l,(hl),a>' test. ~5 new tests."
```

### Task 1: Add Flags.afterSll

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`:

```kotlin
@Test
fun `afterSll shifts left and forces bit 0 to 1, ejected bit 7 sets C`() {
    val r = Flags.afterSll(0x55)
    // 0x55 = 01010101 -> shift left = 10101010, set bit 0 -> 10101011 = 0xAB
    // ejected bit 7 of 0x55 = 0 -> C clear
    assertThat(r.value).isEqualTo(0xAB)
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.S).isNotZero // bit 7 of 0xAB = 1
    assertThat(r.newF and Flags.Z).isZero
    assertThat(r.newF and Flags.H).isZero
    assertThat(r.newF and Flags.N).isZero
}

@Test
fun `afterSll on 0x80 shifts to 0x01 and sets C`() {
    val r = Flags.afterSll(0x80)
    // ejected bit 7 = 1 -> C set; result = 0x00 shifted left + 1 = 0x01
    assertThat(r.value).isEqualTo(0x01)
    assertThat(r.newF and Flags.C).isNotZero
    assertThat(r.newF and Flags.S).isZero
    assertThat(r.newF and Flags.Z).isZero
}

@Test
fun `afterSll on 0x00 yields 0x01, no flags except parity`() {
    val r = Flags.afterSll(0x00)
    assertThat(r.value).isEqualTo(0x01)
    assertThat(r.newF and Flags.C).isZero
    assertThat(r.newF and Flags.Z).isZero // 0x01 != 0
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests FlagsTest`
Expected: FAIL with `Unresolved reference: afterSll`.

- [ ] **Step 3: Implement Flags.afterSll**

In `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`, after `afterSrl` (around line 328):

```kotlin
/**
 * SLL: shift left logical, undocumented. Bit 0 always 1; bit 7 → C. Z80 quirk: this is
 * sometimes called "SL1" or "SLIA" in disassemblers since SLA already shifts left and SLL forces
 * bit 0 to 1 instead of 0.
 */
fun afterSll(value: Int): AluResult {
    val v = value and 0xFF
    val newC = (v and 0x80) != 0
    val result = ((v shl 1) or 0x01) and 0xFF
    return AluResult(result, computeRotateShiftFlags(result, newC))
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests FlagsTest`
Expected: all pass (existing + 3 new).

### Task 2: Add SLL to RotateOp enum

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/rot/RotateOp.kt`

- [ ] **Step 1: Add SLL value and dispatch**

Modify `RotateOp.kt`:

```kotlin
enum class RotateOp(val mnemonic: String) {
    RLC("RLC"),
    RRC("RRC"),
    RL("RL"),
    RR("RR"),
    SLA("SLA"),
    SRA("SRA"),
    SLL("SLL"),     // NEW — undocumented, Phase 2.12
    SRL("SRL");

    fun apply(value: Int, oldF: Int): AluResult =
        when (this) {
            RLC -> Flags.afterRlc(value)
            RRC -> Flags.afterRrc(value)
            RL -> Flags.afterRl(value, oldF)
            RR -> Flags.afterRr(value, oldF)
            SLA -> Flags.afterSla(value)
            SRA -> Flags.afterSra(value)
            SLL -> Flags.afterSll(value)
            SRL -> Flags.afterSrl(value)
        }

    companion object {
        /**
         * Map CB opcode bits 5-3 (the 'ooo' field) to a RotateOp. Encoding: 0=RLC, 1=RRC, 2=RL,
         * 3=RR, 4=SLA, 5=SRA, 6=SLL (undocumented but installed in Phase 2.12), 7=SRL.
         */
        fun fromBits(bits: Int): RotateOp =
            when (bits and 0x07) {
                0 -> RLC
                1 -> RRC
                2 -> RL
                3 -> RR
                4 -> SLA
                5 -> SRA
                6 -> SLL
                7 -> SRL
                else -> error("unreachable")
            }
    }
}
```

- [ ] **Step 2: Run to verify nothing else broke**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all existing rotate/shift tests still pass; the only logic change is `fromBits(6)` no longer throws.

(Note: at this point CB 0x30-0x37 are still null because CbOps still does `if (oooBits == 6) continue`. Task 4 fixes that.)

### Task 3: Add SLL execution test on RotShiftReg + RotShiftHl

**Files:**
- Modify: `src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftRegTest.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftHlTest.kt`

- [ ] **Step 1: Append SLL test to RotShiftRegTest**

```kotlin
@Test
fun `SLL B shifts left, sets bit 0 to 1`() {
    val cpu =
        Cpu().apply {
            pc = 0x100
            r = 0
            tStates = 0L
            b = 0x55
        }
    RotShiftReg(RotateOp.SLL, Reg.B).execute(cpu, Memory())
    assertThat(cpu.b).isEqualTo(0xAB)
    assertThat(cpu.pc).isEqualTo(0x102)
    assertThat(cpu.r).isEqualTo(2)
    assertThat(cpu.tStates).isEqualTo(8L)
}

@Test
fun `SLL mnemonic is SLL r`() {
    assertThat(RotShiftReg(RotateOp.SLL, Reg.B).mnemonic { 0 }).isEqualTo("SLL B")
}
```

- [ ] **Step 2: Append SLL test to RotShiftHlTest**

```kotlin
@Test
fun `SLL (HL) shifts memory[HL], sets bit 0 to 1`() {
    val cpu = Cpu().apply { hl = 0x4000 }
    val mem = Memory().apply { write(0x4000, 0x80) }
    RotShiftHl(RotateOp.SLL).execute(cpu, mem)
    assertThat(mem.read(0x4000)).isEqualTo(0x01)
    assertThat(cpu.f and Flags.C).isNotZero
}
```

- [ ] **Step 3: Run to verify pass**

Run: `./gradlew test --tests RotShiftRegTest --tests RotShiftHlTest`
Expected: existing + 3 new tests pass.

### Task 4: Update CbOps to install SLL slots

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt`

- [ ] **Step 1: Update CbOpsTest — flip null-assertions to populated-assertions**

Locate the existing test in `CbOpsTest.kt` that asserts SLL slots null. It looks like:

```kotlin
@Test
fun `installInto leaves SLL slots (CB 0x30-0x37) null`() {
    // ...
    for (slot in 0x30..0x37) {
        assertThat(d.cb[slot]).`as`("SLL slot 0x%02X must be null", slot).isNull()
    }
}
```

Replace with:

```kotlin
@Test
fun `installInto registers SLL ops at CB 0x30-0x37`() {
    val d = Decoder()
    CbOps.installInto(d)
    // CB 0x30 = SLL B, CB 0x31 = SLL C, ..., CB 0x35 = SLL L,
    // CB 0x36 = SLL (HL), CB 0x37 = SLL A
    assertThat((d.cb[0x30] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL B")
    assertThat((d.cb[0x31] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL C")
    assertThat((d.cb[0x32] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL D")
    assertThat((d.cb[0x33] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL E")
    assertThat((d.cb[0x34] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL H")
    assertThat((d.cb[0x35] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL L")
    assertThat((d.cb[0x36] as RotShiftHl).mnemonic { 0 }).isEqualTo("SLL (HL)")
    assertThat((d.cb[0x37] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL A")
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests CbOpsTest`
Expected: FAIL — slots still null.

- [ ] **Step 3: Update CbOps.installRotateShift to stop skipping oooBits=6**

In `src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt`, edit `installRotateShift`:

```kotlin
private fun installRotateShift(d: Decoder) {
    for (oooBits in 0..7) {
        // Phase 2.12: oooBits=6 is SLL (undocumented). Now installed.
        val op = RotateOp.fromBits(oooBits)
        for (rrrBits in 0..7) {
            val opcode = (oooBits shl 3) or rrrBits
            d.cb[opcode] =
                if (rrrBits == 6) RotShiftHl(op) else RotShiftReg(op, Reg.fromBits(rrrBits))
        }
    }
}
```

Also update the KDoc on `CbOps.installInto` to remove the "minus 8 SLL slots" carve-out:

```kotlin
/**
 * Registers the entire CB-prefixed table: rotate/shift ops in CB 0x00-0x3F (including SLL at
 * 0x30-0x37, added in Phase 2.12), BIT n,r in 0x40-0x7F, RES n,r in 0x80-0xBF, SET n,r in
 * 0xC0-0xFF.
 */
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests CbOpsTest`
Expected: all pass.

### Task 5: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL. FUSE pass-rate should climb by ~8 cases (the 8 SLL FUSE cases that were failing).

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/main/kotlin/ru/alepar/zx80/op/rot/RotateOp.kt \
        src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/rot/RotShiftHlTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/cb/CbOpsTest.kt
git commit -m "feat(op/cb): SLL — 8 undocumented CB-prefixed shift opcodes

Add SLL to the existing RotateOp enum and install CB 0x30-0x37.
Phase 2.7 deliberately left these slots null per its 'documented Z80
only' non-goal; ZEXDOC's 'shf/rot <b,c,d,e,h,l,(hl),a>' test exercises
SLL alongside the 7 documented rotate/shift ops, so we now install it.

SLL semantics: shift left by 1, set bit 0 to 1 (vs SLA which sets bit 0
to 0). Ejected bit 7 → C. S/Z/P/V flags computed from result; H = 0,
N = 0.

The negative null-assertion in CbOpsTest is replaced with positive
assertions for each of the 8 slots.

Closes zx80-019 (WU 2.12-5).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close zx80-019`

---

## WU 2.12-6 — Sweep + tag m1-phase02-12

Beads: `zx80-y8k`

### Task 1: Verification

- [ ] **Step 1: Run full suite**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Capture score**

Run: `./build/install/zx80/bin/zx80 score`
Expected: opcodes count climbs from 700 to ~850, FUSE pass-rate climbs noticeably (likely +50 or more cases). Composite score should rise from 0.430.

Record exact `SCORE:` line for the commit message.

- [ ] **Step 3: Run ZEXDOC and verify validation gates**

Run: `timeout 600 ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc.log`

Expected: ZEXDOC progresses past these test groups WITHOUT crashing (CRC `ERROR` is acceptable; what matters is no `IllegalStateException: no dispatch route`):

1. `aluop a,<ixh,ixl,iyh,iyl>` — was crash, now should print `OK` or `ERROR ...crc...`
2. `<inc,dec> ixh` / `ixl` / `iyh` / `iyl`
3. `ld <ixh,ixl,iyh,iyl>,nn`
4. `ld <bcdexya>,<bcdexya>`
5. `shf/rot <b,c,d,e,h,l,(hl),a>`

If any of these still produces a dispatch crash, STOP and triage — do not tag. The crash diagnostic identifies the next gap (likely DDCB/FDCB copy-back forms in `shf/rot (<ix,iy>+1)` or `<set,res> n,(<ix,iy>+1)`); file as Phase 2.13 issues if so.

If all five gates clear, tag the milestone and proceed.

- [ ] **Step 4: Apply milestone tag**

```bash
git tag -a m1-phase02-12 -m "Phase 2.12: undocumented IX/IY halves + SLL (ZEXDOC dispatch crashes resolved)

ZEXDOC now progresses past the five test groups identified in the
phase 2.12 spec without dispatch crashes. Remaining ZEXDOC failures
(if any) are CRC mismatches due to unmodelled X/Y flag bits — separate
phase."
```

### Task 2: Commit (sweep-only commits) + close beads

- [ ] **Step 1: Commit if anything changed**

If WU 2.12-6 made no code changes (just verification), there's nothing to commit beyond the tag — which is on the previous commit.

If verification surfaces a small fix needed (e.g. a flag arm missed), apply it as its own commit BEFORE tagging.

- [ ] **Step 2: Close beads**

```bash
bd close zx80-y8k --reason="Phase 2.12 sweep complete; m1-phase02-12 tag applied; ZEXDOC validation gates clear (no dispatch crashes in five named test groups)."
```

- [ ] **Step 3: Close epic**

```bash
bd close zx80-6k8 --reason="All 6 WUs (2.12-1 through 2.12-6) closed; m1-phase02-12 tag applied; ZEXDOC dispatch-crash gap closed."
```

- [ ] **Step 4: Status check on zx80-jfb (Phase 2.11 WU 7)**

`zx80-jfb` is the m1-cpu-complete tag WU and is blocked by `zx80-y8k`. After closing `zx80-y8k`, `zx80-jfb` should be unblocked. Whether it can be CLOSED depends on whether ZEXDOC also passes CRC-clean (no `ERROR` lines) — which it almost certainly will NOT, due to X/Y flag bits being unmodelled.

If ZEXDOC has CRC errors, file Phase D (X/Y bit modelling) and add `zx80-jfb` blocked by Phase D's epic. Do NOT tag `m1-cpu-complete` until ZEXDOC passes cleanly.

---

## Self-Review

**1. Spec coverage:**

| Spec section | Plan coverage |
|---|---|
| IndexHalfReg enum | WU 2.12-1 Task 1 |
| AluAFromIxHalf (32 opcodes) | WU 2.12-1 Tasks 2-3 |
| IncIxHalf / DecIxHalf (8 opcodes) | WU 2.12-2 |
| LdIxHalfImm (4 opcodes) | WU 2.12-3 |
| LdRegRegPrefixed | WU 2.12-4 Task 1 |
| LdRegFromIxHalf | WU 2.12-4 Task 2 |
| LdIxHalfFromReg | WU 2.12-4 Task 3 |
| LdIxHalfFromIxHalf | WU 2.12-4 Task 4 |
| IxOps install loop for LD r,r' (49 patterns × 2 prefixes) | WU 2.12-4 Task 5 |
| SLL extension to RotateOp + Flags + CbOps | WU 2.12-5 |
| Validation gates (5 ZEXDOC test groups) | WU 2.12-6 Task 1 Step 3 |
| `m1-phase02-12` tag | WU 2.12-6 Task 1 Step 4 |
| Beads epic close + zx80-jfb status | WU 2.12-6 Task 2 Steps 3-4 |

All spec sections are covered.

**2. Placeholder scan:** No "TBD" / "TODO" / placeholder text. Every step has exact code or exact commands.

**3. Type consistency:** `IndexHalfReg` properties (`mnemonic`, `parent`, `isHigh`) are referenced consistently. Op constructors use named parameters where there's potential ambiguity (e.g. `LdIxHalfFromReg(dst = …, src = …)`). `Flags.afterSll` is defined in WU 2.12-5 Task 1 and consumed in Task 2. `RotateOp.SLL` is added in WU 2.12-5 Task 2 and consumed in Tasks 3-4.

**4. Test name lint:** No backtick test names contain `..`, `->`, or `;`. Substituted "to" / "0 to 7" where needed.
