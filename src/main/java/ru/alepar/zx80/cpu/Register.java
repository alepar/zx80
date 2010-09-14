package ru.alepar.zx80.cpu;

import ru.alepar.zx80.base.Cell;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class Register {

    private Cell cell;
    private RegisterType type;

    public Register(RegisterType type) {
        this.type = type;
        this.cell = new Cell();
    }

    public Cell getCell() {
        return cell;
    }

    public RegisterType getType() {
        return type;
    }
}
