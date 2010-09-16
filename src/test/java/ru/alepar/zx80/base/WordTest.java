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

    @Test
    public void testGetAfterSetGivesSameValue() throws Exception {
        Word word = new Word();

        word.setValue(0xcafe);
        assertThat(word.getValue(), equalTo(0xcafe));

        word.setValue(0xbabe);
        assertThat(word.getValue(), equalTo(0xbabe));
    }
}
