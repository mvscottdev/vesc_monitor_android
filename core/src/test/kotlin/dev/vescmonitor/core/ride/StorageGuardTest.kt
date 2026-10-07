package dev.vescmonitor.core.ride

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageGuardTest {
    private val mb = 1024L * 1024

    @Test
    fun `needs 200 MB free, or twice the database when that is larger`() {
        assertTrue(StorageGuard.low(150 * mb, 10 * mb))
        assertFalse(StorageGuard.low(300 * mb, 10 * mb))
        assertTrue(StorageGuard.low(300 * mb, 400 * mb))
        assertFalse(StorageGuard.low(900 * mb, 400 * mb))
    }
}
