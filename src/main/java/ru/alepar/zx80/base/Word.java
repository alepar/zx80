package ru.alepar.zx80.base;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class Word {

    private Cell high, low;

    public Word() {
        high = new Cell();
        low = new Cell();
    }

    public Cell getHigh() {
        return high;
    }

    public Cell getLow() {
        return low;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Word word = (Word) o;

        if (high != null ? !high.equals(word.high) : word.high != null) return false;
        if (low != null ? !low.equals(word.low) : word.low != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = high != null ? high.hashCode() : 0;
        result = 31 * result + (low != null ? low.hashCode() : 0);
        return result;
    }
}
