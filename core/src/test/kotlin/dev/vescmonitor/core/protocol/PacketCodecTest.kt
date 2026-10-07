package dev.vescmonitor.core.protocol

import dev.vescmonitor.core.bytes
import dev.vescmonitor.core.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PacketCodecTest {
    @Test
    fun `crc check value`() {
        assertEquals(0x31C3, Crc16.compute("123456789".toByteArray()))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "FW_VERSION, 00, 02 01 00 00 00 03",
        "GET_VALUES, 04, 02 01 04 40 84 03",
        "GET_VALUES_SETUP, 2F, 02 01 2F D5 8D 03",
        "PING_CAN, 3E, 02 01 3E D7 9D 03",
        "FORWARD_CAN 1 GET_VALUES, 22 01 04, 02 03 22 01 04 9B 13 03",
        "GET_VALUES_SELECTIVE fast, 32 00 02 81 CF, 02 05 32 00 02 81 CF 26 07 03",
    )
    fun `golden request frames`(
        name: String,
        payload: String,
        frame: String,
    ) {
        val p = dev.vescmonitor.core.hex(payload)
        assertEquals(frame, PacketCodec.encode(p).toHex(), name)
        assertEquals(frame, GuardedFrame.of(p).bytes.toHex(), name)
    }

    @Test
    fun `request builders match the golden payloads`() {
        assertEquals("32 00 02 81 CF", Requests.getValuesSelective(ValuesLayout.MASK_FAST).toHex())
        assertEquals("22 01 04", Requests.forwardCan(1, Requests.getValues()).toHex())
    }

    @Test
    fun `long frame uses two length bytes`() {
        val payload = ByteArray(300) { it.toByte() }
        val frame = PacketCodec.encode(payload)
        assertEquals(0x03, frame[0].toInt())
        assertEquals(bytes(0x01, 0x2C).toHex(), frame.copyOfRange(1, 3).toHex())
        assertEquals(300 + 6, frame.size)
    }

    @Test
    fun `255 bytes still use the short frame`() {
        assertEquals(0x02, PacketCodec.encode(ByteArray(255)).first().toInt())
    }

    @Test
    fun `payload limits`() {
        assertThrows<IllegalArgumentException> { PacketCodec.encode(ByteArray(0)) }
        assertThrows<IllegalArgumentException> { PacketCodec.encode(ByteArray(513)) }
    }
}
