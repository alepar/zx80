# M1 Residuals: 2 FUSE Failures (ed5f + ed7d) — Design

## Goal

Close the last 2 FUSE failures (`ed5f LD A,R` and `ed7d RETI` alias) to
bring FUSE pass rate to 1356/1356 (100%). Composite SCORE creeps from
0.997 to ~0.998. No new architecture; surgical fixes only.

## Context

Beads issue: `zx80-b0c`.

After Phase G, FUSE was 1354/1356 — the two residuals are well-localized:

1. **ed5f LD A,R** — `af mismatch: 0xF3A1 vs 0xF5A1`. The high byte (A)
   differs by 2. Most likely root cause: our code reads R BEFORE
   `bumpR(2)`, but real Z80 increments R during the M1 fetch cycles for
   the prefix and opcode BEFORE the copy stage. So real hardware copies
   the post-increment R into A; we copy the pre-increment value.

2. **ed7d RETI alias** — `iff1 mismatch`. Per Sean Young's TUZD, RETI
   restores IFF1 from IFF2 identically to RETN. Current `Reti.execute`
   only pops PC; the IFF1 restoration line is missing. The ed7d slot is
   one of the Phase G RETI alias positions, which is why this only
   surfaced post-Phase G even though canonical RETI (ed4d) has the same
   bug.

## Scope

### residuals-A: Fix RETI iff1 restoration

Modify `src/main/kotlin/ru/alepar/zx80/op/ed/Reti.kt`:

```kotlin
object Reti : Op {
    override val operandLength = 0
    override val baseCycles = 14

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = cpu.pop(mem)
        cpu.iff1 = cpu.iff2    // NEW: mirror Retn's IFF1 restoration
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RETI"
}
```

Also update the kdoc: change "behaves identically to RET" to "behaves
identically to RETN (restores IFF1 from IFF2)".

Add a unit test (extend `RetiTest.kt` if it exists, otherwise create it):

```kotlin
@Test
fun `RETI restores iff1 from iff2`() {
    val cpu = Cpu().apply {
        sp = 0xFFFE
        iff1 = false
        iff2 = true
    }
    val mem = Memory().apply {
        write(0xFFFE, 0x34) // return address low
        write(0xFFFF, 0x12) // return address high
    }
    Reti.execute(cpu, mem)
    assertThat(cpu.pc).isEqualTo(0x1234)
    assertThat(cpu.iff1).isTrue
    assertThat(cpu.iff2).isTrue
}
```

### residuals-B: Fix LD A,R R-bump ordering

Modify `src/main/kotlin/ru/alepar/zx80/op/ed/LdAR.kt`. Real Z80
behavior: the R register is incremented at the END of each M1 fetch
cycle. A 2-byte instruction (ED prefix + opcode) has two M1 fetches, so
R is bumped twice BEFORE the instruction's "copy" stage. The current
code reads R first and bumps last; swap the order:

```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    cpu.bumpR(2)                    // R bumped during fetch, BEFORE the copy
    cpu.a = cpu.r and 0xFF
    var f = cpu.f and Flags.C
    if (cpu.a == 0) f = f or Flags.Z
    if (cpu.a and 0x80 != 0) f = f or Flags.S
    if (cpu.iff2) f = f or Flags.PV
    f = f or (cpu.a and 0x28)
    cpu.f = f
    cpu.pc = (cpu.pc + 2) and 0xFFFF
    cpu.tStates += baseCycles
}
```

Update the stale comment at the top of the file. The current comment
says "R is read BEFORE bumpR(2), per the documented Z80 semantics —
you see the value just after the instruction fetch." Replace with:

```kotlin
/**
 * `LD A, R` — copy R to A and compute flags from result. ED 5F. 9 T-states. R+=2. PC+=2.
 *
 * R is incremented during the M1 fetch cycles (prefix + opcode) BEFORE the copy stage of
 * the instruction executes. So `cpu.a` ends up with the value of R AFTER the two bumps —
 * if the test sets pre-execute R=0xF1, A will be 0xF3 after LD A,R.
 *
 * Flags: S = bit 7 of A; Z = A == 0; H = 0; P/V = cpu.iff2; N = 0; C preserved. X/Y from A.
 */
```

Update existing `LdARTest.kt`:
- Any test that asserts `cpu.a == initial R value` needs to be
  `cpu.a == (initial R + 2) and 0x7F` (or similar — be careful about
  the R counter's 7-bit wrapping; `bumpR` preserves bit 7 and increments
  the low 7 bits).
- Add a new test specifically pinning the bump-then-copy semantics:

