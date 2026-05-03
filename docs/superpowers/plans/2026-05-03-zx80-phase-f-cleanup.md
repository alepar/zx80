# Phase F — Comprehensive Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lift FUSE pass rate from 1195/1356 (88.1%) to ≥99% by closing three independent gap categories: unmapped opcode slots (~132 cases), FUSE port-read simulation (~28 cases), HALT PC behavior (1 case).

**Architecture:** Three independent sub-fixes. F-1 changes opcode-table installations (DDCB/FDCB BIT mirror, ED unmapped → `EdNop`). F-2 extends `FuseTestParser` to read PR events from `tests.expected`, adds `QueueIoBus`, wires into `FuseSuite`. F-3 changes `Halt.execute` to not advance PC (Z80 hardware behavior).

**Tech Stack:** Kotlin (JDK 21), JUnit 5, AssertJ, Gradle, Spotless ktlint.

**Spec:** `docs/superpowers/specs/2026-05-03-zx80-phase-f-cleanup-design.md`

---

## Universal patterns

**TDD per task:** failing test → run → confirm FAIL → modify → re-run → confirm PASS.

**Test name rules:** avoid `..`, `->`, `;` in backtick names.

**Beads workflow per WU:** claim → execute → `./gradlew test spotlessApply` → commit → `bd close <id>`.

## File Structure

**New files:**
- `src/main/kotlin/ru/alepar/zx80/op/ed/EdNop.kt` — singleton 8-cycle NOP for ED unmapped slots (WU F-1)
- `src/test/kotlin/ru/alepar/zx80/op/ed/EdNopTest.kt` (WU F-1)
- `src/main/kotlin/ru/alepar/zx80/harness/io/QueueIoBus.kt` — list-backed IoBus for FUSE (WU F-2)
- `src/test/kotlin/ru/alepar/zx80/harness/io/QueueIoBusTest.kt` (WU F-2)

**Modified files:**
- `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt` — `installBit` fills 8 rrr slots per (n, prefix) (WU F-1)
- `src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt` — fill remaining null `d.ed[i]` with `EdNop` (WU F-1)
- `src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt` — assertions for BIT mirror (WU F-1)
- `src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt` — assertions for `EdNop` (WU F-1)
- `src/main/kotlin/ru/alepar/zx80/harness/fuse/FuseTestParser.kt` — parse PR events from `tests.expected` (WU F-2)
- `src/test/kotlin/ru/alepar/zx80/harness/fuse/FuseTestParserTest.kt` — verify PR-event parsing (WU F-2)
- `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt` — install `QueueIoBus` per-test (WU F-2)
- `src/main/kotlin/ru/alepar/zx80/op/misc/Halt.kt` — don't advance PC (WU F-3)
- `src/test/kotlin/ru/alepar/zx80/op/misc/HaltTest.kt` — flip PC-advance assertions (WU F-3)

---

## WU F-1 — Opcode coverage (~132 residuals)

Beads: `<BD-ID-WU-F-1>`. Three sub-fixes in one WU.

### Task 1: DDCB/FDCB BIT mirror slots

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt`

- [ ] **Step 1: Append failing test**

```kotlin
@Test
fun `installBit fills all 8 rrr slots per (n, prefix) with the same BitIxd instance`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    // For BIT 0,(IX+d): documented at DDCB 0x46 (rrr=110); mirror slots at 0x40-0x45, 0x47.
    val canonical = d.ddcb[0x46]
    assertThat(canonical).isInstanceOf(BitIxd::class.java)
    for (rrrBits in 0..7) {
        val opcode = 0x40 or rrrBits
        assertThat(d.ddcb[opcode]).isSameAs(canonical)
    }
}

