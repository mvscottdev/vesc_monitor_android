package dev.vescmonitor.core.frame

import dev.vescmonitor.core.battery.BatteryDetect
import dev.vescmonitor.core.battery.CapacityLearner
import dev.vescmonitor.core.battery.Chemistry
import dev.vescmonitor.core.battery.Ocv
import dev.vescmonitor.core.battery.RangeEstimator
import dev.vescmonitor.core.battery.SagTracker
import dev.vescmonitor.core.battery.SocEstimator
import dev.vescmonitor.core.session.ControllerLive
import dev.vescmonitor.core.speed.SpeedCombiner
import kotlin.math.abs

/** Speed, trip, battery and sag values derived for one session. */
data class Derived(
    val speedMps: Double? = null,
    val speedValid: Boolean = false,
    /** A controller reports zero speed while turning or the default wheel: set it in VESC Tool. */
    val wheelMissing: Boolean = false,
    val slip: Boolean = false,
    val maxSpeedMps: Double = 0.0,
    val tripM: Double = 0.0,
    val whUsed: Double = 0.0,
    val whPerKm: Double? = null,
    val socPct: Double? = null,
    val cellV: Double? = null,
    val cells: Int? = null,
    val chemistry: Chemistry? = null,
    val socConfidence: BatteryDetect.Confidence? = null,
    val batteryConfirm: Boolean = false,
    val sagV: Double? = null,
    val restRefV: Double? = null,
    val minVoltageV: Double? = null,
    /** Learned pack capacity; null while learning. */
    val capacityAh: Double? = null,
    /** Range on the energy left at the blended consumption; null while learning. */
    val rangeKm: Double? = null,
) {
    companion object {
        val EMPTY = Derived()
    }
}

/**
 * Runs the estimators over the combined values at frame rate. Owned by the frame builder;
 * everything resets with the session.
 */
class Derivations {
    private val speed = SpeedCombiner()
    private val sag = SagTracker()
    private var maxSpeed = 0.0
    private var detect: BatteryDetect.Result? = null
    private var auto: BatteryDetect.Result? = null
    private var evidence: BatteryDetect.Evidence? = null
    private var soc: SocEstimator? = null
    private var detectedAt = Double.NaN
    private var lastMs = Long.MIN_VALUE
    private var learner = CapacityLearner()
    private var learnedAt = Double.NaN
    private val range = RangeEstimator()

    /** Capacity learning sums; the caller persists them across sessions. */
    var capacityState: CapacityLearner.State
        get() = learner.state
        set(value) {
            learner = CapacityLearner(value)
        }

    /** Cell voltage the rider set as empty (0 %); null uses the chemistry's default. */
    var emptyCellOverrideV: Double? = null

    /** Pack capacity the rider entered (Ah); replaces the learned one while set. */
    var capacityOverrideAh: Double? = null

    /** Median Wh/km of past rides on this vehicle; range uses it for the first 500 m. */
    var rangePrior: Double?
        get() = range.prior
        set(value) {
            range.prior = value
        }

    /** This session's Wh/km once long enough to remember, else null. */
    val rideWhPerKm: Double? get() = range.rideWhPerKm

    /** The pack the rider confirmed or entered; replaces auto-detection while set. */
    var confirmedPack: BatteryDetect.Candidate? = null
        set(value) {
            field = value
            use(value?.let(::confirmedResult) ?: auto)
        }

    /** Detection state for the battery screen: guess, confidence, alternatives, evidence. Numbers only. */
    fun batteryInfo(): Map<String, Any?> {
        val a = auto
        val e = evidence
        return mapOf(
            "confirmed" to confirmedPack?.let(::candidateMap),
            "detected" to a?.let { candidateMap(it.best) },
            "confidence" to a?.let { it.confidence.ordinal + 1 },
            "confirmChemistry" to if (a?.confirmChemistry == true) 1 else 0,
            "alternatives" to (a?.alternatives ?: emptyList()).map(::candidateMap),
            "vRest" to e?.vRest,
            "vMaxSeen" to e?.vMaxSeen,
            "cutStartV" to e?.cutStartV,
            "cutEndV" to e?.cutEndV,
            "capacityAh" to capacityOverrideAh,
            "emptyCellV" to emptyCellOverrideV,
            "defaultEmptyCellV" to (confirmedPack?.chemistry ?: a?.best?.chemistry)?.emptyCellV,
            "learnedAh" to learner.capacityAh,
            "learnIntervals" to learner.state.intervals,
            "learnNeeded" to CapacityLearner.MIN_INTERVALS,
        )
    }

    private fun candidateMap(c: BatteryDetect.Candidate): Map<String, Any?> =
        mapOf("cells" to c.cells, "chemistry" to TelemetryFrame.CHEMISTRY_CODE.getValue(c.chemistry), "p" to c.p)

    private fun confirmedResult(c: BatteryDetect.Candidate) =
        BatteryDetect.Result(c.copy(p = 1.0), emptyList(), BatteryDetect.Confidence.HIGH, confirmChemistry = false)

