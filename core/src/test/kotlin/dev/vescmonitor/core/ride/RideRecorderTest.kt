package dev.vescmonitor.core.ride

import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.session.ControllerLive
import dev.vescmonitor.core.session.SessionListener
import dev.vescmonitor.core.session.VehicleSession
import dev.vescmonitor.core.sim.FakeVesc
import dev.vescmonitor.core.sim.SyntheticTransport
import dev.vescmonitor.core.store.MemoryStore
import dev.vescmonitor.core.store.StreamInfo
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Synthetic session → recorder → in-memory store, for 1 and 2 controllers. */
class RideRecorderTest {
    private class Rig(
        val session: VehicleSession,
        val recorder: RideRecorder,
        val store: MemoryStore,
    )

    private fun TestScope.rig(count: Int): Rig {
        val clock = Clock { testScheduler.currentTime }
        val store = MemoryStore()
        val recorder = RideRecorder(store, backgroundScope)
        val listener =
            object : SessionListener {
                override suspend fun onPolling(controllers: List<ControllerLive>) {
                    recorder.open(clock.nowMs(), controllers.mapIndexed { i, c -> StreamInfo(i, c.controller.canId, c.controller.isLocal) })
                }

                override fun onSample(
                    index: Int,
                    c: ControllerLive,
                ) = recorder.onSample(index, clock.nowMs(), c.values)
            }
        val transport = SyntheticTransport(FakeVesc.synthetic(count), backgroundScope, clock)
        return Rig(VehicleSession(transport, clock, backgroundScope, 1, listener = listener), recorder, store)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `a ride records every sample of every controller and closes with a summary`(count: Int) =
        runTest {
            val r = rig(count)
            r.session.start()
            advanceTimeBy(40_000)
            r.recorder.close()
            runCurrent()
            val capture =
                r.store.captures.values
                    .single()
            assertEquals("closed", capture.state)
            assertEquals(count, capture.streams.size)
            assertEquals(
                count,
                capture.chunks
                    .map { it.stream }
                    .distinct()
                    .size,
            )
            val s = capture.summary!!
            assertTrue(s.durationMs in 35_000..40_000, "duration ${s.durationMs}")
            assertTrue(s.samples > 35 * count * 10, "samples ${s.samples}")
            assertTrue(s.peakPowerW > 1000 * count, "peak ${s.peakPowerW}")
            assertTrue(s.peakRegenW < 0, "regen ${s.peakRegenW}")
            assertTrue(s.minVoltageV!! < 48.0)
            assertTrue(s.maxTempMotorC!! > 30.0)
            // One store call per second, not one per chunk.
            assertTrue(capture.writes <= 42, "writes ${capture.writes}")
            assertEquals(0L, r.recorder.droppedChunks)
        }

    @ParameterizedTest
    @ValueSource(ints = [2])
    fun `storage stays near the budget`(count: Int) =
        runTest {
            val r = rig(count)
            r.session.start()
            advanceTimeBy(60_000)
            r.recorder.close()
            runCurrent()
            val c =
                r.store.captures.values
                    .single()
            val hz = c.summary!!.samples / 60.0 / count
            val bytesPerHour = c.summary!!.bytes * 60.0
            // Budget ~11 B x controllers x Hz per second of riding (+ chunk overhead) - synthetic, smooth data.
            val budget = 11.0 * count * hz * 3600 * 2
            assertTrue(bytesPerHour < budget, "bytes/h $bytesPerHour at $hz Hz, budget $budget")
        }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `a ride left recording by a kill is recovered from its chunks`(count: Int) =
        runTest {
            val r = rig(count)
            r.session.start()
            advanceTimeBy(10_000)
            // Simulated kill: the ride is never closed. A new recorder on the next start recovers it.
            val next = RideRecorder(r.store, backgroundScope)
            next.recover()
            val capture =
                r.store.captures.values
                    .single()
            assertEquals("recovered", capture.state)
            assertTrue(capture.summary!!.samples > 0)
        }
}
