package dev.vescmonitor.core.frame

import dev.vescmonitor.core.battery.BatteryDetect
import dev.vescmonitor.core.battery.Chemistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The golden sample and the Kotlin frame must have the same keys and value kinds. */
class ContractTest {
    private val golden = JSONObject(javaClass.getResource("/telemetry-frame.v1.json")!!.readText())

    private val frame =
        TelemetryFrame(
            seq = 1234,
            tMs = 5678901,
            generation = 3,
            combined =
                TelemetryFrame.Combined(
                    48.35,
                    2,
                    2,
                    false,
                    CombinedValues(30.5, 82.5, 1474.6, 0.46, 52.1, 68.4),
                    TelemetryFrame.Peaks(2210.0, -640.0),
                    null,
                    Derived(
                        speedMps = 8.4,
                        speedValid = true,
                        maxSpeedMps = 12.5,
                        tripM = 3250.0,
                        whUsed = 58.5,
                        whPerKm = 18.0,
                        socPct = 62.5,
                        cellV = 3.72,
                        cells = 13,
                        chemistry = Chemistry.NMC,
                        socConfidence = BatteryDetect.Confidence.MEDIUM,
                        sagV = 2.4,
                        minVoltageV = 44.1,
                        capacityAh = 12.1,
                        rangeKm = 9.6,
                    ),
                ),
            vescs =
                listOf(
                    TelemetryFrame.VescEntry(
                        10,
                        true,
                        48.3,
                        true,
                        ControllerValues(48.0, 61.0, 38.0, 14.2, 685.86, 0.41, 18500.0, 0),
                        40,
                        8.4,
                    ),
                    TelemetryFrame.VescEntry(
                        20,
                        false,
                        48.4,
                        true,
                        ControllerValues(52.1, 68.4, 44.5, 16.3, 788.92, 0.46, 20800.0, 0),
                        25,
                        8.5,
                    ),
                ),
        )

    @Test
    fun `version matches`() {
        assertEquals(TelemetryFrame.VERSION, golden.getInt("v"))
    }

    @Test
    fun `frame map equals the golden sample`() {
        assertEquals(normalize(golden), normalize(JSONObject(frame.toMap())))
    }

    /** Numbers compared as doubles so 1 and 1.0 match. */
    private fun normalize(v: Any?): Any? =
        when (v) {
            is JSONObject -> v.keySet().associateWith { normalize(v.get(it)) }
            is JSONArray -> (0 until v.length()).map { normalize(v.get(it)) }
            is Number -> v.toDouble()
            JSONObject.NULL -> null
            else -> v
        }
}
