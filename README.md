# zx80 — ZX Spectrum 48K Emulator

Kotlin rewrite of an unfinished 2010 Java emulator project. Despite the
repo name (`zx80`), the actual emulation target is the Sinclair ZX Spectrum
48K (which uses a Z80 CPU). The original ZX80 / ZX81 hardware is *not*
emulated.

## Status

In active development on the `opus-4.7` branch. M1 (headless Z80 CPU)
is the current milestone. M2 (Spectrum machine: ROM, ULA, keyboard) and
M3 (tape/snapshot loading) are future work.

See:
- `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md`
- `docs/superpowers/plans/`

## Build & run

Requires JDK 21 on PATH.

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 score
```

> *Note: as of WU1 there is no source code yet — `./gradlew help` is the only command that works until WU2+ lands.*

Expected during M1 development: a `SCORE: x.yyy` line on stdout, plus
`build/score.json` with the breakdown.

## Repository layout

- `src/main/kotlin/` — the active codebase
- `legacy/` — archived 2010 Java implementation, kept for reference
- `docs/superpowers/` — specs and implementation plans
