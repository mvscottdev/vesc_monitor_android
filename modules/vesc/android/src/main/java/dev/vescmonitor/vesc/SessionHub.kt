package dev.vescmonitor.vesc

import android.content.Context
import android.os.SystemClock
import dev.vescmonitor.core.alert.ActiveAlert
import dev.vescmonitor.core.alert.AlertCatalog
import dev.vescmonitor.core.alert.Severity
import dev.vescmonitor.core.alert.withThresholds
import dev.vescmonitor.core.alert.withTuning
import dev.vescmonitor.core.battery.BatteryDetect
import dev.vescmonitor.core.battery.CapacityLearner
import dev.vescmonitor.core.battery.Chemistry
import dev.vescmonitor.core.battery.RangeEstimator
import dev.vescmonitor.core.frame.TelemetryFrame
import dev.vescmonitor.core.link.BleTransport
import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.replay.CaptureRecord
import dev.vescmonitor.core.replay.RecordingTransport
import dev.vescmonitor.core.replay.ReplayTransport
import dev.vescmonitor.core.run.Bracket
import dev.vescmonitor.core.session.ConnectionState
import dev.vescmonitor.core.session.PollConfig
import dev.vescmonitor.core.session.SessionSnapshot
import dev.vescmonitor.core.session.StatusText
import dev.vescmonitor.core.session.VehicleSession
import dev.vescmonitor.core.sim.FakeVesc
import dev.vescmonitor.core.sim.SyntheticTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors

/** Where the session reads its bytes from. */
internal sealed interface TransportChoice {
    data object Ble : TransportChoice

    data class Synthetic(
        val controllers: Int,
    ) : TransportChoice

    data class Replay(
        val path: String,
    ) : TransportChoice
}

/**
 * Process-wide holder of the session. All session work runs on one dedicated thread;
 * the JS module only sends intents and receives events.
 */
