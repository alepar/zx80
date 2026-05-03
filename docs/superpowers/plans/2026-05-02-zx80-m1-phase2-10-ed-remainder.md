# ZX Spectrum Emulator — Phase 2.10 Implementation Plan (ED remainder + I/O + Trap B fix)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the documented Z80 ISA. ~51 ED-prefixed + main-table-straggler opcodes across ~28 Op classes. Plus two harness foundations: `IoBus` abstraction and the Trap B fix to FuseSuite.

**Architecture:** New `IoBus` interface with `NoIoBus` default singleton; `Cpu.io: IoBus` field. I/O Op classes use `cpu.io.read/write`. Trap B fix in `FuseSuite.runOne`: loop until `cpu.tStates >= startTStates + tStatesToRun` instead of executing exactly one instruction. Block ops (LDIR/LDDR/CPIR/CPDR/INIR/INDR/OTIR/OTDR) implement one iteration per `execute()` call; if BC ≠ 0 (or the comparison hasn't matched) they leave PC unchanged so the next dispatch re-fires the same op.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-10-ed-remainder-design.md`
**Branch:** `opus-4.7`
**Base commit:** spec commit. Phase 2.9 must complete first.

---

## Universal patterns

- All ED-prefixed ops: `cpu.bumpR(2)`, PC advance varies (most +2, some +4).
- Block ops with loop semantics: leave PC unchanged when BC ≠ 0 (so next dispatch re-fires); advance PC by 2 on exit.
- I/O ops use `cpu.io.read(port)` / `cpu.io.write(port, value)` where port is 16-bit.
- Each ED Op installs at `decoder.ed[opcode]`. Main-table I/O stragglers (IN A,(n) at 0xDB, OUT (n),A at 0xD3) install at `decoder.main`.

---

## File Structure

**New files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/
  IoBus.kt                     # WU 2.10-1
src/main/kotlin/ru/alepar/zx80/op/ed/
  EdOps.kt                     # WU 2.10-1 (skeleton), extended in 2-6
  LdIA.kt                      # WU 2.10-2
  LdRA.kt                      # WU 2.10-2
  LdAI.kt                      # WU 2.10-2
  LdAR.kt                      # WU 2.10-2
  Neg.kt                       # WU 2.10-2
  Reti.kt                      # WU 2.10-2
  Retn.kt                      # WU 2.10-2
  Rrd.kt                       # WU 2.10-2
  Rld.kt                       # WU 2.10-2
  LdAddrFromPair.kt            # WU 2.10-3
  LdPairFromAddr.kt            # WU 2.10-3
  Ldi.kt                       # WU 2.10-4
  Ldd.kt                       # WU 2.10-4
  Ldir.kt                      # WU 2.10-4
  Lddr.kt                      # WU 2.10-4
  Cpi.kt                       # WU 2.10-5
  Cpd.kt                       # WU 2.10-5
  Cpir.kt                      # WU 2.10-5
  Cpdr.kt                      # WU 2.10-5
  InRC.kt                      # WU 2.10-6
  OutCR.kt                     # WU 2.10-6
  InAImm.kt                    # WU 2.10-6
  OutImmA.kt                   # WU 2.10-6
  Ini.kt                       # WU 2.10-6
  Ind.kt                       # WU 2.10-6
  Inir.kt                      # WU 2.10-6
  Indr.kt                      # WU 2.10-6
  Outi.kt                      # WU 2.10-6
  Outd.kt                      # WU 2.10-6
  Otir.kt                      # WU 2.10-6
  Otdr.kt                      # WU 2.10-6

src/test/kotlin/ru/alepar/zx80/cpu/
  IoBusTest.kt                 # WU 2.10-1
src/test/kotlin/ru/alepar/zx80/op/ed/
  EdOpsTest.kt                 # WU 2.10-1 (skeleton), extended in 2-6
  (one Test.kt per Op above)   # WUs 2.10-2/3/4/5/6
src/test/kotlin/ru/alepar/zx80/harness/suites/
  FuseSuiteTest.kt             # WU 2.10-1 (extend with Trap B fix tests)
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt                          # WU 2.10-1 (add `var io: IoBus = NoIoBus`)
src/test/kotlin/ru/alepar/zx80/cpu/CpuTest.kt                      # WU 2.10-1 (add io-default test)
src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt         # WU 2.10-1 (Trap B loop)
src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt                # WU 2.10-1 (add EdOps.installInto)
```

---

## WU 2.10-1 — Foundation: IoBus + Cpu.io + Trap B fix + EdOps skeleton

### Task 1: IoBus interface + NoIoBus default

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cpu/IoBus.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/IoBusTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IoBusTest {
    @Test
    fun `NoIoBus returns 0xFF on every read`() {
        assertThat(NoIoBus.read(0)).isEqualTo(0xFF)
        assertThat(NoIoBus.read(0xFFFF)).isEqualTo(0xFF)
        assertThat(NoIoBus.read(0x1234)).isEqualTo(0xFF)
    }

    @Test
    fun `NoIoBus accepts writes silently`() {
        NoIoBus.write(0, 0)
        NoIoBus.write(0xFFFF, 0xFF)
        NoIoBus.write(0x1234, 0x42)
        // No assertions — just verify no exception.
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.cpu

/**
 * Z80 I/O port bus. Ports are 16-bit (0..0xFFFF). IN A,(n) sets the
 * high byte from cpu.a; IN r,(C) uses cpu.bc as port; OUT (n),A and
 * OUT (C),r similarly.
 *
 * In M1 we default to NoIoBus (returns 0xFF on read, ignores writes).
 * M2 will swap in a real bus connected to ULA, keyboard, beeper, etc.
 */
interface IoBus {
    fun read(port: Int): Int
    fun write(port: Int, value: Int)
}

object NoIoBus : IoBus {
    override fun read(port: Int): Int = 0xFF
    override fun write(port: Int, value: Int) { /* ignore */ }
}
```

- [ ] **Step 4: Verify pass.** 2 tests.

### Task 2: Add Cpu.io field

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/CpuTest.kt`

- [ ] **Step 1: Failing test**

Append to `CpuTest.kt`:

```kotlin
@Test
fun `Cpu has io field defaulting to NoIoBus`() {
    val cpu = Cpu()
    assertThat(cpu.io).isSameAs(NoIoBus)
}

@Test
fun `Cpu io can be reassigned`() {
    val customBus = object : IoBus {
        override fun read(port: Int) = port and 0xFF
        override fun write(port: Int, value: Int) {}
    }
    val cpu = Cpu()
    cpu.io = customBus
    assertThat(cpu.io.read(0x42)).isEqualTo(0x42)
}
```

- [ ] **Step 2: Verify fails (Cpu.io doesn't exist yet).**

- [ ] **Step 3: Add `io` field to Cpu**

In `src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt`, add after the existing fields:

```kotlin
class Cpu {
    // ... existing fields (a, f, b, c, ..., tStates) ...

    /** I/O bus; defaults to NoIoBus (returns 0xFF on read, ignores writes). */
    var io: IoBus = NoIoBus
}
```

- [ ] **Step 4: Verify pass.** 2 new CpuTest tests.

### Task 3: Trap B fix in FuseSuite.runOne

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/harness/suites/FuseSuiteTest.kt`

- [ ] **Step 1: Failing test**

Append to `FuseSuiteTest.kt`:

```kotlin
@Test
fun `runOne loops dispatch until tStatesToRun is reached (Trap B fix)`() {
    // Synthetic test: a stub Op that takes 4 T-states. tStatesToRun = 12.
    // Expect 3 iterations of the stub Op.
    val stubOp = object : ru.alepar.zx80.op.Op {
        override val operandLength = 0
        override val baseCycles = 4
        var executions = 0
        override fun execute(cpu: ru.alepar.zx80.cpu.Cpu, mem: ru.alepar.zx80.cpu.Memory) {
            executions++
            cpu.tStates += 4
            cpu.pc = (cpu.pc + 1) and 0xFFFF   // advance past the opcode each time
        }
        override fun mnemonic(operands: ru.alepar.zx80.op.OperandFetcher) = "STUB"
    }
    val decoder = ru.alepar.zx80.cpu.Decoder().apply { main[0x00] = stubOp }
    val input = ru.alepar.zx80.harness.fuse.FuseInputCase(
        name = "loop-test",
        af = 0, bc = 0, de = 0, hl = 0,
        afAlt = 0, bcAlt = 0, deAlt = 0, hlAlt = 0,
        ix = 0, iy = 0, sp = 0, pc = 0,
        i = 0, r = 0,
        iff1 = false, iff2 = false, im = 0,
        memptr = 0,
        halted = false, tStatesToRun = 12,
        memory = listOf(0 to byteArrayOf(0, 0, 0)),
    )
    val expected = ru.alepar.zx80.harness.fuse.FuseExpectedCase(
        name = "loop-test",
        af = 0, bc = 0, de = 0, hl = 0,
        afAlt = 0, bcAlt = 0, deAlt = 0, hlAlt = 0,
        ix = 0, iy = 0, sp = 0, pc = 3,                      // PC advanced 3 times
        i = 0, r = 0,
        iff1 = false, iff2 = false, im = 0,
        memptr = 0,
        halted = false, tStatesAfter = 12,                    // 3 * 4 = 12 T-states
        memory = emptyList(),
    )
    val suite = FuseSuite(decoder, listOf(input), listOf(expected))
    val r = suite.run()
    assertThat(r.passed).isEqualTo(1)                        // success → loop ran 3 iterations
    assertThat(stubOp.executions).isEqualTo(3)
}
```

(Field names — `memptr`, `tStatesToRun`, `tStatesAfter` — match what was actually defined in the parser per the spec compliance review of WU6.)

- [ ] **Step 2: Verify fails (current FuseSuite runs exactly one instruction).**

- [ ] **Step 3: Implement Trap B fix in FuseSuite**

In `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt`, locate `runOne` and replace the dispatch+execute block:

OLD:
```kotlin
val op = dispatcher.decodeAt(cpu, mem) ?: run {
    val opcodeByte = mem.read(cpu.pc)
    return "no op for opcode 0x${"%02X".format(opcodeByte)} (no dispatch route)"
}
op.execute(cpu, mem)
```

NEW:
```kotlin
val startTStates = cpu.tStates
val targetTStates = startTStates + input.tStatesToRun
while (cpu.tStates < targetTStates) {
    val curOp = dispatcher.decodeAt(cpu, mem) ?: run {
        val opcodeByte = mem.read(cpu.pc)
        return "no op for opcode 0x${"%02X".format(opcodeByte)} (no dispatch route)"
    }
    curOp.execute(cpu, mem)
}
```

(Note the `?: run { ... return ...}` lambda return pattern from the existing code is preserved; the loop simply wraps it.)

- [ ] **Step 4: Verify pass.** New test passes; existing FuseSuite tests still pass (they pass because non-block ops naturally consume exactly tStatesToRun in one iteration).

### Task 4: EdOps skeleton + wire into builder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class EdOpsTest {
    @Test
    fun `installInto on empty Decoder leaves ed table as before (skeleton)`() {
        val d = Decoder()
        val priorEdInstalled = d.ed.count { it != null }
        EdOps.installInto(d)
        // Skeleton installs nothing; existing ed entries (from MiscOps and AluOps in earlier phases) unchanged.
        val newEdInstalled = d.ed.count { it != null }
        assertThat(newEdInstalled).isEqualTo(priorEdInstalled)
    }
}
```

(Note: this WU's skeleton is empty; ed table only has the IM 0/1/2 + ADC HL,rr + SBC HL,rr entries that prior phase fragments installed. Since MiscOps/AluOps aren't called in this test setup, the test essentially asserts an empty ed table after only EdOps.installInto runs.)

- [ ] **Step 2: Verify fails (EdOps doesn't exist).**

- [ ] **Step 3: Implement skeleton**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the remaining ED-prefixed Op family into decoder.ed (and
 * the two main-table I/O stragglers IN A,(n)/OUT (n),A into
 * decoder.main). Filled in by WUs 2.10-2 through 2.10-6.
 *
 * (IM 0/1/2 was installed in Phase 2.1a-3 by MiscOps. ADC HL,rr and
 * SBC HL,rr were installed in Phase 2.3 by AluOps.)
 */
object EdOps {
    fun installInto(d: Decoder) {
        // Filled in by WUs 2.10-2 through 2.10-6.
    }
}
```

