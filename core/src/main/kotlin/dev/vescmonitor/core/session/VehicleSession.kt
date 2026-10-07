package dev.vescmonitor.core.session

import dev.vescmonitor.core.alert.ActiveAlert
import dev.vescmonitor.core.alert.AlertEngine
import dev.vescmonitor.core.battery.BatteryDetect
import dev.vescmonitor.core.battery.CapacityLearner
import dev.vescmonitor.core.frame.FrameBuilder
import dev.vescmonitor.core.frame.TelemetryFrame
import dev.vescmonitor.core.link.BleTransport
import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.link.Link
import dev.vescmonitor.core.link.LinkException
import dev.vescmonitor.core.link.LinkState
import dev.vescmonitor.core.link.Reason
import dev.vescmonitor.core.run.Bracket
import dev.vescmonitor.core.run.RunContext
import dev.vescmonitor.core.run.RunReport
import dev.vescmonitor.core.run.RunTimer
import dev.vescmonitor.core.speed.SpeedCombiner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Hooks for the recorder; called on the session thread. */
interface SessionListener {
    /** Discovery finished (again after a reconnect); polling starts for these controllers. */
    suspend fun onPolling(controllers: List<ControllerLive>)

    /** A new sample of controller [index]; its values are in [c]. */
    fun onSample(
        index: Int,
        c: ControllerLive,
    )
}

/**
 * One connection: connect → discovery → polling. With [reconnect] on, a link that was
 * connected once and drops for a recoverable reason goes to RECONNECTING and retries with
 * [Reconnect.backoffMs] until it is back or the user stops; otherwise it goes to LOST with
 * a reason and stops. Run on a single-thread dispatcher.
 */
