package ru.alepar.zx80.retrieve;

import org.junit.Test;
import ru.alepar.zx80.cpu.WordRegister;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * User: alepar
 * Date: Oct 3, 2010
 */
public class IndirectWordMemoryRetrieverTest {

    @Test
    public void testMnemonicWithNonZeroOffset() throws Exception {
        Mnemonic r = new IndirectWordMemoryRetriever(new RegistryWordRetriever(WordRegister.IX), (byte) 5);
        assertThat(r.mnemonic(), equalTo("(IX + 5)"));
    }

    @Test
    public void testMnemonicWithZeroOffset() throws Exception {
        Mnemonic r = new IndirectWordMemoryRetriever(new RegistryWordRetriever(WordRegister.IY));
        assertThat(r.mnemonic(), equalTo("(IY)"));
    }

}
