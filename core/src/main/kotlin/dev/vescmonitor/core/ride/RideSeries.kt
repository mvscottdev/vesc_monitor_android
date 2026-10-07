package dev.vescmonitor.core.ride

import dev.vescmonitor.core.alert.AlertCodes
import dev.vescmonitor.core.store.Chunk
import dev.vescmonitor.core.store.ChunkCodec
import dev.vescmonitor.core.store.SampleField
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A stored ride downsampled for charts: fixed time buckets with mean, min and max per
 * metric, so short spikes stay visible at any zoom. Combined values follow the summary:
 * at every sample time each controller contributes its latest sample (power summed,
 * voltage averaged, temperatures and duty the highest).
 */
class RideSeries private constructor(
    val startMs: Long,
    val bucketMs: Long,
    val metrics: Map<Metric, Bucketed>,
) {
    enum class Metric(
        val key: String,
    ) {
        POWER("powerW"),
        VOLTAGE("voltageV"),
        TEMP_FET("tempFetC"),
        TEMP_MOTOR("tempMotorC"),
        DUTY("duty"),
        ERPM("erpm"),
        SPEED("speedMps"),
        ALERT("alert"),
    }

    /** Per bucket; NaN where the bucket had no value. */
    class Bucketed(
        val mean: DoubleArray,
        val min: DoubleArray,
        val max: DoubleArray,
    )

    /** For the bridge: numbers only, null for empty buckets. */
    fun toMap(): Map<String, Any?> =
        mapOf(
            "startMs" to startMs,
            "bucketMs" to bucketMs,
            "buckets" to (
                metrics.values
                    .firstOrNull()
                    ?.mean
                    ?.size ?: 0
            ),
        ) +
            metrics.map { (m, b) ->
                m.key to mapOf("mean" to b.mean.toNullable(), "min" to b.min.toNullable(), "max" to b.max.toNullable())
            }

    companion object {
        const val MAX_BUCKETS = 2_000

        fun of(
            chunks: List<Chunk>,
            buckets: Int,
        ): RideSeries? {
            val events = ArrayList<Event>()
            chunks.forEach { c ->
                val d = ChunkCodec.decode(c.data)
                val t = d.columns[SampleField.T_MS.id] ?: return@forEach
                for (i in 0 until d.n) {
                    val v = value(d, SampleField.V_IN, i)
                    val iIn = value(d, SampleField.CURRENT_IN, i)
                    events +=
                        Event(
                            t[i],
                            c.stream,
                            doubleArrayOf(
                                if (v.isNaN() || iIn.isNaN()) Double.NaN else v * iIn,
                                v,
                                value(d, SampleField.TEMP_FET, i),
                                value(d, SampleField.TEMP_MOTOR, i),
                                abs(value(d, SampleField.DUTY, i)),
                                abs(value(d, SampleField.ERPM, i)),
                                abs(value(d, SampleField.SPEED, i)),
                                d.columns[SampleField.ALERT.id]?.get(i)?.toDouble() ?: Double.NaN,
                            ),
                        )
                }
            }
            if (events.isEmpty()) return null
            events.sortBy { it.tMs }
            val start = events.first().tMs
            val span = max(1L, events.last().tMs - start + 1)
            val n = buckets.coerceIn(1, MAX_BUCKETS)
            val width = max(1L, (span + n - 1) / n)
            val count = ((span + width - 1) / width).toInt()
            val metrics = Metric.entries
            val sum = Array(metrics.size) { DoubleArray(count) }
            val cnt = Array(metrics.size) { IntArray(count) }
            val lo = Array(metrics.size) { DoubleArray(count) { Double.NaN } }
            val hi = Array(metrics.size) { DoubleArray(count) { Double.NaN } }
            val latest = HashMap<Int, DoubleArray>()
            for (e in events) {
                val prev = latest.getOrPut(e.stream) { DoubleArray(metrics.size) { Double.NaN } }
                for (k in metrics.indices) if (!e.values[k].isNaN()) prev[k] = e.values[k]
                val b = ((e.tMs - start) / width).toInt()
                for ((k, m) in metrics.withIndex()) {
                    val x = combine(m, latest.values.map { it[k] }.filterNot(Double::isNaN)) ?: continue
                    sum[k][b] += x
                    cnt[k][b]++
                    lo[k][b] = if (lo[k][b].isNaN()) x else min(lo[k][b], x)
                    hi[k][b] =
                        when {
                            hi[k][b].isNaN() -> x
                            m == Metric.ALERT -> if (AlertCodes.rank(x.toInt()) > AlertCodes.rank(hi[k][b].toInt())) x else hi[k][b]
                            else -> max(hi[k][b], x)
                        }
                }
            }
            val out =
                metrics.withIndex().associate { (k, m) ->
                    m to Bucketed(DoubleArray(count) { if (cnt[k][it] == 0) Double.NaN else sum[k][it] / cnt[k][it] }, lo[k], hi[k])
                }
            return RideSeries(start, width, out)
        }

        private fun combine(
            m: Metric,
            xs: List<Double>,
        ): Double? {
            if (xs.isEmpty()) return null
            return when (m) {
                Metric.POWER -> xs.sum()

                Metric.VOLTAGE, Metric.SPEED -> xs.average()

                // The most severe alert code; 0 (none) stays 0.
                Metric.ALERT -> xs.maxBy { AlertCodes.rank(it.toInt()) }

                else -> xs.max()
            }
        }

        private fun value(
            d: ChunkCodec.Decoded,
            f: SampleField,
            i: Int,
        ): Double {
            val raw = d.columns[f.id]?.get(i) ?: return Double.NaN
            return SampleField.unscaled(raw, f.scale)
        }

        private fun DoubleArray.toNullable(): List<Double?> = map { if (it.isNaN()) null else it }
    }

    private class Event(
        val tMs: Long,
        val stream: Int,
        val values: DoubleArray,
    )
}
