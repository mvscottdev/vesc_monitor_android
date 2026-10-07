package dev.vescmonitor.core.replay

import dev.vescmonitor.core.link.BleTransport
import dev.vescmonitor.core.link.LinkState
import dev.vescmonitor.core.link.Reason
import dev.vescmonitor.core.protocol.GuardedFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Plays a recorded capture, paced by requests: each write advances to the next
 * recorded `tx` record and plays the `rx` chunks that followed it with their original
 * spacing. Writes that differ from the recording are counted, not rejected.
 */
class ReplayTransport(
    private val records: List<CaptureRecord>,
    private val scope: CoroutineScope,
) : BleTransport {
    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state
    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = BUFFER)
    override val notifications: Flow<ByteArray> = _notifications

    override val mtu: Int =
        records
            .filterIsInstance<CaptureRecord.Meta>()
            .firstOrNull()
            ?.fields
            ?.get("mtu")
            ?.toDoubleOrNull()
            ?.toInt() ?: DEFAULT_MTU

    private var cursor = 0
    private var playing: Job? = null

    var mismatchedWrites = 0
        private set

    override suspend fun connect() {
        cursor = 0
        _state.value = LinkState.Connected(mtu)
    }

    override suspend fun write(frame: GuardedFrame): Long {
        val txIndex = (cursor until records.size).firstOrNull { isTx(records[it]) }
        if (txIndex == null) {
            _state.value = LinkState.Lost(Reason.REPLAY_ENDED)
            return 0
        }
        val tx = records[txIndex] as CaptureRecord.BleChunk
        if (!tx.bytes.contentEquals(frame.bytes)) mismatchedWrites++
        val end = (txIndex + 1 until records.size).firstOrNull { isTx(records[it]) } ?: records.size
        cursor = end
        val rx = records.subList(txIndex + 1, end).filterIsInstance<CaptureRecord.BleChunk>()
        playing = scope.launch { play(tx.t, rx) }
        return 0
    }

    private suspend fun play(
        fromT: Long,
        rx: List<CaptureRecord.BleChunk>,
    ) {
        var last = fromT
        for (r in rx) {
            delay((r.t - last).coerceAtLeast(0))
            last = r.t
            _notifications.emit(r.bytes)
        }
    }

    override suspend fun disconnect() {
        playing?.cancel()
        _state.value = LinkState.Idle
    }

    private fun isTx(r: CaptureRecord) = r is CaptureRecord.BleChunk && r.direction == CaptureRecord.Direction.TX

    companion object {
        const val DEFAULT_MTU = 23
        private const val BUFFER = 256
    }
}
