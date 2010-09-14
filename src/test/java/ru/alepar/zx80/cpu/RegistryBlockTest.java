package ru.alepar.zx80.cpu;

import org.junit.Test;

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
        for (int i = 0; i < RegisterType.values().length; i++) {
            RegisterType type = RegisterType.values()[i];
            Register register = registryBlock.getRegister(type);

            assertThat(register, notNullValue());
            assertThat(register.getCell(), notNullValue());
            assertThat(register.getType(), equalTo(type));
        }
    }

    @Test
    public void allEnumeratedWordRegistriesAreAccessible() {
        for (int i = 0; i < WordRegisterType.values().length; i++) {
            WordRegisterType type = WordRegisterType.values()[i];
            WordRegister register = registryBlock.getWordRegister(type);

            assertThat(register, notNullValue());
            assertThat(register.getWord(), notNullValue());
            assertThat(register.getType(), equalTo(type));
        }
    }

}
