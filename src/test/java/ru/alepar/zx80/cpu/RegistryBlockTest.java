package ru.alepar.zx80.cpu;

import org.junit.Test;
import ru.alepar.zx80.base.Cell;
import ru.alepar.zx80.base.Word;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class RegistryBlockTest {

    private RegistryBlock registryBlock = new RegistryBlock();
    
    @Test
    public void allEnumeratedRegistriesAreAccessible() {
        for (int i = 0; i < Register.values().length; i++) {
            Register type = Register.values()[i];
            Cell register = registryBlock.getCell(type);

            assertThat(register, notNullValue());
        }
    }

    @Test
    public void allEnumeratedWordRegistriesAreAccessible() {
        for (int i = 0; i < WordRegister.values().length; i++) {
            WordRegister type = WordRegister.values()[i];
            Word register = registryBlock.getWordRegister(type);

            assertThat(register, notNullValue());
        }
    }

}
