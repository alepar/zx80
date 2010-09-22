package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.base.Word;

import static ru.alepar.zx80.base.Address.*;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class IndirectMemoryRetriever implements CellRetriever {

    private final CellRetriever high, low;

    IndirectMemoryRetriever(CellRetriever high, CellRetriever low) {
        this.high = high;
        this.low = low;
    }

    @Override
    public Cell getFrom(Speccy speccy) {
        return speccy.getMemory().getCell(
                address(
                        new Word(high.getFrom(speccy), low.getFrom(speccy))
                )
        );
    }
}
