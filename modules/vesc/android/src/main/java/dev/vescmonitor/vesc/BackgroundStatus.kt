package dev.vescmonitor.vesc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Live checks for running in the background: what Android may use to stop the
 * connection while the screen is off. Read-only; the rider changes them in system settings.
 */
internal object BackgroundStatus {
    fun check(context: Context): Map<String, Any?> {
        val pm = context.getSystemService(PowerManager::class.java)
        val nearby =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                granted(context, Manifest.permission.BLUETOOTH_SCAN) && granted(context, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
            }
        return mapOf(
            "nearby" to nearby.flag(),
            "notifications" to NotificationManagerCompat.from(context).areNotificationsEnabled().flag(),
            "batteryOptimized" to (pm?.isIgnoringBatteryOptimizations(context.packageName) == false).flag(),
            "manufacturer" to Build.MANUFACTURER.lowercase(),
            "sdk" to Build.VERSION.SDK_INT,
        )
    }

    /** Opens the system list where the rider can exempt the app from battery optimisation. */
    fun openBatterySettings(context: Context): Boolean =
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }

    private fun granted(
        context: Context,
        permission: String,
    ) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Boolean.flag() = if (this) 1 else 0
}
