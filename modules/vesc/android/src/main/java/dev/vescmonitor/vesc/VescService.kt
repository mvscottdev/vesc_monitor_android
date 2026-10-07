package dev.vescmonitor.vesc

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Foreground service that keeps the BLE session alive in the background. Started only
 * from the visible app after the connect permission is granted; never restarts itself.
 */
class VescService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notification = Notifier.build(this, SessionHub.statusText(), SessionHub.rideElapsedNow(), SessionHub.sessionOpen())
        ServiceCompat.startForeground(this, Notifier.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Save the open ride (at most the current second is lost), then end the session.
        SessionHub.shutdownFromService()
        stopSelf()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, VescService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VescService::class.java))
        }
    }
}
