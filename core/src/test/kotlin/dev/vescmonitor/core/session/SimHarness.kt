package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.link.Link
import dev.vescmonitor.core.sim.FakeVesc
import dev.vescmonitor.core.sim.SimNode
import dev.vescmonitor.core.sim.SyntheticTransport
import kotlinx.coroutines.test.TestScope

/** A link to a [FakeVesc] on virtual time. */
class SimHarness(
    scope: TestScope,
    val vesc: FakeVesc,
    depth: Int = 1,
) {
    val clock = Clock { scope.testScheduler.currentTime }
    val transport = SyntheticTransport(vesc, scope.backgroundScope, clock)
    val link = Link(transport, clock, depth).also { it.start(scope.backgroundScope) }

    companion object {
        fun single() = FakeVesc(SimNode(10))

        fun dualOnCan() = FakeVesc(SimNode(10), listOf(SimNode(20)))
    }
}
