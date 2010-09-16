package ru.alepar.zx80.base;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class Cell {

    private byte value;

    public byte getValue() {
        return value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public void copyFrom(Cell src) {
        this.value = src.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Cell cell = (Cell) o;

        return value == cell.value;
    }

    @Override
    public int hashCode() {
        return (int) value;
    }
}
