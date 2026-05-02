# FUSE Z80 test suite

`tests.in` and `tests.expected` are vendored from the FUSE emulator
project (see `LICENSE.fuse`). Format documentation lives in the FUSE
source tree under `z80/tests/`.

Each test is one Z80 instruction with a fully-specified initial CPU
state and the exact expected state (registers, memory pokes, and
T-state count) after executing exactly one instruction.

We use these as the primary granular gradient for the scoring harness
(`zx80 score --suite=fuse`).
