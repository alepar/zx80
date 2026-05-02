package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.cpu.Register;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class RegistryRetriever implements CellRetriever {

    private final Register register;

    RegistryRetriever(Register register) {
        this.register = register;
    }

    @Override
    public Cell getFrom(Speccy speccy) {
        return speccy.getRegistryBlock().getCell(register);
    }

    @Override
    public String mnemonic() {
        return register.name();
    }
}
