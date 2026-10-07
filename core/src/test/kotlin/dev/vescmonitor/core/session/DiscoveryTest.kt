package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.Link
import dev.vescmonitor.core.link.Reason
import dev.vescmonitor.core.protocol.FwVersion
import dev.vescmonitor.core.sim.FakeVesc
import dev.vescmonitor.core.sim.SimNode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscoveryTest {
    private suspend fun discover(h: SimHarness): DiscoveryResult {
        h.transport.connect()
        return Discovery(h.link).run()
    }

    private fun ids(r: DiscoveryResult) = (r as DiscoveryResult.Found).topology.controllers.map { it.canId to it.isLocal }

    @Test
    fun `one controller`() =
        runTest {
            val r = discover(SimHarness(this, SimHarness.single()))
            assertEquals(listOf(10 to true), ids(r))
        }

    @Test
    fun `two controllers found through PING_CAN`() =
        runTest {
            val r = discover(SimHarness(this, SimHarness.dualOnCan()))
            assertEquals(listOf(10 to true, 20 to false), ids(r))
        }

    @Test
    fun `progress reports the firmware, then each controller found`() =
        runTest {
            val h = SimHarness(this, SimHarness.dualOnCan())
            h.transport.connect()
            val seen = ArrayList<SetupProgress>()
            Discovery(h.link) { seen += it }.run()
            assertEquals(listOf(0, 1, 2), seen.map { it.found })
            assertEquals(SetupStep.CONTROLLERS, seen.first().step)
            assertEquals(true, seen.first().firmware != null)
        }

    @Test
    fun `PING empty falls back to probing id + 1`() =
        runTest {
            val vesc = FakeVesc(SimNode(10), listOf(SimNode(11)), pingListsPeers = false)
            val r = discover(SimHarness(this, vesc))
            assertEquals(listOf(10 to true, 11 to false), ids(r))
        }

    @Test
    fun `PING empty and no id + 1 leaves one controller`() =
        runTest {
            val vesc = FakeVesc(SimNode(10), listOf(SimNode(30)), pingListsPeers = false)
            assertEquals(listOf(10 to true), ids(discover(SimHarness(this, vesc))))
        }

    @Test
    fun `BMS and custom modules on CAN are not controllers`() =
        runTest {
            val vesc =
                FakeVesc(
                    SimNode(10),
                    listOf(SimNode(20), SimNode(40, hwType = FwVersion.HW_TYPE_BMS), SimNode(50, hwType = FwVersion.HW_TYPE_CUSTOM_MODULE)),
                )
            val r = discover(SimHarness(this, vesc))
            assertEquals(listOf(10 to true, 20 to false), ids(r))
            assertEquals(listOf(40 to "bms", 50 to "module"), (r as DiscoveryResult.Found).topology.otherNodes.map { it.canId to it.kind })
        }

    @Test
    fun `bridge without a motor puts every controller on CAN`() =
        runTest {
            val vesc = FakeVesc(SimNode(2, hwType = FwVersion.HW_TYPE_CUSTOM_MODULE), listOf(SimNode(5), SimNode(6)))
            assertEquals(listOf(5 to false, 6 to false), ids(discover(SimHarness(this, vesc))))
        }

    @Test
    fun `old firmware is refused`() =
        runTest {
            val r = discover(SimHarness(this, FakeVesc(SimNode(10, major = 5, minor = 2))))
            assertEquals(DiscoveryResult.Failed(Reason.FIRMWARE_TOO_OLD), r)
        }

    @Test
    fun `old firmware on a CAN peer skips only that peer`() =
        runTest {
            val r = discover(SimHarness(this, FakeVesc(SimNode(10), listOf(SimNode(20, major = 4, minor = 0)))))
            assertEquals(listOf(10 to true), ids(r))
            assertEquals(listOf(20), (r as DiscoveryResult.Found).topology.unsupportedIds)
        }

    @Test
    fun `silent VESC gives a plain reason`() =
        runTest {
            val h = SimHarness(this, SimHarness.single())
            h.transport.connect()
            val silent = Link(SilentTransport(), h.clock).also { it.start(backgroundScope) }
            assertEquals(DiscoveryResult.Failed(Reason.VESC_NOT_ANSWERING), Discovery(silent).run())
        }

    @Test
    fun `bridge only with nothing on CAN finds no controllers`() =
        runTest {
            val r = discover(SimHarness(this, FakeVesc(SimNode(2, hwType = FwVersion.HW_TYPE_CUSTOM_MODULE))))
            assertEquals(DiscoveryResult.Failed(Reason.NO_CONTROLLERS), r)
        }
}
