package dev.vescmonitor.core.link

import dev.vescmonitor.core.protocol.GuardedFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Monotonic milliseconds. */
fun interface Clock {
    fun nowMs(): Long
}

/** Link state as the transport sees it. */
sealed interface LinkState {
    data object Idle : LinkState

    data object Connecting : LinkState

    data class Connected(
        val mtu: Int,
    ) : LinkState

    data class Lost(
        val reason: Reason,
    ) : LinkState
}

/** Thrown by [BleTransport.connect] with a user-facing reason. */
class LinkException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.message, cause)

/**
 * The BLE link to a bridge. Implementations: the Nordic adapter, replay and synthetic.
 * [write] takes only a [GuardedFrame], so nothing but allow-listed requests reach the wire.
 */
interface BleTransport {
    val state: StateFlow<LinkState>

    /** Every notification from every notifiable characteristic, in arrival order. */
    val notifications: Flow<ByteArray>

    /** Negotiated ATT MTU (23 when nothing was negotiated). */
    val mtu: Int

    suspend fun connect()

    /** Writes the frame; returns the time spent writing all chunks, in ms. */
    suspend fun write(frame: GuardedFrame): Long

    suspend fun disconnect()
}
