package dev.vescmonitor.vesc

import android.bluetooth.BluetoothGatt
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest
import no.nordicsemi.android.ble.ktx.suspend
import java.io.IOException

/**
 * Nordic BLE manager for one bridge: runs the service cascade, raises the connection
 * priority, asks for MTU 517 and subscribes to every notifying characteristic.
 */
internal class VescBleManager(
    context: Context,
) : BleManager(context) {
    private var profile: BridgeProfile? = null
    private val _chunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = CHUNK_BUFFER)
    val chunks: SharedFlow<ByteArray> = _chunks

    val negotiatedMtu: Int get() = mtu

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        profile = BridgeProfile.detect(gatt)
        return profile != null
    }

    override fun initialize() {
        val p = profile ?: return
        requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH).enqueue()
        // 517, never 247 (VESC Express truncation window). The NRF51 stays at 23; any result is fine.
        requestMtu(REQUESTED_MTU).enqueue()
        for (c in p.notify) {
            setNotificationCallback(c).with { _, data -> data.value?.let { _chunks.tryEmit(it) } }
            enableNotifications(c).enqueue()
        }
    }

    override fun onServicesInvalidated() {
        profile = null
    }

    /** Writes one chunk (at most 20 bytes, fits MTU 23). */
    suspend fun writeChunk(bytes: ByteArray) {
        val p = profile ?: throw IOException("not connected")
        writeCharacteristic(p.write, bytes, p.writeType).suspend()
    }

    companion object {
        const val REQUESTED_MTU = 517
        private const val CHUNK_BUFFER = 512
    }
}
