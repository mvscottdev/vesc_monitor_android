package dev.vescmonitor.core.speed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedTest {
    @Test
    fun `speed doc vectors - agreeing, slip accelerating and braking`() {
        assertEquals(
            10.15 to false,
            SpeedCombiner.pick(listOf(10.0, 10.3), accelerating = true).let { (v, s) ->
                (
                    Math.round(v * 100) /
                        100.0
                ) to
                    s
            },
        )
        assertEquals(10.0 to true, SpeedCombiner.pick(listOf(10.0, 11.5), accelerating = true))
        assertEquals(11.5 to true, SpeedCombiner.pick(listOf(10.0, 11.5), accelerating = false))
    }

    @Test
    fun `one controller is itself, three use the median`() {
        assertEquals(7.0 to false, SpeedCombiner.pick(listOf(7.0), accelerating = true))
        assertEquals(10.0 to true, SpeedCombiner.pick(listOf(9.0, 10.0, 14.0), accelerating = true))
    }

    @Test
    fun `slip flag is held for half a second`() {
        val c = SpeedCombiner()
        assertTrue(c.combine(listOf(10.0, 11.5), 1_000).slip)
        assertTrue(c.combine(listOf(10.0, 10.1), 1_400).slip)
        assertFalse(c.combine(listOf(10.0, 10.1), 1_600).slip)
    }

    @Test
    fun `ratio is learned from SETUP and gives fast speed from ERPM`() {
        val r = SpeedRatio()
        r.onSetup(8.866, 10_000.0)
        assertTrue(r.valid)
        assertEquals(4.433, r.speedMps(5_000.0), 1e-9)
    }

    @Test
    fun `no speed below the ERPM floor, with the default config, or when the VESC reports 0`() {
        val r = SpeedRatio()
        r.onSetup(0.01, 100.0)
        assertTrue(r.speedMps(1000.0).isNaN())
        r.onSetup(SpeedRatio.DEFAULT_K * 5000, 5000.0)
        assertFalse(r.valid)
        val z = SpeedRatio()
        z.onSetup(8.0, 10_000.0)
        z.onSetup(0.0, 10_000.0)
        assertFalse(z.valid)
    }
}
