package dev.vescmonitor.core.battery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RangeTest {
    @Test
    fun `doc vectors - blend and range`() {
        assertEquals(24.0, RangeEstimator.blend(26.0, 22.0, 6_000.0), 1e-9)
        assertEquals(12.5, RangeEstimator.rangeKm(300.0, 24.0), 1e-9)
        assertEquals(999.0, RangeEstimator.rangeKm(1e6, 1.0), 1e-9)
    }

    @Test
    fun `doc vectors - table energy for 13S 12 Ah NMC`() {
        // findings §8: E(100 %) = 583 Wh, E(43.3 %) = 237 Wh, E(50 %) = 276 Wh.
        assertEquals(583.0, RangeEstimator.whRemaining(Chemistry.NMC, 13, 12.0, 100.0), 3.0)
        assertEquals(237.0, RangeEstimator.whRemaining(Chemistry.NMC, 13, 12.0, 43.3), 3.0)
        assertEquals(276.0, RangeEstimator.whRemaining(Chemistry.NMC, 13, 12.0, 50.0), 3.0)
    }

    @Test
    fun `consumption needs 500 m above walking speed`() {
        val r = RangeEstimator()
        var m = 0.0
        var wh = 0.0
        r.update(m, wh, 8.0)
        repeat(9) {
            m += 50
            wh += 1.0
            r.update(m, wh, 8.0)
        }
        assertNull(r.whPerKm)
        // Standing still with the motor drawing idle power: no distance, ignored.
        r.update(m, wh + 5, 0.0)
        wh += 5
        repeat(2) {
            m += 50
            wh += 1.0
            r.update(m, wh, 8.0)
        }
        assertEquals(20.0, r.whPerKm!!, 1e-6)
    }

    @Test
    fun `past rides stand in for the first 500 m and the ride is remembered after 2 km`() {
        val r = RangeEstimator()
        r.prior = RangeEstimator.median(listOf(30.0, 18.0, 22.0, 25.0))
        var m = 0.0
        var wh = 0.0
        r.update(m, wh, 8.0)
        assertEquals(23.5, r.whPerKm!!, 1e-9)
        assertNull(r.rideWhPerKm)
        repeat(40) {
            m += 50
            wh += 0.8
            r.update(m, wh, 8.0)
        }
        // 2 km at 16 Wh/km: this ride's own number replaces the prior.
        assertEquals(16.0, r.rideWhPerKm!!, 1e-9)
        assertEquals(16.0, r.whPerKm!!, 1e-6)
        assertNull(RangeEstimator.median(emptyList()))
        assertEquals(listOf(2.0, 3.0, 4.0, 5.0, 6.0), RangeEstimator.remember(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 6.0))
    }

    @Test
    fun `capacity learns from two discharge intervals`() {
        val l = CapacityLearner()
        assertFalse(l.onRest(90.0, 0.0))
        assertFalse(l.onRest(80.0, 1.2))
        assertTrue(l.onRest(55.0, 4.2))
        assertNull(l.capacityAh)
        // A charge resets the anchor without learning.
        assertFalse(l.onRest(95.0, 4.2))
        assertTrue(l.onRest(60.0, 8.4))
        assertEquals(12.0, l.capacityAh!!, 1e-9)
        assertEquals(2, l.state.intervals)
        val restored = CapacityLearner(l.state)
        assertEquals(12.0, restored.capacityAh!!, 1e-9)
    }
}