- [ ] **Step 4: Wire into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:
- Add: `import ru.alepar.zx80.op.ed.EdOps`
- In `build()`, add `EdOps.installInto(d)` after the existing installers.

- [ ] **Step 5: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/cpu/IoBus.kt \
        src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/IoBusTest.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/CpuTest.kt \
        src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt \
        src/test/kotlin/ru/alepar/zx80/harness/suites/FuseSuiteTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat: Phase 2.10 foundation — IoBus + Cpu.io + Trap B fix + EdOps skeleton

IoBus interface with NoIoBus default (returns 0xFF on read, ignores
writes). Cpu gains var io: IoBus = NoIoBus. FuseSuite.runOne loops
until cpu.tStates >= startTStates + input.tStatesToRun (the long-
deferred Trap B fix). Empty EdOps fragment wired into OpTableBuilder."
```

---

## WU 2.10-2 — Register transfers + NEG + RETI/RETN + RRD/RLD (8 Op classes)

This WU has 8 distinct Op classes — all small (each ~20-30 LOC). I'll show the first three in full and provide compact specs for the rest.

### Task 1: LdIA

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/LdIA.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/LdIATest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdIATest {
    @Test
    fun `LD I, A copies A to I, advances pc by 2, r by 2, 9 T-states, no flags`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; a = 0x42; i = 0; f = 0xFF }
        LdIA.execute(cpu, Memory())
        assertThat(cpu.i).isEqualTo(0x42)
        assertThat(cpu.a).isEqualTo(0x42)              // A unchanged
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(9L)
        assertThat(cpu.f).isEqualTo(0xFF)              // no flag changes
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdIA.mnemonic { 0 }).isEqualTo("LD I, A")
    }

    @Test
    fun `operandLength=0, baseCycles=9`() {
        assertThat(LdIA.operandLength).isZero
        assertThat(LdIA.baseCycles).isEqualTo(9)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD I, A` — copy A into the interrupt vector register I. ED 47.
 * 9 T-states. R+=2. PC+=2. No flag changes.
 */
