package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.Register;
import ru.alepar.zx80.op.Ld;
import ru.alepar.zx80.op.Op;

import static ru.alepar.zx80.cpu.Register.*;
import static ru.alepar.zx80.retrieve.Retrievers.reg;

/**
 * User: alepar
 * Date: Oct 8, 2010
 */
public class LdImpliedDst extends SpeccyOpFactory {
    @Override
    public int accept(Cell[] opcode) {
        if (opcode[0].getValue() == (byte) 0xed){
            if(opcode[1].getValue() == (byte) 0x47 ||
                 opcode[1].getValue() == (byte) 0x4f) {
                return 2;
            }
        }
        return 0;
    }

    @Override
    public Op build(Cell[] opcode) {
        Register arg;
        if(opcode[1].getValue() == (byte) 0x47) {
            arg = I;
        } else {
            arg = R;
        }
        return new Ld(
                reg(A),
                reg(arg)
        );
    }
}
