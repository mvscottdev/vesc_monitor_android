package dev.vescmonitor.core.replay

import dev.vescmonitor.core.link.BleTransport
import dev.vescmonitor.core.link.Clock
import dev.vescmonitor.core.link.LinkState
import dev.vescmonitor.core.protocol.GuardedFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach

/**
 * Wraps a transport and, while a recording runs, writes every request frame,
 * notification and link state as capture records. Raw captures can hold device addresses and controller UUIDs: run
 * them through the scrub script before they become fixtures.
 */
class RecordingTransport(
    private val inner: BleTransport,
    private val clock: Clock,
) : BleTransport {
    @Volatile
    private var sink: ((String) -> Unit)? = null

    @Volatile
    private var startMs = 0L

    val isRecording: Boolean get() = sink != null

    override val state: StateFlow<LinkState> get() = inner.state
    override val notifications: Flow<ByteArray> = inner.notifications.onEach { chunk(CaptureRecord.Direction.RX, it) }
    override val mtu: Int get() = inner.mtu

    /** Starts writing records to [sink], beginning with a meta record. */
    fun startRecording(
        sink: (String) -> Unit,
        meta: Map<String, String>,
    ) {
        startMs = clock.nowMs()
        this.sink = sink
        val base = mapOf("format" to CaptureRecord.FORMAT_VERSION.toString(), "mtu" to mtu.toString())
        emit(CaptureRecord.Meta(0, base + meta))
    }

    fun stopRecording() {
        sink = null
    }

    private fun writeState(state: String) = emit(CaptureRecord.SessionState(t(), state))

    override suspend fun connect() {
        inner.connect()
        writeState("connected")
    }

    override suspend fun write(frame: GuardedFrame): Long {
        chunk(CaptureRecord.Direction.TX, frame.bytes)
        return inner.write(frame)
    }

    override suspend fun disconnect() {
        inner.disconnect()
        writeState("idle")
    }

    private fun chunk(
        dir: CaptureRecord.Direction,
        bytes: ByteArray,
    ) = emit(CaptureRecord.BleChunk(t(), dir, bytes))

    private fun t() = clock.nowMs() - startMs

    private fun emit(r: CaptureRecord) {
        sink?.invoke(CaptureRecord.encode(r))
    }
}
