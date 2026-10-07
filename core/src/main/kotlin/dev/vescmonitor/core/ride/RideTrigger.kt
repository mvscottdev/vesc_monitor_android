package dev.vescmonitor.core.ride

import dev.vescmonitor.core.frame.TelemetryFrame
import kotlin.math.abs

/**
 * When a ride starts and ends: it opens when the vehicle moves (logging on, connected)
 * and closes after [idleEndMs] without motion. Disconnecting also ends it (the app does).
 */
class RideTrigger(
    private val idleEndMs: Long = IDLE_END_MS,
) {
    enum class Action { NONE, OPEN, CLOSE }

    private var lastMovingMs: Long? = null

    fun update(
        nowMs: Long,
        moving: Boolean,
        recording: Boolean,
    ): Action {
        if (moving || (recording && lastMovingMs == null)) lastMovingMs = nowMs
        return when {
            !recording && moving -> Action.OPEN
            recording && nowMs - (lastMovingMs ?: nowMs) >= idleEndMs -> Action.CLOSE.also { lastMovingMs = null }
            else -> Action.NONE
        }
    }

    fun reset() {
        lastMovingMs = null
    }

    companion object {
        /** A stop longer than this ends the ride; the next motion starts a new one. */
        const val IDLE_END_MS = 10 * 60_000L

        /** Moving: combined speed above this, or a fresh controller turning (also on a stand). */
        const val MOVING_MPS = 0.5
        const val MOVING_ERPM = 500.0

        fun moving(frame: TelemetryFrame): Boolean {
            val speed = frame.combined.derived.speedMps
            if (speed != null && abs(speed) > MOVING_MPS) return true
            return frame.vescs.any { it.fresh && abs(it.values.erpm ?: 0.0) > MOVING_ERPM }
        }
    }
}
