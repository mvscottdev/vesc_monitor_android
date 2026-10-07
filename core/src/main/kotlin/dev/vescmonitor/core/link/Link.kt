package dev.vescmonitor.core.link

import dev.vescmonitor.core.protocol.CommandId
import dev.vescmonitor.core.protocol.GuardedFrame
import dev.vescmonitor.core.protocol.Reassembler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** A matched reply plus the bench numbers measured for it. */
class Reply(
    val payload: ByteArray,
    val rttMs: Long,
    val writeMs: Long,
    val notifications: Int,
    /** Time from the first to the last notification of this reply. */
    val spanMs: Long,
)

/** Link-wide counters for diagnostics. */
data class LinkCounters(
    val notifications: Long,
    val crcErrors: Long,
    val droppedBytes: Long,
    val unmatched: Long,
    val timeouts: Long,
)

/**
 * Request/response over a [BleTransport]. Replies carry no request ID, so a reply is
 * matched to the oldest pending request whose matcher accepts it; anything else is
 * dropped. [depth] = 1 is the strict one-in-flight mode; 2 is a bench experiment.
 *
 * Not thread-safe: run every call on the session's single-thread dispatcher.
 */
class Link(
    private val transport: BleTransport,
    private val clock: Clock,
    depth: Int = 1,
) {
    private val reassembler = Reassembler()
    private val permits = Semaphore(depth)
    private val pending = ArrayList<Pending>(2)
    private var notifications = 0L
    private var unmatched = 0L
    private var timeouts = 0L

    /** While true, PING_CAN is refused: it blocks the VESC for up to ~2.5 s. */
    var pollingActive = false

    val counters: LinkCounters
        get() = LinkCounters(notifications, reassembler.crcErrors, reassembler.droppedBytes, unmatched, timeouts)

    fun start(scope: CoroutineScope): Job = scope.launch { transport.notifications.collect { onChunk(it) } }

    /** Sends [frame] and waits for a reply accepted by [matcher]; null on timeout. */
    suspend fun request(
        frame: GuardedFrame,
        timeoutMs: Long,
        matcher: (ByteArray) -> Boolean,
    ): Reply? {
        check(!(pollingActive && frame.effectiveCommandId == CommandId.PING_CAN)) { "PING_CAN while polling" }
        return permits.withPermit { exchange(frame, timeoutMs, matcher) }
    }

    /** [request] with [retries] extra attempts after a timeout. */
    suspend fun requestWithRetry(
        frame: GuardedFrame,
        timeoutMs: Long,
        retries: Int = 1,
        matcher: (ByteArray) -> Boolean,
    ): Reply? {
        repeat(retries + 1) { request(frame, timeoutMs, matcher)?.let { return it } }
        return null
    }

    private suspend fun exchange(
        frame: GuardedFrame,
        timeoutMs: Long,
        matcher: (ByteArray) -> Boolean,
    ): Reply? {
        val p = Pending(matcher)
        pending += p
        val sentAt = clock.nowMs()
        val writeMs =
            try {
                transport.write(frame)
            } catch (e: Exception) {
                pending -= p
                throw e
            }
        val payload = withTimeoutOrNull(timeoutMs) { p.result.await() }
        if (payload == null) {
            pending -= p
            timeouts++
            return null
        }
        val span = if (p.firstNotifMs < 0) 0 else p.lastNotifMs - p.firstNotifMs
        return Reply(payload, clock.nowMs() - sentAt, writeMs, p.notifications, span)
    }

    private fun onChunk(chunk: ByteArray) {
        val now = clock.nowMs()
        notifications++
        for (p in pending) {
            if (p.firstNotifMs < 0) p.firstNotifMs = now
            p.lastNotifMs = now
            p.notifications++
        }
        for (payload in reassembler.feed(chunk, now)) dispatch(payload)
    }

    private fun dispatch(payload: ByteArray) {
        val p = pending.firstOrNull { it.matcher(payload) }
        if (p == null) {
            unmatched++
            return
        }
        pending -= p
        p.result.complete(payload)
    }

    private class Pending(
        val matcher: (ByteArray) -> Boolean,
    ) {
        val result = CompletableDeferred<ByteArray>()
        var notifications = 0
        var firstNotifMs = -1L
        var lastNotifMs = -1L
    }
}
