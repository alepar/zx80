package ru.alepar.zx80.cpu;

import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.base.Word;

import java.util.HashMap;
import java.util.Map;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class RegistryBlock {

    private Map<Register, Cell> registers;
    private Map<WordRegister, Word> wordRegisters;

    public RegistryBlock() {
        initRegisters();
        initWordRegisters();
    }

    private void initRegisters() {
        registers = new HashMap<Register, Cell>();
        for (int i = 0; i < Register.values().length; i++) {
            Register type = Register.values()[i];
            registers.put(type, new Cell());
        }
    }

    private void initWordRegisters() {
        wordRegisters = new HashMap<WordRegister, Word>();
        for (int i = 0; i < WordRegister.values().length; i++) {
            WordRegister type = WordRegister.values()[i];
            wordRegisters.put(type, new Word());
        }
    }

    public Cell getCell(Register type) {
        return registers.get(type);
    }

    public Word getWord(WordRegister type) {
        return wordRegisters.get(type);
    }

    public Word getWord(Register high, Register low) {
        return new Word(getCell(high), getCell(low));
    }
}
