package dev.vescmonitor.core.battery

import dev.vescmonitor.core.battery.BatteryDetect.Confidence
import dev.vescmonitor.core.battery.BatteryDetect.Evidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BatteryTest {
    @Test
    fun ocvVectors() {
        assertEquals(43.33, Ocv.socOf(Chemistry.NMC, 48.1 / 13), 0.05)
        assertEquals(42.05, Ocv.socOf(Chemistry.NMC, (46.0 + 0.08 * 25) / 13), 0.05)
        assertEquals(100.0, Ocv.socOf(Chemistry.NMC, 54.6 / 13), 1e-9)
        assertEquals(95.0, Ocv.socOf(Chemistry.NMC, 4.15), 1.0)
        assertEquals(100.0, Ocv.socOf(Chemistry.NMC, 42.0 / 10), 1e-9)
        assertEquals(70.0, Ocv.socOf(Chemistry.LFP, 52.8 / 16), 1e-9)
        assertEquals(50.0, Ocv.socOf(Chemistry.LFP, 3.26), 1e-9)
        assertEquals(0.0, Ocv.displaySoc(Chemistry.NMC, 2.9), 1e-9)
        assertEquals(100.0, Ocv.displaySoc(Chemistry.LFP, 3.5), 1e-9)
    }

    private fun top(
        e: Evidence,
        chem: Chemistry,
        cells: Int,
        p: Double,
        conf: Confidence,
        tolerance: Double = 0.02,
    ): BatteryDetect.Result {
        val r = requireNotNull(BatteryDetect.detect(e))
        assertEquals(chem, r.best.chemistry)
        assertEquals(cells, r.best.cells)
        assertEquals(p, r.best.p, tolerance)
        assertEquals(conf, r.confidence)
        return r
    }

    @Test
    fun detectVector1FullPack() {
        top(Evidence(54.2, vMaxSeen = 54.6), Chemistry.NMC, 13, 0.85, Confidence.HIGH)
    }

    @Test
    fun detectVector2MidRangeTie() {
        val r = requireNotNull(BatteryDetect.detect(Evidence(51.0)))
        assertEquals(Confidence.LOW, r.confidence)
        assertEquals(0.36, r.best.p, 0.02)
        assertTrue(r.best.cells in setOf(13, 14))
        assertTrue(r.alternatives.any { it.cells in setOf(13, 14) && it.chemistry == Chemistry.NMC })
    }

    @Test
    fun detectVector3Config() {
        val e = Evidence(54.5, cfgCells = 14, cfgChemistry = Chemistry.NMC, cutEndV = 39.2, cutStartV = 44.8)
        top(e, Chemistry.NMC, 14, 0.96, Confidence.HIGH)
    }

    @Test
    fun detectVector4DefaultsAreNoEvidence() {
        val e = Evidence(42.0, cfgCells = 3, cfgChemistry = Chemistry.NMC, cutStartV = 10.0, cutEndV = 8.0, maxVin = 57.0)
        // The reference script is not in the repo; the scored table gives 0.46 / 0.23 here.
        val r = top(e, Chemistry.NMC, 10, 0.52, Confidence.LOW, tolerance = 0.07)
        assertEquals(0.26, r.alternatives.first { it.cells == 12 }.p, 0.04)
    }

    @Test
    fun detectVector5Lfp() {
        val e = Evidence(53.0, cfgCells = 16, cfgChemistry = Chemistry.LFP, cutEndV = 44.0, cutStartV = 48.0)
        top(e, Chemistry.LFP, 16, 0.90, Confidence.HIGH)
    }

    @Test
    fun detectVector6PriorOnlyIsCapped() {
        val r = top(Evidence(51.0, vMaxSeen = 58.4), Chemistry.NMC, 14, 0.89, Confidence.MEDIUM)
        assertTrue(r.confirmChemistry)
    }

    @Test
    fun detectVector7HighVoltage() {
        top(Evidence(84.0), Chemistry.NMC, 20, 0.40, Confidence.LOW)
    }

    @Test
    fun detectVector8FourteenFull() {
        top(Evidence(58.6, vMaxSeen = 58.8), Chemistry.NMC, 14, 0.89, Confidence.HIGH)
    }

    @Test
    fun detectVector9ImplausibleConfig() {
        val e = Evidence(51.0, cfgCells = 13, cutEndV = 48.0, cutStartV = 50.0)
        val r = top(e, Chemistry.NMC, 16, 0.34, Confidence.LOW, tolerance = 0.04)
        assertEquals(listOf(13, 15), r.alternatives.take(2).map { it.cells })
    }

    @Test
    fun socNeverRisesWhileDischargingAndIsSlewLimited() {
        val soc = SocEstimator(Chemistry.NMC, 13)
        soc.update(48.1, 0.0, 0.1)
        var prev = soc.socAbs!!
        // 20 A with a 1.6 V dip beyond the IR drop, 10 s.
        repeat(100) {
            val s = soc.update(48.1 - 20 * 0.065 - 1.6, 20.0, 0.1)
            assertTrue(s <= prev + 1e-9)
            assertTrue(prev - s <= 1.5 * 0.1 + 1e-9)
            prev = s
        }
    }

    @Test
    fun socFirstSampleAndStepLearning() {
        val soc = SocEstimator(Chemistry.NMC, 13)
        assertEquals(43.33, soc.update(48.1, 0.0, 0.1), 0.05)
        // Step 0 -> 25 A with a true R of 0.08 Ohm.
        repeat(3) { soc.update(48.1, 0.0, 0.1) }
        soc.update(48.1 - 0.08 * 25, 25.0, 0.1)
        assertEquals(0.08, soc.resistance!!, 1e-6)
    }

    @Test
    fun aHigherEmptyPointLowersTheDisplaySoc() {
        val soc = SocEstimator(Chemistry.NMC, 13)
        soc.update(48.1, 0.0, 0.1)
        val byDefault = soc.displaySoc!!
        soc.emptyCellV = 3.4
        assertTrue(soc.displaySoc!! < byDefault - 5, "${soc.displaySoc} vs $byDefault")
        val whDefault = RangeEstimator.whRemaining(Chemistry.NMC, 13, 10.0, soc.socAbs!!)
        assertTrue(RangeEstimator.whRemaining(Chemistry.NMC, 13, 10.0, soc.socAbs!!, 3.4) < whDefault)
    }

    @Test
    fun sagVector() {
        val sag = SagTracker()
        var t = 0L
        repeat(40) { sag.update(54.0, 0.2, t.also { t += 100 }) }
        assertEquals(54.0, sag.restRefV!!, 1e-9)
        sag.update(46.5, 45.0, t)
        assertEquals(7.5, sag.sagV, 1e-9)
        assertEquals(46.5, sag.minVoltageV!!, 1e-9)
        sag.update(54.33, -10.0, t + 100)
        assertEquals(0.0, sag.sagV, 1e-9)
        assertEquals(0.33, sag.regenRiseV, 1e-9)
        sag.update(53.0, 3.0, t + 200)
        assertEquals(0.0, sag.sagV, 1e-9)
    }
}
