; fib10: iterative Fibonacci. Computes F(11) = 89 and stores it at 0x100.
; Strategy: HL=a, DE=b. Loop: ADD HL,DE; EX DE,HL. After 10 iterations
; the latest sum is in DE; one more EX brings it into HL for storing.

        ORG  0x0000
        LD HL, 0          ; a = 0
        LD DE, 1          ; b = 1
        LD B, 10          ; counter
LOOP:   ADD HL, DE        ; HL = a + b
        EX DE, HL         ; HL <-> DE
        DJNZ LOOP
        EX DE, HL         ; bring final sum back into HL
        LD (0x100), HL    ; store
        HALT
