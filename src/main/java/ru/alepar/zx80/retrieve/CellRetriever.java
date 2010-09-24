package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public interface CellRetriever extends Mnemonic {

    Cell getFrom(Speccy speccy);

}
