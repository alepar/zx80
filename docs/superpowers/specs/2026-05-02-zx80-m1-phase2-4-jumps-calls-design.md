# ZX Spectrum Emulator — Phase 2.4 Design (jumps, calls, returns)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement Z80 jumps, calls, and returns: `JP`, `JP cc`, `JP (HL)`, `JR`, `JR cc`, `DJNZ`, `CALL`, `CALL cc`, `RET`, `RET cc`, `RST p`. **42 new opcode positions across 11 Op classes.** None of these ops affect flags — first batch since 2.1a/2.1b without any flag computation.

After this plan: control flow (loops, subroutine calls, returns) becomes possible. The `fib10` program from the spec's curated programs list becomes runnable once it lands.

## Context

Phase 2.3 (planned, tag `m1-phase02-3`) covers 16-bit arithmetic. The `Cpu`, `Memory`, `Reg`, `RegPair`, `Flags` foundations are in place. Stack pointer (`cpu.sp`) exists but no opcode currently mutates it.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.4).

## Scope

### In scope

| Group | Opcodes | T-states | Op class |
|-------|---------|----------|----------|
| `JP nn` | 0xC3 (1) | 10 | `JpAbs` |
| `JP cc,nn` | 0xC2/CA/D2/DA/E2/EA/F2/FA (8) | 10 (always) | `JpAbsCc(cond)` |
| `JP (HL)` | 0xE9 (1) | 4 | `JpHl` |
| `JR e` | 0x18 (1) | 12 | `JrRel` |
| `JR cc,e` | 0x20/28/30/38 (4: NZ/Z/NC/C) | 7 not-taken / 12 taken | `JrRelCc(cond)` |
| `DJNZ e` | 0x10 (1) | 8 not-taken / 13 taken | `Djnz` |
| `CALL nn` | 0xCD (1) | 17 | `CallAbs` |
| `CALL cc,nn` | 0xC4/CC/D4/DC/E4/EC/F4/FC (8) | 10 not-taken / 17 taken | `CallAbsCc(cond)` |
| `RET` | 0xC9 (1) | 10 | `Ret` |
| `RET cc` | 0xC0/C8/D0/D8/E0/E8/F0/F8 (8) | 5 not-taken / 11 taken | `RetCc(cond)` |
| `RST p` | 0xC7/CF/D7/DF/E7/EF/F7/FF (8) | 11 | `Rst(target)` |

Total: **42 opcode positions across 11 Op classes**. All in `decoder.main` (no ED-prefixed ops in this batch).

Plus foundation: `Condition` enum, `Cpu.push`/`Cpu.pop` extension methods, `BranchOps.installInto(decoder)` fragment.

### Out of scope (deferred)

- IX/IY-prefixed jumps (`JP (IX)`, `JP (IY)`) — DD/FD-prefixed; spec batch 2.8.
- `RETI` (0xED 0x4D) and `RETN` (0xED 0x45) — ED-prefixed; spec batch 2.10.
- `PUSH rr` / `POP rr` — spec batch 2.5 (next).

## Architecture

### `Condition` enum

`src/main/kotlin/ru/alepar/zx80/cpu/Condition.kt`. The 8 Z80 condition codes used by JP cc, CALL cc, RET cc. JR cc uses only the first 4 (NZ, Z, NC, C).

```kotlin
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

### `Cpu.push` / `Cpu.pop` extension methods

Located in a new file `src/main/kotlin/ru/alepar/zx80/cpu/CpuStack.kt`. Z80 stack semantics: SP pre-decremented before push (high byte first, then low byte at SP-2); SP post-incremented after pop (low byte from SP first, then high byte from SP+1). Returns/accepts 16-bit values.

```kotlin
package ru.alepar.zx80.cpu

/**
 * Push a 16-bit value onto the Z80 stack.
 *
 * Convention: SP is decremented BEFORE writing each byte. High byte is
 * written first (at SP-1), then low byte (at SP-2). After the push,
 * SP points to the low byte.
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
 * read first (from SP), then high byte (from SP+1). After the pop, SP
 * points one above the original high byte.
 */
