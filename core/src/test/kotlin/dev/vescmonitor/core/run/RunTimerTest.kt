package dev.vescmonitor.core.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RunTimerTest {
    private val ctx = RunContext()

    @Test
    fun `interpolated crossing`() {
        val s = listOf(RunSample(3_100, 58.9), RunSample(3_150, 60.5))
        assertEquals(3.134375, RunMath.interpCrossing(s, 60.0)!!, 1e-9)
    }

    /** Standstill for 1.5 s, then constant [accel] from t = 2 s, at 20 Hz. */
    private fun constantAccel(
        timer: RunTimer,
        accel: Double,
        untilS: Double,
    ) {
        timer.arm(0)
        var t = 0L
        while (t <= (untilS * 1000).toLong()) {
            val launch = (t - 2_000).coerceAtLeast(0) / 1000.0
            timer.onSample(t, accel * launch, ctx)
            timer.tick(t)
            t += 50
        }
    }

    @Test
    fun `constant 4 m per s2 - rollout and 0-60`() {
        val timer = RunTimer(listOf(Bracket("0-60", kmh = 60.0)))
        constantAccel(timer, 4.0, 7.0)
        assertEquals(RunTimer.State.DONE, timer.state)
        val t = timer.times.single()
        assertEquals(3.777, t.rolloutS!!, 0.002)
        assertEquals(4.167, t.firstMotionS!!, 0.002)
        assertEquals(2_390.0, timer.startMs!!.toDouble(), 2.0)
    }

    @Test
    fun `brackets set at arm stay for later runs`() {
        val timer = RunTimer()
        timer.arm(0, Bracket.IMPERIAL)
        assertEquals(listOf("0-20 mph", "0-30 mph", "0-40 mph", "100 m"), timer.times.map { it.bracket.label })
        constantAccel(timer, 4.0, 7.0)
        // 20 mph = 8.9408 m/s, reached 2.2352 s after first motion at 4 m/s2.
        assertEquals(2.2352, timer.times.first().firstMotionS!!, 0.01)
    }

    @Test
    fun `distance bracket from the rollout start`() {
        val timer = RunTimer(listOf(Bracket("30 m", meters = 30.0)))
        constantAccel(timer, 4.0, 8.0)
        // From standstill, 30.3048 m at 4 m/s2 takes sqrt(2 * 30.3048 / 4) = 3.8925 s.
        assertEquals(3.8925 - 0.3904, timer.times.single().rolloutS!!, 0.003)
    }

    @Test
    fun `standstill stages, a slow creep blocks it`() {
        val timer = RunTimer()
        timer.arm(0)
        listOf(0.10, 0.12, 0.05, 0.10, 0.12, 0.05, 0.10, 0.12, 0.05, 0.10, 0.12).forEachIndexed { i, v -> timer.onSample(i * 100L, v, ctx) }
        assertEquals(RunTimer.State.STAGED, timer.state)
        val blocked = RunTimer()
        blocked.arm(0)
        for (i in 0..20) blocked.onSample(i * 100L, 0.20, ctx)
        assertEquals(RunTimer.State.ARMED, blocked.state)
    }

    private fun staged(): RunTimer {
        val timer = RunTimer()
        timer.arm(0)
        for (i in 0..12) timer.onSample(i * 100L, 0.0, ctx)
        assertEquals(RunTimer.State.STAGED, timer.state)
        return timer
    }

    @Test
    fun `rolling start aborts`() {
        val timer = staged()
        timer.onSample(1_300, 6.0 / 3.6, ctx)
        assertEquals(RunTimer.Abort.ROLLING_START, timer.abort)
    }

    @Test
    fun `lift-off after the peak aborts`() {
        val timer = staged()
        var t = 1_300L
        var kmh = 1.0
        while (kmh < 42) {
            timer.onSample(t, kmh / 3.6, ctx)
            t += 50
            kmh += 1.0
        }
        repeat(9) {
            timer.onSample(t, 36.0 / 3.6, ctx)
            t += 50
        }
        assertEquals(RunTimer.State.ABORTED, timer.state)
        assertEquals(RunTimer.Abort.LIFT_OFF, timer.abort)
    }

    @Test
    fun `fault and link loss abort, cancel resets`() {
        val faulted = staged()
        faulted.onSample(1_300, 0.0, RunContext(fault = true))
        assertEquals(RunTimer.Abort.FAULT, faulted.abort)
        val lost = staged()
        lost.onSample(1_300, 0.5, ctx)
        lost.onSample(1_350, 0.7, ctx)
        lost.onSample(1_400, 0.9, ctx)
        assertEquals(RunTimer.State.RUNNING, lost.state)
        lost.tick(2_000)
        assertEquals(RunTimer.Abort.LINK, lost.abort)
        val idle = RunTimer()
        idle.cancel()
        assertEquals(RunTimer.State.IDLE, idle.state)
        assertNull(idle.abort)
    }

    @Test
    fun `top speed below a bracket finishes with it not reached`() {
        val timer = RunTimer(listOf(Bracket("0-30", kmh = 30.0), Bracket("0-60", kmh = 60.0)))
        timer.arm(0)
        var t = 0L
        while (t < 15_000) {
            val s = (t - 2_000).coerceAtLeast(0) / 1000.0
            timer.onSample(t, minOf(4.0 * s, 40.0 / 3.6), ctx)
            t += 50
        }
        assertEquals(RunTimer.State.DONE, timer.state)
        assertEquals(listOf(true, false), timer.times.map { it.rolloutS != null })
    }

    @Test
    fun `saved run carries a decimated curve in tenths of km per h`() {
        val timer = RunTimer(listOf(Bracket("0-60", kmh = 60.0)))
        constantAccel(timer, 4.0, 7.0)
        val saved = RunReport.toSaved(timer, 7_000, listOf(5.5e-4))

        @Suppress("UNCHECKED_CAST")
        val curve = saved["curve"] as List<List<Long>>
        assertEquals(listOf(0L, 0L), curve.first())
        assertEquals(600.0, curve.last()[1].toDouble(), 10.0)
        assertNull(saved["elapsedMs"])
    }
}
