package ru.alepar.zx80.retrieve;

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static ru.alepar.zx80.base.Address.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class MemoryRetrieverTest {
    @Test
    public void testMnemonic() throws Exception {
        assertThat(mem(address(0xcafe)).mnemonic(), equalTo("(0xCAFE)"));
    }
}
