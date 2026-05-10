# M2.1: Memory map + 48K ROM load + write-guard at 0x4000 — Design

## Goal

Lay the M2 foundation: introduce Spectrum-shaped memory with a write-guard
at 0x4000, a way to bundle and load the real Sinclair 48K ROM, and a
minimal `Spectrum48k` machine container that performs Z80 reset and boots
the ROM. No frame loop, no interrupts, no ULA — those are subsequent WUs.

## Context

Beads issue: `zx80-myy` (parent: `zx80-48f` M2 epic).

Master spec line 66:
> ROM-vs-RAM distinction is a flag in M1 (not enforced) and a 0x4000
> write-guard in M2.

After M1 (Phase G), the emulator has a flag-perfect documented Z80 with
ZEXDOC clean and FUSE 1354/1356. Memory is a flat 64K array with no
ROM/RAM distinction; every test and harness suite calls `Memory()` and
writes freely across the whole address space.

M2.1 is the first concrete M2 step: it introduces the machine-shape
infrastructure (memory map, ROM loading, machine container) that
subsequent M2 WUs build on. M2.2 adds the frame loop and 50Hz interrupt;
M2.3-M2.4 add ULA video; M2.5 wires the keyboard matrix to IoBus.

## Scope

Five concrete deliverables:

### M2.1-A: WritePolicy + Memory write-guard

New `cpu/WritePolicy.kt`:

```kotlin
fun interface WritePolicy {
    fun shouldWrite(addr: Int): Boolean
}

object OpenPolicy : WritePolicy {
    override fun shouldWrite(addr: Int) = true
}

class ReadOnlyBelow(private val limit: Int) : WritePolicy {
    override fun shouldWrite(addr: Int) = (addr and 0xFFFF) >= limit
}
```

`Memory` gains a constructor parameter:

```kotlin
class Memory(private val writePolicy: WritePolicy = OpenPolicy) {
    fun write(addr: Int, value: Int) {
        if (!writePolicy.shouldWrite(addr)) return
        bytes[addr and ADDR_MASK] = (value and 0xFF).toByte()
    }

    /** Bypasses [writePolicy] — used to install ROM at boot. */
    fun loadAt(addr: Int, payload: ByteArray) { ... unchanged ... }
}
```

Default `OpenPolicy` keeps every M1 call site (~80+ test usages, plus
harness suites) compiling unchanged with M1 behavior preserved. Spectrum
constructs `Memory(ReadOnlyBelow(0x4000))`.

Z80 hardware semantics on Spectrum: ROM-area writes complete a bus cycle
but the byte is dropped — no fault, no signal. Programs that write to
ROM (rare BASIC bugs) just see the read-back unchanged. Our silent no-op
matches.

### M2.1-B: Gradle `downloadRom` task

Hand-rolled DSL task in `build.gradle.kts` (~20 lines, no plugin
dependency):

```kotlin
val downloadRom by tasks.registering {
    val outFile = layout.buildDirectory.file("generated-resources/rom/48.rom")
    val url = "https://github.com/redcode/ZX-Spectrum-48K-ROMs/raw/master/48.rom"
    // SHA-256 of canonical Sinclair 48K ROM — captured during M2.1-B execution
    // by downloading once and recording the hex digest. Pinned thereafter.
    val expectedSha = "<captured during M2.1-B>"
    outputs.file(outFile)
    outputs.upToDateWhen { /* file exists AND sha matches */ }
    doLast {
        // URL.openStream() → outFile; verify SHA-256; fail with clear message
    }
}

tasks.processResources {
    dependsOn(downloadRom)
    from(layout.buildDirectory.dir("generated-resources"))
}
```

The task is up-to-date if `build/generated-resources/rom/48.rom` exists
and SHA-256 matches. Cached after first run; no network on subsequent
builds. Repo stays binary-clean.

