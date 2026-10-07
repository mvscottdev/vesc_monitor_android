package dev.vescmonitor.core.session

import dev.vescmonitor.core.frame.CombinedValues
import dev.vescmonitor.core.frame.Derived
import dev.vescmonitor.core.frame.TelemetryFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StatusTextTest {
    private val frame =
        TelemetryFrame(
            seq = 1,
            tMs = 0,
            generation = 1,
            combined =
                TelemetryFrame.Combined(
                    48.0,
                    2,
                    2,
                    false,
                    CombinedValues(powerW = 1840.0),
                    derived = Derived(speedMps = 37 / 3.6, socPct = 72.4, tripM = 12_400.0),
                ),
            vescs = emptyList(),
        )

    @Test
    fun `riding shows speed, charge and power, then source, rate and trip, with the ride time`() {
        val t = StatusText.of(ConnectionState.CONNECTED, 0, "ble", 18.2, frame, 761_000, mph = false)
        assertEquals("37 km/h · 72 % · 1.84 kW", t.title)
        assertEquals("VESC BLE connected · 18 Hz · 12.4 km", t.text)
        assertEquals("REC", t.sub)
    }

    @Test
    fun `imperial units`() {
        val t = StatusText.of(ConnectionState.CONNECTED, 0, "ble", null, frame, null, mph = true)
        assertEquals("23 mph · 72 % · 1.84 kW", t.title)
        assertEquals("VESC BLE connected · 7.7 mi", t.text)
        assertNull(t.sub)
    }

    @Test
    fun `reconnecting names the attempt and that the ride keeps recording`() {
        val t = StatusText.of(ConnectionState.RECONNECTING, 3, "ble", null, frame, 5_000, mph = false)
        assertEquals("Reconnecting (attempt 3)", t.title)
        assertEquals("Connection lost · retrying. The ride keeps recording.", t.text)
    }

    @Test
    fun `connected before the first frame`() {
        assertEquals("Connected", StatusText.of(ConnectionState.CONNECTED, 0, "synthetic-2", null, null, null, false).title)
        assertEquals("Synthetic connected", StatusText.of(ConnectionState.CONNECTED, 0, "synthetic-2", null, null, null, false).text)
    }
}