object LdIA : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.i = cpu.a
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD I, A"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 2: LdRA

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/LdRA.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/LdRATest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdRATest {
    @Test
    fun `LD R, A copies all 8 bits of A into R (overrides any current R value)`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0x55; tStates = 0L
            a = 0xAB
        }
        LdRA.execute(cpu, Memory())
        // R written from A directly, then bumpR(2) increments low 7 bits.
        // 0xAB → after bumpR(2) → top bit 1 preserved; low 7 = (0x2B + 2) & 0x7F = 0x2D
        // So final R = 0x80 | 0x2D = 0xAD
        assertThat(cpu.r).isEqualTo(0xAD)
    }

    @Test
    fun `LD R, A — A unchanged, no flags, 9 T-states`() {
        val cpu = Cpu().apply { a = 0x42; f = 0xFF }
        LdRA.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.tStates).isEqualTo(9L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRA.mnemonic { 0 }).isEqualTo("LD R, A")
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD R, A` — copy A into the memory refresh register R. ED 4F.
 * 9 T-states. PC+=2. R is then ALSO incremented by the normal bumpR(2)
 * path (so the final R = (A+2) low 7 bits | (A and 0x80) high bit).
 * No flag changes.
 */
object LdRA : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.r = cpu.a
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD R, A"
}
```

- [ ] **Step 4: Verify.** 3 tests.

