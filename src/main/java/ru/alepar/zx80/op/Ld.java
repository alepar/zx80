package ru.alepar.zx80.op;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.retrieve.CellRetriever;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public class Ld implements Op {

    private final CellRetriever src, dst;

    public Ld(CellRetriever src, CellRetriever dst) {
        this.src = src;
        this.dst = dst;
    }

    @Override
    public void execute(Speccy speccy) {
        dst.getFrom(speccy).copyFrom(src.getFrom(speccy));
    }
}
