package dev.vescmonitor.core.ride

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnergyCounterTest {
    @Test
    fun `power doc vector - reboot counts energy since boot`() {
        val c = EnergyCounter(EnergyCounter.EPS_WH)
        listOf(10.00, 10.50, 11.10, 0.20, 0.55).forEach(c::add)
        assertEquals(1.65, c.total, 1e-9)
        assertEquals(1, c.resets)
    }

    @Test
    fun `jitter below eps is not a reboot`() {
        val c = EnergyCounter(EnergyCounter.EPS_WH)
        listOf(5.0, 4.995, 5.2).forEach(c::add)
        assertEquals(0.205, c.total, 1e-9)
        assertEquals(0, c.resets)
    }

    @Test
    fun `unknown values are ignored`() {
        val c = EnergyCounter(EnergyCounter.EPS_AH)
        listOf(Double.NaN, 1.0, Double.NaN, 1.5).forEach(c::add)
        assertEquals(0.5, c.total, 1e-9)
    }
}
