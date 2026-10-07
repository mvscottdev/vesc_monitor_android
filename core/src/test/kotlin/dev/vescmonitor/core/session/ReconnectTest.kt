package dev.vescmonitor.core.session

import dev.vescmonitor.core.link.Reason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ReconnectTest {
    @Test
    fun `half-second steps up to five seconds, then the slow tier only off screen`() {
        assertEquals(listOf(500L, 1_000L, 1_500L), (1..3).map { Reconnect.backoffMs(it, visible = true) })
        assertEquals(5_000L, Reconnect.backoffMs(10, visible = false))
        assertEquals(5_000L, Reconnect.backoffMs(12, visible = false))
        assertEquals(30_000L, Reconnect.backoffMs(13, visible = false))
        assertEquals(5_000L, Reconnect.backoffMs(500, visible = true))
    }

    @Test
    fun `user and configuration reasons are not retried`() {
        for (r in listOf(
            Reason.USER_DISCONNECT,
            Reason.NOT_A_BRIDGE,
            Reason.FIRMWARE_TOO_OLD,
            Reason.PERMISSION_CONNECT,
            Reason.PAIRING_NEEDED,
        )) {
            assertFalse(r in Reconnect.RETRYABLE, "$r")
        }
    }
}
