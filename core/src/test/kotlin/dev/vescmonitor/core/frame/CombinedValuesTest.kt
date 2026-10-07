package dev.vescmonitor.core.frame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CombinedValuesTest {
    private val front = ControllerValues(48.0, 61.0, 38.0, 14.2, 685.9, 0.41, 18_500.0)
    private val rear = ControllerValues(52.0, 68.0, 44.5, 15.3, 740.0, -0.46, 20_800.0)

    @Test
    fun `one controller is itself`() {
        val c = CombinedValues.of(listOf(front))
        assertEquals(CombinedValues(14.2, 38.0, 685.9, 0.41, 48.0, 61.0), c)
    }

    @Test
    fun `two controllers sum currents and power, take max duty and hottest sensor`() {
        val c = CombinedValues.of(listOf(front, rear))
        assertEquals(29.5, c.currentInA!!, 1e-9)
        assertEquals(82.5, c.currentMotorA!!, 1e-9)
        assertEquals(1425.9, c.powerW!!, 1e-9)
        assertEquals(-0.46, c.duty!!, 1e-9)
        assertEquals(52.0, c.tempMosC)
        assertEquals(68.0, c.tempMotorC)
    }

    @Test
    fun `an absent sensor is skipped, an unknown value on every controller stays unknown`() {
        val c = CombinedValues.of(listOf(front.copy(tempMotorC = null, powerW = null), rear.copy(powerW = null)))
        assertEquals(68.0, c.tempMotorC)
        assertNull(c.powerW)
    }

    @Test
    fun `no fresh controller gives unknown values`() {
        assertEquals(CombinedValues.EMPTY, CombinedValues.of(emptyList()))
    }
}