Source URL: `redcode/ZX-Spectrum-48K-ROMs` is a stable GitHub mirror with
the canonical bytes. SHA-256 verify guards against drift; on mismatch the
task fails with the message `48.rom SHA-256 mismatch — got <X>, expected
<Y>. Update URL or place a verified copy at build/generated-resources/rom/48.rom`.

`--offline` path: dropping a manually-supplied 48.rom into
`build/generated-resources/rom/48.rom` (correct SHA) makes the task
up-to-date and skip the network entirely.

### M2.1-C: RomLoader

New `machine/RomLoader.kt`:

```kotlin
object RomLoader {
    fun load48k(): ByteArray {
        val stream = javaClass.getResourceAsStream("/rom/48.rom")
            ?: error("48.rom not found on classpath. Run ./gradlew downloadRom.")
        val bytes = stream.use { it.readBytes() }
        check(bytes.size == 16_384) { "48.rom expected 16384 bytes, got ${bytes.size}" }
        return bytes
    }
}
```

Loud failure on missing or wrong-sized resource — points the user at the
fix.

### M2.1-D: Cpu.reset()

Add a `reset()` method to `Cpu`:

```kotlin
fun reset() {
    pc = 0
    sp = 0xFFFF
    af = 0xFFFF
    bc = 0xFFFF
    de = 0xFFFF
    hl = 0xFFFF
    // alternates and IX/IY also 0xFFFF
    afAlt = 0xFFFF; bcAlt = 0xFFFF; deAlt = 0xFFFF; hlAlt = 0xFFFF
    ix = 0xFFFF; iy = 0xFFFF
    i = 0; r = 0
    iff1 = false; iff2 = false
    im = 0
    halted = false
    memptr = 0
    tStates = 0L
}
```