fun Cpu.pop(mem: Memory): Int {
    val lo = mem.read(sp)
    sp = (sp + 1) and 0xFFFF
    val hi = mem.read(sp)
    sp = (sp + 1) and 0xFFFF
    return (hi shl 8) or lo
}
```

These are reused by Phase 2.5's PUSH/POP rr ops (`PUSH BC` is just `cpu.push(mem, cpu.bc)`).

### Op class skeletons

| Class | Opcodes | T-states | operandLength | Behavior |
|-------|---------|----------|---------------|----------|
| `JpAbs` | 0xC3 | 10 | 2 | `cpu.pc = mem.readWord(cpu.pc + 1)` |
| `JpAbsCc(cond)` | `11 ccc 010` (8) | 10 | 2 | always reads nn; `pc = if (cond.test(cpu)) nn else (pc+3) and 0xFFFF` |
| `JpHl` | 0xE9 | 4 | 0 | `cpu.pc = cpu.hl` |
| `JrRel` | 0x18 | 12 | 1 | reads signed e; `pc = (pc + 2 + signedE) and 0xFFFF` |
| `JrRelCc(cond)` | `001 cc 000` (4) | 7 not-taken / 12 taken | 1 | always reads e; if taken, jump and add 5 extra T-states |
| `Djnz` | 0x10 | 8 not-taken / 13 taken | 1 | `cpu.b = (cpu.b - 1) and 0xFF`; jumps if b != 0; **flags untouched** |
| `CallAbs` | 0xCD | 17 | 2 | `cpu.push(mem, (pc+3) and 0xFFFF); cpu.pc = mem.readWord(pc+1)` |
| `CallAbsCc(cond)` | `11 ccc 100` (8) | 10 not-taken / 17 taken | 2 | always reads nn; if taken, push (pc+3), set pc = nn, +7 extra T-states |
| `Ret` | 0xC9 | 10 | 0 | `cpu.pc = cpu.pop(mem)` |
| `RetCc(cond)` | `11 ccc 000` (8) | 5 not-taken / 11 taken | 0 | if taken, pop into pc and add 6 extra T-states; else `pc += 1` |
| `Rst(target)` | `11 ttt 111` (8) | 11 | 0 | `cpu.push(mem, (pc+1) and 0xFFFF); cpu.pc = target`. target = `ttt * 8` (0x00, 0x08, 0x10, ..., 0x38). |

All increment R by 1 (no prefixed opcodes here).

### Signed displacement for JR/DJNZ

The displacement byte is signed 8-bit (-128..+127). Convention:

```kotlin
val displacementByte = mem.read(cpu.pc + 1)   // 0..255
val signedDisplacement = displacementByte.toByte().toInt()   // -128..+127
val newPc = (cpu.pc + 2 + signedDisplacement) and 0xFFFF
```

The `+ 2` accounts for the JR/DJNZ instruction being 2 bytes long (PC after is the byte after the displacement).

### `BranchOps.installInto(decoder)` fragment

`src/main/kotlin/ru/alepar/zx80/op/branch/BranchOps.kt`. Loop-based registration:

```kotlin
object BranchOps {
    fun installInto(d: Decoder) {
        installJpFamily(d)        // 10 opcodes
        installJrAndDjnz(d)       //  6 opcodes
        installCallFamily(d)      //  9 opcodes
        installRetFamily(d)       //  9 opcodes
        installRstFamily(d)       //  8 opcodes
    }
    // ... 5 private installers ...
}
```

Wired into `OpTableBuilder.build()` via a single `BranchOps.installInto(d)` line.

### Mnemonics

- `"JP nn"`, `"JP NZ, nn"`, `"JP (HL)"`
- `"JR e"`, `"JR NZ, e"`
- `"DJNZ e"`
- `"CALL nn"`, `"CALL Z, nn"`
- `"RET"`, `"RET NC"`
- `"RST 18H"` (target rendered in uppercase hex with H suffix; standard Z80 convention)

### Test strategy

1. **Per-Op tests** asserting:
   - PC after the op (jump destination, fall-through, etc.).
   - SP after the op (changes for CALL/RET/RST, unchanged for JP/JR/DJNZ).
   - Memory state for CALL/RST (return address pushed correctly: little-endian, high byte at higher address).
   - T-state cost (both branches for conditionals).
   - Flag invariance (every Op tested with f=0xFF and assertion that f=0xFF after).
   - R increment.

2. **`Condition.test` tests** covering all 8 conditions × both flag states (16 cases).

3. **`Cpu.push`/`pop` tests** covering basic push/pop, SP wrap at 0x0000/0xFFFF, byte-order correctness.

4. **`BranchOps` fragment tests** asserting each opcode position has the right Op via mnemonic match.

## Implementation Sequence

6 work units:

| WU | Subject | New code | Tests | Opcodes |
|----|---------|----------|-------|---------|
| **2.4-1** | `Condition` enum + `Cpu.push`/`pop` + empty `BranchOps` skeleton + wire into builder | foundation | ~20 | 0 |
| **2.4-2** | JP family: `JpAbs`, `JpAbsCc(cond)`, `JpHl` + extend BranchOps | 3 classes | ~15 | 10 |
| **2.4-3** | JR + DJNZ family: `JrRel`, `JrRelCc(cond)`, `Djnz` + extend BranchOps | 3 classes | ~15 | 6 |
| **2.4-4** | CALL family: `CallAbs`, `CallAbsCc(cond)` + extend BranchOps | 2 classes | ~12 | 9 |
| **2.4-5** | RET + RST family: `Ret`, `RetCc(cond)`, `Rst(target)` + extend BranchOps | 3 classes | ~18 | 17 |
| **2.4-6** | Sweep + tag `m1-phase02-4` | none | none | 0 |

## Risks

- **R1: Signed displacement for JR/DJNZ.** The displacement byte is signed 8-bit (-128..+127). The `mem.read()` API returns 0..255. Easy to forget the sign-extension. Mitigation: explicit per-Op test with negative displacement (e.g. JR -2 → infinite loop on self).
- **R2: Conditional T-state accounting.** `CALL cc` taken = 17, not-taken = 10. `JR cc` taken = 12, not-taken = 7. `RET cc` taken = 11, not-taken = 5. Easy to use wrong constant. Mitigation: per-Op test asserts both branches.
- **R3: Stack push pre-decrement, pop post-increment.** Standard Z80 convention. Easy to swap or off-by-one. Mitigation: per-helper test asserts SP behavior at boundaries.
- **R4: CALL pushes PC+3 (the return address), RST pushes PC+1.** Off-by-one is easy. Mitigation: explicit tests verifying the pushed value.
- **R5: `Djnz` does NOT touch flags despite decrementing B.** Counterintuitive (8-bit DEC sets flags). Mitigation: explicit "f preserved" test.
- **R6: `JpHl` reads HL value, does NOT dereference `(HL)`.** Despite the parens in the mnemonic. Mitigation: clear KDoc + test that asserts memory at `cpu.hl` is irrelevant to the result.
- **R7: `RetCc` not-taken cycle count is 5** (unusually short). Easy to use 10 (the unconditional RET cost). Mitigation: explicit test.

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by 42.
3. nop_loop still passes.
4. `Condition.test` covered for all 8 conditions × both flag states.
5. `Cpu.push` and `Cpu.pop` covered with byte-order + SP-wrap tests.
6. Tag `m1-phase02-4` placed.
7. Phase 2.5 (PUSH/POP) is structurally unblocked (push/pop helpers in place).

## Open Questions (deferred to implementation)

- Whether `Rst(target)` validates the target argument (must be one of 0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38). Probably yes — `require(target in setOf(0x00, 0x08, ..., 0x38))` in `init`. Matches the `Im(mode)` precedent.
- Whether to factor the "CALL" body (push + jump) and the "JR" body (signed displacement decode) into private helpers shared across CallAbs/CallAbsCc and JrRel/JrRelCc. Likely yes; trivial.
