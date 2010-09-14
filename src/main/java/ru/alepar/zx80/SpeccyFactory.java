package ru.alepar.zx80;

import ru.alepar.zx80.config.Config;
import ru.alepar.zx80.config.StaticConfig;
import ru.alepar.zx80.cpu.Memory;
import ru.alepar.zx80.cpu.RegistryBlock;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class SpeccyFactory {

    public static Speccy buildSpeccy() {
        Config config = new StaticConfig();
        Memory memory = new Memory(config.getMemorySize());
        RegistryBlock registryBlock = new RegistryBlock();

        return new Speccy(memory, registryBlock); 
    }
}
