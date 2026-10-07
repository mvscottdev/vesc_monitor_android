package dev.vescmonitor.vesc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.vescmonitor.core.session.StatusText

/** The persistent notification of the foreground service (settings mockup, ongoing notification). */
internal object Notifier {
    const val ID = 1
    private const val CHANNEL = "session"

    /**
     * [rideElapsedMs] non-null shows the ride timer; [connected] adds Disconnect. The actions
     * only end the ride or the session in the app; nothing is sent to the controller.
     */
    fun build(
        context: Context,
        text: StatusText,
        rideElapsedMs: Long?,
        connected: Boolean,
    ): Notification {
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val b =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(text.title)
                .setContentText(text.text)
                .setSubText(text.sub)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (rideElapsedMs != null) {
            b
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - rideElapsedMs)
            b.addAction(0, "Stop recording", action(context, NotificationActions.STOP_RECORDING))
        } else {
            b.setShowWhen(false)
        }
        if (connected) b.addAction(0, "Disconnect", action(context, NotificationActions.DISCONNECT))
        return b.build()
    }

    fun update(
        context: Context,
        text: StatusText,
        rideElapsedMs: Long?,
        connected: Boolean,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(ID, build(context, text, rideElapsedMs, connected))
        } catch (_: SecurityException) {
            // Notifications not allowed: the service keeps running without updates.
        }
    }

    private fun action(
        context: Context,
        name: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            name.hashCode(),
            Intent(context, NotificationActions::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Connection", NotificationManager.IMPORTANCE_LOW))
    }
}
