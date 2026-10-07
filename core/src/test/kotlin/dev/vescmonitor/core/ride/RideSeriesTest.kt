package dev.vescmonitor.core.ride

import dev.vescmonitor.core.alert.ActiveAlert
import dev.vescmonitor.core.alert.AlertCodes
import dev.vescmonitor.core.alert.Severity
import dev.vescmonitor.core.store.Chunk
import dev.vescmonitor.core.store.ChunkCodec
import dev.vescmonitor.core.store.SampleField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RideSeriesTest {
    /** One chunk of [ts] samples with constant voltage and the given input currents. */
    private fun chunk(
        stream: Int,
        ts: List<Long>,
        voltageV: Double,
        currents: List<Double>,
        tempMotorC: Double = Double.NaN,
        alerts: List<Long> = List(ts.size) { 0L },
    ): Chunk {
        val n = ts.size
        val cols =
            mapOf(
                SampleField.T_MS.id to ts.toLongArray(),
                SampleField.V_IN.id to LongArray(n) { SampleField.scaled(voltageV, SampleField.V_IN.scale) },
                SampleField.CURRENT_IN.id to LongArray(n) { SampleField.scaled(currents[it], SampleField.CURRENT_IN.scale) },
                SampleField.TEMP_MOTOR.id to LongArray(n) { SampleField.scaled(tempMotorC, SampleField.TEMP_MOTOR.scale) },
                SampleField.ALERT.id to alerts.toLongArray(),
            )
        return Chunk(stream, ts.first(), n, ChunkCodec.VERSION, ChunkCodec.encode(n, cols, emptyMap()))
    }

    @Test
    fun `buckets keep spikes in min and max`() {
        val c = chunk(0, listOf(0, 100, 200, 300), 50.0, listOf(1.0, 20.0, 1.0, 1.0))
        val s = RideSeries.of(listOf(c), 2)!!
        assertEquals(
            2,
            s.metrics
                .getValue(RideSeries.Metric.POWER)
                .max.size,
        )
        assertEquals(151, s.bucketMs)
        val p = s.metrics.getValue(RideSeries.Metric.POWER)
        assertEquals(1000.0, p.max[0], 1e-9)
        assertEquals(50.0, p.min[0], 1e-9)
        assertEquals(525.0, p.mean[0], 1e-9)
        assertEquals(50.0, p.mean[1], 1e-9)
    }

    @Test
    fun `two controllers sum power and average voltage`() {
        val a = chunk(0, listOf(0, 100), 50.0, listOf(2.0, 2.0), tempMotorC = 40.0)
        val b = chunk(1, listOf(50, 150), 52.0, listOf(1.0, 1.0), tempMotorC = 60.0)
        val s = RideSeries.of(listOf(a, b), 1)!!
        val p = s.metrics.getValue(RideSeries.Metric.POWER)
        assertEquals(152.0, p.max[0], 1e-9)
        assertEquals(51.0, s.metrics.getValue(RideSeries.Metric.VOLTAGE).max[0], 1e-9)
        assertEquals(60.0, s.metrics.getValue(RideSeries.Metric.TEMP_MOTOR).max[0], 1e-9)
        val map = s.toMap()
        assertEquals(1, map["buckets"])
        assertNull((map["tempFetC"] as Map<*, *>)["mean"].let { (it as List<*>)[0] })
    }

    @Test
    fun `no samples gives no series`() {
        assertNull(RideSeries.of(emptyList(), 100))
    }

    @Test
    fun `the most severe alert per bucket wins, codes keep their key`() {
        val duty = AlertCodes.KEYS.indexOf("duty") * 4
        val low = AlertCodes.KEYS.indexOf("low_battery") * 4
        // duty critical (3) then low battery warning (2) in bucket 0; nothing in bucket 1.
        val c = chunk(0, listOf(0, 100, 200, 300), 50.0, listOf(1.0, 1.0, 1.0, 1.0), alerts = listOf(duty + 3L, low + 2L, 0, 0))
        val a = RideSeries.of(listOf(c), 2)!!.metrics.getValue(RideSeries.Metric.ALERT)
        assertEquals((duty + 3).toDouble(), a.max[0])
        assertEquals(0.0, a.max[1])
        val active = listOf(ActiveAlert("sag", null, Severity.WARNING, 25.0, 0), ActiveAlert("duty", 20, Severity.CRITICAL, 0.95, 0))
        assertEquals(AlertCodes.KEYS.indexOf("sag") * 4 + 2, AlertCodes.forController(active, 10))
        assertEquals(duty + 3, AlertCodes.forController(active, 20))
    }
}
