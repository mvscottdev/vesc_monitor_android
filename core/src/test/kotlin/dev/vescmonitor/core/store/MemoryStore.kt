package dev.vescmonitor.core.store

import dev.vescmonitor.core.ride.RideSummary

/** In-memory [SampleStore] for tests. */
class MemoryStore : SampleStore {
    class Capture(
        val startMs: Long,
        val streams: List<StreamInfo>,
    ) {
        val chunks = ArrayList<Chunk>()
        var state = "recording"
        var summary: RideSummary? = null
        var writes = 0
    }

    val captures = LinkedHashMap<Long, Capture>()
    private var nextId = 1L

    override suspend fun openCapture(
        startMs: Long,
        streams: List<StreamInfo>,
    ): Long = nextId++.also { captures[it] = Capture(startMs, streams) }

    override suspend fun writeChunks(
        captureId: Long,
        chunks: List<Chunk>,
    ) {
        val c = captures.getValue(captureId)
        c.chunks += chunks
        c.writes++
    }

    override suspend fun chunks(captureId: Long): List<Chunk> =
        captures[captureId]?.chunks?.sortedWith(compareBy({ it.stream }, { it.t0Ms })) ?: emptyList()

    override suspend fun closeCapture(
        captureId: Long,
        summary: RideSummary,
        recovered: Boolean,
    ) {
        val c = captures.getValue(captureId)
        c.summary = summary
        c.state = if (recovered) "recovered" else "closed"
    }

    override suspend fun deleteCapture(captureId: Long) {
        captures.remove(captureId)
    }

    override suspend fun openCaptures(): List<Long> = captures.filterValues { it.state == "recording" }.keys.toList()
}
