package dev.vescmonitor.core.session

import dev.vescmonitor.core.protocol.CommandId
import dev.vescmonitor.core.protocol.Requests
import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.sim.FakeVesc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PollSchedulerTest {
    private class Polling(
        val h: SimHarness,
        val lives: List<ControllerLive>,
        val order: MutableList<Int>,
        val job: Job,
    )

    private suspend fun TestScope.startPolling(
        vesc: FakeVesc,
        config: PollConfig = PollConfig(),
        scope: CoroutineScope = backgroundScope,
    ): Polling {
        val h = SimHarness(this, vesc, config.depth)
        h.transport.connect()
        val topology = (Discovery(h.link).run() as DiscoveryResult.Found).topology
        val lives = topology.controllers.map { ControllerLive(it) }
        val order = ArrayList<Int>()
        val job = scope.launch { PollScheduler(h.link, h.clock, lives, config) { order += it.controller.canId }.run() }
        return Polling(h, lives, order, job)
    }

    @Test
    fun `one controller polls with fresh voltage`() =
        runTest {
            val p = startPolling(SimHarness.single())
            advanceTimeBy(3_000)
            val c = p.lives.single()
            assertTrue(c.isFresh(p.h.clock.nowMs()))
            assertTrue(c.voltageV in 45.0..51.0, "voltage ${c.voltageV}")
            assertTrue(c.measuredHz in 10.0..50.5, "hz ${c.measuredHz}")
        }

    @Test
    fun `two controllers rotate`() =
        runTest {
            val p = startPolling(SimHarness.dualOnCan())
            advanceTimeBy(3_000)
            val tail = p.order.takeLast(20)
            assertTrue(tail.zipWithNext().all { (a, b) -> a != b }, "rotation $tail")
            assertTrue(p.lives.all { it.isFresh(p.h.clock.nowMs()) })
        }

    @Test
    fun `rate is capped at 50 Hz per controller`() =
        runTest {
            val h = SimHarness(this, SimHarness.single())
            val fast =
                dev.vescmonitor.core.sim
                    .SyntheticTransport(h.vesc, backgroundScope, h.clock, latencyMs = 0, spacingMs = 0)
            val link =
                dev.vescmonitor.core.link
                    .Link(fast, h.clock)
                    .also { it.start(backgroundScope) }
            fast.connect()
            val topology = (Discovery(link).run() as DiscoveryResult.Found).topology
            val lives = topology.controllers.map { ControllerLive(it) }
            var samples = 0
            backgroundScope.launch { PollScheduler(link, h.clock, lives, PollConfig()) { samples++ }.run() }
            advanceTimeBy(2_000)
            assertTrue(samples in 90..101, "samples $samples")
        }

    @Test
    fun `timeout plus retry makes a controller stale, a reply brings it back`() =
        runTest {
            val vesc = SimHarness.dualOnCan()
            val p = startPolling(vesc)
            advanceTimeBy(1_000)
            val peer = p.lives.first { !it.controller.isLocal }
            vesc.peers.single().online = false
            advanceTimeBy(2_500)
            assertTrue(peer.stale)
            assertFalse(peer.isFresh(p.h.clock.nowMs()))
            assertTrue(p.lives.first { it.controller.isLocal }.isFresh(p.h.clock.nowMs()), "local stays fresh")
            vesc.peers.single().online = true
            advanceTimeBy(2_500)
            assertFalse(peer.stale)
            assertTrue(peer.isFresh(p.h.clock.nowMs()))
        }

    @Test
    fun `PING_CAN is refused while polling`() =
        runTest {
            val p = startPolling(SimHarness.single())
            advanceTimeBy(100)
            assertThrows<IllegalStateException> {
                p.h.link.request(Requests.frame(Requests.pingCan(), null), 5_000) { true }
            }
        }

    @Test
    fun `battery cut once per controller, slow mask about once a second`() =
        runTest {
            val vesc = SimHarness.dualOnCan()
            val p = startPolling(vesc)
            advanceTimeBy(5_000)
            assertEquals(2, vesc.requestsByCommand[CommandId.GET_BATTERY_CUT])
            val slow = vesc.selectiveMasks.count { it == ValuesLayout.MASK_SLOW }
            assertTrue(slow in 10..12, "slow polls $slow")
            assertEquals(2, p.lives.count { it.batteryCut != null })
        }

    @Test
    fun `full mask bench mode`() =
        runTest {
            val vesc = SimHarness.dualOnCan()
            val p = startPolling(vesc, PollConfig(selective = false))
            advanceTimeBy(2_000)
            assertTrue(vesc.selectiveMasks.isEmpty())
            assertTrue(p.lives.all { it.isFresh(p.h.clock.nowMs()) })
        }

    @Test
    fun `depth two bench mode keeps both controllers fresh`() =
        runTest {
            val p = startPolling(SimHarness.dualOnCan(), PollConfig(depth = 2))
            advanceTimeBy(2_000)
            assertTrue(p.lives.all { it.isFresh(p.h.clock.nowMs()) })
            assertTrue(p.lives.all { it.measuredHz > 10 }, p.lives.map { it.measuredHz }.toString())
        }

    @Test
    fun `bench numbers are measured`() =
        runTest {
            val p = startPolling(SimHarness.single())
            advanceTimeBy(1_000)
            val b = p.lives.single().bench
            assertTrue(b.requests > 10)
            assertTrue(b.notificationsPerReply >= 1.0)
            assertTrue(b.rttMs > 0)
        }
}
