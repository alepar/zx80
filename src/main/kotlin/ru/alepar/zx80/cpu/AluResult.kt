package ru.alepar.zx80.cpu

/**
 * The (result, new F register) pair returned by `Flags.afterXxx` helpers and by `AluOp.apply`.
 * `value` is masked to 8 bits; `newF` is the full computed F-register byte.
 */
data class AluResult(val value: Int, val newF: Int)
