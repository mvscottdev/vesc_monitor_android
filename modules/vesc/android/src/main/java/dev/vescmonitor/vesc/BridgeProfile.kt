package dev.vescmonitor.vesc

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import java.util.UUID

/**
  * Which GATT characteristics carry the VESC byte stream: Nordic UART Service first,
  * then the HM-10 style FFE0/FFE1 service. Never chosen by device name.
  */
internal class BridgeProfile(
    val write: BluetoothGattCharacteristic,
    val notify: List<BluetoothGattCharacteristic>,
) {
    companion object {
        val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val HM10_SERVICE: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val HM10_CHAR: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

        /** Runs the detection cascade; null means "not a VESC bridge?". */
        fun detect(gatt: BluetoothGatt): BridgeProfile? =
            from(gatt.getService(NUS_SERVICE), NUS_RX) ?: from(gatt.getService(HM10_SERVICE), HM10_CHAR)

        private fun from(service: BluetoothGattService?, writeUuid: UUID): BridgeProfile? {
            if (service == null) return null
            val write = service.getCharacteristic(writeUuid) ?: return null
            if (write.properties and WRITE_PROPS == 0) return null
            // Some modules notify on the RX characteristic too: subscribe to every notifier.
            val notify = service.characteristics.filter { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
            if (notify.isEmpty()) return null
            return BridgeProfile(write, notify)
        }

        private const val WRITE_PROPS =
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    }

    val writeType: Int
        get() =
            if (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

    /** A looks-like-VESC hint for scan results that advertise one of the services. */
    object Hint {
        fun matches(
            serviceUuids: List<UUID>?,
            name: String?,
        ): Boolean =
            serviceUuids?.any { it == NUS_SERVICE || it == HM10_SERVICE } == true ||
                name?.contains("vesc", ignoreCase = true) == true
    }
}
