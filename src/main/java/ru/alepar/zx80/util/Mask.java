package ru.alepar.zx80.util;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public class Mask {

    private final int mask;
    private final int shift;

    public static Mask mask(int mask) {
        return new Mask(mask);
    }

    public static Mask mask(byte mask) {
        return new Mask(((int) mask) & 0xff);
    }

    private Mask(int mask) {
        this.mask = mask;
        int temp = mask, count = 0;
        if (mask != 0) {
            while ((temp & 1) == 0) {
                temp = temp >>> 1;
                count++;
            }
        }
        shift = count;
    }

    public byte applyTo(byte src) {
        return (byte) applyTo(((int) src) & 0xff);
    }

    public int applyTo(int src) {
        return (src & mask) >>> shift;
    }
}
