package dev.vescmonitor.vesc

import android.content.Context
import dev.vescmonitor.core.alert.ActiveAlert
import dev.vescmonitor.core.alert.AlertCodes
import dev.vescmonitor.core.frame.TelemetryFrame
import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.ride.RideRecorder
import dev.vescmonitor.core.ride.RideSeries
import dev.vescmonitor.core.ride.RideTrigger
import dev.vescmonitor.core.ride.StorageGuard
import dev.vescmonitor.core.session.ControllerLive
import dev.vescmonitor.core.session.SessionListener
import dev.vescmonitor.core.store.StreamInfo
import dev.vescmonitor.vesc.db.RideRow
import dev.vescmonitor.vesc.db.RoomSampleStore
import dev.vescmonitor.vesc.db.VescDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Ride logging: the Room store on its own writer thread, the recorder, and the persisted
 * on/off toggle. Session callbacks arrive on the session thread.
 */
internal class RideLog(
    context: Context,
    private val clock: Clock,
) : SessionListener {
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "VescStore") }.asCoroutineDispatcher()
    private val writerScope = CoroutineScope(SupervisorJob() + writer)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val store = RoomSampleStore(VescDatabase.open(context), writer)
    val recorder = RideRecorder(store, writerScope)
    private var controllers: List<ControllerLive> = emptyList()

    /** Logging on or off; persisted. On by default: rides are kept unless the rider opts out. */
    var enabled: Boolean = prefs.getBoolean(KEY_ENABLED, true)
        private set

    init {
        writerScope.launch { recorder.recover() }
    }

    /** The session's active alerts, stored with each sample. */
    @Volatile var activeAlerts: () -> List<ActiveAlert> = { emptyList() }

    override suspend fun onPolling(controllers: List<ControllerLive>) {
        // After a reconnect the open ride continues, unless the controllers changed: its
        // streams follow the first layout, so a different set starts a new ride.
        if (recorder.recording && ids(controllers) != ids(this.controllers)) recorder.close()
        this.controllers = controllers
    }

    private val trigger = RideTrigger()
    private val dbFile = context.getDatabasePath(VescDatabase.NAME)
    private var spaceCheckedMs = Long.MIN_VALUE

    /** Phone storage is nearly full: rides are not recorded until space is freed. */
    @Volatile var storageLow = false
        private set

    private fun checkSpace(nowMs: Long) {
        if (spaceCheckedMs != Long.MIN_VALUE && nowMs - spaceCheckedMs < SPACE_CHECK_MS) return
        spaceCheckedMs = nowMs
        val free = dbFile.parentFile?.usableSpace ?: return
        val db = listOf("", "-wal", "-shm").sumOf { File(dbFile.path + it).length() }
        storageLow = StorageGuard.low(free, db)
    }

    /** Session thread, ~10 Hz while connected: opens a ride on motion, closes it after a long stop. */
    suspend fun autoTick(frame: TelemetryFrame) {
        val now = clock.nowMs()
        checkSpace(now)
        if (storageLow) {
            if (recorder.recording) recorder.close()
            return
        }
        when (trigger.update(now, RideTrigger.moving(frame), recorder.recording)) {
            RideTrigger.Action.OPEN -> if (enabled && controllers.isNotEmpty()) open()
            RideTrigger.Action.CLOSE -> recorder.close()
            RideTrigger.Action.NONE -> Unit
        }
    }

    private fun ids(list: List<ControllerLive>) = list.map { it.controller.canId to it.controller.isLocal }

    override fun onSample(
        index: Int,
        c: ControllerLive,
    ) = recorder.onSample(index, clock.nowMs(), c.values, c.speedMps, AlertCodes.forController(activeAlerts(), c.controller.canId))

    /** Session thread. Switching on while connected opens a ride at once; off closes it. */
    suspend fun setEnabled(
        on: Boolean,
        connected: Boolean,
    ) {
        enabled = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        if (on && connected && controllers.isNotEmpty() && !storageLow) open() else if (!on) recorder.close()
    }

    /** Session thread: the session ended (disconnect, link loss, app closing). */
    suspend fun sessionEnded() {
        trigger.reset()
        controllers = emptyList()
        recorder.close()
    }

    suspend fun rides(limit: Int): List<RideRow> = store.rides(limit)

    /** A stored ride downsampled for charts; null when it has no samples. */
    suspend fun series(
        id: Long,
        buckets: Int,
    ): Map<String, Any?>? = RideSeries.of(store.chunks(id), buckets)?.toMap()

    suspend fun delete(id: Long) {
        if (id == recorder.captureId) return
        store.deleteCapture(id)
    }

    /** Saves a finished speed-test run, whether or not ride logging is on. */
    fun saveRun(report: Map<String, Any?>) {
        writerScope.launch {
            try {
                store.saveRun(JSONObject(report).toString())
            } catch (_: Exception) {
                runSaveErrors++
            }
        }
    }

    var runSaveErrors = 0
        private set

    suspend fun runs(limit: Int): List<Map<String, Any?>> =
        store.runs(limit).map { mapOf("id" to it.id, "startWallMs" to it.startWallMs, "json" to it.json) }

    suspend fun deleteRun(id: Long) = store.deleteRun(id)

    fun stateMap(nowMs: Long): Map<String, Any?> =
        mapOf(
            "logging" to enabled,
            "rideElapsedMs" to recorder.rideStartMs?.let { nowMs - it },
            "droppedChunks" to recorder.droppedChunks,
            "storeErrors" to recorder.storeErrors,
            "storageLow" to storageLow,
        )

    private fun open() {
        recorder.open(clock.nowMs(), controllers.mapIndexed { i, c -> StreamInfo(i, c.controller.canId, c.controller.isLocal) })
    }

    companion object {
        private const val PREFS = "vesc-monitor"
        private const val KEY_ENABLED = "logging"
        private const val SPACE_CHECK_MS = 30_000L
    }
}
