package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.op.Ld;
import ru.alepar.zx80.retrieve.CellRetriever;

import static ru.alepar.zx80.cpu.Register.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public class LdIndirectReg extends SpeccyOpFactory {

    public LdIndirectReg(Speccy speccy) {
        super(speccy);
    }

    @Override
    public int accept(Cell[] opcode) {
        switch (opcode[0].getValue()) {
            case (byte) 0x0a:
            case (byte) 0x1a:
            case (byte) 0x02:
            case (byte) 0x12:
                return 1;
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        CellRetriever src;
        CellRetriever dst;
        switch (opcode[0].getValue()) {
            case (byte) 0x0a:
                src = imem(reg(B), reg(C));
                dst = reg(A);
                break;
            case (byte) 0x1a:
                src = imem(reg(D), reg(E));
                dst = reg(A);
                break;
            case (byte) 0x02:
                src = reg(A);
                dst = imem(reg(B), reg(C));
                break;
            case (byte) 0x12:
                src = reg(A);
                dst = imem(reg(D), reg(E));
                break;
            default:
                throw new RuntimeException("should not happen, cases cover all variants");
        }
        return new Ld(src, dst);
    }

}
