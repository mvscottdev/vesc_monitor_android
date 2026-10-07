package dev.vescmonitor.core.protocol

/**
 * CRC-16/XMODEM (poly 0x1021, init 0, no reflection, no xorout) over the payload only.
 * @source vedderb/bldc@4fd8279 util/crc.c:26-64 (GPL-3.0)
 */
object Crc16 {
    private val table =
        IntArray(256) { i ->
            var crc = i shl 8
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc and 0xFFFF
        }

    fun compute(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset,
    ): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val index = ((crc ushr 8) xor data[i].toInt()) and 0xFF
            crc = ((crc shl 8) xor table[index]) and 0xFFFF
        }
        return crc
    }
}