### Task 3: LdAI (the flag-computing one)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/LdAI.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/LdAITest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class LdAITest {
    @Test
    fun `LD A, I copies I to A and computes flags`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            i = 0x42; a = 0
            iff2 = false
            f = Flags.C    // C will be preserved
        }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.S).isZero          // bit 7 of 0x42 is 0
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.PV).isZero          // iff2 was false
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.C).isNotZero        // preserved
        assertThat(cpu.tStates).isEqualTo(9L)
    }

    @Test
    fun `LD A, I sets PV when iff2 is true`() {
        val cpu = Cpu().apply { i = 0x42; iff2 = true }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.f and Flags.PV).isNotZero
    }

    @Test
    fun `LD A, I sets Z when I is zero`() {
        val cpu = Cpu().apply { i = 0; iff2 = false }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.a).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `LD A, I sets S when I bit 7 is set`() {
        val cpu = Cpu().apply { i = 0x80; iff2 = false }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x80)
        assertThat(cpu.f and Flags.S).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAI.mnemonic { 0 }).isEqualTo("LD A, I")
    }

    @Test
    fun `operandLength=0, baseCycles=9`() {
        assertThat(LdAI.operandLength).isZero
        assertThat(LdAI.baseCycles).isEqualTo(9)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD A, I` — copy I to A and compute flags from result.
 * ED 57. 9 T-states. R+=2. PC+=2.
 *
 * Flags: S = bit 7 of A; Z = A == 0; H = 0; **P/V = cpu.iff2** (note:
 * NOT parity — this is the one place IFF2 leaks into the flag register);
 * N = 0; C preserved.
 */
object LdAI : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.a = cpu.i
        var f = cpu.f and Flags.C   // preserve C
        if (cpu.a == 0) f = f or Flags.Z
        if (cpu.a and 0x80 != 0) f = f or Flags.S
        if (cpu.iff2) f = f or Flags.PV
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD A, I"
}
```

- [ ] **Step 4: Verify.** 6 tests.

### Tasks 4-9: Remaining Op classes (compact)

Each follows the same TDD pattern: write failing test, verify fails, implement, verify passes. Per-Op specs:

- [ ] **Task 4: LdAR** — `LdAR` singleton, ED 5F, 9T. Same shape as LdAI but `cpu.a = cpu.r` and same flag computation (PV from iff2).

- [ ] **Task 5: Neg** — `Neg` singleton, ED 44, 8T, PC+=2. Computes `Flags.afterSub(0, cpu.a, 0)`. Sets A to result. Test specifically: `A=0 → A=0, Z set, no C`; `A=0x01 → A=0xFF, S set, C set`; `A=0x80 → A=0x80, P/V (overflow) set`.

- [ ] **Task 6: Reti** — `Reti` singleton, ED 4D, 14T, PC+=2 (then pop). `cpu.pc = cpu.pop(mem)`. No flag changes. (No effect on iff in our M1 emulator since we don't model interrupt acknowledge.)

- [ ] **Task 7: Retn** — `Retn` singleton, ED 45, 14T. `cpu.pc = cpu.pop(mem); cpu.iff1 = cpu.iff2`. Test specifically asserts iff1 changes after.

- [ ] **Task 8: Rrd** — `Rrd` singleton, ED 67, 18T, PC+=2. Reads `m = mem[HL]`. New `m` = `((cpu.a and 0x0F) shl 4) or (m ushr 4)`. New `cpu.a` = `(cpu.a and 0xF0) or (m and 0x0F)`. Writes new m to mem[HL]. Flags: S/Z from new A, PV=parity(new A), H=0, N=0, C preserved.

- [ ] **Task 9: Rld** — `Rld` singleton, ED 6F, 18T, PC+=2. Similar but rotation goes the other way: New `m` = `((m and 0x0F) shl 4) or (cpu.a and 0x0F)`. New `cpu.a` = `(cpu.a and 0xF0) or (m ushr 4)`.

Each gets per-Op tests covering: state delta, PC+R+T-states, flag impact (or lack thereof), mnemonic.

### Task 10: Extend EdOps + EdOpsTest

- [ ] **Step 1: Add registrations to EdOps**

```kotlin
object EdOps {
    fun installInto(d: Decoder) {
        installRegisterTransfers(d)
        installNeg(d)
        installReturns(d)
        installRrdRld(d)
    }

    private fun installRegisterTransfers(d: Decoder) {
        d.ed[0x47] = LdIA
        d.ed[0x4F] = LdRA
        d.ed[0x57] = LdAI
        d.ed[0x5F] = LdAR
    }

    private fun installNeg(d: Decoder) {
        d.ed[0x44] = Neg
    }

    private fun installReturns(d: Decoder) {
        d.ed[0x45] = Retn
        d.ed[0x4D] = Reti
    }

    private fun installRrdRld(d: Decoder) {
        d.ed[0x67] = Rrd
        d.ed[0x6F] = Rld
    }
}
```

- [ ] **Step 2: Add EdOpsTest assertions**

Append to `EdOpsTest.kt`:

```kotlin
@Test
fun `installInto registers register transfers at ED 47, 4F, 57, 5F`() {
    val d = Decoder()
    EdOps.installInto(d)
    assertThat(d.ed[0x47]).isSameAs(LdIA)
    assertThat(d.ed[0x4F]).isSameAs(LdRA)
    assertThat(d.ed[0x57]).isSameAs(LdAI)
    assertThat(d.ed[0x5F]).isSameAs(LdAR)
}

@Test
fun `installInto registers NEG at ED 44, RETN at 45, RETI at 4D`() {
    val d = Decoder()
    EdOps.installInto(d)
    assertThat(d.ed[0x44]).isSameAs(Neg)
    assertThat(d.ed[0x45]).isSameAs(Retn)
    assertThat(d.ed[0x4D]).isSameAs(Reti)
}

@Test
fun `installInto registers RRD at ED 67, RLD at 6F`() {
    val d = Decoder()
    EdOps.installInto(d)
    assertThat(d.ed[0x67]).isSameAs(Rrd)
    assertThat(d.ed[0x6F]).isSameAs(Rld)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew test spotlessApply
git add src/main/kotlin/ru/alepar/zx80/op/ed/Ld*.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Neg.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Ret*.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Rrd.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Rld.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/Ld*Test.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/NegTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/Ret*Test.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/RrdTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/RldTest.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt
git commit -m "feat(op/ed): register transfers, NEG, RETI/RETN, RRD/RLD (8 opcodes)

LdIA/LdRA (no flags), LdAI/LdAR (compute flags incl. P/V from iff2).
Neg delegates to Flags.afterSub(0, A, 0). Reti/Retn pop pc; Retn
also restores iff1 from iff2. Rrd/Rld rotate decimal nibbles between
A and (HL); compute S/Z/PV/H=0/N=0/C preserved."
```

---

## WU 2.10-3 — Extended LD pair to/from memory (8 opcodes via 2 parameterized classes)

### Task 1: LdAddrFromPair

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/LdAddrFromPair.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/LdAddrFromPairTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdAddrFromPairTest {
    @Test
    fun `LD (nn), BC writes BC as little-endian word, advances pc by 4, r by 2, 20 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; bc = 0xABCD; f = 0xFF }
        val mem = Memory().apply {
            write(0x100, 0xED); write(0x101, 0x43)
            write(0x102, 0x00); write(0x103, 0x40)   // nn = 0x4000
        }
        LdAddrFromPair(pair = RegPair.BC).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0xCD)   // low byte
        assertThat(mem.read(0x4001)).isEqualTo(0xAB)   // high byte
        assertThat(cpu.bc).isEqualTo(0xABCD)            // unchanged
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(20L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD (nn), SP works for SP`() {
        val cpu = Cpu().apply { pc = 0x100; sp = 0x1234 }
        val mem = Memory().apply { write(0x102, 0x00); write(0x103, 0x40) }
        LdAddrFromPair(pair = RegPair.SP).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x34)
        assertThat(mem.read(0x4001)).isEqualTo(0x12)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAddrFromPair(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD (nn), BC")
        assertThat(LdAddrFromPair(pair = RegPair.SP).mnemonic { 0 }).isEqualTo("LD (nn), SP")
    }

    @Test
    fun `operandLength=2, baseCycles=20`() {
        val op = LdAddrFromPair(pair = RegPair.BC)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(20)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (nn), rr` — write 16-bit register pair to little-endian memory.
 * ED 43/53/63/73 for BC/DE/HL/SP. 20 T-states. R+=2. PC+=4.
 * No flag changes.
 *
 * Note: ED 63 (LD (nn),HL) duplicates the main-table 0x22 encoding;
 * both are documented and FUSE tests both. The ED form takes 20T (vs
 * 16T for the main-table form) due to the prefix.
 */
class LdAddrFromPair(private val pair: RegPair) : Op {
    init {
        require(pair != RegPair.AF) { "LD (nn),rr does not accept AF" }
    }

    override val operandLength = 2
    override val baseCycles = 20

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 2)
        mem.writeWord(addr, pair.read(cpu))
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (nn), ${pair.mnemonic}"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: LdPairFromAddr

Same shape; opposite direction.

```kotlin
class LdPairFromAddr(private val pair: RegPair) : Op {
    init { require(pair != RegPair.AF) }
    override val operandLength = 2
    override val baseCycles = 20

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 2)
        pair.write(cpu, mem.readWord(addr))
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${pair.mnemonic}, (nn)"
}
```

Test: ED 4B (LD BC,(nn)), ED 7B (LD SP,(nn)), wraparound, mnemonics.

### Task 3: Extend EdOps + EdOpsTest

```kotlin
private fun installExtendedLdPair(d: Decoder) {
    // LD (nn), rr — opcode pattern ED 01 pp 0011 → ED 43, 53, 63, 73
    // LD rr, (nn) — opcode pattern ED 01 pp 1011 → ED 4B, 5B, 6B, 7B
    for (ppBits in 0..3) {
        val pair = RegPair.fromBits(ppBits)
        d.ed[0x43 or (ppBits shl 4)] = LdAddrFromPair(pair)
        d.ed[0x4B or (ppBits shl 4)] = LdPairFromAddr(pair)
    }
}
```

EdOpsTest assertions: ED 43 → "LD (nn), BC"; ED 7B → "LD SP, (nn)"; etc.

Commit message: `feat(op/ed): extended LD rr,(nn) and LD (nn),rr — 8 opcodes`.

---

## WU 2.10-4 — Block move (LDI/LDD/LDIR/LDDR)

### Task 1: Ldi

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/Ldi.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/LdiTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class LdiTest {
    @Test
    fun `LDI copies (HL) to (DE), increments HL and DE, decrements BC, 16 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000; de = 0x5000; bc = 0x0003
            f = Flags.S or Flags.Z or Flags.C   // preserve S/Z/C
        }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Ldi.execute(cpu, mem)
        assertThat(mem.read(0x5000)).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4001)
        assertThat(cpu.de).isEqualTo(0x5001)
        assertThat(cpu.bc).isEqualTo(0x0002)
        assertThat(cpu.f and Flags.S).isNotZero        // preserved
        assertThat(cpu.f and Flags.Z).isNotZero        // preserved
        assertThat(cpu.f and Flags.C).isNotZero        // preserved
        assertThat(cpu.f and Flags.N).isZero            // cleared
        assertThat(cpu.f and Flags.H).isZero            // cleared
        assertThat(cpu.f and Flags.PV).isNotZero       // BC != 0 after
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `LDI clears PV when BC becomes 0`() {
        val cpu = Cpu().apply { hl = 0x4000; de = 0x5000; bc = 0x0001 }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Ldi.execute(cpu, mem)
        assertThat(cpu.bc).isZero
        assertThat(cpu.f and Flags.PV).isZero
    }

    @Test
    fun `LDI handles HL wrap (0xFFFF -> 0x0000)`() {
        val cpu = Cpu().apply { hl = 0xFFFF; de = 0x5000; bc = 1 }
        val mem = Memory().apply { write(0xFFFF, 0x42) }
        Ldi.execute(cpu, mem)
        assertThat(cpu.hl).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ldi.mnemonic { 0 }).isEqualTo("LDI")
    }

    @Test
    fun `operandLength=0, baseCycles=16`() {
        assertThat(Ldi.operandLength).isZero
        assertThat(Ldi.baseCycles).isEqualTo(16)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LDI` — block move increment. ED A0. 16 T-states. R+=2. PC+=2.
 *
 * mem[DE] = mem[HL]; HL++; DE++; BC--.
 *
 * Flags: H=0, N=0, P/V = (BC != 0 after); S/Z/C preserved.
 */
object Ldi : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        mem.write(cpu.de, mem.read(cpu.hl))
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.de = (cpu.de + 1) and 0xFFFF
        cpu.bc = (cpu.bc - 1) and 0xFFFF
        var f = cpu.f and (Flags.S or Flags.Z or Flags.C)   // preserve S/Z/C
        if (cpu.bc != 0) f = f or Flags.PV
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LDI"
}
```

- [ ] **Step 4: Verify.** 5 tests.

### Task 2: Ldd

Same shape but HL/DE decrement. Implementation:

```kotlin
mem.write(cpu.de, mem.read(cpu.hl))
cpu.hl = (cpu.hl - 1) and 0xFFFF
cpu.de = (cpu.de - 1) and 0xFFFF
cpu.bc = (cpu.bc - 1) and 0xFFFF
// flags same as Ldi
```

mnemonic = "LDD". 16T.

### Task 3: Ldir (the loop variant — exercises Trap B fix)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/Ldir.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/LdirTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdirTest {
    @Test
    fun `LDIR with BC=2 — first iteration leaves PC unchanged so dispatcher re-fires`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000; de = 0x5000; bc = 0x0002
        }
        val mem = Memory().apply { write(0x4000, 0xAA); write(0x4001, 0xBB) }
        Ldir.execute(cpu, mem)
        assertThat(mem.read(0x5000)).isEqualTo(0xAA)
        assertThat(cpu.bc).isEqualTo(0x0001)
        assertThat(cpu.pc).isEqualTo(0x100)            // PC unchanged — re-fires
        assertThat(cpu.tStates).isEqualTo(21L)         // 21T for looping iteration
    }

    @Test
    fun `LDIR with BC=1 — final iteration advances PC by 2 and uses 16 T-states`() {
        val cpu = Cpu().apply { hl = 0x4000; de = 0x5000; bc = 0x0001; pc = 0x100; tStates = 0L }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Ldir.execute(cpu, mem)
        assertThat(cpu.bc).isZero
        assertThat(cpu.pc).isEqualTo(0x102)            // advance past LDIR
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `LDIR end-to-end via two execute calls (BC=2 → 1 → 0)`() {
        val cpu = Cpu().apply { hl = 0x4000; de = 0x5000; bc = 0x0002; pc = 0x100; tStates = 0L }
        val mem = Memory().apply { write(0x4000, 0xAA); write(0x4001, 0xBB) }
        Ldir.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x100)
        Ldir.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(mem.read(0x5000)).isEqualTo(0xAA)
        assertThat(mem.read(0x5001)).isEqualTo(0xBB)
        assertThat(cpu.bc).isZero
        assertThat(cpu.tStates).isEqualTo(21L + 16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ldir.mnemonic { 0 }).isEqualTo("LDIR")
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LDIR` — block move increment + repeat. ED B0. Each iteration:
 * - mem[DE] = mem[HL]; HL++; DE++; BC--
 * - if BC != 0: leave PC unchanged so next dispatch re-fires LDIR; 21 T-states
 * - if BC == 0: advance PC by 2; 16 T-states
 * - flags as Ldi
 *
 * The Trap B fix in FuseSuite.runOne loops dispatch until tStates is
 * reached; that's how multiple iterations get executed within one
 * FUSE case.
 */
object Ldir : Op {
    override val operandLength = 0
    override val baseCycles = 16   // exit cost; loop iteration is +5

    override fun execute(cpu: Cpu, mem: Memory) {
        mem.write(cpu.de, mem.read(cpu.hl))
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.de = (cpu.de + 1) and 0xFFFF
        cpu.bc = (cpu.bc - 1) and 0xFFFF
        var f = cpu.f and (Flags.S or Flags.Z or Flags.C)
        if (cpu.bc != 0) f = f or Flags.PV
        cpu.f = f
        if (cpu.bc != 0) {
            // Don't advance PC — next dispatch re-fires LDIR
            cpu.tStates += 21
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
            cpu.tStates += 16
        }
        cpu.bumpR(2)
    }

    override fun mnemonic(operands: OperandFetcher) = "LDIR"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 4: Lddr

Same shape as Ldir but HL/DE decrement. Cycle counts and PC handling identical.

### Task 5: Extend EdOps + EdOpsTest

```kotlin
private fun installBlockMove(d: Decoder) {
    d.ed[0xA0] = Ldi
    d.ed[0xA8] = Ldd
    d.ed[0xB0] = Ldir
    d.ed[0xB8] = Lddr
}
```

EdOpsTest: assert each opcode mapping.

Commit: `feat(op/ed): block move LDI/LDD/LDIR/LDDR — 4 opcodes (exercises Trap B fix)`.

---

## WU 2.10-5 — Block compare (CPI/CPD/CPIR/CPDR)

Same structure as block move but compare instead of copy. Each iteration:
- compare A with mem[HL] (set flags as if SUB but don't write A)
- HL ± 1, BC -= 1
- For repeating variants (CPIR/CPDR): exit when BC == 0 OR when match found (Z set after compare); else loop.

### Task 1: Cpi

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/Cpi.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/CpiTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CpiTest {
    @Test
    fun `CPI compares A with (HL), increments HL, decrements BC, sets N, 16T`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            hl = 0x4000; bc = 0x0003; a = 0x42
            f = Flags.C   // C preserved
        }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)              // unchanged
        assertThat(cpu.hl).isEqualTo(0x4001)
        assertThat(cpu.bc).isEqualTo(0x0002)
        assertThat(cpu.f and Flags.Z).isNotZero        // match
        assertThat(cpu.f and Flags.N).isNotZero        // CP-style: N set
        assertThat(cpu.f and Flags.C).isNotZero        // preserved
        assertThat(cpu.f and Flags.PV).isNotZero       // BC != 0 after
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `CPI clears Z when no match`() {
        val cpu = Cpu().apply { hl = 0x4000; bc = 1; a = 0x42 }
        val mem = Memory().apply { write(0x4000, 0xAA) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `CPI clears PV when BC reaches 0`() {
        val cpu = Cpu().apply { hl = 0x4000; bc = 1; a = 0x42 }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.bc).isZero
        assertThat(cpu.f and Flags.PV).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpi.mnemonic { 0 }).isEqualTo("CPI")
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CPI` — block compare increment. ED A1. 16 T-states. R+=2. PC+=2.
 *
 * Compare A with mem[HL] (set flags as SUB but don't write A);
 * HL++; BC--.
 *
 * Flags: S/Z from A - mem[HL]; H from low nibble; N=1; P/V = (BC != 0 after);
 * C preserved.
 */
object Cpi : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterSub(cpu.a, mem.read(cpu.hl), 0)
        // Don't update A — CP doesn't.
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.bc = (cpu.bc - 1) and 0xFFFF
        // CPI flag rules: keep S/Z/H/N from afterSub; preserve C from oldF; set PV from BC != 0
        var f = (r.newF and (Flags.S or Flags.Z or Flags.H or Flags.N)) or (cpu.f and Flags.C)
        if (cpu.bc != 0) f = f or Flags.PV
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CPI"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Tasks 2-4: Cpd, Cpir, Cpdr

- [ ] **Cpd** — Same as Cpi but `cpu.hl = (cpu.hl - 1) and 0xFFFF`.
- [ ] **Cpir** — Same as Cpi but with loop. Exit when BC == 0 OR when match found (Z set). Loop iteration: 21T; exit: 16T. PC handling like Ldir.
- [ ] **Cpdr** — Same as Cpd with loop.

### Task 5: Extend EdOps + EdOpsTest

```kotlin
private fun installBlockCompare(d: Decoder) {
    d.ed[0xA1] = Cpi
    d.ed[0xA9] = Cpd
    d.ed[0xB1] = Cpir
    d.ed[0xB9] = Cpdr
}
```

Commit: `feat(op/ed): block compare CPI/CPD/CPIR/CPDR — 4 opcodes`.

---

## WU 2.10-6 — I/O ops (12 classes, 18 opcodes)

Includes 2 main-table stragglers + 16 ED-prefixed.

### Task 1: InAImm (main table)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/InAImm.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/InAImmTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory

class InAImmTest {
    @Test
    fun `IN A, (n) reads from port (a shl 8) or n into A, 11T, no flags`() {
        val recordedPort = mutableListOf<Int>()
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            a = 0xAB; f = 0xFF
            io = object : IoBus {
                override fun read(port: Int): Int { recordedPort.add(port); return 0x42 }
                override fun write(port: Int, value: Int) {}
            }
        }
        val mem = Memory().apply {
            write(0x100, 0xDB); write(0x101, 0x55)   // port low byte = 0x55
        }
        InAImm.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(recordedPort).containsExactly(0xAB55)   // (A shl 8) or n
        assertThat(cpu.f).isEqualTo(0xFF)              // no flag changes
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)                  // R+=1 (main table, not ED)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `IN A, (n) with NoIoBus returns 0xFF`() {
        val cpu = Cpu().apply { pc = 0x100; a = 0; }
        val mem = Memory().apply { write(0x101, 0) }
        InAImm.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(InAImm.mnemonic { 0 }).isEqualTo("IN A, (n)")
    }

    @Test
    fun `operandLength=1, baseCycles=11`() {
        assertThat(InAImm.operandLength).isEqualTo(1)
        assertThat(InAImm.baseCycles).isEqualTo(11)
    }
}
```

- [ ] **Step 2: Verify fails. Step 3: Implement.**

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `IN A, (n)` — read I/O port into A. 0xDB nn (main table). 11 T-states.
 * R+=1. PC+=2. operandLength=1.
 *
 * Port = (cpu.a shl 8) or n  — high byte from A, low byte from immediate.
 * No flag changes.
 */
object InAImm : Op {
    override val operandLength = 1
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        val port = (cpu.a shl 8) or n
        cpu.a = cpu.io.read(port)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "IN A, (n)"
}
```

