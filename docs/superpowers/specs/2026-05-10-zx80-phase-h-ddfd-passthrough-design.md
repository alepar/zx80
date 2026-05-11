# Phase H: DD/FD-as-noop Opcode Coverage — Design

## Goal

Install `DdFdPrefixPassthrough(wrapped: Op)` wrappers at every currently-null
`dd[i]` / `fd[i]` slot where `main[i]` is non-null. Runtime behavior is
identical to the existing `DdFdNopPrefix` fallback + main-table dispatch,
but the table entries are now populated, so the `OpcodeCoverage` suite
counts them. Composite SCORE expected to climb from 0.966 toward ~0.99.

## Context

Beads issue: `zx80-6q9`.

After Phase F, `Dispatcher.decodeAt` returns `DdFdNopPrefix` for any null
slot in `dd[]` / `fd[]` — a singleton Op that advances PC by 1, bumps R,
adds 4 T-states, and lets the next dispatch handle the trailing opcode
unprefixed. Functionally correct.

But the `OpcodeCoverage` suite counts table entries directly:
```kotlin
val passed = tables.sumOf { it.second.count { op -> op != null } }
```
The 296 currently-null slots therefore drag the opcode score down to
1496/1792 = 83.5%, even though every Z80 instruction the CPU actually
executes works correctly. Phase H fills these slots with passthrough
wrappers so the score reflects actual coverage.

Open question whether Phase H also unsticks the M2.2 FRAMES ROM-init bug
at PC=0x11E6 (filed as `zx80-1qc`): if the ROM hits a previously-null
slot, behavior is unchanged (passthrough = NopPrefix + main); if the bug
is in an existing op's flags, Phase H doesn't help. We verify in the
sweep.

## Scope

### H-A: DdFdPrefixPassthrough class

New `src/main/kotlin/ru/alepar/zx80/op/ix/DdFdPrefixPassthrough.kt`:

```kotlin
package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * Wraps a main-table Op for a DD/FD prefix slot where the prefix is a no-op:
 * adds the prefix's 4 T-states, 1 R, 1 PC, then runs the wrapped op as if
 * the prefix weren't there. Identical net effect to [DdFdNopPrefix] followed
 * by the wrapped op on the next dispatch.
 *
 * Installed by [IxOps.installInto] for every (dd, fd) slot where the main
 * table has a non-null op and the IX/IY-aware version is also absent.
 */
class DdFdPrefixPassthrough(private val wrapped: Op) : Op {
    override val operandLength = 1 + wrapped.operandLength
    override val baseCycles = 4 + wrapped.baseCycles

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.bumpR()
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.tStates += 4
        wrapped.execute(cpu, mem)
    }

    override fun mnemonic(operands: OperandFetcher): String =
        "DD/FD ${wrapped.mnemonic(operands)}"
}
```

Tests (`DdFdPrefixPassthroughTest`):
- Wrap a known no-op-like op (e.g. `Nop` from `op/misc/Nop.kt`); execute
  from PC=0x100, R=0, tStates=0. Verify PC=0x102 (1 prefix + 1 NOP), R=2
  (prefix + NOP each bump), tStates=8 (4 prefix + 4 NOP).
- `operandLength == 1 + wrapped.operandLength` for a multi-byte op
  (e.g. wrap `LdRegImm(Reg.A)` which has operandLength=1; passthrough
  reports 2).
- `baseCycles == 4 + wrapped.baseCycles`.
- `mnemonic` includes the wrapped op's mnemonic with a "DD/FD" prefix.

### H-B: IxOps install loop

Modify `src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt`. At the END of
`installInto(d: Decoder)`, after all existing IX-aware installs, append:

```kotlin
        // Phase H: any dd/fd slot still null where the unprefixed main op exists is a
        // "prefix-as-noop" combo. Install a passthrough wrapper so OpcodeCoverage counts
        // these slots and runtime behavior matches the prior DdFdNopPrefix fallback path.
        for (i in 0..255) {
            val main = d.main[i] ?: continue
            if (d.dd[i] == null) d.dd[i] = DdFdPrefixPassthrough(main)
            if (d.fd[i] == null) d.fd[i] = DdFdPrefixPassthrough(main)
        }
```

Slots where `main[i]` is itself null (0xCB / 0xDD / 0xED / 0xFD — all
prefixes) are skipped. The existing `DdFdNopPrefix` fallback in
`Dispatcher.decodeIndexed` continues to handle prefix-chains (DD DD,
DD ED, etc.) the same way as before — those slots stay null, the
dispatcher returns the singleton fallback.

