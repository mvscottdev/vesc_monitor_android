package dev.vescmonitor.core.store

import dev.vescmonitor.core.ride.RideSummary

/** Where a capture's chunks and summary go. The Android adapter uses Room; tests use memory. */
interface SampleStore {
    /** Starts a capture in state `recording`; returns its id. */
    suspend fun openCapture(
        startMs: Long,
        streams: List<StreamInfo>,
    ): Long

    /** Writes one second of chunks in one transaction. */
    suspend fun writeChunks(
        captureId: Long,
        chunks: List<Chunk>,
    )

    /** Every chunk of a capture, ordered by stream then time. */
    suspend fun chunks(captureId: Long): List<Chunk>

    /** Marks the capture closed (or recovered) and stores its summary. */
    suspend fun closeCapture(
        captureId: Long,
        summary: RideSummary,
        recovered: Boolean,
    )

    /** Removes a capture and everything stored for it. */
    suspend fun deleteCapture(captureId: Long)

    /** Captures still `recording`: left open by a kill. */
    suspend fun openCaptures(): List<Long>
}

/** A stream of a capture: which controller it records. */
data class StreamInfo(
    val stream: Int,
    val controllerId: Int,
    val local: Boolean,
)
