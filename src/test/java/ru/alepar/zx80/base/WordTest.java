package ru.alepar.zx80.base;

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * User: alepar
 * Date: Sep 17, 2010
 */
public class WordTest {

    @Test
    public void wordValueGivesCorrectInt() {
        Word word = new Word();
        word.getHigh().setValue((byte) 0xf0);
        word.getLow().setValue((byte) 0xf0);

        assertThat(word.getValue(), equalTo(0xf0f0));
    }
}
