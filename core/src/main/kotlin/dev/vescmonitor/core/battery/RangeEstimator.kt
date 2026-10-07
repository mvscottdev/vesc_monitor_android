package dev.vescmonitor.core.battery

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Range from consumption and the energy left (battery.md, Range): a distance-domain
 * exponential average of Wh/km (decay [DECAY_M]) blended with the ride average,
 * `α = min(0.5, rideM / 6000)`, floor [FLOOR_WH_KM]. Only distance covered above
 * [MIN_SPEED_MPS] counts; energy is net of regen. Before [MIN_RIDE_M] the [prior]
 * (the median of past rides on this vehicle) stands in, when there is one.
 */
class RangeEstimator {
    private var lastTripM = Double.NaN
    private var lastWh = Double.NaN
    private var segM = 0.0
    private var segWh = 0.0
    var rideM = 0.0
        private set
    private var rideWh = 0.0
    private var eShort = Double.NaN

    /** Wh/km of past rides (their median), used until this ride has covered [MIN_RIDE_M]. */
    var prior: Double? = null

    /** Feed the session trip (m) and net energy (Wh) totals at frame rate. */
    fun update(
        tripM: Double,
        whNet: Double,
        speedMps: Double?,
    ) {
        if (lastTripM.isNaN()) {
            lastTripM = tripM
            lastWh = whNet
            return
        }
        val dM = tripM - lastTripM
        val dWh = whNet - lastWh
        lastTripM = tripM
        lastWh = whNet
        if (dM <= 0 || speedMps == null || speedMps < MIN_SPEED_MPS) return
        rideM += dM
        rideWh += dWh
        segM += dM
        segWh += dWh
        if (segM >= SEGMENT_M) {
            val e = segWh / (segM / 1000)
            val a = 1 - exp(-segM / DECAY_M)
            eShort = if (eShort.isNaN()) e else eShort + a * (e - eShort)
            segM = 0.0
            segWh = 0.0
        }
    }

    /** Blended Wh/km; before [MIN_RIDE_M] the [prior], else null. */
    val whPerKm: Double?
        get() {
            if (rideM < MIN_RIDE_M || eShort.isNaN()) return prior?.let { max(FLOOR_WH_KM, it) }
            return blend(eShort, rideWh / (rideM / 1000), rideM)
        }

    /** This ride's average Wh/km once it is long enough to stand for the vehicle, else null. */
    val rideWhPerKm: Double?
        get() = if (rideM >= HISTORY_MIN_M && rideWh > 0) rideWh / (rideM / 1000) else null

    companion object {
        const val DECAY_M = 2_000.0
        const val SEGMENT_M = 50.0
        const val MIN_RIDE_M = 500.0
        const val MIN_SPEED_MPS = 3 / 3.6
        const val FLOOR_WH_KM = 2.0
        const val MAX_KM = 999.0
        const val HISTORY_MIN_M = 2_000.0
        const val HISTORY_SIZE = 5

        /** Median of the remembered rides, null when there are none. */
        fun median(xs: List<Double>): Double? {
            if (xs.isEmpty()) return null
            val s = xs.sorted()
            val m = s.size / 2
            return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
        }

        /** Remembered rides after adding [x]: the latest [HISTORY_SIZE]. */
        fun remember(
            xs: List<Double>,
            x: Double,
        ): List<Double> = (xs + x).takeLast(HISTORY_SIZE)

        fun blend(
            eShort: Double,
            eRide: Double,
            rideM: Double,
        ): Double {
            val a = min(0.5, rideM / 6_000)
            return max(FLOOR_WH_KM, a * eShort + (1 - a) * eRide)
        }

        /** Range in km from the energy left and consumption. */
        fun rangeKm(
            whRemaining: Double,
            whPerKm: Double,
        ): Double = min(MAX_KM, max(0.0, whRemaining) / max(FLOOR_WH_KM, whPerKm))

        /** Energy left down to the chemistry's empty point. */
        fun whRemaining(
            chem: Chemistry,
            cells: Int,
            capacityAh: Double,
            socAbsPct: Double,
            emptyCellV: Double = chem.emptyCellV,
        ): Double {
            val empty = Ocv.socOf(chem, emptyCellV)
            return capacityAh * cells * (Ocv.whPerAhCell(chem, socAbsPct) - Ocv.whPerAhCell(chem, empty))
        }
    }
}
