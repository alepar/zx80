package ru.alepar.zx80;

import ru.alepar.zx80.cpu.Memory;
import ru.alepar.zx80.cpu.RegistryBlock;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class Speccy {

    private final Memory memory;
    private final RegistryBlock registryBlock;

    public Speccy(Memory memory, RegistryBlock registryBlock) {
        this.memory = memory;
        this.registryBlock = registryBlock;
    }

    public Memory getMemory() {
        return memory;
    }

    public RegistryBlock getRegistryBlock() {
        return registryBlock;
    }
}