- [ ] **Step 4: Verify.** 4 tests.

### Task 2: OutImmA (main table)

Same shape; opposite direction. `cpu.io.write((cpu.a shl 8) or n, cpu.a)`. 11T, R+=1, PC+=2, operandLength=1, no flags. Mnemonic `"OUT (n), A"`.

### Task 3: InRC (parameterized over Reg)

ED 40+8*r. 12T, R+=2, PC+=2, no operand. Port = `cpu.bc`. `dst.write(cpu, cpu.io.read(cpu.bc))`. Flags: S/Z/PV from byte read, H=0, N=0, C preserved.

For rrr=110, reads but doesn't write to a register (only sets flags). Implement with a special-case "discard" or use a `dst: Reg?` (null for the rrr=110 case) — null variant is awkward; cleaner to have a separate `InC` singleton just for the rrr=110 case. But that's an extra class...

**Decision:** parameterize `InRC(dst: Reg?)`; null means "discard byte (just set flags)". Validate `dst` doesn't include the (HL) wrapper concept.

Or: use a sentinel `RegOrNone` enum. For now, simplest: separate `InC` singleton for the rrr=110 case + parameterized `InRC(dst: Reg)` for the 7 register cases.

```kotlin
class InRC(private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = cpu.io.read(cpu.bc)
        dst.write(cpu, byte)
        var f = cpu.f and Flags.C   // preserve C
        if (byte == 0) f = f or Flags.Z
        if (byte and 0x80 != 0) f = f or Flags.S
        if (Flags.parity(byte)) f = f or Flags.PV
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "IN ${dst.mnemonic}, (C)"
}
```

