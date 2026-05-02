package ru.alepar.zx80.util;

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static ru.alepar.zx80.util.Mask.*;

/**
 * User: alepar
 * Date: Sep 15, 2010
 */
public class MaskTest {

    @Test
    public void variousByteMasksAreProperlyApplied() {
        assertThat(mask((byte) 0x0f).applyTo((byte) 0xd5), equalTo((byte) 0x05));
        assertThat(mask((byte) 0xf0).applyTo((byte) 0xd5), equalTo((byte) 0x0d));

        assertThat(mask((byte) 0xff).applyTo((byte) 0x00), equalTo((byte) 0x00));
        assertThat(mask((byte) 0xff).applyTo((byte) 0x05), equalTo((byte) 0x05));

        assertThat(mask((byte) 0xff).applyTo((byte) 0x05), equalTo((byte) 0x05));
        assertThat(mask((byte) 0x01).applyTo((byte) 0xf1), equalTo((byte) 0x01));
    }

    @Test
    public void zeroByteMaskGivesZero() {
        assertThat(mask((byte) 0x00).applyTo((byte) 0xff), equalTo((byte) 0x00));
    }

    @Test
    public void variousIntMasksAreProperlyApplied() {
        assertThat(mask(0x0000000f).applyTo(0x000000d5), equalTo(0x05));

        assertThat(mask(0xf0000000).applyTo(0xd0000000), equalTo(0x0d));
        assertThat(mask(0xd0000000).applyTo(0xf0000000), equalTo(0x0d));
    }

    @Test
    public void zeroIntMaskGivesZero() {
        assertThat(mask(0x00).applyTo(0xffffffff), equalTo(0x00));
    }
}
