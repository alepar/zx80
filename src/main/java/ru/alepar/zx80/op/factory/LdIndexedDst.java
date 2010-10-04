package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.WordRegister;
import ru.alepar.zx80.op.Ld;

import static ru.alepar.zx80.op.factory.LdCommon.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 15, 2010
 * 
 * A,B,C... -> (IX + d)
 */
public class LdIndexedDst extends SpeccyOpFactory {

    @Override
    public int accept(Cell[] opcode) {
        if (opcode.length < 3) {
            return 0;
        }
        if (opcode[0].getValue() == (byte)0xdd || opcode[0].getValue() == (byte)0xfd) {
            if (getHeader(opcode[1]) == (byte) 0x01) {
                if (getDstVal(opcode[1]) == (byte) 0x06) {
                    return 3;
                }
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
                getSrcCell(getSrcVal(opcode[1])),
                imem(reg(r), opcode[2].getValue())
            );
    }

}
