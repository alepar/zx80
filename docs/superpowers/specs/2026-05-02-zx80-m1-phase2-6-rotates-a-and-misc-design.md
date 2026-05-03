# ZX Spectrum Emulator — Phase 2.6 Design (rotates on A + DAA/CPL/SCF/CCF)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement the 8 main-table single-byte opcodes that operate on the accumulator A and/or directly manipulate flags: `RLCA`, `RRCA`, `RLA`, `RRA`, `DAA`, `CPL`, `SCF`, `CCF`. After this plan: opcodes count climbs by 8.

**The fiddly part: DAA.** The BCD-adjust algorithm is famously error-prone. Phase 2.6 is mostly a 4-WU pass over standard ops, but DAA gets dense per-edge testing.

## Context

Phases 2.1a–2.5 (planned) cover all the LD, ALU, control-flow, and stack opcodes. Phase 2.6 adds the remaining single-byte main-table ops in the 0x00-0x3F range that aren't already covered (rotates on A + the four flag-manipulators). This is the last phase before the CB-prefixed table (Phase 2.7) explodes the opcode count.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.6).

## Scope

### In scope

| Opcode | Mnemonic | T-states | Behavior summary |
|--------|----------|----------|------------------|
| 0x07 | RLCA | 4 | A rotated left circular; bit 7 → C and → bit 0 |
| 0x0F | RRCA | 4 | A rotated right circular; bit 0 → C and → bit 7 |
| 0x17 | RLA  | 4 | A rotated left through carry: new A = (oldA shl 1) or oldC; new C = old bit 7 |
| 0x1F | RRA  | 4 | A rotated right through carry |
| 0x27 | DAA  | 4 | BCD adjust accumulator |
| 0x2F | CPL  | 4 | A = A xor 0xFF |
| 0x37 | SCF  | 4 | C = 1 |
| 0x3F | CCF  | 4 | C = !C; H = oldC |

Total: **8 opcode positions across 8 Op classes** (each is a singleton — no parameterization needed).

Plus foundation: extend `Flags` with 5 new helpers (`afterRotateA`, `afterDaa`, `afterCpl`, `afterScf`, `afterCcf`).

### Out of scope (deferred)

- CB-prefixed `RLC r`, `RRC r`, `RL r`, `RR r`, `SLA r`, `SRA r`, `SRL r` and BIT/SET/RES — spec batch 2.7 (next).
- DD/FD-prefixed indexed rotate variants — batches 2.8/2.9.

## Architecture

### Flag computation — extend `Flags` with 5 helpers

```kotlin
object Flags {
    // ... existing helpers ...

    /**
     * Common flag rules for the 4 rotate-A opcodes (RLCA, RRCA, RLA, RRA):
     * - C = newC (the bit that was shifted out).
     * - H = 0.
     * - N = 0.
     * - S, Z, P/V = preserved from oldF.
     *
     * Caller computes the rotated value AND newC; this helper just packages
     * the result + new F.
     */
    fun afterRotateA(rotated: Int, newC: Boolean, oldF: Int): AluResult

    /**
     * BCD adjust accumulator after a previous arithmetic operation.
     * Behavior depends on N, H, C flags from the previous op.
     *
     * - S = bit 7 of result.
     * - Z = result == 0.
     * - H = (a xor result) bit 4 (covers both add/sub paths).
     * - P/V = parity of result.
     * - N = preserved from oldF.
     * - C = potentially set if a high-nibble correction was applied.
     */
    fun afterDaa(a: Int, oldF: Int): AluResult

    /**
     * CPL: A = A xor 0xFF.
     * - H = 1.
     * - N = 1.
     * - S, Z, P/V, C = preserved from oldF.
     */
    fun afterCpl(a: Int, oldF: Int): AluResult

    /**
     * SCF: set carry flag.
     * - C = 1.
     * - H = 0.
     * - N = 0.
     * - S, Z, P/V = preserved from oldF.
     *
     * Returns just the new F (A is unchanged, so no AluResult wrapping needed).
     */
    fun afterScf(oldF: Int): Int

    /**
     * CCF: complement carry flag.
     * - C = !oldC.
     * - H = oldC (counterintuitively).
     * - N = 0.
     * - S, Z, P/V = preserved from oldF.
     */
    fun afterCcf(oldF: Int): Int
}
```

### DAA implementation

The standard table-based algorithm. Tested against documented Z80 reference values for ~10 boundary cases.

```kotlin
fun afterDaa(a: Int, oldF: Int): AluResult {
    val n = oldF and N != 0
    val cFlag = oldF and C != 0
    val hFlag = oldF and H != 0
    var correction = 0
    var newC = cFlag
    if (hFlag || (!n && (a and 0x0F) > 9)) correction = correction or 0x06
    if (cFlag || (!n && a > 0x99)) {
        correction = correction or 0x60
        newC = true
    }
    val result = (if (n) a - correction else a + correction) and 0xFF
    var f = oldF and N   // preserve N
    if (result == 0) f = f or Z
    if (result and 0x80 != 0) f = f or S
    if (parity(result)) f = f or PV
    if (newC) f = f or C
    if ((a xor result) and 0x10 != 0) f = f or H
    return AluResult(result, f)
}
```

