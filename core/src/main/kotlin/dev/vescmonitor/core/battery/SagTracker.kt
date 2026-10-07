package dev.vescmonitor.core.battery

import kotlin.math.abs

/**
 * Rest reference, sag and session minimum voltage (t4-algorithms findings §9).
 * Quick rest: |I| <= 0.5 A for 3 s captures the reference; sag counts only at I >= 5 A.
 * Settled rest (60 s) is exposed for SoC anchoring and battery detection.
 * Reference decay needs capacity and is not done yet; the reference expires after 10 min.
 */
class SagTracker {
    var restRefV: Double? = null
        private set
    var sagV: Double = 0.0
        private set
    var regenRiseV: Double = 0.0
        private set
    var peakSagV: Double = 0.0
        private set
    var minVoltageV: Double? = null
        private set
    var settledRestV: Double? = null
        private set
    var maxRestV: Double? = null
        private set

    private var restSinceMs: Long? = null
    private var refAtMs = 0L

    val atRest: Boolean get() = restSinceMs != null

    fun update(
        v: Double,
        i: Double,
        nowMs: Long,
    ) {
        minVoltageV = minVoltageV?.let { minOf(it, v) } ?: v
        if (abs(i) <= REST_A) {
            val since = restSinceMs ?: nowMs.also { restSinceMs = it }
            if (nowMs - since >= QUICK_REST_MS) {
                restRefV = v
                refAtMs = nowMs
            }
            if (nowMs - since >= SETTLED_REST_MS) {
                settledRestV = v
                maxRestV = maxRestV?.let { maxOf(it, v) } ?: v
            }
        } else {
            restSinceMs = null
        }
        if (restRefV != null && nowMs - refAtMs > EXPIRE_MS) restRefV = null
        val ref = restRefV
        sagV = if (ref != null && i >= LOAD_A) maxOf(0.0, ref - v) else 0.0
        regenRiseV = if (ref != null && i < 0) maxOf(0.0, v - ref) else 0.0
        peakSagV = maxOf(peakSagV, sagV)
    }

    /** Uses a known rest voltage as the reference (first sample after connect at rest). */
    fun seed(
        v: Double,
        nowMs: Long,
    ) {
        if (restRefV == null) {
            restRefV = v
            refAtMs = nowMs
        }
    }

    companion object {
        const val REST_A = 0.5
        const val LOAD_A = 5.0
        const val QUICK_REST_MS = 3_000L
        const val SETTLED_REST_MS = 60_000L
        const val EXPIRE_MS = 10 * 60_000L
    }
}
