package dev.vescmonitor.core.protocol

/** Thrown when a reply is shorter than its layout or otherwise malformed. */
class ParseException(
    message: String,
) : Exception(message)

/** Big-endian reader with a remaining-bytes guard on every read. */
class ByteReader(
    private val data: ByteArray,
    start: Int = 0,
) {
    var position = start
        private set

    val remaining: Int get() = data.size - position

    private fun need(n: Int) {
        if (remaining < n) throw ParseException("need $n bytes at $position, have $remaining")
    }

    fun u8(): Int {
        need(1)
        return data[position++].toInt() and 0xFF
    }

    fun i8(): Int {
        need(1)
        return data[position++].toInt()
    }

    fun i16(): Int {
        need(2)
        val v = (data[position].toInt() shl 8) or (data[position + 1].toInt() and 0xFF)
        position += 2
        return v.toShort().toInt()
    }

    fun i32(): Int {
        need(4)
        var v = 0
        repeat(4) { v = (v shl 8) or (data[position++].toInt() and 0xFF) }
        return v
    }

    fun u32(): Long = i32().toLong() and 0xFFFF_FFFFL

    fun bytes(n: Int): ByteArray {
        need(n)
        val out = data.copyOfRange(position, position + n)
        position += n
        return out
    }

    /** NUL-terminated string; a missing terminator is a parse error. */
    fun cString(): String {
        val end = (position until data.size).firstOrNull { data[it] == 0.toByte() }
        if (end == null) throw ParseException("unterminated string at $position")
        val s = String(data, position, end - position, Charsets.UTF_8)
        position = end + 1
        return s
    }
}
