package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.Reason

/** Retry policy after a link loss. */
object Reconnect {
    /** Losses worth retrying: the module may come back in range, power up or free itself. */
    val RETRYABLE =
        setOf(
            Reason.LINK_LOST,
            Reason.MODULE_NOT_FOUND,
            Reason.VESC_NOT_ANSWERING,
            Reason.LINK_ERRORS,
            Reason.TAKEN_BY_OTHER,
            Reason.BLUETOOTH_OFF,
        )

    const val STEP_MS = 500L
    const val FAST_MAX_MS = 5_000L
    const val FAST_ATTEMPTS = 12
    const val SLOW_MS = 30_000L

    /** Connected but no valid reply from any controller for this long: rebuild the link. */
    const val STALE_MS = 4_000L
    const val STALE_CHECK_MS = 500L

    /**
     * Wait before retry [attempt] (1-based): 0.5 s steps up to 5 s for the first
     * [FAST_ATTEMPTS], then [SLOW_MS], but never the slow tier while the app is [visible].
     */
    fun backoffMs(
        attempt: Int,
        visible: Boolean,
    ): Long {
        val fast = minOf(STEP_MS * attempt.coerceAtLeast(1), FAST_MAX_MS)
        return if (attempt > FAST_ATTEMPTS && !visible) SLOW_MS else fast
    }
}
