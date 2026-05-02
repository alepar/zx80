package ru.alepar.zx80.retrieve;

import org.junit.Test;
import ru.alepar.zx80.cpu.Register;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 23, 2010
 */
public class RegistryRetrieverTest {

    @Test
    public void testMnemonic() throws Exception {
        assertThat(reg(Register.A).mnemonic(), equalTo("A"));
        assertThat(reg(Register.B).mnemonic(), equalTo("B"));
        assertThat(reg(Register.H).mnemonic(), equalTo("H"));
    }

}