    private fun use(r: BatteryDetect.Result?) {
        val prev = detect
        detect = r
        if (r == null) {
            soc = null
        } else if (prev == null || prev.best.cells != r.best.cells || prev.best.chemistry != r.best.chemistry) {
            soc = SocEstimator(r.best.chemistry, r.best.cells)
            // Capacity learned against another pack's SoC table no longer applies.
            if (prev != null) learner = CapacityLearner()
        }
    }

    fun update(
        controllers: List<ControllerLive>,
        fresh: List<ControllerLive>,
        voltageV: Double?,
        currentInA: Double?,
        nowMs: Long,
    ): Derived {
        val dt = if (lastMs == Long.MIN_VALUE) 0.0 else (nowMs - lastMs) / 1000.0
        lastMs = nowMs

        val speeds = fresh.map { it.speedMps }
        val s = speed.combine(speeds, nowMs)
        s.speedMps?.let { maxSpeed = maxOf(maxSpeed, abs(it)) }
        val wheelMissing = controllers.any { it.speed.vescSpeedMissing || (!it.speed.k.isNaN() && !it.speed.valid) }

        // Trip: mean of per-controller distance deltas (each controller sees the same road).
        val withDistance = controllers.filter { it.distanceM.total > 0 }
        val trip = if (withDistance.isEmpty()) 0.0 else withDistance.sumOf { it.distanceM.total } / withDistance.size
        val wh = controllers.sumOf { it.whUsed.total - it.whCharged.total }
        val whPerKm = if (trip >= MIN_TRIP_M) wh / (trip / 1000) else null

        range.update(trip, wh, s.speedMps?.let(::abs))

        var socPct: Double? = null
        if (voltageV != null && currentInA != null && fresh.size == controllers.size) {
            sag.update(voltageV, currentInA, nowMs)
            redetect(voltageV, currentInA, controllers)
            learn(controllers.sumOf { it.ahUsed.total - it.ahCharged.total })
            soc?.let { est ->
                est.emptyCellV = emptyCellOverrideV ?: est.chemistry.emptyCellV
                if (dt > 0 &&
                    dt < MAX_DT_S
                ) {
                    est.update(voltageV, currentInA, dt)
                } else if (est.socAbs == null) {
                    est.update(voltageV, currentInA, 0.1)
                }
                socPct = est.displaySoc
            }
        } else {
            soc?.let { socPct = it.displaySoc }
        }
        val d = detect
        val cap = capacityOverrideAh ?: learner.capacityAh
        val e = range.whPerKm
        val socAbs = soc?.socAbs
        val rangeKm =
            if (d != null && cap != null && e != null && socAbs != null) {
                val empty = emptyCellOverrideV ?: d.best.chemistry.emptyCellV
                RangeEstimator.rangeKm(RangeEstimator.whRemaining(d.best.chemistry, d.best.cells, cap, socAbs, empty), e)
            } else {
                null
            }
        return Derived(
            speedMps = s.speedMps,
            speedValid = s.speedMps != null,
            wheelMissing = wheelMissing,
            slip = s.slip,
            maxSpeedMps = maxSpeed,
            tripM = trip,
            whUsed = wh,
            whPerKm = range.whPerKm ?: whPerKm,
            socPct = socPct,
            cellV = if (d != null && voltageV != null) voltageV / d.best.cells else null,
            cells = d?.best?.cells,
            chemistry = d?.best?.chemistry,
            socConfidence = d?.confidence,
            batteryConfirm = d?.confirmChemistry ?: false,
            sagV = if (sag.restRefV != null && voltageV != null) sag.sagV else null,
            restRefV = sag.restRefV,
            minVoltageV = sag.minVoltageV,
            capacityAh = cap,
            rangeKm = rangeKm,
        )
    }

    /** Feeds capacity learning once per settled rest of a known pack. */
    private fun learn(ahNet: Double) {
        val rest = sag.settledRestV ?: return
        if (rest == learnedAt) return
        learnedAt = rest
        val d = detect ?: return
        learner.onRest(Ocv.socOf(d.best.chemistry, rest / d.best.cells), ahNet)
    }

    /** Detects on the first quiet reading, then again on each settled rest or new maximum. */
    private fun redetect(
        v: Double,
        i: Double,
        controllers: List<ControllerLive>,
    ) {
        val restV = sag.settledRestV ?: if (detect == null && abs(i) < FIRST_REST_A) v else null
        if (restV == null || restV == detectedAt) return
        detectedAt = restV
        val cut = controllers.firstOrNull { it.controller.isLocal }?.batteryCut ?: controllers.firstNotNullOfOrNull { it.batteryCut }
        val ev =
            BatteryDetect.Evidence(
                vRest = restV,
                vMaxSeen = sag.maxRestV,
                cutStartV = cut?.startV,
                cutEndV = cut?.endV,
            )
        val r = BatteryDetect.detect(ev) ?: return
        evidence = ev
        auto = r
        if (confirmedPack == null) use(r)
    }

    companion object {
        const val FIRST_REST_A = 1.0
        const val MIN_TRIP_M = 200.0
        const val MAX_DT_S = 2.0
    }
}
