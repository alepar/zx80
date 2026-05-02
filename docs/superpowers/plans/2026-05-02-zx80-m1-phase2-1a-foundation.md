# ZX Spectrum Emulator — Phase 2.1a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the dispatcher (Trap A fix), the OpTableBuilder pattern, and the simplest two Op families (misc + EX). After this plan, `./build/install/zx80/bin/zx80 score` reports a non-zero composite score for the first time, `nop_loop` passes, and the dispatch infrastructure is ready for all subsequent opcode batches to slot into.

**Architecture:** A new `Dispatcher` type encapsulates "PC + memory → Op?" by reading the byte at PC and following any prefix (CB / ED / DD / FD / DDCB / FDCB) into the right sub-table. A new `OpTableBuilder` populates the seven dispatch tables on a fresh `Decoder` by calling per-family fragments (`MiscOps.installInto(decoder)`, `ExOps.installInto(decoder)`, …). Each Op is a Kotlin singleton (or small parameterized class) responsible for advancing PC, incrementing R, accumulating T-states, and doing its work — flag bits are untouched in this batch.

**Tech Stack:** Kotlin 2.1.10, JDK 21, Gradle 8.10, JUnit 5, AssertJ. No new dependencies.

**Reference spec:** `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-1a-foundation-design.md`
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Base commit:** `8eb2624` (the spec commit)

---

## File Structure

By the end of this plan the following files exist or have been modified.

**New files:**
```
src/main/kotlin/ru/alepar/zx80/cpu/
  Dispatcher.kt                       # WU 2.1a-1
src/main/kotlin/ru/alepar/zx80/op/
  OpTableBuilder.kt                   # WU 2.1a-2
  misc/Nop.kt                         # WU 2.1a-3
  misc/Halt.kt                        # WU 2.1a-3
  misc/Di.kt                          # WU 2.1a-3
  misc/Ei.kt                          # WU 2.1a-3
  misc/Im.kt                          # WU 2.1a-3
  misc/MiscOps.kt                     # WU 2.1a-3
  ex/ExAfAfAlt.kt                     # WU 2.1a-4
  ex/Exx.kt                           # WU 2.1a-4
  ex/ExDeHl.kt                        # WU 2.1a-4
  ex/ExSpHl.kt                        # WU 2.1a-4
  ex/ExOps.kt                         # WU 2.1a-4

src/test/kotlin/ru/alepar/zx80/cpu/
  DispatcherTest.kt                   # WU 2.1a-1
src/test/kotlin/ru/alepar/zx80/op/
  OpTableBuilderTest.kt               # WU 2.1a-2
  misc/NopTest.kt                     # WU 2.1a-3
  misc/HaltTest.kt                    # WU 2.1a-3
  misc/DiTest.kt                      # WU 2.1a-3
  misc/EiTest.kt                      # WU 2.1a-3
  misc/ImTest.kt                      # WU 2.1a-3
  misc/MiscOpsTest.kt                 # WU 2.1a-3
  ex/ExAfAfAltTest.kt                 # WU 2.1a-4
  ex/ExxTest.kt                       # WU 2.1a-4
  ex/ExDeHlTest.kt                    # WU 2.1a-4
  ex/ExSpHlTest.kt                    # WU 2.1a-4
  ex/ExOpsTest.kt                     # WU 2.1a-4
```

**Modified files:**
```
src/main/kotlin/ru/alepar/zx80/cli/ScoreCommand.kt        # WU 2.1a-2 (Decoder() → OpTableBuilder.build())
src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt    # WU 2.1a-2 (use Dispatcher)
src/main/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuite.kt # WU 2.1a-2 (use Dispatcher)
src/test/kotlin/ru/alepar/zx80/harness/suites/FuseSuiteTest.kt # WU 2.1a-2 (constructor change)
src/test/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuiteTest.kt # WU 2.1a-2 (constructor change)
src/test/kotlin/ru/alepar/zx80/cli/ScoreCommandTest.kt    # WU 2.1a-3 onwards (score is now non-zero)
```

---

## WU 2.1a-1 — Dispatcher (Trap A fix)

Implements the prefix-aware fetch-decode helper. After this WU the helper exists and is fully tested with stub Ops, but no real Ops yet exist and no caller has been rewired.

### Task 1: Failing test for Dispatcher main-path lookup

**Files:**
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/DispatcherTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

class DispatcherTest {

    @Test
    fun `decodes unprefixed opcode from main table`() {
        val decoder = Decoder().apply { main[0x42] = StubOp("MAIN_42") }
        val cpu = Cpu().apply { pc = 0x100 }
        val mem = Memory().apply { write(0x100, 0x42) }
        val op = Dispatcher(decoder).decodeAt(cpu, mem)
        assertThat(op).isNotNull
        assertThat(op!!.mnemonic(StubFetcher)).isEqualTo("MAIN_42")
    }

    @Test
    fun `returns null for unimplemented main opcode`() {
        val cpu = Cpu().apply { pc = 0x100 }
        val mem = Memory().apply { write(0x100, 0x99) }
        assertThat(Dispatcher(Decoder()).decodeAt(cpu, mem)).isNull()
    }
}

private class StubOp(val name: String) : Op {
    override val operandLength = 0
    override val baseCycles = 4
    override fun execute(cpu: Cpu, mem: Memory) {}
    override fun mnemonic(operands: OperandFetcher) = name
}

private object StubFetcher : OperandFetcher {
    override fun byteAt(operandIndex: Int) = 0
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests DispatcherTest`
Expected: FAIL with `Unresolved reference: Dispatcher`.

- [ ] **Step 3: Write the minimal implementation**

Create `src/main/kotlin/ru/alepar/zx80/cpu/Dispatcher.kt`:

```kotlin
package ru.alepar.zx80.cpu

import ru.alepar.zx80.op.Op

/**
 * Decodes the instruction at `cpu.pc` by reading bytes from `mem` and
 * following any prefix (CB / ED / DD / FD / DDCB / FDCB) into the right
 * sub-table on the [Decoder]. Returns the matched [Op] or `null` if no
 * Op is installed at the resolved table position.
 *
 * Does NOT advance `cpu.pc` and does NOT touch `cpu.tStates` —
 * `Op.execute` owns those.
 */
class Dispatcher(private val decoder: Decoder) {