@Test
fun `installBit fills mirror slots for FD prefix and all bit positions`() {
    val d = Decoder()
    IxCbOps.installInto(d)
    // BIT 7,(IY+d) at FDCB 0x40+0x38+0x06 = 0x7E; mirrors at 0x78-0x7D, 0x7F
    val canonical = d.fdcb[0x7E]
    for (rrrBits in 0..7) {
        val opcode = 0x40 or (7 shl 3) or rrrBits
        assertThat(d.fdcb[opcode]).isSameAs(canonical)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: FAIL — currently only the rrr=6 slot is non-null.

- [ ] **Step 3: Update IxCbOps.installBit**

In `IxCbOps.kt`, find the existing `installBit` method (around line 35-40). Replace its body:

```kotlin
private fun installBit(table: Array<Op?>, idx: IndexReg) {
    for (n in 0..7) {
        val opInstance = BitIxd(idx, n)
        for (rrrBits in 0..7) {
            val opcode = 0x40 or (n shl 3) or rrrBits
            table[opcode] = opInstance
        }
    }
}
```

The same `opInstance` is installed at all 8 rrr slots — Z80 quirk: BIT n,r,(IX+d) at the mirror slots (rrr ≠ 6) behaves identically to the documented BIT n,(IX+d) form (BIT has no result; the register field is ignored).

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IxCbOpsTest`
Expected: all pass.

### Task 2: EdNop class + install in unused ED slots

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/op/ed/EdNop.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/op/ed/EdNopTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt`

- [ ] **Step 1: Write the failing EdNop test**

`src/test/kotlin/ru/alepar/zx80/op/ed/EdNopTest.kt`:

```kotlin
package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class EdNopTest {
    @Test
    fun `EdNop advances pc by 2, bumps r by 2, adds 8 T-states, leaves all else untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x42
                f = 0x55
            }
        EdNop.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f).isEqualTo(0x55)
    }

    @Test
    fun `EdNop mnemonic and metadata`() {
        assertThat(EdNop.mnemonic { 0 }).isEqualTo("NOP*")
        assertThat(EdNop.operandLength).isZero
        assertThat(EdNop.baseCycles).isEqualTo(8)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests EdNopTest`
Expected: FAIL with `Unresolved reference: EdNop`.

- [ ] **Step 3: Implement EdNop**

`src/main/kotlin/ru/alepar/zx80/op/ed/EdNop.kt`:

```kotlin
package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `NOP*` — installed at every ED-prefixed opcode slot that is not a documented Z80 instruction
 * (e.g. ED 0x00-0x3F, ED 0x80-0x9F minus block ops, ED 0xC0-0xFF). Real Z80 hardware treats these
 * as 8-cycle NOPs that advance PC by 2 (the prefix byte + opcode byte). Phase F installs this
 * blanket fallback after all real ED ops have been registered, filling any still-null slots.
 */
object EdNop : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "NOP*"
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests EdNopTest`
Expected: 2 tests pass.

- [ ] **Step 5: Append failing test to EdOpsTest**

```kotlin
@Test
fun `installInto fills all null ED slots with EdNop`() {
    val d = Decoder()
    EdOps.installInto(d)
    // Spot-check: ED 0x00 (no documented op there) is now EdNop
    assertThat(d.ed[0x00]).isSameAs(EdNop)
    // ED 0xFF (no documented op) is also EdNop
    assertThat(d.ed[0xFF]).isSameAs(EdNop)
    // Documented ED slots are NOT replaced — ED 0x44 = NEG should remain
    assertThat(d.ed[0x44]).isNotSameAs(EdNop)
    // Total ED slots are now 256/256 non-null
    val nonNull = (0..255).count { d.ed[it] != null }
    assertThat(nonNull).isEqualTo(256)
}
```

- [ ] **Step 6: Run to verify it fails**

Run: `./gradlew test --tests EdOpsTest`
Expected: FAIL — d.ed[0x00] and d.ed[0xFF] currently null.

- [ ] **Step 7: Update EdOps.installInto to fill remaining slots**

In `src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt`, modify `installInto` to add a final pass that fills any still-null slots:

```kotlin
fun installInto(d: Decoder) {
    installRegisterTransfers(d)
    installNeg(d)
    installReturns(d)
    installRrdRld(d)
    installExtendedLdPair(d)
    installBlockMove(d)
    installBlockCompare(d)
    installSingleIo(d)
    installBlockIo(d)
    installMainTableIoStragglers(d)
    installEdNopFallback(d)
}

private fun installEdNopFallback(d: Decoder) {
    for (i in 0..255) {
        if (d.ed[i] == null) {
            d.ed[i] = EdNop
        }
    }
}
```

- [ ] **Step 8: Run to verify pass**

Run: `./gradlew test --tests EdOpsTest`
Expected: all pass.

### Task 3: Investigate the 2 DD/FD slot residuals

The Phase E report mentioned "2 DD/FD no-dispatch" but didn't enumerate which. Find them via FUSE failures.

- [ ] **Step 1: Identify the 2 slots**

Run: `./gradlew installDist && ./build/install/zx80/bin/zx80 score --suite=fuse 2>&1; cat build/score.json | grep -A5 '"failures"' | grep -E '"(dd|fd)[0-9a-f]{2}' | head`

Look for `ddXX` or `fdXX` pattern entries in failures. Each names the prefix byte and the byte after.

- [ ] **Step 2: For each failing slot, install per Z80 spec**

For each unmapped DD/FD opcode found in Step 1, the Z80 hardware behavior is one of:
- The non-prefix opcode does not touch H/L or (HL): prefix is a no-op with +4 T-states. Install a `LdRegRegPrefixed`-like wrapper class that delegates to the unprefixed op with PC+=2, bumpR(2), and the unprefixed op's T-states + 4.
- The opcode does touch H/L: usually documented (covered by Phase 2.8). If a documented slot is still null, that's a Phase 2.8 bug — surface and fix.

If both slots turn out to be the "no-op prefix on a non-half opcode" pattern, add minimal wrapper Ops:

```kotlin
// In src/main/kotlin/ru/alepar/zx80/op/ix/PrefixPassthrough.kt (NEW FILE)
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * Used for DD/FD-prefixed opcodes whose unprefixed form does NOT touch H/L or (HL). Z80 hardware
 * behavior: prefix is effectively a no-op; the underlying op runs with PC += 2 (instead of += 1)
 * and an extra 4 T-states. Phase F installs this for the few DD/FD slots that ZEXDOC/FUSE
 * dispatch hits and that aren't covered by Phase 2.8.
 */
class PrefixPassthrough(private val unprefixed: Op) : Op {
    override val operandLength = unprefixed.operandLength
    override val baseCycles = unprefixed.baseCycles + 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val prevPc = cpu.pc
        unprefixed.execute(cpu, mem)
        cpu.pc = (prevPc + 2 + unprefixed.operandLength) and 0xFFFF
        // unprefixed.execute already did bumpR(1) and added base T-states; add 1 more bumpR for prefix
        cpu.bumpR()
        cpu.tStates += 4
    }

    override fun mnemonic(operands: OperandFetcher) = unprefixed.mnemonic(operands)
}
```

(This wrapper is invasive — only use if Step 1 surfaces specific failing slots that need it. If Step 1 finds the 2 failing slots are something simpler (e.g. a typo in Phase 2.8 install), apply the minimal fix.)

If Step 1 finds 0 failing slots (the "2" estimate from Phase E was off), skip Task 3 entirely and document in the WU close reason.

- [ ] **Step 3: Run FUSE again to verify**

Run: `./build/install/zx80/bin/zx80 score --suite=fuse | head -1`
Expected: dd/fd-prefix failures gone.

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Capture FUSE delta**

Run: `./build/install/zx80/bin/zx80 score`
Expected: FUSE pass count climbed by ~132 from 1195 (target ~1327).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/EdNop.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/IxCbOpsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/EdNopTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt
# also any DD/FD additions from Task 3
git commit -m "feat(op): close opcode-coverage gaps for FUSE — BIT mirror + ED NOPs

Phase F WU 1 of 4. Three sub-fixes:
1. DDCB/FDCB BIT mirror: install the same BitIxd instance at all 8 rrr
   slots per (n, prefix). Phase 2.13 deliberately skipped these mirror
   slots (rrr != 6) because ZEXDOC didn't need them; FUSE does.
2. EdNop: 8-cycle NOP installed at every ED slot that wasn't otherwise
   filled (ED 00-3F, parts of 80-9F, C0-FF). Real Z80 treats these as
   2-byte 8-T NOPs.
3. (If applicable) DD/FD slots: install per Z80 spec.

Closes <BD-ID-WU-F-1>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Close beads**

Run: `bd close <BD-ID-WU-F-1>`

---

## WU F-2 — FUSE-aware IoBus (~28 residuals)

Beads: `<BD-ID-WU-F-2>`. Three sub-tasks: parser extension, QueueIoBus class, FuseSuite wiring.

### Task 1: Extend FuseTestParser to parse PR events from `tests.expected`

PR events are in the **expected** file (`tests.expected`), among the event lines that the parser currently ignores. Format example:

```
edb2_03
    0 MC 0000
    4 MR 0000 ED
    8 MC 0001
   12 MR 0001 B2
   16 MC 5C5C
   20 MR 5C5C 12
   24 PR 0701 47
   28 MC 5C5C
