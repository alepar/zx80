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
    private final byte offset;

    IndirectWordMemoryRetriever(WordRetriever word) {
        this.word = word;
        this.offset = 0;
    }

    IndirectWordMemoryRetriever(WordRetriever word, byte offset) {
        this.word = word;
        this.offset = offset;
    }

    @Override
    public Cell getFrom(Speccy speccy) {
        return speccy.getMemory().getCell(
                address(word.getFrom(speccy).getValue() + offset)
        );
    }

    @Override
    public String mnemonic() {
        return "(" + word.mnemonic() + ")";
    }
}
