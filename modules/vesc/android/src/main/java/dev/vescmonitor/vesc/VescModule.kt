package dev.vescmonitor.vesc

import dev.vescmonitor.core.session.PollConfig
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * JS bridge only: typed intents in, `telemetry` (≤ 30 Hz, latest-wins, only while
 * observed), `session` and `scan` (≤ 5 Hz) and `alerts` (on change) events out. No logic lives here.
 */
class VescModule : Module() {
    override fun definition() =
        ModuleDefinition {
            Name("Vesc")

            Events(TELEMETRY, SESSION, SCAN, ALERTS)

            OnCreate {
                appContext.reactContext?.let { SessionHub.init(it) }
            }

            OnStartObserving(TELEMETRY) { SessionHub.frameListener = { sendEvent(TELEMETRY, it) } }
            OnStopObserving(TELEMETRY) { SessionHub.frameListener = null }
            OnStartObserving(SESSION) { SessionHub.sessionListener = { sendEvent(SESSION, it) } }
            OnStopObserving(SESSION) { SessionHub.sessionListener = null }
            OnStartObserving(SCAN) { SessionHub.scanListener = { sendEvent(SCAN, it) } }
            OnStopObserving(SCAN) { SessionHub.scanListener = null }
            OnStartObserving(ALERTS) { SessionHub.alertListener = { sendEvent(ALERTS, it) } }
            OnStopObserving(ALERTS) { SessionHub.alertListener = null }

            AsyncFunction("getLiveState") Coroutine { -> SessionHub.liveState() }

            AsyncFunction("scan") Coroutine { on: Boolean -> SessionHub.scan(on) }

            AsyncFunction("connect") Coroutine { address: String? -> SessionHub.connect(address) }

            AsyncFunction("disconnect") Coroutine { -> SessionHub.disconnect() }

            AsyncFunction("setTransport") Coroutine { kind: String, controllers: Int?, path: String? ->
                val choice =
                    when (kind) {
                        "synthetic" -> TransportChoice.Synthetic((controllers ?: 1).coerceIn(1, 2))
                        "replay" -> TransportChoice.Replay(requireNotNull(path) { "replay needs a file" })
                        else -> TransportChoice.Ble
                    }
                SessionHub.setTransport(choice)
            }

            AsyncFunction("setPollConfig") Coroutine { selective: Boolean, depth: Int, forwardLocal: Boolean ->
                SessionHub.setPollConfig(PollConfig(selective = selective, depth = depth.coerceIn(1, 2), forwardLocal = forwardLocal))
            }

            AsyncFunction("startRecording") Coroutine { -> SessionHub.startRecording() }

            AsyncFunction("stopRecording") Coroutine { -> SessionHub.stopRecording() }

            AsyncFunction("listRecordings") Coroutine { -> SessionHub.recordings() }

            AsyncFunction("setLogging") Coroutine { on: Boolean -> SessionHub.setLogging(on) }

            AsyncFunction("listRides") Coroutine { limit: Int -> SessionHub.rides(limit.coerceIn(1, 500)) }

            AsyncFunction("rideSeries") Coroutine { id: Long, buckets: Int -> SessionHub.rideSeries(id, buckets.coerceIn(1, 2_000)) }

            AsyncFunction("deleteRide") Coroutine { id: Long -> SessionHub.deleteRide(id) }

            AsyncFunction("dismissFaults") Coroutine { -> SessionHub.dismissFaults() }

            AsyncFunction("alertSettings") Coroutine { -> SessionHub.alertSettings() }

            AsyncFunction("setAlertEnabled") Coroutine { key: String, on: Boolean -> SessionHub.setAlertEnabled(key, on) }

            AsyncFunction("silentAlerts") Coroutine { -> SessionHub.silentAlertKeys() }

            AsyncFunction("setAlertSilent") Coroutine { key: String, silent: Boolean -> SessionHub.setAlertSilent(key, silent) }

            AsyncFunction("setVisible") Coroutine { visible: Boolean -> SessionHub.setVisible(visible) }

            AsyncFunction("setCapacity") Coroutine { ah: Double? -> SessionHub.setCapacity(ah) }

            AsyncFunction("setEmptyCell") Coroutine { v: Double? -> SessionHub.setEmptyCell(v) }

            AsyncFunction("alertThresholds") Coroutine { -> SessionHub.alertThresholds() }

            AsyncFunction("alertTuning") Coroutine { -> SessionHub.alertTuning() }

            AsyncFunction("setAlertTuning") Coroutine { key: String, values: List<Double>? -> SessionHub.setAlertTuning(key, values) }

            AsyncFunction("setAlertThresholds") Coroutine { key: String, values: List<Double>? -> SessionHub.setAlertThresholds(key, values) }

            AsyncFunction("batteryInfo") Coroutine { -> SessionHub.batteryInfo() }

            AsyncFunction("setPack") Coroutine { cells: Int?, chemistry: Int? -> SessionHub.setPack(cells, chemistry) }

            AsyncFunction("uiSetting") Coroutine { key: String -> SessionHub.uiSetting(key) }

            AsyncFunction("setUiSetting") Coroutine { key: String, value: String? -> SessionHub.setUiSetting(key, value) }

            AsyncFunction("backgroundStatus") { -> appContext.reactContext?.let { BackgroundStatus.check(it) } }

            AsyncFunction("openBatterySettings") { -> appContext.reactContext?.let { BackgroundStatus.openBatterySettings(it) } ?: false }

            AsyncFunction("testAlert") { -> SessionHub.testAlert() }

            AsyncFunction("armRun") Coroutine { -> SessionHub.armRun() }

            AsyncFunction("cancelRun") Coroutine { -> SessionHub.cancelRun() }

            AsyncFunction("listRuns") Coroutine { limit: Int -> SessionHub.runs(limit.coerceIn(1, 500)) }

            AsyncFunction("deleteRun") Coroutine { id: Long -> SessionHub.deleteRun(id) }
        }

    companion object {
        private const val TELEMETRY = "telemetry"
        private const val SESSION = "session"
        private const val SCAN = "scan"
        private const val ALERTS = "alerts"
    }
}
