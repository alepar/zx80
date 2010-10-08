package ru.alepar.zx80.op.factory;

import org.junit.Test;
import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.SpeccyFactory;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.Register;
import ru.alepar.zx80.cpu.WordRegister;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static ru.alepar.zx80.base.Address.address;
import static ru.alepar.zx80.cpu.Register.*;

/**
 * User: alepar
 * Date: Oct 8, 2010
 */
public class LdRegDstImmediateTest {

    private final Speccy speccy = SpeccyFactory.buildSpeccy();
    private final OpFactory opFactory = new LdRegDstImmediate();

    @Test
    public void ldImmediateToLExecutedProperly() {
        Cell[] opcode = new Cell[]{new Cell(), new Cell(), new Cell()};
        opcode[0].setValue((byte) 0xdd); // n -> L
        opcode[1].setValue((byte) 0x2e);
        opcode[2].setValue((byte) 0xba);

        assertThat(opFactory.accept(opcode), equalTo(3));

        opFactory.build(opcode).execute(speccy);

        assertThat(speccy.getRegistryBlock().getCell(L).getValue(), equalTo((byte) 0xba));
    }

}
