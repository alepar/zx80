package ru.alepar.zx80.exception;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class AddressOutOfRange extends RuntimeException {

    public AddressOutOfRange() {
    }

    public AddressOutOfRange(String message) {
        super(message);
    }

    public AddressOutOfRange(String message, Throwable cause) {
        super(message, cause);
    }

    public AddressOutOfRange(Throwable cause) {
        super(cause);
    }
}
