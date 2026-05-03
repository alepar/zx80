package ru.alepar.zx80.cpu

/**
 * Z80 I/O port bus. Ports are 16-bit (0..0xFFFF). IN A,(n) sets the high byte from cpu.a; IN r,(C)
 * uses cpu.bc as port; OUT (n),A and OUT (C),r similarly.
 *
 * In M1 we default to NoIoBus (returns 0xFF on read, ignores writes). M2 will swap in a real bus
 * connected to ULA, keyboard, beeper, etc.
 */
interface IoBus {
    fun read(port: Int): Int

    fun write(port: Int, value: Int)
}

object NoIoBus : IoBus {
    override fun read(port: Int): Int = 0xFF

    override fun write(port: Int, value: Int) {
        /* ignore */
    }
}
