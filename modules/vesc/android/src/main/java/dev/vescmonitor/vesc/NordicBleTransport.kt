package dev.vescmonitor.vesc

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import dev.vescmonitor.core.link.BleTransport
import dev.vescmonitor.core.link.LinkException
import dev.vescmonitor.core.link.LinkState
import dev.vescmonitor.core.link.Reason
import dev.vescmonitor.core.protocol.GuardedFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.exception.BluetoothDisabledException
import no.nordicsemi.android.ble.exception.DeviceDisconnectedException
import no.nordicsemi.android.ble.exception.InvalidRequestException
import no.nordicsemi.android.ble.exception.RequestFailedException
import no.nordicsemi.android.ble.ktx.state.ConnectionState
import no.nordicsemi.android.ble.ktx.stateAsFlow
import no.nordicsemi.android.ble.ktx.suspend

/**
 * BLE link to a bridge through the Nordic library. Direct connect (no autoConnect);
 * on link loss the state goes to Lost and the user reconnects.
 */
internal class NordicBleTransport(
    context: Context,
    private val address: String,
    private val scope: CoroutineScope,
) : BleTransport {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val manager = VescBleManager(context)
    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    override val state: StateFlow<LinkState> = _state
    override val notifications: Flow<ByteArray> = manager.chunks
    override val mtu: Int get() = manager.negotiatedMtu

    private var watch: Job? = null

    @Volatile
    private var closing = false

    override suspend fun connect() {
        val a = adapter
        if (a == null || !a.isEnabled) throw LinkException(Reason.BLUETOOTH_OFF)
        val device =
            try {
                a.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                throw LinkException(Reason.MODULE_NOT_FOUND, e)
            }
        closing = false
        watch?.cancel()
        _state.value = LinkState.Connecting
        val states = manager.stateAsFlow()
        try {
            manager.connect(device).useAutoConnect(false).timeout(CONNECT_TIMEOUT_MS).suspend()
        } catch (e: CancellationException) {
            manager.close()
            throw e
        } catch (e: Exception) {
            manager.close()
            val reason = reasonFor(e)
            _state.value = LinkState.Lost(reason)
            throw LinkException(reason, e)
        }
        _state.value = LinkState.Connected(mtu)
        watch =
            scope.launch {
                states.collect { s ->
                    if (s is ConnectionState.Disconnected && !closing) _state.value = LinkState.Lost(Reason.LINK_LOST)
                }
            }
    }

    override suspend fun write(frame: GuardedFrame): Long {
        val start = SystemClock.elapsedRealtime()
        val bytes = frame.bytes
        try {
            var i = 0
            while (i < bytes.size) {
                manager.writeChunk(bytes.copyOfRange(i, minOf(i + CHUNK, bytes.size)))
                i += CHUNK
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LinkException(Reason.LINK_LOST, e)
        }
        return SystemClock.elapsedRealtime() - start
    }

    override suspend fun disconnect() {
        closing = true
        watch?.cancel()
        try {
            manager.disconnect().suspend()
        } catch (_: Exception) {
            // Already gone.
        }
        manager.close()
        _state.value = LinkState.Idle
    }

    private fun reasonFor(e: Exception): Reason =
        when (e) {
            is BluetoothDisabledException -> Reason.BLUETOOTH_OFF
            is SecurityException -> Reason.PERMISSION_CONNECT
            is DeviceDisconnectedException, is InvalidRequestException -> Reason.MODULE_NOT_FOUND
            is RequestFailedException -> reasonForStatus(e.status)
            else -> Reason.MODULE_NOT_FOUND
        }

    /** GATT and library status codes to plain reasons (busy-by-another-app is only a hint). */
    private fun reasonForStatus(status: Int): Reason =
        when (status) {
            FailCallback.REASON_DEVICE_NOT_SUPPORTED -> Reason.NOT_A_BRIDGE
            FailCallback.REASON_BLUETOOTH_DISABLED -> Reason.BLUETOOTH_OFF
            GATT_CONN_TERMINATE_PEER_USER -> Reason.TAKEN_BY_OTHER
            GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_ENCRYPTION, GATT_AUTH_FAIL -> Reason.PAIRING_NEEDED
            else -> Reason.MODULE_NOT_FOUND
        }

    companion object {
        /** Bridges take 20-byte chunks on any MTU (vesc_tool bleuart.cpp does the same). */
        const val CHUNK = 20
        const val CONNECT_TIMEOUT_MS = 30_000L
        private const val GATT_CONN_TERMINATE_PEER_USER = 19
        private const val GATT_INSUFFICIENT_AUTHENTICATION = 5
        private const val GATT_INSUFFICIENT_ENCRYPTION = 15
        private const val GATT_AUTH_FAIL = 137
    }
}