Tests (extend `IxOpsTest`):
- After `OpTableBuilder.build()`, count non-null entries in `decoder.dd[]`
  and `decoder.fd[]` — assert each is at least 252 (out of 256, minus
  the four prefix slots).
- Spot-check that `decoder.dd[0x00]` is a `DdFdPrefixPassthrough` whose
  wrapped op is the same as `decoder.main[0x00]`.
- Spot-check that `decoder.dd[0xCB]` is still null (the dispatcher's
  ddcb path handles that; install loop skipped because main[0xCB] is
  null).
- Spot-check that a previously IX-aware slot is untouched: e.g.,
  `decoder.dd[0x21]` (LD IX, nn) is NOT a `DdFdPrefixPassthrough` — it's
  the existing IX-aware op. The install loop only touches null slots.

Extend `OpcodeCoverageTest`:
- Run the suite, assert `passed >= 1748`. (Approximate: 1496 baseline +
  ~252 dd + ~252 fd, minus a handful of fd[]/dd[] slots that were already
  filled.)

### H-C: Sweep + tag

Standard sweep:
1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. All new tests pass.
3. Existing M1 + M2 tests still pass — runtime behavior is unchanged.
4. ZEXDOC clean (0 IllegalStateException, 0 ERROR).
5. FUSE: 1354/1356 — Phase H doesn't touch FUSE-specific paths.
6. Programs: 5/5.
7. Composite SCORE: ≥ 0.99 (target). Minimum acceptable: ≥ 0.98.
8. Spot-check: the 2 known FUSE residuals (`ed5f`, `ed7d`) are unchanged
   — Phase H is orthogonal.

Tag: `m1-phase-h` (extends the M1 phase line, since this is opcode
coverage, not M2 machine work).

Bonus check (informational, not a gate): re-run the M2.2 FRAMES stretch
gate from the M2.2 spec — write a temporary `Spectrum48k().reset();
repeat(50) { runFrame() }` test reading `mem.read(0x5C78)`. If it now
reads a non-zero value, Phase H accidentally unstuck the ROM (file as a
note on `zx80-1qc`). If still 0, the ROM bug is elsewhere — leave
`zx80-1qc` open.

### Out of scope

- Filling the 4 prefix slots (CB/DD/ED/FD in dd/fd tables) — handled by
  the runtime `DdFdNopPrefix` fallback for prefix-chains.
- Filling ddcb/fdcb null slots — those have their own coverage story.
- The 2 FUSE residuals (`zx80-b0c`) — separate correctness work.
- Diagnosing the M2.2 FRAMES bug if Phase H doesn't fix it.

## Architecture

```
src/main/kotlin/ru/alepar/zx80/
  op/ix/DdFdPrefixPassthrough.kt   NEW
  op/ix/IxOps.kt                   MODIFY (install loop at end of installInto)
src/test/kotlin/ru/alepar/zx80/
  op/ix/DdFdPrefixPassthroughTest.kt   NEW (4 assertions)
  op/ix/IxOpsTest.kt                    EXTEND (4 assertions)
  harness/suites/OpcodeCoverageTest.kt  EXTEND (1 assertion)
```

**Runtime behavior is unchanged.** `DdFdPrefixPassthrough(main[i])` and
`DdFdNopPrefix → main[i]` produce identical post-execute state:
- PC: original + 1 (prefix) + main[i]'s own advance (operand bytes + 1).
- R: bumped twice (once for prefix, once for main, same as today's
  two-step dispatch).
- T-states: 4 (prefix) + main[i].baseCycles.
- All register/flag mutations: whatever main[i].execute does.

**OpcodeCoverage score impact.** Best estimate: ~252 new dd[] entries +
~252 new fd[] entries. Some fd[] slots may already be IX-aware (the
IxOps install loop populates both DD and FD symmetrically), so the
actual count may be slightly lower; the test threshold (`passed >= 1748`)
allows for variance.

## Test strategy

### DdFdPrefixPassthroughTest — 4 assertions

