package dev.vescmonitor.core.store

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

class ChunkCodecTest {
    private fun roundTrip(columns: Map<Int, LongArray>) {
        val n = columns.values.first().size
        val d = ChunkCodec.decode(ChunkCodec.encode(n, columns, mapOf(2 to 123_456L)))
        assertEquals(n, d.n)
        columns.forEach { (id, col) -> assertArrayEquals(col, d.columns[id], "column $id") }
        assertEquals(123_456L, d.keyframe[2])
    }

    @Test
    fun `round trip of smooth, noisy, constant and extreme columns`() {
        val rnd = Random(7)
        roundTrip(
            mapOf(
                0 to LongArray(20) { 1_000_000L + it * 50 },
                1 to LongArray(20) { 724L + rnd.nextInt(-3, 4) },
                2 to LongArray(20) { rnd.nextLong() },
                3 to LongArray(20) { SampleField.MISSING },
                4 to LongArray(20) { if (it % 2 == 0) Long.MAX_VALUE else Long.MIN_VALUE },
            ),
        )
    }

    @Test
    fun `random round trips`() {
        val rnd = Random(42)
        repeat(200) {
            val n = rnd.nextInt(1, 60)
            roundTrip(mapOf(0 to LongArray(n) { rnd.nextLong(-1_000_000, 1_000_000) }, 9 to LongArray(n) { rnd.nextLong() }))
        }
    }

    @Test
    fun `regenerate the golden blob`() {
        assumeTrue(System.getProperty("regenerateFixtures") == "true")
        val bytes =
            ChunkCodec.encode(
                5,
                mapOf(
                    SampleField.T_MS.id to longArrayOf(1000, 1050, 1100, 1150, 1200),
                    SampleField.V_IN.id to longArrayOf(724, 723, 721, 722, 724),
                ),
                mapOf(KeyframeField.WATT_HOURS.id to 51_234L),
            )
        File("../fixtures/chunks").mkdirs()
        File("../fixtures/chunks/golden-v1.bin").writeBytes(bytes)
    }

    @Test
    fun `golden blob decodes to the same values`() {
        // Encoded once with codec version 1; a codec change must keep old chunks readable.
        val golden = javaClass.getResource("/chunks/golden-v1.bin")!!.readBytes()
        val d = ChunkCodec.decode(golden)
        assertEquals(5, d.n)
        assertArrayEquals(longArrayOf(1000, 1050, 1100, 1150, 1200), d.columns[SampleField.T_MS.id])
        assertArrayEquals(longArrayOf(724, 723, 721, 722, 724), d.columns[SampleField.V_IN.id])
        assertEquals(51_234L, d.keyframe[KeyframeField.WATT_HOURS.id])
    }

    @Test
    fun `unknown field ids are kept apart and ignored by readers`() {
        val d = ChunkCodec.decode(ChunkCodec.encode(2, mapOf(0 to longArrayOf(1, 2), 200 to longArrayOf(5, 5))))
        assertArrayEquals(longArrayOf(1, 2), d.columns[0])
    }

    @Test
    fun `garbage is rejected, not decoded as a guess`() {
        assertThrows(CodecException::class.java) { ChunkCodec.decode(byteArrayOf(1, 2, 3, 4)) }
    }
}
