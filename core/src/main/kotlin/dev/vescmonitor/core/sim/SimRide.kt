package dev.vescmonitor.core.sim

import kotlin.math.exp
import kotlin.math.min

/**
 * A repeating synthetic ride: accelerate, cruise, brake with regen, stand still.
 * Deterministic in time so tests stay stable. Values are per controller.
 */
class SimRide(
    /** Scales currents and ERPM, so two controllers differ slightly. */
    private val gain: Double = 1.0,
    private val offsetMs: Long = 0,
) {
    data class Point(
        val duty: Double,
        val erpm: Double,
        val currentMotorA: Double,
        val currentInA: Double,
    )

    fun at(nowMs: Long): Point {
        val t = ((nowMs + offsetMs) % PERIOD_MS) / 1000.0
        return when {
            t < 8 -> {
                val f = t / 8
                Point(0.8 * f, MAX_ERPM * f * gain, (60 - 40 * f) * gain, (40 * f + 5) * gain)
            }

            t < 18 -> {
                Point(0.62, MAX_ERPM * 0.95 * gain, 15 * gain, 10 * gain)
            }

            t < 24 -> {
                val f = (t - 18) / 6
                Point(0.62 * (1 - f), MAX_ERPM * 0.95 * (1 - f) * gain, -30 * gain, -10 * gain)
            }

            else -> {
                Point(0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    /** Speed the synthetic VESC reports from its "configured" wheel: a fixed ratio of ERPM. */
    fun speedMps(nowMs: Long): Double = at(nowMs).erpm * SPEED_PER_ERPM

    /** Distance since t = 0, integrated in 100 ms steps (whole periods are precomputed). */
    fun distanceM(nowMs: Long): Double {
        val periods = (nowMs + offsetMs) / PERIOD_MS
        val rest = (nowMs + offsetMs) % PERIOD_MS
        return periods * periodDistanceM + integrate(0, rest) - integrate(0, offsetMs % PERIOD_MS)
    }

    private val periodDistanceM: Double by lazy { integrate(0, PERIOD_MS) }

    private fun integrate(
        fromMs: Long,
        toMs: Long,
    ): Double {
        var d = 0.0
        var t = fromMs
        while (t < toMs) {
            d += (at(t - offsetMs).erpm * SPEED_PER_ERPM) * STEP_MS / 1000.0
            t += STEP_MS
        }
        return d
    }

    /** Temperatures rise towards a plateau over minutes, like a real ride. */
    fun tempMosC(nowMs: Long): Double = 30 + 25 * warm(nowMs)

    fun tempMotorC(nowMs: Long): Double = 30 + 45 * warm(nowMs) * gain

    private fun warm(nowMs: Long): Double = 1 - exp(-min(nowMs, WARM_CAP_MS) / WARM_TAU_MS)

    companion object {
        const val PERIOD_MS = 30_000L
        const val MAX_ERPM = 30_000.0

        /** m/s per ERPM of the synthetic vehicle (about 60 km/h at full ERPM). */
        const val SPEED_PER_ERPM = 5.5e-4
        private const val STEP_MS = 100L

        /** Internal resistance used for the synthetic sag (ohm). */
        const val SAG_OHM = 0.08
        private const val WARM_TAU_MS = 600_000.0
        private const val WARM_CAP_MS = 3_600_000L
    }
}
