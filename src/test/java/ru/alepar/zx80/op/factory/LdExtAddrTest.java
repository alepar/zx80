package ru.alepar.zx80.op.factory;

import org.junit.Test;
import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.SpeccyFactory;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.Register;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static ru.alepar.zx80.base.Address.*;

/**
 * User: alepar
 * Date: Oct 3, 2010
 */
public class LdExtAddrTest {

    private final Speccy speccy = SpeccyFactory.buildSpeccy();
    private final OpFactory opFactory = new LdExtAddrDst();

    @Test
    public void ldIxToAExecutedProperly() {
        Cell[] opcode = new Cell[]{new Cell(),new Cell(),new Cell()};
        opcode[0].setValue((byte) 0x32); // A -> (nn)
        opcode[1].setValue((byte) 0x02);
        opcode[2].setValue((byte) 0x05);

        assertThat(opFactory.accept(opcode), equalTo(3));

        speccy.getRegistryBlock().getCell(Register.A).setValue((byte) 0xca);

        opFactory.build(opcode).execute(speccy);

        int addr = 0x0205;
        assertThat(speccy.getMemory().getCell(address(addr)).getValue(), equalTo((byte) 0xca));
    }
    
}