    fun decodeAt(cpu: Cpu, mem: Memory): Op? {
        val b0 = mem.read(cpu.pc)
        return when (b0) {
            0xCB -> decoder.cb[mem.read(cpu.pc + 1)]
            0xED -> decoder.ed[mem.read(cpu.pc + 1)]
            0xDD -> decodeIndexed(decoder.dd, decoder.ddcb, cpu, mem)
            0xFD -> decodeIndexed(decoder.fd, decoder.fdcb, cpu, mem)
            else -> decoder.main[b0]
        }
    }

    private fun decodeIndexed(
        prefixTable: Array<Op?>,
        cbTable: Array<Op?>,
        cpu: Cpu,
        mem: Memory,
    ): Op? {
        val b1 = mem.read(cpu.pc + 1)
        return if (b1 == 0xCB) {
            cbTable[mem.read(cpu.pc + 3)]
        } else {
            prefixTable[b1]
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests DispatcherTest`
Expected: 2 tests pass.

### Task 2: Add tests for every prefix path

**Files:**
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/DispatcherTest.kt`

- [ ] **Step 1: Add CB / ED / DD / FD / DDCB / FDCB tests + wrap edge case**

Append the following tests to `DispatcherTest`:

```kotlin
    @Test
    fun `decodes CB-prefixed opcode from cb table`() {
        val decoder = Decoder().apply { cb[0x10] = StubOp("CB_10") }
        val cpu = Cpu().apply { pc = 0x200 }
        val mem = Memory().apply {
            write(0x200, 0xCB)
            write(0x201, 0x10)
        }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("CB_10")
    }

    @Test
    fun `decodes ED-prefixed opcode from ed table`() {
        val decoder = Decoder().apply { ed[0x46] = StubOp("ED_46") }
        val cpu = Cpu().apply { pc = 0x300 }
        val mem = Memory().apply {
            write(0x300, 0xED)
            write(0x301, 0x46)
        }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("ED_46")
    }

    @Test
    fun `decodes DD-prefixed opcode from dd table when second byte is not CB`() {
        val decoder = Decoder().apply { dd[0x21] = StubOp("DD_21") }
        val cpu = Cpu().apply { pc = 0x400 }
        val mem = Memory().apply {
            write(0x400, 0xDD)
            write(0x401, 0x21)
        }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("DD_21")
    }

    @Test
    fun `decodes FD-prefixed opcode from fd table when second byte is not CB`() {
        val decoder = Decoder().apply { fd[0x21] = StubOp("FD_21") }
        val cpu = Cpu().apply { pc = 0x500 }
        val mem = Memory().apply {
            write(0x500, 0xFD)
            write(0x501, 0x21)
        }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("FD_21")
    }

    @Test
    fun `decodes DDCB-prefixed opcode reads opcode byte at pc plus 3 (after displacement)`() {
        val decoder = Decoder().apply { ddcb[0x06] = StubOp("DDCB_06") }
        val cpu = Cpu().apply { pc = 0x600 }
        val mem = Memory().apply {
            write(0x600, 0xDD)
            write(0x601, 0xCB)
            write(0x602, 0x05)   // displacement byte
            write(0x603, 0x06)   // actual opcode
        }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("DDCB_06")
    }

    @Test
    fun `decodes FDCB-prefixed opcode reads opcode byte at pc plus 3 (after displacement)`() {
        val decoder = Decoder().apply { fdcb[0xFE] = StubOp("FDCB_FE") }
        val cpu = Cpu().apply { pc = 0x700 }
        val mem = Memory().apply {
            write(0x700, 0xFD)
            write(0x701, 0xCB)
            write(0x702, 0x00)
            write(0x703, 0xFE)
        }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("FDCB_FE")
    }

    @Test
    fun `DDCB lookup wraps mod 64K when pc near end of memory`() {
        // Place DD CB d xx straddling 0xFFFD..0x0000
        val decoder = Decoder().apply { ddcb[0x42] = StubOp("DDCB_42_WRAP") }
        val cpu = Cpu().apply { pc = 0xFFFD }
        val mem = Memory().apply {
            write(0xFFFD, 0xDD)
            write(0xFFFE, 0xCB)
            write(0xFFFF, 0x00)   // displacement
            write(0x0000, 0x42)   // actual opcode (wrapped)
        }
        assertThat(Dispatcher(decoder).decodeAt(cpu, mem)!!.mnemonic(StubFetcher))
            .isEqualTo("DDCB_42_WRAP")
    }
```

- [ ] **Step 2: Run all DispatcherTest tests**

Run: `./gradlew test --tests DispatcherTest`
Expected: 9 tests pass total (2 from Task 1 + 7 added here).

### Task 3: Verify whole suite + commit

- [ ] **Step 1: Run full test suite + spotless apply**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with all 53 prior tests + 9 new = 62 tests passing.

If `spotlessApply` modifies `Dispatcher.kt` or `DispatcherTest.kt`, that's expected (kotlinlang style); the modified files are picked up by the `git add` in step 2.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Dispatcher.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/DispatcherTest.kt
git commit -m "feat(cpu): Dispatcher follows prefix bytes through to sub-tables

Fixes Trap A from the prior plan's final review. FuseSuite/ProgramsSuite
will be wired to use this in the next WU; for now the helper exists with
full unit-test coverage of all 7 dispatch paths (main, cb, ed, dd, fd,
ddcb, fdcb) including the wrap edge case at pc near 0xFFFD."
```

---

## WU 2.1a-2 — OpTableBuilder skeleton + wire CLI/suites to Dispatcher

After this WU the `Dispatcher` is in active use throughout the codebase, the `OpTableBuilder` pattern exists with an empty `build()`, and the score is still `0.000` (no Ops yet).

### Task 1: Empty OpTableBuilder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/OpTableBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpTableBuilderTest {

    @Test
    fun `build returns a Decoder with no Ops installed yet`() {
        val d = OpTableBuilder.build()
        val tables = listOf(d.main, d.cb, d.ed, d.dd, d.fd, d.ddcb, d.fdcb)
        val totalInstalled = tables.sumOf { table -> table.count { it != null } }
        assertThat(totalInstalled).isZero
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests OpTableBuilderTest`
Expected: FAIL with `Unresolved reference: OpTableBuilder`.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Decoder

/**
 * Builds the populated [Decoder] for production. Per-family fragments
 * (e.g. `MiscOps`, `ExOps`, `LdOps`, ...) own their own opcode-to-Op
 * registrations; this object just calls them in order.
 *
 * Slow startup is acceptable; runtime dispatch is O(1) array index.
 */
object OpTableBuilder {

    fun build(): Decoder {
        val d = Decoder()
        // Family fragments slot in here as they land:
        //   MiscOps.installInto(d)  (WU 2.1a-3)
        //   ExOps.installInto(d)    (WU 2.1a-4)
        //   LdOps.installInto(d)    (Plan 2.1b)
        //   ArithOps.installInto(d) (Plan 2.2)
        //   ...
        return d
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests OpTableBuilderTest`
Expected: 1 test passes.

### Task 2: Rewire FuseSuite to use Dispatcher

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/harness/suites/FuseSuiteTest.kt`

- [ ] **Step 1: Read the existing FuseSuite implementation**

Read: `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt`

Identify two things:
1. The constructor signature `class FuseSuite(private val decoder: Decoder, private val inputs: ..., private val expected: ...)`.
2. Inside `runOne`, the lookup `val op = decoder.main[opcodeByte] ?: return "no op for opcode 0x${"%02X".format(opcodeByte)}"`.

- [ ] **Step 2: Replace decoder.main lookup with Dispatcher.decodeAt**

In `FuseSuite.kt`:

1. Add import: `import ru.alepar.zx80.cpu.Dispatcher`.
2. Add a private field: `private val dispatcher = Dispatcher(decoder)` (after the existing `private val expected: ...` constructor parameter).
3. Inside `runOne`, replace these lines:

   ```kotlin
   val opcodeByte = mem.read(cpu.pc)
   val op = decoder.main[opcodeByte] ?: return "no op for opcode 0x${"%02X".format(opcodeByte)}"
   ```

   with:

   ```kotlin
   val op = dispatcher.decodeAt(cpu, mem) ?: run {
       val opcodeByte = mem.read(cpu.pc)
       return "no op for opcode 0x${"%02X".format(opcodeByte)} (no dispatch route)"
   }
   ```

   Rationale: the old failure message printed only the byte at PC, which is misleading for prefixed cases (it'd say "no op for opcode 0xCB" when the real issue is no entry in the cb sub-table). The new message keeps the byte at PC for context but adds "no dispatch route" so the reader knows the dispatcher returned null.

- [ ] **Step 3: Update FuseSuiteTest to use the new failure message wording**

Find the existing test that asserts on the failure-reason string in `FuseSuiteTest` (specifically the `with empty decoder all cases fail` test or any test that checks failure reasons via `r.details`). Tests currently expect `"no op for opcode 0xXX"`; if any test asserts that exact text, update to also accept `"no op for opcode 0xXX (no dispatch route)"`.

If no test asserts on the specific failure text (only on `passed=0, total=N`), no test changes are needed.

- [ ] **Step 4: Run the FuseSuite tests**

Run: `./gradlew test --tests FuseSuiteTest`
Expected: All 4 FuseSuiteTest tests pass (or whatever count exists).

### Task 3: Rewire ProgramsSuite to use Dispatcher

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuite.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuiteTest.kt`

- [ ] **Step 1: Read the existing ProgramsSuite implementation**

Read: `src/main/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuite.kt`

Identify the constructor `class ProgramsSuite(private val decoder: Decoder, private val programs: List<ProgramFixture>)` and inside `runOne` the lookup roughly like:

```kotlin
val opcodeByte = mem.read(cpu.pc)
val op = decoder.main[opcodeByte]
if (op == null) {
    failure = "no op for opcode 0x${opcodeByte.toString(16)} at pc=0x${cpu.pc.toString(16)}"
    break
}
```

- [ ] **Step 2: Replace decoder.main lookup with Dispatcher.decodeAt**

In `ProgramsSuite.kt`:

1. Add import: `import ru.alepar.zx80.cpu.Dispatcher`.
2. Add a private field: `private val dispatcher = Dispatcher(decoder)`.
3. Inside `runOne`, replace the opcode lookup block with:

   ```kotlin
   val op = dispatcher.decodeAt(cpu, mem)
   if (op == null) {
       val opcodeByte = mem.read(cpu.pc)
       failure = "no op for opcode 0x${opcodeByte.toString(16)} at pc=0x${cpu.pc.toString(16)} (no dispatch route)"
       break
   }
   ```

- [ ] **Step 3: Check ProgramsSuiteTest assertions**

The existing tests in `ProgramsSuiteTest` likely assert that the failure reason contains `"0x76"` or similar substrings. Verify by reading the test file. If a test expects an exact reason string, update to accept the new wording.

- [ ] **Step 4: Run the ProgramsSuite tests**

Run: `./gradlew test --tests ProgramsSuiteTest`
Expected: All ProgramsSuiteTest tests pass.

### Task 4: Wire ScoreCommand to OpTableBuilder

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/ScoreCommand.kt`

- [ ] **Step 1: Read the existing ScoreCommand**

Read: `src/main/kotlin/ru/alepar/zx80/cli/ScoreCommand.kt`

Locate the line `val decoder = Decoder()` (probably with an inline comment about "empty for now").

- [ ] **Step 2: Replace `Decoder()` with `OpTableBuilder.build()`**

In `ScoreCommand.kt`:

1. Add import: `import ru.alepar.zx80.op.OpTableBuilder`.
2. Replace `val decoder = Decoder()` with `val decoder = OpTableBuilder.build()`.
3. Remove the inline comment about "empty for now" (it's no longer accurate as more families land — keeping the line clean).

- [ ] **Step 3: Run the CLI test**

Run: `./gradlew test --tests ScoreCommandTest`
Expected: All ScoreCommandTest tests pass; the existing `score on empty decoder reports SCORE colon zero` test still passes because the builder is currently empty (still produces SCORE 0.000).

### Task 5: Verify whole suite + commit

- [ ] **Step 1: Run full test suite + spotless apply**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with 63 tests passing (62 prior + 1 new OpTableBuilderTest).

- [ ] **Step 2: End-to-end smoke check**

Run:
```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected stdout: `SCORE: 0.000  (opcodes 0/1792, fuse 0/1356, programs 0/1)` — same as before this WU. (The Dispatcher rewiring is invisible in the score because no Ops have been installed yet.)

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt \
        src/test/kotlin/ru/alepar/zx80/op/OpTableBuilderTest.kt \
        src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt \
        src/main/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuite.kt \
        src/main/kotlin/ru/alepar/zx80/cli/ScoreCommand.kt \
        src/test/kotlin/ru/alepar/zx80/harness/suites/FuseSuiteTest.kt \
        src/test/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuiteTest.kt
git commit -m "feat(harness): OpTableBuilder + Dispatcher wired into CLI and suites

Score is still 0.000 (no Ops installed yet) but every consumer now uses
Dispatcher and OpTableBuilder. Adding a new Op family in the next WU is
a single line in OpTableBuilder.build()."
```

---

## WU 2.1a-3 — Misc family (NOP, HALT, DI, EI, IM)

Five new Op classes. After this WU the score climbs above zero for the first time, `nop_loop` passes, and at least 7 opcodes are installed.

### Task 1: Nop

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Nop.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/NopTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class NopTest {
    @Test
    fun `Nop advances pc by 1, increments r by 1, adds 4 T-states, leaves all else untouched`() {
        val cpu = Cpu().apply {
            pc = 0x1000; r = 0x10; tStates = 100L
            a = 0x55; f = 0xAA; b = 0x11; bc = 0x1122
        }
        Nop.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x1001)
        assertThat(cpu.r).isEqualTo(0x11)
        assertThat(cpu.tStates).isEqualTo(104L)
        assertThat(cpu.a).isEqualTo(0x55)
        assertThat(cpu.f).isEqualTo(0xAA)
        assertThat(cpu.bc).isEqualTo(0x1122)
    }

    @Test
    fun `Nop r increment wraps within bottom 7 bits, top bit preserved`() {
        val cpu = Cpu().apply { r = 0xFF }      // bottom 7 bits = 0x7F, top bit = 1
        Nop.execute(cpu, Memory())
        // Bottom 7 bits go 0x7F → 0x00; top bit stays at 1; result = 0x80
        assertThat(cpu.r).isEqualTo(0x80)
    }

    @Test
    fun `Nop pc wrap mod 64K`() {
        val cpu = Cpu().apply { pc = 0xFFFF }
        Nop.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x0000)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Nop.mnemonic { 0 }).isEqualTo("NOP")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests NopTest`
Expected: FAIL with `Unresolved reference: Nop`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Nop : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "NOP"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests NopTest`
Expected: 4 tests pass.

### Task 2: Halt

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Halt.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/HaltTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class HaltTest {
    @Test
    fun `Halt sets halted flag, advances pc by 1, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L; halted = false }
        Halt.execute(cpu, Memory())
        assertThat(cpu.halted).isTrue
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `Halt does not touch flags`() {
        val cpu = Cpu().apply { pc = 0; f = 0xFF }
        Halt.execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Halt.mnemonic { 0 }).isEqualTo("HALT")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests HaltTest`
Expected: FAIL with `Unresolved reference: Halt`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Halt : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.halted = true
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "HALT"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests HaltTest`
Expected: 3 tests pass.

### Task 3: Di

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Di.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/DiTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class DiTest {
    @Test
    fun `Di clears iff1 and iff2, advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            iff1 = true; iff2 = true
        }
        Di.execute(cpu, Memory())
        assertThat(cpu.iff1).isFalse
        assertThat(cpu.iff2).isFalse
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `Di on already-clear iff1 iff2 stays clear`() {
        val cpu = Cpu().apply { iff1 = false; iff2 = false }
        Di.execute(cpu, Memory())
        assertThat(cpu.iff1).isFalse
        assertThat(cpu.iff2).isFalse
    }

    @Test
    fun `mnemonic`() {
        assertThat(Di.mnemonic { 0 }).isEqualTo("DI")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests DiTest`
Expected: FAIL with `Unresolved reference: Di`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Di : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.iff1 = false
        cpu.iff2 = false
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DI"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests DiTest`
Expected: 3 tests pass.

### Task 4: Ei

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Ei.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/EiTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class EiTest {
    @Test
    fun `Ei sets iff1 and iff2, advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply {
            pc = 0x100; r = 0; tStates = 0L
            iff1 = false; iff2 = false
        }
        Ei.execute(cpu, Memory())
        assertThat(cpu.iff1).isTrue
        assertThat(cpu.iff2).isTrue
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ei.mnemonic { 0 }).isEqualTo("EI")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests EiTest`
Expected: FAIL with `Unresolved reference: Ei`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Ei : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.iff1 = true
        cpu.iff2 = true
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EI"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests EiTest`
Expected: 2 tests pass.

### Task 5: Im (parameterized for modes 0/1/2)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/Im.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/ImTest.kt`

`Im` is the only parameterized class in this batch. It's an ED-prefixed instruction so PC advances by 2, r increments by 2, T-states is 8.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ImTest {
    @Test
    fun `Im(0) sets im=0, advances pc by 2, r by 2, adds 8 T-states`() {
        val cpu = Cpu().apply { pc = 0x100; r = 0x10; tStates = 0L; im = 1 }
        Im(0).execute(cpu, Memory())
        assertThat(cpu.im).isZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(0x12)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `Im(1) sets im=1`() {
        val cpu = Cpu().apply { im = 0 }
        Im(1).execute(cpu, Memory())
        assertThat(cpu.im).isEqualTo(1)
    }

    @Test
    fun `Im(2) sets im=2`() {
        val cpu = Cpu().apply { im = 0 }
        Im(2).execute(cpu, Memory())
        assertThat(cpu.im).isEqualTo(2)
    }

    @Test
    fun `Im rejects mode outside 0..2`() {
        assertThatThrownBy { Im(3) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Im(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic includes mode`() {
        assertThat(Im(0).mnemonic { 0 }).isEqualTo("IM 0")
        assertThat(Im(1).mnemonic { 0 }).isEqualTo("IM 1")
        assertThat(Im(2).mnemonic { 0 }).isEqualTo("IM 2")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests ImTest`
Expected: FAIL with `Unresolved reference: Im`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

class Im(private val mode: Int) : Op {
    init {
        require(mode in 0..2) { "IM mode must be 0, 1, or 2; got $mode" }
    }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.im = mode
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 2) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "IM $mode"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests ImTest`
Expected: 5 tests pass.

### Task 6: MiscOps fragment + wire into OpTableBuilder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/misc/MiscOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/misc/MiscOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class MiscOpsTest {
    @Test
    fun `installInto registers Nop, Halt, Di, Ei in main table`() {
        val d = Decoder()
        MiscOps.installInto(d)
        assertThat(d.main[0x00]).isSameAs(Nop)
        assertThat(d.main[0x76]).isSameAs(Halt)
        assertThat(d.main[0xF3]).isSameAs(Di)
        assertThat(d.main[0xFB]).isSameAs(Ei)
    }

    @Test
    fun `installInto registers IM 0, IM 1, IM 2 in ed table`() {
        val d = Decoder()
        MiscOps.installInto(d)
        assertThat(d.ed[0x46]).isInstanceOf(Im::class.java)
        assertThat(d.ed[0x56]).isInstanceOf(Im::class.java)
        assertThat(d.ed[0x5E]).isInstanceOf(Im::class.java)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests MiscOpsTest`
Expected: FAIL with `Unresolved reference: MiscOps`.

- [ ] **Step 3: Write the MiscOps fragment**

```kotlin
package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the misc Op family (NOP, HALT, DI, EI, IM 0/1/2) into the
 * decoder. Called by [ru.alepar.zx80.op.OpTableBuilder].
 */
object MiscOps {
    fun installInto(d: Decoder) {
        d.main[0x00] = Nop
        d.main[0x76] = Halt
        d.main[0xF3] = Di
        d.main[0xFB] = Ei
        d.ed[0x46] = Im(0)
        d.ed[0x56] = Im(1)
        d.ed[0x5E] = Im(2)
    }
}
```

- [ ] **Step 4: Wire MiscOps into OpTableBuilder**

In `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`, add the import and call:

1. Add: `import ru.alepar.zx80.op.misc.MiscOps`
2. In `build()`, add `MiscOps.installInto(d)` (between the `val d = Decoder()` line and the `return d` line; before the comment block listing future fragments).

The file should now look like:

```kotlin
package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.misc.MiscOps

object OpTableBuilder {

    fun build(): Decoder {
        val d = Decoder()
        MiscOps.installInto(d)
        // Future: ExOps.installInto(d)    (WU 2.1a-4)
        // Future: LdOps.installInto(d)    (Plan 2.1b)
        // ...
        return d
    }
}
```

- [ ] **Step 5: Update OpTableBuilderTest to expect non-zero installations**

Replace the body of the existing `build returns a Decoder with no Ops installed yet` test with a new test that asserts the misc family has been installed:

```kotlin
package ru.alepar.zx80.op

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpTableBuilderTest {

    @Test
    fun `build returns a Decoder with at least the misc family installed`() {
        val d = OpTableBuilder.build()
        // Spot check: NOP at 0x00 and IM 1 at ED 0x56
        assertThat(d.main[0x00]).isNotNull
        assertThat(d.ed[0x56]).isNotNull
    }
}
```

- [ ] **Step 6: Run the misc tests + the OpTableBuilder test + the score-CLI test**

Run: `./gradlew test --tests "MiscOpsTest" --tests "OpTableBuilderTest" --tests "ScoreCommandTest"`

Expected: MiscOps and OpTableBuilder tests pass. The pre-existing `score on empty decoder reports SCORE colon zero` test in `ScoreCommandTest` will likely **fail** now because the headline number is no longer 0.000.

- [ ] **Step 7: Update ScoreCommandTest for non-zero score**

Open `src/test/kotlin/ru/alepar/zx80/cli/ScoreCommandTest.kt`. Find the test `score on empty decoder reports SCORE colon zero`. Update it:

1. Rename to `score reports headline with installed opcode count`.
2. Change the assertion to check that the headline contains `"opcodes"` and a non-zero passed count, but not assert on the exact composite score (which depends on which FUSE cases pass — varies as more Ops land):

```kotlin
@Test
fun `score reports headline with non-zero opcode count`() {
    val cli = Zx80Cli().subcommands(
        ScoreCommand(), RunCommand(), DisasmCommand(), BenchCommand(), ZexdocCommand()
    )
    val result = cli.test("score")
    assertThat(result.statusCode).isZero
    assertThat(result.stdout).contains("SCORE: ")
    assertThat(result.stdout).contains("opcodes ")
    assertThat(result.stdout).doesNotContain("opcodes 0/")    // we now have some installed
    assertThat(result.stdout).contains("programs 1/1")        // nop_loop now passes
}
```

(The other two tests in `ScoreCommandTest` — `score with suite filter runs only one suite` and the unknown-suite test — should still pass; only the headline-number assertion changes.)

- [ ] **Step 8: Run the full suite**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with all tests passing. New count: ~63 prior + 4 (Nop) + 3 (Halt) + 3 (Di) + 2 (Ei) + 5 (Im) + 2 (MiscOps) = ~82 tests.

- [ ] **Step 9: End-to-end smoke**

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected: a non-zero composite score, `opcodes 7/1792`, `programs 1/1`, and a non-zero `fuse N/1356` count.

Capture the actual output and proceed if it looks reasonable. (E.g. `SCORE: 0.012  (opcodes 7/1792, fuse 18/1356, programs 1/1)` — exact numbers depend on which FUSE cases the misc family covers.)

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/misc/ \
        src/test/kotlin/ru/alepar/zx80/op/misc/ \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt \
        src/test/kotlin/ru/alepar/zx80/op/OpTableBuilderTest.kt \
        src/test/kotlin/ru/alepar/zx80/cli/ScoreCommandTest.kt
git commit -m "feat(op/misc): NOP, HALT, DI, EI, IM — first non-zero score

7 opcodes installed (4 main + 3 ED). nop_loop program passes.
ScoreCommandTest no longer asserts on the exact 0.000 headline."
```

---

## WU 2.1a-4 — EX family (EX AF,AF', EXX, EX DE,HL, EX (SP),HL)

Four new Op classes. `EX (SP),HL` is the only one that touches memory (and the only one above 4 T-states).

### Task 1: ExAfAfAlt

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ex/ExAfAfAlt.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ex/ExAfAfAltTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ExAfAfAltTest {
    @Test
    fun `ExAfAfAlt swaps a with aAlt and f with fAlt`() {
        val cpu = Cpu().apply {
            a = 0x11; f = 0x22
            aAlt = 0x33; fAlt = 0x44
        }
        ExAfAfAlt.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x33)
        assertThat(cpu.f).isEqualTo(0x44)
        assertThat(cpu.aAlt).isEqualTo(0x11)
        assertThat(cpu.fAlt).isEqualTo(0x22)
    }

    @Test
    fun `ExAfAfAlt advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply { pc = 0x500; r = 5; tStates = 0L }
        ExAfAfAlt.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x501)
        assertThat(cpu.r).isEqualTo(6)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `ExAfAfAlt does NOT touch other registers`() {
        val cpu = Cpu().apply {
            b = 0x11; c = 0x22; d = 0x33; e = 0x44; h = 0x55; l = 0x66
            bAlt = 0x77; cAlt = 0x88; dAlt = 0x99; eAlt = 0xAA; hAlt = 0xBB; lAlt = 0xCC
        }
        ExAfAfAlt.execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x11); assertThat(cpu.c).isEqualTo(0x22)
        assertThat(cpu.d).isEqualTo(0x33); assertThat(cpu.e).isEqualTo(0x44)
        assertThat(cpu.h).isEqualTo(0x55); assertThat(cpu.l).isEqualTo(0x66)
        assertThat(cpu.bAlt).isEqualTo(0x77); assertThat(cpu.cAlt).isEqualTo(0x88)
        assertThat(cpu.dAlt).isEqualTo(0x99); assertThat(cpu.eAlt).isEqualTo(0xAA)
        assertThat(cpu.hAlt).isEqualTo(0xBB); assertThat(cpu.lAlt).isEqualTo(0xCC)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ExAfAfAlt.mnemonic { 0 }).isEqualTo("EX AF, AF'")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests ExAfAfAltTest`
Expected: FAIL with `Unresolved reference: ExAfAfAlt`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object ExAfAfAlt : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a; val f = cpu.f
        cpu.a = cpu.aAlt; cpu.f = cpu.fAlt
        cpu.aAlt = a; cpu.fAlt = f
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EX AF, AF'"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests ExAfAfAltTest`
Expected: 4 tests pass.

### Task 2: Exx

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ex/Exx.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ex/ExxTest.kt`

`EXX` swaps BC↔BC', DE↔DE', HL↔HL'. AF/AF' are NOT touched.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ExxTest {
    @Test
    fun `Exx swaps bc with bcAlt, de with deAlt, hl with hlAlt`() {
        val cpu = Cpu().apply {
            b = 0x11; c = 0x22; d = 0x33; e = 0x44; h = 0x55; l = 0x66
            bAlt = 0x77; cAlt = 0x88; dAlt = 0x99; eAlt = 0xAA; hAlt = 0xBB; lAlt = 0xCC
        }
        Exx.execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x77); assertThat(cpu.c).isEqualTo(0x88)
        assertThat(cpu.d).isEqualTo(0x99); assertThat(cpu.e).isEqualTo(0xAA)
        assertThat(cpu.h).isEqualTo(0xBB); assertThat(cpu.l).isEqualTo(0xCC)
        assertThat(cpu.bAlt).isEqualTo(0x11); assertThat(cpu.cAlt).isEqualTo(0x22)
        assertThat(cpu.dAlt).isEqualTo(0x33); assertThat(cpu.eAlt).isEqualTo(0x44)
        assertThat(cpu.hAlt).isEqualTo(0x55); assertThat(cpu.lAlt).isEqualTo(0x66)
    }

    @Test
    fun `Exx does NOT touch a, f, aAlt, fAlt`() {
        val cpu = Cpu().apply {
            a = 0xAA; f = 0xBB; aAlt = 0xCC; fAlt = 0xDD
        }
        Exx.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xAA); assertThat(cpu.f).isEqualTo(0xBB)
        assertThat(cpu.aAlt).isEqualTo(0xCC); assertThat(cpu.fAlt).isEqualTo(0xDD)
    }

    @Test
    fun `Exx advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply { pc = 0x500; r = 5; tStates = 0L }
        Exx.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x501)
        assertThat(cpu.r).isEqualTo(6)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Exx.mnemonic { 0 }).isEqualTo("EXX")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests ExxTest`
Expected: FAIL with `Unresolved reference: Exx`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Exx : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val b = cpu.b; val c = cpu.c; val d = cpu.d; val e = cpu.e; val h = cpu.h; val l = cpu.l
        cpu.b = cpu.bAlt; cpu.c = cpu.cAlt
        cpu.d = cpu.dAlt; cpu.e = cpu.eAlt
        cpu.h = cpu.hAlt; cpu.l = cpu.lAlt
        cpu.bAlt = b; cpu.cAlt = c
        cpu.dAlt = d; cpu.eAlt = e
        cpu.hAlt = h; cpu.lAlt = l
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EXX"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests ExxTest`
Expected: 4 tests pass.

### Task 3: ExDeHl

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ex/ExDeHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ex/ExDeHlTest.kt`

Swaps the main DE with the main HL. Does NOT touch the alternate set.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ExDeHlTest {
    @Test
    fun `ExDeHl swaps de with hl`() {
        val cpu = Cpu().apply {
            d = 0x11; e = 0x22; h = 0x33; l = 0x44
        }
        ExDeHl.execute(cpu, Memory())
        assertThat(cpu.d).isEqualTo(0x33); assertThat(cpu.e).isEqualTo(0x44)
        assertThat(cpu.h).isEqualTo(0x11); assertThat(cpu.l).isEqualTo(0x22)
    }

    @Test
    fun `ExDeHl does NOT touch alternate registers`() {
        val cpu = Cpu().apply { dAlt = 0xAA; eAlt = 0xBB; hAlt = 0xCC; lAlt = 0xDD }
        ExDeHl.execute(cpu, Memory())
        assertThat(cpu.dAlt).isEqualTo(0xAA); assertThat(cpu.eAlt).isEqualTo(0xBB)
        assertThat(cpu.hAlt).isEqualTo(0xCC); assertThat(cpu.lAlt).isEqualTo(0xDD)
    }

    @Test
    fun `ExDeHl advances pc, increments r, adds 4 T-states`() {
        val cpu = Cpu().apply { pc = 0x500; r = 5; tStates = 0L }
        ExDeHl.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x501)
        assertThat(cpu.r).isEqualTo(6)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ExDeHl.mnemonic { 0 }).isEqualTo("EX DE, HL")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests ExDeHlTest`
Expected: FAIL with `Unresolved reference: ExDeHl`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object ExDeHl : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = cpu.d; val e = cpu.e
        cpu.d = cpu.h; cpu.e = cpu.l
        cpu.h = d; cpu.l = e
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EX DE, HL"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests ExDeHlTest`
Expected: 4 tests pass.

### Task 4: ExSpHl (the tricky one)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ex/ExSpHl.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ex/ExSpHlTest.kt`

`EX (SP),HL` exchanges the byte at `(SP)` with `L`, and the byte at `(SP+1)` with `H`. Z80 is little-endian: low byte at the lower address. 19 T-states. Does NOT touch flags.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ExSpHlTest {
    @Test
    fun `ExSpHl swaps L with byte at SP and H with byte at SP+1`() {
        val cpu = Cpu().apply {
            sp = 0x4000
            h = 0xAB; l = 0xCD
        }
        val mem = Memory().apply {
            write(0x4000, 0x12)   // (SP) — will become L
            write(0x4001, 0x34)   // (SP+1) — will become H
        }
        ExSpHl.execute(cpu, mem)
        assertThat(cpu.l).isEqualTo(0x12)
        assertThat(cpu.h).isEqualTo(0x34)
        assertThat(mem.read(0x4000)).isEqualTo(0xCD)   // old L
        assertThat(mem.read(0x4001)).isEqualTo(0xAB)   // old H
    }

    @Test
    fun `ExSpHl does NOT change SP itself`() {
        val cpu = Cpu().apply { sp = 0x4000 }
        ExSpHl.execute(cpu, Memory())
        assertThat(cpu.sp).isEqualTo(0x4000)
    }

    @Test
    fun `ExSpHl does NOT touch flags`() {
        val cpu = Cpu().apply { sp = 0x4000; f = 0xAA }
        ExSpHl.execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `ExSpHl advances pc, increments r, adds 19 T-states`() {
        val cpu = Cpu().apply { pc = 0x500; r = 5; tStates = 0L; sp = 0x4000 }
        ExSpHl.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x501)
        assertThat(cpu.r).isEqualTo(6)
        assertThat(cpu.tStates).isEqualTo(19L)
    }

    @Test
    fun `ExSpHl wraps SP+1 mod 64K`() {
        val cpu = Cpu().apply { sp = 0xFFFF; h = 0x11; l = 0x22 }
        val mem = Memory().apply {
            write(0xFFFF, 0x33)
            write(0x0000, 0x44)
        }
        ExSpHl.execute(cpu, mem)
        assertThat(cpu.l).isEqualTo(0x33)
        assertThat(cpu.h).isEqualTo(0x44)
        assertThat(mem.read(0xFFFF)).isEqualTo(0x22)
        assertThat(mem.read(0x0000)).isEqualTo(0x11)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ExSpHl.mnemonic { 0 }).isEqualTo("EX (SP), HL")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests ExSpHlTest`
Expected: FAIL with `Unresolved reference: ExSpHl`.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object ExSpHl : Op {
    override val operandLength = 0
    override val baseCycles = 19

    override fun execute(cpu: Cpu, mem: Memory) {
        val low = mem.read(cpu.sp)
        val high = mem.read(cpu.sp + 1)
        mem.write(cpu.sp, cpu.l)
        mem.write(cpu.sp + 1, cpu.h)
        cpu.l = low
        cpu.h = high
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EX (SP), HL"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests ExSpHlTest`
Expected: 6 tests pass.

### Task 5: ExOps fragment + wire into OpTableBuilder

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ex/ExOps.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ex/ExOpsTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class ExOpsTest {
    @Test
    fun `installInto registers ExAfAfAlt at 0x08, Exx at 0xD9, ExDeHl at 0xEB, ExSpHl at 0xE3`() {
        val d = Decoder()
        ExOps.installInto(d)
        assertThat(d.main[0x08]).isSameAs(ExAfAfAlt)
        assertThat(d.main[0xD9]).isSameAs(Exx)
        assertThat(d.main[0xEB]).isSameAs(ExDeHl)
        assertThat(d.main[0xE3]).isSameAs(ExSpHl)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests ExOpsTest`
Expected: FAIL with `Unresolved reference: ExOps`.

- [ ] **Step 3: Write the ExOps fragment**

```kotlin
package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the EX Op family (EX AF,AF'; EXX; EX DE,HL; EX (SP),HL) into
 * the decoder. Called by [ru.alepar.zx80.op.OpTableBuilder].
 */
object ExOps {
    fun installInto(d: Decoder) {
        d.main[0x08] = ExAfAfAlt
        d.main[0xD9] = Exx
        d.main[0xEB] = ExDeHl
        d.main[0xE3] = ExSpHl
    }
}
```

- [ ] **Step 4: Wire ExOps into OpTableBuilder**

Modify `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:

1. Add: `import ru.alepar.zx80.op.ex.ExOps`
2. In `build()`, add `ExOps.installInto(d)` (right after the `MiscOps.installInto(d)` line).

The file should now look like:

```kotlin
package ru.alepar.zx80.op

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.ex.ExOps
import ru.alepar.zx80.op.misc.MiscOps

object OpTableBuilder {

    fun build(): Decoder {
        val d = Decoder()
        MiscOps.installInto(d)
        ExOps.installInto(d)
        // Future: LdOps.installInto(d)    (Plan 2.1b)
        // ...
        return d
    }
}
```

- [ ] **Step 5: Run all the EX tests**

Run: `./gradlew test --tests "Ex*Test"`
Expected: ExAfAfAltTest (4) + ExxTest (4) + ExDeHlTest (4) + ExSpHlTest (6) + ExOpsTest (1) = 19 tests pass.

- [ ] **Step 6: Run the full suite + spotless apply**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL with all tests passing. New count: ~82 prior + 4+4+4+6+1 = ~101 tests.

- [ ] **Step 7: End-to-end smoke**

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

Expected: composite score has climbed; opcode count is now `11/1792` (4 misc main + 3 misc ed + 4 ex main); fuse count is higher than after WU 2.1a-3; programs still `1/1`.

Capture the actual headline string for the commit message.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ex/ \
        src/test/kotlin/ru/alepar/zx80/op/ex/ \
        src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt
git commit -m "feat(op/ex): EX AF/AF', EXX, EX DE/HL, EX (SP)/HL

Four more opcodes in the main table. Total now 11 opcodes installed.
Composite score continues to climb."
```

---

## WU 2.1a-5 — Sweep + tag

Final verification. No new code; just confirming the contract holds end-to-end and tagging the milestone.

### Task 1: Full clean build + verification

- [ ] **Step 1: Clean build from scratch**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL, all ~101 tests passing, `build/install/zx80/bin/zx80` produced.

- [ ] **Step 2: Run the CLI and capture exact output**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: a `SCORE: <non-zero>  (opcodes 11/1792, fuse <N>/1356, programs 1/1)` line.

- [ ] **Step 3: Inspect score.json**

```bash
head -20 build/score.json
```

Expected: well-formed JSON with `score` > 0, `suites.opcodes.passed = 11`, `suites.programs.passed = 1`, `suites.fuse.passed > 0`.

- [ ] **Step 4: Verify all stub commands still work**

```bash
./build/install/zx80/bin/zx80 run 2>&1 | head -1
./build/install/zx80/bin/zx80 disasm 2>&1 | head -1
./build/install/zx80/bin/zx80 bench 2>&1 | head -1
./build/install/zx80/bin/zx80 zexdoc 2>&1 | head -1
```

Expected: each prints "not yet implemented" to stderr and exits 0.

- [ ] **Step 5: Spotless final check**

Run: `./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run a single FUSE case manually (sanity check)**

This is informal verification that the dispatcher + an Op work correctly together end-to-end. Pick a simple test case from the FUSE corpus that exercises a freshly-implemented Op. The case named `00` is `NOP` — its expected state is "PC = initial+1, T-states = 4". Verify the FUSE pass-rate includes this case by inspecting the failures list in `build/score.json`:

```bash
python3 -c "import json; d = json.load(open('build/score.json')); failures = d['suites']['fuse']['details'].get('failures', []); print(f'first 5 failures: {failures[:5]}')"
```

Expected: failures list does NOT include `00` (because NOP works). If `00` is in the failures, debug — it should be passing.

### Task 2: Tag the milestone

- [ ] **Step 1: Verify clean tree**

```bash
git status
```
Expected: `nothing to commit, working tree clean`.

- [ ] **Step 2: Create the annotated tag**

```bash
git tag -a m1-phase02-1a -m "M1 Phase 2.1a complete: dispatcher, OpTableBuilder, misc + EX families

Trap A fixed (Dispatcher follows prefix bytes through to sub-tables).
OpTableBuilder pattern established (per-family fragments register into
the decoder). 11 opcodes installed: NOP, HALT, DI, EI, IM 0/1/2,
EX AF/AF', EXX, EX DE/HL, EX (SP)/HL. nop_loop program passes; the
composite score is non-zero for the first time.

Plan 2.1b (LD family) is the next plan — adding a new family is one
new fragment file plus one line in OpTableBuilder.build()."
```

- [ ] **Step 3: Verify the tag**

```bash
git tag --list 'm1-*'
git show m1-phase02-1a | head -20
```

Expected: tag `m1-phase02-1a` listed alongside `m1-phase01-harness-baseline`; the show output displays the tag annotation followed by the most recent commit.

This plan is complete.

---

## Self-Review

Performed inline. Notes:

1. **Spec coverage:** Every section of `docs/superpowers/specs/2026-05-02-zx80-m1-phase2-1a-foundation-design.md` maps to a task. Dispatcher → WU 2.1a-1; OpTableBuilder + CLI/suite rewiring → WU 2.1a-2; Misc family → WU 2.1a-3; EX family → WU 2.1a-4; Sweep + tag → WU 2.1a-5. The per-op spec table (cycles, r increment, mnemonic) maps to the assertions in each Op's TDD test.

2. **Placeholder scan:** No "TBD" / "TODO" / "implement later" in the plan. Every step that changes code shows the actual code. No "similar to Task N" — patterns are restated when they recur (e.g. each Op's commit-and-test pattern).

3. **Type consistency:** `Op.operandLength` and `Op.baseCycles` are property names matching the existing `Op` interface from WU4 of the prior plan. `Cpu.r` is incremented as `(cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)` consistently — preserves the high bit, increments the low 7. PC advance uses `(cpu.pc + N) and 0xFFFF` consistently. T-state updates use `cpu.tStates += baseCycles`. Mnemonic format is "MNEMONIC OPERAND, OPERAND" (with comma-space) as in `EX AF, AF'`.

4. **`Reg` enum unused in this batch:** Confirmed. None of misc/EX ops use the `Reg` enum since they don't take a register-coded `r` field. `Reg` first matters in plan 2.1b when LD ops appear.

5. **One known plan choice:** WU 2.1a-3 Step 7 changes a pre-existing test in `ScoreCommandTest`. This is the one place a previously-green test would otherwise become red after this WU lands; the plan handles it explicitly by updating the assertion in the same WU.

6. **Mnemonic spacing:** `EX AF, AF'` and `EX (SP), HL` use a comma-then-space separator. `IM 0` uses a space. Consistent with the canonical Z80 mnemonics that disassemblers commonly emit.

No issues found requiring further plan revision.
