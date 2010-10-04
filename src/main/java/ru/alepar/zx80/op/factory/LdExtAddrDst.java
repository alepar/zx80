package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.base.Word;
import ru.alepar.zx80.cpu.Register;
import ru.alepar.zx80.op.Ld;

import static ru.alepar.zx80.base.Address.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Oct 05, 2010
 * 
 *  A -> (nn)
 */
public class LdExtAddrDst extends SpeccyOpFactory {

    @Override
    public int accept(Cell[] opcode) {
        if (opcode.length < 3) {
            return 0;
        }
        if (opcode[0].getValue() == (byte)0x32) {
            return 3;
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        return new Ld(
                reg(Register.A),
                mem(address(new Word(opcode[1], opcode[2])))
            );
    }

}
