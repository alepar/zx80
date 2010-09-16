package ru.alepar.zx80.base;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class Address {

    private int value;

    public static Address address(int value) {
        return new Address(value);
    }

    public static Address address(Word value) {
        return new Address(value.getValue());
    }

    private Address(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