internal object SessionHub {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "VescSession") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val clock = Clock { SystemClock.elapsedRealtime() }

    @Volatile var frameListener: ((Map<String, Any?>) -> Unit)? = null

    @Volatile var sessionListener: ((Map<String, Any?>) -> Unit)? = null

    @Volatile var scanListener: ((Map<String, Any?>) -> Unit)? = null

    @Volatile var alertListener: ((Map<String, Any?>) -> Unit)? = null

    private lateinit var context: Context
    private lateinit var scanner: BleScanner
    private lateinit var captures: CaptureFiles
    private var choice: TransportChoice = TransportChoice.Ble
    private var pollConfig = PollConfig()
    private var session: VehicleSession? = null
    private var recorder: RecordingTransport? = null
    private var address: String? = null
    private var generation = 0
    private var started = false
    private var serviceRunning = false
    private lateinit var rideLog: RideLog

    @Synchronized
    fun init(appContext: Context) {
        if (started) return
        started = true
        context = appContext.applicationContext
        scanner = BleScanner(context)
        captures = CaptureFiles(context)
        rideLog = RideLog(context, clock).also { it.activeAlerts = { session?.activeAlerts ?: emptyList() } }
        scope.launch { tick() }
    }

    /** Texts of the ongoing notification. */
    fun statusText(): StatusText {
        val src = transportName()
        val s = session?.snapshot?.value ?: return StatusText.of(ConnectionState.IDLE, 0, src, null, null, null, false)
        val now = clock.nowMs()
        val hz = s.controllers.filter { it.isFresh(now) && it.measuredHz > 0 }.minOfOrNull { it.measuredHz }
        val mph = prefs().getString(UNITS_KEY, null)?.contains("\"speed\":\"mph\"") == true
        return StatusText.of(s.state, s.attempt, src, hz, session?.frame(), rideElapsedMs(now), mph)
    }

    private fun rideElapsedMs(now: Long): Long? = rideLog.recorder.rideStartMs?.let { now - it }

    /** Time since the open ride started; null when none records. */
    fun rideElapsedNow(): Long? = rideElapsedMs(clock.nowMs())

    /** Whether the notification offers Disconnect. */
    fun sessionOpen(): Boolean =
        session?.snapshot?.value?.state.let { it == ConnectionState.CONNECTED || it == ConnectionState.RECONNECTING }

    /** Notification buttons: stop ride logging (as the Settings switch) or end the session. */
    fun fromNotification(
        action: String?,
        done: () -> Unit,
    ) {
        // A stale notification in a fresh process: nothing to act on.
        if (!started) return done()
        scope.launch {
            try {
                when (action) {
                    NotificationActions.STOP_RECORDING -> {
                        rideLog.setEnabled(false, false)
                    }

                    NotificationActions.DISCONNECT -> {
                        stopSession()
                        stopService()
                    }
                }
            } finally {
                done()
            }
        }
    }


    suspend fun setTransport(next: TransportChoice) = withContext(dispatcher) { choice = next }

    suspend fun setPollConfig(next: PollConfig) = withContext(dispatcher) { pollConfig = next }

    suspend fun scan(on: Boolean): String? =
        withContext(dispatcher) {
            if (on) scanner.start()?.message else null.also { scanner.stop() }
        }

    suspend fun connect(bleAddress: String?) =
        withContext(dispatcher) {
            stopSession()
            scanner.stop()
            address = bleAddress
            val inner = makeTransport(bleAddress)
            val rec = RecordingTransport(inner, clock)
            recorder = rec
            if (choice == TransportChoice.Ble) startService()
            session =
                VehicleSession(rec, clock, scope, ++generation, pollConfig, rideLog, reconnect = choice == TransportChoice.Ble).also {
                    it.disabledAlerts = disabledAlerts()
                    silent = silentAlerts()
                    it.alertThresholds = thresholds()
                    it.alertTuning = tuning()
                    it.confirmedPack = savedPack()
                    savedCapacity = loadCapacity()
                    it.capacityState = savedCapacity
                    it.rangePrior = RangeEstimator.median(rangeHistory())
                    it.capacityOverrideAh = savedCapacityAh()
                    it.emptyCellOverrideV = savedEmptyCellV()
                    it.visible = visible
                    it.start()
                }
            alertsChanged = true
        }

    suspend fun disconnect() = withContext(dispatcher) { stopSession() }

    suspend fun startRecording(): String? =
        withContext(dispatcher) {
            val rec = recorder ?: return@withContext null
            val sink = captures.open()
            val meta = mutableMapOf("transport" to transportName(), "generation" to generation.toString())
            address?.let { meta["device"] = it }
            rec.startRecording(sink, meta)
            captures.recordingPath
        }

    suspend fun stopRecording(): String? =
        withContext(dispatcher) {
            recorder?.stopRecording()
            captures.close()
        }

    suspend fun recordings(): List<String> = withContext(dispatcher) { captures.list() }

    suspend fun liveState(): Map<String, Any?> =
        withContext(dispatcher) {
            val now = clock.nowMs()
            mapOf(
                "frame" to session?.frame()?.toMap(),
                "alerts" to alertsMap(),
                "session" to sessionMap(now),
                "scan" to scanMap(),
            )
        }

    private fun makeTransport(bleAddress: String?): BleTransport =
        when (val c = choice) {
            TransportChoice.Ble -> NordicBleTransport(context, requireNotNull(bleAddress) { "no device" }, scope)
            is TransportChoice.Synthetic -> SyntheticTransport(FakeVesc.synthetic(c.controllers), scope, clock)
            is TransportChoice.Replay -> ReplayTransport(CaptureRecord.decodeAll(captures.read(c.path)), scope)
        }

    suspend fun setLogging(on: Boolean) =
        withContext(dispatcher) {
            rideLog.setEnabled(on, session?.snapshot?.value?.state == ConnectionState.CONNECTED)
        }

    suspend fun rides(limit: Int): List<Map<String, Any?>> =
        rideLog.rides(limit).map {
            mapOf(
                "id" to it.id,
                "state" to it.state,
                "startWallMs" to it.startWallMs,
                "streams" to it.streams,
                "bytes" to it.bytes,
                "durationMs" to it.durationMs,
                "whUsed" to it.whUsed,
                "whRegen" to it.whRegen,
                "peakPowerW" to it.peakPowerW,
                "peakRegenW" to it.peakRegenW,
                "peakCurrentInA" to it.peakCurrentInA,
                "minVoltageV" to it.minVoltageV,
                "maxTempMotorC" to it.maxTempMotorC,
                "maxTempFetC" to it.maxTempFetC,
                "faults" to it.faults,
            )
        }

    suspend fun deleteRide(id: Long) = rideLog.delete(id)

    suspend fun rideSeries(
        id: Long,
        buckets: Int,
    ) = rideLog.series(id, buckets)

    suspend fun runs(limit: Int): List<Map<String, Any?>> = rideLog.runs(limit)

    suspend fun deleteRun(id: Long) = rideLog.deleteRun(id)

    /** Called when the app is swiped away: save the open ride, then end the session. Blocks briefly. */
    fun shutdownFromService() {
        runBlocking {
            withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { withContext(dispatcher) { stopSession() } }
        }
    }

    private suspend fun stopSession() {
        rememberRide()
        rideLog.sessionEnded()
        recorder?.stopRecording()
        captures.close()
        session?.stop()
        session = null
        alertsChanged = true
        recorder = null
        stopService()
    }

    private fun startService() {
        VescService.start(context)
        serviceRunning = true
    }

    private fun stopService() {
        if (!serviceRunning) return
        VescService.stop(context)
        serviceRunning = false
    }

    private fun transportName(): String =
        when (val c = choice) {
            TransportChoice.Ble -> "ble"
            is TransportChoice.Synthetic -> "synthetic-${c.controllers}"
            is TransportChoice.Replay -> "replay"
        }

    private fun sessionMap(now: Long): Map<String, Any?> {
        session?.refresh()
        val snap = session?.snapshot?.value ?: SessionSnapshot(generation, ConnectionState.IDLE, config = pollConfig)
        return snap.toMap(now) + rideLog.stateMap(now) +
            mapOf(
                "transport" to transportName(),
                "address" to address,
                "recording" to captures.recordingPath,
                "run" to session?.runMap(now),
            )
    }

    /** Arms a speed-test run; resolves to a plain reason when it cannot. */
    suspend fun armRun(): String? =
        withContext(dispatcher) {
            // Brackets follow the rider's speed unit (the UI's units setting).
            val mph = prefs().getString(UNITS_KEY, null)?.contains("\"speed\":\"mph\"") == true
            session?.armRun(if (mph) Bracket.IMPERIAL else Bracket.DEFAULT) ?: "Connect to the vehicle first."
        }

    suspend fun cancelRun() = withContext(dispatcher) { session?.cancelRun() }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun disabledAlerts(): Set<String> = prefs().getStringSet(KEY_DISABLED, emptySet())?.toSet() ?: emptySet()

    suspend fun alertSettings(): List<String> = withContext(dispatcher) { disabledAlerts().sorted() }

    /** Switches one alert on or off (persisted, applied to the running session at once). */
    suspend fun setAlertEnabled(
        key: String,
        on: Boolean,
    ) = withContext(dispatcher) {
        val next = if (on) disabledAlerts() - key else disabledAlerts() + key
        prefs().edit().putStringSet(KEY_DISABLED, next).apply()
        session?.disabledAlerts = next
        alertsChanged = true
    }

    /** Alerts that only show the on-screen banner, never a notification (read on every alert change). */
    @Volatile private var silent: Set<String> = emptySet()

    private fun silentAlerts(): Set<String> = prefs().getStringSet(KEY_SILENT, emptySet())?.toSet() ?: emptySet()

    suspend fun silentAlertKeys(): List<String> = withContext(dispatcher) { silentAlerts().sorted() }

    /** Whether the app is on screen; retries never use the slow tier while it is. */
    private var visible = true

    suspend fun setVisible(on: Boolean) =
        withContext(dispatcher) {
            visible = on
            session?.visible = on
        }

    /** Makes one alert banner-only (no sound or notification), or loud again; persisted. */
    suspend fun setAlertSilent(
        key: String,
        silentOn: Boolean,
    ) = withContext(dispatcher) {
        val next = if (silentOn) silentAlerts() + key else silentAlerts() - key
        prefs().edit().putStringSet(KEY_SILENT, next).apply()
        silent = next
    }

    private fun thresholds(): Map<String, List<Double>> = readLists(KEY_THRESHOLDS)

    /** Defaults and the rider's thresholds for every editable alert. */
    suspend fun alertThresholds(): Map<String, Any?> =
        withContext(dispatcher) {
            mapOf(
                "defaults" to AlertCatalog.EDITABLE.associate { r -> r.key to r.levels.map { it.threshold } },
                "overrides" to thresholds(),
            )
        }

    /** Sets one alert's thresholds (null restores the defaults); resolves to a plain reason when rejected. */
    suspend fun setAlertThresholds(
        key: String,
        values: List<Double>?,
    ): String? =
        withContext(dispatcher) {
            val rule = AlertCatalog.EDITABLE.firstOrNull { it.key == key } ?: return@withContext "This alert has fixed thresholds."
            if (values != null && rule.withThresholds(values) == null) {
                return@withContext "Each level must be ${if (rule.above) "higher" else "lower"} than the one before."
            }
            val next = if (values == null) thresholds() - key else thresholds() + (key to values)
            val o = JSONObject()
            next.forEach { (k, v) -> o.put(k, JSONArray(v)) }
            prefs().edit().putString(KEY_THRESHOLDS, o.toString()).apply()
            session?.alertThresholds = next
            alertsChanged = true
            null
        }

    private fun tuning(): Map<String, List<Double>> = readLists(KEY_TUNING)

    private fun readLists(key: String): Map<String, List<Double>> {
        val raw = prefs().getString(key, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> List(a.length()) { a.getDouble(it) } } }
        } catch (_: JSONException) {
            emptyMap()
        }
    }

    /** Defaults and the rider's [hysteresis, repeat s] for every editable alert. */
    suspend fun alertTuning(): Map<String, Any?> =
        withContext(dispatcher) {
            mapOf(
                "defaults" to AlertCatalog.EDITABLE.associate { r -> r.key to listOf(r.hysteresis, r.cooldownMs / 1000.0) },
                "overrides" to tuning(),
            )
        }

    /** Sets one alert's hysteresis and repeat time (null restores the defaults); resolves to a plain reason when rejected. */
    suspend fun setAlertTuning(
        key: String,
        values: List<Double>?,
    ): String? =
        withContext(dispatcher) {
            val base = AlertCatalog.EDITABLE.firstOrNull { it.key == key } ?: return@withContext "This alert has fixed settings."
            // The margin is checked against the thresholds in effect, as the engine applies it.
            val rule = thresholds()[key]?.let { base.withThresholds(it) } ?: base
            if (values != null && (values.size != 2 || rule.withTuning(values[0], (values[1] * 1000).toLong()) == null)) {
                return@withContext "The clear margin must be smaller than the first level, and repeat at most an hour."
            }
            val next = if (values == null) tuning() - key else tuning() + (key to values)
            val o = JSONObject()
            next.forEach { (k, v) -> o.put(k, JSONArray(v)) }
            prefs().edit().putString(KEY_TUNING, o.toString()).apply()
            session?.alertTuning = next
            alertsChanged = true
            null
        }

    /** Battery packs are remembered per vehicle: the BLE address, or the transport for synthetic sources. */
    private fun packKey() = KEY_PACK_PREFIX + (address ?: transportName())

    private fun savedPack(): BatteryDetect.Candidate? {
        val raw = prefs().getString(packKey(), null) ?: return null
        val (chem, cells) = raw.split(':').takeIf { it.size == 2 } ?: return null
        val c = Chemistry.entries.firstOrNull { it.name == chem } ?: return null
        val n = cells.toIntOrNull()?.takeIf { it in PACK_CELLS } ?: return null
        return BatteryDetect.Candidate(c, n, 1.0)
    }

    /** Detection state for the battery screen; null while no session exists. */
    suspend fun batteryInfo(): Map<String, Any?>? = withContext(dispatcher) { session?.batteryInfo() }

    /**
     * Confirms or enters the pack ([chemistry] uses the frame's chemistry codes). Null
     * clears it and returns to auto-detection. Resolves to a plain reason when rejected.
     */
    suspend fun setPack(
        cells: Int?,
        chemistry: Int?,
    ): String? =
        withContext(dispatcher) {
            val pack =
                if (cells == null || chemistry == null) {
                    null
                } else {
                    val chem =
                        TelemetryFrame.CHEMISTRY_CODE.entries.firstOrNull { it.value == chemistry }?.key
                            ?: return@withContext "Unknown chemistry."
                    if (cells !in PACK_CELLS) return@withContext "Cells in series must be ${PACK_CELLS.first}..${PACK_CELLS.last}."
                    BatteryDetect.Candidate(chem, cells, 1.0)
                }
            val edit = prefs().edit()
            if (pack == null) edit.remove(packKey()) else edit.putString(packKey(), "${pack.chemistry.name}:${pack.cells}")
            edit.apply()
            // A different pack also resets capacity learning; the next tick persists that.
            session?.confirmedPack = pack
            null
        }

    private var savedCapacity = CapacityLearner.State()

    private fun capacityKey() = KEY_CAPACITY_PREFIX + (address ?: transportName())

    private fun loadCapacity(): CapacityLearner.State {
        val parts = prefs().getString(capacityKey(), null)?.split(':') ?: return CapacityLearner.State()
        val ah = parts.getOrNull(0)?.toDoubleOrNull()
        val soc = parts.getOrNull(1)?.toDoubleOrNull()
        val n = parts.getOrNull(2)?.toIntOrNull()
        return if (ah != null && soc != null && n != null) CapacityLearner.State(ah, soc, n) else CapacityLearner.State()
    }

    /** Persists capacity learning when it changed (runs on the alert tick). */
    private fun saveCapacity() {
        val st = session?.capacityState ?: return
        if (st == savedCapacity) return
        savedCapacity = st
        prefs().edit().putString(capacityKey(), "${st.sumAh}:${st.sumSoc}:${st.intervals}").apply()
    }

    private fun capacityAhKey() = KEY_CAPACITY_AH_PREFIX + (address ?: transportName())

    private fun savedCapacityAh(): Double? =
        prefs()
            .getString(capacityAhKey(), null)
            ?.toDoubleOrNull()
            ?.takeIf { it in CAPACITY_AH }

    /** Sets the pack capacity by hand (Ah), or null to go back to learning it; persisted per vehicle. */
    suspend fun setCapacity(ah: Double?): String? =
        withContext(dispatcher) {
            if (ah != null && ah !in CAPACITY_AH) {
                return@withContext "Capacity must be ${CAPACITY_AH.start.toInt()}..${CAPACITY_AH.endInclusive.toInt()} Ah."
            }
            val edit = prefs().edit()
            if (ah == null) edit.remove(capacityAhKey()) else edit.putString(capacityAhKey(), ah.toString())
            edit.apply()
            session?.capacityOverrideAh = ah
            null
        }

    private fun emptyCellKey() = KEY_EMPTY_CELL_PREFIX + (address ?: transportName())

    private fun savedEmptyCellV(): Double? =
        prefs()
            .getString(emptyCellKey(), null)
            ?.toDoubleOrNull()
            ?.takeIf { it in EMPTY_CELL_V }

    /** Sets the cell voltage shown as 0 %, or null for the chemistry's default; persisted per vehicle. */
    suspend fun setEmptyCell(v: Double?): String? =
        withContext(dispatcher) {
            if (v != null && v !in EMPTY_CELL_V) {
                return@withContext "Empty must be ${EMPTY_CELL_V.start}..${EMPTY_CELL_V.endInclusive} V per cell."
            }
            val edit = prefs().edit()
            if (v == null) edit.remove(emptyCellKey()) else edit.putString(emptyCellKey(), v.toString())
            edit.apply()
            session?.emptyCellOverrideV = v
            null
        }

    private fun rangeKey() = KEY_RANGE_PREFIX + (address ?: transportName())

    /** Wh/km of the last few rides on this vehicle, oldest first. */
    private fun rangeHistory(): List<Double> =
        prefs()
            .getString(rangeKey(), null)
            ?.split(',')
            ?.mapNotNull { it.toDoubleOrNull() }
            ?.filter { it.isFinite() && it > 0 } ?: emptyList()

    /** Remembers this session's Wh/km for the next session's first 500 m (2 km or more only). */
    private fun rememberRide() {
        val e = session?.rideWhPerKm ?: return
        val next = RangeEstimator.remember(rangeHistory(), e)
        prefs().edit().putString(rangeKey(), next.joinToString(",")).apply()
    }

    /** UI settings the app stores on the phone (keys under `ui.`), such as dashboard layouts. */
    suspend fun uiSetting(key: String): String? =
        withContext(dispatcher) { if (key.startsWith(UI_PREFIX)) prefs().getString(key, null) else null }

    suspend fun setUiSetting(
        key: String,
        value: String?,
    ) = withContext(dispatcher) {
        require(key.startsWith(UI_PREFIX) && (value?.length ?: 0) <= UI_MAX_CHARS) { "Not a UI setting." }
        val edit = prefs().edit()
        if (value == null) edit.remove(key) else edit.putString(key, value)
        edit.apply()
    }

    /** Posts a sample alert notification so the rider hears the sound. */
    fun testAlert() {
        AlertNotifier.post(context, ActiveAlert("test", null, Severity.WARNING, 0.0, 0L))
    }

    suspend fun dismissFaults() =
        withContext(dispatcher) {
            session?.dismissFaults()
            alertsChanged = true
        }

    private var alertsChanged = false

    private fun alertsMap(): Map<String, Any?> = mapOf("alerts" to (session?.activeAlerts ?: emptyList()).map { it.toMap() })

    /** Alerts run in the service at ~10 Hz whether or not the UI observes frames. */
    private fun evaluateAlerts(frame: TelemetryFrame?) {
        val s = session
        val changed = if (s != null && frame != null) s.evaluateAlerts(frame) else false
        if (!changed && !alertsChanged) return
        alertsChanged = false
        val active = s?.activeAlerts ?: emptyList()
        alertListener?.invoke(alertsMap())
        val loud = active.filter { it.notify && it.key !in silent }
        if (loud.isNotEmpty() && !AlertNotifier.inForeground()) loud.forEach { AlertNotifier.post(context, it) }
    }

    private fun scanMap(): Map<String, Any?> = mapOf("scanning" to scanner.scanning, "devices" to scanner.results())

    /** Frames at ~30 Hz while observed; session and scan events at ~5 Hz; notification on change. */
    private suspend fun tick() {
        var n = 0L
        var scanVersion = -1
        var lastNotice: StatusText? = null
        var lastNoticeState: ConnectionState? = null
        var lastNoticeRecording = false
        var lastNoticeMs = 0L
        while (scope.isActive) {
            delay(FRAME_PERIOD_MS)
            n++
            val alertTick = n % ALERT_EVERY == 0L
            val listener = frameListener
            val frame = if (listener != null || alertTick) session?.frame() else null
            if (listener != null && frame != null) listener(frame.toMap())
            if (alertTick) evaluateAlerts(frame)
            if (alertTick) session?.takeFinishedRun()?.let { rideLog.saveRun(it) }
            if (alertTick) saveCapacity()
            if (alertTick && frame != null && session?.snapshot?.value?.state == ConnectionState.CONNECTED) rideLog.autoTick(frame)
            if (n % SLOW_EVERY != 0L) continue
            val now = clock.nowMs()
            sessionListener?.invoke(sessionMap(now))
            if (scanner.version != scanVersion) {
                scanVersion = scanner.version
                scanListener?.invoke(scanMap())
            }
            val state = session?.snapshot?.value?.state
            // A ride stays open while the link is retried; it ends when the session stops or gives up.
            val ended = state != ConnectionState.CONNECTED && state != ConnectionState.RECONNECTING
            if (rideLog.recorder.recording && ended) rideLog.sessionEnded()
            if (serviceRunning && state == ConnectionState.LOST) stopService()
            if (serviceRunning) {
                val text = statusText()
                val recording = rideLog.recorder.recording
                val changed = state != lastNoticeState || recording != lastNoticeRecording
                if (text != lastNotice && (changed || now - lastNoticeMs > NOTICE_MS)) {
                    Notifier.update(context, text, rideElapsedMs(now), sessionOpen())
                    lastNotice = text
                    lastNoticeState = state
                    lastNoticeRecording = recording
                    lastNoticeMs = now
                }
            }
        }
    }

    private const val FRAME_PERIOD_MS = 33L
    private const val SLOW_EVERY = 6L
    private const val ALERT_EVERY = 3L
    const val PREFS = "vesc-monitor"
    private const val KEY_DISABLED = "alerts.disabled"
    private const val KEY_SILENT = "alerts.silent"
    private const val UNITS_KEY = "ui.units"
    private const val KEY_THRESHOLDS = "alerts.thresholds"
    private const val KEY_TUNING = "alerts.tuning"
    private const val KEY_PACK_PREFIX = "battery.pack."
    private const val KEY_CAPACITY_PREFIX = "battery.capacity."
    private const val KEY_RANGE_PREFIX = "battery.range."
    private const val KEY_CAPACITY_AH_PREFIX = "battery.capacityAh."
    private val CAPACITY_AH = 1.0..500.0
    private const val KEY_EMPTY_CELL_PREFIX = "battery.emptyCellV."
    private val EMPTY_CELL_V = 2.5..3.7
    private const val UI_PREFIX = "ui."
    private const val UI_MAX_CHARS = 64_000
    private val PACK_CELLS = 3..36
    private const val NOTICE_MS = 10_000L
    private const val SHUTDOWN_TIMEOUT_MS = 3_000L
}
