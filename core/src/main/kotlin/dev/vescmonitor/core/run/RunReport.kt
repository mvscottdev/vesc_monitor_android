package dev.vescmonitor.core.run

/** The run state as a UI event map: times in ms, speeds in m/s, flags 0/1, null = unknown. */
object RunReport {
    fun toMap(
        r: RunTimer,
        nowMs: Long,
        k: List<Double>,
    ): Map<String, Any?> {
        val start = r.startMs
        val elapsed =
            when {
                start == null -> null
                r.state == RunTimer.State.RUNNING -> (nowMs - start).coerceAtLeast(0)
                else -> null
            }
        return mapOf(
            "state" to r.state.name.lowercase(),
            "abort" to r.abort?.name?.lowercase(),
            "elapsedMs" to elapsed,
            "brackets" to
                r.times.map { t ->
                    mapOf(
                        "label" to t.bracket.label,
                        "rolloutMs" to t.rolloutS?.let { (it * 1000).toLong() },
                        "firstMotionMs" to t.firstMotionS?.let { (it * 1000).toLong() },
                    )
                },
            "peakSpeedMps" to r.peakMps,
            "peakPowerW" to r.peakPowerW,
            "minVoltageV" to r.minVoltageV,
            "maxTempMosC" to r.maxTempMosC,
            "maxTempMotorC" to r.maxTempMotorC,
            "slip" to if (r.slipBelow20) 1 else 0,
            "startEstimated" to if (r.startEstimated) 1 else 0,
            "sampleRateHz" to r.sampleRateHz,
            "jitterP95Ms" to r.jitterP95Ms,
            "k" to k.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** What is saved for a finished run: the report plus its curve as [ms from start, km/h × 10]. */
    fun toSaved(
        r: RunTimer,
        nowMs: Long,
        k: List<Double>,
    ): Map<String, Any?> {
        val t0 = r.curve().firstOrNull()?.tMs ?: 0L
        val curve = decimate(r.curve(), MAX_CURVE_POINTS).map { listOf(it.tMs - t0, Math.round(it.v * 36.0)) }
        return toMap(r, nowMs, k) - "elapsedMs" + ("curve" to curve)
    }

    private fun <T> decimate(
        xs: List<T>,
        max: Int,
    ): List<T> {
        if (xs.size <= max) return xs
        val step = xs.size.toDouble() / max
        return List(max) { xs[(it * step).toInt()] }
    }

    const val MAX_CURVE_POINTS = 600
}
