package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.WordRegister;
import ru.alepar.zx80.op.Ld;

import static ru.alepar.zx80.op.factory.LdCommon.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 15, 2010
 * 
 * (IX + d) -> A,B,C...
 */
public class LdIndexedSrc extends SpeccyOpFactory {

    public LdIndexedSrc(Speccy speccy) {
        super(speccy);
    }

    @Override
    public int accept(Cell[] opcode) {
        if (opcode.length < 3) {
            return 0;
        }
        if (opcode[0].getValue() == 0xdd || opcode[0].getValue() == 0xfd) {
            if (getHeader(opcode[1]) == 0x01) {
                if (getSrcVal(opcode[1]) == 6) {
                    return 3;
                }
            }
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        WordRegister r;
        if (opcode[0].getValue() == 0xdd) {
            r = WordRegister.IX;
        } else {
            r = WordRegister.IY;
        }
        return new Ld(
                imem(reg(r), opcode[3].getValue()),
                getDstCell(opcode[1].getValue()));
    }

}
