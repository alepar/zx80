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
 * <p/>
 * (nn) -> A
 */
public class LdExtAddrSrc extends SpeccyOpFactory {

    @Override
    public int accept(Cell[] opcode) {
        if (opcode.length < 4) {
            return 0;
        }
        if (opcode[0].getValue() == (byte) 0xfd) {
            if (opcode[1].getValue() == (byte) 0x3a) {
                return 4;
            }
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        return new Ld(
                mem(address(new Word(opcode[2], opcode[3]))),
                reg(Register.A)
                );
    }

}
