package dev.vescmonitor.core.protocol

import dev.vescmonitor.core.bytes
import dev.vescmonitor.core.hex
import dev.vescmonitor.core.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReassemblerTest {
    private val r = Reassembler()
    private val fw = hex("02 01 00 00 00 03")
    private val values = PacketCodec.encode(ByteArray(74) { if (it == 0) 4 else it.toByte() })

    private fun feedAll(
        chunks: List<ByteArray>,
        stepMs: Long = 5,
    ): List<ByteArray> = chunks.flatMapIndexed { i, c -> r.feed(c, i * stepMs) }

    @Test
    fun `packet split over 20-byte notifications`() {
        val chunks = values.toList().chunked(20).map { it.toByteArray() }
        val out = feedAll(chunks)
        assertEquals(1, out.size)
        assertEquals(74, out[0].size)
    }

    @Test
    fun `several packets in one notification`() {
        val out = r.feed(fw + fw + values, 0)
        assertEquals(3, out.size)
        assertEquals("00", out[0].toHex())
    }

    @Test
    fun `garbage before and between packets is skipped`() {
        val out = r.feed(bytes(0x55, 0xAA, 0x03) + fw + bytes(0x00, 0x04, 0x02) + fw, 0)
        assertEquals(2, out.size)
    }

    @Test
    fun `bad crc drops one byte and resyncs`() {
        val bad = fw.copyOf().also { it[3] = 0x11 }
        val out = r.feed(bad + fw, 0)
        assertEquals(1, out.size)
        assertEquals(1, r.crcErrors)
    }

    @Test
    fun `silence resets a partial packet`() {
        r.feed(values.copyOfRange(0, 30), 0)
        val out = r.feed(values.copyOfRange(30, values.size), Reassembler.SILENCE_RESET_MS + 1)
        assertTrue(out.isEmpty())
        assertEquals(1, r.feed(fw, Reassembler.SILENCE_RESET_MS + 2).size)
    }

    @Test
    fun `gap within the silence window keeps the partial packet`() {
        r.feed(values.copyOfRange(0, 30), 0)
        assertEquals(1, r.feed(values.copyOfRange(30, values.size), Reassembler.SILENCE_RESET_MS).size)
    }

    @Test
    fun `short frame with length zero is rejected`() {
        assertTrue(r.feed(bytes(0x02, 0x00, 0x00, 0x00, 0x03), 0).isEmpty())
        assertEquals(1, r.feed(fw, 1).size)
    }

    @Test
    fun `long frame shorter than 256 is rejected`() {
        val payload = ByteArray(200) { 4 }
        val crc = Crc16.compute(payload)
        val frame = bytes(0x03, 0x00, 200) + payload + bytes(crc ushr 8, crc and 0xFF, 0x03)
        assertTrue(r.feed(frame, 0).isEmpty())
    }

    @Test
    fun `long frame over 512 is rejected`() {
        val payload = ByteArray(513) { 4 }
        val crc = Crc16.compute(payload)
        val frame = bytes(0x03, 0x02, 0x01) + payload + bytes(crc ushr 8, crc and 0xFF, 0x03)
        assertTrue(r.feed(frame, 0).isEmpty())
    }

    @Test
    fun `valid long frame is accepted`() {
        val payload = ByteArray(400) { 14 }
        assertEquals(400, r.feed(PacketCodec.encode(payload), 0).single().size)
    }

    @Test
    fun `three-byte-length frames are never accepted`() {
        val payload = ByteArray(10) { 4 }
        val crc = Crc16.compute(payload)
        val frame = bytes(0x04, 0x00, 0x00, 10) + payload + bytes(crc ushr 8, crc and 0xFF, 0x03)
        assertTrue(r.feed(frame, 0).isEmpty())
    }

    @Test
    fun `missing end byte is rejected`() {
        val bad = fw.copyOf().also { it[5] = 0x07 }
        assertTrue(r.feed(bad, 0).isEmpty())
    }

    @Test
    fun `oversize garbage stream does not overflow`() {
        val junk = ByteArray(5000) { 0x55 }
        assertTrue(r.feed(junk, 0).isEmpty())
        assertEquals(1, r.feed(fw, 1).size)
    }
}
