; nop_loop: a single HALT at 0x0000.
; Expected: after 1 step, pc=0x0001 and halted=true.
; Also serves as a smoke test for the harness wiring.

        ORG  0x0000
        HALT
