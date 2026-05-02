package ru.alepar.zx80.config;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class StaticConfig implements Config {

    public static final int MEMORY_SIZE = 16 * 1024;

    @Override
    public int getMemorySize() {
        return MEMORY_SIZE;
    }

}