(For the rrr=110 case "IN (C)", create `object InCFlags` with same logic but no `dst.write`. Mnemonic `"IN (C)"`.)

### Task 4: OutCR (parameterized)

OUT (C),r. 12T, no flags. Port = cpu.bc. Value = src.read(cpu). For rrr=110, value is 0 (some Z80 versions write a different value; we go with 0).

### Task 5: Block I/O (8 classes)

`Ini`, `Ind`, `Inir`, `Indr`, `Outi`, `Outd`, `Otir`, `Otdr`. Each follows a structure similar to LDI/LDIR/CPI/CPIR but uses I/O.

Quick template for Ini:

```kotlin
object Ini : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = cpu.io.read(cpu.bc)
        mem.write(cpu.hl, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.b = (cpu.b - 1) and 0xFF      // note: only B, not BC
        // Flags computation for block I/O is hairy and partially undocumented.
        // Standard documented bits: Z = (B == 0), N = 1.
        var f = 0
        if (cpu.b == 0) f = f or Flags.Z
        f = f or Flags.N
        // S, H, P/V have undocumented dependence on byte value; we leave them at 0.
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INI"
}
```

(Outi: same shape but mem→port; etc.)

Repeating variants (Inir/Indr/Otir/Otdr): exit when B == 0; loop iteration 21T; exit 16T. PC handling like Ldir.

