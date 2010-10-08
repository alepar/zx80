package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.op.Ld;

import static ru.alepar.zx80.op.factory.LdCommon.*;
import static ru.alepar.zx80.retrieve.Retrievers.immed;

/**
 * User: alepar
 * Date: Sep 15, 2010
 * <p/>
 * n -> A,B,C, ... ,(HL)
 */
public class LdRegDstImmediate extends SpeccyOpFactory {

    @Override
    public int accept(Cell[] opcode) {
        if (opcode.length < 3) {
            return 0;
        }
        if(opcode[0].getValue() == (byte) 0xdd) {
            if (getHeader(opcode[1]) == 0x00) {
                if (getSrcVal(opcode[1]) == 6) {
                    return 3;
                }
            }
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        return new Ld(
                immed(opcode[2].getValue()),
                getDstCell(getDstVal(opcode[1]))
        );
    }

}
