package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Word;

/**
 * User: alepar
 * Date: Oct 02, 2010
 */
public interface WordRetriever extends Mnemonic {

    Word getFrom(Speccy speccy);

}
