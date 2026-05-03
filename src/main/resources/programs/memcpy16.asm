; memcpy16: copy 16 bytes from 0x100 to 0x200 using LDIR.
; Source bytes are pre-loaded via initial_memory in the fixture .json.

        ORG  0x0000
        LD HL, 0x100      ; source
        LD DE, 0x200      ; dest
        LD BC, 16         ; count
        LDIR
        HALT
