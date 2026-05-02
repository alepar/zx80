package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.op.Op;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public interface OpFactory {

    int accept(Cell[] opcode);

    Op build(Cell[] opcode);

}
