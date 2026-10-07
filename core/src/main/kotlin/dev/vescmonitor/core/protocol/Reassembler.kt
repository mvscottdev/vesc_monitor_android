package dev.vescmonitor.core.protocol

/**
 * Turns a BLE notification byte stream into packet payloads. A packet can span
 * notifications and one notification can hold several packets. On a bad frame it
 * drops one byte and searches for the next start byte, like the firmware does.
 * @source vedderb/bldc@4fd8279 comm/packet.c:164-243 (GPL-3.0)
 */
class Reassembler(
    private val silenceResetMs: Long = SILENCE_RESET_MS,
) {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var length = 0
    private var lastRxMs = Long.MIN_VALUE

    var crcErrors = 0L
        private set
    var droppedBytes = 0L
        private set

    fun feed(
        chunk: ByteArray,
        nowMs: Long,
    ): List<ByteArray> {
        if (length > 0 && lastRxMs != Long.MIN_VALUE && nowMs - lastRxMs > silenceResetMs) {
            droppedBytes += length
            length = 0
        }
        lastRxMs = nowMs
        append(chunk)
        val out = ArrayList<ByteArray>(1)
        var start = 0
        while (start < length) {
            when (val r = tryDecode(start)) {
                is Decode.NeedMore -> {
                    break
                }

                is Decode.Bad -> {
                    if (r.crc) crcErrors++
                    droppedBytes++
                    start++
                }

                is Decode.Ok -> {
                    out += r.payload
                    start += r.consumed
                }
            }
        }
        compact(start)
        return out
    }

    fun reset() {
        length = 0
        lastRxMs = Long.MIN_VALUE
    }

    private fun append(chunk: ByteArray) {
        var src = chunk
        if (src.size > BUFFER_SIZE) {
            droppedBytes += src.size - BUFFER_SIZE
            src = src.copyOfRange(src.size - BUFFER_SIZE, src.size)
        }
        val overflow = length + src.size - BUFFER_SIZE
        if (overflow > 0) {
            droppedBytes += overflow
            compact(overflow)
        }
        src.copyInto(buffer, length)
        length += src.size
    }

    private fun compact(consumed: Int) {
        if (consumed <= 0) return
        buffer.copyInto(buffer, 0, consumed, length)
        length -= consumed
    }

    private fun byteAt(i: Int): Int = buffer[i].toInt() and 0xFF

    private fun tryDecode(at: Int): Decode {
        val avail = length - at
        val header: Int
        val payloadLen: Int
        when (buffer[at]) {
            PacketCodec.START_SHORT -> {
                if (avail < 2) return Decode.NeedMore
                header = 2
                payloadLen = byteAt(at + 1)
                if (payloadLen == 0) return Decode.Bad(crc = false)
            }

            PacketCodec.START_LONG -> {
                if (avail < 3) return Decode.NeedMore
                header = 3
                payloadLen = (byteAt(at + 1) shl 8) or byteAt(at + 2)
                // Senders use the short frame up to 255 bytes; a long frame must be longer.
                if (payloadLen <= PacketCodec.SHORT_MAX_PAYLOAD) return Decode.Bad(crc = false)
                if (payloadLen > PacketCodec.MAX_PAYLOAD) return Decode.Bad(crc = false)
            }

            // 0x04 (3-byte length) frames are never produced by firmware: treated as garbage.
            else -> {
                return Decode.Bad(crc = false)
            }
        }
        val total = header + payloadLen + 3
        if (avail < total) return Decode.NeedMore
        if (buffer[at + total - 1] != PacketCodec.END) return Decode.Bad(crc = false)
        val crcAt = at + header + payloadLen
        val expected = (byteAt(crcAt) shl 8) or byteAt(crcAt + 1)
        if (Crc16.compute(buffer, at + header, payloadLen) != expected) return Decode.Bad(crc = true)
        return Decode.Ok(buffer.copyOfRange(at + header, at + header + payloadLen), total)
    }

    private sealed interface Decode {
        data object NeedMore : Decode

        data class Bad(
            val crc: Boolean,
        ) : Decode

        class Ok(
            val payload: ByteArray,
            val consumed: Int,
        ) : Decode
    }

    companion object {
        /**
         * Drop a partial packet after this much silence. Mirrors the NRF bridge's own
         * partial-packet expiry; tunable.
         * @source vedderb/nrf51_vesc@3fb3b27 (UNVERIFIED exact value on every bridge)
         */
        const val SILENCE_RESET_MS = 100L
        private const val BUFFER_SIZE = 2048
    }
}
