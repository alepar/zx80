# ZX Spectrum 48K Emulator — Design (M1: Headless Z80)

**Date:** 2026-05-02
**Branch:** `opus-4.7`
**Status:** Approved, ready for implementation planning

## Goal

Rewrite this Sinclair-themed emulator project as a **ZX Spectrum 48K emulator in Kotlin**, with a scoring harness that produces a numeric gradient an autonomous loop can climb. Documented Z80 instruction set only. M1 (this spec) delivers a headless Z80 capable of loading and running a hand-written program from RAM. M2 (separate spec) adds the Spectrum machine: ROM, ULA, keyboard, interrupts.

The project keeps the name `zx80` for historical continuity, despite emulating a Spectrum (which uses a Z80).

## Background

The repo holds a 2010-era Java emulator project that the author started while learning how computers work and never finished. Only the `LD` instruction is implemented. Core data types (`Cell` as heap-allocated byte wrapper, `RegistryBlock` as `HashMap<Register, Cell>`) prevent reaching real-hardware speeds. Decoder design ("ask every `OpFactory` if it accepts these bytes") works but is O(n) per instruction.

Patterns worth preserving from the original code:
- One class per opcode pattern, with one test per class
- `Mnemonic` thread through ops and operand sources, useful for disassembly
- Decode-time construction of operation objects with their operands

Patterns to drop:
- `Cell` / `Word` / `Address` heap wrappers in hot paths
- HashMap-of-Register
- Linear factory-chain decode

## Milestone Ladder

- **M0** Tooling baseline. Empty Kotlin/Gradle project on branch `opus-4.7`. Harness CLI runnable, prints `SCORE: 0.000`.
- **M1** Z80 CPU complete (headless). **This spec.** Full documented Z80 instruction set; T-state counting; loads raw Z80 binary into RAM; runs until HALT or cycle budget. No ROM, no display, no keyboard. ZEXDOC passes.
- **M2** ZX Spectrum 48K machine. Boot the 48K ROM into BASIC; ULA video to host window; matrix keyboard from host keyboard; 50Hz interrupt; beeper. Out of scope for this spec — separate brainstorm.
- **M3** Tape and snapshot loading (`.tap`/`.tzx`/`.sna`/`.z80`). Run real games. Out of scope.

## Architecture

### CPU state — flat, no wrappers

```kotlin
class Cpu {
    var a = 0; var f = 0
    var b = 0; var c = 0
    var d = 0; var e = 0
    var h = 0; var l = 0
    // Alternate set
    var aAlt = 0; var fAlt = 0
    var bAlt = 0; var cAlt = 0
    var dAlt = 0; var eAlt = 0
    var hAlt = 0; var lAlt = 0
    // 16-bit
    var ix = 0; var iy = 0
    var sp = 0; var pc = 0
    var i  = 0; var r  = 0
    // Interrupt state
    var iff1 = false; var iff2 = false
    var im   = 0
    var halted = false
    // Cycle accumulator
    var tStates: Long = 0
}
```

8-bit registers as `Int` in range `0..255` (avoids signed-byte pain). Register pairs are computed (`bc = (b shl 8) or c`). `EX`/`EXX` are field swaps. Flags are bit operations on `f`.

### Memory — `ByteArray(0x10000)`

Read returns `Int` (`bytes[addr].toInt() and 0xff`); write takes `Int`. No `Cell`, no `Address`, no allocation. ROM-vs-RAM distinction is a flag in M1 (not enforced) and a `0x4000` write-guard in M2.

### `Op` layer — testable abstraction over flat state

```kotlin
interface Op {
    fun execute(cpu: Cpu, mem: Memory)
    fun mnemonic(operandFetcher: OperandFetcher): String
    val baseCycles: Int
    val operandLength: Int
}
```

Each opcode pattern is its own class with its own JUnit 5 test. The `Op` decomposition is preserved from the original code — what changes is that `LdRegReg` now reads and writes named CPU fields (via a `Reg` enum with `read(cpu): Int` / `write(cpu, v: Int)`) instead of going through `Cell` and `CellRetriever`.

The `Reg` enum covers the seven 8-bit register slots `B,C,D,E,H,L,A`. The Z80's `r` encoding bit pattern `110` (which means `(HL)` — a memory access through HL) is **not** part of the enum; opcodes with a `(HL)` operand are instantiated as a separate `Op` class (e.g. `LdRegFromHl` rather than `LdRegReg`). This keeps `Reg.read/write` purely register-side and avoids smuggling memory access through it.

