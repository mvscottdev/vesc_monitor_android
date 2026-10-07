package dev.vescmonitor.core.ride

import dev.vescmonitor.core.store.Chunk
import dev.vescmonitor.core.store.ChunkCodec
import dev.vescmonitor.core.store.KeyframeField
import dev.vescmonitor.core.store.SampleField

/**
 * Exact ride summary, computed from the stored chunks (a normal close and a recovery
 * share this path). Energy comes from reset-safe counter deltas; combined power and
 * current are the sum of each controller's latest sample at every sample time.
 */
data class RideSummary(
    val startMs: Long,
    val endMs: Long,
    val samples: Int,
    val whUsed: Double,
    val whRegen: Double,
    val ahUsed: Double,
    val ahRegen: Double,
    val peakPowerW: Double,
    val peakRegenW: Double,
    val peakCurrentInA: Double,
    val minVoltageV: Double?,
    val maxTempMotorC: Double?,
    val maxTempFetC: Double?,
    val faultCodes: List<Int>,
    val counterResets: Int,
    val bytes: Long,
) {
    val durationMs: Long get() = endMs - startMs

    companion object {
        fun of(chunks: List<Chunk>): RideSummary? {
            if (chunks.isEmpty()) return null
            val acc = Accumulator()
            chunks.groupBy { it.stream }.forEach { (stream, list) ->
                list.sortedBy { it.t0Ms }.forEach { acc.addChunk(stream, ChunkCodec.decode(it.data)) }
            }
            return acc.summary(chunks.sumOf { it.data.size.toLong() })
        }
    }

    private class Accumulator {
        private class Event(
            val tMs: Long,
            val stream: Int,
            val powerW: Double,
            val currentInA: Double,
        )

        private val events = ArrayList<Event>()
        private val wh = HashMap<Int, EnergyCounter>()
        private val whCharged = HashMap<Int, EnergyCounter>()
        private val ah = HashMap<Int, EnergyCounter>()
        private val ahCharged = HashMap<Int, EnergyCounter>()
        private var minV = Double.NaN
        private var maxMotor = Double.NaN
        private var maxFet = Double.NaN
        private val faults = LinkedHashSet<Int>()

        fun addChunk(
            stream: Int,
            d: ChunkCodec.Decoded,
        ) {
            val t = d.columns[SampleField.T_MS.id] ?: return
            for (i in 0 until d.n) {
                val v = value(d, SampleField.V_IN, i)
                val iIn = value(d, SampleField.CURRENT_IN, i)
                val p = if (v.isNaN() || iIn.isNaN()) Double.NaN else v * iIn
                events += Event(t[i], stream, p, iIn)
                minV = minOf(minV, v)
                maxMotor = maxOf(maxMotor, value(d, SampleField.TEMP_MOTOR, i))
                maxFet = maxOf(maxFet, value(d, SampleField.TEMP_FET, i))
                d.columns[SampleField.FAULT.id]
                    ?.get(i)
                    ?.toInt()
                    ?.takeIf { it != 0 }
                    ?.let(faults::add)
            }
            counter(wh, stream, EnergyCounter.EPS_WH).add(key(d, KeyframeField.WATT_HOURS))
            counter(whCharged, stream, EnergyCounter.EPS_WH).add(key(d, KeyframeField.WATT_HOURS_CHARGED))
            counter(ah, stream, EnergyCounter.EPS_AH).add(key(d, KeyframeField.AMP_HOURS))
            counter(ahCharged, stream, EnergyCounter.EPS_AH).add(key(d, KeyframeField.AMP_HOURS_CHARGED))
        }

        fun summary(bytes: Long): RideSummary {
            events.sortBy { it.tMs }
            val latestP = HashMap<Int, Double>()
            val latestI = HashMap<Int, Double>()
            var peakP = 0.0
            var peakRegen = 0.0
            var peakI = 0.0
            for (e in events) {
                if (!e.powerW.isNaN()) latestP[e.stream] = e.powerW
                if (!e.currentInA.isNaN()) latestI[e.stream] = e.currentInA
                val p = latestP.values.sum()
                peakP = maxOf(peakP, p)
                peakRegen = minOf(peakRegen, p)
                peakI = maxOf(peakI, latestI.values.sum())
            }
            return RideSummary(
                startMs = events.first().tMs,
                endMs = events.last().tMs,
                samples = events.size,
                whUsed = wh.values.sumOf { it.total },
                whRegen = whCharged.values.sumOf { it.total },
                ahUsed = ah.values.sumOf { it.total },
                ahRegen = ahCharged.values.sumOf { it.total },
                peakPowerW = peakP,
                peakRegenW = peakRegen,
                peakCurrentInA = peakI,
                minVoltageV = minV.known(),
                maxTempMotorC = maxMotor.known(),
                maxTempFetC = maxFet.known(),
                faultCodes = faults.toList(),
                counterResets = wh.values.sumOf { it.resets },
                bytes = bytes,
            )
        }

        private fun counter(
            map: HashMap<Int, EnergyCounter>,
            stream: Int,
            eps: Double,
        ) = map.getOrPut(stream) { EnergyCounter(eps) }

        private fun value(
            d: ChunkCodec.Decoded,
            f: SampleField,
            i: Int,
        ): Double {
            val raw = d.columns[f.id]?.get(i) ?: return Double.NaN
            return SampleField.unscaled(raw, f.scale)
        }

        private fun key(
            d: ChunkCodec.Decoded,
            f: KeyframeField,
        ): Double = d.keyframe[f.id]?.let { it / f.scale } ?: Double.NaN

        /** minOf/maxOf with NaN meaning "none yet": a NaN never wins over a number. */
        private fun minOf(
            a: Double,
            b: Double,
        ): Double =
            if (a.isNaN()) {
                b
            } else if (b.isNaN()) {
                a
            } else {
                kotlin.math.min(a, b)
            }

        private fun maxOf(
            a: Double,
            b: Double,
        ): Double =
            if (a.isNaN()) {
                b
            } else if (b.isNaN()) {
                a
            } else {
                kotlin.math.max(a, b)
            }

        private fun Double.known(): Double? = takeUnless { isNaN() }
    }
}
