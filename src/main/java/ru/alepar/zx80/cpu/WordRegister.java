package ru.alepar.zx80.cpu;

import ru.alepar.zx80.base.Word;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class WordRegister {

    private Word word;
    private WordRegisterType type;

    public WordRegister(WordRegisterType type) {
        this.type = type;
        this.word = new Word();
    }

    public Word getWord() {
        return word;
    }

    public WordRegisterType getType() {
        return type;
    }
}
