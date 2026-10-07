package dev.vescmonitor.core.session

import dev.vescmonitor.core.protocol.BatteryCut
import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.protocol.ValuesSample
import dev.vescmonitor.core.ride.EnergyCounter
import dev.vescmonitor.core.speed.SpeedRatio
import kotlin.math.max

/** Latest values and bench numbers of one controller. Owned by the session thread. */
class ControllerLive(
    val controller: Controller,
) {
    val values = LiveValues()
    val voltageV: Double get() = values.voltageV
    var lastSampleMs = NEVER
        private set

    /** Set after a timeout and its retry; cleared by the next valid reply. */
    var stale = false
        internal set
    var intervalEwmaMs = Double.NaN
        private set
    var batteryCut: BatteryCut? = null
        internal set
    var untestedFirmware = false
        internal set

    internal var lastSlowMs = NEVER
    internal var lastSetupMs = NEVER
    internal var nextDueMs = 0L
    internal var busy = false

    val bench = BenchStats()

    /** Learned speed ratio from SETUP replies; session-scoped like the counters below. */
    val speed = SpeedRatio()

    /** Session distance (m) and energy (Wh) from reset-safe counter deltas. */
    val distanceM = EnergyCounter(EPS_DISTANCE_M)
    val whUsed = EnergyCounter(EnergyCounter.EPS_WH)
    val whCharged = EnergyCounter(EnergyCounter.EPS_WH)
    val ahUsed = EnergyCounter(EnergyCounter.EPS_AH)
    val ahCharged = EnergyCounter(EnergyCounter.EPS_AH)

    /** Speed from the latest fast ERPM; NaN when the ratio is not trusted. */
    val speedMps: Double get() = speed.speedMps(values.erpm)

    val measuredHz: Double get() = if (intervalEwmaMs.isNaN() || intervalEwmaMs <= 0) 0.0 else 1000.0 / intervalEwmaMs

    /** Fresh if the last sample is at most max(1 s, 5 missed polls) old and no timeout since. */
    fun isFresh(nowMs: Long): Boolean {
        if (stale || lastSampleMs == NEVER) return false
        val interval = if (intervalEwmaMs.isNaN()) 0.0 else intervalEwmaMs
        return nowMs - lastSampleMs <= max(FRESH_MIN_MS, FRESH_MISSED_POLLS * interval)
    }

    internal fun onSample(
        nowMs: Long,
        sample: ValuesSample,
    ) {
        if (lastSampleMs != NEVER) {
            val dt = (nowMs - lastSampleMs).toDouble()
            intervalEwmaMs = if (intervalEwmaMs.isNaN()) dt else EWMA_ALPHA * dt + (1 - EWMA_ALPHA) * intervalEwmaMs
        }
        lastSampleMs = nowMs
        values.apply(sample)
        if (sample.layout === ValuesLayout.SETUP) {
            speed.onSetup(values.vescSpeedMps, values.vescSpeedErpm)
            distanceM.add(values.distanceAbsM)
        } else {
            whUsed.add(values.wattHours)
            whCharged.add(values.wattHoursCharged)
            ahUsed.add(values.ampHours)
            ahCharged.add(values.ampHoursCharged)
        }
        stale = false
    }

    companion object {
        const val NEVER = Long.MIN_VALUE
        const val FRESH_MIN_MS = 1_000.0
        const val FRESH_MISSED_POLLS = 5.0
        const val EWMA_ALPHA = 0.2
        const val EPS_DISTANCE_M = 0.5
    }
}

/** Per-controller BLE bench numbers (EWMA where averaged). */
class BenchStats {
    var requests = 0L
        private set
    var timeouts = 0L
        private set
    var writeMs = 0.0
        private set
    var rttMs = 0.0
        private set
    var notificationsPerReply = 0.0
        private set
    var notificationSpanMs = 0.0
        private set

    internal fun onReply(
        writeMs: Long,
        rttMs: Long,
        notifications: Int,
        spanMs: Long,
    ) {
        requests++
        this.writeMs = ewma(this.writeMs, writeMs.toDouble())
        this.rttMs = ewma(this.rttMs, rttMs.toDouble())
        notificationsPerReply = ewma(notificationsPerReply, notifications.toDouble())
        notificationSpanMs = ewma(notificationSpanMs, spanMs.toDouble())
    }

    internal fun onTimeout() {
        requests++
        timeouts++
    }

    private fun ewma(
        old: Double,
        new: Double,
    ): Double = if (requests <= 1) new else ControllerLive.EWMA_ALPHA * new + (1 - ControllerLive.EWMA_ALPHA) * old
}
