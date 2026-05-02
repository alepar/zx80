package ru.alepar.zx80.cpu

/**
 * 64K linear address space. Reads return unsigned 0..255 as Int. Writes mask to 8 bits. Address
 * arithmetic wraps mod 65536.
 */
class Memory {
    private val bytes = ByteArray(SIZE)

    fun read(addr: Int): Int = bytes[addr and ADDR_MASK].toInt() and 0xFF

    fun write(addr: Int, value: Int) {
        bytes[addr and ADDR_MASK] = (value and 0xFF).toByte()
    }

    fun loadAt(addr: Int, payload: ByteArray) {
        require(addr in 0..ADDR_MASK) { "load address out of range: 0x${addr.toString(16)}" }
        require(addr + payload.size <= SIZE) {
            "payload of ${payload.size} bytes at 0x${addr.toString(16)} overflows 64K address space"
        }
        payload.copyInto(bytes, addr)
    }

    companion object {
        const val SIZE = 0x10000
        const val ADDR_MASK = 0xFFFF
    }
}
