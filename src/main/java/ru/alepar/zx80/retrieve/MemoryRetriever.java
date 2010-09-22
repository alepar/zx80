package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Address;
import ru.alepar.zx80.base.Cell;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class MemoryRetriever implements CellRetriever {

    private final Address address;

    MemoryRetriever(Address address) {
        this.address = address;
    }

    @Override
    public Cell getFrom(Speccy speccy) {
        return speccy.getMemory().getCell(address);
    }
}
