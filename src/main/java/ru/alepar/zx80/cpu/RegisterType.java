package ru.alepar.zx80.cpu;

/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public enum RegisterType {
    A, A_ALT,   //accumulator
    F, F_ALT,   //flags

    B, B_ALT,
    C, C_ALT,

    D, D_ALT,
    E, E_ALT,

    H, H_ALT,
    L, L_ALT,

    /**
     * interrupt vector
     */
    I,

    /**
     * memory refresh
     */
    R,

}
