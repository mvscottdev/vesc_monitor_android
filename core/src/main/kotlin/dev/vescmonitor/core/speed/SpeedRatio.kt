package dev.vescmonitor.core.speed

import kotlin.math.abs

/**
 * Learns one controller's effective speed ratio k = speed / ERPM (m/s per ERPM) from the
 * VESC's own SETUP speed, so fast speed = k x ERPM from every fast sample. The VESC
 * computes its speed linearly from ERPM with its configured wheel, gear and poles
 * (bldc mc_interface.c, mc_interface_get_speed), so k is a constant of the vehicle.
 */
class SpeedRatio {
    private val recent = ArrayDeque<Double>()

    /** Median of the recent ratios; NaN until one was learned. */
    var k = Double.NaN
        private set

    /** The VESC reports zero speed while the motor turns: its wheel config is missing. */
    var vescSpeedMissing = false
        private set

    fun onSetup(
        speedMps: Double,
        erpm: Double,
    ) {
        if (speedMps.isNaN() || erpm.isNaN() || abs(erpm) <= MIN_ERPM) return
        if (speedMps == 0.0) {
            vescSpeedMissing = true
            return
        }
        vescSpeedMissing = false
        recent.addLast(speedMps / erpm)
        if (recent.size > WINDOW) recent.removeFirst()
        k = recent.sorted()[recent.size / 2]
    }

    /** k is learned and not the firmware's unconfigured default. */
    val valid: Boolean
        get() = !k.isNaN() && !vescSpeedMissing && abs(k / DEFAULT_K - 1) > DEFAULT_TOLERANCE

    fun speedMps(erpm: Double): Double = if (valid && !erpm.isNaN()) k * erpm else Double.NaN

    companion object {
        /** Below this, sensorless ERPM is unreliable (speed.md). */
        const val MIN_ERPM = 200.0

        /** m/s per ERPM with bldc's default wheel, gear and poles (UNVERIFIED, speed.md). */
        const val DEFAULT_K = 1.24e-4
        const val DEFAULT_TOLERANCE = 0.01
        const val WINDOW = 9
    }
}