Each `Op.execute` is responsible for:
- Advancing `cpu.pc` by `1 + operandLength`
- Updating `cpu.tStates` by the correct cycle count (varies for conditional jumps)
- Updating `cpu.r` (lower 7 bits incremented per M1 cycle: 1 for unprefixed, 2 for prefixed)
- Updating flags

Operands (`n`, `nn`, `d`) are read from `mem[pc+1..]` inside `execute()`; they are not baked into the `Op` instance.

### Decoder — eager dispatch table built once at startup

```kotlin
class Decoder {
    val main: Array<Op?> = arrayOfNulls(256)  // unprefixed
    val cb:   Array<Op?> = arrayOfNulls(256)  // CB-prefixed
    val ed:   Array<Op?> = arrayOfNulls(256)  // ED-prefixed
    val dd:   Array<Op?> = arrayOfNulls(256)  // DD-prefixed (IX)
    val fd:   Array<Op?> = arrayOfNulls(256)  // FD-prefixed (IY)
    val ddcb: Array<Op?> = arrayOfNulls(256)  // DD CB d xx
    val fdcb: Array<Op?> = arrayOfNulls(256)  // FD CB d xx
}
```

`OpTableBuilder` populates the tables once at startup, looping `0..255` per table and instantiating the right `Op` per opcode (e.g. for the `0x40-0x7F` block of the main table it instantiates `LdRegReg(srcReg, dstReg)` per byte). Slow startup is acceptable; runtime dispatch is O(1) array index.

### Fetch-decode-execute loop

```kotlin
fun step() {
    val opcode = mem.read(cpu.pc)
    val op = decoder.main[opcode] ?: handlePrefix(opcode)
    op.execute(cpu, mem)
}

fun runUntil(stopCondition: () -> Boolean) {
    while (!stopCondition()) step()
}
```

### Why this design

- Preserves the original code's testability (one class per opcode pattern, one test per class).
- Preserves disassembly hooks via `mnemonic()` — useful for debugging.
- Drops per-instruction allocation forced by `Cell`/`Word`/`Address`.
- Drops linear "ask every factory" decode for an O(1) array index.
- One virtual call per executed instruction is something C2 inlines well — should comfortably exceed 10 MHz emulated Z80 on modern hardware (target floor: 3.5 MHz, real-hardware speed).

### Files / package layout (single Gradle module)

```
src/main/kotlin/ru/alepar/zx80/
  cpu/
    Cpu.kt              // state
    Memory.kt
    Reg.kt              // enum: B,C,D,E,H,L,A and its read/write (no (HL))
    Flags.kt            // bit constants + helpers
    Decoder.kt
    OpTableBuilder.kt
  op/
    Op.kt
    ld/                 // LdRegReg, LdRegImm, LdRegMem, LdMemA, ...
    arith/              // Add, Adc, Sub, Sbc, Cp, Inc, Dec, Daa, ...
    logic/              // And, Or, Xor, Cpl, Neg, ...
    rot/                // Rlca, Rrca, Rla, Rra, Rlc, Rrc, Rl, Rr, Sla, Sra, Srl
    bit/                // Bit, Set, Res
    branch/             // Jp, Jr, Djnz, Call, Ret, Rst
    stack/              // Push, Pop
    io/                 // In, Out, Ini, Outi, ...
    block/              // Ldi, Ldir, Cpi, Cpir, ...
    ex/                 // Ex, Exx, ExAfAf
    misc/               // Nop, Halt, Di, Ei, Im, Ccf, Scf, ...
  cli/
    Main.kt             // entry point: ./harness <subcommand>
  harness/
    Score.kt
    suites/
      OpcodeCoverage.kt
      FuseSuite.kt
      ProgramsSuite.kt
src/main/resources/
  fuse/                  // FUSE tests.in / tests.expected (vendored)
                         //   — consumed by `zx80 score --suite=fuse`
  programs/              // assembled .bin + expected-state .json
                         //   — consumed by `zx80 score --suite=programs`
  zexdoc.com             // for milestone gate, consumed by `zx80 zexdoc`
src/test/kotlin/...     // mirrors main, one test class per Op
```

## Harness

### CLI

Application plugin → `./gradlew installDist` produces `build/install/zx80/bin/zx80`. Subcommands:

```
zx80 score                 # run all suites, print headline + write build/score.json
zx80 score --suite=fuse    # one suite only
zx80 score --diff          # show regressions vs build/score.json from last run
zx80 score --strict        # exit nonzero on regression (CI mode)
zx80 run program.bin       # load raw Z80 binary at 0x0000, run until HALT, dump state
zx80 disasm program.bin    # disassemble using the Op mnemonic() thread
zx80 zexdoc                # M1 milestone gate: run ZEXDOC, print CRC table
zx80 bench                 # report emulated MHz on a fixed workload
```

