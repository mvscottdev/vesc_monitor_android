package dev.vescmonitor.core.frame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CombinedVoltageTest {
    @Test
    fun `none fresh`() {
        assertNull(CombinedVoltage.of(emptyList()).voltageV)
    }

    @Test
    fun `one controller is itself`() {
        assertEquals(48.3, CombinedVoltage.of(listOf(48.3)).voltageV)
    }

    @Test
    fun `two within tolerance give the mean`() {
        val c = CombinedVoltage.of(listOf(48.0, 48.4))
        assertEquals(48.2, c.voltageV!!, 1e-9)
        assertFalse(c.divergent)
    }

    @Test
    fun `tolerance is one percent above 50 V`() {
        // 1 % of 80 V = 0.8 V > 0.5 V
        assertFalse(CombinedVoltage.of(listOf(80.0, 80.7)).divergent)
        assertTrue(CombinedVoltage.of(listOf(80.0, 80.9)).divergent)
    }

    @Test
    fun `two apart give the lower with a divergence flag`() {
        val c = CombinedVoltage.of(listOf(48.0, 47.0))
        assertEquals(47.0, c.voltageV)
        assertTrue(c.divergent)
    }

    @Test
    fun `three or more give the median`() {
        assertEquals(48.1, CombinedVoltage.of(listOf(48.5, 47.0, 48.1)).voltageV)
        assertEquals(48.2, CombinedVoltage.of(listOf(48.5, 47.0, 48.1, 48.3)).voltageV!!, 1e-9)
    }
}
