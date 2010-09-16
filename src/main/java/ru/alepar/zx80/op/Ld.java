package ru.alepar.zx80.op;

import ru.alepar.zx80.base.Cell;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public class Ld implements Op {

    private final Cell src, dst;

    public Ld(Cell src, Cell dst) {
        this.src = src;
        this.dst = dst;
    }

    @Override
    public void execute() {
        dst.copyFrom(src);
    }
}
