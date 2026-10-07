package dev.vescmonitor.core.sim

import dev.vescmonitor.core.link.BleTransport
import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.link.LinkException
import dev.vescmonitor.core.link.LinkState
import dev.vescmonitor.core.link.Reason
import dev.vescmonitor.core.protocol.GuardedFrame
import dev.vescmonitor.core.protocol.PacketCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A transport backed by [FakeVesc]: replies are framed and cut into 20-byte
 * notifications with a fixed spacing, like an MTU-23 bridge.
 */
class SyntheticTransport(
    private val vesc: FakeVesc,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val latencyMs: Long = LATENCY_MS,
    private val spacingMs: Long = SPACING_MS,
) : BleTransport {
    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state
    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    override val notifications: Flow<ByteArray> = _notifications
    override val mtu: Int = 23

    /** One UART behind the bridge: replies never interleave. */
    private val uart = Mutex()

    /** The next this many connects fail as if the module were out of range. */
    var refuseConnects = 0

    override suspend fun connect() {
        mute = false
        if (refuseConnects > 0) {
            refuseConnects--
            _state.value = LinkState.Lost(Reason.MODULE_NOT_FOUND)
            throw LinkException(Reason.MODULE_NOT_FOUND)
        }
        _state.value = LinkState.Connected(mtu)
    }

    /** The bridge stays connected but the VESC behind it stops answering. */
    var mute = false

    override suspend fun write(frame: GuardedFrame): Long {
        if (mute) return 0
        val reply = vesc.respond(frame.payload, clock.nowMs()) ?: return 0
        val bytes = PacketCodec.encode(reply)
        scope.launch {
            uart.withLock {
                delay(latencyMs)
                var i = 0
                while (i < bytes.size) {
                    _notifications.emit(bytes.copyOfRange(i, minOf(i + CHUNK, bytes.size)))
                    i += CHUNK
                    if (i < bytes.size) delay(spacingMs)
                }
            }
        }
        return 0
    }

    override suspend fun disconnect() {
        _state.value = LinkState.Idle
    }

    fun simulateLoss(reason: Reason) {
        _state.value = LinkState.Lost(reason)
    }

    companion object {
        const val CHUNK = 20
        const val LATENCY_MS = 8L
        const val SPACING_MS = 8L
    }
}
