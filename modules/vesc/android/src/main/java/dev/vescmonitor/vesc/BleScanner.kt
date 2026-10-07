package dev.vescmonitor.vesc

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import dev.vescmonitor.core.link.Reason

/**
 * Unfiltered user scan: name, RSSI and a looks-like-VESC hint per device. Android
 * silently throttles a 5th start within 30 s, so starts are rate-limited here.
 */
internal class BleScanner(
    context: Context,
) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val found = LinkedHashMap<String, Map<String, Any?>>()
    private val starts = ArrayDeque<Long>()

    @Volatile
    var scanning = false
        private set

    @Volatile
    var version = 0
        private set

    private val callback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) = record(result)

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { record(it) }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                version++
            }
        }

    /** Starts scanning; returns a reason when it cannot. */
    @SuppressLint("MissingPermission")
    fun start(): Reason? {
        val a = adapter ?: return Reason.BLUETOOTH_OFF
        if (!a.isEnabled) return Reason.BLUETOOTH_OFF
        if (scanning) return null
        val now = SystemClock.elapsedRealtime()
        while (starts.isNotEmpty() && now - starts.first() > THROTTLE_WINDOW_MS) starts.removeFirst()
        if (starts.size >= MAX_STARTS) return null
        val scanner = a.bluetoothLeScanner ?: return Reason.BLUETOOTH_OFF
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            synchronized(found) { found.clear() }
            scanner.startScan(null, settings, callback)
        } catch (_: SecurityException) {
            return Reason.PERMISSION_SCAN
        }
        starts.addLast(now)
        scanning = true
        version++
        return null
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        scanning = false
        version++
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: SecurityException) {
            // Permission revoked while scanning: nothing to stop.
        }
    }

    /** Devices seen in this scan, strongest first. */
    fun results(): List<Map<String, Any?>> = synchronized(found) { found.values.sortedByDescending { it["rssi"] as Int } }

    @SuppressLint("MissingPermission")
    private fun record(r: ScanResult) {
        val name =
            r.scanRecord?.deviceName ?: try {
                r.device.name
            } catch (_: SecurityException) {
                null
            }
        val uuids = r.scanRecord?.serviceUuids?.map { it.uuid }
        val entry =
            mapOf(
                "address" to r.device.address,
                "name" to name,
                "rssi" to r.rssi,
                "looksLikeVesc" to BridgeProfile.Hint.matches(uuids, name),
            )
        synchronized(found) { found[r.device.address] = entry }
        version++
    }

    companion object {
        private const val MAX_STARTS = 4
        private const val THROTTLE_WINDOW_MS = 30_000L
    }
}
