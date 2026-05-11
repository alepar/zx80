package ru.alepar.zx80.machine

/**
 * The 40 keys on the ZX Spectrum 48K rubber keyboard. Each entry pins its position in the 8x5
 * matrix. Digit keys use the prefix `K` because Kotlin enum identifiers can't start with a digit.
 *
 * Row 0: CAPS SHIFT, Z, X, C, V       (selected by clearing A8)
 * Row 1: A, S, D, F, G                (A9)
 * Row 2: Q, W, E, R, T                (A10)
 * Row 3: 1, 2, 3, 4, 5                (A11)
 * Row 4: 0, 9, 8, 7, 6                (A12)
 * Row 5: P, O, I, U, Y                (A13)
 * Row 6: ENTER, L, K, J, H            (A14)
 * Row 7: SPACE, SYMBOL SHIFT, M, N, B (A15)
 */
enum class SpectrumKey(val row: Int, val bit: Int) {
    CAPS_SHIFT(0, 0), Z(0, 1), X(0, 2), C(0, 3), V(0, 4),
    A(1, 0), S(1, 1), D(1, 2), F(1, 3), G(1, 4),
    Q(2, 0), W(2, 1), E(2, 2), R(2, 3), T(2, 4),
    K1(3, 0), K2(3, 1), K3(3, 2), K4(3, 3), K5(3, 4),
    K0(4, 0), K9(4, 1), K8(4, 2), K7(4, 3), K6(4, 4),
    P(5, 0), O(5, 1), I(5, 2), U(5, 3), Y(5, 4),
    ENTER(6, 0), L(6, 1), K(6, 2), J(6, 3), H(6, 4),
    SPACE(7, 0), SYMBOL_SHIFT(7, 1), M(7, 2), N(7, 3), B(7, 4),
}
