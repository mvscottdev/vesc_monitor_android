package dev.vescmonitor.core.protocol

import dev.vescmonitor.core.bytes
import dev.vescmonitor.core.hex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ParsersTest {
    /** Hand-built 5.03+ GET_VALUES reply (74 B), fields in firmware order. */
    private val fullValues =
        hex(
            "04 " +
                "01 63 01 92 " + // temp_mos 35.5, temp_motor 40.2
                "00 00 04 D2 FF FF FE BF " + // current_motor 12.34, current_in -3.21
                "00 00 00 00 00 00 00 00 " + // id, iq
                "01 C8 FF FF D1 20 02 D4 " + // duty 0.456, erpm -12000, v_in 72.4
                "00 00 30 39 00 00 00 00 00 00 00 00 00 00 00 00 " + // Ah 1.2345, Ah ch, Wh, Wh ch
                "00 00 03 E8 00 00 03 E8 00 " + // tach 1000, tach_abs 1000, fault 0
                "00 00 00 00 0A " + // pid_pos, controller_id 10
                "01 5E 01 5F 01 60 " + // temp_mos_1..3
                "00 00 00 00 00 00 00 00 00", // vd, vq, status
        )

    @Test
    fun `full GET_VALUES 5_03 layout`() {
        assertEquals(74, fullValues.size)
        assertEquals(74, ValuesLayout.VALUES.fullPayloadLength)
        val s = ValuesParser.parseFull(ValuesLayout.VALUES, fullValues)
        assertEquals(35.5, s["tempMosC"], 1e-9)
        assertEquals(40.2, s["tempMotorC"], 1e-9)
        assertEquals(12.34, s["currentMotorA"], 1e-9)
        assertEquals(-3.21, s["currentInA"], 1e-9)
        assertEquals(0.456, s["duty"], 1e-9)
        assertEquals(-12000.0, s["erpm"], 1e-9)
        assertEquals(72.4, s["voltageV"], 1e-9)
        assertEquals(1.2345, s["ampHours"], 1e-9)
        assertEquals(10, s.controllerId)
        assertEquals(35.2, s.values[ValuesLayout.VALUES.field("tempMos123C").slot + 2], 1e-9)
        assertFalse(s.untestedFirmware)
    }

    @Test
    fun `longer reply parses the known prefix and flags untested firmware`() {
        val s = ValuesParser.parseFull(ValuesLayout.VALUES, fullValues + bytes(1, 2, 3))
        assertEquals(72.4, s["voltageV"], 1e-9)
        assertTrue(s.untestedFirmware)
    }

    @Test
    fun `reply cut short is rejected`() {
        assertThrows<ParseException> { ValuesParser.parseFull(ValuesLayout.VALUES, fullValues.copyOf(73)) }
        assertThrows<ParseException> { ValuesParser.parseFull(ValuesLayout.VALUES, fullValues.copyOf(59)) }
    }

    @Test
    fun `selective fast mask`() {
        val payload =
            hex("32 00 02 81 CF") +
                hex("01 63 01 92 00 00 04 D2 FF FF FE BF 01 C8 FF FF D1 20 02 D4 00 0B")
        assertEquals(27, payload.size)
        val s = ValuesParser.parseSelective(ValuesLayout.VALUES, payload)
        assertEquals(ValuesLayout.MASK_FAST, s.mask)
        assertEquals(72.4, s["voltageV"], 1e-9)
        assertEquals(11, s.controllerId)
        assertTrue(s["ampHours"].isNaN())
        assertEquals(ValuesLayout.MASK_FAST, ValuesParser.echoedMask(payload))
    }

    @Test
    fun `selective reply cut short or with unknown bits is rejected`() {
        assertThrows<ParseException> { ValuesParser.parseSelective(ValuesLayout.VALUES, hex("32 00 02 81 CF 01 63")) }
        assertThrows<ParseException> { ValuesParser.parseSelective(ValuesLayout.VALUES, hex("32 00 40 00 00 01")) }
    }

    @Test
    fun `full GET_VALUES_SETUP 5_03 layout is 70 bytes`() {
        assertEquals(70, ValuesLayout.SETUP.fullPayloadLength)
        val payload = ByteArray(70).also { it[0] = CommandId.GET_VALUES_SETUP.toByte() }
        // speed at offset 1 + 2+2+4+4+2+4 = 19: 12.345 m/s
        hex("00 00 30 39").copyInto(payload, 19)
        val s = ValuesParser.parseFull(ValuesLayout.SETUP, payload)
        assertEquals(12.345, s["speedMps"], 1e-9)
    }

    @Test
    fun `FW_VERSION 6_05 with fw name and hw crc`() {
        val p =
            hex("00 06 05") + "75_100_V2".toByteArray() + bytes(0) + ByteArray(12) + bytes(1, 0, 0, 0, 0, 0, 0, 0) + bytes(0) +
                bytes(0, 0, 0, 0)
        val fw = FwVersion.parse(p)
        assertEquals(6, fw.major)
        assertEquals(5, fw.minor)
        assertEquals("75_100_V2", fw.hwName)
        assertEquals(0, fw.hwType)
        assertEquals("", fw.fwName)
        assertTrue(fw.isSupported)
        assertTrue(fw.isMotorController)
        assertFalse(fw.untestedFirmware)
    }

    @Test
    fun `FW_VERSION 5_03 without fw name`() {
        val p = hex("00 05 03") + "60".toByteArray() + bytes(0) + ByteArray(12) + bytes(1, 0, 0, 0, 0, 0, 0, 0)
        val fw = FwVersion.parse(p)
        assertEquals("5.03", fw.label)
        assertNull(fw.fwName)
        assertTrue(fw.isSupported)
    }

    @Test
    fun `BMS reply is short and not a motor controller`() {
        val p = hex("00 06 00") + "BMS_A".toByteArray() + bytes(0) + ByteArray(12) + bytes(0, 0, 1, 0)
        val fw = FwVersion.parse(p)
        assertEquals(FwVersion.HW_TYPE_BMS, fw.hwType)
        assertFalse(fw.isMotorController)
    }

    @Test
    fun `firmware older than 5_03 is unsupported and hw type defaults to motor`() {
        val p = hex("00 05 02") + "60".toByteArray() + bytes(0) + ByteArray(12) + bytes(1, 0)
        val fw = FwVersion.parse(p)
        assertFalse(fw.isSupported)
        assertEquals(FwVersion.HW_TYPE_VESC, fw.hwType)
    }

    @Test
    fun `FW_VERSION cut inside the uuid is rejected`() {
        assertThrows<ParseException> { FwVersion.parse(hex("00 06 05 36 30 00 01 02")) }
    }

    @Test
    fun `PING_CAN and battery cut`() {
        assertEquals(listOf(1, 20), SimpleReplies.parsePingCan(hex("3E 01 14")))
        assertEquals(emptyList<Int>(), SimpleReplies.parsePingCan(hex("3E")))
        val cut = SimpleReplies.parseBatteryCut(hex("73 00 01 11 70 00 00 FD E8"))
        assertEquals(70.0, cut.startV, 1e-9)
        assertEquals(65.0, cut.endV, 1e-9)
        assertThrows<ParseException> { SimpleReplies.parseBatteryCut(hex("73 00 01")) }
    }
}