class VehicleSession(
    private val transport: BleTransport,
    private val clock: Clock,
    private val scope: CoroutineScope,
    val generation: Int,
    private val config: PollConfig = PollConfig(),
    private val listener: SessionListener? = null,
    private val reconnect: Boolean = false,
) {
    private val _snapshot = MutableStateFlow(SessionSnapshot(generation, ConnectionState.IDLE, config = config))
    val snapshot: StateFlow<SessionSnapshot> = _snapshot

    private val frames = FrameBuilder(generation)
    private val alerts = AlertEngine()
    private val run = RunTimer()
    private var link: Link? = null
    private var controllers: List<ControllerLive> = emptyList()
    private var job: Job? = null

    /** The app is on screen: the slow retry tier is not used while it is. */
    @Volatile
    var visible: Boolean = true

    fun start(): Job {
        val j = scope.launch { runSession() }
        job = j
        return j
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        transport.disconnect()
        update(ConnectionState.IDLE, Reason.USER_DISCONNECT)
    }

    /** Current frame; null until the topology is known. Also runs the speed-test clock. */
    fun frame(): TelemetryFrame? {
        if (controllers.isEmpty()) return null
        val now = clock.nowMs()
        run.tick(now)
        return frames.build(controllers, now)
    }

    /** Arms a speed-test run; returns a plain reason when it cannot. */
    fun armRun(brackets: List<Bracket>? = null): String? {
        if (_snapshot.value.state != ConnectionState.CONNECTED) return "Connect to the vehicle first."
        if (controllers.none { it.speed.valid }) {
            return "Speed is unknown: set the wheel size in VESC Tool, or spin the wheel once so the app can learn it."
        }
        run.arm(clock.nowMs(), brackets)
        return null
    }

    fun cancelRun() = run.cancel()

    private var reportedRun = 0

    /** A run that just finished (DONE), once per run, with its curve; null otherwise. */
    fun takeFinishedRun(): Map<String, Any?>? {
        if (run.state != RunTimer.State.DONE || run.runNumber == reportedRun) return null
        reportedRun = run.runNumber
        return RunReport.toSaved(run, clock.nowMs(), controllers.mapNotNull { it.speed.k.takeUnless(Double::isNaN) })
    }

    /** Run state for the session event. */
    fun runMap(nowMs: Long): Map<String, Any?> =
        RunReport.toMap(run, nowMs, controllers.mapNotNull { it.speed.k.takeUnless(Double::isNaN) })

    /** Feeds the run timer with the combined motor speed at this sample's time. */
    private fun feedRun() {
        if (run.state == RunTimer.State.IDLE || run.state == RunTimer.State.DONE || run.state == RunTimer.State.ABORTED) return
        val now = clock.nowMs()
        val fresh = controllers.filter { it.isFresh(now) }
        val speeds = fresh.map { it.speedMps }.filterNot(Double::isNaN)
        if (speeds.isEmpty()) return
        val (v, slip) = SpeedCombiner.pick(speeds, accelerating = true)
        val ctx =
            RunContext(
                allFresh = fresh.size == controllers.size && speeds.size == fresh.size,
                fault = fresh.any { it.values.faultCode != 0 },
                powerW = fresh.sumOf { it.values.powerW }.takeUnless(Double::isNaN),
                voltageV =
                    fresh
                        .map { it.voltageV }
                        .filterNot(Double::isNaN)
                        .takeIf { it.isNotEmpty() }
                        ?.average(),
                tempMosC = fresh.map { it.values.tempMosC }.filterNot(Double::isNaN).maxOrNull(),
                tempMotorC = fresh.map { it.values.tempMotorC }.filterNot(Double::isNaN).maxOrNull(),
                slip = slip,
            )
        run.onSample(now, v, ctx)
    }

    /** Runs the alert catalog on [frame]; true when the active list changed. */
    fun evaluateAlerts(frame: TelemetryFrame): Boolean {
        val cut = controllers.firstOrNull { it.controller.isLocal }?.batteryCut ?: controllers.firstNotNullOfOrNull { it.batteryCut }
        return alerts.evaluate(frame, cut, clock.nowMs())
    }

    val activeAlerts: List<ActiveAlert> get() = alerts.active

    /** Alert keys the rider switched off. */
    var disabledAlerts: Set<String>
        get() = alerts.disabled
        set(value) {
            alerts.disabled = value
        }

    /** The pack the rider confirmed; null follows auto-detection. */
    var confirmedPack: BatteryDetect.Candidate?
        get() = frames.confirmedPack
        set(value) {
            frames.confirmedPack = value
        }

    fun batteryInfo(): Map<String, Any?> = frames.batteryInfo()

    /** Capacity learning sums, persisted per vehicle by the app. */
    var capacityState: CapacityLearner.State
        get() = frames.capacityState
        set(value) {
            frames.capacityState = value
        }

    /** Cell voltage the rider set as empty, persisted per vehicle by the app. */
    var emptyCellOverrideV: Double?
        get() = frames.emptyCellOverrideV
        set(value) {
            frames.emptyCellOverrideV = value
        }

    /** Pack capacity the rider entered (Ah), persisted per vehicle by the app. */
    var capacityOverrideAh: Double?
        get() = frames.capacityOverrideAh
        set(value) {
            frames.capacityOverrideAh = value
        }

    /** Median Wh/km of past rides on this vehicle, persisted by the app. */
    var rangePrior: Double?
        get() = frames.rangePrior
        set(value) {
            frames.rangePrior = value
        }

    /** This session's Wh/km once long enough to remember, else null. */
    val rideWhPerKm: Double? get() = frames.rideWhPerKm

    /** The rider's alert hysteresis and repeat time per key, as [hysteresis, cooldown s]. */
    var alertTuning: Map<String, List<Double>>
        get() = alerts.tuning
        set(value) {
            alerts.tuning = value
        }

    /** The rider's alert thresholds per key. */
    var alertThresholds: Map<String, List<Double>>
        get() = alerts.thresholds
        set(value) {
            alerts.thresholds = value
        }

    /** The rider dismissed the latched faults. */
    fun dismissFaults() = alerts.dismissFaults()

    /** Refreshes the counters in the snapshot (call at the session event rate). */
    fun refresh() {
        _snapshot.value = _snapshot.value.copy(counters = link?.counters, mtu = transport.mtu)
    }

    private suspend fun runSession() {
        update(ConnectionState.CONNECTING, null)
        var everConnected = false
        var failures = 0
        while (true) {
            val (reason, connected) = attempt()
            everConnected = everConnected || connected
            if (connected) failures = 0
            if (!reconnect || !everConnected || reason !in Reconnect.RETRYABLE) {
                fail(reason)
                return
            }
            transport.disconnect()
            failures++
            _snapshot.value = _snapshot.value.copy(state = ConnectionState.RECONNECTING, reason = reason, attempt = failures)
            delay(Reconnect.backoffMs(failures, visible))
        }
    }

    /** One connect → discovery → polling pass; returns why it ended and whether it got to polling. */
    private suspend fun attempt(): Pair<Reason, Boolean> {
        try {
            transport.connect()
        } catch (e: LinkException) {
            return e.reason to false
        }
        val l = Link(transport, clock, config.depth)
        link = l
        var reason = Reason.LINK_LOST
        var connected = false
        val readerJob = l.start(scope)
        val work =
            scope.launch {
                try {
                    progress(SetupProgress(SetupStep.FIRMWARE, null, null, 0))
                    when (val result = Discovery(l, ::progress).run()) {
                        is DiscoveryResult.Failed -> {
                            reason = result.reason
                        }

                        is DiscoveryResult.Found -> {
                            connected = true
                            poll(l, result.topology)
                        }
                    }
                } catch (e: LinkException) {
                    reason = e.reason
                }
            }
        val watchJob =
            scope.launch {
                reason = (transport.state.first { it is LinkState.Lost } as LinkState.Lost).reason
                work.cancel()
            }
        // Stale watchdog: polling but no valid reply from any controller for a while → rebuild the link.
        val staleJob =
            if (!reconnect) {
                null
            } else {
                scope.launch {
                    // The clock starts when polling starts: discovery has its own timeouts.
                    var since: Long? = null
                    while (true) {
                        delay(Reconnect.STALE_CHECK_MS)
                        if (!connected) continue
                        val start = since ?: clock.nowMs().also { since = it }
                        val newest = controllers.maxOfOrNull { it.lastSampleMs } ?: ControllerLive.NEVER
                        if (clock.nowMs() - maxOf(newest, start) > Reconnect.STALE_MS) {
                            reason = Reason.VESC_NOT_ANSWERING
                            work.cancel()
                            return@launch
                        }
                    }
                }
            }
        try {
            work.join()
        } finally {
            work.cancel()
            staleJob?.cancel()
            watchJob.cancel()
            readerJob.cancel()
        }
        return reason to connected
    }

    private fun progress(p: SetupProgress) {
        val s = _snapshot.value
        _snapshot.value =
            s.copy(
                setupStep = p.step,
                setupFound = p.found,
                firmware = p.firmware ?: s.firmware,
                hardware = p.hardware ?: s.hardware,
                mtu = transport.mtu,
            )
    }

    private suspend fun poll(
        l: Link,
        topology: Topology,
    ) {
        controllers = topology.controllers.map { ControllerLive(it) }
        _snapshot.value =
            _snapshot.value.copy(
                state = ConnectionState.CONNECTED,
                reason = null,
                firmware = topology.bridgeNode.label,
                hardware = topology.bridgeNode.hwName,
                controllers = controllers,
                mtu = transport.mtu,
                attempt = 0,
                setupStep = null,
                setupFound = controllers.size,
                otherNodes = topology.otherNodes,
            )
        listener?.onPolling(controllers)
        val index = controllers.withIndex().associate { (i, c) -> c to i }
        PollScheduler(l, clock, controllers, config) { c ->
            listener?.onSample(index.getValue(c), c)
            feedRun()
        }.run()
    }

    private suspend fun fail(reason: Reason) {
        update(ConnectionState.LOST, reason)
        transport.disconnect()
    }

    private fun update(
        state: ConnectionState,
        reason: Reason?,
    ) {
        _snapshot.value = _snapshot.value.copy(state = state, reason = reason)
    }
}
