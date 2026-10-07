package dev.vescmonitor.core.session

import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.protocol.ValuesParser
import dev.vescmonitor.core.sim.ReplyEncoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveValuesTest {
    private fun sample(
        mask: Int,
        v: Map<String, Double>,
    ) = ValuesParser.parseSelective(ValuesLayout.VALUES, ReplyEncoder.values(ValuesLayout.VALUES, mask, v))

    @Test
    fun `fast mask values are kept in SI units`() {
        val live = LiveValues()
        live.apply(sample(ValuesLayout.MASK_FAST, mapOf("voltageV" to 72.4, "currentInA" to 10.0, "duty" to 0.5, "tempMosC" to 41.3)))
        assertEquals(72.4, live.voltageV, 1e-9)
        assertEquals(724.0, live.powerW, 1e-6)
        assertEquals(0.5, live.duty, 1e-9)
        assertEquals(41.3, live.tempMosC, 1e-9)
    }

    @Test
    fun `a missing sensor reads as unknown, not -100 degC`() {
        val live = LiveValues()
        live.apply(sample(ValuesLayout.MASK_FAST, mapOf("tempMotorC" to -100.0)))
        assertTrue(live.tempMotorC.isNaN())
    }

    @Test
    fun `the slow mask keeps the last fast values`() {
        val live = LiveValues()
        live.apply(sample(ValuesLayout.MASK_FAST, mapOf("voltageV" to 72.4, "currentInA" to 10.0)))
        live.apply(sample(ValuesLayout.MASK_SLOW, emptyMap()))
        assertEquals(72.4, live.voltageV, 1e-9)
        assertEquals(10.0, live.currentInA, 1e-9)
    }

    @Test
    fun `nothing seen yet is unknown`() {
        assertTrue(LiveValues().currentInA.isNaN())
    }
}
