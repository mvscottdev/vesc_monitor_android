package dev.vescmonitor.core.store

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Columnar chunk payload, one chunk per second per stream. Each column is stored as
 * raw, delta or delta-of-delta zigzag varints, whichever is smallest; the whole payload
 * is Deflate-compressed. Columns and keyframe values are tagged with field IDs, so
 * decoding skips what it doesn't know and a new field needs no DB migration.
 *
 * Layout (before Deflate): version u8, n varint, columns u8, then per column
 * [fieldId u8, mode u8, n varints], then keyframes u8, per keyframe [fieldId u8, varint].
 */
object ChunkCodec {
    const val VERSION = 1
    private const val RAW = 0
    private const val DELTA = 1
    private const val DELTA2 = 2

    class Decoded(
        val n: Int,
        val columns: Map<Int, LongArray>,
        val keyframe: Map<Int, Long>,
    )

    fun encode(
        n: Int,
        columns: Map<Int, LongArray>,
        keyframe: Map<Int, Long> = emptyMap(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        Varint.write(out, n.toLong())
        out.write(columns.size)
        for ((id, col) in columns) {
            require(col.size == n) { "column $id has ${col.size} values, expected $n" }
            val (mode, values) = smallest(col)
            out.write(id)
            out.write(mode)
            values.forEach { Varint.write(out, it) }
        }
        out.write(keyframe.size)
        for ((id, v) in keyframe) {
            out.write(id)
            Varint.write(out, v)
        }
        return deflate(out.toByteArray())
    }

    fun decode(data: ByteArray): Decoded {
        val r = VarintReader(inflate(data))
        val version = r.u8()
        if (version != VERSION) throw CodecException("unknown codec version $version")
        val n = r.long().toInt()
        val columns = HashMap<Int, LongArray>()
        repeat(r.u8()) {
            val id = r.u8()
            val mode = r.u8()
            val raw = LongArray(n) { r.long() }
            columns[id] = restore(mode, raw)
        }
        val keyframe = HashMap<Int, Long>()
        repeat(r.u8()) { keyframe[r.u8()] = r.long() }
        return Decoded(n, columns, keyframe)
    }

    private fun smallest(col: LongArray): Pair<Int, LongArray> {
        val d1 = diff(col)
        val d2 = diff(d1, from = 2)
        return listOf(RAW to col, DELTA to d1, DELTA2 to d2).minBy { (_, v) -> v.sumOf { Varint.size(it) } }
    }

    /** Differences from index [from] on; earlier values are kept as they are. */
    private fun diff(
        v: LongArray,
        from: Int = 1,
    ): LongArray = LongArray(v.size) { i -> if (i < from) v[i] else v[i] - v[i - 1] }

    private fun restore(
        mode: Int,
        v: LongArray,
    ): LongArray =
        when (mode) {
            RAW -> v
            DELTA -> integrate(v, 1)
            DELTA2 -> integrate(integrate(v, 2), 1)
            else -> throw CodecException("unknown column mode $mode")
        }

    private fun integrate(
        v: LongArray,
        from: Int,
    ): LongArray {
        val out = v.copyOf()
        for (i in from until out.size) out[i] = out[i - 1] + v[i]
        return out
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(bytes)
        d.finish()
        val out = ByteArrayOutputStream(bytes.size / 2 + 16)
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val i = Inflater()
        i.setInput(bytes)
        val out = ByteArrayOutputStream(bytes.size * 4)
        val buf = ByteArray(1024)
        try {
            while (!i.finished()) {
                val k = i.inflate(buf)
                if (k == 0 && (i.needsInput() || i.needsDictionary())) throw CodecException("truncated chunk")
                out.write(buf, 0, k)
            }
        } catch (e: DataFormatException) {
            throw CodecException("corrupt chunk: ${e.message}")
        } finally {
            i.end()
        }
        return out.toByteArray()
    }
}
