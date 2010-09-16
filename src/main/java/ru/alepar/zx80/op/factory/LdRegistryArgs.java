package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.base.Word;
import ru.alepar.zx80.cpu.Register;
import ru.alepar.zx80.op.Ld;
import ru.alepar.zx80.op.Op;

import static ru.alepar.zx80.base.Address.*;
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
        if (mask(opcode[0].getValue()).applyTo((byte) 0xc0) == 0x01) {
            return 1;
        }
        return 0;
    }

    @Override
    public Op build(Cell[] opcode) {
        byte op = opcode[0].getValue();
        byte srcMask = 0x07;
        byte dstMask = 0x38;

        byte srcVal = mask(srcMask).applyTo(op);
        byte dstVal = mask(dstMask).applyTo(op);

        return new Ld(
                getSrcCell(srcVal),
                getDstCell(dstVal)
        );
    }

    private Cell getSrcCell(byte srcVal) {
        switch (srcVal) {
            case 0:
                return speccy.getRegistryBlock().getCell(Register.B);
            case 1:
                return speccy.getRegistryBlock().getCell(Register.C);
            case 2:
                return speccy.getRegistryBlock().getCell(Register.D);
            case 3:
                return speccy.getRegistryBlock().getCell(Register.E);
            case 4:
                return speccy.getRegistryBlock().getCell(Register.F);
            case 5:
                return speccy.getRegistryBlock().getCell(Register.L);
            case 6:
                Word word = speccy.getRegistryBlock().getWord(Register.H, Register.L);
                return speccy.getMemory().getCell(address(word));
            case 7:
                return speccy.getRegistryBlock().getCell(Register.A);
            default:
                throw new RuntimeException("all cases are covered, should not happen");
        }
    }

    private Cell getDstCell(byte srcVal) {
        switch (srcVal) {
            case 0:
                return speccy.getRegistryBlock().getCell(Register.B);
            case 1:
                return speccy.getRegistryBlock().getCell(Register.C);
            case 2:
                return speccy.getRegistryBlock().getCell(Register.D);
            case 3:
                return speccy.getRegistryBlock().getCell(Register.E);
            case 4:
                return speccy.getRegistryBlock().getCell(Register.H);
            case 5:
                return speccy.getRegistryBlock().getCell(Register.L);
            case 6:
                Word word = speccy.getRegistryBlock().getWord(Register.H, Register.L);
                return speccy.getMemory().getCell(address(word));
            case 7:
                return speccy.getRegistryBlock().getCell(Register.A);
            default:
                throw new RuntimeException("all cases are covered, should not happen");
        }
    }

}
