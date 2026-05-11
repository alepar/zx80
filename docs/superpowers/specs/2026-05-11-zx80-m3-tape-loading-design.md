# M3: Tape Loading — High-Level Design

## Goal

Add tape loading to the Spectrum 48K emulator. Read `.tap` and `.tzx`
(standard-block subset) files, load them into the emulator via both
ROM-trap interception (fast path, ~95% of standard-loader games) and
real-time pulse-level simulation (slow path, turbo-data 0x11 blocks and
non-standard loaders). New CLI flag `--tape=<path>`. End-to-end
milestone: load 3-5 real or synthetic "game" tapes and verify each
ends up at a recognizable screen state.

## Context

After M2 (`m2-spectrum-machine` + `m2-hello-world`): emulator boots
the real Sinclair 48K ROM, runs BASIC, accepts keyboard input, prints
to the screen, plays the beeper. CPU is FUSE-perfect (1356/1356) and
opcode coverage is 1776/1792 = 99.1%. Composite SCORE 0.9984.

M3 turns the emulator into a real Spectrum: feed it a tape, run a
game. This is the "do real things" milestone.

## Score System (locked from brainstorming)

Four new Suites plug into the score harness alongside the existing
five (opcodes, fuse, programs, boots-to-basic):

| Suite | Weight | Test count | What it scores |
|---|---|---|---|
| `TapeParserSuite` | 0.05 | ~30 | Pure parser: can we decode `.tap` and `.tzx` standard-block-subset fixtures into typed block structures? No CPU involvement; data-layer only. |
| `TapeTrapLoadSuite` | 0.10 | ~15 | Instant ROM-trap loading: synthetic fixtures with known payloads loaded via LD-BYTES interception at 0x0556; assert memory contains expected bytes at expected addresses. |
| `TapePulseLoadSuite` | 0.05 | ~5-10 | Real-time pulse-level loading: .tzx 0x11 (turbo) and 0x13 (pulse sequence) blocks loaded by driving the EAR pin; assert same memory outcome as trap loading. |
| `GameMilestoneSuite` | 0.05 | 3-5 | End-to-end "real game works" gates. Mix of 2-3 synthetic "fake games" we build + 1-2 real freeware-licensed Spectrum games (sourced via Gradle download task like the ROM). Each fixture loaded, run N frames, assert recognizable screen pattern (text via ScreenReader, or pixel SHA). Binary pass/fail. |

