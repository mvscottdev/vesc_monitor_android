package dev.vescmonitor.core.battery

import kotlin.math.abs

/**
 * SoC from pack voltage with IR compensation (t4-algorithms findings §6.3).
 * Internal resistance is learned from current steps and a 30 s regression; before that a
 * prior of 5 mOhm per cell is used. The estimate never rises while discharging and is
 * slew-limited. Feed at 5-20 Hz with the combined voltage and summed battery current
 * (discharge positive).
 */
class SocEstimator(
    val chemistry: Chemistry,
    val cells: Int,
) {
    /** Filtered absolute SoC in % (table scale), null before the first sample. */
    var socAbs: Double? = null
        private set

    /** Learned pack resistance in Ohm, null while only the prior is used. */
    var resistance: Double? = null
        private set

    val confidenceCoarse: Boolean get() = chemistry == Chemistry.LFP

    private val stepBuf = ArrayDeque<Pair<Double, Double>>()
    private val steps = ArrayDeque<Double>()
    private val window = ArrayDeque<Pair<Double, Double>>()
    private var lowS = 0.0
    private var n = 0L

    /** Cell voltage shown as 0 %; the chemistry's default unless the rider set one. */
    var emptyCellV: Double = chemistry.emptyCellV

    /** Display SoC in % (0 % at the chemistry's empty point), null before the first sample. */
    val displaySoc: Double?
        get() = socAbs?.let { toDisplay(it) }

    fun update(
        v: Double,
        i: Double,
        dt: Double,
    ): Double {
        n++
        learnFromStep(v, i)
        learnFromRegression(v, i, dt)
        val r = resistance ?: (PRIOR_OHM_PER_CELL * cells)
        val raw = Ocv.socOf(chemistry, (v + r * i) / cells)
        val prev = socAbs
        if (prev == null) {
            socAbs = raw
            return raw
        }
        lowS = if (abs(i) < REST_A) lowS + dt else 0.0
        val tau = if (lowS >= REST_S) TAU_REST_S else TAU_RIDE_S
        var next = prev + (dt / tau).coerceAtMost(1.0) * (raw - prev)
        if (i > REST_A && next > prev) next = prev
        val up = if (i > REST_A) 0.0 else SLEW_UP_PCT_S * dt
        next = next.coerceIn(prev - SLEW_DOWN_PCT_S * dt, prev + up)
        socAbs = next
        return next
    }

    private fun learnFromStep(
        v: Double,
        i: Double,
    ) {
        stepBuf.addLast(v to i)
        if (stepBuf.size > STEP_SAMPLES) stepBuf.removeFirst()
        if (stepBuf.size < STEP_SAMPLES) return
        val (v0, i0) = stepBuf.first()
        val dI = i - i0
        if (abs(dI) >= 8.0 && (i0 < 2.0 || abs(dI) >= 15.0)) {
            val r = -(v - v0) / dI
            if (r in R_MIN..R_MAX) {
                steps.addLast(r)
                if (steps.size > 15) steps.removeFirst()
                resistance = if (steps.size >= 3) median(steps) else r
                stepBuf.clear()
            }
        }
    }

    private fun learnFromRegression(
        v: Double,
        i: Double,
        dt: Double,
    ) {
        window.addLast(v to i)
        val keep = (REGRESSION_S / dt.coerceAtLeast(0.01)).toInt().coerceAtLeast(150)
        while (window.size > keep) window.removeFirst()
        if (n % 20 != 0L || window.size < 150) return
        val meanI = window.sumOf { it.second } / window.size
        val meanV = window.sumOf { it.first } / window.size
        var sxx = 0.0
        var sxy = 0.0
        var minI = Double.MAX_VALUE
        var maxI = -Double.MAX_VALUE
        for ((wv, wi) in window) {
            sxx += (wi - meanI) * (wi - meanI)
            sxy += (wi - meanI) * (wv - meanV)
            minI = minOf(minI, wi)
            maxI = maxOf(maxI, wi)
        }
        val std = kotlin.math.sqrt(sxx / window.size)
        if (std < 4.0 || maxI - minI < 12.0) return
        val r = -sxy / sxx
        if (r in R_MIN..R_MAX) resistance = resistance?.let { it + 0.2 * (r - it) } ?: r
    }

    private fun toDisplay(abs: Double): Double {
        val empty = Ocv.socOf(chemistry, emptyCellV)
        return ((abs - empty) / (100 - empty) * 100).coerceIn(0.0, 100.0)
    }

    private fun median(xs: Collection<Double>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    companion object {
        const val PRIOR_OHM_PER_CELL = 0.005
        private const val REST_A = 2.0
        private const val REST_S = 10.0
        private const val TAU_REST_S = 4.0
        private const val TAU_RIDE_S = 20.0
        private const val SLEW_UP_PCT_S = 2.0
        private const val SLEW_DOWN_PCT_S = 1.5
        private const val STEP_SAMPLES = 4
        private const val REGRESSION_S = 30.0
        private const val R_MIN = 0.005
        private const val R_MAX = 0.5
    }
}
