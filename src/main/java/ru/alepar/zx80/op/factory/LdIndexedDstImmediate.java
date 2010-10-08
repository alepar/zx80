package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.WordRegister;
import ru.alepar.zx80.op.Ld;

import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 15, 2010
 * <p/>
 * n -> (IX/IY + d)
 */
public class LdIndexedDstImmediate extends SpeccyOpFactory {

    @Override
    public int accept(Cell[] opcode) {
        if (opcode.length < 4) {
            return 0;
        }
        if (opcode[0].getValue() == (byte) 0xdd || opcode[0].getValue() == (byte) 0xfd) {
            if (opcode[1].getValue() == (byte) 0x36) {
                    return 4;
            }
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        WordRegister r;
        if (opcode[0].getValue() == (byte) 0xdd) {
            r = WordRegister.IX;
        } else {
            r = WordRegister.IY;
        }
        return new Ld(
                immed(opcode[3].getValue()),
                imem(reg(r), opcode[2].getValue())
        );
    }

}
