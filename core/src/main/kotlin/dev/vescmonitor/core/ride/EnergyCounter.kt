package dev.vescmonitor.core.ride

import kotlin.math.max

/**
 * Sums a VESC RAM counter (Wh, Ah, tachometer) over a ride. The counter restarts at 0
 * when the controller reboots, so only deltas are summed: the first sample counts 0,
 * a rise counts its delta, a drop by more than [eps] is a reboot and counts the new
 * value (energy since boot). Rules and test vector in power.md.
 */
class EnergyCounter(
    private val eps: Double,
) {
    var total = 0.0
        private set
    var resets = 0
        private set
    private var prev = Double.NaN

    fun add(x: Double) {
        if (x.isNaN()) return
        if (prev.isNaN()) {
            prev = x
            return
        }
        if (x >= prev - eps) {
            total += max(0.0, x - prev)
        } else {
            total += x
            resets++
        }
        prev = x
    }

    companion object {
        const val EPS_WH = 0.01
        const val EPS_AH = 0.001
        const val EPS_TACH = 0.5
    }
}
