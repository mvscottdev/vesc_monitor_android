package dev.vescmonitor.core.store

import dev.vescmonitor.core.session.LiveValues

/** One stored chunk: a second of samples from one stream (stream = controller index). */
class Chunk(
    val stream: Int,
    val t0Ms: Long,
    val n: Int,
    val codec: Int,
    val data: ByteArray,
)

/**
 * Collects the samples of one stream and cuts a chunk at every second boundary
 * (session clock). The keyframe carries the counters as of the chunk's last sample.
 */
class ChunkBuilder(
    val stream: Int,
) {
    private val rows = ArrayList<LongArray>()
    private var keyframe: Map<Int, Long> = emptyMap()
    private var t0Ms = Long.MIN_VALUE

    val pending: Int get() = rows.size

    /** Adds a sample; returns the finished chunk of the previous second, if this sample starts a new one. */
    fun add(
        tMs: Long,
        values: LiveValues,
        speedMps: Double = Double.NaN,
        alertCode: Int = 0,
    ): Chunk? {
        val second = Math.floorDiv(tMs, 1000L) * 1000L
        val done = if (rows.isNotEmpty() && second != t0Ms) flush() else null
        if (rows.isEmpty()) t0Ms = second
        rows += values.toRow(tMs, speedMps, alertCode)
        keyframe = values.keyframe()
        return done
    }

    /** The open chunk, or null when empty. */
    fun flush(): Chunk? {
        if (rows.isEmpty()) return null
        val n = rows.size
        val columns = SampleField.entries.associate { f -> f.id to LongArray(n) { rows[it][f.ordinal] } }
        val chunk = Chunk(stream, t0Ms, n, ChunkCodec.VERSION, ChunkCodec.encode(n, columns, keyframe))
        rows.clear()
        return chunk
    }
}
