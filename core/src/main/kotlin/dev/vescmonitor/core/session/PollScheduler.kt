package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.link.Link
import dev.vescmonitor.core.link.Matchers
import dev.vescmonitor.core.link.Reply
import dev.vescmonitor.core.protocol.CommandId
import dev.vescmonitor.core.protocol.ParseException
import dev.vescmonitor.core.protocol.Requests
import dev.vescmonitor.core.protocol.SimpleReplies
import dev.vescmonitor.core.protocol.ValuesLayout
import dev.vescmonitor.core.protocol.ValuesParser
import dev.vescmonitor.core.protocol.ValuesSample
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Polling options; the non-default ones are BLE bench toggles. */
data class PollConfig(
    /** SELECTIVE fast + slow masks; false polls the full GET_VALUES every time. */
    val selective: Boolean = true,
    /** Requests in flight per link: 1 strict, 2 experimental. */
    val depth: Int = 1,
    /** Poll the local controller through FORWARD_CAN to its own ID (UNVERIFIED that every firmware answers). */
    val forwardLocal: Boolean = false,
    val maxHzPerController: Double = MAX_HZ,
) {
    init {
        require(depth in 1..2) { "depth must be 1 or 2" }
    }

    companion object {
        const val MAX_HZ = 50.0
    }
}

/**
 * Response-paced rotation over the controllers: the next request goes out when the
 * previous one is answered or times out. Each controller is capped at
 * [PollConfig.maxHzPerController]; a timeout gets one retry, then the controller is
 * stale until its next valid reply. Never sends PING_CAN (the link refuses it).
 */
class PollScheduler(
    private val link: Link,
    private val clock: Clock,
    private val controllers: List<ControllerLive>,
    private val config: PollConfig,
    private val onSample: (ControllerLive) -> Unit = {},
) {
    private var cursor = 0
    private val minIntervalMs = (1000.0 / config.maxHzPerController).toLong()

    suspend fun run() {
        link.pollingActive = true
        try {
            for (c in controllers) readBatteryCut(c)
            coroutineScope { repeat(config.depth) { launch { worker() } } }
        } finally {
            link.pollingActive = false
        }
    }

    private suspend fun worker() {
        while (currentCoroutineContext().isActive) {
            val c = nextDue()
            if (c == null) {
                delay(waitMs())
                continue
            }
            c.busy = true
            try {
                poll(c)
            } finally {
                c.busy = false
            }
        }
    }

    private fun nextDue(): ControllerLive? {
        val now = clock.nowMs()
        for (i in controllers.indices) {
            val c = controllers[(cursor + i) % controllers.size]
            if (!c.busy && c.nextDueMs <= now) {
                cursor = (cursor + i + 1) % controllers.size
                return c
            }
        }
        return null
    }

    private fun waitMs(): Long {
        val now = clock.nowMs()
        val earliest = controllers.filter { !it.busy }.minOfOrNull { it.nextDueMs } ?: (now + minIntervalMs)
        return (earliest - now).coerceIn(1, minIntervalMs)
    }

    private suspend fun poll(c: ControllerLive) {
        val now = clock.nowMs()
        c.nextDueMs = now + minIntervalMs
        val canId = route(c)
        val setup = c.lastSetupMs == ControllerLive.NEVER || now - c.lastSetupMs >= SETUP_PERIOD_MS
        val layout = if (setup) ValuesLayout.SETUP else ValuesLayout.VALUES
        val reply: Reply?
        if (setup) {
            val mask = ValuesLayout.MASK_SETUP
            reply = request(Requests.getValuesSetupSelective(mask), canId, Matchers.selectiveValues(layout, mask, c.controller.canId))
            // A controller that never answers SETUP is retried at the slow period, not every poll.
            c.lastSetupMs = now
        } else if (config.selective) {
            val slow = c.lastSlowMs == ControllerLive.NEVER || now - c.lastSlowMs >= SLOW_PERIOD_MS
            val mask = if (slow) ValuesLayout.MASK_SLOW else ValuesLayout.MASK_FAST
            reply = request(Requests.getValuesSelective(mask), canId, Matchers.selectiveValues(layout, mask, c.controller.canId))
            if (reply != null && slow) c.lastSlowMs = now
        } else {
            reply = request(Requests.getValues(), canId, Matchers.fullValues(layout, c.controller.canId))
        }
        if (reply == null) {
            c.bench.onTimeout()
            c.stale = true
            return
        }
        val sample = parse(layout, setup, reply.payload) ?: return
        c.bench.onReply(reply.writeMs, reply.rttMs, reply.notifications, reply.spanMs)
        if (sample.untestedFirmware) c.untestedFirmware = true
        c.onSample(clock.nowMs(), sample)
        // SETUP replies only refresh speed and distance; recorded samples are the VALUES ones.
        if (!setup) onSample(c)
    }

    private suspend fun request(
        inner: ByteArray,
        canId: Int?,
        matcher: (ByteArray) -> Boolean,
    ): Reply? = link.requestWithRetry(Requests.frame(inner, canId), REQUEST_TIMEOUT_MS, 1, matcher)

    private fun parse(
        layout: ValuesLayout,
        setup: Boolean,
        payload: ByteArray,
    ): ValuesSample? =
        try {
            if (setup || config.selective) ValuesParser.parseSelective(layout, payload) else ValuesParser.parseFull(layout, payload)
        } catch (_: ParseException) {
            null
        }

    private suspend fun readBatteryCut(c: ControllerLive) {
        val reply = request(Requests.batteryCut(), route(c), Matchers.command(CommandId.GET_BATTERY_CUT)) ?: return
        c.batteryCut =
            try {
                SimpleReplies.parseBatteryCut(reply.payload)
            } catch (_: ParseException) {
                null
            }
    }

    private fun route(c: ControllerLive): Int? = if (c.controller.isLocal && !config.forwardLocal) null else c.controller.canId

    companion object {
        const val REQUEST_TIMEOUT_MS = 1_000L
        const val SLOW_PERIOD_MS = 1_000L

        /** SETUP (speed, battery level, distance) once per second per controller. */
        const val SETUP_PERIOD_MS = 1_000L
    }
}