Total new weight: **0.25** added to the normalized Score formula
(M2.9's `sum * ratio / sum * weight`). Each new test that passes
nudges the score up. Ceiling stays at 1.0.

### Format scope

- **`.tap` fully.** Simple format: each block is a 2-byte little-endian length followed by that many data bytes. Standard ROM loader expects the first data byte to be `0x00` (header) or `0xFF` (data) followed by content, with a final parity byte.
- **`.tzx` standard-block subset.** Block types: `0x10` Standard Speed Data (binary-compatible with .tap blocks), `0x11` Turbo Speed Data, `0x20` Pause / Stop the Tape, `0x21` Group Start (info), `0x22` Group End (info), `0x30` Text Description (info), `0x32` Archive Info (info). Skipped: `0x12-0x14` pulse-level blocks (deferred — pulse loader supports `0x11` via the same machinery, but we don't surface arbitrary pulse sequences yet), `0x18` CSW, `0x25` loop blocks, exotic types.

Block IDs / format details from the canonical TZX spec at
[worldofspectrum.org/TZXformat.html](https://worldofspectrum.org/TZXformat.html).

### Load mechanism — ROM trap + pulse fallback

**ROM trap (fast path).** Detect CPU entry to the Sinclair ROM's
LD-BYTES routine at 0x0556. When PC reaches that address with the
expected register setup (A = block type, DE = byte count, IX = load
address, F's carry flag = verify/load mode), intercept: copy the next
tape block's data bytes directly into RAM at IX, set DE=0, set Z flag,
clear carry, push a synthetic RET, advance PC to the LD-BYTES exit
point. Instant load; transparent to the BASIC LOAD command.

**Pulse fallback.** For .tzx `0x11` turbo blocks (or any case where the
ROM trap isn't appropriate), drive the EAR pin via SpectrumIoBus's
read of port 0xFE bit 6. A `TapePulser` class converts tape block data
into a pulse-edge timeline (timestamps + new EAR level) and consumes
it one frame at a time. The CPU runs the loader code in real time and
sees the same waveform a real cassette would produce.

The two paths coexist:
- A `TapeDeck` holds the currently-loaded tape file.
- When CPU enters 0x0556 (LD-BYTES), the dispatcher checks if a tape
  is loaded AND if the next block is "trappable" (standard-speed
  data, not turbo). If yes → fast trap. Otherwise → pulse mode
  engaged, CPU runs the loader, EAR-pulse timeline feeds it bytes.
- A CLI flag `--no-tape-trap` forces pulse mode for everything (useful
  for testing).

## Architecture

### New package: `machine/tape/`

```
src/main/kotlin/ru/alepar/zx80/machine/tape/
  TapeFile.kt                NEW   sealed type: TapTapeFile | TzxTapeFile
  TapBlock.kt                NEW   data class (length: Int, bytes: ByteArray)
  TzxBlock.kt                NEW   sealed hierarchy: TzxStandardData, TzxTurboData, TzxPause, TzxGroupStart, etc.
  TapParser.kt               NEW   .tap binary → List<TapBlock>
  TzxParser.kt               NEW   .tzx binary → List<TzxBlock>
  TapeDeck.kt                NEW   holds current TapeFile, tracks block index, "playing" state
  RomTrap.kt                 NEW   detects 0x0556 entry, copies data, advances PC
  TapePulser.kt              NEW   block data → pulse-edge timeline; consumed by SpectrumIoBus reads of port 0xFE bit 6
  TapeFixtureBuilder.kt      NEW   test helper that builds synthetic .tap and .tzx byte arrays programmatically
```

### Modifications

```
src/main/kotlin/ru/alepar/zx80/
  cpu/Dispatcher.kt          MODIFY   add tape-trap hook called before dispatch; if PC == 0x0556 and tapeDeck.hasTrap(), invoke RomTrap
  machine/SpectrumIoBus.kt   MODIFY   read of port 0xFE consults tapeDeck.earLevel(cpu.tStates) when pulse-mode is active
  machine/Spectrum48k.kt     MODIFY   add `val tapeDeck: TapeDeck` field; wire RomTrap and pulse-mode glue
  cli/SpectrumCommand.kt     MODIFY   add `--tape=<path>` and `--no-tape-trap` options; load file into tapeDeck before reset
```

### Score harness additions

```
src/main/kotlin/ru/alepar/zx80/harness/suites/
  TapeParserSuite.kt         NEW
  TapeTrapLoadSuite.kt       NEW
  TapePulseLoadSuite.kt      NEW
  GameMilestoneSuite.kt      NEW
src/main/kotlin/ru/alepar/zx80/cli/
  ScoreCommand.kt            MODIFY   register the four new suites
```

### Build / resources

```
build.gradle.kts             MODIFY   add downloadGame tasks for 1-2 freeware games, similar to downloadRom
src/test/resources/games/    NEW dir  destination of downloaded freeware games (gitignored; populated by Gradle)
```

Freeware game candidates (implementor researches at fixture-design
time, picks 1-2 with explicit PD/CC license from World of Spectrum's
licensing-clear archive):
- Castle Master (Domark, released as freeware by author)
- CSSCGC contest entries (CC-licensed compilation)
- Author-released PD games from worldofspectrum.org

SHA-pinned downloads, same pattern as `48.rom`.

## Phase breakdown

### M3.1: Tape format parsers

**Scope.** Implement `.tap` and `.tzx` parsing without any CPU
interaction. Pure file-bytes-to-typed-blocks transformation.

**Files.**
- `machine/tape/TapeFile.kt` — sealed type wrapping a list of blocks.
- `machine/tape/TapBlock.kt` — simple `data class TapBlock(val data: ByteArray)`.
- `machine/tape/TzxBlock.kt` — sealed hierarchy: `TzxStandardData`,
  `TzxTurboData`, `TzxPause`, `TzxGroupStart`, `TzxGroupEnd`,
  `TzxTextDescription`, `TzxArchiveInfo`, `TzxUnknown(id: Int, raw:
  ByteArray)` for non-standard blocks (preserved but not interpreted).
- `machine/tape/TapParser.kt` — `parseTap(bytes: ByteArray):
  TapTapeFile`. Reads 2-byte LE length + data, repeats to EOF.
- `machine/tape/TzxParser.kt` — `parseTzx(bytes: ByteArray):
  TzxTapeFile`. Validates the 10-byte "ZXTape!" header + version byte,
  then iterates block-by-block per the TZX spec block IDs.
- `machine/tape/TapeFixtureBuilder.kt` (test helper) — programmatic
  builder for synthetic fixtures.
- `harness/suites/TapeParserSuite.kt` — wires ~30 fixtures into the
  score harness. Each fixture asserts decoded blocks match expected
  structure.

**Tests** (~30 assertions across `TapParserTest`, `TzxParserTest`,
`TapeParserSuiteTest`):
- Empty `.tap` → empty file.
- Single-block `.tap` with a header block (0x00 flag) → correctly
  parsed.
- Multi-block `.tap` → all blocks decoded in order.
- Length-prefix corruption (length > remaining bytes) → throw
  `TapParseException`.
- `.tzx` header validation (wrong magic, wrong version) → throw.
- `.tzx` 0x10 block (standard data) → decoded with correct pause and
  length.
- `.tzx` 0x11 block (turbo data) → all timing fields decoded.
- `.tzx` 0x20 / 0x21 / 0x22 / 0x30 / 0x32 → each decoded.
- `.tzx` with an unknown block ID → wrapped in `TzxUnknown`, parser
  continues.

**Deps.** None.

### M3.2: ROM-trap loader

**Scope.** Detect `LD-BYTES` entry at 0x0556; intercept and copy the
next trappable tape block directly into memory.

**Files.**
- `machine/tape/TapeDeck.kt` — holds current `TapeFile`, current
  block index, "playing" state. API: `loadTape(file)`,
  `currentBlock(): TapeBlock?`, `advanceBlock()`, `hasTrap(): Boolean`
  (true if current block is a standard data block).
- `machine/tape/RomTrap.kt` — `tryTrap(cpu: Cpu, mem: Memory, deck:
  TapeDeck): Boolean`. Checks PC == 0x0556 AND deck.hasTrap(). If so:
  reads CPU registers (A=expected flag byte, DE=length, IX=address),
  reads the next block bytes from deck, verifies parity, writes bytes
  to memory, sets CPU return state (DE=0, F=Z+!C, PC=next-instruction-
  after-LD-BYTES-CALL-site), advances deck. Returns true on success.
- `cpu/Dispatcher.kt` modify — add a hook called at the start of
  decodeAt; if a `pcHook: ((cpu, mem) -> Op?)?` returns non-null, use
  that as the Op instead of the normal dispatch table.
- `machine/Spectrum48k.kt` modify — wire `tapeDeck`; install pcHook
  that calls `RomTrap.tryTrap`.
- `harness/suites/TapeTrapLoadSuite.kt` — wires ~15 fixtures.

**Tests** (~15 assertions):
- Synthetic .tap with 1-byte payload at address 0x6000 → trap fires,
  memory[0x6000] equals expected byte.
- Synthetic .tap with a 256-byte block at 0x6000 → all 256 bytes
  loaded.
- Multi-block synthetic .tap with header + data → both blocks load on
  successive LD-BYTES calls.
- Trap with no tape loaded → no-op (CPU falls through to real ROM
  loader).
- Trap with a `.tzx` 0x10 block → behaves identically to .tap.
- Trap disabled (`deck.trapEnabled = false`) → falls through.
- Parity-mismatch fixture → trap returns "carry set, Z clear"
  signaling load failure to the BASIC caller.

**Deps.** M3.1.

### M3.3: Pulse-level loader

**Scope.** Drive the EAR pin in real time from tape block bytes. Used
for .tzx 0x11 turbo blocks and any case where the user passes
`--no-tape-trap`.

**Files.**
- `machine/tape/TapePulser.kt` — converts a block's data + timing
  parameters into a sequence of `(absoluteTState, earLevel)` events.
  For a standard or turbo block the sequence is: pilot tone (~8063
  pulses of 2168 T-state half-period) + sync pulses (667 + 735 T-states)
  + data bits (855 T-states for 0, 1710 T-states for 1, half-periods)
  + end pause. API: `start(blockData, startTState: Long)`,
  `earLevelAt(tState: Long): Int` (returns 0 or 0x40, the bit-6 mask
  in port 0xFE reads).
- `machine/SpectrumIoBus.kt` modify — when reading port 0xFE
  (keyboard + EAR + idle), bit 6 comes from
  `tapeDeck.pulser?.earLevelAt(cpu.tStates)` if pulse mode is engaged;
  otherwise the existing constant 1.
- `harness/suites/TapePulseLoadSuite.kt` — wires ~5-10 fixtures.

**Tests** (~10 assertions):
- TapePulser starts with EAR low; pilot-tone alternation correct at
  expected T-state intervals.
- Sync pulses follow pilot tone with correct timing.
- Bit-0 vs bit-1 data pulses have correct half-period durations.
- Synthetic .tzx 0x11 fixture loaded via pulse mode → memory contents
  match the expected payload after running BASIC LOAD.
- End pause: EAR is low (or driven high per pause-block-id) for the
  pause duration after the block.
- Fixtures are slower than trap-mode equivalents but produce
  identical memory outcomes.

**Deps.** M3.1.

### M3.4: CLI integration

**Scope.** Wire tape loading into the `zx80 spectrum` CLI.

**Files.**
- `cli/SpectrumCommand.kt` modify:
  - `--tape=<path>` option. Reads the file, dispatches to TapParser
    or TzxParser based on extension/magic, hands the parsed TapeFile
    to `machine.tapeDeck.loadTape(...)` before `reset()`.
  - `--no-tape-trap` flag. When set, `machine.tapeDeck.trapEnabled =
    false`; all blocks load via pulse mode.

**Tests.**
- CLI `--help` lists `--tape` and `--no-tape-trap`.
- Manual smoke (DISPLAY-required, deferred): `zx80 spectrum
  --tape=<path>` launches with the tape pre-loaded; typing `LOAD ""`
  in BASIC loads and runs the tape.

**Deps.** M3.2, M3.3.

### M3.5: Game milestone fixtures

**Scope.** Build the GameMilestoneSuite fixtures.

**Files.**
- `harness/suites/GameMilestoneSuite.kt` — 3-5 fixtures, each: load,
  run N frames, ScreenReader-match a known text pattern OR pixel SHA.
- Synthetic fake games (2-3): generated via TapeFixtureBuilder.
  Examples:
  - "fake-game-1": loader-only .tap that paints `LOAD OK` on screen
    via direct screen-RAM writes after BASIC `LOAD ""` completes.
  - "fake-game-2": .tap with header + data block + `RANDOMIZE USR
    <addr>` that runs machine code printing `RUN OK`.
  - "fake-game-3": .tzx 0x11 turbo block version of fake-game-2;
    exercises pulse-mode end-to-end.
- Real freeware games (1-2): downloaded via Gradle task.
  Implementor researches and picks at fixture-design time. Candidates
  with verified PD/CC licenses on World of Spectrum.
- `build.gradle.kts` modify — add `downloadGame1`, `downloadGame2`
  tasks mirroring `downloadRom`.

**Tests** (~5 fixture executions, plus suite-level unit tests):
- Each synthetic game loads + runs + ScreenReader.contains() its
  expected pattern → suite passed = 5/5.
- If a freeware-game download fails (network), the suite reports
  partial PASS for the synthetics + N/A for the missing freeware
  fixtures; SCORE drops slightly but doesn't block.

**Deps.** M3.2, M3.3.

### M3.6: Sweep + milestone tag

**Scope.** Final validation.

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. All new tests pass.
3. Existing M2 tests still pass — tape additions are opt-in;
   SpectrumCommand without `--tape` behaves identically to today.
4. Score harness: FUSE 1356/1356, programs 5/5, boots-to-basic 3/3,
   ZEXDOC clean, AND new suites report passing counts:
   - tape-parser: ~30/30
   - tape-trap-load: ~15/15
   - tape-pulse-load: ~10/10
   - game-milestone: ~5/5 (or partial if freeware download skipped)
5. Composite SCORE ≥ 0.998 (Phase H + residuals + M2 milestones
   preserved). With new suites all-passing, SCORE should tick slightly
   higher than 0.9984.

Tag: `m3-tape-loading`.

Close `zx80-48f`-equivalent M3 epic.

**Deps.** M3.1, M3.2, M3.3, M3.4, M3.5.

## Out of scope

- Snapshot loading (.sna, .z80) — separate scope (potentially M4 or a
  parallel sibling spec).
- TZX pulse-level blocks beyond 0x11 (0x12 Pure Tone, 0x13 Pulse
  Sequence, 0x14 Pure Data) — deferred; the pulse machinery would
  support them but the parser/loader paths aren't wired.
- TZX loop blocks (0x24/0x25), call sequence (0x26), jump (0x23) —
  niche; deferred.
- CSW recording blocks (0x18/0x19) — audio-format compression; M4 if
  ever.
- Tape saving (intercepting SAVE to produce .tap output) — far future.
- Loading-screen multi-color border timing during fast load — depends
  on M2.7 contention.
- Multiple-tape support (insert/eject mid-emulation) — single-tape
  scope for M3.

## Composite score impact

- Pre-M3: SCORE 0.9984 across 5 suites at weights {opcodes 0.2, fuse
  0.7, programs 0.1, boots-to-basic 0.1, other 0.0}, total 1.1
  (normalized → ~1.0 ceiling).
- Post-M3: 9 suites at additional weights {parser 0.05, trap 0.10,
  pulse 0.05, games 0.05}, total 1.35 (normalized → ~1.0 ceiling
  preserved).
- With all-passing M3 suites the contribution to SCORE adds ~0.25 of
  weight all at ratio 1.0; the normalized score should remain at or
  near 1.0 (currently 0.9984 due to opcode coverage 1776/1792 = 0.991
  at 0.2 weight, which holds steady through M3).

## Architecture notes

**Determinism.** Tape loading is deterministic given the same tape
file and CPU starting state. Both trap and pulse loaders produce
identical memory outcomes for the same block (verified by the
fixture-pair test pattern in M3.3).

**Threading.** Tape loading runs on the Pacer thread (during runFrame
inside step). No locks needed.

**Backward compatibility.** `SpectrumCommand` without `--tape`
behaves identically to today: `machine.tapeDeck` is constructed but
left empty (`loadTape(null)`), the trap detects "no tape loaded" and
falls through to the real ROM loader, the EAR pin reads its
constant-high default.

**Test isolation.** Existing tests (FUSE, programs, boots-to-basic,
hello-world) don't touch the tape system; they construct `Memory()` /
`Spectrum48k()` without tape and see the same behavior as before.

## Risks

- **TZX 0x10 block timing fields.** Standard-speed data has a pause
  field (default 1000ms = 50000 T-states). For trap-mode loading the
  pause is irrelevant (instant); for pulse-mode it must be honored or
  some games' multi-stage loaders break. The pulse loader handles it.
- **Pulse-loader CPU correctness.** Custom loaders use precise
  timing; our 50Hz INT scheduler is frame-quantized. The CPU itself
  runs cycle-accurate (with M2.7 if landed; without contention it's
  still T-state-accurate). Most ROM-loader and turbo-loader patterns
  should work; exotic custom loaders may not. Out of scope.
- **Freeware game licensing.** Implementor must verify each picked
  game's license is explicit (PD or CC). When in doubt, skip and use
  synthetics. World of Spectrum's per-file license tags are the
  source of truth.
- **Gradle download task brittleness.** Same pattern as `downloadRom`
  — pinned SHA, mirror-URL fallback note in the build script.
- **Test name colons.** Recurring Kotlin trap. Backtick-quoted test
  method names must use commas/dashes, never colons.
