# ZX Spectrum Emulator — Phase 2.11 Design (programs + ZEXDOC + M1 close)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Close out M1 (headless Z80 CPU). Two tracks:

1. **Programs suite to 5/5.** Add `fib10`, `memcpy16`, `bubblesort_4`, `crc8` fixtures (joining the existing `nop_loop`). Extend `ProgramExpectation` with an `initial_memory` field so programs can declare pre-loaded data without putting it in the binary.
2. **ZEXDOC milestone gate.** Vendor `ZEXDOC.COM`, implement a CP/M BDOS trap (syscalls 2 and 9) so the binary's character output is captured, wire a `zx80 zexdoc` CLI subcommand. Run it end-to-end; iterate on any flag-edge defects it surfaces; tag `m1-cpu-complete`.

After this phase: M1 milestone "C" done. Headless Z80 capable of running real Z80 programs and passing the ZEXDOC documented-instruction test suite.

## Context

Phases 2.1a through 2.10 implement the documented Z80 instruction set: main table, CB-prefixed (rotate/shift/BIT/RES/SET), DD/FD-prefixed (IX/IY ops), DDCB/FDCB-prefixed (indexed bit/rotate), and ED-prefixed remainder (block ops, I/O, 16-bit LD via memory, NEG/RETI/RETN/IM, LD I/R). Phase 2.10 also introduces the `IoBus` abstraction with `NoIoBus` default.

The original top-level spec (`docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md`) lists this as batch 2.11 plus Phase 3:

> **Batch 2.11**: Add programs: bubblesort_8, memcpy16, crc8. Tighten flag-edge tests where FUSE is still red. Programs suite goes 5/5.
>
> **Phase 3 — ZEXDOC gate**: Add `zx80 zexdoc` subcommand (loads ZEXDOC.COM at 0x100, traps CP/M BDOS calls 2 and 9, runs to completion). Iterate on flag edges revealed by ZEXDOC until all CRCs match. Tag commit `m1-cpu-complete`. End of M1 / milestone "C".

We consolidate both into one Phase 2.11 plan because they share the same spirit (final cleanup + integration validation) and because flag-edge debugging can flow naturally between programs and ZEXDOC.

## Scope

### In scope

**Track 1 — Programs:**

1. `ProgramExpectation` extension:
   ```kotlin
   data class ProgramExpectation(
       …existing fields…,
       val initial_memory: Map<String, Int>? = null,  // address → byte, hex-prefixed keys
   )
   ```
2. `ProgramsSuite.runOne` applies `initial_memory` AFTER loading the program bytes, BEFORE running. Same hex-key convention as `expect.memory`.
3. **fib10** — iterative Fibonacci. Computes F(11) = 89 and stores it at 0x100. ASM:
   ```asm
           ORG  0x0000
           LD HL, 0          ; a = 0
           LD DE, 1          ; b = 1
           LD B, 10          ; counter
   LOOP:   ADD HL, DE        ; HL = a + b
           EX DE, HL         ; HL <-> DE
           DJNZ LOOP
           EX DE, HL         ; result is in DE after final swap; bring it back to HL
           LD (0x100), HL    ; store
           HALT
   ```
   Bytes: `21 00 00 11 01 00 06 0A 19 EB 10 FC EB 22 00 01 76` (17 bytes). Expected: `pc=0x10`, `halted=true`, `mem[0x100]=0x59`, `mem[0x101]=0x00` (89 little-endian).
4. **memcpy16** — block copy via LDIR. ASM:
   ```asm
           ORG  0x0000
           LD HL, 0x100      ; source
           LD DE, 0x200      ; dest
           LD BC, 16         ; count
           LDIR
           HALT
   ```
   Bytes: `21 00 01 11 00 02 01 10 00 ED B0 76` (12 bytes). `initial_memory`: `0x100..0x10F` set to a known pattern (e.g. `0x11, 0x22, 0x33, …`). Expected: `0x200..0x20F` matches the same pattern; `bc=0`, `hl=0x110`, `de=0x210`, `halted=true`.
