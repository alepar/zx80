package ru.alepar.zx80.machine.tape

/** Sealed type representing a fully-parsed tape file. */
sealed class TapeFile

/** A parsed `.tap` file containing an ordered list of [TapBlock]s. */
data class TapTapeFile(val blocks: List<TapBlock>) : TapeFile()

/** A parsed `.tzx` file containing an ordered list of [TzxBlock]s. */
data class TzxTapeFile(val blocks: List<TzxBlock>) : TapeFile()
