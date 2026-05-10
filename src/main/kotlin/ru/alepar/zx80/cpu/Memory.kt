package ru.alepar.zx80.cpu

/**
 * 64K linear address space. Reads return unsigned 0..255 as Int. Writes mask to 8 bits. Address
 * arithmetic wraps mod 65536.
 *
 * Callers SHOULD pass raw arithmetic results (e.g. `sp - 1`) without pre-masking; the address is
 * wrapped internally. This is required for Z80 semantics like `PUSH` from `SP=0` decrementing to
 * `0xFFFF`.
 *
 * The [writePolicy] gates `write` (and indirectly `writeWord`). [loadAt] bypasses the policy —
 * it is the install path for ROM and other privileged loads.
 */
class Memory(private val writePolicy: WritePolicy = OpenPolicy) {
    private val bytes = ByteArray(SIZE)

    fun read(addr: Int): Int = bytes[addr and ADDR_MASK].toInt() and 0xFF

    fun write(addr: Int, value: Int) {
        if (!writePolicy.shouldWrite(addr and ADDR_MASK)) return
        bytes[addr and ADDR_MASK] = (value and 0xFF).toByte()
    }

    /**
     * Read a 16-bit little-endian word at `addr`. Low byte at `addr`, high byte at `addr + 1` (mod
     * 64K). Returns Int in 0..0xFFFF.
     */
    fun readWord(addr: Int): Int = read(addr) or (read(addr + 1) shl 8)

    /**
     * Write a 16-bit value as little-endian. Low byte at `addr`, high byte at `addr + 1` (mod 64K).
     * Value masked to 16 bits. Both bytes are subject to [writePolicy].
     */
    fun writeWord(addr: Int, value: Int) {
        write(addr, value and 0xFF)
        write(addr + 1, (value ushr 8) and 0xFF)
    }

    /** Bypasses [writePolicy]. Use this to install ROM bytes at boot or set up test fixtures. */
    fun loadAt(addr: Int, payload: ByteArray) {
        require(addr in 0..ADDR_MASK) { "load address out of range: 0x${addr.toString(16)}" }
        require(payload.size <= SIZE - addr) {
            "payload of ${payload.size} bytes at 0x${addr.toString(16)} overflows 64K address space"
        }
        payload.copyInto(bytes, addr)
    }

    companion object {
        const val SIZE = 0x10000
        const val ADDR_MASK = 0xFFFF
    }
}
