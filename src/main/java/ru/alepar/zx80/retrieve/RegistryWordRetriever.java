package ru.alepar.zx80.retrieve;

import ru.alepar.zx80.Speccy;
import ru.alepar.zx80.base.Word;
import ru.alepar.zx80.cpu.WordRegister;

/**
 * User: alepar
 * Date: Oct 02, 2010
 */
public class RegistryWordRetriever implements WordRetriever {

    private final WordRegister register;

    RegistryWordRetriever(WordRegister register) {
        this.register = register;
    }

    @Override
    public Word getFrom(Speccy speccy) {
        return speccy.getRegistryBlock().getWord(register);
    }

    @Override
    public String mnemonic() {
        return register.name();
    }
}
