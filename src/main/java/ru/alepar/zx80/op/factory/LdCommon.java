package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.retrieve.CellRetriever;

import static ru.alepar.zx80.cpu.Register.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;
import static ru.alepar.zx80.util.Mask.*;

/**
 * User: alepar
 * Date: Oct 2, 2010
 */
public class LdCommon {

    public static CellRetriever getSrcCell(byte srcVal) {
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

    public static CellRetriever getDstCell(byte val) {
        switch (val) {
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

    public static byte getHeader(Cell op) {
        return mask(0xc0).applyTo(op.getValue());
    }

    public static byte getDstVal(Cell op) {
        return mask((byte) 0x38).applyTo(op.getValue());
    }

    public static byte getSrcVal(Cell op) {
        return mask((byte) 0x07).applyTo(op.getValue());
    }
}
