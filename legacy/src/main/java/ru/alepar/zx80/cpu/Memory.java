package ru.alepar.zx80.cpu;

import ru.alepar.zx80.base.Address;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.exception.AddressOutOfRange;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class Memory {

    private Cell[] cells;

    public Memory(int memorySize) {
        cells = new Cell[memorySize];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = new Cell();
        }
    }

    public Cell getCell(Address src) {
        try {
            return cells[src.getValue()];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new AddressOutOfRange("memory size = " + cells.length + "; address = " + src, e);
        }
    }

}
