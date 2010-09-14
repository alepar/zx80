package ru.alepar.zx80.cpu;

import java.util.HashMap;
import java.util.Map;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class RegistryBlock {

    private Map<RegisterType, Register> registers;
    private Map<WordRegisterType, WordRegister> wordRegisters;

    public RegistryBlock() {
        initRegisters();
        initWordRegisters();
    }

    private void initRegisters() {
        registers = new HashMap<RegisterType, Register>();
        for (int i = 0; i < RegisterType.values().length; i++) {
            RegisterType type = RegisterType.values()[i];
            registers.put(type, new Register(type));
        }
    }

    private void initWordRegisters() {
        wordRegisters = new HashMap<WordRegisterType, WordRegister>();
        for (int i = 0; i < WordRegisterType.values().length; i++) {
            WordRegisterType type = WordRegisterType.values()[i];
            wordRegisters.put(type, new WordRegister(type));
        }
    }

    public Register getRegister(RegisterType type) {
        return registers.get(type);
    }

    public WordRegister getWordRegister(WordRegisterType type) {
        return wordRegisters.get(type);
    }
}