...
```

The `24 PR 0701 47` line means: at T-state 24, port 0x0701 was read and the byte 0x47 was returned. We need to capture these per test and stash them on `FuseExpectedCase`.

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/fuse/FuseTestParser.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/harness/fuse/FuseTestParserTest.kt`

- [ ] **Step 1: Append failing parser test**

```kotlin
@Test
fun `parseExpected captures PR events with port and byte`() {
    val expectedText =
        """
        bigtest
            0 MC 0000
            4 MR 0000 DB
            8 MC 0001
           12 MR 0001 04
           16 PR 4204 99
        FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF 0002 0000
        FF 02 0 0 0 0 11
        """.trimIndent().lineSequence()
    val cases = FuseTestParser.parseExpected(expectedText)
    assertThat(cases).hasSize(1)
    val case = cases[0]
    assertThat(case.name).isEqualTo("bigtest")
    assertThat(case.portReads).hasSize(1)
    assertThat(case.portReads[0].port).isEqualTo(0x4204)
    assertThat(case.portReads[0].byte).isEqualTo(0x99)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests FuseTestParserTest`
Expected: FAIL with `Unresolved reference: portReads`.

- [ ] **Step 3: Add PortRead data class + portReads to FuseExpectedCase**

In `FuseTestParser.kt`, add at the top:

