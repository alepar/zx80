package ru.alepar.zx80.op.factory;

import org.junit.Test;
import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.SpeccyFactory;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.Register;
import ru.alepar.zx80.cpu.WordRegister;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static ru.alepar.zx80.base.Address.*;

/**
 * User: alepar
 * Date: Oct 3, 2010
 */
public class LdIndexedSrcTest {

    private final Speccy speccy = SpeccyFactory.buildSpeccy();
    private final OpFactory opFactory = new LdIndexedSrc();

    @Test
    public void ldIxToAExecutedProperly() {
        Cell[] opcode = new Cell[]{new Cell(), new Cell(), new Cell()};
        opcode[0].setValue((byte) 0xdd); // (IX+d) -> A
        opcode[1].setValue((byte) 0x7e);
        opcode[2].setValue((byte) 0x05);

        assertThat(opFactory.accept(opcode), equalTo(3));

        int addr = 0x10;
        int offset = 0x05;
        speccy.getRegistryBlock().getWord(WordRegister.IX).setValue(addr);
        speccy.getMemory().getCell(address(addr + offset)).setValue((byte) 0xca);

        opFactory.build(opcode).execute(speccy);
        assertThat(speccy.getRegistryBlock().getCell(Register.A).getValue(), equalTo((byte) 0xca));
    }

}