```kotlin
package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.misc.Nop

class DdFdPrefixPassthroughTest {
    @Test
    fun `wrapping NOP: PC advances by 2, R bumped twice, T-states += 8`() {
        val passthrough = DdFdPrefixPassthrough(Nop)
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L }
        passthrough.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `operandLength is 1 plus wrapped`() {
        // Nop's operandLength is 0; passthrough should be 1.
        val passthrough = DdFdPrefixPassthrough(Nop)
        assertThat(passthrough.operandLength).isEqualTo(1)
    }

    @Test
    fun `baseCycles is 4 plus wrapped`() {
        // Nop's baseCycles is 4; passthrough should be 8.
        val passthrough = DdFdPrefixPassthrough(Nop)
        assertThat(passthrough.baseCycles).isEqualTo(8)
    }

    @Test
    fun `mnemonic includes DD slash FD prefix`() {
        val passthrough = DdFdPrefixPassthrough(Nop)
        assertThat(passthrough.mnemonic { 0 }).startsWith("DD/FD ")
    }
}
```

### IxOpsTest extension — 4 assertions

```kotlin
@Test
fun `Phase H install loop fills dd null slots with passthroughs`() {
    val decoder = OpTableBuilder.build()
    val ddNonNull = (0..255).count { decoder.dd[it] != null }
    assertThat(ddNonNull).isGreaterThanOrEqualTo(252) // 256 - 4 prefix slots
}

@Test
fun `Phase H install loop fills fd null slots with passthroughs`() {
    val decoder = OpTableBuilder.build()
    val fdNonNull = (0..255).count { decoder.fd[it] != null }
    assertThat(fdNonNull).isGreaterThanOrEqualTo(252)
}

@Test
fun `dd 0x00 is a passthrough wrapping the main NOP`() {
    val decoder = OpTableBuilder.build()
    val op = decoder.dd[0x00]
    assertThat(op).isInstanceOf(DdFdPrefixPassthrough::class.java)
}

@Test
fun `dd 0x21 (LD IX, nn) is the IX-aware op, not a passthrough`() {
    val decoder = OpTableBuilder.build()
    val op = decoder.dd[0x21]
    assertThat(op).isNotNull
    assertThat(op).isNotInstanceOf(DdFdPrefixPassthrough::class.java)
}
```

### OpcodeCoverageTest extension — 1 assertion

```kotlin
@Test
fun `Phase H raises opcode coverage to at least 1748 of 1792`() {
    val decoder = OpTableBuilder.build()
    val suite = OpcodeCoverage(decoder)
    val result = suite.run()
    assertThat(result.passed).isGreaterThanOrEqualTo(1748)
}
```

Total: 9 new assertions across 1 new test file + 2 extended.

## Validation gates (H-C)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. New tests pass (9 assertions).
3. Existing tests still pass — runtime behavior unchanged.
4. ZEXDOC: 0 IllegalStateException, 0 ERROR.
5. FUSE: 1354/1356 (unchanged).
6. Programs: 5/5.
7. Composite SCORE: ≥ 0.99 target, ≥ 0.98 minimum.
8. Bonus FRAMES check: temporary 50-frame test confirms whether `zx80-1qc`
   is incidentally fixed (informational; tag regardless).

Tag: `m1-phase-h`.

## Work-unit breakdown

| WU | Description |
|---|---|
| H-A | `DdFdPrefixPassthrough` class + `DdFdPrefixPassthroughTest` (4 assertions). |
| H-B | IxOps install loop at end of `installInto` + extend `IxOpsTest` (4 assertions) + extend `OpcodeCoverageTest` (1 assertion). |
| H-C | Sweep + bonus FRAMES check + tag `m1-phase-h` + push. |

Within-phase deps: H-A → H-B (B uses the wrapper class). H-C depends on
both.

## Risks

- **Tests that assume specific dd/fd slots are null.** Grep for
  `decoder.dd[` and `decoder.fd[` in existing tests; any that assert
  `null` for an opcode where `main[i]` is non-null will now fail and
  needs updating. Expected count: low; existing tests mostly assert
  specific instance types.
- **Wrapped Op state mutation.** Wrapped Ops are singletons / stateless
  (Kotlin `object` instances or instance-per-pattern with no per-instance
  state). Wrapping the same Op in two passthrough slots is safe. Verify
  by inspecting a couple representative Ops.
- **`Nop` import.** If `Nop` is named differently (e.g., `NopOp`, `Noop`),
  the test imports need updating. Check `op/misc/` for the exact class
  name. Spot-check this in H-A.
- **Whether Phase H unsticks ROM.** Unknown until H-C bonus check. If it
  doesn't, the M2.2 FRAMES bug is in an existing op's flag handling or
  in a different opcode path; further triage stays open in `zx80-1qc`.
