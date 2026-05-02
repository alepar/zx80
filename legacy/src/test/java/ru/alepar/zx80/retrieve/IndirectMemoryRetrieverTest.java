package ru.alepar.zx80.retrieve;

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static ru.alepar.zx80.cpu.Register.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class IndirectMemoryRetrieverTest {
    @Test
    public void testMnemonic() throws Exception {
        assertThat(imem(reg(A), reg(B)).mnemonic(), equalTo("(AB)"));
        assertThat(imem(reg(H), reg(L)).mnemonic(), equalTo("(HL)"));
    }
}
