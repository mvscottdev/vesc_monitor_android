package dev.vescmonitor.core.replay

import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.session.ConnectionState
import dev.vescmonitor.core.session.VehicleSession
import dev.vescmonitor.core.sim.FakeVesc
import dev.vescmonitor.core.sim.SyntheticTransport
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/**
 * Replays the committed synthetic captures (1 and 2 controllers) through a full session.
 * Regenerate them with `-DregenerateFixtures=true`.
 */
class ReplayTest {
    private fun fixture(count: Int) = "/captures/synthetic-$count.jsonl"

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `replayed capture gives the recorded topology and voltages`(count: Int) =
        runTest {
            val text = javaClass.getResource(fixture(count))!!.readText()
            val clock = Clock { testScheduler.currentTime }
            val transport = ReplayTransport(CaptureRecord.decodeAll(text), backgroundScope)
            val s = VehicleSession(transport, clock, backgroundScope, generation = 1)
            s.start()
            advanceTimeBy(1_500)
            assertEquals(ConnectionState.CONNECTED, s.snapshot.value.state)
            val frame = s.frame()!!
            assertEquals(count, frame.vescs.size)
            assertTrue(frame.vescs.all { it.fresh && it.voltageV != null })
            assertEquals(0, transport.mismatchedWrites)
        }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `regenerate capture fixtures`(count: Int) =
        runTest {
            assumeTrue(System.getProperty("regenerateFixtures") == "true")
            val clock = Clock { testScheduler.currentTime }
            val synthetic = SyntheticTransport(FakeVesc.synthetic(count), backgroundScope, clock)
            val rec = RecordingTransport(synthetic, clock)
            val lines = ArrayList<String>()
            rec.startRecording({ lines += it }, mapOf("source" to "synthetic", "controllers" to count.toString()))
            val s = VehicleSession(rec, clock, backgroundScope, generation = 1)
            s.start()
            advanceTimeBy(2_000)
            rec.stopRecording()
            File("../fixtures${fixture(count)}").apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = "\n"))
        }
}
