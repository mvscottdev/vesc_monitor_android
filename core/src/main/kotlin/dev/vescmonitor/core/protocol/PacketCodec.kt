package dev.vescmonitor.core.protocol

/**
 * VESC packet framing.
 * short: 0x02 | len u8 | payload | crc16 BE | 0x03 (payload 1..255)
 * long:  0x03 | len u16 BE | payload | crc16 BE | 0x03 (payload 256..512)
 * @source vedderb/bldc@4fd8279 comm/packet.c:42-69 (GPL-3.0)
 */
object PacketCodec {
    const val START_SHORT: Byte = 0x02
    const val START_LONG: Byte = 0x03
    const val END: Byte = 0x03

    /** Firmware PACKET_MAX_PL_LEN. */
    const val MAX_PAYLOAD = 512
    const val SHORT_MAX_PAYLOAD = 255

    fun encode(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "empty payload" }
        require(payload.size <= MAX_PAYLOAD) { "payload ${payload.size} > $MAX_PAYLOAD" }
        val short = payload.size <= SHORT_MAX_PAYLOAD
        val header = if (short) 2 else 3
        val out = ByteArray(header + payload.size + 3)
        if (short) {
            out[0] = START_SHORT
            out[1] = payload.size.toByte()
        } else {
            out[0] = START_LONG
            out[1] = (payload.size ushr 8).toByte()
            out[2] = payload.size.toByte()
        }
        payload.copyInto(out, header)
        val crc = Crc16.compute(payload)
        out[header + payload.size] = (crc ushr 8).toByte()
        out[header + payload.size + 1] = crc.toByte()
        out[out.size - 1] = END
        return out
    }
}