```kotlin
/** A port-read event from a FUSE expected file. */
data class PortRead(val port: Int, val byte: Int)
```

Add `portReads: List<PortRead>` to `FuseExpectedCase`:

```kotlin
data class FuseExpectedCase(
    val name: String,
    // ... existing fields ...
    val tStatesAfter: Int,
    val memory: List<Pair<Int, ByteArray>>,
    val portReads: List<PortRead>,
)
```

- [ ] **Step 4: Update parseExpected to extract PR events**

Find `parseExpected` in `FuseTestParser.kt`. There's a section that reads event lines (currently ignored). Change to capture PRs:

```kotlin
fun parseExpected(lines: Sequence<String>): List<FuseExpectedCase> {
    val it = lines.iterator()
    val result = mutableListOf<FuseExpectedCase>()
    while (true) {
        val name = it.nextNonBlankOrNull() ?: break
        // Read event lines (start with whitespace) — capture PR events, skip others
        val portReads = mutableListOf<PortRead>()
        var stateLine: String? = null
        while (it.hasNext()) {
            val line = it.next()
            if (line.isBlank()) continue
            if (line.firstOrNull()?.isWhitespace() == true) {
                // event line — check if it's a PR (4 tokens: tstate, "PR", port, byte)
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[1] == "PR") {
                    portReads.add(
                        PortRead(
                            port = parts[2].toInt(16),
                            byte = parts[3].toInt(16),
                        ),
                    )
                }
                continue
            }
            stateLine = line
            break
        }
        val regs =
            (stateLine ?: error("missing state line for '$name'"))
                .splitTokens(STATE_TOKEN_COUNT, name)
        val ctrl =
            it.nextRequired("control line for '$name'").splitTokens(CONTROL_TOKEN_COUNT, name)
        val mem = readExpectedMemory(it)
        result.add(
            FuseExpectedCase(
                name = name,
                af = regs[0].toInt(16),
                bc = regs[1].toInt(16),
                de = regs[2].toInt(16),
                hl = regs[3].toInt(16),
                afAlt = regs[4].toInt(16),
                bcAlt = regs[5].toInt(16),
                deAlt = regs[6].toInt(16),
                hlAlt = regs[7].toInt(16),
                ix = regs[8].toInt(16),
                iy = regs[9].toInt(16),
                sp = regs[10].toInt(16),
                pc = regs[11].toInt(16),
                memptr = regs[12].toInt(16),
                i = ctrl[0].toInt(16),
                r = ctrl[1].toInt(16),
                iff1 = ctrl[2].toBoolFlexible(),
                iff2 = ctrl[3].toBoolFlexible(),
                im = ctrl[4].toInt(),
                halted = ctrl[5] == "1",
                tStatesAfter = ctrl[6].toInt(),
                memory = mem,
                portReads = portReads.toList(),
            ),
        )
    }
    return result
}
```

