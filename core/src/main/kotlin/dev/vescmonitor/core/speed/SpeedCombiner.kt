package dev.vescmonitor.core.speed

import kotlin.math.abs
import kotlin.math.max

/**
 * Combines the fresh controllers' speeds (speed.md, combining controllers): agreeing
 * speeds give the mean; beyond the tolerance the lower one while accelerating, the
 * higher one while braking (the median with 3+), and a slip flag held for 0.5 s.
 */
class SpeedCombiner {
    private var lastMps = Double.NaN
    private var slipUntilMs = Long.MIN_VALUE

    data class Result(
        val speedMps: Double?,
        val slip: Boolean,
    )

    fun combine(
        fresh: List<Double>,
        nowMs: Long,
    ): Result {
        val v = fresh.filterNot { it.isNaN() }
        if (v.isEmpty()) {
            lastMps = Double.NaN
            return Result(null, false)
        }
        val mean = v.average()
        val accelerating = lastMps.isNaN() || abs(mean) >= abs(lastMps)
        val picked = pick(v, mean, accelerating)
        if (picked.second) slipUntilMs = nowMs + SLIP_HOLD_MS
        lastMps = picked.first
        return Result(picked.first, nowMs < slipUntilMs)
    }

    companion object {
        const val TOL_MIN_MPS = 0.4
        const val TOL_FRACTION = 0.04
        const val SLIP_HOLD_MS = 500L

        /** Pure rule: (value, slip) for [v] given the direction of travel. */
        fun pick(
            v: List<Double>,
            mean: Double = v.average(),
            accelerating: Boolean,
        ): Pair<Double, Boolean> {
            if (v.size == 1) return v[0] to false
            val tol = max(TOL_MIN_MPS, TOL_FRACTION * abs(mean))
            val spread = v.max() - v.min()
            if (spread <= tol) return mean to false
            val value =
                when {
                    v.size >= 3 -> median(v)
                    accelerating -> v.minBy { abs(it) }
                    else -> v.maxBy { abs(it) }
                }
            return value to true
        }

        private fun median(v: List<Double>): Double {
            val s = v.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }
    }
}
