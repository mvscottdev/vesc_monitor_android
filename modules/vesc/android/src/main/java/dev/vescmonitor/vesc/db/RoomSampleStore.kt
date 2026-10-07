package dev.vescmonitor.vesc.db

import dev.vescmonitor.core.ride.RideSummary
import dev.vescmonitor.core.store.Chunk
import dev.vescmonitor.core.store.ChunkCodec
import dev.vescmonitor.core.store.SampleStore
import dev.vescmonitor.core.store.StreamInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [SampleStore] on Room. Every call runs on [writer], one transaction per call. */
class RoomSampleStore(
    private val db: VescDatabase,
    private val writer: CoroutineDispatcher,
) : SampleStore {
    private val dao = db.rides()

    override suspend fun openCapture(
        startMs: Long,
        streams: List<StreamInfo>,
    ): Long =
        io {
            val text = streams.joinToString(",") { "${it.stream}:${it.controllerId}:${if (it.local) "L" else "C"}" }
            dao.insertCapture(
                CaptureEntity(state = STATE_RECORDING, startWallMs = System.currentTimeMillis(), codecVer = ChunkCodec.VERSION, streams = text),
            )
        }

    override suspend fun writeChunks(
        captureId: Long,
        chunks: List<Chunk>,
    ) = io {
        db.runInTransaction {
            dao.insertChunks(chunks.map { ChunkEntity(captureId = captureId, stream = it.stream, tier = 0, t0Ms = it.t0Ms, n = it.n, codec = it.codec, data = it.data) })
            dao.addBytes(captureId, chunks.sumOf { it.data.size.toLong() })
        }
    }

    override suspend fun chunks(captureId: Long): List<Chunk> = io { dao.chunks(captureId).map { Chunk(it.stream, it.t0Ms, it.n, it.codec, it.data) } }

    override suspend fun closeCapture(
        captureId: Long,
        summary: RideSummary,
        recovered: Boolean,
    ) = io {
        db.runInTransaction {
            dao.insertRide(summary.toEntity(captureId))
            dao.setState(captureId, if (recovered) STATE_RECOVERED else STATE_CLOSED)
        }
    }

    override suspend fun deleteCapture(captureId: Long) = io { dao.deleteCapture(captureId) }

    override suspend fun openCaptures(): List<Long> = io { dao.openCaptureIds() }

    suspend fun rides(limit: Int): List<RideRow> = io { dao.rides(limit) }

    suspend fun saveRun(json: String): Long = io { dao.insertRun(RunEntity(startWallMs = System.currentTimeMillis(), json = json)) }

    suspend fun runs(limit: Int): List<RunEntity> = io { dao.runs(limit) }

    suspend fun deleteRun(id: Long) = io { dao.deleteRun(id) }

    private suspend fun <T> io(block: () -> T): T = withContext(writer) { block() }

    private fun RideSummary.toEntity(captureId: Long) =
        RideEntity(
            captureId = captureId,
            durationMs = durationMs,
            samples = samples,
            whUsed = whUsed,
            whRegen = whRegen,
            ahUsed = ahUsed,
            ahRegen = ahRegen,
            peakPowerW = peakPowerW,
            peakRegenW = peakRegenW,
            peakCurrentInA = peakCurrentInA,
            minVoltageV = minVoltageV,
            maxTempMotorC = maxTempMotorC,
            maxTempFetC = maxTempFetC,
            faults = faultCodes.joinToString(","),
            counterResets = counterResets,
        )

    companion object {
        const val STATE_RECORDING = "recording"
        const val STATE_CLOSED = "closed"
        const val STATE_RECOVERED = "recovered"
    }
}
