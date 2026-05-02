# Legacy Java Sources (2010)

The original Java implementation of this project. Kept here for human reference only — not built, not on the classpath, not maintained.

The new Kotlin implementation lives in `src/main/kotlin/` (sibling of this directory's parent). See `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` for the design that supersedes this code.

Patterns from this code worth understanding before reading the new implementation:
- `op/factory/*.java` — one factory per opcode pattern, each implementing `accept(byte[])` and `build(byte[])`. The new design preserves the per-opcode-pattern decomposition but uses a 256-entry dispatch table instead of a linear factory chain.
- `retrieve/*.java` — `CellRetriever` and friends model the source/destination of a value (register, memory, immediate). The new design folds this into named CPU fields.
- `Mnemonic` interface — every Op and every retriever can stringify itself for disassembly. The new design preserves this idea on `Op`.
