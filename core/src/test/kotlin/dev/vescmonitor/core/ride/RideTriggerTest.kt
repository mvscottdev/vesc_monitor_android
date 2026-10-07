package dev.vescmonitor.core.ride

import dev.vescmonitor.core.ride.RideTrigger.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RideTriggerTest {
    @Test
    fun `opens on motion, stays open through short stops, closes after the idle time`() {
        val t = RideTrigger(idleEndMs = 60_000)
        assertEquals(Action.NONE, t.update(0, moving = false, recording = false))
        assertEquals(Action.OPEN, t.update(1_000, moving = true, recording = false))
        assertEquals(Action.NONE, t.update(30_000, moving = false, recording = true))
        assertEquals(Action.NONE, t.update(60_000, moving = true, recording = true))
        assertEquals(Action.NONE, t.update(119_000, moving = false, recording = true))
        assertEquals(Action.CLOSE, t.update(120_000, moving = false, recording = true))
        assertEquals(Action.NONE, t.update(121_000, moving = false, recording = false))
        assertEquals(Action.OPEN, t.update(200_000, moving = true, recording = false))
    }

    @Test
    fun `a ride opened by hand while standing still closes after the idle time`() {
        val t = RideTrigger(idleEndMs = 60_000)
        assertEquals(Action.NONE, t.update(5_000, moving = false, recording = true))
        assertEquals(Action.CLOSE, t.update(65_000, moving = false, recording = true))
    }
}
