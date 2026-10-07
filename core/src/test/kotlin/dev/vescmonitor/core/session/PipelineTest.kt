package dev.vescmonitor.core.session

import dev.vescmonitor.core.frame.TelemetryFrame
import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.link.Reason
import dev.vescmonitor.core.sim.FakeVesc
import dev.vescmonitor.core.sim.SyntheticTransport
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Whole pipeline on the synthetic transport: connect → discovery → polling → frames. */
class PipelineTest {
    private fun TestScope.session(
        count: Int,
        reconnect: Boolean = false,
    ): Pair<VehicleSession, SyntheticTransport> {
        val clock = Clock { testScheduler.currentTime }
        val transport = SyntheticTransport(FakeVesc.synthetic(count), backgroundScope, clock)
        return VehicleSession(transport, clock, backgroundScope, generation = 7, reconnect = reconnect) to transport
    }

    private fun TestScope.framesOver(
        s: VehicleSession,
        ms: Long,
    ): List<TelemetryFrame> {
        val frames = ArrayList<TelemetryFrame>()
        repeat((ms / 33).toInt()) {
            advanceTimeBy(33)
            s.frame()?.let { frames += it }
        }
        return frames
    }

    @Test
    fun `two synthetic controllers give frames with two changing voltages`() =
        runTest {
            val (s, _) = session(2)
            s.start()
            val frames = framesOver(s, 6_000).drop(60)
            assertEquals(ConnectionState.CONNECTED, s.snapshot.value.state)
            assertTrue(frames.size > 50)
            val last = frames.last()
            assertEquals(7, last.generation)
            assertEquals(listOf(10, 20), last.vescs.map { it.controllerId })
            assertEquals(2, last.combined.fresh)
            for (i in 0..1) {
                val volts = frames.mapNotNull { it.vescs[i].voltageV }.distinct()
                assertTrue(volts.size > 10, "controller $i voltages $volts")
            }
            assertTrue(frames.zipWithNext().all { (a, b) -> b.seq > a.seq })
            assertTrue(last.combined.voltageV!! in 40.0..51.0)
            val powers = frames.mapNotNull { it.combined.values.powerW }.distinct()
            assertTrue(powers.size > 10, "combined power $powers")
            assertTrue(last.vescs.all { it.values.tempMotorC != null && it.values.duty != null })
        }

    @Test
    fun `synthetic ride derives speed, trip, battery and sag`() =
        runTest {
            for (count in 1..2) {
                val (s, _) = session(count)
                s.start()
                val frames = framesOver(s, 40_000)
                val d = frames.map { it.combined.derived }
                assertTrue(d.mapNotNull { it.speedMps }.distinct().size > 20, "speed moves ($count)")
                assertTrue(frames.last().vescs.all { it.speedMps != null || !it.fresh })
                val last = d.last()
                assertTrue(last.maxSpeedMps > 5.0, "max ${last.maxSpeedMps}")
                assertTrue(last.tripM > 50.0, "trip ${last.tripM}")
                assertTrue(last.cells != null && last.socPct != null, "battery $last")
                assertTrue(d.mapNotNull { it.sagV }.any { it > 0.5 }, "sag rises under load ($count)")
                assertTrue(last.minVoltageV!! < frames.last().combined.voltageV!!)
                s.stop()
            }
        }

    @Test
    fun `synthetic ride raises and clears alerts`() =
        runTest {
            val (s, _) = session(2)
            s.start()
            val seen = HashSet<String>()
            repeat(2_400) {
                advanceTimeBy(100)
                s.frame()?.let { f ->
                    s.evaluateAlerts(f)
                    s.activeAlerts.forEach { a -> seen += "${a.key}:${a.severity}" }
                }
            }
            assertTrue(seen.isNotEmpty(), "no alert in 4 min of synthetic riding")
            assertTrue(seen.none { it.startsWith("link_stale") || it.startsWith("fault") }, "$seen")
            s.stop()
        }