Reference test cases (Z80 reference):
- `DAA after ADD 0x09 + 0x01 = 0x0A` (no flags) → A becomes 0x10, H set
- `DAA after ADD 0x99 + 0x01 = 0x9A` (no flags) → A becomes 0x00, C and Z set
- `DAA after SUB 0x10 - 0x01 = 0x0F` (N=1, H=1) → A becomes 0x09, H set
- `DAA at A=0x00, N=0, C=0, H=0` → A unchanged, Z set
- `DAA at A=0x00, N=1, C=1, H=0` → A becomes 0xA0, S+C set

### Op classes (8 singletons)

All 8 ops are `object` singletons with `operandLength=0`, `baseCycles=4`, identical PC/R/T-state increment pattern. Each `execute` body differs in the flag/value computation.

Example skeleton:

```kotlin
object Rlca : Op {
    override val operandLength = 0
    override val baseCycles = 4
    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val newC = (a and 0x80) != 0
        val rotated = ((a shl 1) or (if (newC) 1 else 0)) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }
    override fun mnemonic(operands: OperandFetcher) = "RLCA"
}
```

Pattern is identical across the 4 rotates; same for the 4 misc additions (Daa/Cpl/Scf/Ccf).

### Packaging

- `op/rot/`: `Rlca`, `Rrca`, `Rla`, `Rra` + new `RotOps.installInto(decoder)` fragment. (Phase 2.7's CB-rotates will extend this.)
- `op/misc/`: extend with `Daa`, `Cpl`, `Scf`, `Ccf` + extend existing `MiscOps.installInto`.

The `RotOps` fragment is wired into `OpTableBuilder.build()` with one new line. `MiscOps` is already wired.

### Test strategy

1. **Per-Flags-helper tests** in `FlagsTest.kt` (extending the existing file):
   - `afterRotateA`: basic, C bit propagation, S/Z/PV preservation, H/N clearing.
   - `afterDaa`: 8-10 boundary cases per the reference list above.
   - `afterCpl`: bit-flip correctness, H/N set, S/Z/PV/C preservation.
   - `afterScf`: C set, H/N cleared, S/Z/PV preservation.
   - `afterCcf`: toggle C, H gets oldC, N cleared, S/Z/PV preservation.

2. **Per-Op tests**: state delta + PC/R/T-states. Most ops are mechanical; DAA is the one that gets the comprehensive flag spot-checks.

3. **Fragment tests**: `RotOpsTest` + extended `MiscOpsTest` assert each opcode position has the right Op via mnemonic match.

## Implementation Sequence

4 work units:

| WU | Subject | Approx scope |
|----|---------|--------------|
| **2.6-1** | 5 Flags helpers + dense per-edge tests (especially DAA) | 0 Op classes; ~30 unit tests |
| **2.6-2** | Rlca + Rrca + Rla + Rra + new RotOps fragment + wire into builder | 4 Op classes; ~16 tests |
| **2.6-3** | Daa + Cpl + Scf + Ccf + extend MiscOps | 4 Op classes; ~16 tests |
| **2.6-4** | Sweep + tag `m1-phase02-6` | verification only |

## Risks

- **R1: DAA correctness.** Famously hard to get right. Mitigation: 8-10 boundary tests against documented Z80 reference values; FUSE `27` case is the gold standard.
- **R2: RLCA/RRCA/RLA/RRA preserve S/Z/PV** while CB-prefixed RLC r/RRC r/RL r/RR r compute them. Easy to swap rules. Mitigation: each rotate test sets oldF = `Flags.S or Flags.Z or Flags.PV` and asserts those bits are preserved.
- **R3: CCF sets H to OLD C value** — counterintuitive. Mitigation: explicit test sets f=C → after CCF: H=1 and C=0; sets f=0 → after CCF: H=0 and C=1.
- **R4: RLA/RRA fold OLD C bit IN** as the new low/high bit, not new C. Test with C=1 explicitly.
- **R5: DAA preserves N flag** — different from most other ALU ops. Test asserts.

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by 8.
3. nop_loop still passes.
4. FUSE 07, 0F, 17, 1F, 27, 2F, 37, 3F all pass.
5. Tag `m1-phase02-6` placed.
6. Phase 2.7 (CB-prefixed rotates/shifts/BIT/SET/RES — the big one) is structurally unblocked.

## Open Questions (deferred to implementation)

- Whether the 4 rotate-A ops share a small private helper for the rotate-then-package pattern. Likely no — each is 5 lines of execute body, factoring saves only 2 lines per op.
- Whether DAA's H rule deserves more explicit comments. Yes; the implementation includes a comment about `(a xor result) and 0x10` covering both add and sub paths.
