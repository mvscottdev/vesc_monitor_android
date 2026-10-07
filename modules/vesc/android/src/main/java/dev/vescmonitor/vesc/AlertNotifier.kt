package dev.vescmonitor.vesc

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.vescmonitor.core.alert.ActiveAlert
import dev.vescmonitor.core.alert.Severity

/**
 * Heads-up notifications for alerts while the app is in the background (the dashboard
 * shows its own banner in the foreground). Warnings and critical alerts only; info alerts
 * never make sound. The titles mirror the UI's alert texts.
 */
internal object AlertNotifier {
    private const val CHANNEL = "alerts"
    private const val ID_BASE = 100

    fun inForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    fun post(
        context: Context,
        alert: ActiveAlert,
    ) {
        if (alert.severity == Severity.INFO) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(nm)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val n =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title(alert))
                .setContentText(detail(alert, fahrenheit(context)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        try {
            nm.notify(ID_BASE + (alert.key.hashCode() + 31 * (alert.controllerId ?: 0)).mod(1000), n)
        } catch (_: SecurityException) {
            // Notifications not allowed: the banner still shows in the foreground.
        }
    }

    private fun title(a: ActiveAlert): String {
        val crit = a.severity == Severity.CRITICAL
        return when (a.key) {
            "fet_temp" -> if (crit) "Controller power limited" else "Controller getting hot"
            "motor_temp" -> if (crit) "Motor power limited" else "Motor getting hot"
            "fet_temp_rise", "motor_temp_rise" -> "Temperature rising fast"
            "low_battery" -> if (crit) "Battery almost empty" else "Battery low"
            "battery_cut" -> if (crit) "Battery power cut" else "Battery power cut soon"
            "low_cell" -> "Low cell voltage"
            "sag" -> "Heavy voltage sag"
            "diverge" -> "Controller voltages differ"
            "duty" -> if (crit) "At maximum duty" else "Near maximum duty"
            "fault" -> "Controller fault"
            "link_stale" -> "Controller not responding"
            "slip" -> "Wheel slip"
            "test" -> "Test alert"
            else -> "Alert"
        }
    }

    /** The rider's temperature unit, from the UI's units setting (°C unless it says °F). */
    private fun fahrenheit(context: Context): Boolean =
        context
            .getSharedPreferences(SessionHub.PREFS, Context.MODE_PRIVATE)
            .getString(UNITS_KEY, null)
            ?.contains("\"temp\":\"f\"") == true

    private const val UNITS_KEY = "ui.units"

    private fun detail(
        a: ActiveAlert,
        fahrenheit: Boolean,
    ): String {
        val who = a.controllerId?.let { "Controller $it · " } ?: ""
        val v =
            when (a.key) {
                "fet_temp", "motor_temp" -> if (fahrenheit) "%.0f °F".format(a.value * 1.8 + 32) else "%.0f °C".format(a.value)
                "low_battery" -> "%.0f %%".format(a.value)
                "battery_cut", "diverge" -> "%.1f V".format(a.value)
                "low_cell" -> "%.2f V/cell".format(a.value)
                "sag" -> "%.0f %% sag".format(a.value)
                "duty" -> "%.0f %% duty".format(a.value * 100)
                "fault" -> "code %d".format(a.value.toInt())
                "test" -> "Alerts sound like this when the app is in the background"
                else -> ""
            }
        return who + v
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Alerts", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
    }
}
