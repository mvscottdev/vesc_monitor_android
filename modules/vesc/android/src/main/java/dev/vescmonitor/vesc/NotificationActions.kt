package dev.vescmonitor.vesc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Buttons of the ongoing notification. They act on the app only, never on the controller. */
class NotificationActions : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        SessionHub.fromNotification(intent.action) { pending.finish() }
    }

    companion object {
        const val STOP_RECORDING = "dev.vescmonitor.vesc.STOP_RECORDING"
        const val DISCONNECT = "dev.vescmonitor.vesc.DISCONNECT"
    }
}
