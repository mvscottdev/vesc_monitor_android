package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.BleTransport
import dev.vescmonitor.core.link.LinkState
import dev.vescmonitor.core.protocol.GuardedFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Connects but never answers; records what was written. */
class SilentTransport : BleTransport {
    val written = ArrayList<GuardedFrame>()
    override val state: StateFlow<LinkState> = MutableStateFlow(LinkState.Connected(23))
    override val notifications: Flow<ByteArray> = emptyFlow()
    override val mtu: Int = 23

    override suspend fun connect() {}

    override suspend fun write(frame: GuardedFrame): Long {
        written += frame
        return 0
    }

    override suspend fun disconnect() {}
}
