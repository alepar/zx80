package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.op.Ld;

import static ru.alepar.zx80.op.factory.LdCommon.*;

/**
 * User: alepar
 * Date: Sep 15, 2010
 * <p/>
 * A,B,C,... -> A,B,C,...
 */
public class LdRegistryArgs extends SpeccyOpFactory {

    public LdRegistryArgs(Speccy speccy) {
        super(speccy);
    }

    @Override
    public int accept(Cell[] opcode) {
        if (getHeader(opcode[0]) == 0x01) {
            return 1;
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        Cell op = opcode[0];

        return new Ld(
                getSrcCell(getSrcVal(op)),
                getDstCell(getDstVal(op))
        );
    }

}
