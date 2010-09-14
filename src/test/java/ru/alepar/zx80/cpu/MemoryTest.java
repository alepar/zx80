package ru.alepar.zx80.cpu;

import org.junit.Test;
import ru.alepar.zx80.base.Address;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.exception.AddressOutOfRange;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class MemoryTest {

    public static final int MEMORY_SIZE = 256;

    private Memory memory = new Memory(MEMORY_SIZE);

    @Test
    public void allCellsAreAllocatedNotNullAndZeroed() {
        for (int i = 0; i < MEMORY_SIZE; i++) {
            Cell cell = memory.getCell(new Address(i));
            assertThat(cell, notNullValue());
            assertThat(cell.getValue(), equalTo((byte)0));
        }
    }

    @Test(expected = AddressOutOfRange.class)
    public void getOOVerMemorySizeThrowsOutOfRange() {
        memory.getCell(new Address(MEMORY_SIZE));
    }
}
