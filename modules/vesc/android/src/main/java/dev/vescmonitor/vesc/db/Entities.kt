package dev.vescmonitor.vesc.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One recording (a ride; speed-test runs later). State: recording, closed or recovered. */
@Entity(tableName = "capture")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = "ride",
    val state: String,
    /** Wall-clock start (epoch ms) for the history list. */
    @ColumnInfo(name = "start_wall_ms") val startWallMs: Long,
    @ColumnInfo(name = "codec_ver") val codecVer: Int,
    /** Streams as "index:canId:L|C" joined by commas (L = wired to the bridge). */
    val streams: String,
    val bytes: Long = 0,
)

/** One second of samples of one stream, compressed by the core chunk codec. */
@Entity(
    tableName = "chunk",
    indices = [Index(value = ["capture_id", "stream", "tier", "t0_ms"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = CaptureEntity::class, parentColumns = ["id"], childColumns = ["capture_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "capture_id") val captureId: Long,
    val stream: Int,
    val tier: Int,
    @ColumnInfo(name = "t0_ms") val t0Ms: Long,
    val n: Int,
    val codec: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
)

/** Exact summary of a closed or recovered ride. */
@Entity(
    tableName = "ride",
    foreignKeys = [
        ForeignKey(entity = CaptureEntity::class, parentColumns = ["id"], childColumns = ["capture_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RideEntity(
    @PrimaryKey @ColumnInfo(name = "capture_id") val captureId: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val samples: Int,
    @ColumnInfo(name = "wh_used") val whUsed: Double,
    @ColumnInfo(name = "wh_regen") val whRegen: Double,
    @ColumnInfo(name = "ah_used") val ahUsed: Double,
    @ColumnInfo(name = "ah_regen") val ahRegen: Double,
    @ColumnInfo(name = "peak_power_w") val peakPowerW: Double,
    @ColumnInfo(name = "peak_regen_w") val peakRegenW: Double,
    @ColumnInfo(name = "peak_current_in_a") val peakCurrentInA: Double,
    @ColumnInfo(name = "min_voltage_v") val minVoltageV: Double?,
    @ColumnInfo(name = "max_temp_motor_c") val maxTempMotorC: Double?,
    @ColumnInfo(name = "max_temp_fet_c") val maxTempFetC: Double?,
    /** Distinct non-zero fault codes, comma-separated. */
    val faults: String,
    @ColumnInfo(name = "counter_resets") val counterResets: Int,
)

/** A history row: the capture joined with its summary. */
data class RideRow(
    val id: Long,
    val state: String,
    @ColumnInfo(name = "start_wall_ms") val startWallMs: Long,
    val streams: String,
    val bytes: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "wh_used") val whUsed: Double,
    @ColumnInfo(name = "wh_regen") val whRegen: Double,
    @ColumnInfo(name = "peak_power_w") val peakPowerW: Double,
    @ColumnInfo(name = "peak_regen_w") val peakRegenW: Double,
    @ColumnInfo(name = "peak_current_in_a") val peakCurrentInA: Double,
    @ColumnInfo(name = "min_voltage_v") val minVoltageV: Double?,
    @ColumnInfo(name = "max_temp_motor_c") val maxTempMotorC: Double?,
    @ColumnInfo(name = "max_temp_fet_c") val maxTempFetC: Double?,
    val faults: String,
)

/** A finished speed-test run: its report and curve as JSON (fields may grow without a migration). */
@Entity(tableName = "run")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_wall_ms") val startWallMs: Long,
    val json: String,
)
