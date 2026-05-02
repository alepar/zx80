package ru.alepar.zx80.op;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.retrieve.Mnemonic;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public interface Op extends Mnemonic {

    void execute(Speccy speccy);

}
