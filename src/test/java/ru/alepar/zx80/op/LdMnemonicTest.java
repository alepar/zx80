package ru.alepar.zx80.op;

import org.hamcrest.Matchers;
import org.junit.Test;

import static org.junit.Assert.*;
import static ru.alepar.zx80.cpu.Register.*;
import static ru.alepar.zx80.retrieve.Retrievers.*;

/**
 * User: alepar
 * Date: Sep 24, 2010
 */
public class LdMnemonicTest {

    @Test
    public void testMnemonicWithRegArg() {
        Op ld = new Ld(reg(A), reg(B));
        assertThat(ld.mnemonic(), Matchers.equalTo("LD\tB, A"));
    }

    @Test
    public void testMnemonicWithIndirectArg() throws Exception {
        Op ld = new Ld(reg(A), imem(reg(B), reg(C)));
        assertThat(ld.mnemonic(), Matchers.equalTo("LD\t(BC), A"));
    }
}
