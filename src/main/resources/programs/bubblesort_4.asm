; bubblesort_4: sort 4 bytes at 0x100 ascending. Bubble sort.
; B = outer pass count (3 = N-1).
; C = inner count per pass (3 = N-1).
; HL points at the "left" element per inner step; INC HL moves to "right".

        ORG  0x0000
        LD B, 3              ; outer = N-1
OUTER:  LD HL, 0x100         ; reset pointer each pass
        LD C, 3              ; inner = N-1
INNER:  LD A, (HL)           ; A = left
        INC HL               ; advance to right
        CP (HL)              ; A - (HL)
        JR C, NOSWAP         ; A < (HL): in order
        JR Z, NOSWAP         ; A == (HL): in order
        ; A > (HL): swap
        LD D, (HL)
        LD (HL), A
        DEC HL
        LD (HL), D
        INC HL
NOSWAP: DEC C
        JR NZ, INNER
        DJNZ OUTER
        HALT
