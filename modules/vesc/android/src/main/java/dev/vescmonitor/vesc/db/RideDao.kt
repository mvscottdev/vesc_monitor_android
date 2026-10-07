package dev.vescmonitor.vesc.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Blocking DAO: every call runs on the store's single writer thread. */
@Dao
interface RideDao {
    @Insert
    fun insertCapture(c: CaptureEntity): Long

    @Insert
    fun insertChunks(chunks: List<ChunkEntity>)

    @Insert
    fun insertRide(r: RideEntity)

    @Query("UPDATE capture SET bytes = bytes + :bytes WHERE id = :id")
    fun addBytes(
        id: Long,
        bytes: Long,
    )

    @Query("UPDATE capture SET state = :state WHERE id = :id")
    fun setState(
        id: Long,
        state: String,
    )

    @Query("SELECT * FROM chunk WHERE capture_id = :captureId AND tier = 0 ORDER BY stream, t0_ms")
    fun chunks(captureId: Long): List<ChunkEntity>

    @Query("SELECT id FROM capture WHERE state = 'recording'")
    fun openCaptureIds(): List<Long>

    @Query("DELETE FROM capture WHERE id = :id")
    fun deleteCapture(id: Long)

    @Query(
        "SELECT c.id, c.state, c.start_wall_ms, c.streams, c.bytes, r.duration_ms, r.wh_used, r.wh_regen, " +
            "r.peak_power_w, r.peak_regen_w, r.peak_current_in_a, r.min_voltage_v, r.max_temp_motor_c, " +
            "r.max_temp_fet_c, r.faults FROM capture c JOIN ride r ON r.capture_id = c.id " +
            "WHERE c.kind = 'ride' ORDER BY c.start_wall_ms DESC LIMIT :limit",
    )
    fun rides(limit: Int): List<RideRow>

    @Insert
    fun insertRun(r: RunEntity): Long

    @Query("SELECT * FROM run ORDER BY start_wall_ms DESC LIMIT :limit")
    fun runs(limit: Int): List<RunEntity>

    @Query("DELETE FROM run WHERE id = :id")
    fun deleteRun(id: Long)
}
