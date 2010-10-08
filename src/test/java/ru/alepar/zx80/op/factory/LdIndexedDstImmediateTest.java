package ru.alepar.zx80.op.factory;

import org.junit.Test;
import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.SpeccyFactory;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.WordRegister;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static ru.alepar.zx80.base.Address.address;

/**
 * User: alepar
 * Date: Oct 8, 2010
 */
public class LdIndexedDstImmediateTest {

    private final Speccy speccy = SpeccyFactory.buildSpeccy();
    private final OpFactory opFactory = new LdIndexedDstImmediate();

    @Test
    public void ldCToIyExecutedProperly() {
        byte offset = (byte) 0x05;

        Cell[] opcode = new Cell[]{new Cell(), new Cell(), new Cell(), new Cell()};
        opcode[0].setValue((byte) 0xdd); // n -> (IX+d)
        opcode[1].setValue((byte) 0x36);
        opcode[2].setValue(offset);
        opcode[3].setValue((byte) 0xfe);

        assertThat(opFactory.accept(opcode), equalTo(4));

        byte addr = (byte) 0x15;
        speccy.getRegistryBlock().getWord(WordRegister.IX).setValue(addr);

        opFactory.build(opcode).execute(speccy);

        assertThat(speccy.getMemory().getCell(address(addr + offset)).getValue(), equalTo((byte) 0xfe));
    }
}
