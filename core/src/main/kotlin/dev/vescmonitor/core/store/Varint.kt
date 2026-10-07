package dev.vescmonitor.core.store

import java.io.ByteArrayOutputStream

/** LEB128 varints with zigzag for signed values. */
internal object Varint {
    fun zigzag(v: Long): Long = (v shl 1) xor (v shr 63)

    fun unzigzag(v: Long): Long = (v ushr 1) xor -(v and 1)

    fun write(
        out: ByteArrayOutputStream,
        value: Long,
    ) {
        var v = zigzag(value)
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    /** Encoded size in bytes, without writing. */
    fun size(value: Long): Int {
        var v = zigzag(value)
        var n = 1
        while (v and 0x7FL.inv() != 0L) {
            v = v ushr 7
            n++
        }
        return n
    }
}

/** Reads varints from a byte array; throws [CodecException] on truncation. */
internal class VarintReader(
    private val buf: ByteArray,
) {
    var pos = 0
        private set

    val remaining: Int get() = buf.size - pos

    fun u8(): Int {
        if (pos >= buf.size) throw CodecException("truncated")
        return buf[pos++].toInt() and 0xFF
    }

    fun long(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (shift > 63) throw CodecException("varint too long")
            val b = u8()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Varint.unzigzag(result)
    }
}

class CodecException(
    message: String,
) : Exception(message)