(The exact code structure in the existing file may differ slightly; adapt to match. The key change is the loop that processes event lines now also collects PRs.)

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew test --tests FuseTestParserTest`
Expected: all pass.

### Task 2: QueueIoBus class

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/harness/io/QueueIoBus.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/harness/io/QueueIoBusTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.alepar.zx80.harness.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.harness.fuse.PortRead

class QueueIoBusTest {
    @Test
    fun `read returns bytes in order then 0xFF when queue is empty`() {
        val bus = QueueIoBus(listOf(PortRead(0x100, 0x55), PortRead(0x101, 0xAA)))
        assertThat(bus.read(0x100)).isEqualTo(0x55)
        assertThat(bus.read(0x101)).isEqualTo(0xAA)
        // Queue exhausted — fallback to 0xFF (matches the default IoBus behavior)
        assertThat(bus.read(0x999)).isEqualTo(0xFF)
    }

    @Test
    fun `write is a no-op (FUSE doesn't validate ports)`() {
        val bus = QueueIoBus(emptyList())
        bus.write(0x100, 0x42) // should not throw
    }

    @Test
    fun `read ignores port number — pops in order regardless of port`() {
        // FUSE's PR events list port for documentation; the byte is what matters.
        val bus = QueueIoBus(listOf(PortRead(0x100, 0x11), PortRead(0x200, 0x22)))
        // Read with a totally different port — still returns 0x11 (the queue head)
        assertThat(bus.read(0xFFFF)).isEqualTo(0x11)
        assertThat(bus.read(0x0000)).isEqualTo(0x22)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests QueueIoBusTest`
Expected: FAIL with `Unresolved reference: QueueIoBus`.

- [ ] **Step 3: Implement QueueIoBus**

```kotlin
package ru.alepar.zx80.harness.io

import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.harness.fuse.PortRead

/**
 * Drives `cpu.io` from a pre-recorded list of PR (port read) events extracted from a FUSE expected
 * case. Reads pop the queue head and return its byte value; once exhausted, returns 0xFF (matching
 * the project's default no-op IoBus). Writes are dropped — FUSE does not currently validate port
 * writes.
 */
class QueueIoBus(reads: List<PortRead>) : IoBus {
    private val pending = ArrayDeque(reads)

    override fun read(port: Int): Int {
        val next = pending.removeFirstOrNull() ?: return 0xFF
        return next.byte and 0xFF
    }

    override fun write(port: Int, byte: Int) {
        // No-op for FUSE; PW events not validated.
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests QueueIoBusTest`
Expected: 3 tests pass.

### Task 3: Wire QueueIoBus into FuseSuite

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt`

- [ ] **Step 1: Update FuseSuite.runOne to install per-test QueueIoBus**

In `FuseSuite.kt`, find `runOne(input, want)` (around line 60-120). After loading the cpu state but before the dispatch loop, add:

```kotlin
private fun runOne(input: FuseInputCase, want: FuseExpectedCase): String? {
    val cpu = Cpu().apply { loadFrom(input) }
    cpu.io = QueueIoBus(want.portReads)        // NEW — Phase F
    val mem = Memory().apply {
        for ((addr, bytes) in input.memory) {
            for ((offset, b) in bytes.withIndex()) {
                write(addr + offset, b.toInt() and 0xFF)
            }
        }
    }
    // ... rest of method unchanged ...
}
```

Add the imports at the top of `FuseSuite.kt`:
```kotlin
import ru.alepar.zx80.harness.io.QueueIoBus
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. FUSE pass rate should now climb by ~28.

### Task 4: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`

- [ ] **Step 2: Capture FUSE delta**

Run: `./build/install/zx80/bin/zx80 score`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/harness/fuse/FuseTestParser.kt \
        src/main/kotlin/ru/alepar/zx80/harness/io/QueueIoBus.kt \
        src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt \
        src/test/kotlin/ru/alepar/zx80/harness/fuse/FuseTestParserTest.kt \
        src/test/kotlin/ru/alepar/zx80/harness/io/QueueIoBusTest.kt
git commit -m "feat(harness): FUSE-aware IoBus drives cpu.io from PR events

Phase F WU 2 of 4. FuseTestParser now extracts PR (port read) events
from tests.expected. New QueueIoBus pops bytes from a per-test queue
seeded with those events. FuseSuite installs a fresh QueueIoBus on
cpu.io before each test. Closes ~28 IN/OUT FUSE failures that were
caused by the default IoBus returning 0xFF instead of FUSE-specified
bytes — these are harness gaps, not CPU bugs.

Closes <BD-ID-WU-F-2>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Close beads**

Run: `bd close <BD-ID-WU-F-2>`

---

## WU F-3 — HALT PC fix (1 residual)

Beads: `<BD-ID-WU-F-3>`. One-line fix.

### Task 1: Update Halt.execute

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/misc/Halt.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/misc/HaltTest.kt`

- [ ] **Step 1: Update HaltTest expectations**

Read `src/test/kotlin/ru/alepar/zx80/op/misc/HaltTest.kt`. Existing tests likely assert PC advanced by 1. Find and reverse those assertions, e.g.:

```kotlin
@Test
fun `HALT does NOT advance PC (Z80 hardware leaves PC at HALT byte)`() {
    val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L }
    Halt.execute(cpu, Memory())
    assertThat(cpu.pc).isEqualTo(0x100) // PC stays AT the HALT byte
    assertThat(cpu.halted).isTrue
    assertThat(cpu.r).isEqualTo(1)
    assertThat(cpu.tStates).isEqualTo(4L)
}
```

If existing tests asserted `cpu.pc).isEqualTo(0x101)`, change to `0x100`. Identify each test and update accordingly.

- [ ] **Step 2: Run to verify failures (existing tests now mismatch the asserted-0x100 expectation)**

Run: `./gradlew test --tests HaltTest`
Expected: FAIL — Halt currently advances PC.

- [ ] **Step 3: Update Halt.execute**

In `src/main/kotlin/ru/alepar/zx80/op/misc/Halt.kt`, replace `execute`:

```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    cpu.halted = true
    cpu.bumpR()
    cpu.tStates += baseCycles
    // Z80 hardware behavior: PC stays at the HALT byte. The dispatcher's
    // !cpu.halted guard prevents re-execution; on INT acknowledge, the
    // interrupt mechanism is responsible for advancing PC past the HALT.
}
```

(Removed the `cpu.pc = (cpu.pc + 1) and 0xFFFF` line.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests HaltTest`
Expected: all pass.