    @Test
    fun `speed test on the synthetic ride - arm at a stop, time the next launch`() =
        runTest {
            for (count in 1..2) {
                val (s, _) = session(count)
                assertTrue(s.armRun() != null, "arming before connect is refused")
                s.start()
                // Learn the speed ratio, then arm during the stop phase of the 30 s cycle.
                repeat(560) {
                    advanceTimeBy(50)
                    s.frame()
                }
                assertNull(s.armRun())
                var last: Map<String, Any?> = emptyMap()
                repeat(800) {
                    advanceTimeBy(50)
                    s.frame()
                    last = s.runMap(testScheduler.currentTime)
                }
                // The synthetic ride brakes after its cruise: done, or a lift-off once 0-60 is in.
                assertTrue(last["state"] == "done" || (last["state"] == "aborted" && last["abort"] == "lift_off"), "run $last")
                @Suppress("UNCHECKED_CAST")
                val brackets = last["brackets"] as List<Map<String, Any?>>
                val first = brackets.first()["rolloutMs"] as Long
                assertTrue(first in 500..15_000, "0-30 in $first ms")
                s.stop()
            }
        }

    @Test
    fun `one synthetic controller`() =
        runTest {
            val (s, _) = session(1)
            s.start()
            val last = framesOver(s, 3_000).last()
            assertEquals(1, last.vescs.size)
            assertEquals(last.vescs.single().voltageV, last.combined.voltageV)
        }

    @Test
    fun `link loss goes to lost with a reason and stops polling`() =
        runTest {
            val (s, t) = session(2)
            s.start()
            advanceTimeBy(3_000)
            t.simulateLoss(Reason.LINK_LOST)
            runCurrent()
            assertEquals(ConnectionState.LOST, s.snapshot.value.state)
            assertEquals(Reason.LINK_LOST, s.snapshot.value.reason)
            advanceTimeBy(3_000)
            assertTrue(s.frame()!!.vescs.none { it.fresh })
        }

    @Test
    fun `with reconnect on, a lost link retries with backoff and polls again`() =
        runTest {
            val (s, t) = session(2, reconnect = true)
            s.start()
            advanceTimeBy(3_000)
            t.refuseConnects = 2
            t.simulateLoss(Reason.LINK_LOST)
            runCurrent()
            assertEquals(ConnectionState.RECONNECTING, s.snapshot.value.state)
            assertEquals(1, s.snapshot.value.attempt)
            advanceTimeBy(600)
            assertEquals(2, s.snapshot.value.attempt)
            advanceTimeBy(1_100)
            assertEquals(3, s.snapshot.value.attempt)
            advanceTimeBy(3_000)
            assertEquals(ConnectionState.CONNECTED, s.snapshot.value.state)
            assertEquals(0, s.snapshot.value.attempt)
            assertNull(s.snapshot.value.reason)
            advanceTimeBy(1_000)
            assertEquals(2, s.frame()!!.combined.fresh)
            s.stop()
            assertEquals(ConnectionState.IDLE, s.snapshot.value.state)
        }

    @Test
    fun `a connected link with no replies for 4 s is rebuilt`() =
        runTest {
            val (s, t) = session(1, reconnect = true)
            s.start()
            advanceTimeBy(3_000)
            t.mute = true
            advanceTimeBy(3_000)
            assertEquals(ConnectionState.CONNECTED, s.snapshot.value.state)
            advanceTimeBy(1_600)
            assertEquals(ConnectionState.RECONNECTING, s.snapshot.value.state)
            assertEquals(Reason.VESC_NOT_ANSWERING, s.snapshot.value.reason)
            advanceTimeBy(3_000)
            assertEquals(ConnectionState.CONNECTED, s.snapshot.value.state)
            s.stop()
        }

    @Test
    fun `reconnect never retries a first connect that failed`() =
        runTest {
            val (s, t) = session(1, reconnect = true)
            t.refuseConnects = 1
            s.start()
            advanceTimeBy(5_000)
            assertEquals(ConnectionState.LOST, s.snapshot.value.state)
            assertEquals(Reason.MODULE_NOT_FOUND, s.snapshot.value.reason)
        }

    @Test
    fun `reconnect stops on a reason that retrying cannot fix`() =
        runTest {
            val (s, t) = session(1, reconnect = true)
            s.start()
            advanceTimeBy(2_000)
            t.simulateLoss(Reason.PERMISSION_CONNECT)
            runCurrent()
            assertEquals(ConnectionState.LOST, s.snapshot.value.state)
        }

    @Test
    fun `stop returns to idle`() =
        runTest {
            val (s, _) = session(1)
            s.start()
            advanceTimeBy(1_000)
            s.stop()
            assertEquals(ConnectionState.IDLE, s.snapshot.value.state)
        }

    @Test
    fun `no frame before the topology is known`() =
        runTest {
            val (s, _) = session(1)
            assertNull(s.frame())
        }
}
