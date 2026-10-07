package dev.vescmonitor.core.alert

import dev.vescmonitor.core.frame.ControllerValues
import dev.vescmonitor.core.frame.TelemetryFrame
import dev.vescmonitor.core.protocol.BatteryCut
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertTest {
    private fun run(
        rule: AlertRule,
        values: List<Pair<Long, Double?>>,
    ): List<ActiveAlert?> {
        val s = RuleState(rule, null)
        return values.map { (t, v) -> s.update(v, t) }
    }

    @Test
    fun `temperature levels with dwell, escalation and hysteresis`() {
        val r = AlertCatalog.FET_TEMP
        val s = RuleState(r, 10)
        assertNull(s.update(76.0, 0))
        assertNull(s.update(76.0, 1_999))
        val info = s.update(76.0, 2_000)!!
        assertEquals(Severity.INFO, info.severity)
        assertTrue(info.notify)
        assertEquals(Severity.INFO, s.update(85.0, 2_100)!!.severity)
        assertEquals(Severity.WARNING, s.update(85.0, 3_100)!!.severity)
        s.update(92.5, 3_200)
        assertEquals(Severity.CRITICAL, s.update(92.5, 3_700)!!.severity)
        // Clears past threshold minus hysteresis, one step at a time.
        assertEquals(Severity.CRITICAL, s.update(90.0, 3_800)!!.severity)
        assertEquals(Severity.WARNING, s.update(89.0, 3_900)!!.severity)
        assertEquals(Severity.WARNING, s.update(82.0, 4_000)!!.severity)
        assertEquals(Severity.INFO, s.update(81.9, 4_100)!!.severity)
        assertNull(s.update(72.9, 4_200))
    }

    @Test
    fun `the derived temperature levels match the bldc defaults`() {
        assertEquals(listOf(76.0, 85.0, 92.5), AlertCatalog.FET_TEMP.levels.map { it.threshold })
    }

    @Test
    fun `duty warns after 1 s and clears at 0_86`() {
        val out = run(AlertCatalog.DUTY, listOf(0L to 0.91, 999L to 0.91, 1_000L to 0.91, 1_100L to 0.88, 1_200L to 0.86))
        assertNull(out[1])
        assertEquals(Severity.WARNING, out[2]!!.severity)
        assertEquals(Severity.WARNING, out[3]!!.severity)
        assertNull(out[4])
    }

    @Test
    fun `battery cut thresholds from the controller`() {
        val r = AlertCatalog.batteryCut(39.0, 33.0)
        assertEquals(listOf(40.0, 36.0), r.levels.map { it.threshold })
        val out = run(r, listOf(0L to 40.0, 1_000L to 40.0, 1_100L to 36.0, 2_100L to 36.0))
        assertEquals(Severity.WARNING, out[1]!!.severity)
        assertEquals(Severity.CRITICAL, out[3]!!.severity)
    }

    @Test
    fun `cooldown suppresses repeated notifications, not the alert`() {
        val s = RuleState(AlertCatalog.DUTY, 1)
        s.update(0.91, 0)
        assertTrue(s.update(0.91, 1_000)!!.notify)
        assertNull(s.update(0.80, 2_000))
        s.update(0.91, 3_000)
        val again = s.update(0.91, 4_000)!!
        assertEquals(Severity.WARNING, again.severity)
        assertFalse(again.notify)
        s.update(0.80, 5_000)
        s.update(0.91, 30_000)
        assertTrue(s.update(0.91, 31_000)!!.notify)
    }

    @Test
    fun `missing input clears and stays quiet`() {
        val out = run(AlertCatalog.MOTOR_TEMP, listOf(0L to 80.0, 2_000L to 80.0, 2_100L to null))
        assertEquals(Severity.INFO, out[1]!!.severity)
        assertNull(out[2])
    }

    private fun frame(
        vararg e: TelemetryFrame.VescEntry,
        voltage: Double? = 48.0,
    ) = TelemetryFrame(1, 0, 1, TelemetryFrame.Combined(voltage, e.count { it.fresh }, e.size, false), e.toList())

    private fun entry(
        id: Int,
        voltage: Double = 48.0,
        duty: Double = 0.2,
        fault: Int = 0,
        motorC: Double? = 40.0,
    ) = TelemetryFrame.VescEntry(
        id,
        id == 10,
        voltage,
        true,
        ControllerValues(tempMosC = 40.0, tempMotorC = motorC, duty = duty, faultCode = fault, erpm = 5000.0),
        20,
    )

    @Test
    fun `engine - divergence needs two controllers, faults latch, defaults give no cut alert`() {
        val engine = AlertEngine()
        var t = 0L
        repeat(60) {
            engine.evaluate(frame(entry(10, 48.0), entry(20, 46.0)), BatteryCut(10.0, 8.0), t)
            t += 100
        }
        assertEquals(listOf("diverge"), engine.active.map { it.key })
        engine.evaluate(frame(entry(10, fault = 6)), null, t)
        val f = engine.active.single { it.key == "fault" }
        assertEquals(Severity.CRITICAL, f.severity)
        assertTrue(f.notify)
        engine.evaluate(frame(entry(10)), null, t + 100)
        assertTrue(engine.active.single { it.key == "fault" }.cleared)
        engine.dismissFaults()
        engine.evaluate(frame(entry(10)), null, t + 200)
        assertTrue(engine.active.none { it.key == "fault" })
    }

    @Test
    fun `engine - absent motor sensor gives no motor alert`() {
        val engine = AlertEngine()
        for (t in 0L..5_000L step 100) engine.evaluate(frame(entry(10, motorC = null, duty = 0.2)), null, t)
        assertTrue(engine.active.none { it.key == "motor_temp" })
    }

    @Test
    fun `rate window`() {
        val w = RateWindow(5_000)
        assertNull(w.add(0, 40.0))
        assertEquals(0.8, w.add(5_000, 44.0)!!, 1e-9)
    }

    @Test
    fun `a switched-off alert never fires`() {
        val engine = AlertEngine()
        engine.disabled = setOf("duty", "fault")
        for (t in 0L..3_000L step 100) engine.evaluate(frame(entry(10, duty = 0.97, fault = 5)), null, t)
        assertTrue(engine.active.none { it.key == "duty" || it.key == "fault" })
        engine.disabled = emptySet()
        for (t in 3_100L..5_000L step 100) engine.evaluate(frame(entry(10, duty = 0.97)), null, t)
        assertTrue(engine.active.any { it.key == "duty" })
    }

    @Test
    fun `rider thresholds replace the defaults and must keep escalating`() {
        assertNull(AlertCatalog.DUTY.withThresholds(listOf(0.9)))
        assertNull(AlertCatalog.DUTY.withThresholds(listOf(0.9, 0.8)))
        assertNull(AlertCatalog.LOW_BATTERY.withThresholds(listOf(10.0, 20.0)))
        assertEquals(
            30.0,
            AlertCatalog.LOW_BATTERY
                .withThresholds(listOf(30.0, 15.0))!!
                .levels[0]
                .threshold,
        )

        val engine = AlertEngine()
        for (t in 0L..2_000L step 100) engine.evaluate(frame(entry(10, duty = 0.85)), null, t)
        assertTrue(engine.active.none { it.key == "duty" })
        engine.thresholds = mapOf("duty" to listOf(0.8, 0.93))
        for (t in 2_100L..4_000L step 100) engine.evaluate(frame(entry(10, duty = 0.85)), null, t)
        assertEquals(Severity.WARNING, engine.active.single { it.key == "duty" }.severity)
        engine.thresholds = emptyMap()
        for (t in 4_100L..6_000L step 100) engine.evaluate(frame(entry(10, duty = 0.85)), null, t)
        assertTrue(engine.active.none { it.key == "duty" })
    }

    @Test
    fun `rider hysteresis keeps an alert on longer, and nonsense tuning is ignored`() {
        assertNull(AlertCatalog.DUTY.withTuning(-0.1, 1_000))
        assertNull(AlertCatalog.DUTY.withTuning(5.0, 1_000))
        assertNull(AlertCatalog.DUTY.withTuning(0.05, MAX_COOLDOWN_MS + 1))
        assertEquals(0.1, AlertCatalog.DUTY.withTuning(0.1, 5_000)!!.hysteresis)

        val engine = AlertEngine()
        engine.thresholds = mapOf("duty" to listOf(0.8, 0.93))
        engine.tuning = mapOf("duty" to listOf(0.1, 30.0))
        for (t in 0L..2_000L step 100) engine.evaluate(frame(entry(10, duty = 0.85)), null, t)
        assertTrue(engine.active.any { it.key == "duty" })
        // 0.75 is below the default clear point (0.77) but above the rider's (0.70).
        for (t in 2_100L..4_000L step 100) engine.evaluate(frame(entry(10, duty = 0.75)), null, t)
        assertTrue(engine.active.any { it.key == "duty" })
        engine.tuning = mapOf("duty" to listOf(-1.0, 30.0))
        for (t in 4_100L..6_000L step 100) engine.evaluate(frame(entry(10, duty = 0.75)), null, t)
        assertTrue(engine.active.none { it.key == "duty" })
    }
}