### Task 2: Sanity-check the dispatcher loops

The CPU run loops in `ZexdocRunner` and `FuseSuite` and `ProgramsSuite` use a `while (!cpu.halted)` (or similar) loop. Verify that the loop terminates correctly when HALT is hit and PC stays put. If any loop also asserts PC has advanced past HALT, adjust.

- [ ] **Step 1: Search for halt-related assertions in run loops**

Run: `grep -rn "halted\|HALT" src/main/kotlin/ru/alepar/zx80/zexdoc/ src/main/kotlin/ru/alepar/zx80/harness/ src/main/kotlin/ru/alepar/zx80/cli/RunCommand.kt`

If any code expects PC to have advanced past HALT, fix it. Otherwise nothing to change.

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/misc/Halt.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/HaltTest.kt
git commit -m "fix(op/misc): HALT does not advance PC (Z80 hardware behavior)

Phase F WU 3 of 4. Real Z80 leaves PC at the HALT byte during halted
state, so the next M1 cycle re-fetches HALT (idle until INT/NMI). We
were advancing PC by 1, which left FUSE test '76' failing on the
post-state PC. The dispatcher's !cpu.halted guard prevents
re-execution; INT acknowledge (when implemented) is responsible for
advancing PC past HALT.

Closes <BD-ID-WU-F-3>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-F-3>`

---

## WU F-4 — Sweep + tag m1-phase-f

Beads: `<BD-ID-WU-F-4>`.

### Task 1: Verification

- [ ] **Step 1: Run full suite**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: ZEXDOC regression check**

Run: `timeout 600 ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc-f.log`

Expected: 0 IllegalStateException, 0 ERROR markers in `/tmp/zexdoc-f.log`. Streamed output ends with `Tests complete`.

If regression detected, STOP — Phase F introduced a break. Investigate before tagging.

- [ ] **Step 3: Capture FUSE delta + score**

Run: `./build/install/zx80/bin/zx80 score`
Expected SCORE: composite ≥ 0.92. FUSE pass count ≥ 1340/1356 (≥ 98.8%). Capture exact line.

If FUSE < 98%, examine `build/score.json` failures. Likely candidates:
- A specific PR event format we didn't parse correctly (e.g. a multi-byte port read pattern)
- A residual flag bug surfaced by passing more tests
- An ED slot we erroneously masked over

If only HALT/edge-cases remain (1-2 cases): Phase F is complete; tag.

If significant residuals: triage and decide whether to fix-in-place or file Phase G.

- [ ] **Step 4: Apply tag**

```bash
git tag -a m1-phase-f -m "Phase F: comprehensive cleanup (DDCB BIT mirror + ED NOPs + FUSE IoBus + HALT)

