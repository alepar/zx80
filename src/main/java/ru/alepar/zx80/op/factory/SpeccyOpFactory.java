package ru.alepar.zx80.op.factory;

import ru.alepar.zx80.Speccy;

/**
 * User: alepar
 * Date: Sep 17, 2010
 */
public abstract class SpeccyOpFactory implements OpFactory {

    protected final Speccy speccy;

    protected SpeccyOpFactory(Speccy speccy) {
        this.speccy = speccy;
    }
}