5. **bubblesort_4** — sorts 4 bytes ascending via simple bubble sort. Smaller than the spec's bubblesort_8 to keep the hand-assembled binary tractable. ASM:
   ```asm
           ORG  0x0000
           LD B, 3                 ; outer pass count = N-1
   OUTER:  LD HL, 0x100            ; reset pointer each pass
           LD C, 3                 ; inner count = N-1
   INNER:  LD A, (HL)
           INC HL
           CP (HL)                 ; A - (HL); C set if A < (HL) (no swap needed)
           JR C, NOSWAP            ; A < (HL): in order, skip swap
           JR Z, NOSWAP            ; A == (HL): in order, skip swap
           ; swap (HL-1) and (HL): A holds (HL-1); D holds (HL); write D back to HL-1, A to HL
           LD D, (HL)
           LD (HL), A
           DEC HL
           LD (HL), D
           INC HL
   NOSWAP: DEC C
           JR NZ, INNER
           DJNZ OUTER
           HALT
   ```
   Hand-assembled bytes (verify in plan): roughly 25 bytes. `initial_memory`: `0x100..0x103 = 0x04, 0x01, 0x03, 0x02`. Expected after run: `0x100..0x103 = 0x01, 0x02, 0x03, 0x04`.
6. **crc8** — computes CRC-8 (polynomial 0x07, init 0x00, no reflection) over 4 input bytes. ASM:
   ```asm
           ORG  0x0000
           LD HL, 0x100            ; input pointer
           LD B, 4                 ; byte count
           LD A, 0x00              ; CRC accumulator
   NEXTB:  XOR (HL)                ; A ^= byte
           LD C, 8                 ; bit counter
   BIT:    SLA A                   ; shift left, MSB into carry
           JR NC, NOXOR
           XOR 0x07                ; if MSB was 1, XOR with poly
   NOXOR:  DEC C
           JR NZ, BIT
           INC HL
           DJNZ NEXTB
           LD (0x200), A           ; store CRC
           HALT
   ```
   Hand-assembled bytes (verify in plan). `initial_memory`: `0x100..0x103 = 0x31, 0x32, 0x33, 0x34` ("1234" in ASCII). Expected: `mem[0x200]` = the CRC of those bytes (compute in plan; reference: ASCII "123456789" CRC-8/SMBus is 0xF4, so 4-byte prefix value is computable and we'll lock it in plan).

**Track 2 — ZEXDOC:**

7. Vendor `ZEXDOC.COM` (8585 bytes; in the public domain, by Frank Cringle / Ian Bartholomew). Place at `src/main/resources/zexdoc/ZEXDOC.COM`.
8. CP/M BDOS trap. ZEXDOC.COM expects a CP/M-80 environment:
   - Loads at `0x100` (CP/M TPA).
   - Calls `CALL 0x0005` for BDOS syscalls. C register holds syscall number.
   - **Syscall 2 (C=2)** — print char in E to console.
   - **Syscall 9 (C=9)** — print `$`-terminated string at `(DE)` to console.
   - Returns via `RET`.
   - On entry to ZEXDOC, SP is set somewhere (we'll set it to `0xFFFE`); the program eventually calls BDOS `0x00` (warm boot) which we treat as termination.
   We implement the trap as either: (a) a tiny stub at `0x0005` that hooks into a Kotlin handler and emits a `RET`, or (b) a special-cased CALL handler that intercepts target=0x0005. **(a) is cleaner** — write a 1-byte sentinel opcode (or repurpose an unused opcode) that we install at `0x0005` and handle in dispatch. Decision deferred to plan; simplest: write `RET` (`0xC9`) at `0x0005` and intercept in a custom `Op` we install at the BDOS entry by overriding the opcode at that address only via a *post-dispatch hook* on the dispatcher. Actually simpler still: set `mem[0x0005] = 0xC9` (RET); subclass `ProgramRunner` (the ZEXDOC runner) so that BEFORE each step, if `cpu.pc == 0x0005`, run a Kotlin BDOS handler then continue. **This avoids touching CPU/dispatch code.**
9. `zx80 zexdoc` CLI subcommand:
   - Loads `ZEXDOC.COM` at `0x100`.
   - Sets `cpu.pc = 0x100`, `cpu.sp = 0xFFFE`.
   - Sets `mem[0x0005] = 0xC9` (RET).
   - Sets `mem[0x0000] = 0x76` (HALT) to catch warm-boot CALL 0x0000.
   - Runs the dispatch loop; on each iteration, if `cpu.pc == 0x0005`, invoke the BDOS handler (reads C, dispatches to syscall 2 or 9, advances state).
   - Captures all printed output to stdout.
   - Terminates when CPU halts (PC reaches 0x0000) or on `Ctrl-C`.
   - Exit code: 0 if output contains "Tests complete" with no "ERROR" lines; 1 otherwise.
10. **Flag-edge iteration.** Run ZEXDOC. ZEXDOC tests CRC of the result of every documented instruction across many operand combinations. Any CRC mismatch indicates a flag-edge or computation bug somewhere in our implementation. Iterate per-test (or per-batch) until all CRCs match.

### Out of scope

- Undocumented Z80 instructions (per top-level spec non-goals).
- ZEXALL (the full undocumented test suite).
- bubblesort_8 from the original spec (we use bubblesort_4 instead — the difference is purely fixture-size; the algorithm exercises the same ops).
- Performance optimization. ZEXDOC takes minutes to run; that's fine.
- Composite score weight rebalancing — the existing weights stand.

## Architecture

### `ProgramExpectation` extension

`src/main/kotlin/ru/alepar/zx80/harness/programs/ProgramExpectation.kt`:

```kotlin
@Serializable
data class ProgramExpectation(
    val name: String,
    val load_at: Int,
    val entry: Int,
    val max_cycles: Long,
    val stop_on: String = "HALT",
    val initial_memory: Map<String, Int>? = null,  // NEW
    val expect: ExpectedState,
)
```

`ProgramsSuite.runOne` applies it after the program-bytes load, before the dispatch loop:

```kotlin
exp.initial_memory?.forEach { (addrStr, byte) ->
    mem.write(parseHex(addrStr), byte and 0xFF)
}
```

`parseHex` is already on the suite (used by `checkExpectations`); promote it to a top-level helper or duplicate inline.

### Program fixtures (4 files each: `.asm`, `.bin`, `.expected.json`)

Plus: update `ResourceLoader.PROGRAM_NAMES` to:

```kotlin
private val PROGRAM_NAMES = listOf("nop_loop", "fib10", "memcpy16", "bubblesort_4", "crc8")
```

Each fixture lives at `src/main/resources/programs/<name>.{asm,bin,expected.json}`. The `.asm` is human-readable source (commented); `.bin` is hand-assembled by the implementer (each WU's plan provides the byte sequence with a per-byte mnemonic table for verification); `.expected.json` declares load_at, entry, max_cycles, initial_memory (if any), and expectations.

### ZEXDOC infrastructure

New files:

- `src/main/kotlin/ru/alepar/zx80/zexdoc/BdosHandler.kt` — implements syscalls 2 and 9 against an `Appendable` output. Pure logic, no CLI deps.
- `src/main/kotlin/ru/alepar/zx80/zexdoc/ZexdocRunner.kt` — owns `Cpu`, `Memory`, `Dispatcher`; loads `ZEXDOC.COM`; runs the dispatch loop with the BDOS hook; returns an exit status struct (output, terminated cleanly, total cycles).
- `src/main/kotlin/ru/alepar/zx80/cli/ZexdocCommand.kt` — Clikt subcommand `zexdoc`. Wires `ZexdocRunner` to `System.out` and `System.exit`.
- `src/main/resources/zexdoc/ZEXDOC.COM` — vendored binary (8585 bytes).
- Tests:
  - `BdosHandlerTest` — unit tests for syscalls 2 and 9 (string termination on `$`, char output to `Appendable`).
  - `ZexdocRunnerSmokeTest` — runs ZEXDOC for a tiny number of cycles, asserts the runner advances state without crashing. Full run is too slow for CI and is verified in WU 2.11-7.

`ZexdocRunner` outline:

```kotlin
class ZexdocRunner(private val out: Appendable) {
    fun run(rom: ByteArray, maxCycles: Long = Long.MAX_VALUE): ZexdocResult {
        val cpu = Cpu().apply { pc = 0x0100; sp = 0xFFFE }
        val mem = Memory()
        for ((i, b) in rom.withIndex()) mem.write(0x0100 + i, b.toInt() and 0xFF)
        mem.write(0x0005, 0xC9)  // RET at BDOS entry
        mem.write(0x0000, 0x76)  // HALT at warm-boot vector

        val decoder = OpTableBuilder.build()
        val dispatcher = Dispatcher(decoder)
        val handler = BdosHandler(out)

        while (!cpu.halted && cpu.tStates < maxCycles) {
            if (cpu.pc == 0x0005) {
                handler.handle(cpu, mem)  // executes syscall side effect
                // RET (0xC9) at 0x0005 then runs naturally, popping return address
            }
            val op = dispatcher.decodeAt(cpu, mem) ?: error(...)
            op.execute(cpu, mem)
        }
        return ZexdocResult(out.toString(), cpu.halted, cpu.tStates)
    }
}
```

### Test strategy

- **Per-program suite tests** under `ProgramsSuiteTest` to assert each new fixture parses + runs (initially expected to FAIL because not all required ops will be implemented in the test environment, but the *structure* is exercised). After the full M1 stack is in place, all 5 pass.
- **Trivia:** these fixtures are already exercised by the existing `score` CLI smoke test, so we don't need to duplicate.
- **`BdosHandlerTest`** — small, isolated unit tests.
- **`ZexdocRunnerSmokeTest`** — short-run sanity, not full ZEXDOC.
- **WU 2.11-7** runs the full ZEXDOC binary as a manual gate — not in the test suite (too slow). Result is documented in the WU's commit message.

## Implementation Sequence

7 work units:

| WU | Subject | Notes |
|----|---------|-------|
| **2.11-1** | Extend `ProgramExpectation` with `initial_memory`; update `ProgramsSuite` to apply it | Foundation. Add tests covering: initial_memory absent (back-compat), initial_memory present, hex-key parsing. |
| **2.11-2** | Add `fib10` fixture | `.asm`, `.bin` (17 bytes), `.expected.json`. Add `"fib10"` to `PROGRAM_NAMES`. Suite test asserts pass once required ops are implemented. |
| **2.11-3** | Add `memcpy16` fixture | Uses `initial_memory` for source bytes at 0x100..0x10F. Add `"memcpy16"` to `PROGRAM_NAMES`. |
| **2.11-4** | Add `bubblesort_4` fixture | Uses `initial_memory` for unsorted input. Add `"bubblesort_4"` to `PROGRAM_NAMES`. |
| **2.11-5** | Add `crc8` fixture | Uses `initial_memory` for input bytes. Add `"crc8"` to `PROGRAM_NAMES`. Programs suite now reports 5/5 (assuming the prior phases all landed). |
| **2.11-6** | ZEXDOC infrastructure: vendor `ZEXDOC.COM`, implement `BdosHandler` + `ZexdocRunner` + `zx80 zexdoc` CLI subcommand + smoke tests | No `m1-cpu-complete` tag yet — the run might surface bugs. |
| **2.11-7** | Run full ZEXDOC end-to-end; iterate on any flag-edge failures; tag `m1-phase02-11` then `m1-cpu-complete` | Open-ended. If many tests fail, spawn focused fix-up WUs (one per failing instruction class). Final tag declares M1 done. |

## Risks

- **R1: Hand-assembled binaries.** Errors in opcode bytes will silently break the program. Mitigation: each fixture WU includes a per-byte mnemonic table in the plan that the implementer cross-checks before writing the `.bin` file. Pre-flight: implementer can run the fixture in isolation against the current decoder; if the program halts at the wrong PC or produces wrong output, the bytes are the first suspect.
- **R2: bubblesort_4 swap logic correctness.** The swap clobbers `D`. Done criteria: assert all four bytes appear in ascending order. If the algorithm is subtly wrong, the test fails fast.
- **R3: ZEXDOC.COM vendoring.** ZEXDOC is freely redistributable (Frank Cringle's documented-instruction CRC harness, derived from `Yaze`'s ZEX framework). Confirm no licensing issue; include attribution in a `src/main/resources/zexdoc/README.txt`.
- **R4: BDOS hook timing.** The hook runs BEFORE the opcode at `0x0005`. The opcode at `0x0005` is `RET` (0xC9), which we wrote there. After the hook handles the syscall side effect, the `RET` executes normally and pops the return address. Critical: the hook must NOT advance PC itself or pop SP — those are the `RET`'s job. Test: hook run + RET execute = single round trip with side effect. Verified in `BdosHandlerTest` against a tiny test program.
- **R5: ZEXDOC may surface 1, 10, or 100 flag-edge failures.** Open-ended scope. Mitigation: WU 2.11-7 is structured as "run, triage, fix, re-run". If failures explode, spawn discrete fix-up WUs per failing instruction class so progress is checkpointable.
- **R6: ZEXDOC runtime.** Full run is multi-minute. Don't run it in CI. Use a `--max-cycles` flag or `--quick` mode for partial runs during development.
- **R7: Warm-boot detection.** ZEXDOC ends by jumping to `0x0000` (CP/M warm boot). We've set `mem[0x0000] = 0x76` (HALT) so this terminates cleanly. Test: smoke test asserts a tiny ZEXDOC-shaped fragment that does `JP 0x0000` halts.
- **R8: Composite score may not hit ≥0.9 if ZEXDOC reveals many failures.** Acceptable — the score gradient remains useful; the M1 tag goes when ZEXDOC passes, regardless of intermediate score.

## Done Criteria

1. `./gradlew check` green — including the 4 new program fixture tests.
2. `./build/install/zx80/bin/zx80 score` reports `programs 5/5`.
3. `./build/install/zx80/bin/zx80 zexdoc` runs to completion and outputs all CRCs as `OK`. Final stdout includes "Tests complete" and contains zero "ERROR" lines.
4. Composite score is at its M1 plateau (target: ≥ 0.9 weighted average across FUSE + programs + opcodes; not strict if ZEXDOC reveals bugs that lower individual suite numbers, in which case we fix and re-measure).
5. Tag `m1-phase02-11` placed (closes the phase batch).
6. Tag `m1-cpu-complete` placed (closes M1 milestone "C").
7. **M1 done.** Headless Z80 capable of running real Z80 programs and passing ZEXDOC.

## Open Questions (deferred to implementation)

- **Q1:** Best place to put the BDOS PC-equality check — top of the dispatch loop in `ZexdocRunner` (proposed above), or as a `Dispatcher` interceptor? Proposed: in `ZexdocRunner` only — keeps `Dispatcher`/`Cpu` free of CP/M concerns. Decide in WU 2.11-6.
- **Q2:** CRC-8 algorithm parameters lock-in — propose poly=0x07, init=0x00, no reflection, no XOR-out. Compute the expected CRC of `0x31 0x32 0x33 0x34` ("1234") in WU 2.11-5 plan and lock it into the fixture.
- **Q3:** Should the fixture binaries include a `.lst` (assembler listing) for traceability? Out of scope — `.asm` source plus the per-byte plan table is sufficient.
- **Q4:** Should `m1-cpu-complete` be a signed annotated tag? Defer to implementation; default unsigned annotated.