Three independent sub-fixes targeting Phase E residuals:
- DDCB/FDCB BIT mirror slots installed at all 8 rrr per (n, prefix)
- EdNop fills remaining null ED slots as 8-cycle 2-byte NOPs
- FUSE-aware QueueIoBus drives cpu.io from per-test PR events
- HALT no longer advances PC (Z80 hardware behavior)

SCORE: <PASTE-SCORE-LINE-HERE>
FUSE pass: <X>/1356 (was 1195/1356 after Phase E)
ZEXDOC: clean, regression-free."
```

Replace placeholders with captured values from steps 2-3.

### Task 2: Close beads + epic

- [ ] **Step 1: Close WU F-4**

Run: `bd close <BD-ID-WU-F-4> --reason="Phase F sweep complete; m1-phase-f tag applied; FUSE pass-rate <X>/1356."`

- [ ] **Step 2: Close Phase F epic**

Run: `bd close <BD-ID-EPIC-F> --reason="All 4 WUs (F-1 through F-4) closed; FUSE >= 98%."`

- [ ] **Step 3: File Phase G if requested**

If user wants to push toward score 1.0, file Phase G covering remaining opcode-coverage gap (DD/FD-as-noop on all main slots, ~600 install positions). Otherwise skip.

---

## Self-Review

**1. Spec coverage:**

| Spec section | Plan task |
|---|---|
| DDCB/FDCB BIT mirror at 8 rrr per (n, prefix) | WU F-1 Task 1 |
| EdNop singleton + fallback installer | WU F-1 Task 2 |
| 2 DD/FD residual slots | WU F-1 Task 3 |
| FuseTestParser PR events | WU F-2 Task 1 |
| QueueIoBus class | WU F-2 Task 2 |
| FuseSuite QueueIoBus wiring | WU F-2 Task 3 |
| Halt PC fix | WU F-3 Task 1 |
| ZEXDOC regression-clean, FUSE ≥98%, score ≥0.92 | WU F-4 Task 1 |
| Tag m1-phase-f | WU F-4 Task 1 Step 4 |
| Conditional Phase G filing | WU F-4 Task 2 Step 3 |

All spec sections covered.

**2. Placeholder scan:** Plan uses `<BD-ID-WU-F-N>`, `<BD-ID-EPIC-F>`, `<PASTE-SCORE-LINE-HERE>`, `<X>` — runtime substitutions filled in by the executing agent. Documented usage. Task 3 of WU F-1 says "if Step 1 surfaces specific failing slots" which is conditional — that's deliberate (the residual count was reported as 2 but specific slots weren't enumerated; the agent must investigate first).

**3. Type consistency:** `EdNop` is a singleton object referenced uniformly. `PortRead(port, byte)` data class used in both parser test and `QueueIoBus`. `FuseExpectedCase` gains `portReads: List<PortRead>` field with consistent usage in `FuseSuite`. `QueueIoBus(reads: List<PortRead>) : IoBus` — matches the existing `IoBus` interface (read/write).

**4. Test name lint:** No backtick names contain `..`, `->`, or `;`.
