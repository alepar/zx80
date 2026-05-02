package ru.alepar.zx80.retrieve;

import org.junit.Test;
import ru.alepar.zx80.cpu.WordRegister;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * User: alepar
 * Date: Oct 3, 2010
 */
public class RegistryWordRetrieverTest {

    @Test
    public void testIxMnemonic() throws Exception {
        Mnemonic r = new RegistryWordRetriever(WordRegister.IX);
        assertThat(r.mnemonic(), equalTo("IX"));
    }

    @Test
    public void testSpMnemonic() throws Exception {
        Mnemonic r = new RegistryWordRetriever(WordRegister.SP);
        assertThat(r.mnemonic(), equalTo("SP"));
    }

}
