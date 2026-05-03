; crc8: compute CRC-8 over 4 input bytes at 0x100. Polynomial 0x07, init 0x00,
; no reflection, no XOR-out. Result stored at 0x200.
; Algorithm:
;   crc = 0
;   for each byte b:
;     crc ^= b
;     for 8 bits:
;       msb = crc bit 7
;       crc <<= 1 (8-bit)
;       if msb: crc ^= 0x07

        ORG  0x0000
        LD HL, 0x100         ; input pointer
        LD B, 4              ; byte count
        LD A, 0              ; CRC accumulator
NEXTB:  XOR (HL)
        LD C, 8              ; bit counter
BIT:    SLA A                ; shift left, MSB into carry
        JR NC, NOXOR
        XOR 0x07
NOXOR:  DEC C
        JR NZ, BIT
        INC HL
        DJNZ NEXTB
        LD (0x200), A
        HALT
