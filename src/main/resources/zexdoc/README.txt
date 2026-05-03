ZEXDOC.COM — Z80 documented-instruction exerciser.

Author: Frank D. Cringle (1994), with later refinements by Ian Bartholomew.
License: Released into the public domain by the author.
Source: https://github.com/anotherlin/z80emu/tree/master/testfiles
        (canonical: http://mdfs.net/Software/Z80/Exerciser/)

Size: 8704 bytes (this vendored copy).

ZEXDOC tests every documented Z80 instruction with a wide range of operand
combinations and computes a CRC of the resulting CPU state. Each instruction
group prints "OK" if its CRC matches the reference, or "ERROR" otherwise.

Used here by `zx80 zexdoc` as the M1 milestone gate. See
docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md.
