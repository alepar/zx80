package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.Register;
import ru.alepar.zx80.op.Ld;

import static ru.alepar.zx80.base.Address.*;

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
        switch(opcode[0].getValue()) {
            case (byte)0x0a:
            case (byte)0x1a:
            case (byte)0x02:
            case (byte)0x12:
                return 1;
        }
        return 0;
    }

    @Override
    public Ld build(Cell[] opcode) {
        Cell src;
        Cell dst;
        switch(opcode[0].getValue()) {
            case (byte)0x0a:
                src = speccy.getMemory().getCell(
                        address(speccy.getRegistryBlock().getWord(
                                Register.B,
                                Register.C
                        )));
                dst = speccy.getRegistryBlock().getCell(Register.A);
                break;
            case (byte)0x1a:
                src = speccy.getMemory().getCell(
                        address(speccy.getRegistryBlock().getWord(
                                Register.D,
                                Register.E
                        )));
                dst = speccy.getRegistryBlock().getCell(Register.A);
                break;
            case (byte)0x02:
                src = speccy.getRegistryBlock().getCell(Register.A);
                dst = speccy.getMemory().getCell(
                        address(speccy.getRegistryBlock().getWord(
                                Register.B,
                                Register.C
                        )));
                break;
            case (byte)0x12:
                src = speccy.getRegistryBlock().getCell(Register.A);
                dst = speccy.getMemory().getCell(
                        address(speccy.getRegistryBlock().getWord(
                                Register.D,
                                Register.E
                        )));
                break;
            default:
                throw new RuntimeException("should not happen, cases cover all variants");
        }
        return new Ld(src, dst);
    }

}