### Task 6: Extend EdOps + EdOpsTest

```kotlin
private fun installSingleIo(d: Decoder) {
    // IN r, (C) at ED 40+8*r where r != (HL)
    for (rrrBits in 0..7) {
        if (rrrBits == 6) {
            d.ed[0x70] = InCFlags    // IN (C) — flags only
            d.ed[0x71] = OutCZero    // OUT (C), 0
        } else {
            d.ed[0x40 or (rrrBits shl 3)] = InRC(dst = Reg.fromBits(rrrBits))
            d.ed[0x41 or (rrrBits shl 3)] = OutCR(src = Reg.fromBits(rrrBits))
        }
    }
}

private fun installBlockIo(d: Decoder) {
    d.ed[0xA2] = Ini; d.ed[0xAA] = Ind
    d.ed[0xB2] = Inir; d.ed[0xBA] = Indr
    d.ed[0xA3] = Outi; d.ed[0xAB] = Outd
    d.ed[0xB3] = Otir; d.ed[0xBB] = Otdr
}

private fun installMainTableIoStragglers(d: Decoder) {
    d.main[0xDB] = InAImm
    d.main[0xD3] = OutImmA
}
```

EdOpsTest: spot-check each.

Commit: `feat(op/ed): I/O — 18 opcodes (2 main + 16 ED) using IoBus`.

