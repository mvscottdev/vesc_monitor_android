package dev.vescmonitor.core.replay

import dev.vescmonitor.core.bytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaptureTest {
    @Test
    fun `flat json round trip with escapes`() {
        val m = mapOf("a" to "x\"y\\z\n", "n" to 12L, "d" to 1.5, "b" to true, "z" to null)
        val back = FlatJson.parse(FlatJson.write(m))
        assertEquals("x\"y\\z\n", back["a"])
        assertEquals(12.0, back["n"])
        assertEquals(1.5, back["d"])
        assertEquals(true, back["b"])
        assertNull(back["z"])
    }

    @Test
    fun `records round trip`() {
        val chunk = CaptureRecord.BleChunk(42, CaptureRecord.Direction.RX, bytes(2, 1, 0, 0, 0, 3))
        val line = CaptureRecord.encode(chunk)
        assertEquals("""{"type":"ble-chunk","t":42,"direction":"rx","base64":"AgEAAAAD"}""", line)
        val back = CaptureRecord.decode(line) as CaptureRecord.BleChunk
        assertEquals(42, back.t)
        assertArrayEquals(chunk.bytes, back.bytes)
        val meta = CaptureRecord.decode(CaptureRecord.encode(CaptureRecord.Meta(0, mapOf("mtu" to "23"))))
        assertEquals(CaptureRecord.Meta(0, mapOf("mtu" to "23")), meta)
        assertEquals(
            CaptureRecord.SessionState(7, "connected"),
            CaptureRecord.decode("""{"type":"session-state","t":7,"state":"connected"}"""),
        )
    }

    @Test
    fun `unknown record types are skipped`() {
        assertNull(CaptureRecord.decode("""{"type":"future","t":1}"""))
    }
}