Z80 hardware reset (per Sean Young's TUZD): `PC=I=R=IFF1=IFF2=IM=0`,
all other registers technically undefined. Sinclair's ROM expects
SP/AF=0xFFFF (it executes `LD SP, ...` early but the stack is a no-go
zone before then). Setting other registers to 0xFFFF matches power-on
state on most real Z80s and is what every emulator does.

Idempotent: calling `reset()` twice gives the same state as once.

### M2.1-E: Spectrum48k machine class

New `machine/Spectrum48k.kt`:

```kotlin
class Spectrum48k(decoder: Decoder = OpTableBuilder.build()) {
    val cpu = Cpu()                                // cpu.io defaults to NoIoBus
    val mem = Memory(ReadOnlyBelow(0x4000))
    private val dispatcher = Dispatcher(decoder)

    fun reset() {
        cpu.reset()
        mem.loadAt(0, RomLoader.load48k())
    }

    fun step() {
        val op = dispatcher.decodeAt(cpu, mem)
            ?: error("no dispatch route for opcode 0x${mem.read(cpu.pc).toString(16)} at pc=0x${cpu.pc.toString(16)}")
        op.execute(cpu, mem)
    }

    fun run(cycles: Long) {
        val target = cpu.tStates + cycles
        while (cpu.tStates < target && !cpu.halted) step()
    }
}
```

After `reset()`, the CPU is positioned at the first byte of the ROM and
running it executes the real boot sequence (DI at 0xF3 → ...). `run()`
exits early if HALT — useful for the smoke test.

`cpu.io` keeps its default `NoIoBus` (returns 0xFF on read, drops writes
— matches M1). M2.5 replaces it with the keyboard-aware bus. The
`decoder` constructor parameter exists for test injection; production
uses the default `OpTableBuilder.build()`.

### Out of scope

- Frame timer + 50Hz interrupt + cycle budget per frame (M2.2)
- Spectrum48k frame-tick / runFrame() (M2.2)
- ULA video / screen RAM rendering (M2.3)
- Display backend / host window (M2.4)
- Keyboard matrix + IoBus extension for IN(0xFE) (M2.5)
- Beeper audio (M2.6)
- Memory contention (M2.7) — when this lands, WritePolicy gets a sibling
  read-cycle policy or Memory grows a contention hook
- Border color via OUT(0xFE) low 3 bits (M2.8)
- Boots-to-BASIC integration test (M2.9 sweep)
- ROM-call interception (e.g., LD-BYTES at 0x0556 for fast tape) — M3
  concern

## Architecture

**File layout (new files):**

```
src/main/kotlin/ru/alepar/zx80/
  cpu/Memory.kt                    # gains writePolicy ctor arg
  cpu/WritePolicy.kt               # NEW: interface + OpenPolicy + ReadOnlyBelow
  machine/Spectrum48k.kt           # NEW: machine container
  machine/RomLoader.kt             # NEW: classpath resource loader
build.gradle.kts                   # adds downloadRom task wired to processResources
```

`machine/` is a new package: `cpu/` is pure Z80, no machine specifics.
`harness/` is for test suites. M2 introduces machine-level concepts
(memory map, ULA, keyboard, interrupts) — they want their own package.
M2.2-M2.8 will land here.

**Memory write-guard mechanics:**

- `Memory(writePolicy: WritePolicy = OpenPolicy)` — default keeps every
  existing call site compiling unchanged with M1 semantics preserved.
- `write(addr, value)`: if `!writePolicy.shouldWrite(addr)`, silently
  no-op.
- `loadAt(addr, payload)`: bypasses the policy unconditionally. This is
  the install path; used at reset to populate ROM. Document the bypass
  in the kdoc.
- `readWord` / `writeWord` already delegate to `read`/`write`, so they
  pick up the policy automatically.

**Data flow at boot:**

```
Spectrum48k() → reset() →
  cpu.reset()                          # Z80 register state
  mem.loadAt(0, RomLoader.load48k())   # ROM bytes 0x0000-0x3FFF
  (Memory's writePolicy = ReadOnlyBelow(0x4000) is active from construction)
```

After `reset()`, calling `step()` fetches `mem.read(0)` = `0xF3` (DI),
executes, advances PC. The CPU is now running the ROM exactly as on real
hardware — until it hits a code path that needs the 50Hz interrupt or an
ULA register, which is M2.2+.

## Test strategy

### Unit tests (new files)

**1. WritePolicyTest** — 5 assertions
- `OpenPolicy.shouldWrite(any)` = true
- `ReadOnlyBelow(0x4000).shouldWrite(0x0000)` = false
- `ReadOnlyBelow(0x4000).shouldWrite(0x3FFF)` = false
- `ReadOnlyBelow(0x4000).shouldWrite(0x4000)` = true (boundary)
- `ReadOnlyBelow(0x4000).shouldWrite(0xFFFF)` = true

**2. MemoryWriteGuardTest** — extends existing MemoryTest patterns
- `Memory(ReadOnlyBelow(0x4000)).write(0x1000, 0x42)` →
  `read(0x1000)` returns 0 (write dropped)
- Same memory: `write(0x4000, 0x42)` → `read(0x4000)` returns 0x42
- `loadAt(0x0000, payload)` bypasses guard — bytes appear at 0x0000
- Default `Memory()` (OpenPolicy): `write(0x0000, 0x42)` succeeds —
  regression check for M1 callers

**3. RomLoaderTest**
- `RomLoader.load48k().size == 16384`
- `RomLoader.load48k()[0]` matches the canonical 0xF3 (DI — first byte
  of the Sinclair 48K ROM)

**4. CpuResetTest**
- After `reset()`: every documented field at its Z80-reset value
- Reset is idempotent (calling twice = same state as once)
- After `reset()` then `bumpR()`: R == 1 (proves R counter starts at 0)

**5. Spectrum48kTest**
- After `reset()`: `cpu.pc == 0`, `cpu.sp == 0xFFFF`, `cpu.af == 0xFFFF`,
  `cpu.iff1 == false`, `cpu.iff2 == false`, `cpu.im == 0`,
  `cpu.halted == false`, `cpu.tStates == 0L`
- After `reset()`: `mem.read(0x0000) == 0xF3` (DI — known first byte)
- After `reset()`: `mem.read(0x3FFF)` matches a known ROM byte (sanity
  spot-check on last byte)
- Write-through guard at runtime: after reset,
  `mem.write(0x1234, 0xAA)` then `mem.read(0x1234)` returns the original
  ROM byte (write dropped)
- Write-through to RAM: after reset, `mem.write(0x5000, 0xAA)` then
  `mem.read(0x5000)` returns 0xAA
- `step()` after reset: PC advances by 1 (DI is 1 byte)
- `run(10_000)` from reset: no exception, `cpu.tStates >= 10_000L`,
  no `IllegalStateException` from dispatch (smoke test — no specific
  state assertion since boot runs through many ROM addresses)

### Build/integration

**6. Gradle task verification** — manual smoke (not a JUnit test):
`./gradlew clean downloadRom` produces `build/generated-resources/rom/48.rom`
with the expected SHA-256. Run again — task reports `UP-TO-DATE`. Delete
file, run again — re-downloads. Documented in the spec but not automated
as a Gradle integration test.

**Total: ~16 assertions across 5 new test files. About 1 hour of TDD.**

## Validation gates (WU M2.1-F)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. `downloadRom` task succeeds; SHA-256 matches; `48.rom` is 16384 bytes.
3. All new tests pass (~16 new assertions).
4. Existing 53+ tests still pass — zero migration on M1 call sites.
5. ZEXDOC harness still runs (regression: 0 IllegalStateException, 0
   ERROR; current state preserved).
6. FUSE harness still 1354/1356 (regression: WritePolicy default
   OpenPolicy means FuseSuite's `Memory()` is unaffected).
7. Programs suite still 5/5.
8. Spec smoke: `Spectrum48k().reset(); run(10_000)` completes without
   exception and `tStates >= 10_000`.

Tag: `m2-phase01-1`.

## Work-unit breakdown

| WU | Description |
|---|---|
| M2.1-A | WritePolicy interface + OpenPolicy + ReadOnlyBelow + WritePolicyTest. Memory gains `writePolicy: WritePolicy = OpenPolicy` ctor arg + guarded `write()` + `loadAt` bypass + MemoryWriteGuardTest. |
| M2.1-B | Gradle `downloadRom` task: hand-rolled DSL using `URL.openStream()` + SHA-256 verify + cached output to `build/generated-resources/rom/48.rom`. Wire to `processResources`. Manual smoke. |
| M2.1-C | RomLoader (classpath resource → 16KB ByteArray) + RomLoaderTest. |
| M2.1-D | Cpu.reset() method + CpuResetTest. |
| M2.1-E | Spectrum48k class (Cpu + Memory(ReadOnlyBelow(0x4000)) + Dispatcher + reset() + step() + run(cycles); cpu.io stays default NoIoBus) + Spectrum48kTest. |
| M2.1-F | Sweep: full check, regression all suites, tag `m2-phase01-1`. |

Within-phase deps: A, B, D are independent. C depends on B (needs ROM
file present). E depends on A, C, D. F depends on all.

## Risks

- **GitHub mirror URL drift.** `redcode/ZX-Spectrum-48K-ROMs` has been
  stable for years but isn't an official archive. Mitigation: SHA-256
  verify; if it 404s, error message tells the user how to swap the URL
  or supply the file manually.
- **Network in CI.** The `downloadRom` task runs once per fresh checkout.
  CI without network would fail. Mitigation: a manually-supplied
  48.rom dropped into `build/generated-resources/rom/` makes the task
  up-to-date and skip the network. Not blocking for local dev.
- **Cpu.reset() doesn't exist yet.** Adding it touches the Cpu class.
  Risk: a typo could affect M1 dispatch state. Mitigation: TDD
  CpuResetTest first; existing M1 tests catch regressions.
- **Dispatching real ROM may surface latent bugs.** The smoke
  `run(10_000)` may hit an opcode path we've never exercised (the 2
  known FUSE residuals or some edge case). If it crashes, M2.1 doesn't
  ship until either fixed or the bug filed and the smoke runs N cycles
  short of the crash. Acceptable contingency.
