package dev.vescmonitor.core.frame

import dev.vescmonitor.core.battery.BatteryDetect
import dev.vescmonitor.core.battery.CapacityLearner
import dev.vescmonitor.core.battery.Chemistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DerivationsTest {
    private fun cellsOf(
        info: Map<String, Any?>,
        key: String,
    ) = (info[key] as Map<*, *>?)?.get("cells")

    @Test
    fun `confirmed pack replaces detection until cleared`() {
        val d = Derivations()
        var t = 0L
        val first = d.update(emptyList(), emptyList(), 54.2, 0.0, t)
        assertEquals(13, first.cells)
        assertEquals(Chemistry.NMC, first.chemistry)

        d.confirmedPack = BatteryDetect.Candidate(Chemistry.LFP, 16, 1.0)
        t += 100
        val confirmed = d.update(emptyList(), emptyList(), 54.2, 0.0, t)
        assertEquals(16, confirmed.cells)
        assertEquals(Chemistry.LFP, confirmed.chemistry)
        assertEquals(BatteryDetect.Confidence.HIGH, confirmed.socConfidence)
        val info = d.batteryInfo()
        assertEquals(16, cellsOf(info, "confirmed"))
        assertEquals(13, cellsOf(info, "detected"))
        assertEquals(54.2, info["vRest"])

        d.confirmedPack = null
        t += 100
        assertEquals(13, d.update(emptyList(), emptyList(), 54.2, 0.0, t).cells)
        assertNull(d.batteryInfo()["confirmed"])
    }

    @Test
    fun `confirmed before any reading starts SoC at once`() {
        val d = Derivations()
        d.confirmedPack = BatteryDetect.Candidate(Chemistry.NMC, 13, 1.0)
        val r = d.update(emptyList(), emptyList(), 50.0, 5.0, 0)
        assertEquals(13, r.cells)
        assertNull(d.batteryInfo()["detected"])
    }

    @Test
    fun `learned capacity gives a range and resets with another pack`() {
        val d = Derivations()
        d.capacityState = CapacityLearner.State(8.4, 70.0, 2)
        val r = d.update(emptyList(), emptyList(), 54.2, 0.0, 0)
        assertEquals(12.0, r.capacityAh!!, 1e-9)
        assertNull(r.rangeKm) // no consumption yet
        d.confirmedPack = BatteryDetect.Candidate(Chemistry.LFP, 16, 1.0)
        assertEquals(0, d.capacityState.intervals)
    }

    @Test
    fun `entered capacity replaces the learned one until cleared`() {
        val d = Derivations()
        d.capacityState = CapacityLearner.State(8.4, 70.0, 2)
        d.capacityOverrideAh = 20.0
        assertEquals(20.0, d.update(emptyList(), emptyList(), 54.2, 0.0, 0).capacityAh!!, 1e-9)
        assertEquals(20.0, d.batteryInfo()["capacityAh"])
        assertEquals(12.0, d.batteryInfo()["learnedAh"] as Double, 1e-9)
        d.capacityOverrideAh = null
        assertEquals(12.0, d.update(emptyList(), emptyList(), 54.2, 0.0, 0).capacityAh!!, 1e-9)
    }
}