```kotlin
@Test
fun `LD A, R copies the post-bump R value (bumped twice for ED-prefix opcode)`() {
    val cpu = Cpu().apply { a = 0; r = 0x10; pc = 0x100; tStates = 0L }
    LdAR.execute(cpu, Memory())
    assertThat(cpu.a).isEqualTo(0x12)
    assertThat(cpu.r).isEqualTo(0x12)
}

@Test
fun `LD A, R preserves R bit 7 across bumps`() {
    // bumpR keeps bit 7 and wraps the low 7 bits mod 128. Starting R = 0xFE:
    // first bump → 0x7F, second bump → 0x80 / wait that's wrong. Let me think.
    // bumpR: r = (r and 0x80) or ((r + 1) and 0x7F).
    // For r = 0xFE: bit 7 = 1, low 7 bits = 0x7E. After bump: low 7 bits = 0x7F → r = 0xFF.
    // After another bump: low 7 bits = (0x7F + 1) and 0x7F = 0x00 → r = 0x80.
    val cpu = Cpu().apply { r = 0xFE }
    LdAR.execute(cpu, Memory())
    assertThat(cpu.r).isEqualTo(0x80)
    assertThat(cpu.a).isEqualTo(0x80)
}
```

**Hard stop condition.** If after applying the swap the FUSE ed5f test
still fails, the bug is something else (maybe a flag-bit issue, maybe a
FUSE input quirk). STOP. File a follow-up beads with the actual
failure-message bytes and don't shotgun further fixes.

### residuals-C: Sweep + tag

Standard sweep:

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. New unit tests pass.
3. Existing tests still pass.
4. **FUSE: 1356/1356** (THE gate — strict). Verify via the score
   harness output.
5. ZEXDOC: 0 IllegalStateException, 0 ERROR.
6. Programs: 5/5.
7. Composite SCORE: ≥ 0.997.

Tag: `m1-residuals`. Push.

## Out of scope

- Any other FUSE failure surface — there should be exactly 0 left after
  this.
- M2.2 FRAMES bug (`zx80-1qc`). If the LD A,R fix happens to affect the
  ROM init loop, that's a side benefit; don't depend on it.
- RETI's signaling behavior to peripherals (IM 2 daisy chain) — M3
  concern.
- Documenting the canonical ed4d RETI slot vs the Phase G alias slots:
  same Op instance, same bug, same fix.

## Architecture

```
src/main/kotlin/ru/alepar/zx80/
  op/ed/Reti.kt       MODIFY (add 1 line: iff1 = iff2)
  op/ed/LdAR.kt       MODIFY (swap bumpR/read order; fix comment)
src/test/kotlin/ru/alepar/zx80/
  op/ed/RetiTest.kt   EXTEND or CREATE
  op/ed/LdARTest.kt   MODIFY existing + add 2 new
```

No threading, no new abstractions, no constructor cascades. Purely
surgical.

## Test strategy

About 3 new assertions + 1 existing-test update per the bullets above.

Tests use commas/dashes — no colons in backtick-quoted method names.

## Validation gates (residuals-C)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. New tests pass.
3. Existing tests pass.
4. FUSE: 1356/1356 (strict).
5. ZEXDOC clean, programs 5/5, SCORE ≥ 0.997.

Tag: `m1-residuals`.

## Work-unit breakdown

| WU | Description |
|---|---|
| residuals-A | Reti.kt — add iff1=iff2 line; update kdoc; create/extend RetiTest.kt with one iff1-restoration assertion. |
| residuals-B | LdAR.kt — swap bumpR(2) and R-read order; update kdoc; update existing LdARTest assertions to match post-bump A value; add 2 new tests (post-bump value + bit-7 preservation). Hard-stop on FUSE ed5f if swap doesn't fix it. |
| residuals-C | Sweep + tag m1-residuals + push. Strict FUSE 1356/1356 gate. |

Within-phase deps: residuals-A and residuals-B are independent. residuals-C depends on both.

## Risks

- **LD A,R diagnosis might be wrong.** If swapping the bumpR order
  doesn't fix FUSE ed5f, the actual bug is in flags or somewhere else
  entirely. The WU has an explicit hard-stop with "file a follow-up
  beads and report" so we don't shotgun-fix.
- **Existing LdARTest assertions.** They probably assert A == initial R
  (because the old behavior was read-before-bump). After the swap, they
  need to assert A == initial R after 2 bumps. The WU includes "update
  existing tests" explicitly.
- **R counter wrap math.** `bumpR(by)` keeps bit 7 and increments the
  low 7 bits mod 128 (per Cpu.kt line 103-105):
  `r = (r and 0x80) or ((r + by) and 0x7F)`. Pinned in the test "preserves
  R bit 7 across bumps".
- **Test name colons** — Kotlin disallows `:` in backtick-quoted test
  method names. All test names in this spec use commas/dashes.