---

## WU 2.10-7 — Sweep + tag

### Task 1: Verification + tag

- [ ] **Step 1: Clean build**

`./gradlew clean check installDist` — BUILD SUCCESSFUL.

- [ ] **Step 2: Capture exact CLI output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: opcodes count climbs by ~51. Composite score plateaus near M1's max.

- [ ] **Step 3: Spotless check**

`./gradlew spotlessCheck` — green.

- [ ] **Step 4: Sanity-check key FUSE cases pass**

```bash
python3 -c "
import json
d = json.load(open('build/score.json'))
failures = d['suites']['fuse']['details'].get('failures', [])
for op in ['ed 44', 'ed 47', 'ed 57', 'ed a0', 'ed b0', 'ed b1', 'd3', 'db']:
    has = any(f.startswith(f'{op}:') for f in failures)
    print(f'  case {op}: {\"FAIL\" if has else \"PASS\"}')"
```

Expected: most PASS. Block I/O cases (ed a2, ed a3, etc.) may have some failures due to undocumented flag bits — those are acceptable.

- [ ] **Step 5: Tag**

```bash
git tag -a m1-phase02-10 -m "M1 Phase 2.10 complete: ED remainder + I/O + Trap B fix

~51 documented opcodes across ~28 Op classes covering register
transfers (LD I/R/A,I/R), NEG, RETI/RETN, RRD/RLD, extended LD pair
to/from memory, block move (LDI/LDD/LDIR/LDDR), block compare (CPI/
CPD/CPIR/CPDR), block I/O (INI/IND/INIR/INDR/OUTI/OUTD/OTIR/OTDR),
and single I/O (IN A,(n), OUT (n),A, IN r,(C), OUT (C),r). Foundations:
IoBus interface + NoIoBus default + Cpu.io field, and Trap B fix to
FuseSuite.runOne (loop until tStatesToRun reached). LDIR/LDDR/CPIR/
CPDR/INIR/INDR/OTIR/OTDR each implement one iteration per execute()
call; the harness loop drives multiple iterations within one FUSE case.

The documented Z80 instruction set is now complete. Plan 2.11 (programs
+ flag tightening) is the final batch."
```

This plan is complete.

---

## Self-Review

1. **Spec coverage:** Foundation + Trap B → 2.10-1. Register transfers/NEG/RETI/RETN/RRD/RLD → 2.10-2. Extended LD pair → 2.10-3. Block move → 2.10-4. Block compare → 2.10-5. I/O → 2.10-6. Sweep → 2.10-7.

2. **Placeholder scan:** WUs 2.10-2 through 2.10-6 use compact specs ("follows the same template") for the second-and-later Op of each pattern. Each Op still gets its own file + test file with the spec doc's per-op behavior table as authoritative reference.

3. **Type consistency:** All ED ops use `cpu.bumpR(2)`. Block ops follow PC-unchanged-on-loop convention. IoBus is on `cpu.io`.

4. **Critical assertions:**
   - Trap B fix has explicit synthetic test in FuseSuiteTest verifying multiple-iteration dispatch.
   - LdAI/LdAR explicitly test P/V = IFF2.
   - LDIR test runs 2 execute() calls and verifies PC behavior at both points.
   - InAImm asserts port construction `(cpu.a shl 8) or n`.
   - NoIoBus returns 0xFF on every read (verified end-to-end via InAImm test).

5. **Mnemonic format:** `"LD I, A"`, `"LD A, I"`, `"NEG"`, `"RETI"`, `"RETN"`, `"RRD"`, `"RLD"`, `"LD (nn), BC"`, `"LDIR"`, `"CPI"`, `"INI"`, `"OUTI"`, `"OTIR"`, `"IN A, (n)"`, `"OUT (n), A"`, `"IN B, (C)"`, `"OUT (C), B"`, `"IN (C)"`, `"OUT (C), 0"`.
