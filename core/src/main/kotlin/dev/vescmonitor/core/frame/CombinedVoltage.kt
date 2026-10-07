package dev.vescmonitor.core.frame

import kotlin.math.abs
import kotlin.math.max

/** Combined pack voltage over the fresh controllers. */
data class CombinedVoltage(
    val voltageV: Double?,
    /** Two controllers disagree by more than the tolerance; the lower one is shown. */
    val divergent: Boolean,
) {
    companion object {
        const val TOLERANCE_MIN_V = 0.5
        const val TOLERANCE_FRACTION = 0.01

        /**
         * One value: itself. Two: the mean if within max(0.5 V, 1 %), else the lower one
         * with a divergence flag. Three or more: the median. Stale values never count.
         */
        fun of(fresh: List<Double>): CombinedVoltage =
            when (fresh.size) {
                0 -> CombinedVoltage(null, false)
                1 -> CombinedVoltage(fresh[0], false)
                2 -> pair(fresh[0], fresh[1])
                else -> CombinedVoltage(median(fresh), false)
            }

        private fun pair(
            a: Double,
            b: Double,
        ): CombinedVoltage {
            val mean = (a + b) / 2
            val tolerance = max(TOLERANCE_MIN_V, TOLERANCE_FRACTION * mean)
            return if (abs(a - b) <= tolerance) CombinedVoltage(mean, false) else CombinedVoltage(minOf(a, b), true)
        }

        private fun median(values: List<Double>): Double {
            val s = values.sorted()
            val mid = s.size / 2
            return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
        }
    }
}
