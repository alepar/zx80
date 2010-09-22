package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.base.Address;
import ru.alepar.zx80.cpu.Register;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class Retrievers {

    public static CellRetriever mem(Address address) {
        return new MemoryRetriever(address);
    }

    public static CellRetriever imem(CellRetriever high, CellRetriever low) {
        return new IndirectMemoryRetriever(high, low);
    }

    public static CellRetriever reg(Register r) {
        return new RegistryRetriever(r);
    }

}