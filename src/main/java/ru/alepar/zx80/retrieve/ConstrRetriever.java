package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;

/**
 * User: alepar
 * Date: Oct 8, 2010
 */
public class ConstrRetriever implements CellRetriever {

    private final Cell value;

    ConstrRetriever(byte value) {
        this.value = new Cell();
        this.value.setValue(value);
    }

    @Override
    public Cell getFrom(Speccy speccy) {
        return value;
    }

    @Override
    public String mnemonic() {
        return "0x" + Integer.toString(value.getValue(), 16);
    }
}
