package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Cell;

import static ru.alepar.zx80.base.Address.*;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class IndirectWordMemoryRetriever implements CellRetriever {

    private final WordRetriever word;

    IndirectWordMemoryRetriever(WordRetriever word) {
        this.word = word;
    }

    @Override
    public Cell getFrom(Speccy speccy) {
        return speccy.getMemory().getCell(
                address(word.getFrom(speccy))
        );
    }

    @Override
    public String mnemonic() {
        return "(" + word.mnemonic() + ")";
    }
}