`score` is the autonomous-loop entry point. Returns exit 0 by default (so loops don't abort on regressions); `--strict` makes it exit nonzero on regression.

### Headline output (stdout)

Single line, grep-friendly, stable format:

```
SCORE: 0.342  (opcodes 187/1024, fuse 287/1289, programs 1/5)
```

The `0.342` is a weighted composite. Default weights: opcodes 0.2, fuse 0.7, programs 0.1.

### `build/score.json` (machine-readable detail)

```json
{
  "score": 0.342,
  "timestamp": "2026-05-02T15:31:00Z",
  "git": { "branch": "opus-4.7", "sha": "abcd1234", "dirty": false },
  "suites": {
    "opcodes": {
      "weight": 0.2,
      "passed": 187, "total": 1024, "ratio": 0.183,
      "missing": ["main:0x80", "main:0x81"]
    },
    "fuse": {
      "weight": 0.7,
      "passed": 287, "total": 1289, "ratio": 0.223,
      "by_opcode": { "0x40": {"passed": 1, "total": 1} },
      "regressions_vs_previous": [],
      "newly_passing_vs_previous": ["0x47", "0x4f"]
    },
    "programs": {
      "weight": 0.1,
      "passed": 1, "total": 5, "ratio": 0.2,
      "results": [
        {"name": "fib10", "status": "PASS", "cycles": 1247},
        {"name": "bubblesort", "status": "FAIL", "reason": "wrong final memory at 0x100"}
      ]
    }
  }
}
```

`score.json` is rotated to `score.prev.json` on each run so diffs are cheap.

### Suites

- **OpcodeCoverage.** Walks the 7 dispatch tables (`main`, `cb`, `ed`, `dd`, `fd`, `ddcb`, `fdcb`); counts non-null entries against the documented opcode set (a static list of which entries are documented). Total denominator: ~1024 documented opcodes across all tables (exact number set when building the documented-opcode table).
- **FuseSuite.** Vendors `tests.in` and `tests.expected` from the FUSE Z80 test suite (BSD-licensed; bundle `LICENSE.fuse`). For each test case: deserialize initial CPU+memory state into a fresh `Cpu`+`Memory`; run *one* instruction; compare resulting state byte-by-byte against expected, including T-states. Pass = exact match. Tests covering undocumented opcodes/flags are tagged and excluded from the headline (reported in `excluded_undocumented` counter).
- **ProgramsSuite.** Hand-written assembled Z80 binaries in `src/test/resources/programs/`, each with a sibling `.expected.json`:

  ```json
  { "name": "fib10",
    "load_at": 0,
    "entry": 0,
    "max_cycles": 100000,
    "stop_on": "HALT",
    "expect": { "memory": { "0x100": 89 } } }
  ```

  Initial set: `nop_loop` (smoke), `fib10` (returns 89 at 0x100), `memcpy16` (LDIR), `bubblesort_8`, `crc8`.

### ZEXDOC

Not in `score` (takes minutes to run). Lives behind `zx80 zexdoc`. Used as M1's exit gate, not a daily gradient. Loads `ZEXDOC.COM` at 0x100, traps CP/M BDOS calls 2 (write char) and 9 (write string) to capture stdout, runs to completion, parses the per-family CRC lines.

### Determinism

Same git SHA + same input data → byte-identical `score.json` (modulo timestamp and git fields). Required for regression diffs and for autonomous-loop trust.

### Performance harness

`zx80 bench` runs a fixed-length workload (LDIR copying 16K, then a tight loop) and reports emulated-MHz. Not gated; just a number to watch. Floor target: 3.5 MHz (real Z80). Soft target: 10 MHz (3x real-time).

## Implementation Sequence

The harness is built first so every subsequent step has a numeric gradient.

### Phase 0 — Tooling baseline

- Create branch `opus-4.7` off `master`.
- Move existing Java sources, `pom.xml`, `zx80.iml` to `legacy/` (kept for reference, not built).
- Add Gradle (Kotlin DSL), Kotlin 2.1.x, Java 21 toolchain, JUnit 5, application plugin, kotlinx-serialization.
- `Main.kt` prints help; `./gradlew installDist` works.
- `.editorconfig`, `.gitattributes`, `README.md` stub.
- Commit: `chore: kotlin/gradle baseline on opus-4.7 branch`.

### Phase 1 — Empty harness, score = 0

- `Cpu`, `Memory` skeletons (state only, no opcodes).
- `Op` interface, empty `Decoder` (all 7 tables filled with nulls).
- OpcodeCoverage suite (counts nulls).
- FuseSuite — vendor FUSE test data, parser for `tests.in`/`tests.expected`, diff/report. All cases fail; the runner works and produces a clean report.
- ProgramsSuite — runner + first program (`nop_loop`, expects to do nothing for N cycles). Fails until `NOP` exists.
- `zx80 score` produces `SCORE: 0.000 (opcodes 0/1024, fuse 0/1289, programs 0/5)`.
- Commit: `harness: empty suites, score=0.000 baseline`.

### Phase 2 — Z80 core, in opcode-family order

Each batch is its own commit (or small commit series). After each batch, `zx80 score` should monotonically increase. If it doesn't, the previous batch broke something — diff `score.json`.

| Batch | Adds | Roughly unlocks |
|---|---|---|
| 2.1 | `NOP`, `HALT`, `DI`, `EI`, `IM 0/1/2`, register pair `LD rr,nn`, `LD (nn),HL`, `LD HL,(nn)`, `LD A,(nn)`, `LD (nn),A`, `LD r,n`, `LD r,r'`, `LD (HL),r`, `LD r,(HL)`, `LD (HL),n`, `LD A,(BC)/(DE)`, `LD (BC)/(DE),A`, `EX`/`EXX`/`EX DE,HL`/`EX (SP),HL` | All non-prefixed LD + the `nop_loop` program |
| 2.2 | All 8-bit ALU on register/immediate/`(HL)`: `ADD/ADC/SUB/SBC/AND/OR/XOR/CP A,r`, `…A,n`, `…A,(HL)`. `INC r`, `DEC r`, `INC (HL)`, `DEC (HL)`. Flag handling correct. | Most arithmetic FUSE |
| 2.3 | 16-bit arith: `ADD HL,rr`, `INC rr`, `DEC rr`, `ADC HL,rr` / `SBC HL,rr` (ED-prefixed). | More FUSE; sets up loop/index work |
| 2.4 | Jumps & calls: `JP nn`, `JP cc,nn`, `JR e`, `JR cc,e`, `DJNZ e`, `CALL nn`, `CALL cc,nn`, `RET`, `RET cc`, `RST p`, `JP (HL)`. | `fib10` program likely works after this + arith |
| 2.5 | Stack: `PUSH rr`, `POP rr` for BC/DE/HL/AF. | |
| 2.6 | Rotates/shifts on A: `RLCA`, `RRCA`, `RLA`, `RRA`, `DAA`, `CPL`, `SCF`, `CCF`. DAA is fiddly — own commit. | |
| 2.7 | CB-prefixed: full `RLC/RRC/RL/RR/SLA/SRA/SRL` on `r`/`(HL)`, `BIT/SET/RES n,r`/`(HL)`. | Whole CB table. Big FUSE jump. |
| 2.8 | DD/FD-prefixed (IX/IY): IX/IY variants of LD/ADD/INC/DEC, indexed `(IX+d)`/`(IY+d)` addressing. Reuses 2.1–2.6 logic via `RegPair` parameterization. | DD/FD tables |
| 2.9 | DDCB/FDCB-prefixed: indexed bit/rot ops. | DDCB/FDCB tables |
| 2.10 | ED-prefixed: `LDI/LDIR/LDD/LDDR`, `CPI/CPIR/CPD/CPDR`, `IN/INI/INIR/IND/INDR`, `OUT/OUTI/OTIR/OUTD/OTDR`, `NEG`, `RETI/RETN`, `LD I,A`/`LD A,I`/`LD R,A`/`LD A,R`, `RRD/RLD`. | `bubblesort_8`, `memcpy16`. Most of the rest of FUSE. |
| 2.11 | Add programs: `bubblesort_8`, `memcpy16`, `crc8`. Tighten flag-edge tests where FUSE is still red. | Programs suite goes 5/5. |

### Phase 3 — M1 milestone gate

- Add `zx80 zexdoc` subcommand (loads ZEXDOC.COM at 0x100, traps CP/M BDOS calls 2 and 9, runs to completion).
- Iterate on flag edges revealed by ZEXDOC until all CRCs match.
- Tag commit `m1-cpu-complete`.
- **End of M1 / milestone "C".**

### Estimate

Phase 0: a session. Phase 1: a session. Phase 2: the bulk; each batch is roughly half-a-session-to-a-session for a careful human, less for an autonomous loop with a strong gradient. Phase 3: a session of flag debugging. Total to M1: 15–25 focused sessions.

## Tooling Baseline

- **Branch.** `opus-4.7` off `master`. Master untouched.
- **Language.** Kotlin 2.1.x, version pinned in `gradle/libs.versions.toml`.
- **JVM.** Java 21 LTS toolchain. `kotlin.jvmTarget = 21`.
- **Build.** Gradle 8.x with Kotlin DSL. Single module. Wrapper checked in.
- **Plugins.** `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.serialization`, `application`, `com.diffplug.spotless` with `ktfmt`.
- **Dependencies.** `kotlinx-serialization-json`, `com.github.ajalt.clikt:clikt`. No logging framework yet.
- **Tests.** JUnit 5, AssertJ. No mocking library.
- **Test layout.** Mirrors main; one test class per `Op` class. Backtick-quoted readable test method names.
- **Vendored data (production resources, on the runtime classpath).** `src/main/resources/fuse/{tests.in,tests.expected}` (FUSE upstream, BSD); `src/main/resources/programs/*.bin` (assembled with `pasmo` outside the build, `.asm` source checked in alongside); `src/main/resources/zexdoc.com` (public-domain Frank Cringle binary). They live in `main/` (not `test/`) because the harness CLI consumes them at runtime after `installDist`.
- **CI.** GitHub Actions: `./gradlew check`, then run `score` and upload `score.json` as artifact. Doesn't fail on regression initially.
- **Repo.** `.gitignore` for Gradle/IDEA/Kotlin; `.editorconfig` (4-space, LF, UTF-8); `README.md` stub.
- **Deliberately omitted.** No DI framework, no logging framework, no multi-module split, no Maven publish, no detekt.

## Risks

- **R1: Flag correctness is fiddly.** DAA, half-carry rules around ADC/SBC, rotate-A vs rotate-r flag differences, parity bit on logic ops. Mitigation: FUSE tests catch this granularly; ZEXDOC is the gate. Budget for a flag-debugging session in Phase 3.
- **R2: ED-prefixed block ops** (`LDIR`, `CPIR`, etc.) decrement PC by 2 to "loop in place" until `BC=0`, with the interrupt actually checked after each iteration. Easy to get cycle accounting and early-exit-on-IRQ wrong. Mitigation: explicit per-op test asserting PC behavior + cycle counts.
- **R3: T-state accuracy for conditional ops.** `JR cc,e`, `RET cc`, `CALL cc,nn`, `JP cc,nn` have different cycle counts depending on condition outcome. Forgetting this breaks FUSE silently — state passes but cycles fail. Mitigation: FUSE flags it; obvious to fix when caught.
- **R4: HL/IX/IY parameterization in DD/FD prefixes.** Tempting to copy-paste HL ops into IX/IY ops. Mitigation: a `RegPair` enum (`HL`/`IX`/`IY`) with a single op family parameterized over it.
- **R5: FUSE test format quirks.** Per-test register dumps separated by blank lines; "events" in expected file include memory-read/write traces we don't model. Mitigation: parser ignores events we don't care about; only state + T-states matter.
- **R6: Autonomous-loop premise leans on the harness gradient being reliable.** Mitigation: `--strict` mode for CI, regression diffing between runs, `score.prev.json` rotation.

## Open Questions (deferred to implementation)

- Exact `OperandFetcher` interface for `Op.mnemonic()` — design when writing `zx80 disasm`.
- `OUT (n),A` and `IN A,(n)` in M1 with no I/O hardware — answer: a no-op `IoBus` returning `0xFF` on read, ignoring writes. Replaced in M2.
- Composite score weights (currently 0.2/0.7/0.1) — revisit if the gradient feels uninformative.

## Non-Goals

- Undocumented Z80 opcodes, undocumented flag bits (XF/YF), MEMPTR, ZEXALL.
- Memory contention timing.
- ZX80 / ZX81 hardware. Project name is historical.
- Tape/snapshot loading (M3 / future spec).
- 128K / +2 / +3 Spectrum models.
- Multi-platform / web / Android targets.
- Wiring `legacy/` into the new build. It exists for human reference reading only.

## Done Criteria for M1

1. `zx80 zexdoc` reports all CRCs matching.
2. `zx80 score` reports `programs 5/5`.
3. `zx80 score` reports `fuse N/N` where N excludes undocumented-tagged tests.
4. `zx80 bench` reports ≥ 10 MHz emulated Z80.
5. Composite score is reproducible (same SHA → same score.json modulo timestamp/git fields).
