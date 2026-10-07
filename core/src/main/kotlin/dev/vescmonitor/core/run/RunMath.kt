package dev.vescmonitor.core.run

import kotlin.math.sqrt

/** One timed sample: time in ms, speed in m/s. */
data class RunSample(
    val tMs: Long,
    val v: Double,
)

/** Pure timing math for acceleration runs (speed-test.md, t4-algorithms findings §5). */
object RunMath {
    const val ROLLOUT_M = 0.3048
    const val FIT_WINDOW_S = 0.3
    const val FIRST_MOTION_MIN_MPS = 1.5 / 3.6
    const val FIRST_MOTION_MAX_MPS = 6.0 / 3.6

    /** Plain interpolation of the first crossing of [target] (seconds), or null. */
    fun interpCrossing(
        samples: List<RunSample>,
        target: Double,
    ): Double? {
        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            if (a.v < target && b.v >= target) {
                return a.tMs / 1000.0 + (target - a.v) / (b.v - a.v) * ((b.tMs - a.tMs) / 1000.0)
            }
        }
        return null
    }

    /** Crossing by a local least-squares line within ±0.3 s of the interpolated crossing. */
    fun crossing(
        samples: List<RunSample>,
        target: Double,
    ): Double? {
        val guess = interpCrossing(samples, target) ?: return null
        val local = samples.filter { kotlin.math.abs(it.tMs / 1000.0 - guess) <= FIT_WINDOW_S }
        val fit = fit(local) ?: return guess
        if (fit.slope <= 0) return guess
        val t = (target - fit.intercept) / fit.slope
        // A fit that lands outside its own window is worse than interpolation.
        return if (kotlin.math.abs(t - guess) <= FIT_WINDOW_S) t else guess
    }

    /**
     * Time (s) at which the distance integrated from [from] reaches [meters]; trapezoids,
     * solved exactly inside the segment (speed linear within it).
     */
    fun distanceCrossing(
        samples: List<RunSample>,
        fromS: Double,
        meters: Double,
    ): Double? {
        var dist = 0.0
        for (i in 0 until samples.size - 1) {
            var t0 = samples[i].tMs / 1000.0
            val t1 = samples[i + 1].tMs / 1000.0
            if (t1 <= fromS) continue
            var v0 = samples[i].v
            val v1 = samples[i + 1].v
            if (t0 < fromS) {
                v0 += (v1 - v0) * (fromS - t0) / (t1 - t0)
                t0 = fromS
            }
            val dt = t1 - t0
            val seg = (v0 + v1) / 2 * dt
            if (dist + seg >= meters) {
                val need = meters - dist
                val acc = (v1 - v0) / dt
                val tau =
                    if (kotlin.math.abs(acc) < 1e-9) {
                        need / v0
                    } else {
                        (-v0 + sqrt(maxOf(0.0, v0 * v0 + 2 * acc * need))) / acc
                    }
                return t0 + tau
            }
            dist += seg
        }
        return null
    }

    /** First motion: line through samples at 1.5-6 km/h extrapolated to v = 0, not before [lastStillS]. */
    fun firstMotion(
        samples: List<RunSample>,
        lastStillS: Double,
    ): Double? {
        val band = samples.filter { it.v in FIRST_MOTION_MIN_MPS..FIRST_MOTION_MAX_MPS && it.tMs / 1000.0 >= lastStillS }
        val f = fit(band) ?: return null
        if (f.slope <= 0) return null
        return maxOf(lastStillS, -f.intercept / f.slope)
    }

    data class Line(
        val slope: Double,
        val intercept: Double,
    )

    /** Least-squares v = slope * t + intercept (t in s); null with fewer than 2 distinct times. */
    fun fit(samples: List<RunSample>): Line? {
        if (samples.size < 2) return null
        val ts = samples.map { it.tMs / 1000.0 }
        val mt = ts.average()
        val mv = samples.sumOf { it.v } / samples.size
        var sxx = 0.0
        var sxy = 0.0
        samples.forEachIndexed { i, s ->
            sxx += (ts[i] - mt) * (ts[i] - mt)
            sxy += (ts[i] - mt) * (s.v - mv)
        }
        if (sxx <= 0) return null
        val slope = sxy / sxx
        return Line(slope, mv - slope * mt)
    }

    /** 95th percentile of |interval − median interval| in ms. */
    fun jitterP95Ms(samples: List<RunSample>): Double {
        if (samples.size < 3) return 0.0
        val d = samples.zipWithNext { a, b -> (b.tMs - a.tMs).toDouble() }.sorted()
        val med = d[d.size / 2]
        val dev = d.map { kotlin.math.abs(it - med) }.sorted()
        return dev[((dev.size - 1) * 0.95).toInt()]
    }
}
