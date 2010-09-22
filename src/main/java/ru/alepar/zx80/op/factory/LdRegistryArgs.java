package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.op.Ld;
import ru.alepar.zx80.retrieve.CellRetriever;

import static ru.alepar.zx80.cpu.Register.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;
import static ru.alepar.zx80.util.Mask.*;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public class LdRegistryArgs extends SpeccyOpFactory {

    public LdRegistryArgs(Speccy speccy) {
        super(speccy);
    }

    @Override
    public int accept(Cell[] opcode) {
        if (mask(0xc0).applyTo(opcode[0].getValue()) == 0x01) {
            return 1;
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        byte op = opcode[0].getValue();
        byte srcMask = 0x07;
        byte dstMask = 0x38;

        return new Ld(
                getSrc(mask(srcMask).applyTo(op)),
                getDst(mask(dstMask).applyTo(op))
        );
    }

    private CellRetriever getSrc(byte srcVal) {
        switch (srcVal) {
            case 0:
                return reg(B);
            case 1:
                return reg(C);
            case 2:
                return reg(D);
            case 3:
                return reg(E);
            case 4:
                return reg(F);
            case 5:
                return reg(L);
            case 6:
                return imem(reg(H), reg(L));
            case 7:
                return reg(A);
            default:
                throw new RuntimeException("all cases are covered, should not happen");
        }
    }

    private CellRetriever getDst(byte srcVal) {
        switch (srcVal) {
            case 0:
                return reg(B);
            case 1:
                return reg(C);
            case 2:
                return reg(D);
            case 3:
                return reg(E);
            case 4:
                return reg(H);
            case 5:
                return reg(L);
            case 6:
                return imem(reg(H), reg(L));
            case 7:
                return reg(A);
            default:
                throw new RuntimeException("all cases are covered, should not happen");
        }
    }

}
